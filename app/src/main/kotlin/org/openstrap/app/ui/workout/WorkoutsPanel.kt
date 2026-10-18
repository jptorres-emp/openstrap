package org.openstrap.app.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.openstrap.app.data.WorkoutAnalysis
import org.openstrap.app.data.Workouts
import org.openstrap.app.ui.components.Panel
import org.openstrap.app.ui.components.PanelHeader
import org.openstrap.app.ui.components.formatDuration
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.app.ui.theme.PhosphorIcons
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Zone 1 (easy) to 5 (max), cool to hot. */
internal val ZONE_COLORS = listOf(Color(0xFF8FA8FF), Color(0xFF2DD4BF), Color(0xFF3DDC97), Color(0xFFFFB547), Color(0xFFFF5A5F))
internal val ZONE_NAMES = listOf("Warm-up", "Fat burn", "Aerobic", "Anaerobic", "Maximum")

private fun clock(epoch: Long) = Instant.ofEpochSecond(epoch).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))

/** The selected day's workouts on Today; hidden when there are none. */
@Composable
fun WorkoutsPanel(workouts: List<WorkoutAnalysis>, onOpen: (Long) -> Unit) {
    Panel {
        PanelHeader("Workouts", PhosphorIcons.Barbell, MetricColors.steps)
        workouts.forEach { a ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(a.workout.start) }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(a.sportName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        listOfNotNull(
                            "${clock(a.workout.start)} · ${formatDuration(a.durationSeconds)}",
                            a.workout.averageHeartRate?.let { "avg $it bpm" },
                            a.workout.calories?.let { "$it kcal" },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(PhosphorIcons.CaretRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A drop as "−24", a rise as "+3". */
internal fun signedDrop(drop: Int) = when { drop > 0 -> "−$drop"; drop < 0 -> "+${-drop}"; else -> "0" }
