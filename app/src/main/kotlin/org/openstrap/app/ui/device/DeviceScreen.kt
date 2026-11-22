@file:Suppress("MagicNumber") // the strap drawing and its card follow the boards' sizes

package org.openstrap.app.ui.device

import org.openstrap.app.ui.glow.HaloNote
import org.openstrap.app.ui.glow.NoteVariant
import org.openstrap.app.ui.glow.HaloProgress
import org.openstrap.app.ui.glow.CardTone
import org.openstrap.app.ui.glow.HaloBanner
import org.openstrap.app.ui.glow.HaloCard
import org.openstrap.app.ui.glow.HaloPageHeader
import org.openstrap.app.ui.glow.HaloPageList
import org.openstrap.app.ui.glow.PageVariant
import org.openstrap.app.ui.theme.Corner
import org.openstrap.app.ui.theme.Space
import org.openstrap.app.ui.theme.Greys
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.location.LocationManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.openstrap.app.R
import org.openstrap.app.device.BlePermissions
import org.openstrap.app.device.ConnectionState
import org.openstrap.app.device.SyncState
import org.openstrap.app.i18n.Strings
import org.openstrap.app.ui.alarms.AlarmsUiState
import org.openstrap.app.ui.alarms.alarmsSummary
import org.openstrap.app.ui.alarms.healthAlertsSummary
import org.openstrap.app.ui.alarms.phoneAlertsSummary
import org.openstrap.app.ui.dashboard.DATA_ON_STRAP
import org.openstrap.app.ui.dashboard.StrapView
import org.openstrap.app.ui.dashboard.agoText
import org.openstrap.app.ui.firmware.FirmwareViewModel
import org.openstrap.app.ui.glow.ButtonPair
import org.openstrap.app.ui.glow.HaloButton
import org.openstrap.app.ui.glow.HaloButtonVariant
import org.openstrap.app.ui.glow.HaloButtonSize
import org.openstrap.app.ui.glow.HaloIconButton
import org.openstrap.app.ui.glow.HaloIconVariant
import org.openstrap.app.ui.glow.BannerKind
import org.openstrap.app.ui.glow.DestructiveInk
import org.openstrap.app.ui.glow.GlowLayout
import org.openstrap.app.ui.glow.GlowType
import org.openstrap.app.ui.glow.ActionRow
import org.openstrap.app.ui.glow.HaloConfirmSheet
import org.openstrap.app.ui.glow.PageField
import org.openstrap.app.ui.glow.PageGroup
import org.openstrap.app.ui.glow.NavRow
import org.openstrap.app.ui.glow.HaloSheet
import org.openstrap.app.ui.glow.SwitchRow
import org.openstrap.app.ui.glow.StrapDrawing
import org.openstrap.app.ui.glow.draw.LightAngle
import org.openstrap.app.ui.theme.AppIcons
import org.openstrap.app.ui.theme.Ink
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.app.ui.zepp.ZeppSignInDialog
import org.openstrap.app.update.Releases
import org.openstrap.app.update.UpdateCopy
import org.openstrap.app.update.UpdateState

/**
 * The line under the strap's name on the Strap page: what it's doing and when it was last heard from ("Connected.
 * Synced 2 min ago", "Not connected, last seen 18 min ago", "Can't connect, last seen 18 min ago"). [lastSeen] and [now]
 * are epoch seconds.
 */
internal fun strapStateLine(
    connection: ConnectionState,
    sync: SyncState,
    lastSeen: Long?,
    now: Long,
    strings: Strings = Strings.app,
    /** The last full sync ([org.openstrap.app.device.SyncRecord.lastSuccess]), kept across restarts. */
    lastSync: Long? = null,
): String = when (connection) {
    is ConnectionState.Connected -> {
        val synced = lastSync ?: (sync as? SyncState.Done)?.atEpochSeconds
        when {
            connection.charging -> strings.get(R.string.you_strap_state_charging)
            sync is SyncState.Running -> strings.get(R.string.you_strap_state_syncing)
            sync is SyncState.Failed -> strings.get(R.string.you_strap_state_not_synced)
            synced != null -> strings.format(R.string.you_strap_state_synced, agoText(now - synced).lowercase())
            else -> strings.get(R.string.you_strap_state_connected)
        }
    }
    ConnectionState.BluetoothOff -> strings.get(R.string.you_strap_state_bluetooth_off)
    ConnectionState.Connecting, ConnectionState.Authenticating -> strings.get(R.string.you_strap_state_connecting)
    is ConnectionState.Failed -> when {
        connection.keyRejected -> strings.get(R.string.you_strap_state_key_rejected)
        lastSeen != null -> strings.format(R.string.you_strap_state_cant_connect_seen, agoText(now - lastSeen).lowercase())
        else -> strings.get(R.string.you_strap_state_cant_connect)
    }
    ConnectionState.NotPaired -> strings.get(R.string.you_strap_state_not_paired)
    ConnectionState.Disconnected ->
        lastSeen?.let { strings.format(R.string.you_strap_state_last_seen, agoText(now - it).lowercase()) }
            ?: strings.get(R.string.you_strap_state_not_connected)
}

