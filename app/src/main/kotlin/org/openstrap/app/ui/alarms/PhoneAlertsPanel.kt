package org.openstrap.app.ui.alarms

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch
import org.openstrap.app.data.settings.BuzzPattern
import org.openstrap.app.data.settings.SettingsStore
import org.openstrap.app.device.PhoneAlerts
import org.openstrap.app.device.StrapNotificationListener
import org.openstrap.app.ui.components.Panel
import org.openstrap.app.ui.components.PanelHeader
import org.openstrap.app.ui.components.SegmentedTabs
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.app.ui.theme.PhosphorIcons

/** Switches for buzzing the strap on calls and notifications. These are phone settings, so they work offline too. */
@Composable
fun PhoneAlertsPanel(alerts: PhoneAlerts, settings: SettingsStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val calls by settings.buzzOnCalls.collectAsState(false)
    val notifications by settings.buzzOnNotifications.collectAsState(false)
    val callPermission by alerts.callPermission.collectAsState()
    val notificationAccess by alerts.notificationAccess.collectAsState()
    val pattern by settings.notificationPattern.collectAsState(BuzzPattern.TWO_SHORT)
    var previewError by remember { mutableStateOf<String?>(null) }

    // The user may grant access in system settings and come back.
    LifecycleResumeEffect(Unit) { alerts.refresh(); onPauseOrDispose { } }

    val askPhone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        alerts.refresh()
        if (granted) scope.launch { settings.setBuzzOnCalls(true) }
    }
    val openNotificationAccess = {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, ComponentName(context, StrapNotificationListener::class.java).flattenToString())
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        Unit
    }

    Panel {
        PanelHeader("Phone alerts", PhosphorIcons.BellRinging, MetricColors.charge)
        SwitchRow("Incoming calls", "The strap buzzes while your phone rings.", calls && callPermission) { on ->
            when {
                !on -> scope.launch { settings.setBuzzOnCalls(false) }
                callPermission -> scope.launch { settings.setBuzzOnCalls(true) }
                else -> askPhone.launch(Manifest.permission.READ_PHONE_STATE)
            }
        }
        SwitchRow("Notifications", "The strap buzzes for notifications that would make a sound.", notifications && notificationAccess) { on ->
            scope.launch { settings.setBuzzOnNotifications(on) }
            if (on && !notificationAccess) openNotificationAccess()
        }
        if (notifications && !notificationAccess) {
            Text(
                "Allow notification access for OpenStrap to turn this on.",
                style = MaterialTheme.typography.bodySmall, color = MetricColors.warn,
            )
            TextButton(onClick = openNotificationAccess) { Text("Open settings") }
        }
        if (notifications && notificationAccess) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Buzz pattern", style = MaterialTheme.typography.bodyLarge)
                // Picking a pattern plays it once, so it can be felt before the next notification.
                SegmentedTabs(BuzzPattern.entries.map { it.label }, pattern.ordinal, { i ->
                    val p = BuzzPattern.entries[i]
                    scope.launch {
                        settings.setNotificationPattern(p)
                        previewError = runCatching { alerts.preview(p) }.exceptionOrNull()?.let { it.message ?: "Couldn't buzz the strap" }
                    }
                })
                previewError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MetricColors.warn) }
            }
        }
        Text(
            "Silent apps and Do Not Disturb are followed. OpenStrap never reads what a notification says.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange)
    }
}
