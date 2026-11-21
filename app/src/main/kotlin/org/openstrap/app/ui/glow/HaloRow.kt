package org.openstrap.app.ui.glow

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.openstrap.app.ui.components.LocalCardNumbers
import org.openstrap.app.ui.components.cardOpening
import org.openstrap.app.ui.theme.AppIcons
import org.openstrap.app.ui.theme.Corner
import org.openstrap.app.ui.theme.Greys
import org.openstrap.app.ui.theme.IconSize
import org.openstrap.app.ui.theme.Ink
import org.openstrap.app.ui.theme.OnSurface
import org.openstrap.app.ui.theme.Space

/*
 * The one row (docs/design/halo/components-audit.md, 3.5): a lead, a title, a line under it, a value, a trail, and a
 * slot under the text. Every list in the app is this row with a different set of slots, so every list shares one
 * height, one padding, one side inset and one divider; only the data differs. The named helpers below (NavRow,
 * SwitchRow, MetricRow and the rest) are slot combinations of [HaloRow], not other layouts.
 */

/** The row's measurements, all from the scale (ui/theme/Scale.kt) bar the two heights the scale has no step for. */
internal object RowSpec {
    /** A row's least height, and with a line or a slot under the title. */
    val MinHeight = 56.dp
    val MinHeightTall = 64.dp

    /** A compact row of a card (a fact, a split): its own height, no padding above or below. */
    val MinHeightCompact = IconSize.TileS

    /** Above and below the content, and between the slots. */
    val Pad = Space.S12
    val Gap = Space.S12

    /** Between the title and its line, and from the text to the slot under it. */
    val LineGap = Space.S2
    val BelowGap = Space.S8

    /** The lead: a tile with its glyph, a colour pip, a sport tile, a medal. */
    val Tile = TileSize.Small.size
    val Glyph = TileSize.Small.glyph
    val Pip = 10.dp
    val Sport = TileSize.Medium.size
    val Medal = TokenSize.Next.size

    /** The divider's thickness. */
    val Divider = 1.dp

    /** The trail: a radio's ring, an action pill's height. */
    val Radio = 22.dp
    val Action = 36.dp

    /** A tile's tint: the metric's ink at this share. */
    const val TILE_TINT = TileSpec.INK_TINT
    const val WAITING_GLYPH = TileSpec.WAITING_GLYPH
    const val DISABLED = 0.45f
}

/**
 * How far a row's content sits in from its card's edge: the card's own inset ([org.openstrap.app.ui.theme.Inset.list]),
 * which the list, panel or card gives to the rows in it. A row outside a card gets 0, so every list lines up the same
 * distance from the card edge. The rows span the card edge to edge, so a tap or a selection lights the whole width.
 */
@Suppress("CompositionLocalAllowlist", "ComposeCompositionLocalUsage")
internal val LocalRowInset = staticCompositionLocalOf { 0.dp }

/** Pads what is not a row (a header, a note) inside a list to the rows' side inset. */
@Composable
@ReadOnlyComposable
internal fun Modifier.rowSides(): Modifier = padding(horizontal = LocalRowInset.current)

/** What sits before a row's title. [size] is its width, which the slot under the title lines up with. */
internal class RowLead(val size: Dp, val content: @Composable () -> Unit) {
    companion object {
        /** A 32 circle with a 16 glyph: neutral grey (settings) or the metric's [ink] at 16% ([dim] for a waiting row). */
        fun tile(icon: ImageVector, ink: Color? = null, dim: Boolean = false) = RowLead(RowSpec.Tile) {
            HaloTile(icon, tone = ink?.let { TileTone.Ink(it, dim) } ?: TileTone.Neutral)
        }

        /** A goal's [share] as a small ring in the tile's place. */
        fun ring(share: Float, color: Color) = RowLead(RowSpec.Tile) {
            HaloProgressRing({ share }, color, Modifier.size(RowSpec.Tile).padding(Space.S4))
        }

        /** A 10 square in a colour: a heart rate zone. */
        fun pip(color: Color) = RowLead(RowSpec.Pip) { Box(Modifier.size(RowSpec.Pip).clip(Corner.Pip).background(color)) }

        /** A glyph with no tile: an action's icon, in [ink]. */
        fun glyph(icon: ImageVector, ink: Color) = RowLead(IconSize.GlyphM) { Icon(icon, null, Modifier.size(IconSize.GlyphM), tint = ink) }

        /** A 44 sport tile, tinted [ink] at 16%, with its glyph in [ink]. */
        fun sport(icon: ImageVector, ink: Color) = RowLead(RowSpec.Sport) {
            HaloTile(icon, size = TileSize.Medium, tone = TileTone.Ink(ink), glyph = ink)
        }

        /** Anything else of a size: the effort light on a workout. */
        fun custom(size: Dp, content: @Composable () -> Unit) = RowLead(size, content)

        /** A 56 medal. */
        fun medal(content: @Composable () -> Unit) = RowLead(RowSpec.Medal, content)
    }
}

