package com.welovecampus.app.sync

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.welovecampus.app.R
import com.welovecampus.app.container
import com.welovecampus.app.notifications.NotificationHelper
import com.welovecampus.app.util.DateUtils
import com.welovecampus.app.util.NotificationCategory
import java.util.concurrent.TimeUnit

/**
 * Background Sync Service (Requirement 2.5).
 *  - runs every 30 minutes (periodic) and whenever the app comes to the foreground (one-off)
 *  - only runs when the phone has internet (WorkManager constraint)
 * It syncs notes, uploads offline submissions, refreshes the Room cache
 * and raises local reminders for deadlines, events, timetable changes and new announcements.
 */
class SyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (FirebaseAuth.getInstance().currentUser == null) return Result.success()
        val c = applicationContext.container
        return try {
            c.syncEverything()
            raiseReminders()
            Result.success()
        } catch (e: Exception) {
            Log.w("SyncWorker", "sync failed, will retry", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private suspend fun raiseReminders() {
        val c = applicationContext.container
        val ctx = applicationContext
        val prefs = c.prefs
        val now = System.currentTimeMillis()
        val day = TimeUnit.DAYS.toMillis(1)

        // 1. Assignments due within 24 hours
        c.db.assignmentDao().getAll()
            .filter { !it.submitted && it.dueDate in now..(now + day) }
            .forEach {
                NotificationHelper.show(
                    ctx, NotificationCategory.ASSIGNMENTS,
                    ctx.getString(R.string.notif_assignment_due_title, it.moduleCode),
                    ctx.getString(R.string.notif_assignment_due_body, it.title, DateUtils.formatDateTime(it.dueDate)),
                    dedupeKey = "assignments:due:${it.assignmentId}", openTab = 2
                )
            }

        // 2. Events happening in the next 24 hours
        c.db.eventDao().getAll()
            .filter { it.eventDate in now..(now + day) }
            .forEach {
                NotificationHelper.show(
                    ctx, NotificationCategory.EVENTS,
                    ctx.getString(R.string.notif_event_title, it.title),
                    ctx.getString(R.string.notif_event_body, DateUtils.formatDateTime(it.eventDate), it.location),
                    dedupeKey = "events:soon:${it.eventId}"
                )
            }

        // 3. New announcements since last time (skip on the very first sync to avoid a flood)
        val announcements = c.db.announcementDao().getAll()
        val lastSeen = prefs.lastAnnouncementSeen
        if (lastSeen > 0) {
            announcements.filter { it.timestamp > lastSeen }.forEach {
                val emergency = it.category == "EMERGENCY"
                val category = if (emergency) NotificationCategory.EMERGENCY else NotificationCategory.ANNOUNCEMENTS
                NotificationHelper.show(ctx, category, "${it.sender}: ${it.title}", it.message, dedupeKey = "$category:${it.announcementId}")
            }
        }
        announcements.maxOfOrNull { it.timestamp }?.let { prefs.lastAnnouncementSeen = maxOf(lastSeen, it) }

        // 4. Timetable changed by the institution
        val slots = c.db.timetableDao().getAll()
        val hash = slots.map { "${it.slotId}${it.dayOfWeek}${it.startTime}${it.endTime}${it.room}" }.sorted().hashCode()
        if (prefs.timetableHash != 0 && prefs.timetableHash != hash && slots.isNotEmpty()) {
            NotificationHelper.show(
                ctx, NotificationCategory.TIMETABLE,
                ctx.getString(R.string.notif_timetable_changed_title),
                ctx.getString(R.string.notif_timetable_changed_body),
                dedupeKey = "timetable:$hash", openTab = 1
            )
        }
        prefs.timetableHash = hash
    }

    companion object {
        private const val PERIODIC = "wlc_periodic_sync"
        private const val ONE_OFF = "wlc_one_off_sync"

        private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Every 30 minutes while the app is installed. */
        fun schedulePeriodic(context: Context) {
            val req = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES)
                .setConstraints(online)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        /** Runs as soon as there is a connection (used when the app opens / note saved). */
        fun syncNow(context: Context) {
            val req = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(online)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(ONE_OFF, ExistingWorkPolicy.APPEND_OR_REPLACE, req)
        }

        fun cancelAll(context: Context) {
            WorkManager.getInstance(context).cancelAllWork()
        }
    }
}
