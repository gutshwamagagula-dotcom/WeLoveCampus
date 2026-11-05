package com.welovecampus.app

import com.welovecampus.app.data.local.AssignmentEntity
import com.welovecampus.app.data.local.TimetableSlotEntity
import com.welovecampus.app.util.AssignmentStatus
import com.welovecampus.app.util.Priority
import com.welovecampus.app.util.PriorityCalculator
import com.welovecampus.app.util.StreakCalculator
import com.welovecampus.app.util.TimetableLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** Unit tests for the acceptance criteria that are pure logic. Run: Gradle > app > Tasks > verification > test */
class CampusLogicTest {

    private val now = 1_800_000_000_000L
    private val hour = TimeUnit.HOURS.toMillis(1)

    // Requirement 2.3 - due within 48 hours is flagged HIGH automatically
    @Test fun dueIn47Hours_isHigh() = assertEquals(Priority.HIGH, PriorityCalculator.priorityFor(now + 47 * hour, now))
    @Test fun dueIn48Hours_isHigh() = assertEquals(Priority.HIGH, PriorityCalculator.priorityFor(now + 48 * hour, now))
    @Test fun dueIn3Days_isMedium() = assertEquals(Priority.MEDIUM, PriorityCalculator.priorityFor(now + 72 * hour, now))
    @Test fun dueIn10Days_isLow() = assertEquals(Priority.LOW, PriorityCalculator.priorityFor(now + 240 * hour, now))

    @Test fun status_open_submitted_missing() {
        val a = AssignmentEntity("1", "M", "t", "", dueDate = now + hour)
        assertEquals(AssignmentStatus.OPEN, PriorityCalculator.statusFor(a, now))
        assertEquals(AssignmentStatus.SUBMITTED, PriorityCalculator.statusFor(a.copy(submitted = true), now))
        assertEquals(AssignmentStatus.MISSING, PriorityCalculator.statusFor(a.copy(dueDate = now - hour), now))
    }

    // Requirement 2.11 - streak resets after a missed day
    @Test fun streak_continuesFromYesterday() = assertEquals(5, StreakCalculator.newStreak("2026-11-03", 4, "2026-11-04", "2026-11-03"))
    @Test fun streak_resetsAfterMissedDay() = assertEquals(1, StreakCalculator.newStreak("2026-11-01", 9, "2026-11-04", "2026-11-03"))
    @Test fun streak_sameDayNoChange() = assertEquals(4, StreakCalculator.newStreak("2026-11-04", 4, "2026-11-04", "2026-11-03"))
    @Test fun streak_displayZeroWhenBroken() = assertEquals(0, StreakCalculator.displayStreak("2026-10-30", 7, "2026-11-04", "2026-11-03"))

    private fun slot(id: String, day: Int, start: String, end: String) =
        TimetableSlotEntity(id, "MOD$id", "Module", day, start, end, "Room", "Lecturer")

    @Test fun nextClass_laterToday() {
        // Wednesday 09:30
        val cal = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 4, 9, 30) }
        val slots = listOf(slot("a", 3, "08:00", "09:00"), slot("b", 3, "10:00", "11:00"), slot("c", 4, "08:00", "09:00"))
        assertEquals("b", TimetableLogic.nextClass(slots, cal)?.slotId)
    }

    @Test fun nextClass_wrapsToNextWeek() {
        // Friday 18:00 -> Monday's first class
        val cal = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 6, 18, 0) }
        val slots = listOf(slot("mon", 1, "08:00", "09:00"), slot("fri", 5, "08:00", "09:00"))
        assertEquals("mon", TimetableLogic.nextClass(slots, cal)?.slotId)
    }

    @Test fun nextClass_emptyTimetable() = assertNull(TimetableLogic.nextClass(emptyList()))

    @Test fun clashes_detectsOverlap() {
        val slots = listOf(slot("a", 1, "08:00", "10:00"), slot("b", 1, "09:00", "11:00"), slot("c", 1, "11:00", "12:00"))
        assertEquals(1, TimetableLogic.clashes(slots).size)
    }
}
