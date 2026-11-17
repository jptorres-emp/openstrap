package org.openstrap.app.ui.glow

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.openstrap.app.ui.components.LocalCardNumbers
import org.openstrap.app.ui.components.cardOpening
import org.openstrap.app.ui.components.pressClickable
import org.openstrap.app.ui.glow.draw.GlowStyle
import org.openstrap.app.ui.theme.Greys
import org.openstrap.app.ui.theme.Ink
import org.openstrap.app.ui.theme.Inset
import org.openstrap.app.ui.theme.LocalSurface
import org.openstrap.app.ui.theme.OnSurface
import org.openstrap.app.ui.theme.Space

/*
 * The one card (docs/design/halo/components-audit.md, 3.2 and 3.3): a hero, a tile, a strip, a panel of rows or a
 * panel of free content are all [HaloCard] with a tone and a size. One corner rule, one smoothing, one padding rule,
 * one gap between blocks. The light of a lit card is drawn by [LitSurface] (GlowCard.kt) and the draw/ code under it,
 * which the card hosts and does not redo.
 */

/** The card's measurements. The corner and the padding follow the card rule in [CardTuning]. */
internal object CardSpec {
    /** The corner: 36% of the card's shorter side, at most 64 ([CardTuning]). */
    const val CORNER_PERCENT = CardTuning.CORNER
    val CornerCap = CardTuning.CORNER_CAP.dp

    /** One smoothing for every card (it was 55 on grey and lit cards and 65 on [GlowLayout.CardShape]). */
    const val SMOOTHING = GlowLayout.SMOOTHING

    /** Between the blocks of a free card. */
    val Gap = Space.S12

    /** The fixed sizes' least heights. */
    val SmallHeight = 200.dp
    val WideHeight = 160.dp
    val StripHeight = 64.dp

    /** An outline card's ring. */
    val OutlineWidth = 1.5.dp

    /** From the card's edge to its content: 16 plus 15% of the card's own corner, so 22 to 26 ([Inset.card]). */
    fun inset(cornerDp: Float): Dp = Inset.card(cornerDp)

    /** A strip's sides: the inset of a card as high as a strip. Above and below its words it keeps [StripPad]. */
    val StripInset: Dp get() = inset(StripHeight.value * CORNER_PERCENT / PERCENT)
    val StripPad = Space.S12

    private const val PERCENT = 100f
}

/** What a card looks like. */
@Immutable
internal sealed interface CardTone {
    /** Lit from an angle by its own colour: a hero. Its title is a size up ([GlowType.heroTitle]). */
    data class Lit(val style: GlowStyle) : CardTone

    /** Lit up to a level, as Charge and Load are: a lit tile. */
    data class LitLevel(val style: GlowStyle) : CardTone

    /** The grey card ([Greys.Card]), unlit. */
    data object Grey : CardTone

    /** Only a ring, for something on hold or gone. */
    data object Outline : CardTone

    companion object {
        /** The lit tone for [style]: [LitLevel] when it carries a level, else [Lit]. */
        fun lit(style: GlowStyle): CardTone = if (style.lightLevel != null) LitLevel(style) else Lit(style)
    }
}

/** How high a card is. Small, Wide and Strip are least heights: a card grows when its words need it. */
internal enum class HaloCardSize(val height: Dp?) {
    /** A tile on the grid. */
    Small(CardSpec.SmallHeight),

    /** A wide tile on the grid. */
    Wide(CardSpec.WideHeight),

    /** One line across the page, its words centred between [CardSpec.StripInset] sides. */
    Strip(CardSpec.StripHeight),

    /** As high as its content. */
    Free(null),
    ;

    /** A free card's blocks are [CardSpec.Gap] apart; a tile lays out its own picture and number; a strip is centred. */
    val arrangement: Arrangement.Vertical get() = when (this) {
        Free -> Arrangement.spacedBy(CardSpec.Gap)
        Strip -> Arrangement.Center
        Small, Wide -> Arrangement.Top
    }
}

/** What sits inside a card's edge. */
internal enum class CardFill {
    /** Content inset by the card rule ([CardSpec.inset]), blocks [CardSpec.Gap] apart. */
    Inset,

    /** Rows ([HaloRow]) edge to edge, each carrying the side inset ([LocalRowInset]), so a pressed row lights the width. */
    Rows,

