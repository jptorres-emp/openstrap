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
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.foundation.layout.widthIn
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
import org.openstrap.app.ui.glow.ControlSurface
import org.openstrap.app.ui.glow.PageButton
import org.openstrap.app.ui.glow.PageChip
import org.openstrap.app.ui.glow.PageConfirm
import org.openstrap.app.ui.glow.PageSheet
import org.openstrap.app.ui.glow.PageTextButton
import org.openstrap.app.ui.glow.PageHeader
import org.openstrap.app.ui.glow.PageNote
import org.openstrap.app.ui.glow.PagePanel
import org.openstrap.app.ui.dashboard.ScreenList
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.protocol.services.Alarm
import org.openstrap.protocol.services.SettingsCodec
import org.openstrap.app.l10n.tr

private val DAY_LETTERS = listOf("M", "T", "W", "T", "F", "S", "S")
private val DAY_NAMES: List<String> get() = listOf(tr("Mon", "Seg"), tr("Tue", "Ter"), tr("Wed", "Qua"), tr("Thu", "Qui"), tr("Fri", "Sex"), tr("Sat", "Sáb"), tr("Sun", "Dom"))

/** "Once", "Every day", "Weekdays", "Weekends" or "Mon, Wed, Fri". */
fun daysLabel(days: Int): String = when (days) {
    0 -> tr("Once", "Uma vez")
    Alarm.EVERY_DAY -> tr("Every day", "Todos os dias")
    Alarm.WEEKDAYS -> tr("Weekdays", "Dias úteis")
    Alarm.WEEKEND -> tr("Weekends", "Fins de semana")
    Alarm.WORKDAYS -> tr("Workdays", "Dias de trabalho")
    else -> DAY_NAMES.filterIndexed { i, _ -> days and (1 shl i) != 0 }.joinToString(", ")
}

private fun intervalLabel(v: Int) = when (v) {
    0 -> tr("Off", "Desligado")
    SettingsCodec.HR_CONTINUOUS -> tr("Continuous", "Contínuo")
    else -> tr("Every $v min", "A cada $v min")
}

/**
 * What the Strap page's Alarms row says: the first alarm that's on ("06:40 weekdays"), how many more, "None", or null
 * while nothing has been read from the strap.
 */
fun alarmsSummary(state: AlarmsUiState): String? {
    if (state.caps == null) return null
    val on = state.alarms.filter { it.enabled }
    val first = on.firstOrNull() ?: return if (state.alarms.isEmpty()) tr("None", "Nenhum") else tr("All off", "Todos desligados")
    val text = "%02d:%02d %s".format(first.hour, first.minute, daysLabel(first.days).lowercase())
    return if (on.size > 1) tr("$text and ${on.size - 1} more", "$text e mais ${on.size - 1}") else text
}

/**
 * What the Strap page's Health alerts row says, short enough for one line beside the name ([ROW_VALUE_MAX]): the
 * heart rate limits ("Over 150, under 40", "Over 150 bpm"), the oxygen one ("Oxygen under 90%"), "Heart rate and
 * oxygen" when both kinds are on, "Off" when none is, or null while the strap's settings haven't been read.
 */