/**
 * The Strap page (spec 3.4), opened from the header chip on any tab: the strap with its state and battery, Sync now and
 * Find strap, then its settings as rows with their values, each opening its page or switching in place, then
 * Disconnect, Restart and Forget. With no strap paired, the pairing steps.
 */
@Composable
fun DeviceScreen(
    vm: DeviceViewModel,
    contentPadding: PaddingValues,
    onRequestBluetooth: (then: () -> Unit) -> Unit,
    /** The strap's alarms and health settings as last read, for the rows' values. */
    alarms: AlarmsUiState,
    zeppSignedIn: Boolean,
    /** When the strap was last connected (epoch seconds), null when not seen this run. */
    lastSeen: Long?,
    onAlarms: () -> Unit,
    onPhoneAlerts: () -> Unit,
    onHealthAlerts: () -> Unit,
    onFirmware: () -> Unit,
    onZeppAccount: () -> Unit,
    onBack: () -> Unit,
    /** Debug: a previewed strap state, shown on the strap card only ([org.openstrap.app.ui.dashboard.StrapPreview]). */
    preview: StrapView? = null,
    /** The row whose page is open beside the list on a wide screen, by its key in [PageRowKeys], or null. */
    selected: String? = null,
    /** The app's own updates, for the banner and the App updates row (HaloStrap board); null leaves both out. */
    updates: UpdateState? = null,
    installedVersion: String = "",
    onUpdates: (() -> Unit)? = null,
) {
    BackHandler(onBack = onBack)
    val paired by vm.paired.collectAsStateWithLifecycle()
    val connection by vm.connection.collectAsStateWithLifecycle()
    val strapConnected = connection is ConnectionState.Connected
    val finding by vm.findingStrap.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(strapConnected) { if (strapConnected) vm.checkCanRestart() }
    val sync by vm.sync.collectAsStateWithLifecycle()
    val record by vm.syncRecord.collectAsStateWithLifecycle()
    val scan by vm.scan.collectAsStateWithLifecycle()
    val info by vm.info.collectAsStateWithLifecycle()
    val calls by vm.buzzOnCalls.collectAsStateWithLifecycle()
    val notifications by vm.buzzOnNotifications.collectAsStateWithLifecycle()
    val wearReminder by vm.wearReminder.collectAsStateWithLifecycle()
    var confirmForget by rememberSaveable { mutableStateOf(false) }
    var confirmDisconnect by rememberSaveable { mutableStateOf(false) }
    var confirmRestart by rememberSaveable { mutableStateOf(false) }
    val keepConnected = rememberKeepConnected(vm)
    val offerBackground by vm.offerBackground.collectAsStateWithLifecycle()
    LaunchedEffect(offerBackground, paired) {
        val address = paired?.address
        if (offerBackground && address != null) { vm.backgroundOffered(); keepConnected.associate(address) }
    }

    if (confirmDisconnect) {
        HaloConfirmSheet(
            stringResource(R.string.you_strap_disconnect_title),
            stringResource(
                if (sync is SyncState.Running) R.string.you_strap_disconnect_text_syncing else R.string.you_strap_disconnect_text,
            ),
            confirm = stringResource(R.string.you_strap_disconnect), onConfirm = { confirmDisconnect = false; vm.disconnect() },
            onDismiss = { confirmDisconnect = false },
        )
    }

    if (confirmRestart) {
        HaloConfirmSheet(
            stringResource(R.string.you_strap_restart_title),
            stringResource(if (sync is SyncState.Running) R.string.you_strap_restart_text_syncing else R.string.you_strap_restart_text),
            confirm = stringResource(R.string.you_strap_restart), onConfirm = { confirmRestart = false; vm.restartStrap() },
            onDismiss = { confirmRestart = false },
        )
    }

    if (confirmForget) {
        HaloConfirmSheet(
            stringResource(R.string.you_strap_forget_title),
            stringResource(R.string.you_strap_forget_text),
            confirm = stringResource(R.string.you_strap_forget), onConfirm = { confirmForget = false; vm.forget() },
            onDismiss = { confirmForget = false }, destructive = true,
        )
    }

    // What the strap holds is read from it; until then, or without it, the row just opens its page.
    val waiting = stringResource(if (strapConnected) R.string.you_strap_reading else R.string.you_strap_on_strap)
    HaloPageList(contentPadding, variant = PageVariant.Settings) {
        item { HaloPageHeader(stringResource(R.string.you_strap_title), onBack) }

        val device = paired
        if (device == null) {
            item {
                StrapCard(
                    stringResource(R.string.you_strap_no_strap), stringResource(R.string.you_strap_state_not_paired), NotPairedDot,
                    linked = true,
                )
            }
            item { PairPanel(vm, scan, onRequestBluetooth) }
            return@HaloPageList
        }
        val failed = connection as? ConnectionState.Failed
        val rejected = failed?.takeIf { it.keyRejected }
        val keyRejected = rejected != null
        val installedUpdate = updates?.let { Releases.newer(it.releases, installedVersion).firstOrNull() }
        // The app's own update is news here too (HaloStrap board, B1).
        if (installedUpdate != null && onUpdates != null) item {
            HaloBanner(
                BannerKind.Good, stringResource(R.string.you_updates_ready, installedUpdate.versionName),
                note = UpdateCopy.install(installedUpdate, updates.install, recording = false),
                action = stringResource(R.string.you_updates_view), onAction = onUpdates,
            )
        }
        item {
            StrapHero(
                device.name, preview?.connection ?: connection, preview?.sync ?: sync, preview?.lastSeen ?: lastSeen,
                (preview?.record ?: record).lastSuccess,
            ) {
                HeroActions(
                    connection, sync, canFind = info?.findDevice == true, finding = finding,
                    onFind = { if (finding) vm.stopFindingStrap() else vm.findStrap() }, onSync = vm::syncNow,
                    onDisconnect = vm::disconnect, onForget = vm::forget, onConnect = { onRequestBluetooth { vm.connect() } },
                    onBluetooth = { context -> onRequestBluetooth { turnOnBluetooth(context) } },
                )
            }
        }
        // What the card's button can't say: why the key was turned down, or how to bring a strap that is away back.
        if (rejected != null) item {
            Column(verticalArrangement = Arrangement.spacedBy(Space.S8)) {
                HaloNote(rejected.reason, variant = NoteVariant.Footnote)
                HaloNote(stringResource(R.string.you_strap_key_rejected_note), variant = NoteVariant.Footnote)
            }
        } else if (connection is ConnectionState.Disconnected) item {
            HaloNote(stringResource(R.string.you_strap_away_note), variant = NoteVariant.Footnote)
        }
        val connected = connection is ConnectionState.Connected
        val firmware = info?.firmware ?: (connection as? ConnectionState.Connected)?.firmware
        if (!keyRejected) item {
            PageGroup(
                heading = stringResource(R.string.you_strap_group_on_strap),
                footnote = if (connected) null else stringResource(R.string.you_strap_last_read),
            ) {
                NavRow(
                    stringResource(R.string.you_strap_row_alarms), alarmsSummary(alarms) ?: waiting, onClick = onAlarms, divider = false,
                    below = true, icon = AppIcons.Alarm, selected = selected == PageRowKeys.ALARMS,
                )
                NavRow(
                    stringResource(R.string.you_strap_row_phone_alerts), phoneAlertsSummary(calls, notifications), onClick = onPhoneAlerts,
                    below = true, icon = AppIcons.Vibrate, selected = selected == PageRowKeys.PHONE_ALERTS,
                )
                NavRow(
                    stringResource(R.string.you_strap_row_health_alerts), healthAlertsSummary(alarms) ?: waiting, onClick = onHealthAlerts,
                    below = true, icon = AppIcons.Heartbeat, selected = selected == PageRowKeys.HEALTH_ALERTS,
                )
                SwitchRow(
                    stringResource(R.string.you_strap_row_off_body), stringResource(R.string.you_strap_row_off_body_note),
                    wearReminder, vm::setWearReminder, icon = AppIcons.HandPalm,
                )
                KeepConnectedRow(vm, keepConnected, device.address)
            }
        }
        item {
            PageGroup(heading = stringResource(R.string.you_strap_group_software)) {
                val hasFirmware = FirmwareViewModel.UPDATES_ENABLED || firmware != null
                if (FirmwareViewModel.UPDATES_ENABLED) {
                    NavRow(
                        stringResource(R.string.you_strap_row_firmware),
                        firmware ?: stringResource(R.string.you_firmware_unknown),
                        onClick = onFirmware, below = true, divider = false, icon = AppIcons.Cpu,
                        selected = selected == PageRowKeys.FIRMWARE,
                    )
                } else if (firmware != null) {
                    NavRow(stringResource(R.string.you_strap_row_firmware), firmware, below = true, divider = false, icon = AppIcons.Cpu)
                }
                if (updates != null && onUpdates != null) {
                    val now = remember(updates) { System.currentTimeMillis() / 1000 }
                    NavRow(
                        stringResource(R.string.you_strap_row_app_updates), UpdateCopy.summary(updates, installedVersion, now),
                        onClick = onUpdates, below = true, divider = hasFirmware, icon = AppIcons.ArrowCircleUp,
                        selected = selected == PageRowKeys.UPDATES,
                    )
                }
                NavRow(
                    stringResource(R.string.you_strap_row_zepp),
                    stringResource(if (zeppSignedIn) R.string.you_strap_signed_in else R.string.you_strap_not_signed_in),
                    onClick = onZeppAccount, below = true, divider = hasFirmware || (updates != null && onUpdates != null),
                    icon = AppIcons.Key, selected = selected == PageRowKeys.ZEPP_ACCOUNT,
                )
            }
        }
        item {
            PageGroup(footnote = stringResource(R.string.you_strap_forget_note)) {
                if (connected) {
                    ActionRow(
                        stringResource(R.string.you_strap_disconnect), { confirmDisconnect = true }, divider = false,
                        icon = AppIcons.LinkBreak,
                    )
                }
                if (connected && info?.canRestart == true) {
                    ActionRow(
                        stringResource(R.string.you_strap_row_restart), { confirmRestart = true }, icon = AppIcons.ShutDown,
                    )
                }
                ActionRow(
                    stringResource(R.string.you_strap_row_forget), { confirmForget = true }, divider = connected,
                    icon = AppIcons.Trash, destructive = true,
                )
            }
        }
    }
}

