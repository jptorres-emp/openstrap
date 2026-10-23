package org.openstrap.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.openstrap.app.data.db.SportEffectRow
import org.openstrap.app.data.db.Workout
import org.openstrap.app.data.db.WorkoutHeartRate

class WorkoutsTest {
    private val start = 1_000_000L
    private fun workout(exercise: Int = 600) = Workout(start, start + exercise, 223, exercise, 60, 120, 140, 90, 1f, 0.1f, 6, null, null)

    /** 20 min at 140 bpm, then three minutes falling 0.5 bpm a second. */
    private val hr = (0 until 1200).map { WorkoutHeartRate(start, start + it, 140) } +
        (0..180).map { WorkoutHeartRate(start, start + 1200 + it, 140 - it / 2) }

    @Test
    fun endComesFromTheRecoveryTail() {
        val a = Workouts.analyse(workout(), hr, emptyList(), 190)
        assertEquals(start + 1200, a.end)
        assertEquals(30, a.recovery60)
        assertEquals(60, a.recovery120)
        assertEquals(140, a.endHeartRate)
        org.junit.Assert.assertTrue(a.recoveryIsMeaningful)
        // 140/190 = 73 %: all workout time in zone 3 (index 2).
        assertEquals(1200, a.zoneSeconds[2])
    }

    @Test
    fun sportEffectSetsEndAndMaxHr() {
        val effect = SportEffectRow(start + 1100, 127, null, null, null, null, null, null, 160)
        val a = Workouts.analyse(workout(), hr, listOf(effect), 190)
        assertEquals(start + 1100, a.end)
        assertEquals(160, a.maxHeartRate)
        assertEquals(127, a.effect?.recoveryMinutes)
    }

    @Test
    fun noHeartRateFallsBackToExerciseTime() {
        val a = Workouts.analyse(workout(600), emptyList(), emptyList(), 190)
        assertEquals(start + 600, a.end)
        assertNull(a.recovery60)
        assertEquals(0, a.zoneSeconds.sum())
    }

    @Test
    fun zones() {
        assertNull(Workouts.zoneOf(90, 190))
        assertEquals(0, Workouts.zoneOf(100, 190))
        assertEquals(4, Workouts.zoneOf(190, 190))
        assertEquals(4, Workouts.zoneOf(200, 190))
    }

    /** Only a fresh, auto-detected strap workout gets the "detected" notification (codemap 05 §8). */
    @Test
    fun autoDetectNotification() {
        val auto = workout().copy(subtype = Workouts.SUBTYPE_AUTO_DETECTED)
        val justEnded = auto.end + 600
        org.junit.Assert.assertTrue(Workouts.notifyAutoDetected(auto, storedBefore = false, now = justEnded))
        // Already stored: the strap re-sent it, so no second notification.
        org.junit.Assert.assertFalse(Workouts.notifyAutoDetected(auto, storedBefore = true, now = justEnded))
        // Backlog from a first sync, more than a day old.
        org.junit.Assert.assertFalse(Workouts.notifyAutoDetected(auto, storedBefore = false, now = auto.end + Workouts.AUTO_DETECT_NOTIFY_WINDOW_S + 1))
        // Normal (0), mirrored phone workout (6) and rows from before the subtype was stored (null).
        listOf(0, 6, null).forEach {
            org.junit.Assert.assertFalse(Workouts.notifyAutoDetected(auto.copy(subtype = it), storedBefore = false, now = justEnded))
        }
    }

    @Test
    fun startedByYouLeavesOutWhatTheStrapLoggedByItself() {
        val run = workout().copy(sportType = 1)
        val auto = run.copy(subtype = Workouts.SUBTYPE_AUTO_DETECTED)
        val activity = run.copy(sportType = Workouts.GENERIC_ACTIVITY)
        org.junit.Assert.assertTrue(Workouts.analyse(run, emptyList(), emptyList(), 190).startedByYou)
        org.junit.Assert.assertFalse(Workouts.analyse(auto, emptyList(), emptyList(), 190).startedByYou)
        org.junit.Assert.assertFalse(Workouts.analyse(activity, emptyList(), emptyList(), 190).startedByYou)
    }

    @org.junit.Test
    fun autoDetectedLabelOnlyOnStrapWorkouts() {
        val auto = workout().copy(subtype = Workouts.SUBTYPE_AUTO_DETECTED)
        org.junit.Assert.assertTrue(Workouts.analyse(auto, emptyList(), emptyList(), 190).autoDetected)
        org.junit.Assert.assertFalse(Workouts.analyse(workout(), emptyList(), emptyList(), 190).autoDetected)
    }
}
