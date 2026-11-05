package com.welovecampus.app

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import com.welovecampus.app.data.local.AppDatabase
import com.welovecampus.app.data.repo.AuthRepository
import com.welovecampus.app.data.repo.Badge
import com.welovecampus.app.data.repo.CampusRepository
import com.welovecampus.app.data.repo.DemoDataSeeder
import com.welovecampus.app.data.repo.DiscussionRepository
import com.welovecampus.app.data.repo.GamificationRepository
import com.welovecampus.app.data.repo.NotesRepository
import com.welovecampus.app.data.repo.ProfileRepository
import com.welovecampus.app.util.AppPrefs
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/**
 * Simple manual dependency injection: one instance of each repository for the whole app.
 * Access it from anywhere with `context.container`.
 */
class AppContainer(context: Context) {
    val db = AppDatabase.get(context)
    val prefs = AppPrefs(context)

    val profileRepo = ProfileRepository(db, prefs)
    val gamification = GamificationRepository(db)
    val campusRepo = CampusRepository(db, profileRepo, gamification)
    val notesRepo = NotesRepository(db, prefs, gamification)
    val discussionRepo = DiscussionRepository(gamification)
    val authRepo = AuthRepository(db, prefs)
    val seeder = DemoDataSeeder()

    private val syncLock = Mutex()

    /**
     * Runs once after every successful login:
     * profile -> demo data (only if Firestore is empty) -> auto-enrol -> first sync.
     */
    suspend fun bootstrapAfterLogin(languageTag: String) {
        profileRepo.ensureProfile(languageTag)
        runCatching { seeder.seedIfEmpty() }
        // A brand-new student with no modules is enrolled in all of them so the app isn't empty.
        // They can change this under Profile > My modules.
        val profile = profileRepo.cachedProfile()
        if (profile != null && profile.moduleList().isEmpty()) {
            val all = runCatching { campusRepo.refreshAll(); campusRepo.allModules().map { it.code } }.getOrDefault(emptyList())
            if (all.isNotEmpty()) profileRepo.setEnrolledModules(all)
        }
        runCatching { FirebaseMessaging.getInstance().subscribeToTopic(AuthRepository.TOPIC_ANNOUNCEMENTS).await() }
        runCatching { profileRepo.registerFcmToken(FirebaseMessaging.getInstance().token.await()) }
        runCatching { syncEverything() }
    }

    /** Full two-way sync. Throws when offline. Returns any badges unlocked along the way. */
    suspend fun syncEverything(): List<Badge> = syncLock.withLock {
        val badges = mutableListOf<Badge>()
        badges += notesRepo.sync().newBadges
        badges += campusRepo.pushPendingSubmissions()
        campusRepo.refreshAll()
        prefs.lastSyncedAt = System.currentTimeMillis()
        badges
    }
}

/** Shortcut: `context.container` from any Activity, Fragment, Service or Worker. */
val Context.container: AppContainer
    get() = (applicationContext as WeLoveCampusApp).container
