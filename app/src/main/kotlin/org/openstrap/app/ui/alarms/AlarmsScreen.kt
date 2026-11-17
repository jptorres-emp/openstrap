package org.openstrap.app.ui.alarms

import org.openstrap.app.ui.glow.HaloNote
import org.openstrap.app.ui.glow.NoteVariant
import org.openstrap.app.ui.glow.BannerKind
import org.openstrap.app.ui.glow.HaloBanner
import org.openstrap.app.ui.glow.CardFill
import org.openstrap.app.ui.glow.HaloCard
import org.openstrap.app.ui.glow.HaloPageHeader
import org.openstrap.app.ui.glow.HaloPageList
import org.openstrap.app.ui.glow.HaloTitle
import org.openstrap.app.ui.glow.PageVariant
import org.openstrap.app.ui.glow.TitleVariant
import org.openstrap.app.ui.theme.Space
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.openstrap.app.R
import org.openstrap.app.i18n.Strings
import org.openstrap.app.presentation.TimeOfDay
import org.openstrap.app.ui.glow.HaloButton
import org.openstrap.app.ui.glow.GlowLayout
import org.openstrap.app.ui.glow.ActionRow
import org.openstrap.app.ui.glow.ItemRow
import org.openstrap.app.ui.glow.HaloConfirmSheet
import org.openstrap.app.ui.theme.AppIcons
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.protocol.services.Alarm
import org.openstrap.protocol.services.SettingsCodec
import java.time.LocalTime

/** The days of the week, short, Monday first: "Mon", for an alarm on chosen days. */
internal val DAY_NAMES = listOf(
    R.string.you_alarms_day_mon, R.string.you_alarms_day_tue, R.string.you_alarms_day_wed, R.string.you_alarms_day_thu,
    R.string.you_alarms_day_fri, R.string.you_alarms_day_sat, R.string.you_alarms_day_sun,
)

/** The days of the week as one letter, Monday first, on the alarm sheet's chips. */
internal val DAY_LETTERS = listOf(
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
internal fun intervalLabel(v: Int, strings: Strings = Strings.app) = when (v) {
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
        daysLabel(first.days, strings),
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
        HaloConfirmSheet(
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
    HaloPageList(contentPadding, variant = PageVariant.Settings) {
        item { HaloPageHeader(stringResource(R.string.you_alarms_title), onBack) }
        (state.error ?: state.alarmsError)?.let { msg -> item {
            HaloBanner(
                BannerKind.Warn, msg,
                action = stringResource(R.string.you_alarms_try_again), onAction = { if (!state.busy) vm.refresh() },
            )
        } }
        if (state.loading) {
            item { HaloCard { HaloNote(stringResource(R.string.you_alarms_reading)) } }
            return@HaloPageList
        }
        // Never read: the alarms are unknown, not empty, so the error above is all there is to show.
        if (state.read is AlarmsRead.Failed) return@HaloPageList
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
    HaloPageList(contentPadding, variant = PageVariant.Settings) {
        item { HaloPageHeader(stringResource(R.string.you_health_title), onBack) }
        (state.error ?: state.healthError)?.let { msg -> item {
            HaloBanner(
                BannerKind.Warn, msg,
                action = stringResource(R.string.you_alarms_try_again), onAction = { if (!state.busy) vm.refresh() },
            )
        } }
        if (state.loading) {
            item { HaloCard { HaloNote(stringResource(R.string.you_alarms_reading)) } }
            return@HaloPageList
        }
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
    // Plain rows on the black page, the strap's alarms as item rows with a switch each (HaloAlarms board).
    Column(verticalArrangement = Arrangement.spacedBy(Space.S24)) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.S8)) {
            HaloTitle(state.caps?.let { stringResource(R.string.you_alarms_panel_count, state.alarms.size, it.maxCount) }
                    ?: stringResource(R.string.you_alarms_on_strap), variant = TitleVariant.Group)
            if (state.alarms.isEmpty()) HaloNote(stringResource(R.string.you_alarms_none))
            else HaloCard(fill = CardFill.Rows) {
                state.alarms.forEachIndexed { i, a ->
                    val (digits, marker) = dotClock(a.hour, a.minute)
                    ItemRow(
                        listOfNotNull(digits, marker).joinToString(" "), daysLabel(a.days), divider = i > 0,
                        onClick = if (state.busy) null else ({ onEdit(a) }),
                        trailing = { org.openstrap.app.ui.glow.GlowSwitch(a.enabled, { onToggle(a, it) }, enabled = !state.busy) },
                    )
                }
            }
            // Zepp's tip for this strap (alarm_stuttgart_close_tips).
            HaloNote(stringResource(R.string.you_alarms_tip), variant = NoteVariant.Footnote)
        }
        if (state.canAdd) HaloCard(fill = CardFill.Rows) {
            ActionRow(stringResource(R.string.you_alarms_add), onAdd, icon = AppIcons.Plus, enabled = !state.busy, divider = false)
        }
        // Destructive actions sit in a group of their own, last.
        if (state.alarms.size > 1) HaloCard(fill = CardFill.Rows) {
            ActionRow(
                stringResource(R.string.you_alarms_delete_all), onDeleteAll, icon = AppIcons.Trash, destructive = true,
                enabled = !state.busy, divider = false,
            )
        }
    }
}
