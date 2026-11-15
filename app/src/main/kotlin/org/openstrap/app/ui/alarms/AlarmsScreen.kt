package org.openstrap.app.ui.alarms

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.launch
import org.openstrap.app.R
import org.openstrap.app.i18n.Strings
import org.openstrap.app.presentation.TimeOfDay
import org.openstrap.app.ui.dashboard.ScreenList
import org.openstrap.app.ui.device.RadioSheet
import org.openstrap.app.ui.device.SheetActions
import org.openstrap.app.ui.glow.GlowLayout
import org.openstrap.app.ui.glow.GlowType
import org.openstrap.app.ui.glow.HaloButtonSurface
import org.openstrap.app.ui.glow.PageActionRow
import org.openstrap.app.ui.glow.PageButton
import org.openstrap.app.ui.glow.PageFootnote
import org.openstrap.app.ui.glow.PageGroup
import org.openstrap.app.ui.glow.PageHeader
import org.openstrap.app.ui.glow.PageItemRow
import org.openstrap.app.ui.glow.PageNote
import org.openstrap.app.ui.glow.PagePanel
import org.openstrap.app.ui.glow.PageRow
import org.openstrap.app.ui.glow.PageRows
import org.openstrap.app.ui.glow.PageSheet
import org.openstrap.app.ui.glow.PageSwitchRow
import org.openstrap.app.ui.glow.PageConfirm
import org.openstrap.app.ui.glow.PickedRing
import org.openstrap.app.ui.glow.PickedSurface
import org.openstrap.app.ui.glow.SectionLabel
import org.openstrap.app.ui.theme.AppIcons
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.protocol.services.Alarm
import org.openstrap.protocol.services.SettingsCodec
import java.time.LocalTime

/** The days of the week, short, Monday first: "Mon", for an alarm on chosen days. */
private val DAY_NAMES = listOf(
    R.string.you_alarms_day_mon, R.string.you_alarms_day_tue, R.string.you_alarms_day_wed, R.string.you_alarms_day_thu,
    R.string.you_alarms_day_fri, R.string.you_alarms_day_sat, R.string.you_alarms_day_sun,
)

/** The days of the week as one letter, Monday first, on the alarm sheet's chips. */
private val DAY_LETTERS = listOf(
    R.string.you_alarms_letter_mon, R.string.you_alarms_letter_tue, R.string.you_alarms_letter_wed, R.string.you_alarms_letter_thu,
    R.string.you_alarms_letter_fri, R.string.you_alarms_letter_sat, R.string.you_alarms_letter_sun,
)

/** "Once", "Every day", "Weekdays", "Weekends" or "Mon, Wed, Fri". */
fun daysLabel(days: Int, strings: Strings = Strings.app): String = when (days) {
    0 -> strings.get(R.string.you_alarms_days_once)
    Alarm.EVERY_DAY -> strings.get(R.string.you_alarms_days_every_day)
    Alarm.WEEKDAYS -> strings.get(R.string.you_alarms_days_weekdays)
    Alarm.WEEKEND -> strings.get(R.string.you_alarms_days_weekends)
    Alarm.WORKDAYS -> strings.get(R.string.you_alarms_days_workdays)
    else -> DAY_NAMES.filterIndexed { i, _ -> days and (1 shl i) != 0 }.joinToString(", ") { strings.get(it) }
}

/** How often the strap measures heart rate, as its menu says it. */
private fun intervalLabel(v: Int, strings: Strings = Strings.app) = when (v) {
    0 -> strings.get(R.string.you_value_off)
    SettingsCodec.HR_CONTINUOUS -> strings.get(R.string.you_alarms_interval_continuous)
    else -> strings.format(R.string.you_alarms_interval_every, v)
}

/**
 * What the Strap page's Alarms row says: the first alarm that's on ("06:40 weekdays"), how many more, "None", or null
 * while nothing has been read from the strap.
 */
fun alarmsSummary(state: AlarmsUiState, strings: Strings = Strings.app): String? {
    if (state.caps == null) return null
    val on = state.alarms.filter { it.enabled }
    val first = on.firstOrNull()
        ?: return strings.get(if (state.alarms.isEmpty()) R.string.you_alarms_summary_none else R.string.you_alarms_summary_all_off)
    val text = strings.format(
        R.string.you_alarms_summary_first,
        TimeOfDay.format(LocalTime.of(first.hour, first.minute)),
        daysLabel(first.days, strings).lowercase(),
    )
    val more = on.size - 1
    return if (more > 0) strings.plural(R.plurals.you_alarms_summary_more, more, more, text) else text
}