/**
 * Asks Android to turn Bluetooth on, which shows its own dialog. Where that can't be asked (no permission yet, or a phone
 * that refuses), opens the Bluetooth settings.
 */
private fun turnOnBluetooth(context: android.content.Context) {
    runCatching { context.startActivity(android.content.Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
        .onFailure {
            runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)) }
        }
}

/** Starts Android's companion association prompt from the strap page; the result goes back to [CompanionLink]. */
private class KeepConnected(val associate: (address: String) -> Unit)

@Composable
private fun rememberKeepConnected(vm: DeviceViewModel): KeepConnected {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        vm.associationResult(result.resultCode)
    }
    return remember(vm, launcher) {
        KeepConnected { address ->
            val activity = context.findActivity() ?: return@KeepConnected
            vm.associate(activity, address, launch = { launcher.launch(IntentSenderRequest.Builder(it).build()) })
        }
    }
}

private tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * "Stay connected in background": On once a companion association is in place, or battery optimization is off.
 * Off, a tap asks for the association where Android supports it, otherwise (or once the user turned that down) the
 * battery-optimization prompt.
 */
@Composable
private fun KeepConnectedRow(vm: DeviceViewModel, keepConnected: KeepConnected, address: String) {
    val context = LocalContext.current
    val associated by vm.companionAssociated.collectAsStateWithLifecycle()
    val ignoringBattery by vm.ignoringBatteryOptimizations.collectAsStateWithLifecycle()
    val declined by vm.companionDeclined.collectAsStateWithLifecycle()
    val hidden by vm.companionOfferHidden.collectAsStateWithLifecycle()
    val title = stringResource(R.string.you_strap_row_background)
    val on = stringResource(R.string.you_value_on)
    val off = stringResource(R.string.you_value_off)
    val failed = stringResource(R.string.you_strap_battery_settings_failed)
    val icon = AppIcons.Bluetooth
    when {
        associated -> NavRow(title, on, icon = icon)
        vm.companionSupported && !declined && !hidden -> NavRow(title, off, onClick = { keepConnected.associate(address) }, icon = icon)
        !ignoringBattery -> NavRow(title, off, icon = icon, onClick = {
            runCatching {
                context.startActivity(
                    android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(android.net.Uri.parse("package:${context.packageName}")),
                )
            }.onFailure { vm.showMessage(failed) }
        })
        else -> NavRow(title, on, icon = icon)
    }
}

