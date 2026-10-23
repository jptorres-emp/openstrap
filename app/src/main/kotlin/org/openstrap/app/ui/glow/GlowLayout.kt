package org.openstrap.app.ui.glow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.openstrap.app.ui.dashboard.ScreenList
import org.openstrap.app.ui.theme.AppIcons

/*
 * One set of spacing rules for every glow card and dot panel, so their contents line up down the
 * screen: same gutter, same inset from the rounded edge, same rhythm from title to number to chart.
 */
object GlowLayout {
    /** Space between the screen edge and a card. */
    val Gutter = 20.dp
    /** Inside a card: clear of the big rounded corners and the rim light, so text sits on the calm middle. Dot panels use the same, so text columns align. */
    val CardPadding = PaddingValues(start = 30.dp, end = 30.dp, top = 32.dp, bottom = 26.dp)
    /** Title row to the hero number. */
    val TitleGap = 14.dp
    /** Hero number (or the last line of text) to the chart. */
    val ChartGap = 20.dp
    /** Every chart in a card has the same height, axis labels included. */
    val ChartHeight = 116.dp
    /** Dot-matrix hero numbers. */
    val HeroDot = 4.5.dp
    val HeroGap = 1.8.dp
    /** From this width (an unfolded foldable, a tablet, a phone on its side) the dashboards use two columns. */
    val TwoColumnWidth = 600.dp
    /** The widest the cards get; past it the margins grow, so lines stay readable on a tablet. */
    val MaxContentWidth = 1040.dp
    /** Space between the two columns: the same as between cards in a column. */
    val ColumnGap = 12.dp
    /** The arc gauge stops growing here, however wide its card. */
    val GaugeMaxWidth = 440.dp
    /**
     * With two columns on an unfolded foldable each is a little narrower than a phone, and beside a detail pane a
     * dashboard is a narrow phone's width. A tighter gutter and card sides give the cards back a phone card's inner
     * width, so tile labels and values fit as they do there.
     */
    val WideGutter = 16.dp
    /** Narrower than this (a small phone, a dashboard beside a detail) cards use the tight sides too. */
    val NarrowWidth = 400.dp
    val WideCardPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 32.dp, bottom = 26.dp)
}

/** The padding inside glow cards and dot panels; two-column dashboards narrow it. */
val LocalCardPadding = staticCompositionLocalOf { GlowLayout.CardPadding }

/** Where a dashboard block goes when there's room for two columns. On a phone every block stacks in the order given. */
enum class Pane { Full, Left, Right }

/** The blocks of a dashboard, in phone order, each with the column it takes on a wide screen. */
class GlowBlocks internal constructor() {
    internal val blocks = mutableListOf<Pair<Pane, @Composable () -> Unit>>()

    /** The block [GlowScreenList] scrolls to when asked ([focus]), or -1. */
    internal var focusBlock = -1

    /** Marks the next block as the one to scroll to. */
    fun focusNext() { focusBlock = blocks.size }
    fun full(content: @Composable () -> Unit) = add(Pane.Full, content)
    fun left(content: @Composable () -> Unit) = add(Pane.Left, content)
    fun right(content: @Composable () -> Unit) = add(Pane.Right, content)
    private fun add(pane: Pane, content: @Composable () -> Unit) {
        blocks += pane to content
    }
}

/**
 * A glow dashboard's list. Narrower than [GlowLayout.TwoColumnWidth] it's the phone list: every block in
 * order, one under the other. Wider, each run of left and right blocks sits side by side in two columns,
 * and full blocks span both. It stays one lazy list either way, so scrolling, pull to sync and the glow
 * pausing while it scrolls behave the same. With [focus] set it scrolls to the block marked with
 * [GlowBlocks.focusNext] once, then calls [onFocused].
 */
