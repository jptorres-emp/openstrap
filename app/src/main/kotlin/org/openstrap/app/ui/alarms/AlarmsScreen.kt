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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import org.openstrap.app.ui.glow.ChoicePills
import org.openstrap.app.ui.glow.DestructiveInk
import org.openstrap.app.ui.glow.GlowLayout
import org.openstrap.app.ui.glow.GlowType
import org.openstrap.app.ui.glow.PageButton
import org.openstrap.app.ui.glow.PageHeader
import org.openstrap.app.ui.glow.PageNote
import org.openstrap.app.ui.glow.PagePanel
import org.openstrap.app.ui.dashboard.ScreenList
import org.openstrap.app.ui.theme.MetricColors
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

/**
 * What the Strap page's Alarms row says: the first alarm that's on ("06:40 weekdays"), how many more, "None", or null
 * while nothing has been read from the strap.
 */
fun alarmsSummary(state: AlarmsUiState): String? {
    if (state.caps == null) return null
    val on = state.alarms.filter { it.enabled }
    val first = on.firstOrNull() ?: return if (state.alarms.isEmpty()) "None" else "All off"
    val text = "%02d:%02d %s".format(first.hour, first.minute, daysLabel(first.days).lowercase())
    return if (on.size > 1) "$text and ${on.size - 1} more" else text
}

/**
 * What the Strap page's Health alerts row says: every alert that's on, briefly ("Heart rate over 150 and under 40",
 * "Over 150, oxygen under 90%", "Blood oxygen under 90%"), "Off" when none is, or null while the strap's settings
 * haven't been read.
 */
fun healthAlertsSummary(state: AlarmsUiState): String? {
    val g = state.health ?: return null
    fun on(key: Int) = g[key]?.number?.toInt()?.takeIf { it != 0 }
    val heart = listOfNotNull(on(SettingsCodec.KEY_HIGH_HR_ALERT)?.let { "over $it" }, on(SettingsCodec.KEY_LOW_HR_ALERT)?.let { "under $it" })
    val oxygen = on(SettingsCodec.KEY_LOW_SPO2_ALERT)?.let { "under $it%" }
    return when {
        heart.isEmpty() && oxygen == null -> "Off"
        oxygen == null -> "Heart rate ${heart.joinToString(" and ")}"
        heart.isEmpty() -> "Blood oxygen $oxygen"
        else -> (heart + "oxygen $oxygen").joinToString(", ").replaceFirstChar { it.uppercase() }
    }
}

