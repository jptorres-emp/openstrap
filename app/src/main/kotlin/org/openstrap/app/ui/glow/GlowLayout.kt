package org.openstrap.app.ui.glow

import org.openstrap.app.ui.theme.IconSize
import org.openstrap.app.ui.theme.Space
import org.openstrap.app.ui.theme.Greys
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import org.openstrap.app.ui.theme.Ink
import sv.lib.squircleshape.SquircleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.geometry.Size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.openstrap.app.R
import org.openstrap.app.ui.theme.AppIcons
import org.openstrap.app.ui.theme.MetricColors

/*
 * One set of shape and spacing rules for every card (spec 2.4), so their contents line up down the screen: one
 * corner, one inset from the edge on every tier, the same rhythm from title to number to chart. Cards take their
 * corners and padding from here, never a literal of their own.
 */
object GlowLayout {
    /** Space between the screen edge and a card. */
    val Gutter = Space.S16

    /**
     * Corners follow Airofit's rule, measured from its Behance screens on 15 Nov 2026 and made a step rounder by the user:
     * a squircle whose radius is [CORNER_PERCENT] of the shape's shorter side, at [SMOOTHING] (the library's 0 to 100,
     * how far the curve reaches into the straight edge), and never more than [MAX_CORNER] so a tall list stays a card. A
     * 170 dp hero gets 60 dp, a half-width tile 63 dp, anything taller than about 183 dp gets 64 dp.
     */
    const val CORNER_PERCENT = 36
    const val SMOOTHING = 65
    private const val PERCENT = 100f
    val MAX_CORNER = 64.dp

    /** A card's corner in px for a card of [size], for code that draws the outline itself. */
    fun cornerPx(size: Size, density: Density): Float =
        minOf(size.minDimension * CardTuning.corner / PERCENT, with(density) { CardTuning.cornerCap.dp.toPx() })

    /**
     * A fixed corner for what has no card of its own to measure: home screen widgets and strips drawn piece by piece.
     * Cards themselves use [CardShape].
     */
    val CardCorner = 36.dp
    val CardShape: Shape get() = CardTuning.shape
    /** Sport tiles, chart pills, text fields and panels inside a card: the same rule as a card. */
    val TileCorner = 27.dp
    val TileShape: Shape get() = CardShape
    /** The other corners (sheet, inner, chip, pip, pill) are in ui/theme/Scale.kt (`Corner`). Only cards are squircles. */

    /** From a card's edge to its content, on every side and every tier. */
    val CardInset = Space.S20
    val CardPadding = PaddingValues(CardInset)
    /** Above and below each list row; the panel adds the rest of [CardInset] at its top and bottom. */
    val RowPadding = Space.S12
    /** Inside a list: [CardInset] at the sides, and with the first and last row's [RowPadding] at the top and bottom. */
    val ListPadding = PaddingValues(horizontal = CardInset, vertical = CardInset - RowPadding)

    /** A chart's grid and other hairlines inside a card: white at 8% (spec 2.2, 2.7). */
    const val HairlineAlpha = 0.08f
    /**
     * The 1 dp edge round a card or list panel: white at 12%, a little over [HairlineAlpha] so a dark card still
     * reads against the black page on an OLED screen. What's inside the card (grid, dividers) stays fainter than it.
     */
    const val CardEdgeAlpha = 0.12f
    /** The divider between a list's rows: white at 6%. */
    const val DividerAlpha = 0.06f

    /** Title row to the hero number. */
    val TitleGap = Space.S12
    /** Every chart in a card has the same height, axis labels included. */
    val ChartHeight = 116.dp
    /** From this width (an unfolded foldable, a tablet, a phone on its side) the dashboards use two columns. */
    val TwoColumnWidth = 600.dp
    /** The widest a tab's two columns get together (spec 2.4 Unfolded); past it the content is centred. */
    val MaxContentWidth = 960.dp
    /** The side gutter on two columns (spec 2.4 Unfolded). */
    val UnfoldedGutter = Space.S24
    /**
     * The widest a page over a tab gets (spec 2.4 Unfolded): You, the Strap page and what they open, Ready, the sport
     * picker, Add a workout, a detail that isn't beside its tab. One centred column; a sheet is no wider either.
     */
    val PageMaxWidth = 600.dp
    /** Space between the two columns: the same as between cards in a column. */
    val ColumnGap = Space.S12
    /**
     * A dashboard's side gutter at [width]: on two columns [UnfoldedGutter], growing past it so the content is at most
     * [MaxContentWidth] and centred; on a phone [Gutter], 16 dp at every phone width.
     */
    fun dashboardGutter(width: Dp): Dp = when {
        width >= TwoColumnWidth -> maxOf(UnfoldedGutter, (width - MaxContentWidth) / 2)
        else -> Gutter
    }

