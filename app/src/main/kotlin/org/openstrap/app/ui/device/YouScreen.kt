package org.openstrap.app.ui.device

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.openstrap.app.BuildConfig
import org.openstrap.app.device.ConnectionState
import org.openstrap.app.device.SyncState
import org.openstrap.app.ui.components.Panel
import org.openstrap.app.ui.components.SectionLabel
import org.openstrap.app.ui.components.SettingRow
import org.openstrap.app.ui.dashboard.ScreenList
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.app.ui.theme.PhosphorIcons

/**
 * The "You" tab, as in Ultrahuman, Strava and Peloton: who you are, the strap, then settings grouped by
 * what they touch. Strap controls (sync, alarms, firmware, forget) live one level down, on "Your strap".
 */
@Composable
fun YouScreen(
    vm: DeviceViewModel,
    contentPadding: PaddingValues,
    onEditProfile: () -> Unit,
    goals: org.openstrap.app.data.settings.Goals,
    onEditGoals: () -> Unit,
    onOpenStrap: () -> Unit,
    onExportCsv: () -> Unit,
    onExportHealthConnect: () -> Unit,
    /** Opens the glow design sandbox; debug builds only. */
    onOpenDesignLab: (() -> Unit)? = null,
    /** Whether Today uses the glow design; null hides the switch (release builds). */
    glowToday: Boolean? = null,
    onGlowToday: (Boolean) -> Unit = {},
) {
    val profile by vm.profile.collectAsState()
    val paired by vm.paired.collectAsState()
    val connection by vm.strap.connection.collectAsState()
    val sync by vm.strap.sync.collectAsState()
    val log by vm.strap.log.collectAsState()

    ScreenList(contentPadding) {
        item {
            Text("You", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
        }

        item {
            Panel(onClick = onEditProfile) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(52.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Icon(PhosphorIcons.User, null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.primary) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Your profile", style = MaterialTheme.typography.titleMedium)
                        Text(profile.summary(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(PhosphorIcons.CaretRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            Panel(onClick = onEditGoals) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(52.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Icon(PhosphorIcons.ChartLineUp, null, Modifier.size(26.dp), tint = MetricColors.steps) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Your goals", style = MaterialTheme.typography.titleMedium)
                        Text(goals.summary(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(PhosphorIcons.CaretRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item { Group("Device") }
        item { StrapCard(paired?.name, connection, sync, onOpenStrap) }

        item { Group("Your data") }
        item {
            Panel {
                Text(
                    "Health data stays on this phone. Nothing leaves it unless you export it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinkRow(PhosphorIcons.DownloadSimple, "Export CSV", "A zip of every synced record", onExportCsv)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                LinkRow(PhosphorIcons.Heartbeat, "Health Connect", "Copy your data to Android Health Connect", onExportHealthConnect)
            }
        }

        item { Group("Support") }
        item { DiagnosticsPanel(log) }

        if (glowToday != null) item {
            Panel {
                SettingRow("Glow design on Today", "Experimental (debug build)", onClick = { onGlowToday(!glowToday) }) {
                    androidx.compose.material3.Switch(glowToday, onGlowToday)
                }
            }
        }

        if (onOpenDesignLab != null) item {
            TextButton(onClick = onOpenDesignLab, modifier = Modifier.fillMaxWidth()) { Text("Design lab (debug build)") }
        }

        if (org.openstrap.app.data.SampleData.AVAILABLE) item {
            TextButton(onClick = vm::loadSampleData, modifier = Modifier.fillMaxWidth()) { Text("Load sample data (debug build)") }
        }

        item {
            Text(
                "OpenStrap ${BuildConfig.VERSION_NAME} · local-first, no cloud",
                Modifier.fillMaxWidth().padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Group(title: String) = SectionLabel(title, Modifier.padding(start = 4.dp, top = 8.dp))

/** The strap at a glance (Oura's "My devices" card): state, battery, last sync. Opens "Your strap". */
@Composable
private fun StrapCard(name: String?, connection: ConnectionState, sync: SyncState, onClick: () -> Unit) {
    Panel(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(PhosphorIcons.Watch, null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (name == null) {
                    Text("Pair your Helio Strap", style = MaterialTheme.typography.titleMedium)
                    Text("Scan, or sign in with Zepp to fetch the key", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(name, style = MaterialTheme.typography.titleMedium)
                    val connected = connection as? ConnectionState.Connected
                    val (status, color) = when (connection) {
                        is ConnectionState.Connected -> "Connected" to MetricColors.good
                        ConnectionState.Connecting, ConnectionState.Authenticating -> "Connecting…" to MaterialTheme.colorScheme.onSurfaceVariant
                        is ConnectionState.Failed -> "Connection failed" to MaterialTheme.colorScheme.error
                        else -> "Not connected" to MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).background(color, CircleShape))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            listOfNotNull(
                                status,
                                connected?.batteryPercent?.let { if (connected.charging) "Charging $it%" else "$it%" },
                                when (sync) {
                                    is SyncState.Running -> "Syncing…"
                                    is SyncState.Done -> "Synced ${clock(sync.atEpochSeconds)}"
                                    else -> null
                                },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Icon(PhosphorIcons.CaretRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LinkRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(14.dp))
        Box(Modifier.weight(1f)) {
            SettingRow(title, subtitle, onClick) {
                Icon(PhosphorIcons.CaretRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
