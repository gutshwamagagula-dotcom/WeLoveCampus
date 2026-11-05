package com.welovecampus.app.util

import com.welovecampus.app.data.local.AssignmentEntity
import com.welovecampus.app.data.local.TimetableSlotEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/*
 * Pure business rules with no Android dependencies, so they are easy to unit test
 * (see app/src/test/.../CampusLogicTest.kt).
 */

enum class Priority { HIGH, MEDIUM, LOW }

enum class AssignmentStatus { OPEN, SUBMITTED, MISSING }

object PriorityCalculator {
    private val HOURS_48 = TimeUnit.HOURS.toMillis(48)
    private val DAYS_7 = TimeUnit.DAYS.toMillis(7)

    /**
     * Requirement 2.3: due within 48 hours = HIGH (automatically),
     * within 7 days = MEDIUM, anything later = LOW.
     */
    fun priorityFor(dueDate: Long, now: Long = System.currentTimeMillis()): Priority {
        val remaining = dueDate - now
        return when {
            remaining <= HOURS_48 -> Priority.HIGH
            remaining <= DAYS_7 -> Priority.MEDIUM
            else -> Priority.LOW
        }
    }

    /** Submitted beats everything; otherwise past the due date = MISSING. */
    fun statusFor(a: AssignmentEntity, now: Long = System.currentTimeMillis()): AssignmentStatus = when {
        a.submitted -> AssignmentStatus.SUBMITTED
        a.dueDate < now -> AssignmentStatus.MISSING
        else -> AssignmentStatus.OPEN
    }
}

object StreakCalculator {
    /**
     * Requirement 2.11: the streak continues only if the last check-in was yesterday.
     * Returns the new streak value after checking in today.
     */
    fun newStreak(lastCheckInDay: String, currentStreak: Int, today: String, yesterday: String): Int = when (lastCheckInDay) {
        today -> currentStreak                 // already checked in today - no change
        yesterday -> currentStreak + 1         // consecutive day
        else -> 1                              // missed a day (or first ever) - reset
    }

    /** What to SHOW: a streak whose last check-in was before yesterday is already broken. */
    fun displayStreak(lastCheckInDay: String, storedStreak: Int, today: String, yesterday: String): Int =
        if (lastCheckInDay == today || lastCheckInDay == yesterday) storedStreak else 0
}

object TimetableLogic {
    /** Calendar.DAY_OF_WEEK -> our 1..7 (Mon..Sun). */
    fun todayIndex(cal: Calendar = Calendar.getInstance()): Int =
        ((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1

    /** Finds the next class from now, wrapping around to next week. */
    fun nextClass(slots: List<TimetableSlotEntity>, cal: Calendar = Calendar.getInstance()): TimetableSlotEntity? {
        if (slots.isEmpty()) return null
        val today = todayIndex(cal)
        val nowHm = String.format(Locale.US, "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        val sorted = slots.sortedWith(compareBy({ it.dayOfWeek }, { it.startTime }))
        // later today
        sorted.firstOrNull { it.dayOfWeek == today && it.startTime > nowHm }?.let { return it }
        // later this week
        sorted.firstOrNull { it.dayOfWeek > today }?.let { return it }
        // wrap to next week
        return sorted.first()
    }

    /** Two classes on the same day whose times overlap = a clash. */
    fun clashes(slots: List<TimetableSlotEntity>): List<Pair<TimetableSlotEntity, TimetableSlotEntity>> {
        val result = mutableListOf<Pair<TimetableSlotEntity, TimetableSlotEntity>>()
        slots.groupBy { it.dayOfWeek }.values.forEach { day ->
            val s = day.sortedBy { it.startTime }
            for (i in s.indices) for (j in i + 1 until s.size) {
                if (s[j].startTime < s[i].endTime) result += s[i] to s[j]
            }
        }
        return result
    }
}

object DateUtils {
    private const val DAY_PATTERN = "yyyy-MM-dd"

    fun dayKey(time: Long = System.currentTimeMillis()): String =
        SimpleDateFormat(DAY_PATTERN, Locale.US).format(Date(time))

    fun yesterdayKey(): String {
        val c = Calendar.getInstance(); c.add(Calendar.DAY_OF_YEAR, -1)
        return dayKey(c.timeInMillis)
    }

    fun formatDateTime(time: Long): String =
        SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault()).format(Date(time))

    fun formatDate(time: Long): String =
        SimpleDateFormat("EEE d MMM yyyy", Locale.getDefault()).format(Date(time))

    fun formatTime(time: Long): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(time))

    fun isSameDay(a: Long, b: Long): Boolean = dayKey(a) == dayKey(b)
}