/**
 * What the Strap page's Health alerts row says, short enough for one line beside the name ([ROW_VALUE_MAX]): the
 * heart rate limits ("Over 150, under 40", "Over 150 bpm"), the oxygen one ("Oxygen under 90%"), "Heart rate and
 * oxygen" when both kinds are on, "Off" when none is, or null while the strap's settings haven't been read.
 */
fun healthAlertsSummary(state: AlarmsUiState, strings: Strings = Strings.app): String? {
    val g = state.health ?: return null
    fun on(key: Int) = g[key]?.number?.toInt()?.takeIf { it != 0 }
    val high = on(SettingsCodec.KEY_HIGH_HR_ALERT)
    val low = on(SettingsCodec.KEY_LOW_HR_ALERT)
    val oxygen = on(SettingsCodec.KEY_LOW_SPO2_ALERT)?.takeIf { state.oxygenAlertAvailable }
    val heart = high != null || low != null
    return when {
        heart && oxygen != null -> strings.get(R.string.you_health_summary_both)
        high != null && low != null -> strings.format(R.string.you_health_summary_over_under, high, low)
        high != null -> strings.format(R.string.you_health_summary_over, high)
        low != null -> strings.format(R.string.you_health_summary_under, low)
        oxygen != null -> strings.format(R.string.you_health_summary_oxygen, oxygen)
        else -> strings.get(R.string.you_value_off)
    }
}

/** The longest value a Strap page row shows beside its name and still keeps to one line on a 360 dp phone. */
const val ROW_VALUE_MAX = 22

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
        PageConfirm(
            stringResource(R.string.you_alarms_delete_all_title), stringResource(R.string.you_alarms_delete_all_text),
            confirm = stringResource(R.string.you_alarms_delete_all), onConfirm = { vm.deleteAll(); confirmDeleteAll = false },
            onDismiss = { confirmDeleteAll = false }, destructive = true,
        )
    }
    // A refresh can change the alarm under an open sheet (a one-time alarm switching itself off, say). Saving the old copy
    // would undo that, so the sheet moves to the refreshed alarm and starts from its values; if the alarm is gone, or the
    // free slot of a new one was taken, there is nothing left to edit and the sheet closes.
    SideEffect {
        val open = editing ?: return@SideEffect
        if (state.read !is AlarmsRead.Loaded) return@SideEffect
        val (alarm, isNew) = open
        val now = state.alarms.firstOrNull { it.index == alarm.index }
        editing = when {
            isNew -> if (now == null) open else null
            now == null -> null
            now != alarm -> now to false
            else -> open
        }
    }
    val scope = rememberCoroutineScope()
    editing?.let { (alarm, isNew) ->
        // The sheet closes only when the strap took the change; a failure or a busy strap leaves it open for another try.
        key(alarm, isNew) {
            AlarmSheet(
                alarm, isNew, busy = state.busy,
                onSave = { edited -> scope.launch { if (vm.save(edited, isNew).await()) editing = null } },
                onDelete = { scope.launch { if (vm.delete(alarm).await()) editing = null } },
                onDismiss = { editing = null },
            )
        }
    }
    ScreenList(contentPadding, gutter = GlowLayout.Gutter) {
        item { PageHeader(stringResource(R.string.you_alarms_title), onBack) }
        (state.error ?: state.alarmsError)?.let { msg -> item { ErrorPanel(msg, state.busy, vm::refresh) } }
        if (state.loading) {
            item { PagePanel { PageNote(stringResource(R.string.you_alarms_reading)) } }
            return@ScreenList
        }
        // Never read: the alarms are unknown, not empty, so the error above is all there is to show.
        if (state.read is AlarmsRead.Failed) return@ScreenList
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
        item { PageHeader(stringResource(R.string.you_health_title), onBack) }
        (state.error ?: state.healthError)?.let { msg -> item { ErrorPanel(msg, state.busy, vm::refresh) } }
        if (state.loading) {
            item { PagePanel { PageNote(stringResource(R.string.you_alarms_reading)) } }
            return@ScreenList
        }
        state.health?.let { item { HeartAlertsPanel(state, vm) } }
    }
}

