package org.openstrap.app.ui.glow

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import sv.lib.squircleshape.SquircleShape

/** The most a corner's share of the shorter side can be: half, which makes the card a pill. */
private const val MAX_CORNER_PERCENT = 50f

/** The divisor that turns a percent into a share. */
private const val PERCENT = 100f

/** How many times [cornerPadding] re-settles the vertical padding against the height it itself adds to. */
private const val PADDING_SETTLE_PASSES = 4

/**
 * The card rule, one for every card, grey or lit (docs/design/halo/components-audit.md, 3.2 and 3.3): a squircle whose
 * corner is [CORNER] percent of the shorter side, never past [cornerCap], at [SMOOTHING]. The code once had 55 for
 * grey and lit cards here and 65 for [GlowLayout.CardShape]; it is 65 ([GlowLayout.SMOOTHING]) everywhere now, the
 * sheet's corner included. Held as state so a tuning tool can move it live.
 */
object CardTuning {
    /** 36% of the shorter side (HaloSystem board). */
    const val CORNER = GlowLayout.CORNER_PERCENT.toFloat()
    const val SMOOTHING = GlowLayout.SMOOTHING

    /** No corner is bigger than this, so a tall list or a tile keeps a card's corner, not a pill's. */
    const val CORNER_CAP = 64f
    /** Padding is [PADDING_BASE] plus this share of the card's own corner (17 Nov 2026). */
    const val PADDING_GROWTH = 15f
    const val PADDING_BASE = 16f

    var corner by mutableFloatStateOf(CORNER)
    var cornerCap by mutableFloatStateOf(CORNER_CAP)
    var paddingGrowth by mutableFloatStateOf(PADDING_GROWTH)

    /** Every card's shape now. Read in composition or drawing, so a slider move redraws the cards. */
    val shape: Shape get() = PercentSquircle(corner, SMOOTHING, cornerCap)

    /** The padding for a card whose corner is [cornerDp]. */
    fun paddingFor(cornerDp: Float): Float = PADDING_BASE + paddingGrowth / PERCENT * cornerDp

    /** A list's side padding: a list is tall, so its corner is the cap. */
    val listInset get() = paddingFor(cornerCap).dp
}

/** A card's corner in dp for a card [widthDp] by [heightDp], at [percent] of the shorter side, never past the cap. */
internal fun cornerDp(percent: Float, widthDp: Float, heightDp: Float): Float =
    minOf(minOf(widthDp, heightDp) * percent.coerceIn(0f, MAX_CORNER_PERCENT) / PERCENT, CardTuning.cornerCap)

/** A squircle whose corner is [percent] of the shape's shorter side, at most [capDp], at [smoothing] (0 to 100). */
@Immutable
internal data class PercentSquircle(val percent: Float, val smoothing: Int, val capDp: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val radius = with(density) {
            minOf(size.minDimension * percent.coerceIn(0f, MAX_CORNER_PERCENT) / PERCENT, capDp.dp.toPx()).toDp()
        }
        return SquircleShape(radius = radius, smoothing = smoothing).createOutline(size, layoutDirection, density)
    }
}

/**
 * A card's inside padding, grown with the card's own corner ([CardTuning.paddingFor]) so content keeps clear of a big
 * curve. The sides are set from the card's width (its corner if it were tall); the top and bottom from the height the
 * content comes to, so a short card keeps a short card's padding. [percent] is the card's corner share.
 */
internal fun Modifier.cornerPadding(percent: () -> Float): Modifier = layout { measurable, cs ->
    val pct = percent()
    val w = if (cs.hasBoundedWidth) cs.maxWidth.toDp().value else Float.MAX_VALUE
    val side = CardTuning.paddingFor(cornerDp(pct, w, Float.MAX_VALUE)).dp.roundToPx()
    // Top and bottom: settle the padding against the card's own height, which the padding itself adds to.
    fun vertical(contentH: Int): Int {
        var pad = side
        repeat(PADDING_SETTLE_PASSES) {
            val h = (contentH + 2 * pad).toDp().value
            pad = CardTuning.paddingFor(cornerDp(pct, w, h)).dp.roundToPx()
        }
        return pad
    }
    val fixedH = cs.hasFixedHeight
    val topGuess = if (fixedH) CardTuning.paddingFor(cornerDp(pct, w, cs.maxHeight.toDp().value)).dp.roundToPx() else 0
    val inner = cs.copy(minHeight = 0, minWidth = 0).offset(-2 * side, if (fixedH) -2 * topGuess else 0)
    val p = measurable.measure(inner)
    val top = if (fixedH) topGuess else vertical(p.height)
    val width = (p.width + 2 * side).coerceIn(cs.minWidth, cs.maxWidth)
    val height = (p.height + 2 * top).coerceIn(cs.minHeight, cs.maxHeight)
    layout(width, height) { p.place(side, top) }
}
