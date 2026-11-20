package org.openstrap.app.ui.glow

import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.openstrap.app.R
import org.openstrap.app.ui.components.GlassSheet
import org.openstrap.app.ui.theme.Corner
import org.openstrap.app.ui.theme.Greys
import org.openstrap.app.ui.theme.Ink
import org.openstrap.app.ui.theme.Space

/*
 * The one sheet (docs/design/halo/components-audit.md, 3.14): a bottom sheet is [HaloSheet] with a variant, a title, an
 * optional note, its content and a footer (one [HaloButton] or a [ButtonPair]). Every measurement is in [SheetSpec].
 * [GlassSheet] stays the low-level surface under it (the portal, the fill, the corner, the grabber, pull to dismiss and
 * the 600 wide cap), reading its grabber and top from [SheetSpec] too.
 */

/** The measurements of every sheet, from the scale (ui/theme/Scale.kt) bar the grabber and its top, which it has no step for. */
internal object SheetSpec {
    /**
     * The sheet's fill: [Greys.Card], the same as a card (designer, 17 Nov 2026). [GlassSheet] provides it as the
     * [org.openstrap.app.ui.theme.LocalSurface], so a panel on a sheet is Raised, a control or a track on that panel
     * Control, and a picked state Picked, one step up a level as inside a card.
     */
    val Fill = Greys.Card

    /** The grabber: 36 by 4, centred, [Ink.Line], this far from the sheet's top edge. */
    val GrabberWidth = 36.dp
    val GrabberHeight = Space.S4
    val Top = 10.dp

    /** From the sides of the sheet to its content: a page's 20, and 16 for a sheet of grouped lists that bring their own inset. */
    val Side = Space.S20
    val SideGrouped = Space.S16

    /** Under the content, above the screen's bottom edge (the navigation bar adds its own). */
    val Bottom = Space.S32

    /** Between the grabber and the first block, and between blocks. */
    val Gap = Space.S16

    /** Between the parts of a token sheet (the medal, its group, its name, how it is earned). */
    val TokenGap = Space.S8

    /** Between the title and its note. */
    val TitleNoteGap = Space.S4

    /** Inside an info sheet's section: its heading over its words. */
    val SectionGap = Space.S8

    /** An info sheet's body line height, and its scale's bars: 10 high with 8 between them. */
    val ReadingLine = 22.5.sp
    val ScaleBar = 10.dp
    val ScaleGap = Space.S8

    /** The bar of the metric's colour beside an info sheet's title: 4 by 24 with 8 after it. */
    val AccentWidth = Space.S4
    val AccentHeight = Space.S24
    val AccentGap = Space.S8

    /** A token sheet's centred lines are held in from the sides by this much more. */
    val TokenLineSide = Space.S8

    /** An unlit scale bar's opacity: 18% for the lowest, rising by 12% across the bars. */
    const val DIM_FIRST = 0.18f
    const val DIM_RISE = 0.12f

    /** A sheet scrolls when taller than this share of the screen. */
    const val MAX_HEIGHT = 0.88f

    /** The title's type, and how many lines each variant lets it have. */
    val Title = GlowType.sheetTitle
}

/** Which kind of sheet: it sets the sides, the gap between blocks, how the column aligns and how many lines a title may take. */
internal enum class HaloSheetVariant(val side: Dp, val gap: Dp, val centred: Boolean, val titleLines: Int) {
    /** A picker, a form, a question: left aligned, 20 from the sides, 16 between blocks. */
    Page(SheetSpec.Side, SheetSpec.Gap, false, 1),

    /** A sheet of grouped list cards (All metrics), which bring their own inset: 16 from the sides. */
    Grouped(SheetSpec.SideGrouped, SheetSpec.Gap, false, 1),

    /** A reading sheet (what a number means): [Page]'s measurements and a title that may take two lines. */
    Info(SheetSpec.Side, SheetSpec.Gap, false, 2),

    /** A badge or a best: a centred column, 8 between parts, the title centred. */
    Token(SheetSpec.Side, SheetSpec.TokenGap, true, 2),
}

/**
 * A sheet over the page, scrolling when taller than [SheetSpec.MAX_HEIGHT] of the screen. The [title] is 22 (left
 * aligned, centred on a [HaloSheetVariant.Token]), the optional [note] 4 under it. [lead] sits above the title (a token
 * sheet's medal and its group); [accent] puts the metric's colour bar before the title (an info sheet). Without a
 * [title] the content starts at the top, for a sheet that heads itself (the calendar's month). [footer] is the sheet's
 * one [HaloButton] or [ButtonPair], last in the column.
 */
