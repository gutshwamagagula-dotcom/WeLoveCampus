package com.welovecampus.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.welovecampus.app.R
import com.welovecampus.app.ui.main.MainActivity
import com.welovecampus.app.util.AppPrefs
import com.welovecampus.app.util.NotificationCategory

/**
 * One Android notification channel per category (Requirement 2.9).
 * Every notification goes through [show], which checks the student's toggles first.
 * EMERGENCY is always shown, whatever the settings say.
 */
object NotificationHelper {

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val channels = listOf(
            NotificationChannel(NotificationCategory.ASSIGNMENTS, context.getString(R.string.notif_assignments), NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(NotificationCategory.TIMETABLE, context.getString(R.string.notif_timetable), NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(NotificationCategory.ANNOUNCEMENTS, context.getString(R.string.notif_announcements), NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(NotificationCategory.EVENTS, context.getString(R.string.notif_events), NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(NotificationCategory.DISCUSSIONS, context.getString(R.string.notif_discussions), NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(NotificationCategory.EMERGENCY, context.getString(R.string.notif_emergency), NotificationManager.IMPORTANCE_HIGH)
        )
        nm.createNotificationChannels(channels)
    }

    /**
     * @param dedupeKey unique id of the thing being notified about, so the same
     *                  announcement is not shown twice (once by push, once by the worker).
     * @param openTab   which bottom-nav tab MainActivity should open when tapped.
     */
    fun show(
        context: Context,
        category: String,
        title: String,
        body: String,
        dedupeKey: String,
        openTab: Int = 0
    ) {
        val prefs = AppPrefs(context)
        if (!prefs.isCategoryEnabled(category)) return
        if (prefs.wasNotified(dedupeKey)) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_TAB, openTab)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            context, dedupeKey.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isEmergency = category == NotificationCategory.EMERGENCY
        val notification = NotificationCompat.Builder(context, category)
            .setSmallIcon(if (isEmergency) R.drawable.ic_warning else R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, if (isEmergency) R.color.emergency else R.color.crimson))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(if (isEmergency) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(dedupeKey.hashCode(), notification)
            prefs.markNotified(dedupeKey)
        } catch (_: SecurityException) {
            // permission revoked between the check and the call - nothing to do
        }
    }
}
