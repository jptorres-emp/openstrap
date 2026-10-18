package org.openstrap.app.ui.dashboard

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import org.openstrap.app.data.db.SleepSession
import org.openstrap.app.ui.components.Panel
import org.openstrap.app.ui.components.PanelHeader
import org.openstrap.app.ui.components.formatDuration
import org.openstrap.app.ui.theme.MetricColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.openstrap.app.ui.theme.PhosphorIcons

private fun clock(epochSeconds: Long): String =
    Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))

/** The day's naps, one line each. */
@Composable
fun NapsPanel(naps: List<SleepSession>, onClick: (() -> Unit)? = null) {
    Panel(onClick = onClick) {
        PanelHeader("Naps", PhosphorIcons.Moon, MetricColors.sleepLight, chevron = onClick != null)
        naps.forEach { nap ->
            Text("${clock(nap.start)} – ${clock(nap.end)} · ${formatDuration(nap.end - nap.start)}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
