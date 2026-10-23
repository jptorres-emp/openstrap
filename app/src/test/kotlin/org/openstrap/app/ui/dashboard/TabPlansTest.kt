package org.openstrap.app.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openstrap.app.ui.glow.Edge
import org.openstrap.app.ui.glow.Pane

/**
 * No two neighbouring cards on the Recovery, Sleep and Activity tabs look the same (spec 2.4), with every optional
 * card there or not, on a phone and on two columns.
 */
class TabPlansTest {
    private val bools = listOf(false, true)

    /** Fails, naming the pair, when any neighbours in [slots] look the same. */
    private fun assertVaried(slots: List<TabSlot<*>>, wide: Boolean, what: String) {
        val clash = tabClashes(slots, wide).firstOrNull() ?: return
        throw AssertionError("$what: ${slots[clash.first].card} and ${slots[clash.second].card} look the same, ${slots[clash.first].look}")
    }

    @Test fun `recovery is varied in every state`() {
        var checked = 0
        for (habit in bools) for (learning in bools) for (has in bools) for (wide in bools) {
            val f = RecoveryFacts(habitAge = habit, learning = learning, hasRecovery = has)
            val slots = recoveryCards(f)
            assertVaried(slots, wide, "$f wide=$wide")
            // The same cards whether learning or not, and with or without a Recovery yet: only Habit Age comes and goes.
            assertEquals(recoveryCards(RecoveryFacts(habitAge = habit)).map { it.card }, slots.map { it.card })
            checked++
        }
        assertEquals(16, checked)
    }

    @Test fun `sleep is varied in every state`() {
        var checked = 0
        for (breathing in bools) for (naps in bools) for (night in bools) for (wide in bools) {
            val f = SleepFacts(breathing = breathing, naps = naps, hasNight = night)
            assertVaried(sleepCards(f), wide, "$f wide=$wide")
            checked++
        }
        assertEquals(16, checked)
    }

    @Test fun `activity is varied in every state`() {
        var checked = 0
        for (today in bools) for (suggestion in bools) for (wide in bools) {
            val f = ActivityFacts(isToday = today, suggestion = suggestion)
            assertVaried(activityCards(f), wide, "$f wide=$wide")
            checked++
        }
        assertEquals(8, checked)
    }

    @Test fun `each tab leads with one glow`() {
        for (slots in listOf(recoveryCards(), sleepCards(), activityCards(ActivityFacts(isToday = false)))) {
            assertEquals(Tier.Glow, slots.first().look.tier)
        }
        // Today's start strip sits over the steps glow, which still leads the cards.
        assertEquals(listOf(ActivityCard.Start, ActivityCard.Steps), activityCards().take(2).map { it.card })
        // At most two glows and two plain lists on a screen (spec 2.4).
        for (slots in listOf(recoveryCards(), sleepCards(SleepFacts(naps = true)), activityCards())) {
            assertTrue(slots.count { it.look.tier == Tier.Glow } <= 2)
            assertTrue(slots.count { it.look.tier == Tier.List } <= 2)
        }
    }

    @Test fun `edges follow what each card means`() {
        fun <C> look(slots: List<TabSlot<C>>, card: C) = slots.first { it.card == card }.look
        val rec = recoveryCards()
        assertEquals(CardLook(Tier.Edge, Hue.HabitAge, Edge.Left), look(rec, RecoveryCard.HabitAge))
        assertEquals(CardLook(Tier.Edge, Hue.Neutral, Edge.Bottom), look(rec, RecoveryCard.BodySignals))
        assertEquals(Tier.Glow, look(rec, RecoveryCard.Charge).tier)
        val sleep = sleepCards(SleepFacts(naps = true))
        assertEquals(Edge.Top, look(sleep, SleepCard.Regularity).edge)
        assertEquals(Edge.Bottom, look(sleep, SleepCard.Breathing).edge)
        assertEquals(Edge.Left, look(sleep, SleepCard.Naps).edge)
        val act = activityCards()
        assertEquals(CardLook(Tier.Edge, Hue.Load, Edge.Bottom), look(act, ActivityCard.Load))
        assertEquals(CardLook(Tier.Edge, Hue.Neutral, Edge.Top), look(act, ActivityCard.Workouts))
    }

    @Test fun `the spec's two night sky cards in a row would clash`() {
        // Spec 5.2 as first written: regularity and breathing disturbances both lit from the top, one after the other.
        val night = CardLook(Tier.Edge, Hue.Sleep, Edge.Top)
        val slots = sleepCards().map { if (it.card == SleepCard.Breathing) it.copy(look = night) else it }
        assertEquals(listOf(2 to 3), tabClashes(slots, wide = false))
    }

    @Test fun `on two columns a full card touches the top of both columns`() {
        val glow = CardLook(Tier.Glow, Hue.Recovery)
        val list = CardLook(Tier.List, Hue.Neutral)
        // A hero over a run whose right column starts with a glow: not next in reading order, but right under it.
        val slots = listOf(
            TabSlot("hero", glow, Pane.Full), TabSlot("a", list, Pane.Left), TabSlot("b", glow, Pane.Right),
        )
        assertTrue(tabClashes(slots, wide = false).isEmpty())
        assertEquals(listOf(0 to 2), tabClashes(slots, wide = true))
        // Today keeps its own check, which reads only side by side and down each column.
        assertEquals(setOf(0 to 1, 1 to 2), neighbours(slots.map { it.pane }, wide = true))
        assertEquals(setOf(0 to 1, 1 to 2, 0 to 2), neighbours(slots.map { it.pane }, wide = true, fullTouchesColumns = true))
    }
}