/** The state dots (HaloStrap boards): good, away, rejected, not paired. */
private val NotPairedDot = Ink.Tertiary

/**
 * The strap card (HaloStrap, HaloStrapAway, HaloStrapKeyRejected and HaloPair boards): the name, the state line behind
 * its 7 dp dot, the battery bar while it is known, and the strap at the right with its light, then [actions] along the
 * foot. A strap that isn't connected or isn't paired has the strap drawn at the end of a link from the phone instead
 * ([linked]): a dashed line when it is away, a plain one when it was never paired.
 */
@Composable
internal fun StrapCard(
    name: String,
    stateLine: String,
    dot: Color,
    modifier: Modifier = Modifier,
    battery: Int? = null,
    batteryOn: Boolean = false,
    led: Color = dot,
    lit: Boolean = false,
    syncing: Boolean = false,
    linked: Boolean = false,
    dashed: Boolean = false,
    detail: (@Composable ColumnScope.() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    HaloCard(modifier.fillMaxWidth(), tone = CardTone.lit(StrapGlow)) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.S12)) {
            val text: @Composable ColumnScope.() -> Unit = {
                Text(name, color = Color.White, style = GlowType.heroTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S8)) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
                    Text(stateLine, color = Ink.Secondary, style = GlowType.secondary)
                }
                if (battery != null) BatteryBar(battery, batteryOn, Modifier.padding(top = Space.S8))
            }
            if (linked) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.S8), content = text)
                Row(Modifier.fillMaxWidth().height(92.dp).padding(start = Space.S16), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.S8)) {
                    PhoneOutline()
                    LinkLine(dashed, Modifier.weight(1f))
                    StrapDrawing(led, lit, syncing, Modifier.padding(end = 0.dp).size(width = 74.dp, height = 92.dp).offset(x = 6.dp))
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S12)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.S8), content = text)
                    StrapDrawing(led, lit, syncing, Modifier.size(width = 74.dp, height = 92.dp).offset(x = 6.dp))
                }
            }
            detail?.invoke(this)
            if (actions != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.S8), content = actions)
        }
    }
}

