package org.openstrap.app.ui.glow

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.openstrap.app.ui.dashboard.ScreenList
import org.openstrap.app.ui.theme.AppIcons
import org.openstrap.app.ui.theme.MetricColors

/*
 * One set of shape and spacing rules for every card (spec 2.4), so their contents line up down the screen: one
 * corner, one inset from the edge on every tier, the same rhythm from title to number to chart. Cards take their
 * corners and padding from here, never a literal of their own.
 */
object GlowLayout {
    /** Space between the screen edge and a card. */
    val Gutter = 20.dp

    /** Every card's corner, on every tier: glow, edge, list, strip and page panels. */
    val CardCorner = 28.dp
    val CardShape = RoundedCornerShape(CardCorner)
    /** A sheet's top corners match the cards'. */
    val SheetShape = RoundedCornerShape(topStart = CardCorner, topEnd = CardCorner)
    /** Sport tiles, chart pills, text fields: rounded, but a step tighter than a card. */
    val TileCorner = 16.dp
    val TileShape = RoundedCornerShape(TileCorner)

    /** From a card's edge to its content, on every side and every tier. */
    val CardInset = 20.dp
    val CardPadding = PaddingValues(CardInset)
    /** Above and below each list row; the panel adds the rest of [CardInset] at its top and bottom. */
    val RowPadding = 14.dp
    /** Inside a list: [CardInset] at the sides, and with the first and last row's [RowPadding] at the top and bottom. */
    val ListPadding = PaddingValues(horizontal = CardInset, vertical = CardInset - RowPadding)

    /** The 1 dp hairline round a glow or edge card, and a chart's grid: white at 8% (spec 2.2, 2.7). */
    const val HairlineAlpha = 0.08f
    /** A list panel's border and the divider between its rows: white at 6%. */
    const val DividerAlpha = 0.06f

    /** Title row to the hero number. */
    val TitleGap = 14.dp
    /** Hero number (or the last line of text) to the chart. */
    val ChartGap = 20.dp
    /** Every chart in a card has the same height, axis labels included. */
    val ChartHeight = 116.dp
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
     * dashboard is a narrow phone's width. A tighter gutter gives the cards back a phone card's inner width, so tile
     * labels and values fit as they do there.
     */
    val WideGutter = 16.dp
    /** Narrower than this (a small phone, a dashboard beside a detail) the gutter is tight too. */
    val NarrowWidth = 400.dp
}

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
 * Whether the card around this content opens something when tapped. [GlowCard] and [EdgeCard] provide it from their
 * `onClick`, so every card title ([CardTitle]) shows the tap caret on its own (spec 2.5, Tappable says so).
 */
val LocalCardOpens = compositionLocalOf { false }

/** The tap caret (spec 2.5): 14 dp, at [alpha] white, 50% after a card's title and 30% at a row's end. */
@Composable
internal fun TapCaret(alpha: Float, modifier: Modifier = Modifier) =
    Icon(AppIcons.TapCaret, null, tint = Color.White.copy(alpha = alpha), modifier = modifier.size(CaretSize))

internal val CaretSize = 14.dp
/** A card's caret, right after its title. */
internal const val CARD_CARET_ALPHA = 0.5f
/** A row's caret, at its end. */
internal const val ROW_CARET_ALPHA = 0.3f

/** A card's title in [GlowType.cardTitle], with the tap caret 6 dp after the text when the card opens something. */
@Composable
internal fun CardTitle(
    title: String,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    if (!LocalCardOpens.current) {
        Text(title, modifier, color = Color.White, style = GlowType.cardTitle, maxLines = maxLines, overflow = overflow)
        return
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f, fill = false), color = Color.White, style = GlowType.cardTitle, maxLines = maxLines, overflow = overflow)
        Spacer(Modifier.width(6.dp))
        TapCaret(CARD_CARET_ALPHA)
    }
}

/**
 * The title row of a card: the title (with the tap caret when the card opens something), an optional [marker] next to
 * it ([StateMarker]) or else a neutral [tag] ([TitleMark]), and an optional note on the right (text, or
 * [trailingContent] for anything richer).
 */
@Composable
fun GlowHeader(
    title: String,
    trailing: String? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    marker: String? = null,
    tag: TitleTag? = null,
) {
    val marked = marker != null || tag != null
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // The mark goes beside the title, or under it on a narrow card, so it never squeezes the title.
        if (marked) FlowRow(
            Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            CardTitle(title, maxLines = 1)
            TitleMark(marker, tag)
        } else CardTitle(title, Modifier.weight(1f))
        if (trailing != null) Text(trailing, color = Color.White.copy(alpha = GlowType.SECONDARY_ALPHA), style = GlowType.secondary)
        trailingContent?.invoke()
    }
}

/**
 * The hero number of a card in dot-matrix, its unit beside it, and up to two short facts on the right
 * (text, or [side] for anything richer). Sits [GlowLayout.TitleGap] under the title.
 */
@Composable
fun GlowHeroNumber(value: String, unit: String, facts: List<String> = emptyList(), side: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = GlowLayout.TitleGap), verticalAlignment = Alignment.Bottom) {
        DotMatrixText(value, dot = DotSize.Hero)
        // The word after a number (spec 2.3).
        Text(unit, color = Color.White.copy(alpha = GlowType.SECONDARY_ALPHA), style = GlowType.body, modifier = Modifier.padding(start = 8.dp).weight(1f))
        if (facts.isNotEmpty()) Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            facts.forEach { Text(it, color = Color.White.copy(alpha = GlowType.SECONDARY_ALPHA), style = GlowType.secondary) }
        }
        side?.invoke()
    }
}

/** The gap above a card's chart. */
@Composable
fun GlowChartGap() = Spacer(Modifier.padding(top = GlowLayout.ChartGap))

/**
 * A small sync marker next to a card's title, "Up to 09:40" (spec 4.3): amber, as the strap chip, since it's about
 * the sync and not the body.
 */
@Composable
internal fun StateMarker(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, color = MetricColors.warn, style = GlowType.caption, maxLines = 1)
}

/**
 * A neutral tag next to a card's title (spec 4.3, E4): "Partial day", "First score". About the data, not the sync,
 * so white and not amber (spec 2.1). With [onClick], tapping it explains it (spec 7.1, one caveat per card).
 */
class TitleTag(val text: String, val onClick: (() -> Unit)? = null)

/** What sits next to a card's title: the sync [marker] when there's one, else the [tag]. One mark per card. */
@Composable
internal fun TitleMark(marker: String?, tag: TitleTag?, modifier: Modifier = Modifier) {
    if (marker != null) StateMarker(marker, modifier)
    else if (tag != null) {
        val shape = RoundedCornerShape(50)
        val onClick = tag.onClick
        Text(
            tag.text,
            modifier.clip(shape).border(1.dp, Color.White.copy(alpha = 0.22f), shape)
                .let { if (onClick != null) it.clickable(onClickLabel = "About ${tag.text.lowercase()}", onClick = onClick) else it }
                .padding(horizontal = 8.dp, vertical = 2.dp),
            color = Color.White.copy(alpha = GlowType.CAPTION_ALPHA), style = GlowType.caption, maxLines = 1,
        )
    }
}
