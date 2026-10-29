package org.openstrap.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class TrainingTest {
    private val end = LocalDate.of(2026, 10, 28)

    private fun session(d: LocalDate, sport: Int, byYou: Boolean = true) =
        Reports.Session(d, LocalTime.of(19, 0), sport, "s$sport", 3600, listOf(0, 0, 0, 0, 0), byYou)

    @Test fun `your sports carry the Load of a usual day of them`() {
        // CrossFit (130) on Mondays, Wednesdays and Fridays at 110 to 114; futsal (18) on Thursdays at 150; rest at 20.
        val days = (0 until 42).map { back ->
            val d = end.minusDays(back.toLong())
            val load = when (d.dayOfWeek.value) { 1, 3, 5 -> 110f + back % 5; 4 -> 150f; else -> 20f }
            Reports.Day(d, load = load)
        }
        val sessions = days.mapNotNull { d ->
            when (d.date.dayOfWeek.value) { 1, 3, 5 -> session(d.date, 130); 4 -> session(d.date, 18); else -> null }
        } + session(end.minusDays(1), 223, byYou = false)
        val sports = Training.sports(days, sessions, end)
        assertEquals(listOf(130, 18), sports.map { it.sport })
        assertEquals(18, sports[0].days)
        assertEquals(112f, sports[0].dayLoad, 1f)
        assertEquals(150f, sports[1].dayLoad, 1e-3f)
        // A day with two sports counts as a day of each, but its Load stands for neither.
        val both = Training.sports(days, sessions + session(end, 18), end).first { it.sport == 18 }
        assertEquals(sports[1].days + 1, both.days)
        assertEquals(150f, both.dayLoad, 1e-3f)
        // Once, or never alone: not yours yet.
        assertEquals(emptyList<Training.SportLoad>(), Training.sports(days, listOf(session(end, 97)), end))
    }

    @Test fun `a sport's usual day lands under, within or over a target`() {
        assertEquals(Training.Fit.Within, Training.fit(110..205, 112f))
        assertEquals(Training.Fit.Within, Training.fit(110..205, 109.6f))
        assertEquals(Training.Fit.Under, Training.fit(150..250, 112f))
        assertEquals(Training.Fit.Over, Training.fit(0..55, 112f))
    }

    @Test fun `the last week against your usual, in the zones Activity's Load card uses`() {
        assertEquals(1.2f, Training.ratio(Reports.Day(end, usualLoad = 100f, fatigue = 120f))!!, 1e-4f)
        assertNull(Training.ratio(Reports.Day(end, usualLoad = null, fatigue = 120f)))
        assertNull(Training.ratio(Reports.Day(end, usualLoad = 0f, fatigue = 120f)))
        assertEquals(Training.Zone.Easing, Training.zone(0.7f))
        assertEquals(Training.Zone.Steady, Training.zone(0.75f))
        assertEquals(Training.Zone.Steady, Training.zone(1.25f))
        assertEquals(Training.Zone.Building, Training.zone(1.3f))
        assertEquals(Training.Zone.Spike, Training.zone(1.6f))
        // The same boundaries as the Activity tab's word for the week.
        assertEquals("Building", org.openstrap.app.ui.dashboard.TabCopy.loadBalance(Recovery.LoadTrend(100f, 126f, -26f)))
        assertEquals(Training.Zone.Building, Training.zone(1.26f))
    }

    @Test fun `energy comes week by week, a day under way left out`() {
        val days = (0 until 28).map { back ->
            Reports.Day(end.minusDays(back.toLong()), kcal = if (back < 7) 2800f else 2500f, partial = back == 0)
        }
        val weeks = Training.energyWeeks(days, end)
        assertEquals(4, weeks.size)
        assertEquals(end, weeks.last().end)
        assertEquals(2800f, weeks.last().kcalADay!!, 1e-3f)
        assertEquals(6, weeks.last().days)
        assertEquals(2500f, Training.energyBefore(weeks)!!, 1e-3f)
        // Without an estimate (no age, weight or height) there's nothing to average.
        assertNull(Training.energyWeeks(days.map { it.copy(kcal = null) }, end).last().kcalADay)
    }
}
