package com.welovecampus.app

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.auth.FirebaseAuth
import com.welovecampus.app.notifications.NotificationHelper
import com.welovecampus.app.sync.SyncWorker

/**
 * Application class - created once when the process starts.
 * Sets up notification channels, the 30-minute sync and "sync when the app is opened".
 */
class WeLoveCampusApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationHelper.createChannels(this)
        SyncWorker.schedulePeriodic(this)

        // Requirement 2.5: sync whenever the app comes to the foreground
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                if (FirebaseAuth.getInstance().currentUser != null) SyncWorker.syncNow(this@WeLoveCampusApp)
            }
        })
    }
}
