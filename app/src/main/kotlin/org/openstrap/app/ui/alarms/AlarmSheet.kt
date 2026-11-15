package org.openstrap.app.ui.alarms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.openstrap.app.R
import org.openstrap.app.ui.device.SheetActions
import org.openstrap.app.ui.glow.GlowLayout
import org.openstrap.app.ui.glow.GlowType
import org.openstrap.app.ui.glow.HaloButtonSurface
import org.openstrap.app.ui.glow.PageButton
import org.openstrap.app.ui.glow.PageNote
import org.openstrap.app.ui.glow.PageSheet
import org.openstrap.app.ui.glow.PickedRing
import org.openstrap.app.ui.glow.PickedSurface
import org.openstrap.protocol.services.Alarm

/**
 * A new alarm or one being changed, in a sheet (HaloAlarmSheet board): the time on two wheels, the days as 44 squircle chips,
 * then Cancel and Save, with Delete under them when it already exists. A picked day is the picked state of the settings
 * rules (#34363E with a white ring), since Save is the sheet's white tap.
 */
@Composable
internal fun AlarmSheet(
    alarm: Alarm,
    isNew: Boolean,
    busy: Boolean,
    onSave: (Alarm) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var minutes by remember { mutableIntStateOf(alarm.hour * 60 + alarm.minute) }
    var days by remember { mutableIntStateOf(alarm.days) }
    PageSheet(onDismiss, stringResource(if (isNew) R.string.you_alarms_new else R.string.you_alarms_edit)) {
        TimeWheels(minutes, { minutes = it })
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PageNote(stringResource(R.string.you_alarms_repeat, daysLabel(days)))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DAY_LETTERS.forEachIndexed { i, d ->
                    val bit = 1 shl i
                    // Picking a day replaces the strap's own "workdays" calendar with a plain day set.
                    DayChip(
                        stringResource(d), days != Alarm.WORKDAYS && days and bit != 0, stringResource(DAY_NAMES[i]),
                        { days = (if (days == Alarm.WORKDAYS) 0 else days) xor bit }, Modifier.weight(1f),
                    )
                }
            }
        }
        SheetActions(
            onDismiss, { onSave(alarm.copy(hour = minutes / 60, minute = minutes % 60, days = days, enabled = true)) }, saveEnabled = !busy,
        )
        if (!isNew) {
            PageButton(
                stringResource(R.string.you_alarms_delete), onDelete, Modifier.fillMaxWidth(),
                enabled = !busy, destructive = true,
            )
        }
    }
}

/** A day of the week: 44 tall, a squircle, the letter at 14 semibold. Unpicked is dark with grey text. */
@Composable
private fun DayChip(letter: String, picked: Boolean, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.height(44.dp).clip(GlowLayout.IconShape)
            .background(if (picked) PickedSurface else HaloButtonSurface)
            .let { if (picked) it.border(PickedRing, Color.White, GlowLayout.IconShape) else it }
            .clickable(role = Role.Checkbox, onClick = onClick)
            .semantics { selected = picked; contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(letter, color = if (picked) Color.White else GlowType.noteColor, style = GlowType.buttonSmall,
            modifier = Modifier.clearAndSetSemantics {})
    }
}
