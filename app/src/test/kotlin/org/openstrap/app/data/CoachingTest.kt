package org.openstrap.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoachingTest {
    /** Feeds [zone] once a second for [seconds] from [from]; the cues that came, with when. */
    private fun Coach.run(from: Long, seconds: Int, zone: Int): List<Pair<Long, Cue>> =
        (0 until seconds).mapNotNull { i -> feed(from + i, zone, from + i)?.let { (from + i) to it } }

    @Test
    fun warmingUpBelowTheTargetIsNotADrift() {
        val c = Coach(WorkoutTarget.Zones(1, 2))
        assertEquals(emptyList<Pair<Long, Cue>>(), c.run(0, 120, 0))
        // Reaching the target from the warm-up does say so.
        assertEquals(listOf(15L to Cue.BackInTarget).map { (t, cue) -> 120 + t to cue }, c.run(120, 60, 1))
    }

    @Test
    fun driftingAboveBuzzesAfterFifteenSecondsAndBackInAgain() {
        val c = Coach(WorkoutTarget.Zones(1, 2))
        c.run(0, 60, 2)
        assertEquals(listOf(75L to Cue.AboveTarget), c.run(60, 60, 3))
        assertEquals(listOf(135L to Cue.BackInTarget), c.run(120, 60, 1))
    }

    @Test
    fun aShortSpikeNeverBuzzes() {
        val c = Coach(WorkoutTarget.Zones(1, 2))
        c.run(0, 60, 1)
        assertEquals(emptyList<Pair<Long, Cue>>(), c.run(60, 10, 4))
        assertEquals(emptyList<Pair<Long, Cue>>(), c.run(70, 60, 1))
    }

    @Test
    fun zoneCuesKeepFortySecondsApart() {
        val c = Coach(WorkoutTarget.Zones(1, 1))
        c.run(0, 30, 1)
        assertEquals(listOf(45L to Cue.BelowTarget), c.run(30, 20, 0))
        // Back in after 15 s at 65, but the last cue was at 45: it waits until 85.
        assertEquals(listOf(85L to Cue.BackInTarget), c.run(50, 60, 1))
    }

    @Test
    fun timeTargetHalfwayThenGoal() {
        val c = Coach(WorkoutTarget.Time(600))
        assertNull(c.feed(0, 1, 299))
        assertEquals(Cue.Halfway, c.feed(1, 1, 300))
        assertNull(c.feed(2, 1, 301))
        assertEquals(Cue.GoalReached, c.feed(3, 1, 600))
        assertNull(c.feed(4, 1, 700))
    }

    @Test
    fun targetCodesRoundTrip() {
        WorkoutTarget.presets.forEach { assertEquals(it, WorkoutTarget.parse(it.code)) }
        assertNull(WorkoutTarget.parse(null))
        assertNull(WorkoutTarget.parse("x:1"))
    }
}
