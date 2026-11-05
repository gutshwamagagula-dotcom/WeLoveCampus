package com.welovecampus.app.data.repo

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Firestore keeps writes in its local queue while offline and only completes the Task
 * once the server confirms. Waiting forever would freeze the caller, so we wait a few
 * seconds and then carry on - the write is still delivered automatically when the
 * connection comes back.
 * Returns true if the server confirmed in time.
 */
suspend fun <T> Task<T>.awaitOrQueued(timeoutMs: Long = 8_000): Boolean =
    withTimeoutOrNull(timeoutMs) { await(); true } ?: false