/** The strap's alarms: each with its days and a switch, add and delete all. Opened from the Strap page. */
@Composable
fun AlarmsScreen(
    vm: AlarmsViewModel,
    state: AlarmsUiState,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var editing by remember { mutableStateOf<Pair<Alarm, Boolean>?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    if (confirmDeleteAll) {
        org.openstrap.app.ui.components.GlassAlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Delete all alarms?") },
            text = { Text("This removes every alarm from the strap.") },
            confirmButton = { TextButton(onClick = { vm.deleteAll(); confirmDeleteAll = false }) { Text("Delete all", color = DestructiveInk) } },
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
    ScreenList(contentPadding, gutter = GlowLayout.Gutter) {
        item { PageHeader("Alarms", onBack) }
        state.error?.let { msg -> item { ErrorPanel(msg, state.busy, vm::refresh) } }
        if (state.loading) {
            item { PagePanel { PageNote("Reading from the strap") } }
            return@ScreenList
        }
        item {
            AlarmsPanel(state, onToggle = vm::setEnabled, onEdit = { editing = it to false }, onAdd = {
                vm.freeIndex()?.let { editing = Alarm(it, 7, 0, Alarm.WEEKDAYS, enabled = true) to true }
            }, onDeleteAll = { confirmDeleteAll = true })
        }
    }
}

/** How often the strap measures heart rate, and its heart rate and blood oxygen alerts. Opened from the Strap page. */
@Composable
fun HealthAlertsScreen(vm: AlarmsViewModel, state: AlarmsUiState, contentPadding: PaddingValues, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    ScreenList(contentPadding, gutter = GlowLayout.Gutter) {
        item { PageHeader("Health alerts", onBack) }
        state.error?.let { msg -> item { ErrorPanel(msg, state.busy, vm::refresh) } }
        if (state.loading) {
            item { PagePanel { PageNote("Reading from the strap") } }
            return@ScreenList
        }
        state.health?.let { item { HeartAlertsPanel(state, vm) } }
    }
}

@Composable
private fun ErrorPanel(message: String, busy: Boolean, onRetry: () -> Unit) {
    PagePanel {
        PageNote(message, color = MetricColors.warn)
        PageButton("Try again", onRetry, Modifier.fillMaxWidth(), enabled = !busy)
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
    PagePanel(title = "On the strap", note = state.caps?.let { "${state.alarms.size} of ${it.maxCount}" }) {
        if (state.alarms.isEmpty()) PageNote("No alarms on the strap.")
        state.alarms.forEach { a ->
            Row(Modifier.fillMaxWidth().clickable(enabled = !state.busy) { onEdit(a) }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    org.openstrap.app.ui.glow.DotMatrixText(
                        "%02d:%02d".format(a.hour, a.minute), dot = org.openstrap.app.ui.glow.DotSize.Edge,
                        color = if (a.enabled) Color.White else Color.White.copy(alpha = 0.45f),
                    )
                    Text(daysLabel(a.days), style = GlowType.secondary, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(top = 6.dp))
                }
                org.openstrap.app.ui.glow.GlowSwitch(a.enabled, { onToggle(a, it) }, enabled = !state.busy)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.canAdd) PageButton("Add alarm", onAdd, Modifier.weight(1f), enabled = !state.busy)
            if (state.alarms.size > 1) PageButton("Delete all", onDeleteAll, Modifier.weight(1f), enabled = !state.busy, color = DestructiveInk)
        }
        // Zepp's tip for this strap (alarm_stuttgart_close_tips).
        PageNote("The strap vibrates at the alarm time. To stop it, tap the front of the strap several times.")
    }
}

@Composable
private fun HeartAlertsPanel(state: AlarmsUiState, vm: AlarmsViewModel) {
    val g = state.health ?: return
    PagePanel(title = "Heart rate and blood oxygen") {
        g[SettingsCodec.KEY_HR_INTERVAL]?.let { s ->
            ChoiceRow("Heart rate measuring", s.choices.orEmpty(), s.number?.toInt(), state.busy, ::intervalLabel) { vm.setHrInterval(it) }
        }
        val alerts = state.alertsAvailable
        if (!alerts) PageNote("Heart rate alerts need continuous or every-minute measuring.")
        g[SettingsCodec.KEY_HIGH_HR_ALERT]?.let { s ->
            ChoiceRow("High heart rate alert", s.choices.orEmpty(), s.number?.toInt(), state.busy || !alerts, { if (it == 0) "Off" else "Above $it" }) {
                vm.setEnum(SettingsCodec.KEY_HIGH_HR_ALERT, it)
            }
        }
        g[SettingsCodec.KEY_LOW_HR_ALERT]?.let { s ->
            ChoiceRow("Low heart rate alert", s.choices.orEmpty(), s.number?.toInt(), state.busy || !alerts, { if (it == 0) "Off" else "Below $it" }) {
                vm.setEnum(SettingsCodec.KEY_LOW_HR_ALERT, it)
            }
        }
        g[SettingsCodec.KEY_LOW_SPO2_ALERT]?.let { s ->
            ChoiceRow("Low blood oxygen alert", s.choices.orEmpty(), s.number?.toInt(), state.busy, { if (it == 0) "Off" else "Below $it%" }) {
                vm.setEnum(SettingsCodec.KEY_LOW_SPO2_ALERT, it)
            }
        }
        PageNote("The strap vibrates when an alert goes off. OpenStrap isn't a medical device.")
    }
}

@Composable
private fun ChoiceRow(title: String, choices: List<Int>, current: Int?, disabled: Boolean, label: (Int) -> String, onPick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = GlowType.label.copy(fontWeight = null), color = Color.White)
        ChoicePills(choices, current, label, onPick, enabled = !disabled)
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
                if (!isNew) TextButton(onClick = onDelete) { Text("Delete", color = DestructiveInk) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
