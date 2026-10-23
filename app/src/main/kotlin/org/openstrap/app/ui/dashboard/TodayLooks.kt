package org.openstrap.app.ui.dashboard

import org.openstrap.app.data.Moment
import org.openstrap.app.ui.glow.CardSize
import org.openstrap.app.ui.glow.Edge
import org.openstrap.app.ui.glow.Pane

/*
 * How each Today card looks, and the rule that no two cards next to each other look the same (spec 2.4). Plain
 * functions over the final list of cards, after Edit Today, so every moment and every pin and hide is tested
 * without a screen.
 */

/** A card's tier (spec 2.4), with strips and plain pieces (Edit Today) told apart from edge cards. */
enum class Tier { Glow, Edge, List, Strip, Plain }

/** The colour a card is lit in: its metric's, or [Neutral], the cool grey of hsl 220 10% 55%. */
enum class Hue { Recovery, Charge, Sleep, Heart, Steps, HabitAge, Neutral }

/**
 * What a card looks like at a glance: its [tier], its [hue] and, for edge cards and strips, the [edge] its light
 * comes in from. Two cards look the same when tier and edge match ([sameDesign]): a colour alone doesn't tell two
 * cards apart, so two glows in a row, or two edge cards lit from one side, look the same whatever their colours.
 */
data class CardLook(val tier: Tier, val hue: Hue, val edge: Edge? = null) {
    fun sameDesign(other: CardLook?): Boolean = other != null && tier == other.tier && edge == other.edge
}

private fun glow(h: Hue) = CardLook(Tier.Glow, h)
private fun edge(h: Hue, e: Edge) = CardLook(Tier.Edge, h, e)
private fun strip(h: Hue, e: Edge) = CardLook(Tier.Strip, h, e)
private val ListLook = CardLook(Tier.List, Hue.Neutral)

/** The colour of the hero for [moment] (spec 2.5 and 4.2). */
private fun heroHue(moment: Moment?): Hue = when (moment) {
    Moment.Midday -> Hue.Charge
    Moment.During, Moment.After -> Hue.Heart
    Moment.Evening, Moment.Night -> Hue.Sleep
    Moment.Waking, Moment.WorkoutWindow, null -> Hue.Recovery
}

/**
 * The look [slot] has on its own (spec 2.4): the edge follows what the card is about. Heart rate is lit from the right
 * only while the reading is [live]. Your week is a neutral edge from the bottom: a summary of steps, sleep and
 * workouts at their usual, in no one metric's colour.
 */
fun defaultLook(slot: TodaySlot, moment: Moment?, live: Boolean = false): CardLook = when (slot.card) {
    TodayCard.Hero -> glow(heroHue(moment))
    TodayCard.Offer -> strip(Hue.Heart, Edge.Right)
    TodayCard.Suggestion -> strip(Hue.Heart, Edge.Left)
    TodayCard.Alerts -> strip(Hue.Heart, Edge.Top)
    TodayCard.CheckIn -> strip(Hue.Recovery, Edge.Left)
    TodayCard.BodyMorning, TodayCard.RightNow, TodayCard.LoadToday -> ListLook
    TodayCard.LastNight -> if (slot.size == CardSize.Strip) strip(Hue.Sleep, Edge.Top) else edge(Hue.Sleep, Edge.Top)
    TodayCard.HeartRate -> edge(Hue.Heart, if (live) Edge.Right else Edge.Bottom)
    TodayCard.Activity -> edge(Hue.Steps, Edge.Bottom)
    TodayCard.Recovery -> edge(Hue.Recovery, Edge.Left)
    TodayCard.Charge -> if (slot.secondGlow) glow(Hue.Charge) else edge(Hue.Charge, Edge.Bottom)
    TodayCard.Workout -> edge(Hue.Heart, Edge.Left)
    TodayCard.Week -> edge(Hue.Neutral, Edge.Bottom)
    TodayCard.HabitAge -> glow(Hue.HabitAge)
    TodayCard.EditToday -> CardLook(Tier.Plain, Hue.Neutral)
}