/** The battery as a 150 by 8 bar and its percent (HaloStrap board): the level's colour while connected, a dim white when not. */
@Composable
private fun BatteryBar(percent: Int, on: Boolean, modifier: Modifier = Modifier) {
    val fill = when {
        !on -> Ink.Disabled
        percent <= BATTERY_LOW -> DestructiveInk
        percent <= BATTERY_WARN -> MetricColors.warn
        else -> MetricColors.good
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S8)) {
        Box(Modifier.size(width = 150.dp, height = 8.dp).clip(CircleShape).background(Ink.Edge)) {
            Box(Modifier.fillMaxWidth(percent.coerceIn(0, 100) / 100f).fillMaxHeight().background(fill))
        }
        Text("$percent%", color = if (on) Color.White else GlowType.noteColor, style = GlowType.body)
    }
}

private const val BATTERY_LOW = 10
private const val BATTERY_WARN = 20

/** The phone as a small outline at the start of the link (HaloStrapAway board: 30 by 52, a 7 corner, a speaker notch). */
@Composable
private fun PhoneOutline() {
    Canvas(Modifier.size(width = 30.dp, height = 52.dp)) {
        val k = size.width / 30f
        drawRoundRect(Ink.Strong, Offset(k, k), Size(28f * k, 50f * k), CornerRadius(7f * k), style = Stroke(1.4f * k))
        drawLine(Ink.Secondary, Offset(11f * k, 6f * k), Offset(19f * k, 6f * k), 1.4f * k, StrokeCap.Round)
    }
}

