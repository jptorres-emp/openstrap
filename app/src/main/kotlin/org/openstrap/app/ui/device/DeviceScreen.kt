package org.openstrap.app.ui.device

import androidx.compose.foundation.BorderStroke
import android.content.ClipData
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.openstrap.app.device.ConnectionState
import org.openstrap.app.device.SyncState
import org.openstrap.app.ui.components.EmptyHint
import org.openstrap.app.ui.components.Panel
import org.openstrap.app.ui.components.PanelHeader
import org.openstrap.app.ui.components.ProgressRing
import org.openstrap.app.ui.components.SectionLabel
import org.openstrap.app.ui.components.SettingRow
import org.openstrap.app.ui.dashboard.ScreenList
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.app.ui.theme.PhosphorIcons
import org.openstrap.app.ui.zepp.ComputerGuideButton
import org.openstrap.app.ui.zepp.OptionButton
import org.openstrap.app.ui.zepp.ZeppSignInDialog
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun DeviceScreen(
    vm: DeviceViewModel,
    contentPadding: PaddingValues,
    onRequestBluetooth: (then: () -> Unit) -> Unit,
    onFirmware: () -> Unit,
    onAlarms: () -> Unit = {},
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val paired by vm.paired.collectAsState()
    val connection by vm.strap.connection.collectAsState()
    val sync by vm.strap.sync.collectAsState()
    val scan by vm.scan.collectAsState()
    var confirmForget by rememberSaveable { mutableStateOf(false) }
    var confirmDisconnect by rememberSaveable { mutableStateOf(false) }
    val keepConnected = rememberKeepConnected(vm)
    val offerBackground by vm.offerBackground.collectAsState()
    LaunchedEffect(offerBackground, paired) {
        val address = paired?.address
        if (offerBackground && address != null) { vm.backgroundOffered(); keepConnected.associate(address) }
    }

    if (confirmDisconnect) {
        org.openstrap.app.ui.components.GlassAlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            icon = { Icon(PhosphorIcons.LinkBreak, null) },
            title = { Text("Disconnect the strap?") },
            text = {
                Text(
                    (if (sync is SyncState.Running) "The sync in progress will stop. " else "") +
                        "The strap keeps recording on its own, but OpenStrap won't sync or show live heart rate " +
                        "until you connect again.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { confirmDisconnect = false; vm.strap.disconnect() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Disconnect") }
            },
            dismissButton = { TextButton(onClick = { confirmDisconnect = false }) { Text("Cancel") } },
        )
    }

    if (confirmForget) {
        org.openstrap.app.ui.components.GlassAlertDialog(
            onDismissRequest = { confirmForget = false },
            icon = { Icon(PhosphorIcons.LinkBreak, null) },
            title = { Text("Forget this strap?") },
            text = {
                Text(
                    "OpenStrap will disconnect and delete the strap's address and auth key from this phone. " +
                        "Your synced health data stays. To pair again you'll need the auth key.\n\n" +
                        "This doesn't unbind the strap from your Zepp account.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { confirmForget = false; vm.forget() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Forget") }
            },
            dismissButton = { TextButton(onClick = { confirmForget = false }) { Text("Cancel") } },
        )
    }

    ScreenList(contentPadding) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(PhosphorIcons.ArrowLeft, "Back") }
                Text("Your strap", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            }
        }

        val device = paired
        if (device == null) {
            item { PairPanel(vm, scan, onRequestBluetooth) }
        } else {
            item { StatusHero(device.name, device.address, connection, sync) }
            val failed = connection as? ConnectionState.Failed
            if (failed?.keyRejected == true) item { KeyRejectedPanel(failed.reason, onPairAgain = vm::forget) }
            else item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (connection) {
                        is ConnectionState.Connected -> {
                            Button(
                                onClick = vm.strap::syncNow, enabled = sync !is SyncState.Running,
                                modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 14.dp),
                            ) { Text("Sync now") }
                            // Red, like Forget: it cuts the strap off, so it shouldn't look like Sync now's twin.
                            OutlinedButton(
                                onClick = { confirmDisconnect = true }, Modifier.weight(1f), contentPadding = PaddingValues(vertical = 14.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                            ) {
                                Text("Disconnect")
                            }
                        }
                        ConnectionState.Connecting, ConnectionState.Authenticating ->
                            OutlinedButton(onClick = vm.strap::disconnect, Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 14.dp)) {
                                Text("Cancel")
                            }
                        else -> Button(
                            onClick = { onRequestBluetooth { vm.strap.connect() } },
                            modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 14.dp),
                        ) { Text("Connect") }
                    }
                }
            }
            item {
                Panel {
                    SettingRow("Alarms & alerts", "Wake-up alarms and heart-rate alerts on the strap", onClick = onAlarms) {
                        Icon(PhosphorIcons.CaretRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingRow("Firmware update", "Check for and install strap firmware", onClick = onFirmware) {
                        Icon(PhosphorIcons.ArrowCircleUp, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    KeepConnectedRow(vm, keepConnected, device.address)
                    SettingRow("Forget this strap", "Removes the address and auth key from this phone", onClick = { confirmForget = true }) {
                        Icon(PhosphorIcons.CaretRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** Starts Android's companion association prompt from the strap page; the result goes back to [CompanionLink]. */
private class KeepConnected(val associate: (address: String) -> Unit)

@Composable
private fun rememberKeepConnected(vm: DeviceViewModel): KeepConnected {
    val context = LocalContext.current
    val companion = vm.companion
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (!companion.onResult(result.resultCode)) vm.showMessage("Not linked. You can turn off battery optimization for OpenStrap instead.")
    }
    return remember(companion, launcher) {
        KeepConnected { address ->
            val activity = context.findActivity() ?: return@KeepConnected
            companion.associate(
                activity, address,
                launch = { launcher.launch(IntentSenderRequest.Builder(it).build()) },
                onError = vm::showMessage,
            )
        }
    }
}

private tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * "Keep strap connected in the background": a companion association where Android supports it, otherwise (or
 * once the user turned that down) the battery-optimization prompt. Hidden once either is in place.
 */
@Composable
private fun KeepConnectedRow(vm: DeviceViewModel, keepConnected: KeepConnected, address: String) {
    val context = LocalContext.current
    val associated by vm.companion.associated.collectAsState()
    val ignoringBattery by vm.companion.ignoringBatteryOptimizations.collectAsState()
    val declined by vm.companionDeclined.collectAsState()
    when {
        associated -> return
        vm.companion.supported && !declined -> SettingRow(
            "Keep strap connected in the background",
            "Link the strap with Android so it can reconnect without opening OpenStrap",
            onClick = { keepConnected.associate(address) },
        ) { Icon(PhosphorIcons.CaretRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        !ignoringBattery -> SettingRow(
            "Keep strap connected in the background",
            "Turn off battery optimization for OpenStrap so Android doesn't stop it",
            onClick = {
                runCatching {
                    context.startActivity(
                        android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            .setData(android.net.Uri.parse("package:${context.packageName}")),
                    )
                }.onFailure { vm.showMessage("Couldn't open battery settings on this phone.") }
            },
        ) { Icon(PhosphorIcons.CaretRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        else -> return
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** WHOOP/Ultrahuman-style status block: connection label, last sync, battery ring. */
@Composable
private fun StatusHero(name: String, address: String, connection: ConnectionState, sync: SyncState) {
    Panel {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                val (label, color) = when (connection) {
                    is ConnectionState.Connected -> "Connected to" to MetricColors.good
                    ConnectionState.Connecting -> "Connecting to" to MaterialTheme.colorScheme.onSurfaceVariant
                    ConnectionState.Authenticating -> "Authenticating" to MaterialTheme.colorScheme.onSurfaceVariant
                    is ConnectionState.Failed -> "Connection failed" to MaterialTheme.colorScheme.error
                    else -> "Not connected" to MaterialTheme.colorScheme.onSurfaceVariant
                }
                SectionLabel(label, color = color)
                Text(name, style = MaterialTheme.typography.titleLarge)
                Text(address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                SectionLabel("Last sync")
                Text(
                    when (sync) {
                        is SyncState.Done -> clock(sync.atEpochSeconds)
                        is SyncState.Running -> "Syncing…"
                        else -> "—"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        val connected = connection as? ConnectionState.Connected
        val battery = connected?.batteryPercent
        val charging = connected?.charging == true
        val ringColor = MetricColors.battery(battery)
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            ProgressRing((battery ?: 0) / 100f, ringColor, size = 168.dp, stroke = 10.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    if (charging) PhosphorIcons.Lightning else PhosphorIcons.Watch, null, Modifier.size(28.dp),
                    tint = if (charging) ringColor else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(battery?.let { "$it%" } ?: "—", style = MaterialTheme.typography.displaySmall)
                SectionLabel(if (charging) "Charging" else "Battery", color = if (charging) ringColor else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (sync is SyncState.Running) {
            LinearProgressIndicator(Modifier.fillMaxWidth().clip(CircleShape))
            Text(sync.step, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        (connection as? ConnectionState.Connected)?.firmware?.let {
            Text("Firmware $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when {
            connection is ConnectionState.Failed && !connection.keyRejected -> {
                Text(connection.reason, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "OpenStrap keeps retrying. Keep the strap on its charger near the phone, and make sure the Zepp app " +
                        "isn't running: the strap talks to one app at a time.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            sync is SyncState.Failed -> Text("Sync failed: ${sync.reason}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
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
            confirmLabel = "Pair this strap",
            requireKey = true,
            onDismiss = { signIn = false },
            onConfirm = { listing, strap ->
                signIn = false
                onRequestBluetooth { vm.signInAndPair(listing, strap) }
            },
        )
    }

    Panel {
        PanelHeader("Pair your Helio Strap", PhosphorIcons.Bluetooth, MaterialTheme.colorScheme.primary)
        Text(
            "OpenStrap needs the strap's auth key, which Zepp creates when you link the strap. You only do this once.",
            style = MaterialTheme.typography.bodyMedium,
        )
        PairStep(1, "Link the strap in the Zepp app") {
            StepText("Install Zepp, sign in, and link your Helio Strap.")
            StepText(
                "OpenStrap signs in with email only. If you signed up with Google or Apple, add an email and password " +
                    "to your account in the Zepp app's account settings.",
                muted = true,
            )
        }
        PairStep(2, "Get the strap ready") {
            StepText("Put the strap on its charger, close to this phone.")
            StepText("Force-stop or uninstall Zepp so it lets go of the strap. Don't unlink the strap in Zepp: that wipes its key.", muted = true)
        }
        PairStep(3, "Sign in and pair") {
            StepText("Sign in with the same Zepp email. OpenStrap finds your strap and its key, then pairs.")
        }
        // Full panel width, not indented under step 3, so they line up with the panel's edges.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { signIn = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
            ) { Text("Sign in with Zepp") }
            ComputerGuideButton()
            OptionButton("Have the key already?", "Enter it yourself") { manual = true }
        }
    }

    if (manual) ManualPairDialog(vm, scan, onRequestBluetooth, onDismiss = { vm.stopScan(); manual = false })
}

@Composable
private fun PairStep(number: Int, title: String, content: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text("$number", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Column(Modifier.weight(1f).padding(top = 3.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

@Composable
private fun StepText(text: String, muted: Boolean = false) {
    Text(
        text,
        style = if (muted) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
        color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
    )
}

/** For keys read on a computer or from an earlier pairing: scan for the address, paste the key, pair. */
@Composable
private fun ManualPairDialog(
    vm: DeviceViewModel,
    scan: ScanUiState,
    onRequestBluetooth: (then: () -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var address by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf(DeviceViewModel.DEFAULT_NAME) }
    var key by rememberSaveable { mutableStateOf("") }
    var showKey by rememberSaveable { mutableStateOf(false) }

    org.openstrap.app.ui.components.GlassAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(PhosphorIcons.Key, null) },
        title = { Text("Enter the key yourself") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Scan with the strap on its charger and tap it to fill in the address, then paste the 32-character auth key.")
                if (scan.scanning) {
                    OutlinedButton(onClick = vm::stopScan, Modifier.fillMaxWidth()) { Text("Stop scanning") }
                    LinearProgressIndicator(Modifier.fillMaxWidth().clip(CircleShape))
                } else {
                    OutlinedButton(onClick = { onRequestBluetooth { vm.startScan() } }, Modifier.fillMaxWidth()) { Text("Scan for straps") }
                }
                scan.error?.let {
                    Text("Scanning stopped: $it. Check that Bluetooth is on and try again.", color = MaterialTheme.colorScheme.error)
                }
                if (scan.scanning && scan.found.isEmpty()) {
                    Text("Looking for straps nearby… Keep the strap on its charger.", style = MaterialTheme.typography.bodySmall)
                }
                scan.found.forEach { d ->
                    val selected = d.address == address
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { address = d.address; name = d.name }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(PhosphorIcons.Watch, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(d.name, style = MaterialTheme.typography.titleSmall)
                            Text(d.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (selected) Icon(PhosphorIcons.CheckCircle, "Selected", tint = MaterialTheme.colorScheme.primary)
                        else Text("${d.rssi} dBm", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                OutlinedTextField(
                    value = address, onValueChange = { address = it.uppercase() },
                    label = { Text("Bluetooth address") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                )
                OutlinedTextField(
                    value = key, onValueChange = { key = it },
                    label = { Text("Auth key") }, singleLine = true,
                    trailingIcon = { TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Hide" else "Show") } },
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // A bad key or address only shows a message; a valid one saves the strap and the screen moves to its status.
                    if (DeviceViewModel.normalizeAuthKey(key) != null && DeviceViewModel.isAddress(address)) onDismiss()
                    onRequestBluetooth { vm.pair(address, name, key) }
                },
                enabled = address.isNotBlank() && key.isNotBlank(),
            ) { Text("Pair") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Shown under the status when the strap turned the key down: retrying can't help, so offer to start pairing over. */
@Composable
private fun KeyRejectedPanel(reason: String, onPairAgain: () -> Unit) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(PhosphorIcons.WarningCircle, null, tint = MetricColors.bad)
            Text("Pairing didn't work", style = MaterialTheme.typography.titleMedium)
        }
        Text(reason, style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onPairAgain, Modifier.fillMaxWidth()) { Text("Start pairing again") }
        ComputerGuideButton()
    }
}

/** Protocol log, so behaviour on real hardware can be shared when reporting issues. */
@Composable
internal fun DiagnosticsPanel(lines: List<String>, asRow: Boolean = false) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).clickable { expanded = !expanded }) {
                // On You it reads like the rows around it: badge, title, what's inside.
                if (asRow) Row(verticalAlignment = Alignment.CenterVertically) {
                    YouIcon(PhosphorIcons.TerminalWindow)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Diagnostics", style = MaterialTheme.typography.titleMedium)
                        Text(if (expanded) "Tap to hide" else "${lines.size} lines", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                else PanelHeader("Diagnostics", PhosphorIcons.TerminalWindow, trailing = if (expanded) "Hide" else "${lines.size} lines")
            }
            if (expanded) IconButton(onClick = {
                context.getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("OpenStrap log", lines.joinToString("\n")))
            }) { Icon(PhosphorIcons.Copy, "Copy log") }
        }
        if (expanded) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp),
            ) {
                Text(
                    if (lines.isEmpty()) "No events yet." else lines.joinToString("\n"),
                    fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Start,
                )
            }
        }
    }
}

internal fun clock(epochSeconds: Long): String =
    Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM HH:mm"))
