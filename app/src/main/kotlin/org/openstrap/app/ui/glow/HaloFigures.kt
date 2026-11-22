package org.openstrap.app.ui.glow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
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

    /** A label's right edge, kept clear of the next column's rule at any font size. */
    val LabelEnd = Space.S8

    /** The row's sides, so a value lines up with a card's words above it. */
    val Side = Space.S4

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
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val unitInk = GlowType.noteInk
    // A missing value's dash is as high as a value's line, at any font size, so the row never shrinks for it.
    val valueLine = with(density) { FigureSpec.Value.lineHeight.toDp() }
    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = FigureSpec.Side)) {
        // One layout for the whole row: when any value and its unit don't fit a column on one line, every unit goes under
        // its value, so the labels of the columns stay level.
        val stacked = !live && with(density) {
            val column = (constraints.maxWidth - 2 * FigureSpec.Side.roundToPx()) / figures.size -
                (FigureSpec.RuleGap + FigureSpec.Rule).roundToPx()
            figures.withIndex().any { (i, f) ->
                val v = f.first
                v != null && measurer.measure(
                    figureText(v, units.getOrNull(i), unitInk), FigureSpec.Value, softWrap = false, maxLines = 1,
                ).size.width > column
            }
        }
        Row(Modifier.fillMaxWidth()) {
            figures.forEachIndexed { i, (value, label) ->
                Column(
                    Modifier.weight(1f).let { if (i > 0) it.figureRule(rule).padding(start = FigureSpec.RuleGap) else it },
                    verticalArrangement = Arrangement.spacedBy(FigureSpec.Gap),
                ) {
                    when {
                        value != null && stacked -> {
                            val (number, unit) = figureParts(value, units.getOrNull(i))
                            Text(number, color = Color.White, style = FigureSpec.Value, maxLines = 1, softWrap = false)
                            // A column without a unit keeps the line, so the labels line up.
                            Text(unit.orEmpty(), color = unitInk, style = FigureSpec.Unit, minLines = 1, maxLines = 1)
                        }
                        value != null -> Text(
                            figureText(value, units.getOrNull(i), unitInk), color = Color.White, style = FigureSpec.Value,
                            maxLines = 1, softWrap = false,
                        )
                        missing != null -> Text(missing, color = Ink.Disabled, style = FigureSpec.Value, maxLines = 1)
                        else -> Box(Modifier.height(valueLine), contentAlignment = Alignment.CenterStart) { WaitingDash() }
                    }
                    Text(
                        label, Modifier.padding(end = FigureSpec.LabelEnd), color = unitInk, style = FigureSpec.Label,
                        maxLines = if (live) 1 else 2, softWrap = !live,
                    )
                }
            }
        }
    }
}

/** The 1 dp rule down a figure's left edge. */
private fun Modifier.figureRule(color: Color): Modifier =
    drawBehind { drawLine(color, Offset(0f, 0f), Offset(0f, size.height), FigureSpec.Rule.toPx()) }

/** A figure's number and its unit apart: the unit from [unit], else whatever follows the number. */
private fun figureParts(value: String, unit: String?): Pair<String, String?> {
    val split = if (unit == null) NUMBER_THEN_UNIT.matchEntire(value) else null
    val number = split?.groupValues?.get(1) ?: value
    val after = unit ?: split?.groupValues?.get(2)?.trim()
    return number to after?.takeIf { it.isNotBlank() }
}

/** A figure's number with its unit after it, smaller and in [ink]. */
private fun figureText(value: String, unit: String?, ink: Color): AnnotatedString {
    val (number, after) = figureParts(value, unit)
    if (after == null) return AnnotatedString(value)
    return buildAnnotatedString {
        append(number)
        withStyle(FigureSpec.Unit.toSpanStyle().copy(color = ink)) { append(" $after") }
    }
}
