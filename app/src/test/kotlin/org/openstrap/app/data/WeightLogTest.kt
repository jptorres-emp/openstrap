package org.openstrap.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.openstrap.app.data.db.WeightEntry
import org.openstrap.app.ui.device.weightChange
import org.openstrap.app.ui.device.weightLine
import java.time.LocalDate

class WeightLogTest {
    private val today = LocalDate.of(2026, 11, 21)
    private fun w(daysAgo: Long, kg: Float) = WeightEntry(today.minusDays(daysAgo), kg, daysAgo)

    @Test fun `the 7-day average and the 7 days before, with the last weight`() {
        val entries = listOf(w(13, 94f), w(9, 93f), w(6, 93f), w(2, 92.4f), w(0, 92f))
        val s = WeightLog.summary(entries, today)!!
        assertEquals(92f, s.latest.kg)
        assertEquals((93f + 92.4f + 92f) / 3, s.week!!, 0.001f)
        assertEquals(93.5f, s.weekBefore!!, 0.001f)
        assertEquals("92.0 kg, 7-day average 92.5 kg", weightLine(s, imperial = false))
        assertEquals("1.0 kg less than the 7 days before", weightChange(s, imperial = false))
    }

    @Test fun `before the first weight the row asks for one, and a lone week has no change`() {
        assertNull(WeightLog.summary(emptyList(), today))
        assertEquals("Log it to see its trend", weightLine(null, imperial = false))
        val s = WeightLog.summary(listOf(w(1, 80f)), today)!!
        assertNull(s.change)
        assertNull(weightChange(s, imperial = false))
    }

    @Test fun `weeks run back from today, oldest first, empty ones null`() {
        val weeks = WeightLog.weeks(listOf(w(0, 90f), w(14, 92f)), today, 3)
        assertEquals(listOf(today.minusDays(14), today.minusDays(7), today), weeks.map { it.end })
        assertEquals(listOf(92f, null, 90f), weeks.map { it.kg })
    }

    @Test fun `a typed weight reads in kilograms or pounds, with a point or a comma`() {
        assertEquals(92.4f, WeightLog.parse("92,4", imperial = false)!!, 0.001f)
        assertEquals(92.4f, WeightLog.parse(" 92.4 ", imperial = false)!!, 0.001f)
        assertEquals(90.718f, WeightLog.parse("200", imperial = true)!!, 0.01f)
        assertNull(WeightLog.parse("9", imperial = false))
        assertNull(WeightLog.parse("abc", imperial = false))
    }
}
