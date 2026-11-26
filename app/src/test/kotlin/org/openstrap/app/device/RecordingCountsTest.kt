package org.openstrap.app.device

import org.junit.Assert.assertEquals
import org.junit.Test
import org.openstrap.app.data.db.AppWorkoutRound
import org.openstrap.app.data.db.AppWorkoutSet
import org.openstrap.app.testing.appWorkout

/** Sets and rounds are counted with the one under way, so set 1 and round 1 read 1 from their start. */
class RecordingCountsTest {
    private val w = appWorkout(start = 1_000, end = null)

    @Test
    fun noSetsYetCountsNone() {
        assertEquals(0, WorkoutState.Recording(w, 190).setsCounted)
    }

    @Test
    fun theSetUnderWayCounts() {
        val r = WorkoutState.Recording(w, 190, sets = listOf(AppWorkoutSet(1_000, 0, 1_010)))
        assertEquals(1, r.setsCounted)
    }

    @Test
    fun aRestAfterASetKeepsItsCount() {
        val r = WorkoutState.Recording(w, 190, sets = listOf(AppWorkoutSet(1_000, 0, 1_010, end = 1_040)), restUntil = 1_100)
        assertEquals(1, r.setsCounted)
    }

    @Test
    fun theRoundUnderWayCounts() {
        val r = WorkoutState.Recording(w, 190, rounds = listOf(AppWorkoutRound(1_000, 0, 1_005)))
        assertEquals(1, r.roundsCounted)
    }

    @Test
    fun finishedAndCurrentRoundsBothCount() {
        val r = WorkoutState.Recording(
            w, 190,
            rounds = listOf(AppWorkoutRound(1_000, 0, 1_005, workEnd = 1_025, restEnd = 1_035), AppWorkoutRound(1_000, 1, 1_035)),
        )
        assertEquals(2, r.roundsCounted)
    }
}
