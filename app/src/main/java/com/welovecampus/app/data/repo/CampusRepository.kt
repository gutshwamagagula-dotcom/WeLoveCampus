package com.welovecampus.app.data.repo

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import com.welovecampus.app.data.local.AnnouncementEntity
import com.welovecampus.app.data.local.AppDatabase
import com.welovecampus.app.data.local.AssignmentEntity
import com.welovecampus.app.data.local.EventEntity
import com.welovecampus.app.data.local.ModuleEntity
import com.welovecampus.app.data.local.TimetableSlotEntity
import com.welovecampus.app.data.remote.Fs
import com.welovecampus.app.data.remote.int
import com.welovecampus.app.data.remote.millis
import com.welovecampus.app.data.remote.str
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/**
 * Institution data: modules, timetable, assignments, announcements and events.
 * The UI only ever observes Room. [refreshAll] copies the latest Firestore data
 * into Room whenever we are online (Requirements 2.1, 2.2, 2.3, 2.7, 2.8).
 */
class CampusRepository(
    private val db: AppDatabase,
    private val profileRepo: ProfileRepository,
    private val gamification: GamificationRepository
) {
    private val auth = FirebaseAuth.getInstance()
    private val submitLock = Mutex()   // stops the worker and the UI uploading (and scoring) the same submission twice

    // ---------- Observed by the screens ----------
    fun observeModules(): Flow<List<ModuleEntity>> = db.moduleDao().observeAll()
    fun observeTimetable(): Flow<List<TimetableSlotEntity>> = db.timetableDao().observeAll()
    fun observeAssignments(): Flow<List<AssignmentEntity>> = db.assignmentDao().observeAll()
    fun observeAssignment(id: String): Flow<AssignmentEntity?> = db.assignmentDao().observeById(id)
    fun observeAnnouncements(): Flow<List<AnnouncementEntity>> = db.announcementDao().observeAll()
    fun observeEvents(): Flow<List<EventEntity>> = db.eventDao().observeAll()

    suspend fun allModules(): List<ModuleEntity> = db.moduleDao().getAll()

    /**
     * Downloads everything from the server and replaces the Room cache.
     * Throws if offline - callers catch it and simply keep showing the cache.
     */
    suspend fun refreshAll() {
        val uid = auth.currentUser?.uid ?: return
        profileRepo.refresh(fromServerOnly = true)
        val enrolled = profileRepo.cachedProfile()?.moduleList().orEmpty()

        // Modules (all of them, so the student can pick which to enrol in)
        val modules = Fs.db.collection(Fs.MODULES).get(Source.SERVER).await().documents.map {
            ModuleEntity(it.id, it.str("name"), it.str("lecturer"))
        }
        db.moduleDao().replaceAll(modules)

        // Timetable - only classes for enrolled modules
        val slots = queryByModules(Fs.TIMETABLE, enrolled).flatMap { qs ->
            qs.documents.map {
                TimetableSlotEntity(
                    slotId = it.id,
                    moduleCode = it.str("moduleCode"),
                    moduleName = it.str("moduleName"),
                    dayOfWeek = it.int("dayOfWeek", 1),
                    startTime = it.str("startTime"),
                    endTime = it.str("endTime"),
                    room = it.str("room"),
                    lecturer = it.str("lecturer")
                )
            }
        }
        db.timetableDao().replaceAll(slots)

        // Assignments + this student's submission status
        val statusDocs = Fs.user(uid).collection(Fs.ASSIGNMENT_STATUS).get(Source.SERVER).await()
            .documents.associateBy { it.id }
        val localUnsynced = db.assignmentDao().getUnsynced().associateBy { it.assignmentId }
        val assignments = queryByModules(Fs.ASSIGNMENTS, enrolled).flatMap { qs ->
            qs.documents.map { d ->
                val status = statusDocs[d.id]
                val local = localUnsynced[d.id]
                AssignmentEntity(
                    assignmentId = d.id,
                    moduleCode = d.str("moduleCode"),
                    title = d.str("title"),
                    description = d.str("description"),
                    dueDate = d.millis("dueDate"),
                    // a submission made offline and not uploaded yet must not be lost
                    submitted = local?.submitted ?: (status?.getBoolean("submitted") ?: false),
                    submittedAt = local?.submittedAt ?: status?.millis("submittedAt")?.takeIf { it > 0 },
                    statusSynced = local == null
                )
            }
        }
        db.assignmentDao().replaceAll(assignments)

        // Announcements (newest 50)
        val announcements = Fs.db.collection(Fs.ANNOUNCEMENTS)
            .orderBy("timestamp", Query.Direction.DESCENDING).limit(50)
            .get(Source.SERVER).await().documents.map {
                AnnouncementEntity(
                    announcementId = it.id,
                    sender = it.str("sender"),
                    title = it.str("title"),
                    message = it.str("message"),
                    category = it.str("category", "ACADEMIC").uppercase(),
                    timestamp = it.millis("timestamp")
                )
            }
        db.announcementDao().replaceAll(announcements)

        // Events - published by the institution, no app update needed (Requirement 2.8)
        val events = Fs.db.collection(Fs.EVENTS).get(Source.SERVER).await().documents.map {
            EventEntity(
                eventId = it.id,
                title = it.str("title"),
                description = it.str("description"),
                location = it.str("location"),
                eventDate = it.millis("eventDate")
            )
        }
        db.eventDao().replaceAll(events)
    }

    /** Firestore "in" queries accept at most 30 values, so split the module list. */
    private suspend fun queryByModules(collection: String, modules: List<String>): List<QuerySnapshot> {
        if (modules.isEmpty()) return emptyList()
        return modules.chunked(30).map { chunk ->
            Fs.db.collection(collection).whereIn("moduleCode", chunk).get(Source.SERVER).await()
        }
    }

    /**
     * Marks an assignment as submitted. Saved to Room first (works offline),
     * then uploaded by [pushPendingSubmissions].
     */
    suspend fun markSubmitted(id: String) {
        db.assignmentDao().markSubmitted(id, System.currentTimeMillis())
    }

    /** Uploads offline submissions and awards on-time points. Returns any new badges. */
    suspend fun pushPendingSubmissions(): List<Badge> = submitLock.withLock {
        val uid = auth.currentUser?.uid ?: return@withLock emptyList()
        val badges = mutableListOf<Badge>()
        for (a in db.assignmentDao().getUnsynced()) {
            val at = a.submittedAt ?: System.currentTimeMillis()
            Fs.user(uid).collection(Fs.ASSIGNMENT_STATUS).document(a.assignmentId)
                .set(mapOf("submitted" to true, "submittedAt" to at, "moduleCode" to a.moduleCode)).await()
            db.assignmentDao().markStatusSynced(a.assignmentId)
            if (at <= a.dueDate) {
                runCatching { gamification.record(GamAction.ON_TIME_SUBMISSION) }
                    .onSuccess { badges += it.newBadges }
            }
        }
        badges
    }
}