/** What ends a row. Each is a slot for [HaloRow]'s `trail`. */
internal object RowTrail {
    /** The small chevron of a row that opens something: 14, [Ink.Tertiary]. */
    @Composable
    fun Chevron(modifier: Modifier = Modifier) = Icon(AppIcons.TapCaret, null, modifier.size(IconSize.Caret), tint = Ink.Tertiary)

    /** The down caret of a row that opens a menu. */
    @Composable
    fun Down(modifier: Modifier = Modifier) = Icon(AppIcons.CaretDown, null, modifier.size(IconSize.Caret), tint = Ink.Tertiary)

    /** The switch: the row is the switch to a screen reader, so the knob isn't read a second time. */
    @Composable
    fun Toggle(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) =
        GlowSwitch(checked, onChange, modifier.clearAndSetSemantics {}, enabled = enabled)

    /** A tick for the chosen row of a list. */
    @Composable
    fun Check(modifier: Modifier = Modifier) = Icon(AppIcons.Tick02, null, modifier.size(IconSize.GlyphM), tint = Color.White)

    /** A 22 ring that fills white when [picked]. */
    @Composable
    fun Radio(picked: Boolean, modifier: Modifier = Modifier) {
        Box(
            modifier.size(RowSpec.Radio).clip(Corner.Pill)
                .let { if (picked) it.background(Color.White) else it.border(ChipSpec.Ring, Greys.Line, Corner.Pill) },
            contentAlignment = Alignment.Center,
        ) { if (picked) Box(Modifier.size(Space.S8).clip(Corner.Pill).background(Color.Black)) }
    }

    /** One action pill, 36 high. */
    @Composable
    @NonRestartableComposable
    fun Action(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) =
        HaloButton(text, onClick, modifier, size = HaloButtonSize.Small)
}

/**
 * The row. [title] on one line, its [line] under it (grey, up to two lines, or a tag), a [value] on the right
 * ([valueInk]: white at 80% when tapping opens a picker, grey when read only; a waiting dash when null and [dash]), a
 * [trail], and [below], a slot under the text that runs to the row's end, lined up with the title. [lead] goes before
 * the title. A row with a [line] or a [below] is 64 tall at least, any other 56; a [compact] row is 32 with no padding.
 * The sides come from the card it sits in ([LocalRowInset]); [divider] draws the 1 dp line along the top, in line
 * with the content.
 *
 * Without a [line] a value shares the title's line, or drops under it where both don't fit ([RowNameValue]), so the
 * title never wraps. [titleSuffix] follows the title on its line (a dot, a tag). [middle] fills the room between a
 * fixed-width title ([titleWidth]) and the value (a split's bar). [onClick] makes the row tappable and, with
 * [grows], lets it grow into what it opens. A [titleLines] over one lets a title of choices wrap rather than cut it.
 * [tapModifier] replaces the tap for a row that toggles or picks.
 */
