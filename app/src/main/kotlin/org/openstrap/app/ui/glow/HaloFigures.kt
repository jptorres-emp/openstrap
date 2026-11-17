package org.openstrap.app.ui.glow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.openstrap.app.ui.theme.Ink
import org.openstrap.app.ui.theme.OnSurface
import org.openstrap.app.ui.theme.Space

/*
 * The figures (docs/design/halo/components-audit.md, 3.8): up to three numbers side by side, each its value over its
 * label, a 1 dp rule before every column after the first. A detail's, a summary's, a live workout's, the weekly recap's
 * and the Load panel's figures are all [HaloFigures]; only the numbers differ. The one big number is [HaloBigNumber].
 */

/** The figures' measurements (the HaloComponents board). */
internal object FigureSpec {
    /** The value in Display Light 28, a unit after it in Secondary grey, the label under in 12. */
    val Value: TextStyle = GlowType.figure
    val Unit: TextStyle = GlowType.secondary
    val Label: TextStyle = GlowType.noteSmall

    /** Value to label. */
    val Gap = Space.S4

    /** The rule before a column, its width, and from it to the column's words. */
    val Rule = 1.dp
    val RuleGap = Space.S16

    /** The row's sides, so a value lines up with a card's words above it. */
    val Side = Space.S4

    /** A missing value's dash is padded to a value's line height (28): 12 above and below a 3 high dash. */
    val DashPad = Space.S12

    /** Three to a row. */
    const val COLUMNS = 3
}

/** Whether the numbers stand or change while they show. */
internal enum class FiguresVariant {
    /** A detail's, a summary's, the recap's: a label may take two lines. */
    Static,

    /** A live workout's: values and labels never wrap, so a number ticking over never moves the row. */
    Live,
}

/** A value's unit, from [units] when given, else whatever follows the number ("72 bpm", "4.02 km", "7 h 30 min"). */
private val NUMBER_THEN_UNIT = Regex("""^([+\-−]?[\d.,:]+)(\s*.*)$""")

/**
 * The row of figures: each pair a value (null for the waiting dash) and its label, its unit from [units] at the same
 * index or split from the value. [missing] stands in for a missing value as text when a caller wants words. The rules
 * are one step above the surface ([OnSurface.line]).
 */
// The lists arrive whole with each reading and never change in place.
@Suppress("UnstableCollections", "ComposeUnstableCollections")
@Composable
internal fun HaloFigures(
    figures: List<Pair<String?, String>>,
    modifier: Modifier = Modifier,
    variant: FiguresVariant = FiguresVariant.Static,
    units: List<String?> = emptyList(),
    missing: String? = null,
) {
    if (figures.isEmpty()) return
    val rule = OnSurface.line
    val live = variant == FiguresVariant.Live
    Row(modifier.fillMaxWidth().padding(horizontal = FigureSpec.Side)) {
        figures.forEachIndexed { i, (value, label) ->
            Column(
                Modifier.weight(1f).let { if (i > 0) it.figureRule(rule).padding(start = FigureSpec.RuleGap) else it },
                verticalArrangement = Arrangement.spacedBy(FigureSpec.Gap),
            ) {
                when {
                    value != null -> Text(
                        figureText(value, units.getOrNull(i)), color = Color.White, style = FigureSpec.Value, maxLines = 1,
                        softWrap = !live,
                    )
                    missing != null -> Text(missing, color = Ink.Disabled, style = FigureSpec.Value, maxLines = 1)
                    else -> WaitingDash(Modifier.padding(vertical = FigureSpec.DashPad))
                }
                Text(
                    label, color = GlowType.noteColor, style = FigureSpec.Label, maxLines = if (live) 1 else 2,
                    softWrap = !live,
                )
            }
        }
    }
}

/** The 1 dp rule down a figure's left edge. */
private fun Modifier.figureRule(color: Color): Modifier =
    drawBehind { drawLine(color, Offset(0f, 0f), Offset(0f, size.height), FigureSpec.Rule.toPx()) }

/** A figure's number with its unit after it, smaller and grey. */
private fun figureText(value: String, unit: String?): AnnotatedString {
    val split = if (unit == null) NUMBER_THEN_UNIT.matchEntire(value) else null
    val number = split?.groupValues?.get(1) ?: value
    val after = unit ?: split?.groupValues?.get(2)?.trim()
    if (after.isNullOrBlank()) return AnnotatedString(value)
    return buildAnnotatedString {
        append(number)
        withStyle(FigureSpec.Unit.toSpanStyle().copy(color = GlowType.noteColor)) { append(" $after") }
    }
}
