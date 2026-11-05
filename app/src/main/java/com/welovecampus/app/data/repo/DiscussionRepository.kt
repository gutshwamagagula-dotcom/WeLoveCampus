package com.welovecampus.app.data.repo

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.welovecampus.app.data.remote.Fs
import com.welovecampus.app.data.remote.int
import com.welovecampus.app.data.remote.str
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class DiscussionThread(
    val id: String,
    val moduleCode: String,
    val title: String,
    val body: String,
    val authorUid: String,
    val authorName: String,
    val createdAt: Long,
    val replyCount: Int,
    val lastActivity: Long
)

data class DiscussionReply(
    val id: String,
    val body: String,
    val authorUid: String,
    val authorName: String,
    val createdAt: Long
)

/**
 * Module-scoped discussion boards (Requirement 2.6).
 * Uses Firestore snapshot listeners, so new threads/replies appear within seconds.
 * Firestore's built-in offline cache lets the student read boards and even post
 * while offline; the post is delivered when the connection returns.
 */
class DiscussionRepository(private val gamification: GamificationRepository) {

    private val auth = FirebaseAuth.getInstance()
    private val threads get() = Fs.db.collection(Fs.DISCUSSIONS)

    /** Live list of threads for ONE module, most recently active first. */
    fun observeThreads(moduleCode: String): Flow<List<DiscussionThread>> = callbackFlow {
        val reg = threads.whereEqualTo("moduleCode", moduleCode)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.w(TAG, "threads listener", err)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snap?.documents?.map { it.toThread() }.orEmpty()
                    .sortedByDescending { it.lastActivity }
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    fun observeThread(threadId: String): Flow<DiscussionThread?> = callbackFlow {
        val reg = threads.document(threadId).addSnapshotListener { snap, err ->
            if (err != null) { trySend(null); return@addSnapshotListener }
            trySend(snap?.takeIf { it.exists() }?.toThread())
        }
        awaitClose { reg.remove() }
    }

    fun observeReplies(threadId: String): Flow<List<DiscussionReply>> = callbackFlow {
        val reg = threads.document(threadId).collection(Fs.REPLIES)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) { trySend(emptyList()); return@addSnapshotListener }
                trySend(snap?.documents?.map { d ->
                    DiscussionReply(
                        id = d.id,
                        body = d.str("body"),
                        authorUid = d.str("authorUid"),
                        authorName = d.str("authorName"),
                        createdAt = d.time("createdAt")
                    )
                }.orEmpty())
            }
        awaitClose { reg.remove() }
    }

    /** Starts a new thread. Not awaited: Firestore queues it if offline. */
    fun createThread(moduleCode: String, title: String, body: String, authorName: String) {
        val uid = auth.currentUser?.uid ?: return
        threads.document().set(
            mapOf(
                "moduleCode" to moduleCode,
                "title" to title,
                "body" to body,
                "authorUid" to uid,
                "authorName" to authorName,
                "createdAt" to FieldValue.serverTimestamp(),
                "lastActivity" to FieldValue.serverTimestamp(),
                "replyCount" to 0
            )
        ).addOnFailureListener { Log.w(TAG, "createThread", it) }
    }

    /** Adds a reply and bumps the thread's reply counter in one atomic batch. */
    fun reply(threadId: String, body: String, authorName: String) {
        val uid = auth.currentUser?.uid ?: return
        val threadRef = threads.document(threadId)
        val replyRef = threadRef.collection(Fs.REPLIES).document()
        Fs.db.batch()
            .set(
                replyRef, mapOf(
                    "body" to body,
                    "authorUid" to uid,
                    "authorName" to authorName,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            .update(threadRef, mapOf("replyCount" to FieldValue.increment(1), "lastActivity" to FieldValue.serverTimestamp()))
            .commit()
            .addOnFailureListener { Log.w(TAG, "reply", it) }
    }

    /** Points + Module Explorer badge. Needs a connection; failures are ignored. */
    suspend fun rewardPost(moduleCode: String): List<Badge> =
        runCatching { gamification.record(GamAction.DISCUSSION_POST, moduleCode).newBadges }.getOrDefault(emptyList())

    private fun DocumentSnapshot.toThread() = DiscussionThread(
        id = id,
        moduleCode = str("moduleCode"),
        title = str("title"),
        body = str("body"),
        authorUid = str("authorUid"),
        authorName = str("authorName"),
        createdAt = time("createdAt"),
        replyCount = int("replyCount"),
        lastActivity = time("lastActivity")
    )

    /** Server timestamps are null until the server confirms - ESTIMATE uses the local clock meanwhile. */
    private fun DocumentSnapshot.time(field: String): Long =
        getTimestamp(field, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time
            ?: System.currentTimeMillis()

    companion object { private const val TAG = "DiscussionRepo" }
}
