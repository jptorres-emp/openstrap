package org.openstrap.app.ui.alarms

import org.openstrap.app.ui.glow.HaloNote
import org.openstrap.app.ui.glow.HaloPageHeader
import org.openstrap.app.ui.glow.HaloPageList
import org.openstrap.app.ui.glow.PageVariant
import androidx.compose.foundation.layout.padding
import org.openstrap.app.ui.glow.ButtonPair
import org.openstrap.app.ui.theme.Space
import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import org.openstrap.app.R
import org.openstrap.app.i18n.Strings
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.openstrap.app.data.settings.BuzzPattern
import org.openstrap.app.data.settings.CallBuzz
import org.openstrap.app.data.settings.CallBuzzLength
import org.openstrap.app.data.settings.QuietHours
import org.openstrap.app.device.BlePermissions
import org.openstrap.app.device.StrapNotificationListener
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import org.openstrap.app.ui.glow.HaloButton
import org.openstrap.app.ui.glow.GlowLayout
import org.openstrap.app.ui.device.SegmentSheet
import org.openstrap.app.ui.glow.PageGroup
import org.openstrap.app.ui.glow.NavRow
import org.openstrap.app.ui.glow.HaloSheet
import org.openstrap.app.ui.glow.SwitchRow
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.app.presentation.Ranges
import org.openstrap.app.presentation.TimeOfDay

/** What the Strap page's Phone alerts row says: "Calls and notifications", "Calls", "Notifications" or "Off". */
fun phoneAlertsSummary(calls: Boolean, notifications: Boolean, strings: Strings = Strings.app): String = strings.get(
    when {
        calls && notifications -> R.string.you_phone_summary_both
        calls -> R.string.you_phone_summary_calls
        notifications -> R.string.you_phone_summary_notifications
        else -> R.string.you_value_off
    },
)

/** Minutes after midnight on the phone's clock: "22:00", or "10:00 PM". */
internal fun quietClock(minutes: Int): String = TimeOfDay.minutes(minutes)

/** Under the Quiet hours switch: what it holds back, and when. */
internal fun quietHoursSummary(q: QuietHours, callsOn: Boolean, strings: Strings = Strings.app): String = when {
    !q.on -> strings.get(R.string.you_phone_quiet_off)
    q.from == q.to -> strings.get(R.string.you_phone_quiet_same)
    q.calls && callsOn -> strings.format(R.string.you_phone_quiet_with_calls, Ranges.of(quietClock(q.from), quietClock(q.to)))
    else -> strings.format(R.string.you_phone_quiet_notifications, Ranges.of(quietClock(q.from), quietClock(q.to)))
}

/**
 * Buzzing the strap on calls and notifications, opened from the Strap page. These are phone settings, so they work
 * offline too. (The off-body reminder is a row of its own on the Strap page.)
 */
