package org.openstrap.app.ui.glow.draw

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import org.openstrap.app.R
import org.openstrap.app.ui.glow.DotSize

/**
 * The numbers' face: Barlow Condensed SemiBold (SIL OFL 1.1, licenses/OFL-Barlow.txt) with tabular figures, so a value
 * that updates doesn't jitter. A number keeps the height its dot size gave the dot numerals (seven rows and six gaps),
 * so every place that was laid out for one still fits; the condensed figures take about a quarter less width.
 * DotMatrixText sets it in Compose and a widget's number as a bitmap ([renderNumber]), from the same measures.
 */
object NumberFont {
    /** Barlow's figures stand 700 of its 1000 units. */
    const val FIGURE_HEIGHT = 0.7f

    /** How far a comma reaches under the baseline, in the em: a widget's bitmap keeps room for it. */
    const val DESCENT = 0.091f

    /** The digits' height for dots [dot] across: the dot numerals' seven rows and six gaps. */
    fun height(dot: Float): Float = dot * (DotFont.ROWS + (DotFont.ROWS - 1) * DotSize.DOT_GAP)

    /** The type size whose digits stand [height] high, in the same unit. */
    fun em(height: Float): Float = height / FIGURE_HEIGHT

    /**
     * The type size for a widget's bitmap [height] high, comma included: its figures a little shorter than the box, so
     * a comma's tail stays inside it (a bitmap clips, a Compose canvas doesn't).
     */
    fun widgetEm(height: Float): Float = height / (FIGURE_HEIGHT + DESCENT)

    /** [text]'s width at the type size [em], from the font's advances (tabular figures), in the same unit. */
    fun width(text: String, em: Float): Float = text.sumOf { advance(it) } / 1000f * em

    /** A character's advance in thousandths of the em; one the font may not have counts as a figure. */
    private fun advance(c: Char): Int = when (c) {
        in '0'..'9' -> 475
        ',' -> 199
        '.' -> 211
        ':' -> 252
        '-' -> 325
        '−', '+' -> 439
        '%' -> 772
        'k' -> 433
        ' ', '\u00A0', '\u202F' -> 200
        else -> 475
    }

    @Volatile private var typeface: Typeface? = null

    /** The face for a bitmap, loaded once; null if it can't be (the system's sans then stands in). */
    fun typeface(context: Context): Typeface? = typeface ?: runCatching {
        ResourcesCompat.getFont(context.applicationContext, R.font.barlow_condensed_semibold)
    }.getOrNull().also { typeface = it }
}
