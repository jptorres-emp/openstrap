package org.openstrap.app.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.openstrap.app.data.Moment

/** The fork's weekly report card on Today: where it goes, and that Today stays varied with it. */
class ForkTodayPlanTest {
    private val layouts = listOf(false to false, true to false, true to true)

    @Test fun `the weekly report follows the week, on Mondays right under the hero, and never on a bare Today`() {
        val midday = todayCards(Moment.Midday, TodayFacts(report = true)).map { it.card }
        assertEquals(midday.indexOf(TodayCard.Week) + 1, midday.indexOf(TodayCard.Report))
        val monday = todayCards(Moment.Midday, TodayFacts(alerts = true, report = true, reportFirst = true)).map { it.card }
        assertEquals(listOf(TodayCard.Hero, TodayCard.Alerts, TodayCard.Report), monday.take(3))
        assertEquals(1, monday.count { it == TodayCard.Report })
        assertFalse(TodayCard.Report in todayCards(Moment.Midday, TodayFacts(report = true, bare = true)).map { it.card })
        assertFalse(TodayCard.Report in todayCards(Moment.Midday, TodayFacts()).map { it.card })
        // It can be hidden in Edit Today, but not pinned: on Mondays it comes under the hero by itself.
        assertEquals(TodayEdits(hidden = setOf(TodayCard.Report)), TodayEdits().toggleHidden(TodayCard.Report))
        assertEquals(TodayEdits(), TodayEdits().togglePin(TodayCard.Report))
    }

    @Test fun `with the weekly report every moment stays varied, on Mondays too, with the week or Habit Age hidden`() {
        val hides = listOf(emptySet(), setOf(TodayCard.Week), setOf(TodayCard.HabitAge, TodayCard.StartWorkout))
        for (m in Moment.entries) for (first in listOf(false, true)) for (hidden in hides) for ((wide, across) in layouts) {
            val facts = TodayFacts(workout = true, healthspan = true, report = true, reportFirst = first)
            val out = varyLooks(applyEdits(todayCards(m, facts), TodayEdits(hidden = hidden), m), m, false, wide, across)
            val what = "$m monday=$first hidden=$hidden wide=$wide across=$across"
            assertEquals(what, emptyList<Pair<Int, Int>>(), lookClashes(out, wide, m, smallsAcross = across))
            assertEquals(what, emptyList<Pair<Int, Int>>(), litClashes(out, wide, m, across))
            assertEquals(what, TodayCard.Report !in hidden, out.any { it.card == TodayCard.Report })
        }
    }
}