@Composable
fun PhoneAlertsScreen(vm: PhoneAlertsViewModel, contentPadding: PaddingValues, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val calls by vm.calls.collectAsStateWithLifecycle()
    val notifications by vm.notifications.collectAsStateWithLifecycle()
    val callPermission by vm.callPermission.collectAsStateWithLifecycle()
    val notificationAccess by vm.notificationAccess.collectAsStateWithLifecycle()
    val pattern by vm.pattern.collectAsStateWithLifecycle()
    val callStyle by vm.callStyle.collectAsStateWithLifecycle()
    val callLength by vm.callLength.collectAsStateWithLifecycle()
    val previewError by vm.previewError.collectAsStateWithLifecycle()
    val callPreviewError by vm.callPreviewError.collectAsStateWithLifecycle()
    DisposableEffect(vm) { onDispose { vm.clearPreviewErrors() } }
    val quiet by vm.quiet.collectAsStateWithLifecycle()
    // Which end of the quiet hours is being picked: true for From, false for To.
    var pickingFrom by remember { mutableStateOf<Boolean?>(null) }


    // Phone access turned down for good: Android shows no dialog any more, so the switch offers settings instead (audit E5).
    val activity = androidx.activity.compose.LocalActivity.current
    var phoneBlocked by rememberSaveable { mutableStateOf(false) }
    val askPhone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.refresh()
        if (granted) vm.setBuzzOnCalls(true)
        phoneBlocked = !granted && activity != null &&
            BlePermissions.turnedDownForGood(activity, listOf(Manifest.permission.READ_PHONE_STATE))
    }
    val openAppSettings = { context.startActivity(BlePermissions.settingsIntent(context)) }
    // The user may grant access in system settings and come back. Phone access turned on there counts afresh, so a
    // later turn-down asks again before it counts as for good.
    LifecycleResumeEffect(Unit) {
        vm.refresh()
        if (vm.hasCallPermission()) phoneBlocked = false
        onPauseOrDispose { }
    }
    val openNotificationAccess = {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, ComponentName(context, StrapNotificationListener::class.java).flattenToString())
        runCatching { context.startActivity(intent) }
            .onFailure { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        Unit
    }

    // Which picker sheet is open: the call buzz, how long it runs, or the notification pattern.
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    val callsOn = calls && callPermission
    val notificationsOn = notifications && notificationAccess

    HaloPageList(contentPadding, variant = PageVariant.Settings) {
        item { HaloPageHeader(stringResource(R.string.you_phone_title), onBack) }
        // Rows as the HaloPhoneAlerts board draws them: switches, and value rows that open a picker sheet.
        item {
            PageGroup(heading = stringResource(R.string.you_phone_group_calls)) {
                SwitchRow(
                    stringResource(R.string.you_phone_calls), stringResource(R.string.you_phone_calls_note),
                    callsOn,
                    { on ->
                        when {
                            !on -> vm.setBuzzOnCalls(false)
                            callPermission -> vm.setBuzzOnCalls(true)
                            phoneBlocked -> openAppSettings()
                            else -> askPhone.launch(Manifest.permission.READ_PHONE_STATE)
                        }
                    },
                    divider = false,
                )
                if (callsOn) {
                    NavRow(stringResource(R.string.you_phone_call_buzz), callStyle.label, onClick = { picking = "callBuzz" })
                    NavRow(stringResource(R.string.you_phone_call_length), callLength.label, onClick = { picking = "callLength" })
                }
            }
        }
        if (phoneBlocked && !callPermission) item {
            Column(verticalArrangement = Arrangement.spacedBy(Space.S8)) {
                HaloNote(stringResource(R.string.you_phone_calls_blocked), color = MetricColors.warn)
                HaloButton(stringResource(R.string.you_phone_open_settings), openAppSettings, Modifier.fillMaxWidth())
            }
        }
        callPreviewError?.let { msg -> item { HaloNote(msg, color = MetricColors.warn) } }
        item {
            // Do Not Disturb is said once, here, for calls and notifications both.
            PageGroup(heading = stringResource(R.string.you_phone_group_notifications),
                footnote = stringResource(R.string.you_phone_note)) {
                SwitchRow(
                    stringResource(R.string.you_phone_notifications), stringResource(R.string.you_phone_notifications_note),
                    notificationsOn,
                    { on ->
                        vm.setBuzzOnNotifications(on)
                        if (on && !notificationAccess) openNotificationAccess()
                    },
                    divider = false,
                )
                if (notificationsOn) {
                    NavRow(stringResource(R.string.you_phone_pattern), pattern.label, onClick = { picking = "pattern" })
                }
            }
        }
        if (notifications && !notificationAccess) item {
            Column(verticalArrangement = Arrangement.spacedBy(Space.S8)) {
                HaloNote(stringResource(R.string.you_phone_notifications_access), color = MetricColors.warn)
                HaloButton(stringResource(R.string.you_phone_open_settings), openNotificationAccess, Modifier.fillMaxWidth())
            }
        }
        previewError?.let { msg -> item { HaloNote(msg, color = MetricColors.warn) } }
        if (notificationsOn) item {
            PageGroup(heading = stringResource(R.string.you_phone_quiet)) {
                SwitchRow(
                    stringResource(R.string.you_phone_quiet),
                    // The summary is only needed here when it warns (the same start and end).
                    if (quiet.on && quiet.from == quiet.to) quietHoursSummary(quiet, callsOn) else null, quiet.on,
                    { on -> vm.updateQuietHours { it.copy(on = on) } },
                    divider = false,
                )
                if (quiet.on) {
                    NavRow(stringResource(R.string.you_phone_quiet_from), quietClock(quiet.from), onClick = { pickingFrom = true })
                    NavRow(stringResource(R.string.you_phone_quiet_to), quietClock(quiet.to), onClick = { pickingFrom = false })
                    if (callsOn) {
                        SwitchRow(
                            stringResource(R.string.you_phone_quiet_calls), stringResource(R.string.you_phone_quiet_calls_note),
                            quiet.calls,
                            { on -> vm.updateQuietHours { it.copy(calls = on) } },
                        )
                    }
                }
            }
        }
    }

    // A sheet's Save is what writes the choice (and plays it once, as picking did before); Cancel leaves it as it was.
    when (picking) {
        "callBuzz" -> SegmentSheet(
            stringResource(R.string.you_phone_call_buzz), CallBuzz.entries.map { it.label }, callStyle.ordinal,
            onSave = { vm.pickCallBuzz(CallBuzz.entries[it]); picking = null }, onDismiss = { picking = null },
        )
        "callLength" -> SegmentSheet(
            stringResource(R.string.you_phone_call_length), CallBuzzLength.entries.map { it.label }, callLength.ordinal,
            onSave = { vm.pickCallLength(CallBuzzLength.entries[it]); picking = null }, onDismiss = { picking = null },
        )
        "pattern" -> SegmentSheet(
            stringResource(R.string.you_phone_pattern), BuzzPattern.entries.map { it.label }, pattern.ordinal,
            onSave = { vm.pickPattern(BuzzPattern.entries[it]); picking = null }, onDismiss = { picking = null },
        )
    }

    pickingFrom?.let { from ->
        QuietTimeSheet(
            stringResource(if (from) R.string.you_phone_quiet_from_title else R.string.you_phone_quiet_until_title),
            if (from) quiet.from else quiet.to,
            note = quietHoursSummary(quiet, callsOn),
            onSave = { m ->
                pickingFrom = null
                vm.updateQuietHours { if (from) it.copy(from = m) else it.copy(to = m) }
            },
            onDismiss = { pickingFrom = null },
        )
    }
}

/** One end of the quiet hours on two wheels (HaloQuietHoursSheet board), then Cancel and Save. */
@Composable
private fun QuietTimeSheet(title: String, minutes: Int, note: String, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    var picked by remember { mutableIntStateOf(minutes) }
    HaloSheet(
        onDismiss, title,
        footer = {
            ButtonPair(
                stringResource(R.string.you_action_cancel), onDismiss, stringResource(R.string.you_action_save), { onSave(picked) },
            )
        },
    ) {
        TimeWheels(minutes, { picked = it })
        HaloNote(note)
    }
}
