package com.welovecampus.app.data.repo

import androidx.annotation.StringRes
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.SetOptions
import com.welovecampus.app.R
import com.welovecampus.app.data.local.AppDatabase
import com.welovecampus.app.data.remote.Fs
import com.welovecampus.app.data.remote.int
import com.welovecampus.app.data.remote.str
import com.welovecampus.app.data.remote.strList
import com.welovecampus.app.util.DateUtils
import com.welovecampus.app.util.StreakCalculator
import kotlinx.coroutines.tasks.await

/** Things that earn points (Requirement 2.11). */
enum class GamAction(val points: Int) {
    CHECK_IN(10),
    ON_TIME_SUBMISSION(20),
    NOTE_SAVED(5),
    DISCUSSION_POST(5)
}

/** Every badge, its display name and description. */
enum class Badge(@StringRes val title: Int, @StringRes val description: Int) {
    FIRST_CHECK_IN(R.string.badge_first_check_in, R.string.badge_first_check_in_desc),
    STREAK_7(R.string.badge_streak_7, R.string.badge_streak_7_desc),
    STREAK_30(R.string.badge_streak_30, R.string.badge_streak_30_desc),
    ON_TIME(R.string.badge_on_time, R.string.badge_on_time_desc),
    NOTE_TAKER(R.string.badge_note_taker, R.string.badge_note_taker_desc),
    MODULE_EXPLORER(R.string.badge_module_explorer, R.string.badge_module_explorer_desc),
    CENTURY(R.string.badge_century, R.string.badge_century_desc);

    companion object {
        fun fromCode(code: String): Badge? = entries.firstOrNull { it.name == code }
    }
}

data class GamResult(
    val points: Int,
    val streak: Int,
    val newBadges: List<Badge>,
    val alreadyCheckedInToday: Boolean = false
)

/**
 * Scores are updated inside a Firestore TRANSACTION: the document is read and written
 * atomically, so two phones cannot double-award a badge, and each badge is added only
 * if it is not already in the list ("awarded exactly once").
 * Transactions need a connection - callers catch the exception when offline.
 */
class GamificationRepository(private val db: AppDatabase) {

    private val auth = FirebaseAuth.getInstance()

    suspend fun checkIn(): GamResult = record(GamAction.CHECK_IN)

    suspend fun record(action: GamAction, moduleCode: String? = null, count: Int = 1): GamResult {
        val uid = auth.currentUser?.uid ?: error("Not signed in")
        val ref = Fs.user(uid)
        val today = DateUtils.dayKey()
        val yesterday = DateUtils.yesterdayKey()

        val result = Fs.db.runTransaction { tx ->
            val snap = tx.get(ref)
            var points = snap.int("points")
            var streak = snap.int("streakDays")
            var lastCheckIn = snap.str("lastCheckInDay")
            var notesSaved = snap.int("notesSaved")
            var onTime = snap.int("onTimeSubmissions")
            val badges = snap.strList("badges").toMutableList()
            val discussionModules = snap.strList("discussionModules").toMutableSet()
            var already = false

            when (action) {
                GamAction.CHECK_IN -> {
                    if (lastCheckIn == today) {
                        already = true
                    } else {
                        streak = StreakCalculator.newStreak(lastCheckIn, streak, today, yesterday)
                        lastCheckIn = today
                        points += action.points
                    }
                }
                GamAction.ON_TIME_SUBMISSION -> { onTime += count; points += action.points * count }
                GamAction.NOTE_SAVED -> { notesSaved += count; points += action.points * count }
                GamAction.DISCUSSION_POST -> {
                    moduleCode?.let { discussionModules.add(it) }
                    points += action.points
                }
            }

            // Award each badge only once
            val earned = mutableListOf<Badge>()
            fun award(b: Badge, condition: Boolean) {
                if (condition && b.name !in badges) { badges.add(b.name); earned.add(b) }
            }
            award(Badge.FIRST_CHECK_IN, lastCheckIn.isNotEmpty())
            award(Badge.STREAK_7, streak >= 7)
            award(Badge.STREAK_30, streak >= 30)
            award(Badge.ON_TIME, onTime >= 1)
            award(Badge.NOTE_TAKER, notesSaved >= 10)
            award(Badge.MODULE_EXPLORER, discussionModules.size >= 3)
            award(Badge.CENTURY, points >= 100)

            tx.set(
                ref, mapOf(
                    "points" to points,
                    "streakDays" to streak,
                    "lastCheckInDay" to lastCheckIn,
                    "notesSaved" to notesSaved,
                    "onTimeSubmissions" to onTime,
                    "badges" to badges,
                    "discussionModules" to discussionModules.toList()
                ), SetOptions.merge()
            )
            GamResult(points, streak, earned, already)
        }.await()

        // Keep the offline Dashboard cache in step
        db.profileDao().get(uid)?.let { p ->
            val badgeCodes = (p.badgeList() + result.newBadges.map { it.name }).distinct()
            db.profileDao().upsert(
                p.copy(
                    points = result.points,
                    streakDays = result.streak,
                    badges = badgeCodes.joinToString(","),
                    lastCheckInDay = if (action == GamAction.CHECK_IN) today else p.lastCheckInDay
                )
            )
        }
        return result
    }
}