/** The line from the phone to the strap: short dashes while the strap is away, a plain hairline when it was never paired. */
@Composable
private fun LinkLine(dashed: Boolean, modifier: Modifier) {
    Canvas(modifier.height(2.dp)) {
        val y = 1.dp.toPx()
        if (dashed) {
            // Drawn dash by dash (a path effect would be a dotted line, which the rules keep for numbers).
            val dash = 4.dp.toPx()
            var x = 0f
            while (x < size.width) {
                drawLine(Ink.Disabled, Offset(x, y), Offset(minOf(x + dash, size.width), y), 1.dp.toPx())
                x += dash + 3.dp.toPx()
            }
        } else drawLine(Ink.Edge, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
    }
}

/**
 * The strap in its card: connected, a green light and the battery; away or failed, an amber one with the strap at the
 * end of a dashed link; the key turned down, a red one (HaloStrap, HaloStrapAway and HaloStrapKeyRejected boards).
 */
@Composable
internal fun StrapHero(
    name: String,
    connection: ConnectionState,
    sync: SyncState,
    lastSeen: Long?,
    lastSync: Long? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val connected = connection as? ConnectionState.Connected
    val failed = connection as? ConnectionState.Failed
    val dot = when {
        connected != null && sync is SyncState.Failed -> MetricColors.warn
        connected != null -> MetricColors.good
        failed?.keyRejected == true -> DestructiveInk
        connection == ConnectionState.NotPaired -> NotPairedDot
        else -> MetricColors.warn
    }
    // A clock that moves on, so "Synced 2 min ago" stays true while the page is open.
    val now by androidx.compose.runtime.produceState(System.currentTimeMillis() / 1000) {
        while (true) { kotlinx.coroutines.delay(30_000); value = System.currentTimeMillis() / 1000 }
    }
    StrapCard(
        name, strapStateLine(connection, sync, lastSeen, now, lastSync = lastSync), dot,
        battery = connected?.batteryPercent, batteryOn = true, lit = true, syncing = connected != null && sync is SyncState.Running,
        linked = connected == null, dashed = connected == null && failed?.keyRejected != true,
        detail = {
            if (sync is SyncState.Running) {
                HaloProgress(null)
                Text(sync.step, style = GlowType.secondary, color = Ink.Secondary)
            }
            when {
                failed != null && !failed.keyRejected -> {
                    Text(failed.reason, color = MetricColors.warn, style = GlowType.secondary)
                    Text(
                        stringResource(R.string.you_strap_retrying),
                        style = GlowType.secondary, color = Ink.Secondary,
                    )
                }
                sync is SyncState.Failed -> {
                    Text(
                        stringResource(R.string.you_strap_sync_stopped, sync.reason), color = MetricColors.warn,
                        style = GlowType.secondary,
                    )
                    Text(
                        stringResource(R.string.you_strap_sync_retry, DATA_ON_STRAP),
                        style = GlowType.secondary, color = Ink.Secondary,
                    )
                }
            }
        },
        led = dot, actions = actions,
    )
}

/** What the card's foot holds for each state of the connection. */
@Composable
@Suppress("LongParameterList")
private fun RowScope.HeroActions(
    connection: ConnectionState,
    sync: SyncState,
    canFind: Boolean,
    finding: Boolean,
    onFind: () -> Unit,
    onSync: () -> Unit,
    onDisconnect: () -> Unit,
    onForget: () -> Unit,
    onConnect: () -> Unit,
    onBluetooth: (android.content.Context) -> Unit,
) {
    when {
        connection is ConnectionState.Connected -> {
            val syncing = sync is SyncState.Running
            if (canFind) {
                HaloButton(
                    stringResource(if (finding) R.string.you_strap_find_stop else R.string.you_strap_find), onFind, Modifier.weight(1f),
                    icon = if (finding) AppIcons.StopFill else AppIcons.SearchArea, variant = HaloButtonVariant.Glass,
                    size = HaloButtonSize.Card,
                )
                // Sync now is the card's one white tap, a circle with just its icon.
                val label = stringResource(R.string.you_strap_sync_now)
                HaloIconButton(AppIcons.Refresh04, label, onSync, variant = HaloIconVariant.Primary, enabled = !syncing)
            } else {
                HaloButton(
                    stringResource(R.string.you_strap_sync_now), onSync, Modifier.weight(1f), variant = HaloButtonVariant.Primary,
                    icon = AppIcons.Refresh04, enabled = !syncing, size = HaloButtonSize.Card,
                )
            }
        }
        connection == ConnectionState.Connecting || connection == ConnectionState.Authenticating ->
            HaloButton(
                stringResource(R.string.you_action_cancel), onDisconnect, Modifier.weight(1f), variant = HaloButtonVariant.Glass,
                size = HaloButtonSize.Card,
            )
        connection == ConnectionState.BluetoothOff -> {
            val context = LocalContext.current
            HaloButton(
                stringResource(R.string.you_strap_turn_on_bluetooth), { onBluetooth(context) }, Modifier.weight(1f),
                variant = HaloButtonVariant.Primary, icon = AppIcons.Bluetooth, size = HaloButtonSize.Card,
            )
        }
        (connection as? ConnectionState.Failed)?.keyRejected == true ->
            HaloButton(
                stringResource(R.string.you_pair_again), onForget, Modifier.weight(1f), variant = HaloButtonVariant.Primary,
                size = HaloButtonSize.Card,
            )
        else -> HaloButton(
            stringResource(R.string.you_strap_connect), onConnect, Modifier.weight(1f), variant = HaloButtonVariant.Primary,
            size = HaloButtonSize.Card,
        )
    }
}

/**
 * The strap's card: the silver hero lit from below, so it doesn't read as any metric (Halo Strap and Pair boards,
 * `rgba(201,206,214)` from the bottom).
 */
internal val StrapGlow = org.openstrap.app.ui.glow.draw.heroStyle(org.openstrap.app.ui.glow.StrapSilver, LightAngle.Below)

/** The protocol log, for bug reports, opened from the You page; the whole log can be copied (HaloDiagnostics board). */
@Composable
fun DiagnosticsScreen(lines: List<String>, contentPadding: PaddingValues, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val label = stringResource(R.string.you_diagnostics_clip_label)
    val copy = {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, lines.joinToString("\n")))
    }
    HaloPageList(contentPadding, variant = PageVariant.Settings) {
        // Copy is an icon in the header so the title keeps its room at every font size; the labelled button is under the log.
        item {
            HaloPageHeader(stringResource(R.string.you_diagnostics_title), onBack) {
                HaloIconButton(AppIcons.Copy, stringResource(R.string.you_diagnostics_copy), copy)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Space.S8)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = Space.S4), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.you_diagnostics_log), color = GlowType.noteColor, style = GlowType.secondary)
                    Text(
                        pluralStringResource(R.plurals.you_diagnostics_lines, lines.size, lines.size),
                        color = GlowType.noteColor, style = GlowType.secondary,
                    )
                }
                // The log is a black block in a panel, in monospace at 11.5 (the board's).
                HaloCard {
                    Box(
                        Modifier.fillMaxWidth().heightIn(max = 520.dp).clip(GlowLayout.TileShape).background(Color.Black)
                            .verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).padding(Space.S12),
                    ) {
                        Text(
                            if (lines.isEmpty()) stringResource(R.string.you_diagnostics_empty) else lines.joinToString("\n"),
                            style = GlowType.caption.copy(fontFamily = FontFamily.Monospace),
                            color = Ink.Secondary, textAlign = TextAlign.Start,
                        )
                    }
                }
                HaloButton(
                    stringResource(R.string.you_diagnostics_copy), copy, Modifier.fillMaxWidth().minimumInteractiveComponentSize(),
                    icon = AppIcons.Copy,
                )
                HaloNote(stringResource(R.string.you_diagnostics_note), variant = NoteVariant.Footnote)
            }
        }
    }
}

