@file:Suppress("MagicNumber") // the sheet colours are the boards' hex values

package org.openstrap.app.ui.device

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import org.openstrap.app.ui.glow.GlowSegments
import org.openstrap.app.ui.glow.GlowType
import org.openstrap.app.ui.glow.PageButton
import org.openstrap.app.ui.glow.PageNote
import org.openstrap.app.ui.glow.PageRows
import org.openstrap.app.ui.glow.PageSheet
import org.openstrap.app.ui.glow.RoundIconButton
import org.openstrap.app.ui.theme.AppIcons

/*
 * The picker sheets of the settings pages (HaloProfileAgeSheet, HaloQuietHoursSheet and HaloSleepGoalSheet boards, rules B4):
 * a grabber, the title, one picker, a note on what it changes, then Cancel (dark) and Save (white) half and half.
 */

/** Cancel and Save, half and half, 56 tall (rules B4). Save is the sheet's one white thing. */
@Composable
internal fun SheetActions(onCancel: () -> Unit, onSave: () -> Unit, saveEnabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PageButton(stringResource(R.string.you_action_cancel), onCancel, Modifier.weight(1f))
        PageButton(stringResource(R.string.you_action_save), onSave, Modifier.weight(1f), enabled = saveEnabled, primary = true)
    }
}

/**
 * A number between minus and plus (rules B4): the value in Funnel Display Light 44 with its [unit] after it in 15,
 * and a 44 squircle each side. Holds its own draft; Save hands it to [onSave].
 */
@Composable
internal fun StepperSheet(
    title: String,
    initial: Int,
    range: IntRange,
    onSave: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    step: Int = 1,
    note: String? = null,
    unit: String? = null,
    format: (Int) -> String = { it.toString() },
) {
    var value by rememberSaveable { mutableIntStateOf(initial.coerceIn(range)) }
    PageSheet(onDismiss, title) {
        Row(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            RoundIconButton(
                AppIcons.Minus, stringResource(R.string.you_sheet_less), { value = (value - step).coerceIn(range) }, size = 44.dp,
                enabled = value > range.first,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(format(value), color = Color.White, style = GlowType.goalValue)
                if (unit != null) Text(" $unit", color = GlowType.noteColor, style = GlowType.body,
                    modifier = Modifier.padding(bottom = 6.dp))
            }
            RoundIconButton(
                AppIcons.Plus, stringResource(R.string.you_sheet_more), { value = (value + step).coerceIn(range) }, size = 44.dp,
                enabled = value < range.last,
            )
        }
        if (note != null) PageNote(note)
        SheetActions(onDismiss, { onSave(value) })
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
) {
    var picked by rememberSaveable { mutableIntStateOf(initial) }
    PageSheet(onDismiss, title) {
        Box(Modifier.padding(vertical = 8.dp)) { GlowSegments(options, picked, { picked = it }) }
        if (note != null) PageNote(note)
        SheetActions(onDismiss, { onSave(picked) }, saveEnabled = picked in options.indices)
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
) {
    var picked by rememberSaveable { mutableIntStateOf(initial) }
    PageSheet(onDismiss, title) {
        PageRows {
            options.forEachIndexed { i, name ->
                RadioRow(name, i == picked, { picked = i }, divider = i > 0)
            }
        }
        if (note != null) PageNote(note)
        SheetActions(onDismiss, { onSave(picked) }, saveEnabled = picked in options.indices)
    }
}

/** A radio row: the name at 15 and a ring that fills white when picked. */
@Composable
private fun RadioRow(name: String, picked: Boolean, onClick: () -> Unit, divider: Boolean) {
    Column {
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF1C1D22)))
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .clickable(role = Role.RadioButton, onClick = onClick)
                .semantics { this.selected = picked; this.role = Role.RadioButton }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(name, Modifier.weight(1f), color = Color.White, style = GlowType.body, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Box(
                Modifier.size(22.dp).clip(CircleShape)
                    .let { if (picked) it.background(Color.White) else it.border(1.5.dp, Color(0xFF3A3D45), CircleShape) },
                contentAlignment = Alignment.Center,
            ) { if (picked) Box(Modifier.size(8.dp).clip(CircleShape).background(Color.Black)) }
        }
    }
}
