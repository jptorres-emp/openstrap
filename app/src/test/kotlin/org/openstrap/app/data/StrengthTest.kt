package org.openstrap.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StrengthTest {
    private fun hr(from: Long, to: Long, bpm: Int) = (from until to).map { it to bpm }

    @Test
    fun restEndsAtItsTime() {
        val rest = Strength.Rest(90, null)
        assertFalse(Strength.restOver(rest, 0, 90, hr(0, 89, 150), 89))
        assertTrue(Strength.restOver(rest, 0, 90, hr(0, 90, 150), 90))
    }

    @Test
    fun orEarlierOnceHeartRateHoldsBelowTheLevel() {
        val rest = Strength.Rest(90, 110)
        val h = hr(0, 30, 130) + hr(30, 60, 105)
        // Below since 30; held 5 s by 35.
        assertFalse(Strength.restOver(rest, 0, 90, h, 33))
        assertTrue(Strength.restOver(rest, 0, 90, h, 35))
    }

    @Test
    fun notOnHeartRateTooEarlyOrOnOneLowReading() {
        val rest = Strength.Rest(90, 110)
        assertFalse(Strength.restOver(rest, 0, 90, hr(0, 20, 100), 15))
        val spike = hr(0, 40, 130) + listOf(40L to 100) + hr(41, 50, 130)
        assertFalse(Strength.restOver(rest, 0, 90, spike, 45))
    }

    @Test
    fun theTimeLimitAlwaysAppliesAndExtends() {
        val rest = Strength.Rest(60, 110)
        // Heart rate never drops: rest still ends at its time, or when extended, at the new time.
        assertTrue(Strength.restOver(rest, 0, 60, hr(0, 60, 140), 60))
        assertFalse(Strength.restOver(rest, 0, 60 + Strength.EXTEND_S, hr(0, 70, 140), 70))
    }

    @Test
    fun restCodesRoundTrip() {
        assertEquals(Strength.Rest(90, 110), Strength.Rest.parse(Strength.Rest(90, 110).code))
        assertEquals(Strength.Rest(120, null), Strength.Rest.parse(Strength.Rest(120, null).code))
        assertNull(Strength.Rest.parse("x"))
    }
}