@Composable
private fun PairPanel(vm: DeviceViewModel, scan: ScanUiState, onRequestBluetooth: (then: () -> Unit) -> Unit) {
    var signIn by rememberSaveable { mutableStateOf(false) }
    var manual by rememberSaveable { mutableStateOf(false) }

    if (signIn) {
        ZeppSignInDialog(
            preferredApiBase = null,
            confirmLabel = stringResource(R.string.you_pair_confirm),
            requireKey = true,
            onDismiss = { signIn = false },
            onConfirm = { listing, strap ->
                signIn = false
                onRequestBluetooth { vm.signInAndPair(listing, strap) }
            },
        )
    }

    // The pairing steps in one plain panel with the one white tap, then the way in with a key you already have (HaloPair board).
    Column(verticalArrangement = Arrangement.spacedBy(Space.S24)) {
        HaloCard(verticalArrangement = Arrangement.spacedBy(Space.S20)) {
            Text(stringResource(R.string.you_pair_title), color = Color.White, style = GlowType.headerDate)
            StepText(stringResource(R.string.you_pair_intro))
            PairStep(1, stringResource(R.string.you_pair_step_link)) {
                StepText(stringResource(R.string.you_pair_step_link_text))
                StepText(stringResource(R.string.you_pair_step_link_note), muted = true)
            }
            PairStep(2, stringResource(R.string.you_pair_step_ready)) {
                StepText(stringResource(R.string.you_pair_step_ready_text))
                StepText(stringResource(R.string.you_pair_step_ready_note), muted = true)
            }
            PairStep(3, stringResource(R.string.you_pair_step_sign_in)) {
                StepText(stringResource(R.string.you_pair_step_sign_in_text))
            }
            HaloButton(
                stringResource(R.string.you_pair_sign_in), { signIn = true }, Modifier.fillMaxWidth(), variant = HaloButtonVariant.Primary,
            )
        }
        PageGroup {
            NavRow(
                stringResource(R.string.you_pair_have_key), stringResource(R.string.you_pair_enter_key), onClick = { manual = true },
                below = true, divider = false, icon = AppIcons.Key,
            )
        }
    }

    if (manual) ManualPairSheet(vm, scan, onRequestBluetooth, onDismiss = { vm.stopScan(); manual = false })
}

