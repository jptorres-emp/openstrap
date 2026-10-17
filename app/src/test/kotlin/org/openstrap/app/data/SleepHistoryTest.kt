package org.openstrap.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.openstrap.app.data.db.SleepSession
import java.time.LocalDate
import java.time.ZoneOffset

class SleepHistoryTest {
    private val zone = ZoneOffset.UTC
    private val end = LocalDate.of(2026, 10, 17)

    /** A night ending on [end] minus [nightsAgo], from [bed] to [wake] (minutes past midnight; bed may be negative). */
    private fun night(nightsAgo: Long, bed: Int, wake: Int, nap: Boolean = false): SleepSession {
        val midnight = end.minusDays(nightsAgo).atStartOfDay(zone).toEpochSecond()
        return SleepSession(start = midnight + bed * 60L, end = midnight + wake * 60L, nap = nap)
    }

    @Test
    fun sleepBelongsToTheDayItEnds() {
        assertEquals(end, SleepHistory.nightOf(night(0, -60, 7 * 60).end, zone))
        // A sleep ending at 19:00 counts towards the next night.
        assertEquals(end.plusDays(1), SleepHistory.nightOf(night(0, 13 * 60, 19 * 60).end, zone))
    }

    @Test
    fun mainSleepIsTheLongestNonNap() {
        val long = night(0, -60, 7 * 60)
        val short = night(0, 8 * 60, 9 * 60)
        val nap = night(0, -120, 9 * 60, nap = true)
        assertEquals(mapOf(end to long), SleepHistory.mainSleeps(listOf(short, nap, long), zone))
    }

    @Test
    fun asleepUsesStageTotals() {
        val s = night(0, -60, 7 * 60).copy(deepMinutes = 60, lightMinutes = 240, remMinutes = 90, awakeMinutes = 30)
        assertEquals(390 * 60L, SleepHistory.asleepSeconds(s))
        assertEquals(8 * 3600L, SleepHistory.asleepSeconds(night(0, -60, 7 * 60)))
    }

    @Test
    fun regularityNeedsFiveNights() {
        val four = (0L..3L).associate { end.minusDays(it) to night(it, -30, 7 * 60) }
        assertNull(SleepHistory.regularity(four, end, zone))
        val five = (0L..4L).associate { end.minusDays(it) to night(it, -30, 7 * 60) }
        assertEquals(5, SleepHistory.regularity(five, end, zone)!!.nights)
    }

    @Test
    fun identicalNightsScoreFullAcrossMidnight() {
        // 23:30 to 07:00 every night: bedtime averages before midnight without wrapping.
        val nights = (0L..6L).associate { end.minusDays(it) to night(it, -30, 7 * 60) }
        val r = SleepHistory.regularity(nights, end, zone)!!
        assertEquals(100, r.score)
        assertEquals(23 * 60 + 30, SleepHistory.clockMinutes(r.bedtime))
        assertEquals(7 * 60, r.wake)
        assertEquals(SleepHistory.Band.VeryRegular, r.band)
    }

    @Test
    fun bedtimesEitherSideOfMidnightAverageNearMidnight() {
        val nights = (0L..5L).associate { end.minusDays(it) to night(it, if (it % 2 == 0L) -30 else 30, 7 * 60) }
        val r = SleepHistory.regularity(nights, end, zone)!!
        assertEquals(0, SleepHistory.clockMinutes(r.bedtime))
        // Bedtime SD 30 min, wake SD 0: spread 15 min -> 88.
        assertEquals(88, r.score)
        assertEquals(SleepHistory.Band.Regular, r.band)
    }

    @Test
    fun bandsFollowZepp() {
        assertEquals(SleepHistory.Band.VeryRegular, SleepHistory.Band.of(90))
        assertEquals(SleepHistory.Band.Regular, SleepHistory.Band.of(89))
        assertEquals(SleepHistory.Band.FairlyRegular, SleepHistory.Band.of(60))
        assertEquals(SleepHistory.Band.Irregular, SleepHistory.Band.of(59))
    }
}
