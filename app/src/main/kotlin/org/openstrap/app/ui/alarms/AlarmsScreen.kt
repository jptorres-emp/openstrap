package org.openstrap.app.ui.alarms

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.openstrap.app.ui.components.EmptyHint
import org.openstrap.app.ui.components.Panel
import org.openstrap.app.ui.components.PanelHeader
import org.openstrap.app.ui.dashboard.ScreenList
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.app.ui.theme.PhosphorIcons
import org.openstrap.protocol.services.Alarm
import org.openstrap.protocol.services.SettingsCodec

private val DAY_LETTERS = listOf("M", "T", "W", "T", "F", "S", "S")
private val DAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/** "Once", "Every day", "Weekdays", "Weekends" or "Mon, Wed, Fri". */
fun daysLabel(days: Int): String = when (days) {
    0 -> "Once"
    Alarm.EVERY_DAY -> "Every day"
    Alarm.WEEKDAYS -> "Weekdays"
    Alarm.WEEKEND -> "Weekends"
    Alarm.WORKDAYS -> "Workdays"
    else -> DAY_NAMES.filterIndexed { i, _ -> days and (1 shl i) != 0 }.joinToString(", ")
}

private fun intervalLabel(v: Int) = when (v) {
    0 -> "Off"
    SettingsCodec.HR_CONTINUOUS -> "Continuous"
    else -> "Every $v min"
}

@Composable
fun AlarmsScreen(
    vm: AlarmsViewModel,
    state: AlarmsUiState,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    phoneAlerts: @Composable () -> Unit = {},
) {
    BackHandler(onBack = onBack)
    var editing by remember { mutableStateOf<Pair<Alarm, Boolean>?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    if (confirmDeleteAll) {
        org.openstrap.app.ui.components.GlassAlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Delete all alarms?") },
            text = { Text("This removes every alarm from the strap.") },
            confirmButton = { TextButton(onClick = { vm.deleteAll(); confirmDeleteAll = false }) { Text("Delete all", color = MetricColors.bad) } },
            dismissButton = { TextButton(onClick = { confirmDeleteAll = false }) { Text("Cancel") } },
        )
    }
    editing?.let { (alarm, isNew) ->
        AlarmDialog(
            alarm, isNew,
            onSave = { vm.save(it, isNew); editing = null },
            onDelete = { vm.delete(alarm); editing = null },
            onDismiss = { editing = null },
        )
    }
    ScreenList(contentPadding) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(PhosphorIcons.ArrowLeft, "Back") }
                Text("Alarms & alerts", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            }
        }
        state.error?.let { msg ->
            item {
                Panel(highlight = MetricColors.warn) {
                    PanelHeader(msg, PhosphorIcons.Warning, MetricColors.warn)
                    TextButton(onClick = vm::refresh, enabled = !state.busy) { Text("Try again") }
                }
            }
        }
        // Phone-side switches; they don't need the strap to answer.
        item { phoneAlerts() }
        if (state.loading) {
            item { Panel { EmptyHint("Reading from the strap…") } }
            return@ScreenList
        }
        item { AlarmsPanel(state, onToggle = vm::setEnabled, onEdit = { editing = it to false }, onAdd = {
            vm.freeIndex()?.let { editing = Alarm(it, 7, 0, Alarm.WEEKDAYS, enabled = true) to true }
        }, onDeleteAll = { confirmDeleteAll = true }) }
        state.health?.let { item { HeartAlertsPanel(state, vm) } }
    }
}

