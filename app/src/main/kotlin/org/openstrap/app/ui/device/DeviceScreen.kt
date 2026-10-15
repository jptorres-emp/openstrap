package org.openstrap.app.ui.device

import android.content.ClipData
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.AlertDialog
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun DeviceScreen(
    vm: DeviceViewModel,
    contentPadding: PaddingValues,
    onRequestBluetooth: (then: () -> Unit) -> Unit,
    onExportCsv: () -> Unit,
    onExportHealthConnect: () -> Unit,
    onFirmware: () -> Unit,
) {
    val paired by vm.paired.collectAsState()
    val connection by vm.strap.connection.collectAsState()
    val sync by vm.strap.sync.collectAsState()
    val scan by vm.scan.collectAsState()
    val log by vm.strap.log.collectAsState()
    var confirmForget by rememberSaveable { mutableStateOf(false) }

    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            icon = { Icon(Icons.Default.LinkOff, null) },
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
            Text("Device", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
        }

        val device = paired
        if (device == null) {
            item { PairPanel(vm, scan, onRequestBluetooth) }
        } else {
            item { StatusHero(device.name, device.address, connection, sync) }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (connection) {
                        is ConnectionState.Connected -> {
                            Button(
                                onClick = vm.strap::syncNow, enabled = sync !is SyncState.Running,
                                modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 14.dp),
                            ) { Text("Sync now") }
                            OutlinedButton(onClick = vm.strap::disconnect, Modifier.weight(1f), contentPadding = PaddingValues(vertical = 14.dp)) {
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
                    SettingRow("Firmware update", "Check for and install strap firmware", onClick = onFirmware) {
                        Icon(Icons.Default.SystemUpdate, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingRow("Forget this strap", "Removes the address and auth key from this phone", onClick = { confirmForget = true }) {
                        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        item {
            Panel {
                PanelHeader("Your data", Icons.Default.FileDownload, MetricColors.steps)
                Text(
                    "Health data stays on this phone. Nothing leaves it unless you export it.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onExportCsv, Modifier.weight(1f)) { Text("Export CSV") }
                    OutlinedButton(onClick = onExportHealthConnect, Modifier.weight(1f)) { Text("Health Connect") }
                }
            }
        }

        item { DiagnosticsPanel(log) }

        if (org.openstrap.app.data.SampleData.AVAILABLE) item {
            TextButton(onClick = vm::loadSampleData, modifier = Modifier.fillMaxWidth()) { Text("Load sample data (debug build)") }
        }
    }
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
                    if (charging) Icons.Default.Bolt else Icons.Default.Watch, null, Modifier.size(28.dp),
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
            connection is ConnectionState.Failed -> Text(connection.reason, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            sync is SyncState.Failed -> Text("Sync failed: ${sync.reason}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PairPanel(vm: DeviceViewModel, scan: ScanUiState, onRequestBluetooth: (then: () -> Unit) -> Unit) {
    var address by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("Amazfit Helio Strap") }
    var key by rememberSaveable { mutableStateOf("") }
    var showKey by rememberSaveable { mutableStateOf(false) }

    Panel {
        PanelHeader("Pair your Helio Strap", Icons.AutoMirrored.Filled.BluetoothSearching, MaterialTheme.colorScheme.primary)
        Text(
            "Put the strap on its charger, then scan. You'll also need its 32-character auth key from the one-time Zepp bind.",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (scan.scanning) {
            OutlinedButton(onClick = vm::stopScan, Modifier.fillMaxWidth()) { Text("Stop scanning") }
            LinearProgressIndicator(Modifier.fillMaxWidth().clip(CircleShape))
        } else {
            Button(onClick = { onRequestBluetooth { vm.startScan() } }, Modifier.fillMaxWidth()) { Text("Scan for straps") }
        }
        scan.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (scan.scanning && scan.found.isEmpty()) EmptyHint("Looking for straps nearby…")
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
                Icon(Icons.Default.Watch, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(d.name, style = MaterialTheme.typography.titleSmall)
                    Text(d.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${d.rssi} dBm", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            leadingIcon = { Icon(Icons.Default.Key, null) },
            trailingIcon = { TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Hide" else "Show") } },
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        )
        Button(
            onClick = { onRequestBluetooth { vm.pair(address, name, key) } },
            enabled = address.isNotBlank() && key.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 14.dp),
            colors = ButtonDefaults.buttonColors(),
        ) { Text("Pair") }
    }
}

/** Protocol log, so behaviour on real hardware can be shared when reporting issues. */
@Composable
private fun DiagnosticsPanel(lines: List<String>) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).clickable { expanded = !expanded }) {
                PanelHeader("Diagnostics", Icons.Default.Terminal, trailing = if (expanded) "Hide" else "${lines.size} lines")
            }
            if (expanded) IconButton(onClick = {
                context.getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("OpenStrap log", lines.joinToString("\n")))
            }) { Icon(Icons.Default.ContentCopy, "Copy log") }
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

private fun clock(epochSeconds: Long): String =
    Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM HH:mm"))