    /**
     * The narrowest a Small gets (spec 2.4): about its width two across on a folded phone, (411 - 40 - 12) / 2, so
     * its title ("Active minutes") stays on one line and its note on two at most.
     */
    val SmallMinWidth = 176.dp

    /**
     * Whether each column of a two-column dashboard [width] wide holds a row of Smalls (two across, or a Tall beside
     * two) with each Small at least [SmallMinWidth]. When it doesn't (the Fold's inner screen), a row of Smalls spans
     * both columns instead of being squeezed into one.
     */
    fun smallsFitColumn(width: Dp): Boolean {
        val column = (width - dashboardGutter(width) * 2 - ColumnGap) / 2
        return GlowGrid.unitWidthIn(column) >= SmallMinWidth
    }

    /** Whether a dashboard [width] wide spans its rows of Smalls across both columns ([smallsFitColumn]). */
    fun smallsAcross(width: Dp): Boolean = width >= TwoColumnWidth && !smallsFitColumn(width)

    /**
     * The side padding that keeps a page's content in one centred column [column] wide at most (spec 2.4 Unfolded),
     * [gutter] inside it: just [gutter] on a phone, more on a wider screen.
     */
    fun pageSide(width: Dp, column: Dp, gutter: Dp): Dp =
        if (column == Dp.Unspecified) gutter else maxOf(gutter, (width - column) / 2 + gutter)

    /**
     * Which column each block of a two-column run goes in, by the blocks' measured heights (spec 2.4 Unfolded):
     * [left] and [right] as given, except that when one column ends well below the other, its last block moves to
     * the foot of the shorter one if that brings their ends closer. One move only, so the order stays close to the
     * phone's; a block is a whole grid row, so a pair is never split. Returns the blocks' indices per column, left
     * then right, counting [left]'s first and then [right]'s.
     */
    fun balanceColumns(left: List<Int>, right: List<Int>, gap: Int): Pair<List<Int>, List<Int>> {
        val l = left.indices.toMutableList()
        val r = right.indices.map { it + left.size }.toMutableList()
        val heights = left + right
        fun height(col: List<Int>) = col.sumOf { heights[it] } + gap * (col.size - 1).coerceAtLeast(0)
        val diff = height(l) - height(r)
        val (tall, short) = if (diff > 0) l to r else r to l
        if (tall.size > 1) {
            val moved = tall.last()
            val after = (height(tall) - heights[moved] - gap) - (height(short) + heights[moved] + gap)
            if (kotlin.math.abs(after) < kotlin.math.abs(diff)) { tall.removeAt(tall.lastIndex); short += moved }
        }
        return l to r
    }
}

/**
 * The widest a page's column gets ([GlowLayout.PageMaxWidth] over the app); [ScreenList] and the workout pages centre
 * their content in it. A dashboard lays out its own width, so [GlowScreenList] clears it.
 */
val LocalPageColumn = compositionLocalOf { GlowLayout.PageMaxWidth }

/** Where a dashboard block goes when there's room for two columns. On a phone every block stacks in the order given. */
enum class Pane { Full, Left, Right }

/**
 * The blocks of a dashboard, in phone order, each with the column it takes on a wide screen and the [key] its lazy
 * item goes by. A card's key is its card (the cards of its grid row, [cardsKey]), so hiding or pinning one in Edit Today
 * moves the saved state inside the others with them and keeps the scroll anchor on the same card (audit K5).
 */