@Composable
private fun AlarmsPanel(
    state: AlarmsUiState,
    onToggle: (Alarm, Boolean) -> Unit,
    onEdit: (Alarm) -> Unit,
    onAdd: () -> Unit,
    onDeleteAll: () -> Unit,
) {
    Panel {
        PanelHeader("Alarms", PhosphorIcons.CalendarBlank, MetricColors.charge, trailing = state.caps?.let { "${state.alarms.size} of ${it.maxCount}" })
        if (state.alarms.isEmpty()) EmptyHint("No alarms on the strap.")
        state.alarms.forEach { a ->
            Row(Modifier.fillMaxWidth().clickable(enabled = !state.busy) { onEdit(a) }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "%02d:%02d".format(a.hour, a.minute),
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (a.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(daysLabel(a.days), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(a.enabled, { onToggle(a, it) }, enabled = !state.busy)
            }
        }
        Row {
            if (state.canAdd) TextButton(onClick = onAdd, enabled = !state.busy) { Text("Add alarm") }
            if (state.alarms.size > 1) TextButton(onClick = onDeleteAll, enabled = !state.busy) { Text("Delete all", color = MetricColors.bad) }
        }
        // Zepp's tip for this strap (alarm_stuttgart_close_tips).
        Text(
            "The strap vibrates at the alarm time. To stop it, tap the front of the strap several times.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeartAlertsPanel(state: AlarmsUiState, vm: AlarmsViewModel) {
    val g = state.health ?: return
    Panel {
        PanelHeader("Heart & oxygen", PhosphorIcons.Heart, MetricColors.heart)
        g[SettingsCodec.KEY_HR_INTERVAL]?.let { s ->
            ChoiceRow("Heart-rate measuring", s.choices.orEmpty(), s.number?.toInt(), state.busy, ::intervalLabel) { vm.setHrInterval(it) }
        }
        val alerts = state.alertsAvailable
        if (!alerts) {
            Text(
                "Heart-rate alerts need continuous or every-minute measuring.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        g[SettingsCodec.KEY_HIGH_HR_ALERT]?.let { s ->
            ChoiceRow("High heart-rate alert", s.choices.orEmpty(), s.number?.toInt(), state.busy || !alerts, { if (it == 0) "Off" else "Above $it" }) {
                vm.setEnum(SettingsCodec.KEY_HIGH_HR_ALERT, it)
            }
        }
        g[SettingsCodec.KEY_LOW_HR_ALERT]?.let { s ->
            ChoiceRow("Low heart-rate alert", s.choices.orEmpty(), s.number?.toInt(), state.busy || !alerts, { if (it == 0) "Off" else "Below $it" }) {
                vm.setEnum(SettingsCodec.KEY_LOW_HR_ALERT, it)
            }
        }
        g[SettingsCodec.KEY_LOW_SPO2_ALERT]?.let { s ->
            ChoiceRow("Low blood-oxygen alert", s.choices.orEmpty(), s.number?.toInt(), state.busy, { if (it == 0) "Off" else "Below $it%" }) {
                vm.setEnum(SettingsCodec.KEY_LOW_SPO2_ALERT, it)
            }
        }
        Text(
            "The strap vibrates when an alert goes off. OpenStrap isn't a medical device.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceRow(title: String, choices: List<Int>, current: Int?, disabled: Boolean, label: (Int) -> String, onPick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.forEach { v ->
                FilterChip(selected = v == current, onClick = { if (v != current) onPick(v) }, label = { Text(label(v)) }, enabled = !disabled)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AlarmDialog(alarm: Alarm, isNew: Boolean, onSave: (Alarm) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val time = rememberTimePickerState(alarm.hour, alarm.minute, is24Hour = true)
    var days by remember { mutableIntStateOf(alarm.days) }
    org.openstrap.app.ui.components.GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New alarm" else "Edit alarm") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                TimePicker(time)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)) {
                    DAY_LETTERS.forEachIndexed { i, d ->
                        val bit = 1 shl i
                        // Picking a day replaces the strap's own "workdays" calendar with a plain day set.
                        FilterChip(selected = days != Alarm.WORKDAYS && days and bit != 0, onClick = {
                            days = (if (days == Alarm.WORKDAYS) 0 else days) xor bit
                        }, label = { Text(d) })
                    }
                }
                Text(daysLabel(days), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(alarm.copy(hour = time.hour, minute = time.minute, days = days, enabled = true)) }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = onDelete) { Text("Delete", color = MetricColors.bad) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
