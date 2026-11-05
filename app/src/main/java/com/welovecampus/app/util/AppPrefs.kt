package com.welovecampus.app.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Small key-value settings kept in SharedPreferences
 * (sync cursor, notification toggles, which notifications were already shown).
 */
class AppPrefs(context: Context) {
    private val sp = context.getSharedPreferences("wlc_prefs", Context.MODE_PRIVATE)

    /** Server time (ms) of the newest note we have pulled - used for delta sync. */
    var notesPulledUntil: Long
        get() = sp.getLong("notes_pulled_until", 0L)
        set(v) = sp.edit().putLong("notes_pulled_until", v).apply()

    /** When the last full sync finished (shown in Profile/Settings). */
    var lastSyncedAt: Long
        get() = sp.getLong("last_synced_at", 0L)
        set(v) = sp.edit().putLong("last_synced_at", v).apply()

    /** Newest announcement time we have already notified about. */
    var lastAnnouncementSeen: Long
        get() = sp.getLong("last_announcement_seen", 0L)
        set(v) = sp.edit().putLong("last_announcement_seen", v).apply()

    /** Hash of the timetable so we can detect when the institution changes it. */
    var timetableHash: Int
        get() = sp.getInt("timetable_hash", 0)
        set(v) = sp.edit().putInt("timetable_hash", v).apply()

    // ---- Notification preferences (Requirement 2.9) ----
    fun isCategoryEnabled(category: String): Boolean {
        if (category == NotificationCategory.EMERGENCY) return true   // can never be turned off
        return sp.getBoolean("notif_$category", true)
    }

    fun setCategoryEnabled(category: String, enabled: Boolean) {
        if (category == NotificationCategory.EMERGENCY) return
        sp.edit().putBoolean("notif_$category", enabled).apply()
    }

    fun allCategoryPrefs(): Map<String, Boolean> =
        NotificationCategory.ALL.associateWith { isCategoryEnabled(it) }

    // ---- De-duplicate notifications (worker + push could both fire) ----
    fun wasNotified(key: String): Boolean = sp.getStringSet("notified_keys", emptySet())!!.contains(key)

    fun markNotified(key: String) {
        val set = sp.getStringSet("notified_keys", emptySet())!!.toMutableList()
        set.add(key)
        // keep the list small
        val trimmed = set.takeLast(300).toSet()
        sp.edit().putStringSet("notified_keys", trimmed).apply()
    }

    fun clearAll() = sp.edit().clear().apply()
}

object NotificationCategory {
    const val ASSIGNMENTS = "assignments"
    const val TIMETABLE = "timetable"
    const val ANNOUNCEMENTS = "announcements"
    const val EVENTS = "events"
    const val DISCUSSIONS = "discussions"
    const val EMERGENCY = "emergency"
    val ALL = listOf(ASSIGNMENTS, TIMETABLE, ANNOUNCEMENTS, EVENTS, DISCUSSIONS, EMERGENCY)
}

object Network {
    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
