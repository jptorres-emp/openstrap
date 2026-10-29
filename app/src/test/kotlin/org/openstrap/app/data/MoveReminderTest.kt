package org.openstrap.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class MoveReminderTest {
    private val on = MoveSettings(on = true, startMinute = 9 * 60, endMinute = 18 * 60, everyMinutes = 60, weekdaysOnly = true)
    /** A Wednesday. */
    private val wednesday = LocalDateTime.of(2026, 10, 28, 11, 0)
    private val t = 1_800_000_000L

    private fun due(
        settings: MoveSettings = on, now: LocalDateTime = wednesday, steps: Int? = 20, lastAt: Long? = null,
        worn: Boolean? = true, recording: Boolean = false,
    ) = MoveReminder.due(settings, now, t, steps, lastAt, worn, recording)

    @Test fun `an hour sitting in working hours buzzes, once a stretch`() {
        assertTrue(due())
        // A nudge 30 minutes ago: not yet; an hour ago: again.
        assertFalse(due(lastAt = t - 30 * 60))
        assertTrue(due(lastAt = t - 60 * 60))
    }

    @Test fun `walking, a workout, the strap off or the switch off keep it quiet`() {
        assertFalse(due(steps = MoveReminder.MIN_STEPS))
        assertFalse(due(recording = true))
        assertFalse(due(worn = false))
        // Not known whether worn (no reading yet): still on.
        assertTrue(due(worn = null))
        assertFalse(due(settings = on.copy(on = false)))
        // Steps not watched for the whole stretch yet.
        assertFalse(due(steps = null))
    }

    @Test fun `only in the chosen hours and days`() {
        // The first nudge a stretch after the day starts; none from the end on.
        assertFalse(due(now = wednesday.withHour(9).withMinute(30)))
        assertTrue(due(now = wednesday.withHour(10).withMinute(0)))
        assertFalse(due(now = wednesday.withHour(18).withMinute(0)))
        val saturday = wednesday.plusDays(3)
        assertFalse(due(now = saturday))
        assertTrue(due(settings = on.copy(weekdaysOnly = false), now = saturday))
    }

    @Test fun `steps over a stretch come from the strap's running totals`() {
        val samples = listOf(t - 3600L to 5000, t - 1800L to 5040, t - 600L to 5060, t to 5070)
        assertEquals(70, MoveReminder.stepsOver(samples, t, 60))
        assertEquals(30, MoveReminder.stepsOver(samples, t, 30))
        // Not watched for that long.
        assertNull(MoveReminder.stepsOver(samples, t, 90))
        // The total starts again at midnight: only the steps after it count.
        val overMidnight = listOf(t - 3600L to 9000, t - 1800L to 9050, t - 600L to 10, t to 40)
        assertEquals(90, MoveReminder.stepsOver(overMidnight, t, 60))
    }
}