/** The look a slot ends up with: the one [varyLooks] gave it, or its own. */
fun TodaySlot.lookOr(moment: Moment?, live: Boolean = false): CardLook = look ?: defaultLook(this, moment, live)

/**
 * Where each slot goes on a wide screen: the hero and strips span both columns, the cards between take turns, as
 * [org.openstrap.app.ui.glow.GlowScreenList] lays them out.
 */
fun todayPanes(slots: List<TodaySlot>): List<Pane> {
    var side = Pane.Left
    return slots.map { s ->
        when (s.size) {
            CardSize.Large, CardSize.Strip -> Pane.Full
            else -> side.also { side = if (it == Pane.Left) Pane.Right else Pane.Left }
        }
    }
}

/**
 * The pairs of slots (by index, earlier first) that sit next to each other: one after the other in reading order,
 * and on a [wide] screen also side by side and one over the other in a column.
 */
fun neighbours(slots: List<TodaySlot>, wide: Boolean): Set<Pair<Int, Int>> {
    val out = (0 until slots.size - 1).mapTo(mutableSetOf()) { it to it + 1 }
    if (!wide) return out
    val panes = todayPanes(slots)
    var i = 0
    while (i < slots.size) {
        if (panes[i] == Pane.Full) { i++; continue }
        val run = generateSequence(i) { it + 1 }.takeWhile { it < slots.size && panes[it] != Pane.Full }.toList()
        val left = run.filter { panes[it] == Pane.Left }
        val right = run.filter { panes[it] == Pane.Right }
        // A run with one side empty is drawn full width, in reading order.
        if (left.isNotEmpty() && right.isNotEmpty()) {
            left.zip(right).forEach { (a, b) -> out += minOf(a, b) to maxOf(a, b) }
            listOf(left, right).forEach { col -> col.zipWithNext().forEach { out += it } }
        }
        i = run.last() + 1
    }
    return out
}

/** The pairs of neighbours that look the same, in reading order. */
fun lookClashes(slots: List<TodaySlot>, wide: Boolean, moment: Moment? = null, live: Boolean = false): List<Pair<Int, Int>> =
    neighbours(slots, wide).filter { (a, b) -> slots[a].lookOr(moment, live).sameDesign(slots[b].lookOr(moment, live)) }
        .sortedWith(compareBy({ it.second }, { it.first }))

/** Cards whose place the spec leaves open: pins (only "under the hero"), and Your week and Habit Age, which close the list. */
private fun TodaySlot.movable() = pinned || card == TodayCard.Week || card == TodayCard.HabitAge

private fun opposite(e: Edge) = when (e) { Edge.Top -> Edge.Bottom; Edge.Bottom -> Edge.Top; Edge.Left -> Edge.Right; Edge.Right -> Edge.Left }

/** [e]'s opposite first, then the two across from it. */
private fun flips(e: Edge): List<Edge> = listOf(opposite(e)) + Edge.entries.filter { it != e && it != opposite(e) }

/**
 * The other looks [s] may take, in the order they're tried: the edge flipped, then another tier the spec gives the
 * card, then the neutral edge. Last night is a strip or a wide card. A list can sit on an edge card in the neutral
 * light. Charge as the second glow can step down to its edge card. Other glows and Edit Today have no other look,
 * and an alert from the strap keeps its colour.
 */
private fun options(s: TodaySlot): List<TodaySlot> {
    val l = s.look ?: return emptyList()
    if (s.secondGlow) return (listOf(Edge.Bottom) + flips(Edge.Bottom)).map { s.copy(secondGlow = false, look = CardLook(Tier.Edge, l.hue, it)) }
    if (l.tier == Tier.List) return (listOf(Edge.Bottom) + flips(Edge.Bottom)).map { s.copy(look = CardLook(Tier.Edge, Hue.Neutral, it)) }
    val e = l.edge ?: return emptyList()
    val sides = listOf(e) + flips(e)
    return buildList {
        flips(e).forEach { add(s.copy(look = l.copy(edge = it))) }
        if (s.card == TodayCard.LastNight) {
            val (size, tier) = if (l.tier == Tier.Edge) CardSize.Strip to Tier.Strip else CardSize.Wide to Tier.Edge
            sides.forEach { add(s.copy(size = size, look = l.copy(tier = tier, edge = it))) }
        }
        if (l.hue != Hue.Neutral && s.card != TodayCard.Alerts) sides.forEach { add(s.copy(look = l.copy(hue = Hue.Neutral, edge = it))) }
    }
}

