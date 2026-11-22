package org.openstrap.app.ui.glow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openstrap.app.data.db.SleepSession
import org.openstrap.app.data.db.SleepStage
import org.openstrap.app.presentation.StageSpan

/** One set of figures for a night, never more than the night is long, however many stage rows were stored for it. */
class SleepNightTest {
    private val start = 1_000_000L
    private val end = start + 8 * 3600
    private val session = SleepSession(start, end, score = 80, awakeMinutes = 0, wakeCount = 4)

    /** A night's stage rows from [from] to [to] in 30-minute light rows, one awake row of 5 minutes every 2 hours. */
    private fun rows(from: Long, to: Long): List<StageSpan> = buildList {
        var t = from
        var i = 0
        while (t < to) {
            val len = if (i % 4 == 3) 300L else 1800L
            add(StageSpan(t, minOf(to, t + len), if (i % 4 == 3) SleepStage.AWAKE else SleepStage.LIGHT))
            t += len
            i++
        }
    }

    @Test
    fun `two overlapping sets of rows add up to no more than the night`() {
        // The same night stored twice with different edges, as a reseeded database leaves it.
        val twice = rows(start, end) + rows(start + 600, end + 1200)
        val night = shownNight(session, twice)!!
        assertTrue(night.asleep + night.awake <= end - start)
        assertEquals(night.asleep, night.breakdown!!.asleepSeconds)
        night.spans.zipWithNext().forEach { (a, b) -> assertTrue(a.end <= b.start) }
        assertTrue(night.spans.first().start >= start && night.spans.last().end <= end)
    }

    @Test
    fun `rows outside the night are cut and rows wholly outside go`() {
        val clean = NightStages.clean(
            listOf(StageSpan(start - 3600, start + 600, SleepStage.LIGHT), StageSpan(end + 10, end + 900, SleepStage.DEEP)), start, end,
        )
        assertEquals(listOf(StageSpan(start, start + 600, SleepStage.LIGHT)), clean)
    }

    @Test
    fun `where two rows cover the same time the earlier keeps it`() {
        val clean = NightStages.clean(
            listOf(StageSpan(start, start + 1000, SleepStage.DEEP), StageSpan(start + 500, start + 1500, SleepStage.REM)), start, end,
        )
        assertEquals(listOf(StageSpan(start, start + 1000, SleepStage.DEEP), StageSpan(start + 1000, start + 1500, SleepStage.REM)), clean)
    }

    @Test
    fun `awake time and wake count come from the rows the graph draws`() {
        val night = shownNight(session, rows(start, end))!!
        val awakeRows = night.spans.filter { it.stage == SleepStage.AWAKE }
        assertEquals(awakeRows.sumOf { it.end - it.start }, night.awake)
        assertEquals(awakeRows.size, night.session.wakeCount)
        assertEquals((night.awake / 60).toInt(), night.session.awakeMinutes)
    }

    @Test
    fun `a night with no awake rows says no awake time and no wake-ups`() {
        val night = shownNight(session, listOf(StageSpan(start, end, SleepStage.LIGHT)))!!
        assertEquals(0L, night.awake)
        assertEquals(0, night.session.wakeCount)
        assertEquals(end - start, night.asleep)
    }

    @Test
    fun `joined runs of one stage are one span`() {
        val joined = NightStages.join(listOf(StageSpan(0, 10, 1), StageSpan(10, 20, 1), StageSpan(20, 30, 2), StageSpan(30, 40, 1)))
        assertEquals(listOf(StageSpan(0, 20, 1), StageSpan(20, 30, 2), StageSpan(30, 40, 1)), joined)
    }

    @Test
    fun `with no rows the strap's figures are held to the length of the night`() {
        val long = session.copy(deepMinutes = 600, lightMinutes = 600, remMinutes = 100, awakeMinutes = 30)
        val night = shownNight(long, emptyList())!!
        assertEquals(end - start, night.asleep)
        assertEquals(0L, night.awake)
        assertNull(night.breakdown)
    }
}
