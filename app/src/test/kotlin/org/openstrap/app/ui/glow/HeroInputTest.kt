package org.openstrap.app.ui.glow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeroInputTest {
    @Test fun countIsHeldToItsRange() {
        assertEquals(12, typedCount("12", 1..30))
        assertEquals(30, typedCount("99", 1..30))
        assertEquals(1, typedCount("0", 1..30))
        assertNull(typedCount("", 1..30))
    }

    @Test fun distanceRoundsToATenthAndTakesACommaPoint() {
        assertEquals(12_500, typedMeters("12.5", 300))
        assertEquals(12_500, typedMeters("12,5", 300))
        assertEquals(5_000, typedMeters("5", 300))
        assertEquals(10_300, typedMeters("10.26", 300))
        assertEquals(300_000, typedMeters("900", 300))
        assertNull(typedMeters("0", 300))
        assertNull(typedMeters(".", 300))
    }

    @Test fun minutesBecomeSecondsUpToTheLongestGoal() {
        assertEquals(75 * 60L, typedSeconds("75", 10))
        assertEquals(600 * 60L, typedSeconds("9999", 10))
        assertNull(typedSeconds("0", 10))
        assertNull(typedSeconds("1.5", 10))
    }
}

class RulerStickyTest {
    private val labels = setOf(1, 5, 9)

    @Test fun aLabelHoldsTheRulerNearIt() {
        assertEquals(5f, sticky(5f, labels), 0.001f)
        // A third of a step off still shows close to the tick, and rounds onto it.
        assertEquals(5, kotlin.math.round(sticky(5.8f, labels)).toInt())
        assertEquals(5, kotlin.math.round(sticky(4.2f, labels)).toInt())
    }

    @Test fun fartherOutTheRulerFollowsTheFinger() {
        assertEquals(7f, sticky(7f, labels), 0.001f)
        assertEquals(3f, sticky(3f, labels), 0.001f)
    }

    @Test fun noLabelsMeansNoPull() {
        assertEquals(2.3f, sticky(2.3f, emptySet()), 0.001f)
    }
}