@Composable
fun GlowScreenList(
    contentPadding: PaddingValues,
    state: LazyListState,
    focus: Boolean = false,
    onFocused: () -> Unit = {},
    content: GlowBlocks.() -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= GlowLayout.TwoColumnWidth
        val tight = wide || maxWidth < GlowLayout.NarrowWidth
        val gutter = ((maxWidth - GlowLayout.MaxContentWidth) / 2).coerceAtLeast(if (tight) GlowLayout.WideGutter else GlowLayout.Gutter)
        val built = GlowBlocks().apply(content)
        val blocks = built.blocks
        // The lazy item the marked block lands in: its own on a phone; on a wide screen a two-column run is one item.
        val target = built.focusBlock.takeIf { it >= 0 }?.let { if (wide) itemOf(blocks, it) else it }
        androidx.compose.runtime.LaunchedEffect(focus, target) {
            if (focus && target != null) { state.animateScrollToItem(target); onFocused() }
        }
        CompositionLocalProvider(LocalCardPadding provides if (tight) GlowLayout.WideCardPadding else GlowLayout.CardPadding) {
            ScreenList(contentPadding, state, gutter = gutter) {
                if (!wide) blocks.forEach { (_, block) -> item { block() } }
                else sections(blocks).forEach { section ->
                    val left = section.filter { it.first == Pane.Left }.map { it.second }
                    val right = section.filter { it.first == Pane.Right }.map { it.second }
                    // A full block, or a run with nothing on one side, takes the whole width rather than leave a hole.
                    if (left.isEmpty() || right.isEmpty()) section.forEach { (_, block) -> item { block() } }
                    else item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GlowLayout.ColumnGap)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(GlowLayout.ColumnGap)) { left.forEach { it() } }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(GlowLayout.ColumnGap)) { right.forEach { it() } }
                        }
                    }
                }
            }
        }
    }
}

/** The lazy item that block [index] lands in on a wide screen, following [GlowScreenList]'s layout. */
private fun itemOf(blocks: List<Pair<Pane, @Composable () -> Unit>>, index: Int): Int {
    var item = 0
    var seen = 0
    for (section in sections(blocks)) {
        val split = section.any { it.first == Pane.Left } && section.any { it.first == Pane.Right }
        if (index < seen + section.size) return if (split) item else item + (index - seen)
        item += if (split) 1 else section.size
        seen += section.size
    }
    return item
}

/** Splits the blocks into runs: each full block on its own, and each unbroken stretch of left and right blocks together. */
private fun sections(blocks: List<Pair<Pane, @Composable () -> Unit>>): List<List<Pair<Pane, @Composable () -> Unit>>> {
    val out = mutableListOf<MutableList<Pair<Pane, @Composable () -> Unit>>>()
    blocks.forEach { b ->
        val last = out.lastOrNull()
        if (b.first != Pane.Full && last != null && last.first().first != Pane.Full) last += b else out += mutableListOf(b)
    }
    return out
}

/**
 * The title row of a card: the title, an optional note on the right (text, or [trailingContent] for
 * anything richer) and an optional chevron.
 */
@Composable
fun GlowHeader(
    title: String,
    trailing: String? = null,
    chevron: Boolean = false,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White, style = GlowType.title, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall)
        trailingContent?.invoke()
        if (chevron) {
            Spacer(Modifier.width(4.dp))
            Icon(AppIcons.CaretRight, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(16.dp))
        }
    }
}

/**
 * The hero number of a card in dot-matrix, its unit beside it, and up to two short facts on the right
 * (text, or [side] for anything richer). Sits [GlowLayout.TitleGap] under the title.
 */
@Composable
fun GlowHeroNumber(value: String, unit: String, facts: List<String> = emptyList(), side: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = GlowLayout.TitleGap), verticalAlignment = Alignment.Bottom) {
        DotMatrixText(value, dot = GlowLayout.HeroDot, gap = GlowLayout.HeroGap)
        Text(unit, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp).weight(1f))
        if (facts.isNotEmpty()) Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            facts.forEach { Text(it, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall) }
        }
        side?.invoke()
    }
}

/** The gap above a card's chart. */
@Composable
fun GlowChartGap() = Spacer(Modifier.padding(top = GlowLayout.ChartGap))
