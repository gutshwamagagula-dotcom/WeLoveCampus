package com.welovecampus.app.data.repo

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.welovecampus.app.data.local.AppDatabase
import com.welovecampus.app.data.local.ProfileEntity
import com.welovecampus.app.data.remote.Fs
import com.welovecampus.app.data.remote.int
import com.welovecampus.app.data.remote.str
import com.welovecampus.app.data.remote.strList
import com.google.firebase.firestore.DocumentSnapshot
import com.welovecampus.app.util.AppPrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

/**
 * The student's profile document (users/{uid}) and its Room cache.
 */
class ProfileRepository(private val db: AppDatabase, private val prefs: AppPrefs) {

    private val auth = FirebaseAuth.getInstance()
    val uid: String? get() = auth.currentUser?.uid

    fun observeProfile(): Flow<ProfileEntity?> = uid?.let { db.profileDao().observe(it) } ?: flowOf(null)

    suspend fun cachedProfile(): ProfileEntity? = uid?.let { db.profileDao().get(it) }

    /**
     * Called right after login. Creates users/{uid} the first time a student signs in,
     * otherwise just downloads it into Room.
     */
    suspend fun ensureProfile(languageTag: String) {
        val user = auth.currentUser ?: return
        val ref = Fs.user(user.uid)
        val snap = ref.get().await()
        if (!snap.exists()) {
            val data = hashMapOf(
                "fullName" to (user.displayName ?: user.email?.substringBefore("@") ?: "Student"),
                "email" to (user.email ?: ""),
                "preferredLanguage" to languageTag,
                "enrolledModules" to emptyList<String>(),
                "streakDays" to 0,
                "points" to 0,
                "badges" to emptyList<String>(),
                "lastCheckInDay" to "",
                "notesSaved" to 0,
                "onTimeSubmissions" to 0,
                "discussionModules" to emptyList<String>(),
                "notificationPrefs" to prefs.allCategoryPrefs(),
                "createdAt" to FieldValue.serverTimestamp()
            )
            ref.set(data).await()
        }
        refresh()
    }

    /** Pull the latest profile from the server (falls back to Firestore's own cache offline). */
    suspend fun refresh(fromServerOnly: Boolean = false) {
        val id = uid ?: return
        val snap = Fs.user(id).get(if (fromServerOnly) Source.SERVER else Source.DEFAULT).await()
        if (snap.exists()) db.profileDao().upsert(snap.toProfile())
    }

    suspend fun setEnrolledModules(codes: List<String>) {
        val id = uid ?: return
        cachedProfile()?.let { db.profileDao().upsert(it.copy(enrolledModules = codes.joinToString(","))) }
        Fs.user(id).set(mapOf("enrolledModules" to codes), SetOptions.merge()).awaitOrQueued()
    }

    suspend fun setLanguage(tag: String) {
        val id = uid ?: return
        cachedProfile()?.let { db.profileDao().upsert(it.copy(preferredLanguage = tag)) }
        Fs.user(id).set(mapOf("preferredLanguage" to tag), SetOptions.merge()).awaitOrQueued()
    }

    suspend fun setFullName(name: String) {
        val id = uid ?: return
        cachedProfile()?.let { db.profileDao().upsert(it.copy(fullName = name)) }
        Fs.user(id).set(mapOf("fullName" to name), SetOptions.merge()).awaitOrQueued()
    }

    /** Mirrors the notification toggles to Firestore so a server/Cloud Function can respect them. */
    suspend fun saveNotificationPrefs() {
        val id = uid ?: return
        Fs.user(id).set(mapOf("notificationPrefs" to prefs.allCategoryPrefs()), SetOptions.merge()).awaitOrQueued()
    }

    /** Stores this phone's FCM token so pushes can be targeted at the student. */
    suspend fun registerFcmToken(token: String) {
        val id = uid ?: return
        Fs.user(id).set(mapOf("fcmTokens" to FieldValue.arrayUnion(token)), SetOptions.merge()).awaitOrQueued()
    }

    companion object {
        fun DocumentSnapshot.toProfile() = ProfileEntity(
            uid = id,
            fullName = str("fullName", "Student"),
            email = str("email"),
            preferredLanguage = str("preferredLanguage", "en"),
            enrolledModules = strList("enrolledModules").joinToString(","),
            streakDays = int("streakDays"),
            points = int("points"),
            badges = strList("badges").joinToString(","),
            lastCheckInDay = str("lastCheckInDay")
        )
    }
}