    /** Nothing: the content places itself (a map, a picture to the edge). */
    None,
}

/** True inside a [CardTone.Lit] card, so its title is a hero's. */
// Like LocalCardOpens: every title in the card reads it, and no title's caller could thread it through.
@Suppress("CompositionLocalAllowlist", "ComposeCompositionLocalUsage")
internal val LocalCardHero = compositionLocalOf { false }

/**
 * The one card. [tone] is its look, [size] its least height, [fill] what sits inside its edge. [title] is the card's
 * [HaloTitle], placed first with the card's own inset (in a card of rows it gets the sides the rows have). With
 * [onClick] the card is a button and grows into what it opens (spec 3.5). [swell] lifts a lit card's light for a moment.
 */
// The body goes in one of two surfaces by the tone, which a card keeps for its life but for the summary's drop.
@Suppress("ContentSlotReused", "ComposeContentSlotReused")
@Composable
internal fun HaloCard(
    modifier: Modifier = Modifier,
    tone: CardTone = CardTone.Grey,
    size: HaloCardSize = HaloCardSize.Free,
    fill: CardFill = CardFill.Inset,
    onClick: (() -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    verticalArrangement: Arrangement.Vertical = size.arrangement,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    swell: GlowSwell? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sized = modifier.fillMaxWidth().let { m -> size.height?.let { m.heightIn(min = it) } ?: m }
    val rows = fill == CardFill.Rows
    val body: @Composable ColumnScope.() -> Unit = {
        if (title != null) {
            if (rows) Column(Modifier.padding(start = Inset.list, end = Inset.list, top = Inset.list, bottom = Space.S8)) { title() }
            else title()
        }
        if (rows) CompositionLocalProvider(LocalRowInset provides Inset.list) { content() } else content()
    }
    val style = (tone as? CardTone.Lit)?.style ?: (tone as? CardTone.LitLevel)?.style
    if (style != null) {
        // Parts on a lit card were drawn for a grey card, so they step up from Card.
        CompositionLocalProvider(LocalCardHero provides (tone is CardTone.Lit), LocalSurface provides Greys.Card) {
            val padded = fill == CardFill.Inset && size != HaloCardSize.Strip
            LitSurface(style, sized, onClick = onClick, padded = padded, swell = swell) {
                Column(
                    Modifier.fillMaxWidth().stripSides(size), verticalArrangement = verticalArrangement,
                    horizontalAlignment = horizontalAlignment, content = body,
                )
            }
        }
    } else {
        GreySurface(tone == CardTone.Outline, size, fill, onClick, verticalArrangement, horizontalAlignment, sized, body)
    }
}

/** A grey card's surface, or an outline's: the card rule's shape, its press and its growing into what it opens. */
@Composable
private fun GreySurface(
    outline: Boolean,
    size: HaloCardSize,
    fill: CardFill,
    onClick: (() -> Unit)?,
    verticalArrangement: Arrangement.Vertical,
    horizontalAlignment: Alignment.Horizontal,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = CardTuning.shape
    // One step above what it sits on: Card on the page, Control on a sheet (components audit, grey on a sheet).
    val panel = OnSurface.panel
    // Tapped, the card grows into what it opens (spec 3.5).
    val opening = cardOpening(onClick, shape)
    Column(
        modifier
            .then(opening?.modifier ?: Modifier)
            .let { if (onClick != null) it.pressClickable(opening?.onClick ?: onClick, shape) else it }
            .clip(shape)
            .let { if (outline) it.border(CardSpec.OutlineWidth, Ink.Line, shape) else it.background(panel, shape) }
            .let {
                when {
                    size == HaloCardSize.Strip -> it.stripSides(size)
                    fill == CardFill.Inset -> it.cornerPadding { CardTuning.corner }
                    else -> it
                }
            },
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
    ) {
        CompositionLocalProvider(
            LocalCardNumbers provides opening?.numbers, LocalCardOpens provides (onClick != null),
            LocalSurface provides if (outline) LocalSurface.current else panel,
        ) {
            content()
        }
    }
}

/** A strip's padding: its sides from the card rule, a little above and below so its words stay centred. */
private fun Modifier.stripSides(size: HaloCardSize): Modifier =
    if (size == HaloCardSize.Strip) padding(horizontal = CardSpec.StripInset, vertical = CardSpec.StripPad) else this