@Composable
private fun ErrorPanel(message: String, busy: Boolean, onRetry: () -> Unit) {
    PagePanel {
        PageNote(message, color = MetricColors.warn)
        PageButton(stringResource(R.string.you_alarms_try_again), onRetry, Modifier.fillMaxWidth(), enabled = !busy)
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
    // Plain rows on the black page, the strap's alarms as item rows with a switch each (HaloAlarms board).
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val count = state.caps?.let { stringResource(R.string.you_alarms_count, state.alarms.size, it.maxCount) }
            SectionLabel(listOfNotNull(stringResource(R.string.you_alarms_on_strap), count).joinToString(", "))
            if (state.alarms.isEmpty()) PageNote(stringResource(R.string.you_alarms_none))
            else PageRows {
                state.alarms.forEachIndexed { i, a ->
                    val (digits, marker) = dotClock(a.hour, a.minute)
                    PageItemRow(
                        listOfNotNull(digits, marker).joinToString(" "), daysLabel(a.days), divider = i > 0,
                        onClick = if (state.busy) null else ({ onEdit(a) }),
                        trailing = { org.openstrap.app.ui.glow.GlowSwitch(a.enabled, { onToggle(a, it) }, enabled = !state.busy) },
                    )
                }
            }
            // Zepp's tip for this strap (alarm_stuttgart_close_tips).
            PageFootnote(stringResource(R.string.you_alarms_tip))
        }
        if (state.canAdd) PageRows {
            PageActionRow(stringResource(R.string.you_alarms_add), onAdd, icon = AppIcons.Plus, enabled = !state.busy, divider = false)
        }
        // Destructive actions sit in a group of their own, last.
        if (state.alarms.size > 1) PageRows {
            PageActionRow(
                stringResource(R.string.you_alarms_delete_all), onDeleteAll, icon = AppIcons.Trash, destructive = true,
                enabled = !state.busy, divider = false,
            )
        }
    }
}

/** Which of the Health alerts page's picker sheets is open: its settings key and the sheet's kind. */
private enum class HealthSheet { Interval, High, Low, Oxygen }

/**
 * The strap's heart rate and oxygen settings as the HaloHealthAlerts board draws them: the measuring interval, then one
 * switch and one limit row for each alert. An alert's switch is its menu's "off" entry, so turning it on sets the limit
 * [defaultLimit] picks and the limit row opens a sheet with the other choices.
 */
@Composable
private fun HeartAlertsPanel(state: AlarmsUiState, vm: AlarmsViewModel) {
    val g = state.health ?: return
    val alerts = state.alertsAvailable
    var sheet by rememberSaveable { mutableStateOf<HealthSheet?>(null) }
    val bpm = { v: Int -> Strings.app.format(R.string.you_health_bpm_value, v) }
    val percent = { v: Int -> Strings.app.format(R.string.you_health_percent_value, v) }
    val interval = g[SettingsCodec.KEY_HR_INTERVAL]
    val high = g[SettingsCodec.KEY_HIGH_HR_ALERT]
    val low = g[SettingsCodec.KEY_LOW_HR_ALERT]
    val oxygen = g[SettingsCodec.KEY_LOW_SPO2_ALERT]
    val oxygenOk = state.oxygenAlertAvailable

    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        if (interval != null) PageGroup(footnote = stringResource(R.string.you_health_measuring_note)) {
            PageRow(
                stringResource(R.string.you_health_measuring), interval.number?.toInt()?.let { intervalLabel(it) },
                onClick = { sheet = HealthSheet.Interval }, enabled = !state.busy, divider = false,
            )
        }
        if (high != null || low != null) PageGroup(heading = stringResource(R.string.you_health_group_heart)) {
            var first = true
            fun divider() = !first.also { first = false }
            if (high != null) AlertRows(
                stringResource(R.string.you_health_high), high, enabled = !state.busy && alerts, label = bpm, divider = divider(),
                onSwitch = { vm.setEnum(SettingsCodec.KEY_HIGH_HR_ALERT, if (it) defaultLimit(high, last = true) else 0) },
                onLimit = { sheet = HealthSheet.High }, limitName = stringResource(R.string.you_health_row_above),
            )
            if (low != null) AlertRows(
                stringResource(R.string.you_health_low), low, enabled = !state.busy && alerts, label = bpm, divider = divider(),
                onSwitch = { vm.setEnum(SettingsCodec.KEY_LOW_HR_ALERT, if (it) defaultLimit(low, last = false) else 0) },
                onLimit = { sheet = HealthSheet.Low }, limitName = stringResource(R.string.you_health_row_below),
            )
        }
        val autoOxygen = g[SettingsCodec.KEY_SMART_SPO2]?.bool
        if (autoOxygen != null || oxygen != null) PageGroup(
            heading = stringResource(R.string.you_health_group_oxygen), footnote = stringResource(R.string.you_health_note),
        ) {
            var first = true
            fun divider() = !first.also { first = false }
            if (autoOxygen != null) PageSwitchRow(
                stringResource(R.string.you_health_auto_oxygen), stringResource(R.string.you_health_auto_oxygen_note), autoOxygen,
                { vm.setAutoOxygen(it) }, divider = divider(), enabled = !state.busy,
            )
            if (oxygen != null) AlertRows(
                stringResource(R.string.you_health_oxygen), oxygen, enabled = !state.busy && oxygenOk, label = percent, divider = divider(),
                onSwitch = { vm.setEnum(SettingsCodec.KEY_LOW_SPO2_ALERT, if (it) defaultLimit(oxygen, last = true) else 0) },
                onLimit = { sheet = HealthSheet.Oxygen }, limitName = stringResource(R.string.you_health_row_below),
                note = if (oxygenOk) null else stringResource(R.string.you_health_oxygen_note),
            )
        }
    }

    val close = { sheet = null }
    when (sheet) {
        HealthSheet.Interval -> if (interval != null) {
            val choices = interval.choices.orEmpty()
            RadioSheet(
                stringResource(R.string.you_health_measuring), choices.map { intervalLabel(it) }, choices.indexOf(interval.number?.toInt()),
                onSave = { vm.setHrInterval(choices[it]); close() }, onDismiss = close,
            )
        }
        HealthSheet.High -> if (high != null) LimitSheet(stringResource(R.string.you_health_high), high, bpm, { vm.setEnum(SettingsCodec.KEY_HIGH_HR_ALERT, it) }, close)
        HealthSheet.Low -> if (low != null) LimitSheet(stringResource(R.string.you_health_low), low, bpm, { vm.setEnum(SettingsCodec.KEY_LOW_HR_ALERT, it) }, close)
        HealthSheet.Oxygen -> if (oxygen != null) LimitSheet(stringResource(R.string.you_health_oxygen), oxygen, percent, { vm.setEnum(SettingsCodec.KEY_LOW_SPO2_ALERT, it) }, close)
        null -> Unit
    }
}