// The value's modifier goes to exactly one of two places, the shared column or the value's own.
@Suppress("CyclomaticComplexMethod", "ModifierReused", "ComposeModifierReused")
@Composable
internal fun HaloRow(
    title: String,
    modifier: Modifier = Modifier,
    valueModifier: Modifier = Modifier,
    tapModifier: Modifier = Modifier,
    lead: RowLead? = null,
    titleInk: Color = Color.White,
    titleSuffix: (@Composable () -> Unit)? = null,
    line: (@Composable () -> Unit)? = null,
    value: String? = null,
    valueInk: Color = Ink.Strong,
    valueStyle: TextStyle = GlowType.rowValue,
    dash: Boolean = false,
    trail: (@Composable () -> Unit)? = null,
    below: (@Composable () -> Unit)? = null,
    middle: (@Composable RowScope.() -> Unit)? = null,
    titleWidth: Dp? = null,
    titleLines: Int = 1,
    onClick: (() -> Unit)? = null,
    role: Role? = null,
    clickLabel: String? = null,
    divider: Boolean = true,
    enabled: Boolean = true,
    selected: Boolean = false,
    compact: Boolean = false,
    grows: Boolean = false,
) {
    val inset = LocalRowInset.current
    val opening = if (grows) cardOpening(onClick) else null
    val tap = opening?.onClick ?: onClick
    val minHeight = when {
        compact -> RowSpec.MinHeightCompact
        line != null || below != null -> RowSpec.MinHeightTall
        else -> RowSpec.MinHeight
    }
    // Without a line, a middle or a suffix the value shares the title's column; otherwise it has a column of its own.
    val shared = line == null && middle == null && titleSuffix == null
    val frame: @Composable () -> Unit = {
        Column(
            modifier.fillMaxWidth()
                .rowSelected(selected)
                .then(opening?.modifier ?: Modifier)
                .let { if (tap != null) it.clickable(enabled = enabled, onClickLabel = clickLabel, role = role, onClick = tap) else it }
                .then(tapModifier)
                .insetHairline(divider, inset, OnSurface.line)
                .alpha(if (enabled) 1f else RowSpec.DISABLED)
                .heightIn(min = minHeight)
                .padding(horizontal = inset, vertical = if (compact) 0.dp else RowSpec.Pad),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(
                Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(RowSpec.Gap),
            ) {
                lead?.content?.invoke()
                val textColumn = if (titleWidth != null) Modifier.width(titleWidth) else Modifier.weight(1f)
                RowText(
                    title, titleInk, titleSuffix, line, value.takeIf { shared }, valueInk, valueStyle, titleLines,
                    textColumn, valueModifier,
                )
                middle?.invoke(this)
                if (!shared || value == null) RowValue(value, valueInk, valueStyle, dash, valueModifier)
                trail?.invoke()
            }
            if (below != null) Box(
                Modifier.fillMaxWidth().padding(start = lead?.let { it.size + RowSpec.Gap } ?: 0.dp, top = RowSpec.BelowGap),
            ) { below() }
        }
    }
    if (opening != null) CompositionLocalProvider(LocalCardNumbers provides opening.numbers) { frame() } else frame()
}

/** The title with what shares its column: a suffix, a line under it, or (without a line) the value beside it or dropped under. */
@Composable
private fun RowText(
    title: String,
    titleInk: Color,
    suffix: (@Composable () -> Unit)?,
    line: (@Composable () -> Unit)?,
    sharedValue: String?,
    valueInk: Color,
    valueStyle: TextStyle,
    titleLines: Int,
    modifier: Modifier = Modifier,
    valueModifier: Modifier = Modifier,
) {
    if (sharedValue != null) RowNameValue(
        title, titleInk, sharedValue, modifier, valueModifier = valueModifier, valueColor = valueInk, valueStyle = valueStyle,
    ) else Column(modifier, verticalArrangement = Arrangement.spacedBy(RowSpec.LineGap)) {
        if (suffix != null) Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title, Modifier.weight(1f, fill = false), color = titleInk, style = GlowType.body,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(Space.S8))
            suffix()
        } else if (titleLines > 1) {
            Text(title, color = titleInk, style = GlowType.body, maxLines = titleLines, overflow = TextOverflow.Ellipsis)
        } else RowName(title, titleInk)
        line?.invoke()
    }
}

/** The value in its own column on the right: one line, the waiting dash when there is none and [dash]. */
@Composable
private fun RowValue(value: String?, ink: Color, style: TextStyle, dash: Boolean, modifier: Modifier = Modifier) {
    if (value != null) Text(value, modifier, color = ink, style = style, maxLines = 1)
    else if (dash) WaitingDash(modifier)
}

/** A row's line: grey [GlowType.secondary], up to two lines. */
@Composable
internal fun RowLine(text: String, ink: Color = GlowType.noteInk) =
    Text(text, color = ink, style = GlowType.secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)

/** The tint of the [selected] row whose page is open beside the list, across the card. */
private fun Modifier.rowSelected(selected: Boolean): Modifier =
    if (!selected) this else semantics { this.selected = true }.background(SelectedTint)

/**
 * The divider: 1 dp in [color] (one step above the surface, [OnSurface.line]) along the top, from the side [inset] to the
 * other, so it keeps off the card's edges.
 */
private fun Modifier.insetHairline(show: Boolean, inset: Dp, color: Color): Modifier =
    if (!show) this else drawBehind {
        val e = inset.toPx()
        drawLine(color, Offset(e, 0f), Offset(size.width - e, 0f), RowSpec.Divider.toPx())
    }

/** A [HaloRow] whose page is open beside the list. */
private val SelectedTint = Ink.Faint
