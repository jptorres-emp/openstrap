package org.openstrap.app.ui.alarms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.openstrap.app.R
import org.openstrap.app.i18n.Strings
import org.openstrap.app.ui.device.RadioSheet
import org.openstrap.app.ui.glow.PageGroup
import org.openstrap.app.ui.glow.PageRow
import org.openstrap.app.ui.glow.PageSwitchRow
import org.openstrap.protocol.services.Setting
import org.openstrap.protocol.services.SettingsCodec

/** Which of the Health alerts page's picker sheets is open. */
private enum class HealthSheet { Interval, High, Low, Oxygen }

/**
 * The strap's heart rate and oxygen settings as the HaloHealthAlerts board draws them: the measuring interval, then one
 * switch and one limit row for each alert. An alert's switch is its menu's "off" entry, so turning it on sets the limit
 * [defaultLimit] picks and the limit row opens a sheet with the other choices.
 */
@Composable
internal fun HeartAlertsPanel(state: AlarmsUiState, vm: AlarmsViewModel) {
    val g = state.health ?: return
    var sheet by rememberSaveable { mutableStateOf<HealthSheet?>(null) }
    val interval = g[SettingsCodec.KEY_HR_INTERVAL]
    val high = g[SettingsCodec.KEY_HIGH_HR_ALERT]
    val low = g[SettingsCodec.KEY_LOW_HR_ALERT]
    val oxygen = g[SettingsCodec.KEY_LOW_SPO2_ALERT]

    Column(verticalArrangement = Arrangement.spacedBy(GROUP_GAP.dp)) {
        if (interval != null) IntervalGroup(interval, state.busy) { sheet = HealthSheet.Interval }
        if (high != null || low != null) HeartGroup(high, low, state, vm::setEnum) { sheet = it }
        OxygenGroup(g[SettingsCodec.KEY_SMART_SPO2]?.bool, oxygen, state, vm::setAutoOxygen, vm::setEnum) { sheet = HealthSheet.Oxygen }
    }

    val close = { sheet = null }
    val bpm = { v: Int -> Strings.app.format(R.string.you_health_bpm_value, v) }
    when (sheet) {
        HealthSheet.Interval -> if (interval != null) {
            val choices = interval.choices.orEmpty()
            RadioSheet(
                stringResource(R.string.you_health_measuring), choices.map { intervalLabel(it) }, choices.indexOf(interval.number?.toInt()),
                onSave = { vm.setHrInterval(choices[it]); close() }, onDismiss = close,
            )
        }
        HealthSheet.High -> if (high != null) {
            LimitSheet(stringResource(R.string.you_health_high), high, bpm, { vm.setEnum(SettingsCodec.KEY_HIGH_HR_ALERT, it) }, close)
        }
        HealthSheet.Low -> if (low != null) {
            LimitSheet(stringResource(R.string.you_health_low), low, bpm, { vm.setEnum(SettingsCodec.KEY_LOW_HR_ALERT, it) }, close)
        }
        HealthSheet.Oxygen -> if (oxygen != null) {
            val percent = { v: Int -> Strings.app.format(R.string.you_health_percent_value, v) }
            LimitSheet(stringResource(R.string.you_health_oxygen), oxygen, percent, { vm.setEnum(SettingsCodec.KEY_LOW_SPO2_ALERT, it) },
                close)
        }
        null -> Unit
    }
}

private const val GROUP_GAP = 24

/** How often the strap measures heart rate, a value row that opens its choices, with the note on what the alerts need. */
@Composable
private fun IntervalGroup(interval: Setting, busy: Boolean, onOpen: () -> Unit) {
    PageGroup(footnote = stringResource(R.string.you_health_measuring_note)) {
        PageRow(
            stringResource(R.string.you_health_measuring), interval.number?.toInt()?.let { intervalLabel(it) },
            onClick = onOpen, enabled = !busy, divider = false,
        )
    }
}

/** The high and low heart rate alerts. [open] is told which one's limit to pick. */
@Composable
private fun HeartGroup(high: Setting?, low: Setting?, state: AlarmsUiState, setEnum: (Int, Int) -> Unit, open: (HealthSheet) -> Unit) {
    val bpm = { v: Int -> Strings.app.format(R.string.you_health_bpm_value, v) }
    val enabled = !state.busy && state.alertsAvailable
    PageGroup(heading = stringResource(R.string.you_health_group_heart)) {
        if (high != null) AlertRows(
            stringResource(R.string.you_health_high), high, enabled = enabled, label = bpm, divider = false,
            onSwitch = { setEnum(SettingsCodec.KEY_HIGH_HR_ALERT, if (it) defaultLimit(high, last = true) else 0) },
            onLimit = { open(HealthSheet.High) }, limitName = stringResource(R.string.you_health_row_above),
        )
        if (low != null) AlertRows(
            stringResource(R.string.you_health_low), low, enabled = enabled, label = bpm, divider = high != null,
            onSwitch = { setEnum(SettingsCodec.KEY_LOW_HR_ALERT, if (it) defaultLimit(low, last = false) else 0) },
            onLimit = { open(HealthSheet.Low) }, limitName = stringResource(R.string.you_health_row_below),
        )
    }
}

/** Auto blood oxygen and the low oxygen alert. */
@Composable
private fun OxygenGroup(
    auto: Boolean?,
    oxygen: Setting?,
    state: AlarmsUiState,
    setAuto: (Boolean) -> Unit,
    setEnum: (Int, Int) -> Unit,
    onLimit: () -> Unit,
) {
    if (auto == null && oxygen == null) return
    val available = state.oxygenAlertAvailable
    val percent = { v: Int -> Strings.app.format(R.string.you_health_percent_value, v) }
    PageGroup(heading = stringResource(R.string.you_health_group_oxygen), footnote = stringResource(R.string.you_health_note)) {
        if (auto != null) PageSwitchRow(
            stringResource(R.string.you_health_auto_oxygen), stringResource(R.string.you_health_auto_oxygen_note), auto,
            setAuto, divider = false, enabled = !state.busy,
        )
        if (oxygen != null) AlertRows(
            stringResource(R.string.you_health_oxygen), oxygen, enabled = !state.busy && available, label = percent, divider = auto != null,
            onSwitch = { setEnum(SettingsCodec.KEY_LOW_SPO2_ALERT, if (it) defaultLimit(oxygen, last = true) else 0) },
            onLimit = onLimit, limitName = stringResource(R.string.you_health_row_below),
            note = if (available) null else stringResource(R.string.you_health_oxygen_note),
        )
    }
}

/** The limit an alert starts at when its switch goes on: the highest choice for an upper limit, the first for a lower one. */
private fun defaultLimit(s: Setting, last: Boolean): Int {
    val on = s.choices.orEmpty().filter { it != 0 }
    return (if (last) on.lastOrNull() else on.firstOrNull()) ?: 0
}

/** An alert's switch row, and under it, while it is on, the row with its limit that opens the picker. */
@Composable
private fun AlertRows(
    name: String,
    setting: Setting,
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
private fun LimitSheet(title: String, setting: Setting, label: (Int) -> String, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    val choices = setting.choices.orEmpty().filter { it != 0 }
    RadioSheet(
        title, choices.map(label), choices.indexOf(setting.number?.toInt()),
        onSave = { onSave(choices[it]); onDismiss() }, onDismiss = onDismiss,
    )
}
