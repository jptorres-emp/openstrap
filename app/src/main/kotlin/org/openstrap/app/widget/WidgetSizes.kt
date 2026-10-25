package org.openstrap.app.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * The three sizes every widget has (widgets spec 2.1), so a stack that gives all its widgets one size fits each of
 * them. Each is the smallest cell its layout needs, not the size it was drawn at (173 × 176, 358 × 176 and
 * 358 × 272 dp in the mockups): launchers' cells run smaller (One UI's 2 × 2 can be 167 dp wide, its 4 columns
 * 353), and every layout stretches to its cell. Every provider's XML resizes from [MIN_DP] up to [MaxResize]
 * (spec 6.1), and Glance picks the largest of these that fits the cell ([bucket]).
 */
object WidgetSizes {
    /** 2 × 2: a number and a word, or the one action. */
    val Small = DpSize(150.dp, 150.dp)

    /** 4 × 2: the Small plus one more thing. */
    val Wide = DpSize(300.dp, 160.dp)

    /** 4 × 3: everything the widget has. */
    val Large = DpSize(300.dp, 260.dp)

    /**
     * `minWidth`, `minHeight` and their resize limits, dp: two cells by the old formula (70 × 2 − 30), which One UI
     * still uses and which ignores `targetCellWidth`. Anything above it, even the [Small]'s 150, places as 3 × 3.
     */
    const val MIN_DP = 110

    /** As far as a widget resizes: wider than four phone columns for an unfolded screen, about four rows high. */
    val MaxResize = DpSize(480.dp, 400.dp)

    /** All three, smallest first: what `SizeMode.Responsive` is given. */
    val all: Set<DpSize> = linkedSetOf(Small, Wide, Large)

    /** Cells a new widget takes (`targetCellWidth`, `targetCellHeight`): a Small. */
    const val TARGET_CELLS = 2

    /** Every card's corner and padding (spec 2.4), dp. */
    const val CORNER_DP = 28f
    const val PADDING_DP = 20f

    /**
     * The size Glance lays out for a widget [width] by [height] dp: the largest of [sizes] that fits, or the smallest
     * when none does (as `SizeMode.Responsive` picks).
     */
    fun bucket(width: Float, height: Float, sizes: Set<DpSize> = all): DpSize {
        val fits = sizes.filter { it.width.value <= width + FIT_SLACK && it.height.value <= height + FIT_SLACK }
        return fits.maxByOrNull { it.width.value * it.height.value } ?: sizes.minBy { it.width.value * it.height.value }
    }

    /**
     * The real size to draw [bucket]'s bitmaps at: the first of the widget's [reported] sizes (dp, width to height,
     * portrait first) that Glance lays out as [bucket], so the light fills the cell without stretching. [bucket] itself
     * when none is reported or none maps to it.
     */
    fun drawSize(bucket: DpSize, reported: List<Pair<Float, Float>>): Pair<Float, Float> =
        reported.firstOrNull { (w, h) -> w > 0f && h > 0f && bucket(w, h) == bucket } ?: (bucket.width.value to bucket.height.value)

    /**
     * The size to fit [bucket]'s content to: the smallest width and height among the widget's [reported] sizes that
     * Glance lays out as [bucket] (portrait and landscape can differ), so what fits is decided at the real cell, not
     * the bucket's minimum. [bucket] itself when none is reported or none maps to it.
     */
    fun fitSize(bucket: DpSize, reported: List<Pair<Float, Float>>): Pair<Float, Float> {
        val mine = reported.filter { (w, h) -> w > 0f && h > 0f && bucket(w, h) == bucket }
        if (mine.isEmpty()) return bucket.width.value to bucket.height.value
        return mine.minOf { it.first } to mine.minOf { it.second }
    }

    /**
     * The corner a widget draws (spec 6.3): [CORNER_DP], or the launcher's own widget radius ([system], dp, Android 12
     * and later) when that is smaller, so a widget never looks rounder than the launcher's others.
     */
    fun corner(system: Float?): Float = system?.takeIf { it > 0f }?.coerceAtMost(CORNER_DP) ?: CORNER_DP

    /**
     * RemoteViews' bitmap limit (spec 6.2): screen width × height × 4 × 1.5 bytes for all of a widget's bitmaps at
     * once, here for a screen [screenW] by [screenH] px.
     */
    fun bitmapBudget(screenW: Int, screenH: Int): Long = (screenW.toLong() * screenH * 4 * 3) / 2

    /**
     * How far to scale the bitmaps' resolution down, at most 1, so [pixels] of ARGB bitmaps (every size's together,
     * since a responsive widget sends all three at once) stay inside [budget] bytes with a margin.
     */
    fun bitmapScale(pixels: Long, budget: Long): Float {
        if (pixels <= 0) return 1f
        val room = budget * BUDGET_SHARE / (pixels * 4.0)
        return if (room >= 1.0) 1f else kotlin.math.sqrt(room).toFloat()
    }

    /** Launchers round cell sizes, so a size this close still fits. */
    private const val FIT_SLACK = 0.5f

    /** The share of the budget the bitmaps may take, leaving room for the rest of the RemoteViews. */
    private const val BUDGET_SHARE = 0.8
}

/**
 * Rough text sums for fitting a layout at its smallest cell (spec 2.1) in the system sans, which scales with the
 * system font: widths and line heights in dp, erring large so a layout that fits here fits on the phone.
 */
object WidgetText {
    /** The v2 type roles' sizes, sp (spec 6.2). */
    const val TITLE_SP = 17f
    const val HERO_SP = 20f
    const val BODY_SP = 15f
    const val SECONDARY_SP = 13f
    const val CAPTION_SP = 11f

    /** About half an em a character. */
    const val CHAR_EM = 0.52f

    /** A line's height in a TextView with its font padding: Roboto's top to bottom is 1.33 em, rounded up. */
    const val LINE_EM = 1.35f

    /** A rough width for [text] at [sp], dp, scaled with the font. */
    fun width(text: String?, sp: Float, fontScale: Float = 1f): Float = (text?.length ?: 0) * sp * CHAR_EM * fontScale

    /** One line's height at [sp], dp, scaled with the font. */
    fun line(sp: Float, fontScale: Float = 1f): Float = sp * LINE_EM * fontScale

    /** How many lines [text] takes at [sp] in [width] dp, at least 1. */
    fun lines(text: String?, sp: Float, width: Float, fontScale: Float = 1f): Int =
        if (width <= 0f) Int.MAX_VALUE else kotlin.math.ceil(width(text, sp, fontScale) / width).toInt().coerceAtLeast(1)
}

/** A dotted number's size for fitting (spec 3): the same sums as the dot geometry, in dp. */
object WidgetDots {
    /** [text]'s dots at [dot] dp, wide and high. */
    fun width(text: String, dot: Float): Float =
        dot * ((1 + org.openstrap.app.ui.glow.DotSize.DOT_GAP) * org.openstrap.app.ui.glow.draw.DotFont.columns(text) - org.openstrap.app.ui.glow.DotSize.DOT_GAP)
    fun height(dot: Float): Float =
        dot * ((1 + org.openstrap.app.ui.glow.DotSize.DOT_GAP) * org.openstrap.app.ui.glow.draw.DotFont.ROWS - org.openstrap.app.ui.glow.DotSize.DOT_GAP)

    /** The dot that fits [text] into [room] dp, at most [dot]. */
    fun fit(text: String, room: Float, dot: Float): Float = minOf(dot, room / width(text, 1f)).coerceAtLeast(0.5f)
}
