@file:Suppress("MatchingDeclarationName")

package org.openstrap.app.ui.glow

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.openstrap.app.presentation.Units
import org.openstrap.app.ui.theme.Ink
import org.openstrap.app.ui.theme.Space
import org.openstrap.app.ui.glow.draw.DotSize

/*
 * The one big number of a screen (docs/design/halo/components-audit.md, 3.18): [HaloBigNumber] at a [BigNumber] size,
 * with its unit or its words. It draws through [BigNumberText], the low-level DotSpec drawing, which only it and the
 * share pictures call.
 */

/** The big number's measurements. */
internal object BigNumberSpec {
    /** Between the digits and a unit after them, and around a word inside a figure ("4 of 4"). */
    val UnitGap = Space.S8
    val WordSide = Space.S8
    val WordLift = Space.S2

    /** The slash of "4/4" in the display type, with this much either side. */
    val SlashSide = Space.S2

    /** The room a unit keeps when the number shrinks to fit its line. */
    val UnitRoom = 96.dp

    /** The waiting dash in a card's number's place (section 5 row 12). */
    val DashWidth = 28.dp
    val DashHeight = 6.dp
}

/**
 * Runs of digits (with a point, comma, colon or the thin space grouping big numbers, [Units.DOT_GROUP_SPACE]), a slash,
 * and the words between. The thin space stays inside its run, so "25 559" is one dot number with its groups two columns
 * apart, not two numbers drawn edge to edge.
 */
internal val FIGURE_PART = Regex("[0-9][0-9.,:${Units.DOT_GROUP_SPACE}]*|/|[^0-9/]+")

/** A number that is dots alone: digits with their point, comma, colon, group space or a dash. */
internal val ONLY_DIGITS = Regex("[0-9.,:${Units.DOT_GROUP_SPACE}-]*")

/**
 * The screen's one big number in DotSpec dots at the board's Doto [size]. Digits are dots; a slash is the display type
 * at their height; any word between them ("4 of 4", "105 kg") is small in [wordStyle] on their baseline. A [unit]
 * follows the number in [unitColor]. With [fit] a number wider than its line (less the unit's room) shrinks to fit.
 */
@Composable
internal fun HaloBigNumber(
    text: String,
    size: BigNumber,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    unit: String? = null,
    unitColor: Color = GlowType.noteColor,
    unitLines: Int = 1,
    fit: Boolean = false,
    wordStyle: TextStyle = GlowType.body,
) {
    if (fit) {
        BoxWithConstraints(modifier.fillMaxWidth()) {
            val room = maxWidth - if (unit.isNullOrBlank()) 0.dp else BigNumberSpec.UnitRoom
            NumberLine(text, size, color, unit, unitColor, unitLines, room, wordStyle)
        }
    } else NumberLine(text, size, color, unit, unitColor, unitLines, Dp.Unspecified, wordStyle, modifier)
}

@Composable
private fun NumberLine(
    text: String,
    size: BigNumber,
    color: Color,
    unit: String?,
    unitColor: Color,
    unitLines: Int,
    fitWidth: Dp,
    wordStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        if (ONLY_DIGITS.matches(text)) {
            BigNumberText(text, size, color = color, fitWidth = fitWidth)
        } else FIGURE_PART.findAll(text).forEach { m ->
            val part = m.value
            when {
                part[0] in '0'..'9' -> BigNumberText(part, size, color = color)
                part == "/" -> Text(
                    "/", Modifier.padding(horizontal = BigNumberSpec.SlashSide), color = Ink.Secondary, style = GlowType.figureLarge,
                )
                part.isBlank() -> Unit
                else -> Text(
                    part.trim(), Modifier.padding(horizontal = BigNumberSpec.WordSide, vertical = BigNumberSpec.WordLift),
                    color = GlowType.noteColor, style = wordStyle, maxLines = 1,
                )
            }
        }
        if (!unit.isNullOrBlank()) {
            Text(
                unit, Modifier.padding(start = BigNumberSpec.UnitGap).weight(1f, fill = false), color = unitColor,
                style = GlowType.body, maxLines = unitLines,
            )
        }
    }
}

/**
 * A card's number size (boards HaloTodayMidday, HaloRecovery): the hero's 68, a second card's 52, every other card's
 * 44, and a grey Small's 36 (a lit Small keeps 44). [dot] is the old DotSize that says which.
 */
@Composable
@ReadOnlyComposable
internal fun cardNumberSize(dot: DotSize = DotSize.Edge): BigNumber = when (dot) {
    DotSize.Hero, DotSize.Detail, DotSize.Live -> BigNumber.Hero
    DotSize.Second -> BigNumber.Second
    // A grey Small is quieter than a lit tile (boards HaloActivity, HaloTodayWaking).
    else -> if (LocalSmallTile.current && LocalCardTint.current == null) BigNumber.GreySmall else BigNumber.Tile
}