class GlowBlocks internal constructor(
    /** On two columns too narrow for Smalls ([GlowLayout.smallsAcross]): a row of Smalls is a full block. */
    val smallsAcross: Boolean = false,
) {
    internal val blocks = mutableListOf<Block>()

    /** The block [GlowScreenList] scrolls to when asked ([focus]), or -1. */
    internal var focusBlock = -1

    /** Marks the next block as the one to scroll to. */
    fun focusNext() { focusBlock = blocks.size }
    fun full(content: @Composable () -> Unit) = add(Pane.Full, null, content)
    fun left(content: @Composable () -> Unit) = add(Pane.Left, null, content)
    fun right(content: @Composable () -> Unit) = add(Pane.Right, null, content)

    /**
     * Adds [content] to [pane], its lazy item keyed on [key]. Without one it goes by its place, as an item with no key
     * does, so a block added before it moves its state to another block.
     */
    fun add(pane: Pane, key: String?, content: @Composable () -> Unit) {
        blocks += Block(pane, key ?: "#${blocks.size}", content)
    }

    /** One block: its [pane], the [key] its lazy item goes by, and what it draws. */
    internal class Block(val pane: Pane, val key: String, val content: @Composable () -> Unit)

    internal companion object {
        /** The key of a dashboard's header block. */
        const val HEADER = "header"

        /** The key of a grid row holding [cards]: their names, in order. */
        fun cardsKey(cards: List<Any>): String = cards.joinToString("+")
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
        val gutter = GlowLayout.dashboardGutter(maxWidth)
        val built = GlowBlocks(smallsAcross = GlowLayout.smallsAcross(maxWidth)).apply(content)
        val blocks = built.blocks
        val keys = uniqueKeys(blocks.map { it.key })
        // The lazy item the marked block lands in: its own on a phone; on a wide screen a two-column run is one item.
        val target = built.focusBlock.takeIf { it >= 0 }?.let { if (wide) itemOf(blocks, it) else it }
        androidx.compose.runtime.LaunchedEffect(focus, target) {
            if (focus && target != null) { state.animateScrollToItem(target); onFocused() }
        }
        androidx.compose.runtime.CompositionLocalProvider(LocalPageColumn provides Dp.Unspecified) { HaloPageList(contentPadding, variant = PageVariant.Tab, state = state, gutter = gutter) {
            if (!wide) blocks.forEachIndexed { i, block -> item(keys[i]) { block.content() } }
            else sections(blocks.indices.toList()) { blocks[it].pane }.forEach { section ->
                val left = section.filter { blocks[it].pane == Pane.Left }
                val right = section.filter { blocks[it].pane == Pane.Right }
                // A full block, or a run with nothing on one side, takes the whole width rather than leave a hole.
                if (left.isEmpty() || right.isEmpty()) section.forEach { i -> item(keys[i]) { blocks[i].content() } }
                // Two columns are one item, keyed on every block in them.
                else item(section.joinToString("|") { keys[it] }) {
                    BalancedColumns(left.map { blocks[it].content }, right.map { blocks[it].content })
                }
            }
        } }
    }
}

/**
 * Two columns of blocks, [GlowLayout.ColumnGap] apart and between blocks, balanced by height
 * ([GlowLayout.balanceColumns]): the last block of a column that would end far below the other moves under the other.
 */
@Composable
private fun BalancedColumns(left: List<@Composable () -> Unit>, right: List<@Composable () -> Unit>) {
    // Each block in a box of its own, so a block that draws nothing (or several things) is still one child.
    Layout(content = { (left + right).forEach { androidx.compose.foundation.layout.Box(propagateMinConstraints = true) { it() } } }, modifier = Modifier.fillMaxWidth()) { measurables, cs ->
        val gap = GlowLayout.ColumnGap.roundToPx()
        val column = ((cs.maxWidth - gap) / 2).coerceAtLeast(0)
        val placeables = measurables.map { it.measure(Constraints.fixedWidth(column)) }
        val (l, r) = GlowLayout.balanceColumns(
            placeables.take(left.size).map { it.height }, placeables.drop(left.size).map { it.height }, gap,
        )
        fun height(col: List<Int>) = col.sumOf { placeables[it].height } + gap * (col.size - 1).coerceAtLeast(0)
        layout(cs.maxWidth, maxOf(height(l), height(r))) {
            listOf(l to 0, r to column + gap).forEach { (col, x) ->
                var y = 0
                col.forEach { placeables[it].place(x, y); y += placeables[it].height + gap }
            }
        }
    }
}

/** The lazy item that block [index] lands in on a wide screen, following [GlowScreenList]'s layout. */
private fun itemOf(blocks: List<GlowBlocks.Block>, index: Int): Int {
    var item = 0
    var seen = 0
    for (section in sections(blocks) { it.pane }) {
        val split = section.any { it.pane == Pane.Left } && section.any { it.pane == Pane.Right }
        if (index < seen + section.size) return if (split) item else item + (index - seen)
        item += if (split) 1 else section.size
        seen += section.size
    }
    return item
}

/** Splits the blocks into runs: each full block on its own, and each unbroken stretch of left and right blocks together. */
private fun <T> sections(blocks: List<T>, pane: (T) -> Pane): List<List<T>> {
    val out = mutableListOf<MutableList<T>>()
    blocks.forEach { b ->
        val last = out.lastOrNull()
        if (pane(b) != Pane.Full && last != null && pane(last.first()) != Pane.Full) last += b else out += mutableListOf(b)
    }
    return out
}

/**
 * [keys] made unique for a lazy list, which throws on a repeat: a key seen before gets its count after it. A dashboard
 * never repeats a card, so this only guards against a crash.
 */