fun healthAlertsSummary(state: AlarmsUiState): String? {
    val g = state.health ?: return null
    fun on(key: Int) = g[key]?.number?.toInt()?.takeIf { it != 0 }
    val high = on(SettingsCodec.KEY_HIGH_HR_ALERT)
    val low = on(SettingsCodec.KEY_LOW_HR_ALERT)
    val oxygen = on(SettingsCodec.KEY_LOW_SPO2_ALERT)
    val heart = high != null || low != null
    return when {
        heart && oxygen != null -> tr("Heart rate and oxygen", "Frequência cardíaca e oxigénio")
        high != null && low != null -> tr("Over $high, under $low", "Acima de $high, abaixo de $low")
        high != null -> tr("Over $high bpm", "Acima de $high bpm")
        low != null -> tr("Under $low bpm", "Abaixo de $low bpm")
        oxygen != null -> tr("Oxygen under $oxygen%", "Oxigénio abaixo de $oxygen%")
        else -> tr("Off", "Desligado")
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
            tr("Delete all alarms?", "Apagar todos os alarmes?"), tr("This removes every alarm from the strap.", "Isto remove todos os alarmes da strap."),
            confirm = tr("Delete all", "Apagar todos"), onConfirm = { vm.deleteAll(); confirmDeleteAll = false }, onDismiss = { confirmDeleteAll = false },
        )
    }
    editing?.let { (alarm, isNew) ->
        AlarmSheet(
            alarm, isNew,
            onSave = { vm.save(it, isNew); editing = null },
            onDelete = { vm.delete(alarm); editing = null },
            onDismiss = { editing = null },
        )
    }
    ScreenList(contentPadding, gutter = GlowLayout.Gutter) {
        item { PageHeader(tr("Alarms", "Alarmes"), onBack) }
        state.error?.let { msg -> item { ErrorPanel(msg, state.busy, vm::refresh) } }
        if (state.loading) {
            item { PagePanel { PageNote(tr("Reading from the strap", "A ler da strap")) } }
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
        item { PageHeader(tr("Health alerts", "Alertas de saúde"), onBack) }
        state.error?.let { msg -> item { ErrorPanel(msg, state.busy, vm::refresh) } }
        if (state.loading) {
            item { PagePanel { PageNote(tr("Reading from the strap", "A ler da strap")) } }
            return@ScreenList
        }
        state.health?.let { item { HeartAlertsPanel(state, vm) } }
    }
}

@Composable
private fun ErrorPanel(message: String, busy: Boolean, onRetry: () -> Unit) {
    PagePanel {
        PageNote(message, color = MetricColors.warn)
        PageButton(tr("Try again", "Tentar de novo"), onRetry, Modifier.fillMaxWidth(), enabled = !busy)
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
    PagePanel(title = tr("On the strap", "Na strap"), note = state.caps?.let { tr("${state.alarms.size} of ${it.maxCount}", "${state.alarms.size} de ${it.maxCount}") }) {
        if (state.alarms.isEmpty()) PageNote(tr("No alarms on the strap.", "Nenhum alarme na strap."))
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
        if (state.canAdd) PageButton(tr("Add alarm", "Juntar alarme"), onAdd, Modifier.fillMaxWidth(), enabled = !state.busy, primary = true)
        if (state.alarms.size > 1) {
            PageTextButton(tr("Delete all", "Apagar todos"), onDeleteAll, Modifier.align(Alignment.CenterHorizontally), enabled = !state.busy, color = DestructiveInk)
        }
        // Zepp's tip for this strap (alarm_stuttgart_close_tips).
        PageNote(tr(
            "The strap vibrates at the alarm time. To stop it, tap the front of the strap several times.",
            "A strap vibra à hora do alarme. Para parar, toca várias vezes na frente da strap.",
        ))
    }
}

@Composable
private fun HeartAlertsPanel(state: AlarmsUiState, vm: AlarmsViewModel) {
    val g = state.health ?: return
    PagePanel(title = tr("Heart rate and blood oxygen", "Frequência cardíaca e oxigénio no sangue")) {
        g[SettingsCodec.KEY_HR_INTERVAL]?.let { s ->
            ChoiceRow(tr("Heart rate measuring", "Medição da frequência cardíaca"), s.choices.orEmpty(), s.number?.toInt(), state.busy, ::intervalLabel) { vm.setHrInterval(it) }
        }
        val alerts = state.alertsAvailable
        if (!alerts) PageNote(tr(
            "Heart rate alerts need continuous or every-minute measuring.",
            "Os alertas de frequência cardíaca precisam de medição contínua ou a cada minuto.",
        ))
        g[SettingsCodec.KEY_HIGH_HR_ALERT]?.let { s ->
            ChoiceRow(tr("High heart rate alert", "Alerta de frequência cardíaca alta"), s.choices.orEmpty(), s.number?.toInt(), state.busy || !alerts, { if (it == 0) tr("Off", "Desligado") else tr("Above $it", "Acima de $it") }) {
                vm.setEnum(SettingsCodec.KEY_HIGH_HR_ALERT, it)
            }
        }
        g[SettingsCodec.KEY_LOW_HR_ALERT]?.let { s ->
            ChoiceRow(tr("Low heart rate alert", "Alerta de frequência cardíaca baixa"), s.choices.orEmpty(), s.number?.toInt(), state.busy || !alerts, { if (it == 0) tr("Off", "Desligado") else tr("Below $it", "Abaixo de $it") }) {
                vm.setEnum(SettingsCodec.KEY_LOW_HR_ALERT, it)
            }
        }
        g[SettingsCodec.KEY_LOW_SPO2_ALERT]?.let { s ->
            ChoiceRow(tr("Low blood oxygen alert", "Alerta de oxigénio no sangue baixo"), s.choices.orEmpty(), s.number?.toInt(), state.busy, { if (it == 0) tr("Off", "Desligado") else tr("Below $it%", "Abaixo de $it%") }) {
                vm.setEnum(SettingsCodec.KEY_LOW_SPO2_ALERT, it)
            }
        }
        PageNote(tr(
            "The strap vibrates when an alert goes off. OpenStrap isn't a medical device.",
            "A strap vibra quando um alerta dispara. A OpenStrap não é um dispositivo médico.",
        ))
    }
}

@Composable
private fun ChoiceRow(title: String, choices: List<Int>, current: Int?, disabled: Boolean, label: (Int) -> String, onPick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = GlowType.body, color = Color.White)
        ChoicePills(choices, current, label, onPick, enabled = !disabled)
    }
}

