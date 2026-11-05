package com.welovecampus.app.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.welovecampus.app.container
import com.welovecampus.app.util.NotificationCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives Firebase Cloud Messaging pushes.
 * Expected DATA payload (see functions/index.js):
 *   category = assignments | timetable | announcements | events | discussions | emergency
 *   title, body, id (used to avoid duplicates)
 */
class WlcMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch { runCatching { applicationContext.container.profileRepo.registerFcmToken(token) } }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val category = data["category"]?.lowercase()?.takeIf { it in NotificationCategory.ALL }
            ?: NotificationCategory.ANNOUNCEMENTS
        val title = data["title"] ?: message.notification?.title ?: return
        val body = data["body"] ?: message.notification?.body ?: ""
        val id = data["id"] ?: message.messageId ?: title

        val tab = when (category) {
            NotificationCategory.ASSIGNMENTS -> 2
            NotificationCategory.TIMETABLE -> 1
            else -> 0
        }
        NotificationHelper.show(applicationContext, category, title, body, dedupeKey = "$category:$id", openTab = tab)
    }
}
