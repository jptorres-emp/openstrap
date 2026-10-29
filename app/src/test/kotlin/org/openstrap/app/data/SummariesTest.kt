package org.openstrap.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.openstrap.app.data.db.ActivityMinute
import org.openstrap.app.data.db.HeartRateSample
import org.openstrap.app.data.db.SleepStage

class SummariesTest {
    private fun minute(t: Long, steps: Int, hr: Int? = null) = ActivityMinute(t, steps, 0, 0, hr)

    @Test
    fun hourlyStepsBucketsByHour() {
        val day = 1_000_000L
        val buckets = Summaries.hourlySteps(
            listOf(minute(day, 10), minute(day + 59 * 60, 5), minute(day + 3600, 7), minute(day + 25 * 3600, 99)),
            day,
        )
        assertEquals(24, buckets.size)
        assertEquals(15f, buckets[0])
        assertEquals(7f, buckets[1])
        assertEquals(22, Summaries.totalSteps(listOf(minute(day, 10), minute(day + 60, 12))))
    }

    @Test
    fun heartRateSeriesPrefersDedicatedSamplesAndDropsJunk() {
        val series = Summaries.heartRateSeries(
            hr = listOf(HeartRateSample(60, 70)),
            minutes = listOf(minute(0, 0, 65), minute(60, 0, 80), minute(120, 0, 255)),
        )
        assertEquals(listOf(0L to 65, 60L to 70), series)
    }

    @Test
    fun restingHrIsLowestSustainedAverage() {
        val series = (0 until 120).map { i -> i * 60L to if (i in 40..80) 52 else 75 }
        assertEquals(52, Summaries.estimateRestingHr(series))
        assertNull(Summaries.estimateRestingHr(listOf(0L to 60, 60L to 61)))
    }

    @Test
    fun sleepBreakdownSeparatesAwake() {
        val stages = listOf(
            SleepStage(0, 600, 0, SleepStage.LIGHT),
            SleepStage(600, 900, 0, SleepStage.DEEP),
            SleepStage(900, 1000, 0, SleepStage.AWAKE),
        )
        val b = Summaries.sleepBreakdown(stages)
        assertEquals(900L, b.asleepSeconds)
        assertEquals(100L, b.awakeSeconds)
        assertEquals(300L, b.byStage[SleepStage.DEEP])
    }
}

class StepSumsTest {
    private fun minute(t: Long, steps: Int) = ActivityMinute(t, steps, 0, 0, null)

    @Test fun `a sentinel minute counts as no steps, in the day and in its hour`() {
        val minutes = listOf(minute(0, 90), minute(60, 255), minute(120, 251), minute(180, 40))
        assertEquals(130, Summaries.totalSteps(minutes))
        assertEquals(130f, Summaries.hourlySteps(minutes, 0)[0], 0f)
    }

    @Test fun `today's bar takes Today's count when the synced minutes are behind`() {
        val today = java.time.LocalDate.of(2026, 10, 29)
        val days = listOf(DayStat(today.minusDays(1), 9000f, 0f, 120f, 900), DayStat(today, 4000f, 0f, 110f, 500))
        assertEquals(6500f, Summaries.withToday(days, today, 6500f).last().value, 0f)
        // Synced minutes already ahead: kept.
        assertEquals(4000f, Summaries.withToday(days, today, 3000f).last().value, 0f)
        // No row for today yet: added, in date order.
        val added = Summaries.withToday(days.dropLast(1), today, 1200f)
        assertEquals(listOf(today.minusDays(1), today), added.map { it.date })
        assertEquals(1200f, added.last().value, 0f)
    }
}

class HeartRangeTest {
    /** One reading a minute from t = 0, as the strap's per-second readings are kept. */
    private fun minutes(vararg bpm: Int) = bpm.mapIndexed { i, v -> i * 60L to v }

    @Test fun `a workout's peak survives that the 5-minute averages flatten`() {
        val series = minutes(*IntArray(10) { 110 }, 150, 172, 181, 176, 160, *IntArray(10) { 115 })
        assertEquals(110..181, Summaries.heartRange(series))
        // The chart's 5-minute average of the same stretch tops out well under the peak.
        val chartTop = Summaries.bucketAverage(series).maxOf { it.second }
        assertEquals(true, chartTop < 170)
    }

    @Test fun `a lone spike or dip is an artefact and left out`() {
        val series = minutes(62, 64, 63, 210, 65, 64, 35, 63, 62)
        assertEquals(62..65, Summaries.heartRange(series))
        assertEquals(0L to 62, Summaries.lowestMinute(series))
    }

    @Test fun `the strap's per-second maximum raises the high, unless it's far above every minute`() {
        val series = minutes(140, 146, 156, 166, 176, 178, 174, 168)
        assertEquals(140..188, Summaries.heartRange(series, strapMax = 188))
        assertEquals(140..178, Summaries.heartRange(series, strapMax = 215))
        assertEquals(140..178, Summaries.heartRange(series, strapMax = 170))
    }

    @Test fun `no readings, no range`() {
        assertNull(Summaries.heartRange(emptyList(), strapMax = 180))
        assertNull(Summaries.lowestMinute(emptyList()))
    }
}

class BucketTest {
    @Test
    fun averagesIntoFiveMinuteBuckets() {
        val s = listOf(0L to 60, 60L to 70, 299L to 80, 300L to 90)
        assertEquals(listOf(0L to 70, 300L to 90), Summaries.bucketAverage(s))
    }
}