/**
 * A new alarm or one being changed, in a sheet: the time, the days as chips, then Cancel and Save, with Delete as quiet
 * text under them. The time wheel is Material's clock, recoloured to white on the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AlarmSheet(alarm: Alarm, isNew: Boolean, onSave: (Alarm) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val time = rememberTimePickerState(alarm.hour, alarm.minute, is24Hour = true)
    var days by remember { mutableIntStateOf(alarm.days) }
    PageSheet(onDismiss, if (isNew) tr("New alarm", "Novo alarme") else tr("Edit alarm", "Editar alarme"), note = daysLabel(days)) {
        TimePicker(time, Modifier.align(Alignment.CenterHorizontally), colors = AlarmClockColors())
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
            DAY_LETTERS.forEachIndexed { i, d ->
                val bit = 1 shl i
                // Picking a day replaces the strap's own "workdays" calendar with a plain day set.
                PageChip(d, days != Alarm.WORKDAYS && days and bit != 0, {
                    days = (if (days == Alarm.WORKDAYS) 0 else days) xor bit
                }, Modifier.widthIn(min = 40.dp))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PageButton(tr("Cancel", "Cancelar"), onDismiss, Modifier.weight(1f))
            PageButton(tr("Save", "Guardar"), { onSave(alarm.copy(hour = time.hour, minute = time.minute, days = days, enabled = true)) }, Modifier.weight(1f), primary = true)
        }
        if (!isNew) PageTextButton(tr("Delete alarm", "Apagar alarme"), onDelete, Modifier.align(Alignment.CenterHorizontally), color = DestructiveInk)
    }
}

/** The clock in the page's colours: white for the chosen hand and digits, the Control surface for the rest. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmClockColors() = TimePickerDefaults.colors(
    clockDialColor = ControlSurface,
    clockDialSelectedContentColor = Color.Black,
    clockDialUnselectedContentColor = Color.White,
    selectorColor = Color.White,
    containerColor = Color.Transparent,
    timeSelectorSelectedContainerColor = Color.White,
    timeSelectorUnselectedContainerColor = ControlSurface,
    timeSelectorSelectedContentColor = Color.Black,
    timeSelectorUnselectedContentColor = Color.White,
)
