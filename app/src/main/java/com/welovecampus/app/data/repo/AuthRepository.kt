package com.welovecampus.app.data.repo

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.messaging.FirebaseMessaging
import com.welovecampus.app.data.local.AppDatabase
import com.welovecampus.app.util.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Firebase Authentication (Requirement 2.10):
 *  - "Continue with Google": Credential Manager gives us a Google ID token,
 *    Firebase verifies it server-side and creates the session.
 *  - "Institution Sign-In": email + password accounts as the fallback.
 */
class AuthRepository(private val db: AppDatabase, private val prefs: AppPrefs) {

    private val auth = FirebaseAuth.getInstance()

    val isSignedIn: Boolean get() = auth.currentUser != null

    suspend fun signInWithGoogleIdToken(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).await()
    }

    suspend fun signInWithEmail(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).await()
    }

    suspend fun register(fullName: String, email: String, password: String) {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        val request = UserProfileChangeRequest.Builder().setDisplayName(fullName).build()
        result.user?.updateProfile(request)?.await()
    }

    suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email).await()
    }

    /** Signs out of Firebase and Google and wipes this student's local data. */
    suspend fun signOut(context: Context) {
        runCatching { FirebaseMessaging.getInstance().unsubscribeFromTopic(TOPIC_ANNOUNCEMENTS).await() }
        auth.signOut()
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
        withContext(Dispatchers.IO) { db.clearAllTables() }
        prefs.clearAll()
    }

    companion object {
        /** FCM topic every student subscribes to for institution-wide announcements. */
        const val TOPIC_ANNOUNCEMENTS = "announcements"
    }
}
