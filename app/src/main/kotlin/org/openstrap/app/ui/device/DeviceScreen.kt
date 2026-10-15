package org.openstrap.app.ui.device

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.openstrap.app.device.ConnectionState
import org.openstrap.app.device.SyncState
import org.openstrap.app.ui.components.EmptyHint
import org.openstrap.app.ui.components.MetricCard
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
) {
    val paired by vm.paired.collectAsState()
    val connection by vm.strap.connection.collectAsState()
    val sync by vm.strap.sync.collectAsState()
    val scan by vm.scan.collectAsState()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Device", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(vertical = 8.dp)) }

        val device = paired
        if (device == null) {
            item { PairCard(vm, scan, onRequestBluetooth) }
        } else {
            item {
                MetricCard(device.name, Icons.Default.Bluetooth, MaterialTheme.colorScheme.primary) {
                    Text(device.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(connectionLabel(connection), style = MaterialTheme.typography.titleMedium)
                    when (val s = sync) {
                        is SyncState.Running -> {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text(s.step, style = MaterialTheme.typography.bodySmall)
                        }
                        is SyncState.Done -> Text("Last sync ${clock(s.atEpochSeconds)}", style = MaterialTheme.typography.bodySmall)
                        is SyncState.Failed -> Text("Sync failed: ${s.reason}", color = MaterialTheme.colorScheme.error)
                        SyncState.Idle -> {}
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (connection) {
                            is ConnectionState.Connected -> {
                                Button(onClick = vm.strap::syncNow, enabled = sync !is SyncState.Running) {
                                    Text("Sync now")
                                }
                                OutlinedButton(onClick = vm.strap::disconnect) { Text("Disconnect") }
                            }
                            ConnectionState.Connecting, ConnectionState.Authenticating ->
                                OutlinedButton(onClick = vm.strap::disconnect) { Text("Cancel") }
                            else -> Button(onClick = { onRequestBluetooth { vm.strap.connect() } }) { Text("Connect") }
                        }
                    }
                    HorizontalDivider()
                    TextButton(onClick = { vm.forget() }) { Text("Forget this strap") }
                }
            }
        }

        item {
            MetricCard("Your data", Icons.Default.FileDownload, MetricColors.steps) {
                Text(
                    "Everything stays on this phone. Nothing is sent anywhere unless you export it.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onExportCsv) { Text("Export CSV") }
                    FilledTonalButton(onClick = onExportHealthConnect) { Text("Health Connect") }
                }
            }
        }
    }
}

@Composable
private fun PairCard(vm: DeviceViewModel, scan: ScanUiState, onRequestBluetooth: (then: () -> Unit) -> Unit) {
    var address by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("Amazfit Helio Strap") }
    var key by rememberSaveable { mutableStateOf("") }
    var showKey by rememberSaveable { mutableStateOf(false) }

    MetricCard("Pair your Helio Strap", Icons.Default.BluetoothSearching, MaterialTheme.colorScheme.primary) {
        Text(
            "Put the strap on its charger, then scan. You also need the strap's auth key " +
                "(from the one-time Zepp bind — see the README).",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (scan.scanning) {
                OutlinedButton(onClick = vm::stopScan) { Text("Stop scanning") }
            } else {
                Button(onClick = { onRequestBluetooth { vm.startScan() } }) { Text("Scan") }
            }
        }
        if (scan.scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
        scan.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (scan.scanning && scan.found.isEmpty()) EmptyHint("Looking for straps nearby…")
        scan.found.forEach { d ->
            Column(
                Modifier.fillMaxWidth().clickable { address = d.address; name = d.name }.padding(vertical = 6.dp),
            ) {
                Text(d.name, style = MaterialTheme.typography.titleSmall)
                Text("${d.address} · ${d.rssi} dBm", style = MaterialTheme.typography.bodySmall)
            }
        }
        OutlinedTextField(
            value = address, onValueChange = { address = it.uppercase() },
            label = { Text("Bluetooth address") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = key, onValueChange = { key = it },
            label = { Text("Auth key (32 hex characters)") }, singleLine = true,
            leadingIcon = { androidx.compose.material3.Icon(Icons.Default.Key, null) },
            trailingIcon = { TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Hide" else "Show") } },
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onRequestBluetooth { vm.pair(address, name, key) } },
            enabled = address.isNotBlank() && key.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Pair") }
    }
}

private fun connectionLabel(c: ConnectionState) = when (c) {
    ConnectionState.NotPaired -> "Not paired"
    ConnectionState.Disconnected -> "Disconnected"
    ConnectionState.Connecting -> "Connecting…"
    ConnectionState.Authenticating -> "Authenticating…"
    is ConnectionState.Connected -> buildString {
        append("Connected")
        c.batteryPercent?.let { append(" · $it% battery") }
        c.firmware?.let { append(" · fw $it") }
    }
    is ConnectionState.Failed -> "Failed: ${c.reason}"
}

private fun clock(epochSeconds: Long): String =
    Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM HH:mm"))
