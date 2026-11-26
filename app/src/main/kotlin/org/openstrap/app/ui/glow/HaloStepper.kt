package org.openstrap.app.ui.glow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.openstrap.app.ui.components.HapticEvent
import org.openstrap.app.ui.components.rememberHaptics
import org.openstrap.app.ui.theme.AppIcons
import org.openstrap.app.ui.theme.IconSize
import org.openstrap.app.ui.theme.Space

/*
 * The stepper (docs/design/halo/components-audit.md, 3.13): a number between a minus and a plus, the two buttons always
 * [HaloIconButton] 44. A sheet's stepper has the number large between buttons at the sheet's edges; a row's has the
 * number 26 in a 104 wide box between buttons 8 apart. The number is a slot, so it can be typed in place.
 */

/** The stepper's measurements. */
internal object StepperSpec {
    /** Both buttons. */
    val Button = HaloIconSize.Default

    /** A row stepper's number box, and between it and its buttons. */
    val RowNumberWidth = 104.dp
    val RowNumberHeight = IconSize.TileM
    val RowGap = Space.S8

    /** A unit after the number: on a sheet a space then Body, in a row Secondary 4 after it. */
    val UnitGap = Space.S4
    val UnitStyle: TextStyle = GlowType.body
    val RowUnitStyle: TextStyle = GlowType.secondary

    /** How far a unit sits up from the number's foot, so both share a baseline-ish foot. */
    val SheetUnitLift = Space.S8
    val RowUnitLift = Space.S2
}

/** A stepper's size: its number's type. */
internal enum class StepperSize(val number: TextStyle) {
    /** A picker sheet's or a page's one number: Display Light 44. */
    Sheet(GlowType.goalValue),

    /** A goal in a row: Display Light 26. */
    Row(GlowType.goalValueMinor),
}

/**
 * Minus, the [value], plus. [less] and [more] are what the buttons say to a screen reader; [lessEnabled] and
 * [moreEnabled] fade one at its end of the range. A [StepperSize.Sheet] stepper spreads across its width with the value
 * centred between the buttons ([gap] from them); a [StepperSize.Row] one is as wide as its 104 number box and buttons.
 */
@Composable
internal fun HaloStepper(
    less: String,
    more: String,
    onLess: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    size: StepperSize = StepperSize.Sheet,
    lessEnabled: Boolean = true,
    moreEnabled: Boolean = true,
    gap: Dp = 0.dp,
    value: @Composable RowScope.() -> Unit,
) {
    val sheet = size == StepperSize.Sheet
    // Each step clicks, like a notch on the ruler.
    val haptics = rememberHaptics()
    val lessTap = { haptics.perform(HapticEvent.ScrubMark); onLess() }
    val moreTap = { haptics.perform(HapticEvent.ScrubMark); onMore() }
    Row(
        modifier, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (sheet) gap else StepperSpec.RowGap),
    ) {
        HaloIconButton(AppIcons.Minus, less, lessTap, size = StepperSpec.Button, enabled = lessEnabled)
        if (sheet) {
            Row(
                Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                content = value,
            )
        } else {
            Box(Modifier.width(StepperSpec.RowNumberWidth).height(StepperSpec.RowNumberHeight), contentAlignment = Alignment.Center) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically, content = value)
            }
        }
        HaloIconButton(AppIcons.Plus, more, moreTap, size = StepperSpec.Button, enabled = moreEnabled)
    }
}

/** A stepper's number in its size's type, with its [unit] after it in grey. */
@Composable
internal fun StepperNumber(text: String, unit: String?, modifier: Modifier = Modifier, size: StepperSize = StepperSize.Sheet) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(text, color = Color.White, style = size.number, maxLines = 1)
        if (unit != null) {
            if (size == StepperSize.Sheet) {
                Text(
                    " $unit", Modifier.padding(bottom = StepperSpec.SheetUnitLift), color = GlowType.noteColor,
                    style = StepperSpec.UnitStyle,
                )
            } else {
                Text(
                    unit, Modifier.padding(start = StepperSpec.UnitGap, bottom = StepperSpec.RowUnitLift), color = GlowType.noteColor,
                    style = StepperSpec.RowUnitStyle, maxLines = 1,
                )
            }
        }
    }
}
