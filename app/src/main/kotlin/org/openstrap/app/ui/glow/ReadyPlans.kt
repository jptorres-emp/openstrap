@file:Suppress("MatchingDeclarationName")

package org.openstrap.app.ui.glow

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.openstrap.app.R

/*
 * Ready's second settings and switches (board HaloReadyEveryKind): what the hero's ruler doesn't set goes in a plain
 * card under it, a minus, the value and a plus a row; then the switch rows.
 */

/** One setting under the hero: its [name], its [value] as it reads, and a step down and up, each null at its end. */
internal class StepSetting(val name: String, val value: String, val onLess: (() -> Unit)?, val onMore: (() -> Unit)?)

/** The settings under the hero, one stepper row each, in a plain card. Nothing for none. */
@Suppress("UnstableCollections", "ComposeUnstableCollections")
@Composable
internal fun ReadySettings(settings: List<StepSetting>) {
    if (settings.isEmpty()) return
    HaloCard(fill = CardFill.Rows) {
        settings.forEachIndexed { i, s ->
            HaloRow(
                s.name, titleLines = 2, divider = i > 0,
                trail = {
                    HaloStepper(
                        stringResource(R.string.ready_less, s.name), stringResource(R.string.ready_more, s.name),
                        { s.onLess?.invoke() }, { s.onMore?.invoke() }, size = StepperSize.Row,
                        lessEnabled = s.onLess != null, moreEnabled = s.onMore != null,
                    ) { StepperNumber(s.value, null, size = StepperSize.Row) }
                },
            )
        }
    }
}

/** A step from [value] by [step] within [range]: the setting's minus (or its plus, [up]), null where it can go no further. */
internal fun stepAction(value: Long, step: Long, range: LongRange, up: Boolean, set: (Long) -> Unit): (() -> Unit)? {
    val next = org.openstrap.app.ui.workout.StartCopy.stepBy(value, step, range, up)
    return if (next == value) null else ({ set(next) })
}

/** Ready's switch rows under the settings: a [HaloCard] of rows. */
@androidx.compose.runtime.NonRestartableComposable
@Composable
internal fun ReadyRows(content: @Composable ColumnScope.() -> Unit) = HaloCard(fill = CardFill.Rows, content = content)