@Composable
internal fun HaloSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    note: String? = null,
    variant: HaloSheetVariant = HaloSheetVariant.Page,
    lead: (@Composable ColumnScope.() -> Unit)? = null,
    accent: Color? = null,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val maxHeight = with(LocalDensity.current) { (LocalWindowInfo.current.containerSize.height * SheetSpec.MAX_HEIGHT).toDp() }
    GlassSheet(onDismiss) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = maxHeight).verticalScroll(rememberScrollState())
                .padding(horizontal = variant.side).navigationBarsPadding().padding(top = SheetSpec.Gap, bottom = SheetSpec.Bottom),
            verticalArrangement = Arrangement.spacedBy(variant.gap),
            horizontalAlignment = if (variant.centred) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            lead?.invoke(this)
            if (title != null) Column(
                Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SheetSpec.TitleNoteGap),
                horizontalAlignment = if (variant.centred) Alignment.CenterHorizontally else Alignment.Start,
            ) {
                val align = if (variant.centred) TextAlign.Center else TextAlign.Start
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (accent != null) {
                        Box(Modifier.size(SheetSpec.AccentWidth, SheetSpec.AccentHeight).background(accent, Corner.Pill))
                        Spacer(Modifier.width(SheetSpec.AccentGap))
                    }
                    Text(
                        title, Modifier.weight(1f), color = Color.White, style = SheetSpec.Title,
                        textAlign = align, maxLines = variant.titleLines, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (note != null) {
                    Text(note, Modifier.fillMaxWidth(), color = Ink.Secondary, style = GlowType.secondary, textAlign = align)
                }
            }
            content()
            footer?.invoke()
        }
    }
}

/**
 * A question before something that can't be taken back (Forget, Disconnect, Delete all): a [HaloSheet] with the [text],
 * any extra [content], then Cancel and the [confirm] button as its [ButtonPair]: white, or dark with red text when
 * [destructive] (HaloDeleteSleepSheet board).
 */
@Composable
internal fun HaloConfirmSheet(
    title: String,
    text: String?,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismiss: String = stringResource(R.string.glow_cancel),
    confirmEnabled: Boolean = true,
    destructive: Boolean = false,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    HaloSheet(
        onDismiss, title,
        footer = {
            ButtonPair(
                dismiss, onDismiss, confirm, onConfirm, rightEnabled = confirmEnabled,
                rightVariant = if (destructive) HaloButtonVariant.Destructive else HaloButtonVariant.Primary,
            )
        },
    ) {
        if (text != null) Text(text, color = Ink.Strong, style = GlowType.body)
        content?.invoke(this)
    }
}

/** A section of an info sheet: a [title] in Secondary over its [body] in Body at the reading line height, 8 apart. */
@Composable
internal fun SheetSection(title: String, body: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SheetSpec.SectionGap)) {
        Text(title, color = GlowType.noteColor, style = GlowType.secondary)
        Text(body, color = Ink.Primary, style = GlowType.body.copy(lineHeight = SheetSpec.ReadingLine))
    }
}

/** A centred line of a token sheet: its group and the notes in Secondary, what it is in Body, the rule in Footnote. */
@Composable
internal fun SheetTokenLine(text: String, color: Color = GlowType.noteColor, style: TextStyle = GlowType.secondary) =
    Text(
        text, color = color, style = style, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = SheetSpec.TokenLineSide),
    )

/**
 * The scale of an info sheet as coloured bars, low to high, each named under it; the bar you're in is lit and glows. A
 * name may wrap between its words but never inside one: when the widest word of any name doesn't fit its bar, every
 * name steps down to the caption role together, so the row keeps one type size from the design system.
 */
@Suppress("UnstableCollections", "ComposeUnstableCollections")
@Composable
internal fun SheetScale(labels: List<String>, color: Color, here: Int?, modifier: Modifier = Modifier) {
    val gap = SheetSpec.ScaleGap
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cell = ((constraints.maxWidth - with(LocalDensity.current) { gap.roundToPx() } * (labels.size - 1)) / labels.size)
        val style = remember(labels, cell) { fitWords(labels, listOf(GlowType.secondary, GlowType.caption), cell, measurer) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            labels.forEachIndexed { i, label ->
                val on = i == here
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SheetSpec.SectionGap)) {
                    Box(
                        Modifier.fillMaxWidth().height(SheetSpec.ScaleBar)
                            .let { if (on) it.shadow(SheetSpec.ScaleBar, Corner.Pill, ambientColor = color, spotColor = color) else it }
                            .background(if (on) color else color.copy(alpha = dim(i, labels.size)), Corner.Pill),
                    )
                    Text(
                        label, color = if (on) Color.White else Ink.Secondary,
                        style = style.copy(fontWeight = if (on) FontWeight.Medium else FontWeight.Normal),
                    )
                }
            }
        }
    }
}

/** An unlit bar's opacity: 18% for the first, rising to 30% for the last. */
private fun dim(i: Int, count: Int): Float = SheetSpec.DIM_FIRST + SheetSpec.DIM_RISE * i / (count - 1).coerceAtLeast(1)

/**
 * The first of [roles] in which the widest single word of [labels] fits [width] px on one line, measured at Medium,
 * the lit bar's weight and the wider one. The last, the smallest, when none does.
 */
private fun fitWords(labels: List<String>, roles: List<TextStyle>, width: Int, measurer: TextMeasurer): TextStyle {
    val words = labels.flatMap { it.split(' ') }.filter { it.isNotEmpty() }
    if (words.isEmpty() || width <= 0) return roles.first()
    fun widest(s: TextStyle) = words.maxOf {
        measurer.measure(it, s.copy(fontWeight = FontWeight.Medium), softWrap = false, maxLines = 1).size.width
    }
    return roles.firstOrNull { widest(it) <= width } ?: roles.last()
}
