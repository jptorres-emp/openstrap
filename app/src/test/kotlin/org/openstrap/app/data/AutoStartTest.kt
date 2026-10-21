package org.openstrap.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoStartTest {
    private val resting = 60
    private val max = 190
    /** 60 + 0.4 × 130 = 112. */
    private val line = AutoStart.threshold(resting, max)

    private fun hr(from: Long, to: Long, bpm: Int) = (from until to).map { it to bpm }

    @Test
    fun thresholdIsTheSuggestionRule() = assertEquals(112, line)

    @Test
    fun offersOnceTenMinutesOfEffortAreUnderWay() {
        val start = 600L
        val h = hr(0, start, 75) + hr(start, start + 9 * 60, 140)
        assertNull(AutoStart.offer(h, resting, max, 160, start + 9 * 60))
        val more = h + hr(start + 9 * 60, start + 10 * 60, 140)
        val o = AutoStart.offer(more, resting, max, 160, start + 10 * 60)!!
        // Recorded from where the effort began, not from the question.
        assertEquals(start, o.start)
        assertEquals(140, o.averageHeartRate)
        assertEquals(1, o.sportType)
    }

    @Test
    fun countsProbesBeforeStreamingTookOver() {
        // A 30 s probe at 0 found it, then streaming from 180 on: the effort began at the probe.
        val h = hr(0, 30, 130) + hr(180, 180 + 10 * 60, 130)
        assertEquals(0L, AutoStart.offer(h, resting, max, null, 180 + 10 * 60)!!.start)
    }

    @Test
    fun notForAnEffortThatHasEnded() {
        val h = hr(0, 15 * 60, 140) + hr(15 * 60, 25 * 60, 80)
        assertNull(AutoStart.offer(h, resting, max, null, 25 * 60))
    }

    @Test
    fun notForWhatCameBeforeTheLastWorkoutEnded() {
        val h = hr(0, 20 * 60, 140)
        assertNotNull(AutoStart.offer(h, resting, max, null, 20 * 60))
        assertNull(AutoStart.offer(h, resting, max, null, 20 * 60, notBefore = 15 * 60))
    }

    @Test
    fun sportFromCadence() {
        assertEquals(1, AutoStart.sport(165))
        assertEquals(3, AutoStart.sport(110))
        assertEquals(5, AutoStart.sport(10))
        assertEquals(5, AutoStart.sport(null))
    }

    @Test
    fun keepsStreamingWhileHeartRateOrFeetAreBusy() {
        assertTrue(AutoStart.keepStreaming(hr(0, 30, 130), line, null))
        assertFalse(AutoStart.keepStreaming(hr(0, 30, 80), line, 20))
        assertTrue(AutoStart.keepStreaming(emptyList(), line, 120))
        assertFalse(AutoStart.keepStreaming(emptyList(), line, null))
    }
}
