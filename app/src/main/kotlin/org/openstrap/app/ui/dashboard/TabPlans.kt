package org.openstrap.app.ui.dashboard

import org.openstrap.app.ui.glow.Edge
import org.openstrap.app.ui.glow.Pane

/*
 * The cards of the Recovery, Sleep and Activity tabs, in order, each with its look and the column it takes on a wide
 * screen (spec 5). Unlike Today, the tabs keep one set of cards whatever the day, so each card's edge is fixed by
 * what it means (spec 2.4) and chosen so no two neighbours ever look the same: no clash to resolve at run time.
 * The cards that come and go are only the optional ones in each tab's facts, and the tests go through all of them.
 */

/** A tab card: which [card], its [look], and its [pane] on a wide screen (on a phone every card stacks in order). */
data class TabSlot<C>(val card: C, val look: CardLook, val pane: Pane)

/**
 * The neighbours among [slots] that look the same. On two columns, a full-width card counts as touching the top and
 * bottom card of each column next to it, as it does on screen.
 */
fun tabClashes(slots: List<TabSlot<*>>, wide: Boolean): List<Pair<Int, Int>> =
    lookClashes(slots.map { it.look }, slots.map { it.pane }, wide, fullTouchesColumns = true)

private fun glow(h: Hue) = CardLook(Tier.Glow, h)
private fun edge(h: Hue, e: Edge) = CardLook(Tier.Edge, h, e)
private val ListLook = CardLook(Tier.List, Hue.Neutral)

/* ---------------------------------------------- Recovery -------------------------------------------- */

enum class RecoveryCard { Hero, Drivers, Charge, ChargeToday, BodySignals, HabitAge }

/**
 * What changes the Recovery tab. Only [habitAge] adds or removes a card; [learning] and [hasRecovery] change what
 * the cards say, never which cards there are, so the screen keeps its shape from the first night on.
 */
data class RecoveryFacts(val habitAge: Boolean = true, val learning: Boolean = false, val hasRecovery: Boolean = true)

/**
 * Recovery (spec 5.1): the score glows, what drove it as a list, Charge as the second glow with where it went as a
 * list under it. Body signals would make a third list in a row, so it sits on an edge card in the neutral light,
 * soft from below like any value at its usual. Habit Age is about earlier (last week), lit from the left.
 *
 * Wide: the hero spans both columns, Recovery's story on the left, the body on the right. Nothing glows at the top
 * of a column, under the hero.
 */
fun recoveryCards(facts: RecoveryFacts = RecoveryFacts()): List<TabSlot<RecoveryCard>> = listOfNotNull(
    TabSlot(RecoveryCard.Hero, glow(Hue.Recovery), Pane.Full),
    TabSlot(RecoveryCard.Drivers, ListLook, Pane.Left),
    TabSlot(RecoveryCard.Charge, glow(Hue.Charge), Pane.Left),
    TabSlot(RecoveryCard.ChargeToday, ListLook, Pane.Left),
    TabSlot(RecoveryCard.BodySignals, edge(Hue.Neutral, Edge.Bottom), Pane.Right),
    TabSlot(RecoveryCard.HabitAge, edge(Hue.HabitAge, Edge.Left), Pane.Right).takeIf { facts.habitAge },
)

/* ----------------------------------------------- Sleep ---------------------------------------------- */

enum class SleepCard { Hero, Vitals, Regularity, Breathing, Naps }

/** What changes the Sleep tab: whether the night had a breathing summary, and whether there were naps. */
data class SleepFacts(val breathing: Boolean = true, val naps: Boolean = false, val hasNight: Boolean = true)

/**
 * Sleep (spec 5.2): the night glows, its vitals as a list. Regularity is lit from the top like a night sky. Breathing
 * disturbances, right under it, is a value against your usual, so it's lit from below (stronger when off your usual)
 * and the two never look alike. Naps happened earlier in the day: lit from the left, so they differ from either.
 *
 * Wide: the night spans both columns, the vitals and naps on the left, the week's regularity and breathing on the right.
 */
fun sleepCards(facts: SleepFacts = SleepFacts()): List<TabSlot<SleepCard>> = listOfNotNull(
    TabSlot(SleepCard.Hero, glow(Hue.Sleep), Pane.Full),
    TabSlot(SleepCard.Vitals, ListLook, Pane.Left),
    TabSlot(SleepCard.Regularity, edge(Hue.Sleep, Edge.Top), Pane.Right),
    TabSlot(SleepCard.Breathing, edge(Hue.Sleep, Edge.Bottom), Pane.Right).takeIf { facts.breathing },
    TabSlot(SleepCard.Naps, edge(Hue.Sleep, Edge.Left), Pane.Left).takeIf { facts.naps },
)

/* --------------------------------------------- Activity --------------------------------------------- */

enum class ActivityCard { Start, Steps, Load, Facts, Workouts }

/**
 * What changes the Activity tab: the start strip is [isToday] only. A [suggestion] to save is a row in the workouts
 * list, not a card of its own.
 */
data class ActivityFacts(val isToday: Boolean = true, val suggestion: Boolean = false)

/**
 * Activity (spec 5.3): a strip to start a sport (today only), steps glowing, Load as progress toward your usual (lit
 * from below, with its bar), this week's facts as a list. The workouts list comes right after another list, so it
 * sits on an edge card in the neutral light. That edge comes from the top, so it never matches Load's.
 *
 * Wide: the strip and steps span both columns, Load on the left, the facts and workouts on the right.
 */
fun activityCards(facts: ActivityFacts = ActivityFacts()): List<TabSlot<ActivityCard>> = listOfNotNull(
    TabSlot(ActivityCard.Start, CardLook(Tier.Strip, Hue.Steps, Edge.Bottom), Pane.Full).takeIf { facts.isToday },
    TabSlot(ActivityCard.Steps, glow(Hue.Steps), Pane.Full),
    TabSlot(ActivityCard.Load, edge(Hue.Load, Edge.Bottom), Pane.Left),
    TabSlot(ActivityCard.Facts, ListLook, Pane.Right),
    TabSlot(ActivityCard.Workouts, edge(Hue.Neutral, Edge.Top), Pane.Right),
)
