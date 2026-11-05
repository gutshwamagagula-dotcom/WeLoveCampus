package com.welovecampus.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/*
 * Room entities = the tables on the phone.
 * Room is the "source of truth" for the UI: screens always read from here,
 * and Firestore data is copied into these tables whenever we are online.
 * Dates are stored as Long (milliseconds since 1970) so no TypeConverters are needed.
 */

/** A module the institution offers, e.g. OPSC6312. */
@Entity(tableName = "modules")
data class ModuleEntity(
    @PrimaryKey val code: String,
    val name: String,
    val lecturer: String
)

/** One class in the weekly timetable. dayOfWeek: 1 = Monday ... 5 = Friday. */
@Entity(tableName = "timetable_slots")
data class TimetableSlotEntity(
    @PrimaryKey val slotId: String,
    val moduleCode: String,
    val moduleName: String,
    val dayOfWeek: Int,
    val startTime: String,   // "HH:mm"
    val endTime: String,     // "HH:mm"
    val room: String,        // physical room or online link
    val lecturer: String
)

/**
 * An assignment plus THIS student's submission state.
 * statusSynced = false means the student marked it submitted while offline
 * and the SyncWorker still has to upload that to Firestore.
 */
@Entity(tableName = "assignments")
data class AssignmentEntity(
    @PrimaryKey val assignmentId: String,
    val moduleCode: String,
    val title: String,
    val description: String,
    val dueDate: Long,
    val submitted: Boolean = false,
    val submittedAt: Long? = null,
    val statusSynced: Boolean = true
)

/** Sync states for offline-first notes. */
object SyncStatus {
    const val SYNCED = "SYNCED"
    const val PENDING_UPLOAD = "PENDING_UPLOAD"
    const val PENDING_DELETE = "PENDING_DELETE"
}

/** A note. Created on the device first; noteId is a UUID generated on the phone. */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val noteId: String,
    val title: String,
    val content: String,
    val moduleCode: String,
    val tag: String,
    val lastModified: Long,
    val syncStatus: String = SyncStatus.PENDING_UPLOAD
)

/** category: ACADEMIC, ADMINISTRATIVE or EMERGENCY */
@Entity(tableName = "announcements")
data class AnnouncementEntity(
    @PrimaryKey val announcementId: String,
    val sender: String,
    val title: String,
    val message: String,
    val category: String,
    val timestamp: Long
)

@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey val eventId: String,
    val title: String,
    val description: String,
    val location: String,
    val eventDate: Long
)

/**
 * Cached copy of the signed-in student's Firestore profile so the Dashboard
 * can show the name, streak and points while offline.
 * Lists are stored comma-separated to keep the table simple.
 */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val uid: String,
    val fullName: String,
    val email: String,
    val preferredLanguage: String,
    val enrolledModules: String,     // "OPSC6312,PROG6212"
    val streakDays: Int,
    val points: Int,
    val badges: String,              // "FIRST_CHECK_IN,STREAK_7"
    val lastCheckInDay: String       // "yyyy-MM-dd" or ""
) {
    fun moduleList(): List<String> = enrolledModules.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    fun badgeList(): List<String> = badges.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}
