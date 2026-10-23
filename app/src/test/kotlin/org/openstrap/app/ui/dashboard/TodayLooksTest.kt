package org.openstrap.app.ui.dashboard

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openstrap.app.data.Moment
import org.openstrap.app.ui.dashboard.TodayCard.Activity
import org.openstrap.app.ui.dashboard.TodayCard.EditToday
import org.openstrap.app.ui.dashboard.TodayCard.Hero
import org.openstrap.app.ui.dashboard.TodayCard.LastNight
import org.openstrap.app.ui.dashboard.TodayCard.LoadToday
import org.openstrap.app.ui.dashboard.TodayCard.Recovery
import org.openstrap.app.ui.dashboard.TodayCard.RightNow
import org.openstrap.app.ui.dashboard.TodayCard.Week
import org.openstrap.app.ui.glow.CardSize
import org.openstrap.app.ui.glow.Edge

/** No two Today cards next to each other look the same (spec 2.4), whatever the moment, the pins and the hides. */
class TodayLooksTest {
    private val allFacts = TodayFacts(offer = true, suggestion = true, alerts = true, workout = true, healthspan = true)

    /** Fails, naming the pair, when any neighbours in [slots] look the same. */
    private fun assertVaried(slots: List<TodaySlot>, wide: Boolean, what: String) {
        val clash = lookClashes(slots, wide).firstOrNull() ?: return
        throw AssertionError("$what: ${slots[clash.first].card} and ${slots[clash.second].card} look the same, ${slots[clash.first].look}")
    }

    /** Today as the screen builds it. */
    private fun today(m: Moment, facts: TodayFacts, edits: TodayEdits = TodayEdits(), live: Boolean = false, wide: Boolean = false) =
        varyLooks(applyEdits(todayCards(m, facts), edits, m), m, live, wide)

    @Test fun `the check catches two neighbours that look the same`() {
        val teal = CardLook(Tier.Edge, Hue.Recovery, Edge.Left)
        val slots = listOf(TodaySlot(Recovery, CardSize.Wide, look = teal), TodaySlot(Week, CardSize.Wide, look = teal))
        assertEquals(listOf(0 to 1), lookClashes(slots, wide = false))
        // Side by side on two columns counts, as does one over the other in a column.
        val four = List(4) { TodaySlot(Activity, CardSize.Wide, look = CardLook(Tier.Edge, Hue.Steps, if (it % 2 == 1) Edge.Top else Edge.Bottom)) }
        assertTrue(lookClashes(four, wide = false).isEmpty())
        assertEquals(listOf(0 to 2, 1 to 3), lookClashes(four, wide = true))
        // Colour alone doesn't tell two cards apart.
        val bars = listOf(TodaySlot(TodayCard.Charge, CardSize.Wide, look = CardLook(Tier.Edge, Hue.Charge, Edge.Bottom)), TodaySlot(Activity, CardSize.Wide, look = CardLook(Tier.Edge, Hue.Steps, Edge.Bottom)))
        assertEquals(listOf(0 to 1), lookClashes(bars, wide = false))
    }

    @Test fun `two glows never stack, so the second glow steps down to its edge card`() {
        val window = today(Moment.WorkoutWindow, TodayFacts())
        assertEquals(TodayCard.Charge, window[1].card)
        assertEquals(CardLook(Tier.Edge, Hue.Charge, Edge.Bottom), window[1].look)
        assertEquals(false, window[1].secondGlow)
        // After a workout the workout card sits between, so Charge keeps its glow.
        val after = today(Moment.After, TodayFacts(workout = true))
        assertEquals(true, after.first { it.card == TodayCard.Charge }.secondGlow)
    }

    @Test fun `every moment's own cards are varied, on a phone and on two columns`() {
        for (m in Moment.entries) for (facts in listOf(TodayFacts(), allFacts, TodayFacts(isToday = false, alerts = true))) {
            for (live in listOf(false, true)) for (wide in listOf(false, true)) {
                val out = today(m, facts, live = live, wide = wide)
                assertVaried(out, wide, "$m $facts live=$live wide=$wide")
                // The defaults need no help: same cards, same order.
                assertEquals(todayCards(m, facts).map { it.card }, out.map { it.card })
            }
        }
    }

