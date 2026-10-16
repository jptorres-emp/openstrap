package org.openstrap.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import org.openstrap.protocol.services.StepTotals

class LiveStepsTest {
    @Test
    fun plausibleTotalIsUsedDirectly() {
        assertEquals(3000, LiveSteps().today(2456, StepTotals(3000, 2200, 120)))
        assertEquals(2456, LiveSteps().today(2456, StepTotals(100, 70, 5)))
    }

    @Test
    fun offsetCounterAddsOnlyStepsSinceTheLastSync() {
        // Captured on firmware 3.25: 11,368 steps with 1,836 m while history held 2,456.
        val live = LiveSteps()
        assertEquals(2456, live.today(2456, StepTotals(11368, 1836, 151)))
        assertEquals(2479, live.today(2456, StepTotals(11391, 1853, 152)))
        // A sync brings those steps into history: re-anchor rather than count them twice.
        assertEquals(2480, live.today(2480, StepTotals(11392, 1854, 152)))
        assertEquals(2490, live.today(2480, StepTotals(11402, 1861, 152)))
    }

    @Test
    fun counterGoingBackwardsReanchors() {
        val live = LiveSteps()
        live.today(2456, StepTotals(11368, 1836, 151))
        assertEquals(2456, live.today(2456, StepTotals(9000, 10, 1)))
        assertEquals(2460, live.today(2456, StepTotals(9004, 13, 1)))
    }

    @Test
    fun noLiveReadingFallsBackToHistory() {
        assertEquals(2456, LiveSteps().today(2456, null))
    }
}
