package com.welovecampus.app.data.repo

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Source
import com.welovecampus.app.data.local.AppDatabase
import com.welovecampus.app.data.local.NoteEntity
import com.welovecampus.app.data.local.SyncStatus
import com.welovecampus.app.data.remote.Fs
import com.welovecampus.app.data.remote.millis
import com.welovecampus.app.util.AppPrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID

/**
 * Offline-first notes (Requirements 2.4 and 2.5).
 *
 *  - Every create / edit / delete is written to Room ONLY, so it always works in airplane mode.
 *    The note is flagged PENDING_UPLOAD (or PENDING_DELETE).
 *  - [sync] later pushes pending notes and pulls changes made on other devices ("delta sync").
 *  - Conflicts are resolved "last write wins" using each note's lastModified time.
 */
class NotesRepository(
    private val db: AppDatabase,
    private val prefs: AppPrefs,
    private val gamification: GamificationRepository
) {
    private val auth = FirebaseAuth.getInstance()
    private val dao get() = db.noteDao()

    fun observeNotes(): Flow<List<NoteEntity>> = dao.observeVisible()
    fun observePendingCount(): Flow<Int> = dao.observePendingCount()

    suspend fun getNote(id: String): NoteEntity? = dao.getById(id)

    /** Create (id == null) or update a note locally. Returns the note id. */
    suspend fun saveNote(id: String?, title: String, content: String, moduleCode: String, tag: String): String {
        val noteId = id ?: UUID.randomUUID().toString()
        dao.upsert(
            NoteEntity(
                noteId = noteId,
                title = title,
                content = content,
                moduleCode = moduleCode,
                tag = tag,
                lastModified = System.currentTimeMillis(),
                syncStatus = SyncStatus.PENDING_UPLOAD
            )
        )
        return noteId
    }

    /** Soft delete: hidden at once, removed from the server on the next sync. */
    suspend fun deleteNote(id: String) {
        val note = dao.getById(id) ?: return
        dao.upsert(note.copy(syncStatus = SyncStatus.PENDING_DELETE, lastModified = System.currentTimeMillis()))
    }

    data class SyncReport(val uploaded: Int, val downloaded: Int, val newBadges: List<Badge>)

    /** Two-way delta sync. Must be called with a connection (throws otherwise). */
    suspend fun sync(): SyncReport {
        val uid = auth.currentUser?.uid ?: return SyncReport(0, 0, emptyList())
        val notesRef = Fs.user(uid).collection(Fs.NOTES)
        var uploaded = 0
        var newlyCreatedOnServer = 0

        // ---- 1. PUSH local changes ----
        for (local in dao.getPending()) {
            val docRef = notesRef.document(local.noteId)
            val remote = docRef.get(Source.SERVER).await()
            val remoteModified = if (remote.exists()) remote.millis("lastModified") else 0L

            if (remote.exists() && remoteModified > local.lastModified) {
                // Someone edited it more recently on another device -> server wins
                applyRemote(remote.id, remote.data ?: continue)
                continue
            }
            if (local.syncStatus == SyncStatus.PENDING_DELETE) {
                docRef.set(
                    mapOf("deleted" to true, "lastModified" to local.lastModified, "updatedAt" to FieldValue.serverTimestamp())
                ).await()
                dao.deleteById(local.noteId)
            } else {
                docRef.set(
                    mapOf(
                        "title" to local.title,
                        "content" to local.content,
                        "moduleCode" to local.moduleCode,
                        "tag" to local.tag,
                        "lastModified" to local.lastModified,
                        "deleted" to false,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                dao.upsert(local.copy(syncStatus = SyncStatus.SYNCED))
                if (!remote.exists()) newlyCreatedOnServer++
            }
            uploaded++
        }

        // ---- 2. PULL changes made elsewhere since the last pull ----
        val since = Timestamp(Date(prefs.notesPulledUntil))
        val changes = notesRef.whereGreaterThan("updatedAt", since).get(Source.SERVER).await()
        var newest = prefs.notesPulledUntil
        var downloaded = 0
        for (doc in changes.documents) {
            val data = doc.data ?: continue
            if (applyRemote(doc.id, data)) downloaded++
            newest = maxOf(newest, doc.millis("updatedAt"))
        }
        prefs.notesPulledUntil = newest

        // ---- 3. Points for note-taking ----
        val badges = if (newlyCreatedOnServer > 0) {
            runCatching { gamification.record(GamAction.NOTE_SAVED, count = newlyCreatedOnServer).newBadges }
                .getOrDefault(emptyList())
        } else emptyList()

        return SyncReport(uploaded, downloaded, badges)
    }

    /** Applies a server copy unless the local copy is a newer unsynced edit. Returns true if applied. */
    private suspend fun applyRemote(id: String, data: Map<String, Any>): Boolean {
        val remoteModified = (data["lastModified"] as? Number)?.toLong() ?: 0L
        val local = dao.getById(id)
        if (local != null && local.syncStatus != SyncStatus.SYNCED && local.lastModified > remoteModified) {
            return false // local edit is newer; it will be pushed next time
        }
        if (data["deleted"] == true) {
            dao.deleteById(id)
        } else {
            dao.upsert(
                NoteEntity(
                    noteId = id,
                    title = data["title"] as? String ?: "",
                    content = data["content"] as? String ?: "",
                    moduleCode = data["moduleCode"] as? String ?: "",
                    tag = data["tag"] as? String ?: "",
                    lastModified = remoteModified,
                    syncStatus = SyncStatus.SYNCED
                )
            )
        }
        return true
    }
}
