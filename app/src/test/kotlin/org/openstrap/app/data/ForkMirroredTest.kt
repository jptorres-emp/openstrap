package org.openstrap.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openstrap.app.data.db.Workout
import org.openstrap.app.data.db.WorkoutHeartRate

/** The fork's fix for a workout started in Zepp's app, which the strap sums up over its first minutes only. */
class ForkMirroredTest {
    private val start = 1_761_000_000L

    @Test fun `a mirrored phone workout takes its whole track`() {
        // Started in Zepp's app: the summary covers 199 s at 132 bpm (load 24), the strap's track runs 53 minutes, the
        // last ten of them hard.
        val mirrored = Workout(start, start + 199, 52, 199, 33, 132, 159, 79, 2.4f, 2.4f, 24, null, null, subtype = ForkMirrored.SUBTYPE_MIRRORED)
        val track = (0 until 3180).map { t -> WorkoutHeartRate(start, start + t, if (t < 2580) 130 else 178) }
        val fixed = ForkMirrored.wholeTrack(mirrored, track, restingHr = 58f, maxHr = 185f)!!
        assertEquals(start + 3180 - 1 - Workouts.RECOVERY_TAIL_S, fixed.end)
        assertEquals((fixed.end - start).toInt(), fixed.exerciseSeconds)
        assertEquals(178, fixed.maxHeartRate)
        assertTrue("load ${fixed.trainingLoad}", fixed.trainingLoad!! > 24)
        assertTrue((fixed.aerobicTrainingEffect ?: 0f) > 2.4f)
        assertNull(fixed.anaerobicTrainingEffect)
        // A workout of the strap's own, or a mirrored one whose summary covers its track, stays as it is.
        assertNull(ForkMirrored.wholeTrack(mirrored.copy(subtype = 0), track, 58f, 185f))
        assertNull(ForkMirrored.wholeTrack(mirrored.copy(end = start + 3000 - Workouts.RECOVERY_TAIL_S), track, 58f, 185f))
        assertNull(ForkMirrored.wholeTrack(mirrored, emptyList(), 58f, 185f))
    }
}