/** Whether no two neighbours among the first [upTo] + 1 slots look the same. */
private fun settled(slots: List<TodaySlot>, upTo: Int, wide: Boolean) =
    neighbours(slots, wide).none { (a, b) -> b <= upTo && slots[a].look!!.sameDesign(slots[b].look) }

/**
 * Goes down the list and, where a card looks like a neighbour before it, gives the card another look ([options]).
 * When it has none, the earlier neighbour changes instead.
 */
private fun restyle(slots: List<TodaySlot>, wide: Boolean): List<TodaySlot> {
    val out = slots.toMutableList()
    for (i in out.indices) {
        val clashing = neighbours(out, wide).filter { (a, b) -> b == i && out[a].look!!.sameDesign(out[i].look) }.map { it.first }
        if (clashing.isEmpty()) continue
        fun tryAt(at: Int): Boolean {
            val pick = options(out[at]).firstOrNull { o -> settled(out.toMutableList().also { it[at] = o }, i, wide) } ?: return false
            out[at] = pick
            return true
        }
        if (!tryAt(i)) clashing.firstOrNull { tryAt(it) }
    }
    return out
}

/**
 * [slots] with a look each, such that no two neighbours ([neighbours]) look the same (spec 2.4). A clash is resolved
 * first by flipping the later card's edge, then by another tier or the neutral edge, and only then by moving a card
 * whose place the spec leaves open down to the nearest place it fits. [live] is whether heart rate is live, and [wide]
 * whether Today has two columns.
 */
fun varyLooks(slots: List<TodaySlot>, moment: Moment?, live: Boolean = false, wide: Boolean = false): List<TodaySlot> {
    var base = slots.map { it.copy(look = defaultLook(it, moment, live)) }
    repeat(slots.size + 1) {
        val styled = restyle(base, wide)
        val clash = lookClashes(styled, wide).firstOrNull() ?: return styled
        base = move(base, clash, wide) ?: return styled
    }
    return restyle(base, wide)
}

/**
 * [base] with one movable card moved to the nearest place where it looks like none of its neighbours and fewer cards
 * clash overall: a card of the [clash] first, the later one first, then any other. Nothing moves above the hero and
 * the strips that need an answer, or past Edit Today.
 */
private fun move(base: List<TodaySlot>, clash: Pair<Int, Int>, wide: Boolean): List<TodaySlot>? {
    val before = lookClashes(restyle(base, wide), wide).size
    val top = base.indexOfFirst { it.card !in URGENT }.let { if (it < 0) base.size else it }
    val end = base.indexOfFirst { it.card == TodayCard.EditToday }.let { if (it < 0) base.size else it }
    val movers = (listOf(clash.second, clash.first) + base.indices.sortedBy { minOf(kotlin.math.abs(it - clash.first), kotlin.math.abs(it - clash.second)) })
        .distinct().filter { base[it].movable() }
    for (from in movers) {
        val card = base[from]
        val rest = base.toMutableList().also { it.removeAt(from) }
        // Nearest first, and down before up.
        val places = (top until end).filter { it != from }.sortedWith(compareBy({ kotlin.math.abs(it - from) }, { it < from }))
        for (to in places) {
            val moved = rest.toMutableList().also { it.add(to, card) }
            val clashes = lookClashes(restyle(moved, wide), wide)
            if (clashes.size < before && clashes.none { it.first == to || it.second == to }) return moved
        }
    }
    return null
}

private val URGENT = setOf(TodayCard.Hero, TodayCard.Offer, TodayCard.Suggestion, TodayCard.Alerts)
