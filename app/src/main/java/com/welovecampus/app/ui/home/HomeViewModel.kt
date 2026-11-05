package com.welovecampus.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.welovecampus.app.AppContainer
import com.welovecampus.app.data.local.AnnouncementEntity
import com.welovecampus.app.data.local.EventEntity
import com.welovecampus.app.data.local.ProfileEntity
import com.welovecampus.app.data.local.TimetableSlotEntity
import com.welovecampus.app.data.repo.Badge
import com.welovecampus.app.data.repo.GamResult
import com.welovecampus.app.util.DateUtils
import com.welovecampus.app.util.Priority
import com.welovecampus.app.util.PriorityCalculator
import com.welovecampus.app.util.StreakCalculator
import com.welovecampus.app.util.TimetableLogic
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** One row of the merged "Due Soon" list - either an assignment or an event. */
data class DueItem(
    val id: String,
    val title: String,
    val moduleOrPlace: String,
    val time: Long,
    val isAssignment: Boolean,
    val priority: Priority?
)

data class HomeState(
    val profile: ProfileEntity? = null,
    val nextClass: TimetableSlotEntity? = null,
    val dueSoon: List<DueItem> = emptyList(),
    val announcements: List<AnnouncementEntity> = emptyList(),
    val upcomingEvents: List<EventEntity> = emptyList(),
    val displayStreak: Int = 0,
    val checkedInToday: Boolean = false,
    val clashCount: Int = 0
)

/**
 * Dashboard (Requirement 2.1). Everything comes from Room, so it loads offline too.
 * The "unified priority engine" merges assignments and events into one ranked list.
 */
class HomeViewModel(private val c: AppContainer) : ViewModel() {

    val state: StateFlow<HomeState> = combine(
        c.profileRepo.observeProfile(),
        c.campusRepo.observeTimetable(),
        c.campusRepo.observeAssignments(),
        c.campusRepo.observeEvents(),
        c.campusRepo.observeAnnouncements()
    ) { profile, slots, assignments, events, announcements ->
        val now = System.currentTimeMillis()

        val assignmentItems = assignments
            .filter { !it.submitted && it.dueDate >= now }
            .map { DueItem(it.assignmentId, it.title, it.moduleCode, it.dueDate, true, PriorityCalculator.priorityFor(it.dueDate, now)) }
        val eventItems = events
            .filter { it.eventDate >= now }
            .map { DueItem(it.eventId, it.title, it.location, it.eventDate, false, null) }

        // Ranked: HIGH-priority assignments first, then simply by soonest date
        val merged = (assignmentItems + eventItems)
            .sortedWith(compareBy<DueItem>({ if (it.priority == Priority.HIGH) 0 else 1 }, { it.time }))
            .take(3)

        val today = DateUtils.dayKey()
        val yesterday = DateUtils.yesterdayKey()
        HomeState(
            profile = profile,
            nextClass = TimetableLogic.nextClass(slots),
            dueSoon = merged,
            announcements = announcements.take(3),
            upcomingEvents = events.filter { it.eventDate >= now }.take(2),
            displayStreak = profile?.let { StreakCalculator.displayStreak(it.lastCheckInDay, it.streakDays, today, yesterday) } ?: 0,
            checkedInToday = profile?.lastCheckInDay == today,
            clashCount = TimetableLogic.clashes(slots).size
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    suspend fun checkIn(): GamResult = c.gamification.checkIn()

    suspend fun refresh(): List<Badge> = c.syncEverything()
}