internal fun uniqueKeys(keys: List<String>): List<String> {
    val seen = mutableMapOf<String, Int>()
    return keys.map { k ->
        val n = seen.getOrElse(k) { 0 }
        seen[k] = n + 1
        if (n == 0) k else "$k#$n"
    }
}

/**
 * Whether the card around this content opens something when tapped. [GlowCard] and [EdgeCard] provide it from their
 * `onClick`, so every card title ([CardTitle]) shows the tap caret on its own (spec 2.5, Tappable says so).
 */
val LocalCardOpens = compositionLocalOf { false }

/** The tap caret (spec 2.5): 14 dp, in [tint]: Tertiary after a card's title and Disabled at a row's end. */
@Composable
internal fun TapCaret(tint: Color, modifier: Modifier = Modifier) =
    Icon(AppIcons.TapCaret, null, tint = tint, modifier = modifier.size(CaretSize))

internal val CaretSize = 14.dp

/**
 * (i) on a card (spec 2.5): a 32 dp round button at 10% white with a 16 dp icon at 72%, at the right of the title
 * row. The same on every card that has an explanation; it opens that card's info sheet.
 */
@Composable
internal fun InfoButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // A card that opens a detail has no (i): the detail's header has it, so the card keeps one button, its chevron.
    if (LocalCardOpens.current) return
    if (LocalCardTint.current != null) {
        // On a coloured card, the same tinted circle as a chevron's.
        CardCircle(
            AppIcons.Info,
            modifier.clip(CircleShape).clickable(onClickLabel = label, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
                .semantics { contentDescription = label },
            iconSize = 16.dp,
        )
        return
    }
    HaloIconButton(
        AppIcons.Info, label, onClick, modifier, size = HaloIconSize.Small,
        tint = Ink.Secondary,
    )
}

internal val InfoButtonSize = IconSize.TileS

/** False inside a title row that places the chevron itself, after its note. */
// Like LocalCardOpens: every card title in the card reads it, and no title's caller could thread it through.
@Suppress("CompositionLocalAllowlist", "ComposeCompositionLocalUsage")
internal val LocalTitleChevron = compositionLocalOf { true }

/**
 * The way into what a card opens: a [ChevronCircleSize] circle at the card's top right with a right chevron. On a
 * grey card it's #0D0D0D, a step off the card; on a coloured card it's the card's own colour ([LocalCardTint]), so it
 * reads as part of the card whether the light reaches its corner or not.
 */
@Composable
internal fun ChevronCircle(modifier: Modifier = Modifier) = CardCircle(AppIcons.TapCaret, modifier)

/** A card's round button face: [ChevronCircle]'s circle and fill with [icon] in it. */
@Composable
internal fun CardCircle(icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, iconSize: Dp = 14.dp) {
    val tint = LocalCardTint.current
    Box(
        modifier.size(ChevronCircleSize).clip(CircleShape)
            .background(tint?.copy(alpha = CARD_TINT_ALPHA) ?: Greys.Raised),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(iconSize), tint = if (tint != null) Ink.Strong else Ink.Secondary) }
}

/** Set by a coloured card to its colour, so its buttons take that colour instead of grey. */
// Like LocalCardOpens: every button inside the card reads it, so it can't be threaded through each card's content.
@Suppress("CompositionLocalAllowlist", "ComposeCompositionLocalUsage")
internal val LocalCardTint = compositionLocalOf<Color?> { null }
private const val CARD_TINT_ALPHA = 0.25f
internal val ChevronCircleSize = IconSize.TileS

/** A title row's note, in Secondary on one line. */
@Composable
internal fun NoteText(text: String) =
    Text(text, color = Ink.Secondary, style = GlowType.secondary, maxLines = 1)

/**
 * A neutral tag next to a card's title (spec 4.3, E4): "Partial day", "First score". About the data, not the sync,
 * so white and not amber (spec 2.1). With [onClick], tapping it explains it (spec 7.1, one caveat per card).
 */
@androidx.compose.runtime.Immutable
data class TitleTag(val text: String, val onClick: (() -> Unit)? = null)

/**
 * What sits next to a card's title: the sync [marker] and the [tag], both when a card has a partial day and an "Up to"
 * time, each its own pill in the title's row.
 */
@Composable
internal fun TitleMark(marker: String?, tag: TitleTag?, modifier: Modifier = Modifier) {
    if (marker != null) HaloTag(marker, modifier, TagVariant.Marker)
    if (tag != null) HaloTag(tag.text, onClick = tag.onClick)
}

