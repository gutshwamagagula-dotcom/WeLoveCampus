package com.welovecampus.app.data.remote

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Cloud Firestore layout used by the app (this replaces the REST API from the design doc):
 *
 *  modules/{moduleCode}                      name, lecturer
 *  timetable/{slotId}                        moduleCode, moduleName, dayOfWeek, startTime, endTime, room, lecturer
 *  assignments/{assignmentId}                moduleCode, title, description, dueDate (Timestamp)
 *  announcements/{announcementId}            sender, title, message, category, timestamp
 *  events/{eventId}                          title, description, location, eventDate
 *  discussions/{threadId}                    moduleCode, title, body, authorUid, authorName, createdAt, replyCount, lastActivity
 *  discussions/{threadId}/replies/{replyId}  body, authorUid, authorName, createdAt
 *  users/{uid}                               profile + gamification + notification prefs
 *  users/{uid}/notes/{noteId}                title, content, moduleCode, tag, lastModified, updatedAt, deleted
 *  users/{uid}/assignmentStatus/{id}         submitted, submittedAt
 */
object Fs {
    const val MODULES = "modules"
    const val TIMETABLE = "timetable"
    const val ASSIGNMENTS = "assignments"
    const val ANNOUNCEMENTS = "announcements"
    const val EVENTS = "events"
    const val DISCUSSIONS = "discussions"
    const val REPLIES = "replies"
    const val USERS = "users"
    const val NOTES = "notes"
    const val ASSIGNMENT_STATUS = "assignmentStatus"

    val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    fun user(uid: String) = db.collection(USERS).document(uid)
}

/* Null-safe readers - Firestore documents typed by hand in the console may miss fields. */
fun DocumentSnapshot.str(field: String, default: String = ""): String = getString(field) ?: default
fun DocumentSnapshot.int(field: String, default: Int = 0): Int = getLong(field)?.toInt() ?: default
fun DocumentSnapshot.millis(field: String): Long {
    // Accept either a Firestore Timestamp or a plain number of milliseconds
    return when (val v = get(field)) {
        is Timestamp -> v.toDate().time
        is Number -> v.toLong()
        else -> 0L
    }
}
@Suppress("UNCHECKED_CAST")
fun DocumentSnapshot.strList(field: String): List<String> = (get(field) as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
