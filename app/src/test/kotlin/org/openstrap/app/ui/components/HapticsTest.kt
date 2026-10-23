package org.openstrap.app.ui.components

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticsTest {
    @Test fun `throttle lets a tick through at most every interval`() {
        val t = TickThrottle(40)
        assertTrue(t.allow(1_000))
        assertFalse(t.allow(1_010))
        assertFalse(t.allow(1_039))
        assertTrue(t.allow(1_040))
        // A blocked tick doesn't restart the wait.
        assertFalse(t.allow(1_060))
        assertTrue(t.allow(1_080))
    }

    @Test fun `the 3_6 table`() {
        assertEquals(HapticFeedbackType.SegmentFrequentTick, HapticEvent.ScrubStep.feedbackType())
        listOf(HapticEvent.ScrubMark, HapticEvent.ZoneChange, HapticEvent.SetDone, HapticEvent.RangeMark)
            .forEach { assertEquals(HapticFeedbackType.SegmentTick, it.feedbackType()) }
        listOf(HapticEvent.WorkoutStart, HapticEvent.WorkoutEnd, HapticEvent.PairingDone)
            .forEach { assertEquals(HapticFeedbackType.Confirm, it.feedbackType()) }
        assertEquals(HapticFeedbackType.Reject, HapticEvent.SyncFailed.feedbackType())
    }

    @Test fun `only scrub steps are throttled`() {
        val played = mutableListOf<HapticFeedbackType>()
        var now = 0L
        val haptics = Haptics(object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { played += hapticFeedbackType }
        }) { now }
        haptics.perform(HapticEvent.ScrubStep)
        now = 20; haptics.perform(HapticEvent.ScrubStep)
        haptics.perform(HapticEvent.ScrubMark)
        haptics.perform(HapticEvent.ScrubMark)
        now = 45; haptics.perform(HapticEvent.ScrubStep)
        assertEquals(
            listOf(HapticFeedbackType.SegmentFrequentTick, HapticFeedbackType.SegmentTick, HapticFeedbackType.SegmentTick, HapticFeedbackType.SegmentFrequentTick),
            played,
        )
    }
}
