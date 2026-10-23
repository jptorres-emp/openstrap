package org.openstrap.app.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.openstrap.app.data.db.Workout
import org.openstrap.protocol.history.WorkoutSummary
import java.time.ZoneOffset

class WorkoutRowTest {
    private fun summary(subtype: Int?) = WorkoutSummary(
        1_760_000_000L, 1, subtype, 2400, 2280, 300, 140, 170, 90, 3.1f, 0.4f, 80, 45, 4000,
    )

    /** The summary's Type.subtype reaches the stored row, so auto-detected workouts can be flagged (codemap 05 §8). */
    @Test
    fun keepsSubtype() {
        val row = workoutRow(summary(1))
        assertEquals(1, row.subtype)
        assertEquals(1_760_000_000L + 2280, row.end)
        assertEquals(6, workoutRow(summary(6)).subtype)
        assertNull(workoutRow(summary(null)).subtype)
    }

    @Test
    fun notificationTitle() {
        val w = Workout(36_120, 38_400, 1, 2280, null, null, null, null, null, null, null, null, null, subtype = 1)
        assertEquals("Outdoor run detected, 10:02–10:40", AutoDetectNotifier.title(w, ZoneOffset.UTC))
    }
}
