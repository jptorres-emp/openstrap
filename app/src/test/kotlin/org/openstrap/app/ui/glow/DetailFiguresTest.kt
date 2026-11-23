package org.openstrap.app.ui.glow

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.openstrap.app.data.DayStat
import org.openstrap.app.presentation.Range

/** The figures under a detail's chart agree with its headline: "vs normal" is the shown day's value against the normal. */
class DetailFiguresTest {
    @Test fun vsNormalIsTheShownValueMinusTheNormal() {
        // Resting heart rate 60 against a normal of 53 is +7, not the 7 night average's difference.
        assertEquals(7f, vsNormal(60f, 53f)!!, 1e-6f)
        assertEquals(-11f, vsNormal(46f, 57f)!!, 1e-6f)
        assertEquals(0f, vsNormal(53f, 53f)!!, 1e-6f)
    }

    @Test fun aZeroDifferenceHasNoSign() {
        val show = { v: Float -> "${v.toInt()} bpm" }
        assertEquals("0 bpm", signedFigure(0f, show))
        assertEquals("0 bpm", signedFigure(-0f, show))
        assertEquals("0 bpm", signedFigure(0.2f, show))
        assertEquals("+7 bpm", signedFigure(7f, show))
        assertEquals("−11 bpm", signedFigure(-11f, show))
    }

    @Test fun vsNormalWaitsForBothSides() {
        assertNull(vsNormal(null, 53f))
        assertNull(vsNormal(60f, null))
    }

    @Test fun rangeStatsAlignsDaysWithGaps() {
        val start = LocalDate.of(2026, 11, 7)
        val end = LocalDate.of(2026, 11, 13)
        val stats = listOf(DayStat(LocalDate.of(2026, 11, 9), 70f, 50f, 120f, 100))
        val days = rangeStats(Range.Week, stats, start, end)
        assertEquals(7, days.size)
        assertEquals(50f, days[2]!!.min, 0f)
        assertNull(days[0])
    }
}
