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