/** The limit an alert starts at when its switch goes on: the highest choice for an upper limit, the first for a lower one. */
private fun defaultLimit(s: org.openstrap.protocol.services.Setting, last: Boolean): Int {
    val on = s.choices.orEmpty().filter { it != 0 }
    return (if (last) on.lastOrNull() else on.firstOrNull()) ?: 0
}

/** An alert's switch row, and under it, while it is on, the row with its limit that opens the picker. */
@Composable
private fun AlertRows(
    name: String,
    setting: org.openstrap.protocol.services.Setting,
    enabled: Boolean,
    label: (Int) -> String,
    divider: Boolean,
    onSwitch: (Boolean) -> Unit,
    onLimit: () -> Unit,
    limitName: String,
    note: String? = null,
) {
    val limit = setting.number?.toInt() ?: 0
    PageSwitchRow(name, note, limit != 0, onSwitch, divider = divider, enabled = enabled)
    if (limit != 0) PageRow(limitName, label(limit), onClick = onLimit, enabled = enabled)
}

/** The choices of an alert's limit, "off" left to its switch. */
@Composable
private fun LimitSheet(title: String, setting: org.openstrap.protocol.services.Setting, label: (Int) -> String, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    val choices = setting.choices.orEmpty().filter { it != 0 }
    RadioSheet(
        title, choices.map(label), choices.indexOf(setting.number?.toInt()),
        onSave = { onSave(choices[it]); onDismiss() }, onDismiss = onDismiss,
    )
}

/**
 * A new alarm or one being changed, in a sheet (HaloAlarmSheet board): the time on two wheels, the days as 44 squircle chips,
 * then Cancel and Save, with Delete under them when it already exists. A picked day is the picked state of the settings
 * rules (#34363E with a white ring), since Save is the sheet's white tap.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlarmSheet(alarm: Alarm, isNew: Boolean, busy: Boolean, onSave: (Alarm) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
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
                    DayChip(stringResource(d), days != Alarm.WORKDAYS && days and bit != 0, stringResource(DAY_NAMES[i]), Modifier.weight(1f)) {
                        days = (if (days == Alarm.WORKDAYS) 0 else days) xor bit
                    }
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
private fun DayChip(letter: String, picked: Boolean, description: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(44.dp).clip(GlowLayout.IconShape)
            .background(if (picked) PickedSurface else HaloButtonSurface)
            .let { if (picked) it.border(PickedRing, Color.White, GlowLayout.IconShape) else it }
            .clickable(role = Role.Checkbox, onClick = onClick)
            .semantics { selected = picked; contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(letter, color = if (picked) Color.White else GlowType.noteColor, style = GlowType.buttonSmall, modifier = Modifier.clearAndSetSemantics {})
    }
}