    @Test fun `your week no longer borrows Recovery's teal`() {
        val midday = today(Moment.Midday, TodayFacts())
        val rec = midday.first { it.card == Recovery }.look
        val week = midday.first { it.card == Week }.look
        assertEquals(CardLook(Tier.Edge, Hue.Neutral, Edge.Bottom), week)
        assertNotEquals(rec?.hue, week?.hue)
        assertNotEquals(rec?.edge, week?.edge)
    }

    @Test fun `a clash flips the later card's edge first`() {
        val slots = listOf(TodaySlot(Hero, CardSize.Large), TodaySlot(Activity, CardSize.Wide), TodaySlot(Activity, CardSize.Wide))
        val out = varyLooks(slots, Moment.Midday)
        assertEquals(Edge.Bottom, out[1].look?.edge)
        assertEquals(Edge.Top, out[2].look?.edge)
        assertEquals(Hue.Steps, out[2].look?.hue)
    }

    @Test fun `two lists together keep their places, the second on a neutral edge`() {
        val out = today(Moment.Midday, TodayFacts(), TodayEdits(pinned = listOf(RightNow, LoadToday)))
        assertEquals(listOf(Hero, RightNow, LoadToday, LastNight, Activity), out.map { it.card }.take(5))
        assertEquals(CardLook(Tier.List, Hue.Neutral), out[1].look)
        assertEquals(CardLook(Tier.Edge, Hue.Neutral, Edge.Bottom), out[2].look)
        assertVaried(out, false, "midday pins")
    }

    @Test fun `a card moves only when no other look will do`() {
        // Two glows have no other look, so the later one, whose place is open, moves down a card.
        val habit = TodaySlot(TodayCard.HabitAge, CardSize.Wide)
        val slots = listOf(
            TodaySlot(Hero, CardSize.Large), TodaySlot(Activity, CardSize.Wide), habit, habit, TodaySlot(Recovery, CardSize.Wide),
            TodaySlot(EditToday, CardSize.Strip),
        )
        val out = varyLooks(slots, Moment.Midday)
        assertEquals(listOf(Hero, Activity, TodayCard.HabitAge, Recovery, TodayCard.HabitAge, EditToday), out.map { it.card })
        assertVaried(out, false, "two glows")
    }

    @Test fun `every pin and hide on every moment stays varied`() {
        val pinnable = TodayCard.entries.filter { it.pinnable }
        val hideable = TodayCard.entries.filter { it.editable }
        val pinLists = listOf(emptyList<TodayCard>()) + pinnable.map { listOf(it) } +
            pinnable.flatMap { a -> pinnable.filter { it != a }.map { listOf(a, it) } }
        // Every set of up to three hidden cards with every pin, then random larger ones.
        val hideSets = buildList {
            add(emptySet<TodayCard>())
            hideable.forEachIndexed { i, a ->
                add(setOf(a))
                hideable.drop(i + 1).forEachIndexed { j, b ->
                    add(setOf(a, b))
                    hideable.drop(i + j + 2).forEach { c -> add(setOf(a, b, c)) }
                }
            }
        }
        var checked = 0
        for (m in Moment.entries) for (pins in pinLists) for (hidden in hideSets) {
            val edits = TodayEdits(hidden - pins.toSet(), pins)
            for (wide in listOf(false, true)) {
                assertVaried(today(m, TodayFacts(workout = true), edits, wide = wide), wide, "$m pins=$pins hidden=$hidden wide=$wide")
                checked++
            }
        }
        val rnd = Random(42)
        repeat(20_000) {
            val m = Moment.entries.random(rnd)
            val facts = TodayFacts(rnd.nextBoolean(), rnd.nextBoolean(), rnd.nextBoolean(), rnd.nextBoolean(), rnd.nextBoolean(), rnd.nextBoolean())
            val pins = pinLists.random(rnd)
            val hidden = hideable.filter { rnd.nextInt(3) == 0 }.toSet() - pins.toSet()
            val live = rnd.nextBoolean()
            val wide = rnd.nextBoolean()
            val out = today(m, facts, TodayEdits(hidden, pins), live, wide)
            assertVaried(out, wide, "$m $facts pins=$pins hidden=$hidden live=$live wide=$wide")
            // Nothing is lost or added, and Edit Today stays last.
            assertEquals(applyEdits(todayCards(m, facts), TodayEdits(hidden, pins), m).map { it.card }.sorted(), out.map { it.card }.sorted())
            assertEquals(EditToday, out.last().card)
            assertEquals(Hero, out.first().card)
            checked++
        }
        assertTrue(checked > 100_000)
    }
}
