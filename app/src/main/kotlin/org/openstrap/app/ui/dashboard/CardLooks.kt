package org.openstrap.app.ui.dashboard

import org.openstrap.app.ui.glow.CardSize
import org.openstrap.app.ui.glow.Edge
import org.openstrap.app.ui.glow.Pane

/*
 * How a card looks at a glance, and which cards sit next to each other, for every screen (spec 2.4). The rule they
 * serve: never two neighbours with the same design. Plain functions, so each screen's layout is tested without one.
 * Today resolves clashes as it goes ([varyLooks]); the tabs fix their looks up front ([recoveryCards] and the like).
 */

/**
 * A card's tier (spec 2.4), with strips and plain pieces (Edit Today) told apart from edge cards. [Level] is an edge
 * card whose light fills from the left across to the value, like Charge: a fill, not an edge, so it reads as its own
 * design.
 */
enum class Tier { Glow, Edge, Level, List, Strip, Plain }

/** The colour a card is lit in: its metric's, or [Neutral], the cool grey of hsl 220 10% 55%. */
enum class Hue { Recovery, Charge, Sleep, Heart, Steps, Load, HabitAge, Neutral }

/**
 * What a card looks like at a glance: its [tier], its [hue] and, for edge cards and strips, the [edge] its light
 * comes in from. Two cards look the same when tier and edge match ([sameDesign]): a colour alone doesn't tell two
 * cards apart, so two glows in a row, or two edge cards lit from one side, look the same whatever their colours.
 */
data class CardLook(val tier: Tier, val hue: Hue, val edge: Edge? = null) {
    fun sameDesign(other: CardLook?): Boolean = other != null && tier == other.tier && edge == other.edge
}

/**
 * Where each card goes on a wide screen when nothing says otherwise: Large cards and strips span both columns, the
 * cards between take turns, as [org.openstrap.app.ui.glow.GlowScreenList] lays them out.
 */
fun alternatingPanes(sizes: List<CardSize>): List<Pane> {
    var side = Pane.Left
    return sizes.map { s ->
        when (s) {
            CardSize.Large, CardSize.Strip -> Pane.Full
            else -> side.also { side = if (it == Pane.Left) Pane.Right else Pane.Left }
        }
    }
}

/**
 * The pairs of cards (by index, earlier first) that sit next to each other, given each card's [panes]: one after the
 * other in reading order, and on a [wide] screen also side by side and one over the other in a column, laid out as
 * [org.openstrap.app.ui.glow.GlowScreenList] does. With [fullTouchesColumns], a full-width card also touches the top
 * card of each column under it and the last card of each column over it.
 */
fun neighbours(panes: List<Pane>, wide: Boolean, fullTouchesColumns: Boolean = false): Set<Pair<Int, Int>> {
    val out = (0 until panes.size - 1).mapTo(mutableSetOf()) { it to it + 1 }
    if (!wide) return out
    var i = 0
    while (i < panes.size) {
        if (panes[i] == Pane.Full) { i++; continue }
        val run = generateSequence(i) { it + 1 }.takeWhile { it < panes.size && panes[it] != Pane.Full }.toList()
        val left = run.filter { panes[it] == Pane.Left }
        val right = run.filter { panes[it] == Pane.Right }
        // A run with one side empty is drawn full width, in reading order.
        if (left.isNotEmpty() && right.isNotEmpty()) {
            left.zip(right).forEach { (a, b) -> out += minOf(a, b) to maxOf(a, b) }
            listOf(left, right).forEach { col -> col.zipWithNext().forEach { out += it } }
            if (fullTouchesColumns) {
                val above = run.first() - 1
                val below = run.last() + 1
                if (above >= 0) { out += above to left.first(); out += above to right.first() }
                if (below < panes.size) { out += left.last() to below; out += right.last() to below }
            }
        }
        i = run.last() + 1
    }
    return out
}

/** The pairs of neighbours among [looks] (placed in [panes]) that look the same, in reading order. */
fun lookClashes(looks: List<CardLook>, panes: List<Pane>, wide: Boolean, fullTouchesColumns: Boolean = false): List<Pair<Int, Int>> =
    neighbours(panes, wide, fullTouchesColumns).filter { (a, b) -> looks[a].sameDesign(looks[b]) }
        .sortedWith(compareBy({ it.second }, { it.first }))
