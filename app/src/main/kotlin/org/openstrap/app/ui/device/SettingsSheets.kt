@file:Suppress("MagicNumber") // the sheet colours are the boards' hex values

package org.openstrap.app.ui.device

import org.openstrap.app.ui.glow.HaloNote
import org.openstrap.app.ui.glow.HaloStepper
import org.openstrap.app.ui.glow.StepperSize
import org.openstrap.app.ui.glow.StepperNumber
import org.openstrap.app.ui.glow.CardFill
import org.openstrap.app.ui.glow.HaloCard
import org.openstrap.app.ui.theme.Space
import org.openstrap.app.ui.theme.Greys
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.openstrap.app.R
import org.openstrap.app.ui.glow.ButtonPair
import org.openstrap.app.ui.glow.HaloButton
import org.openstrap.app.ui.glow.HaloButtonVariant
import org.openstrap.app.ui.glow.ChoiceRow
import org.openstrap.app.ui.glow.GlowSegments
import org.openstrap.app.ui.glow.GlowType
import org.openstrap.app.ui.glow.HaloSheet
import org.openstrap.app.ui.glow.HaloIconButton
import org.openstrap.app.ui.theme.AppIcons

/*
 * The picker sheets of the settings pages (HaloProfileAgeSheet, HaloQuietHoursSheet and HaloSleepGoalSheet boards, rules B4):
 * a grabber, the title, one picker, a note on what it changes, then Cancel (dark) and Save (white) half and half.
 */

/**
 * A field that may be left blank (the page's values are all optional) has a quiet way back to blank or the default:
 * [label] as a text button over the sheet's Cancel and Save pair.
 */
@Composable
internal fun ColumnScope.SheetClear(label: String?, onClear: (() -> Unit)?) {
    if (label != null && onClear != null) {
        HaloButton(label, onClear, Modifier.align(Alignment.CenterHorizontally), variant = HaloButtonVariant.Text)
    }
}

/**
 * A number between minus and plus (rules B4): a [StepperSize.Sheet] [HaloStepper] with its [unit] after the number.
 * Holds its own draft; Save hands it to [onSave].
 */
@Composable
internal fun StepperSheet(
    title: String,
    initial: Int,
    range: IntRange,
    onSave: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    clearLabel: String? = null,
    onClear: (() -> Unit)? = null,
    step: Int = 1,
    note: String? = null,
    unit: String? = null,
    format: (Int) -> String = { it.toString() },
) {
    var value by rememberSaveable { mutableIntStateOf(initial.coerceIn(range)) }
    HaloSheet(
        onDismiss, title,
        footer = {
            ButtonPair(
                stringResource(R.string.you_action_cancel), onDismiss, stringResource(R.string.you_action_save), { onSave(value) },
            )
        },
    ) {
        HaloStepper(
            stringResource(R.string.you_sheet_less), stringResource(R.string.you_sheet_more),
            { value = (value - step).coerceIn(range) }, { value = (value + step).coerceIn(range) },
            modifier.fillMaxWidth().padding(vertical = Space.S8),
            lessEnabled = value > range.first, moreEnabled = value < range.last,
        ) { StepperNumber(format(value), unit) }
        if (note != null) HaloNote(note)
        SheetClear(clearLabel, onClear)
    }
}

/** One of a few, a segmented control (rules B4: track #111215, the picked one white). */
@Suppress("UnstableCollections", "ComposeUnstableCollections")
@Composable
internal fun SegmentSheet(
    title: String,
    options: List<String>,
    initial: Int,
    onSave: (Int) -> Unit,
    onDismiss: () -> Unit,
    note: String? = null,
    clearLabel: String? = null,
    onClear: (() -> Unit)? = null,
) {
    var picked by rememberSaveable { mutableIntStateOf(initial) }
    HaloSheet(
        onDismiss, title,
        footer = {
            ButtonPair(
                stringResource(R.string.you_action_cancel), onDismiss, stringResource(R.string.you_action_save), { onSave(picked) },
                rightEnabled = picked in options.indices,
            )
        },
    ) {
        Box(Modifier.padding(vertical = Space.S8)) { GlowSegments(options, picked, { picked = it }) }
        if (note != null) HaloNote(note)
        SheetClear(clearLabel, onClear)
    }
}

/** One of many, radio rows in a panel (rules B4). [details] are the second lines, when the options need one. */
@Suppress("UnstableCollections", "ComposeUnstableCollections")
@Composable
internal fun RadioSheet(
    title: String,
    options: List<String>,
    initial: Int,
    onSave: (Int) -> Unit,
    onDismiss: () -> Unit,
    note: String? = null,
    clearLabel: String? = null,
    onClear: (() -> Unit)? = null,
) {
    var picked by rememberSaveable { mutableIntStateOf(initial) }
    HaloSheet(
        onDismiss, title,
        footer = {
            ButtonPair(
                stringResource(R.string.you_action_cancel), onDismiss, stringResource(R.string.you_action_save), { onSave(picked) },
                rightEnabled = picked in options.indices,
            )
        },
    ) {
        HaloCard(fill = CardFill.Rows) {
            options.forEachIndexed { i, name ->
                ChoiceRow(name, i == picked, { picked = i }, divider = i > 0)
            }
        }
        if (note != null) HaloNote(note)
        SheetClear(clearLabel, onClear)
    }
}