/** One numbered pairing step (HaloPair board): its number in a 28 ring, its [title] at 15 semibold, and what to do under it. */
@Composable
internal fun PairStep(number: Int, title: String, content: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.S12)) {
        Box(
            Modifier.size(28.dp).border(1.5.dp, Greys.Line, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("$number", style = GlowType.secondary.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                color = Color.White)
        }
        Column(Modifier.weight(1f).padding(top = Space.S4), verticalArrangement = Arrangement.spacedBy(Space.S4)) {
            Text(title, style = GlowType.cardTitle, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            content()
        }
    }
}

/** A step's text at 14 in #C9CED6, or its note at 13 in #6E7480 when [muted]. */
@Composable
private fun StepText(text: String, muted: Boolean = false) {
    if (muted) Text(text, style = GlowType.secondary, color = GlowType.footnoteColor)
    else Text(text, style = GlowType.body, color = Ink.Strong)
}

/** For keys read on a computer or from an earlier pairing: scan for the address, paste the key, pair. */
@Composable
private fun ManualPairSheet(
    vm: DeviceViewModel,
    scan: ScanUiState,
    onRequestBluetooth: (then: () -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var address by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf(DeviceViewModel.DEFAULT_NAME) }
    var key by rememberSaveable { mutableStateOf("") }
    // Android 11 only reports BLE scan results while Location is on. Read again on resume, after the settings page.
    val context = LocalContext.current
    var locationOff by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        locationOff = BlePermissions.scanNeedsLocationOn &&
            context.getSystemService(LocationManager::class.java)?.isLocationEnabled == false
        onPauseOrDispose { }
    }
    var showKey by rememberSaveable { mutableStateOf(false) }

    // The sheet of the HaloManualPair board: scan, the straps found as tiles, the two fields, a note, Cancel and Pair.
    HaloSheet(
        onDismiss, stringResource(R.string.you_pair_manual_title),
        footer = {
            ButtonPair(
                stringResource(R.string.you_action_cancel),
                onDismiss,
                stringResource(R.string.you_pair_pair),
                {
                        // A bad key or address only shows a message; a valid one saves the strap and the screen moves to its status.
                        if (DeviceViewModel.normalizeAuthKey(key) != null && DeviceViewModel.isAddress(address)) onDismiss()
                        onRequestBluetooth { vm.pair(address, name, key) }
                    },
                rightEnabled = address.isNotBlank() && key.isNotBlank(),
            )
        },
    ) {
        if (scan.scanning) {
            HaloButton(stringResource(R.string.you_pair_scan_stop), vm::stopScan, Modifier.fillMaxWidth(), icon = AppIcons.StopFill,
                size = HaloButtonSize.Card)
            HaloProgress(null)
        } else {
            HaloButton(
                stringResource(R.string.you_pair_scan), { onRequestBluetooth { vm.startScan() } }, Modifier.fillMaxWidth(),
                icon = AppIcons.Bluetooth, size = HaloButtonSize.Card,
            )
        }
        if (locationOff) {
            HaloNote(stringResource(R.string.you_pair_scan_location_off), color = MetricColors.warn)
            HaloButton(
                stringResource(R.string.you_pair_scan_location_on),
                { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
                Modifier.fillMaxWidth(), icon = AppIcons.Location01,
            )
        }
        scan.error?.let { HaloNote(stringResource(R.string.you_pair_scan_failed, it), color = MetricColors.warn) }
        if (scan.scanning && scan.found.isEmpty()) HaloNote(stringResource(R.string.you_pair_scan_looking))
        if (scan.found.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(Space.S8)) {
            scan.found.forEach { d ->
                val selected = d.address == address
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(Corner.Inner)
                        .background(if (selected) Greys.Picked else Greys.Control)
                        .clickable { address = d.address; name = d.name }
                        .semantics { this.selected = selected }
                        .padding(horizontal = Space.S16, vertical = Space.S12),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S12),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.S2)) {
                        Text(d.name, color = Color.White, style = GlowType.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(d.address, color = GlowType.noteColor, style = GlowType.secondary.copy(fontFamily = FontFamily.Monospace))
                    }
                    Text(
                        if (selected) stringResource(R.string.you_pair_chosen) else stringResource(R.string.you_pair_rssi, d.rssi),
                        color = if (selected) Color.White else GlowType.noteColor, style = GlowType.secondary,
                    )
                }
            }
        }
        PageField(
            address, { address = it.uppercase() }, stringResource(R.string.you_pair_address), placeholder = "C8:0F:10:00:00:00",
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
        )
        PageField(
            key, { key = it }, stringResource(R.string.you_pair_key), placeholder = stringResource(R.string.you_pair_key_placeholder),
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailing = {
                HaloButton(
                    stringResource(if (showKey) R.string.you_action_hide else R.string.you_action_show), { showKey = !showKey },
                    variant = HaloButtonVariant.Text,
                )
            },
        )
        HaloNote(stringResource(R.string.you_pair_manual_note))
    }
}

