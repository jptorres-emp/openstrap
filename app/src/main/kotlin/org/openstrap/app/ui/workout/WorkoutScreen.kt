package org.openstrap.app.ui.workout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.openstrap.app.data.WorkoutAnalysis
import org.openstrap.app.data.Workouts
import org.openstrap.app.ui.components.BigValue
import org.openstrap.app.ui.components.ChartPoint
import org.openstrap.app.ui.components.EmptyHint
import org.openstrap.app.ui.components.LineChart
import org.openstrap.app.ui.components.Panel
import org.openstrap.app.ui.components.PanelHeader
import org.openstrap.app.ui.components.SectionLabel
import org.openstrap.app.ui.components.StageRow
import org.openstrap.app.ui.components.formatDuration
import org.openstrap.app.ui.dashboard.ScreenList
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.app.ui.theme.PhosphorIcons
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Zone 1 (easy) to 5 (max), cool to hot. */
internal val ZONE_COLORS = listOf(Color(0xFF8FA8FF), Color(0xFF2DD4BF), Color(0xFF3DDC97), Color(0xFFFFB547), Color(0xFFFF5A5F))
internal val ZONE_NAMES = listOf("Warm-up", "Fat burn", "Aerobic", "Anaerobic", "Maximum")

private fun clock(epoch: Long) = Instant.ofEpochSecond(epoch).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
private fun dateLine(epoch: Long) = Instant.ofEpochSecond(epoch).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE d MMM · HH:mm"))

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

/** Every workout the strap has recorded, newest first, grouped by day. */
@Composable
fun WorkoutsScreen(workouts: List<WorkoutAnalysis>, contentPadding: PaddingValues, onOpen: (Long) -> Unit) {
    ScreenList(contentPadding) {
        item { Text("Activity", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 4.dp, top = 8.dp)) }
        if (workouts.isEmpty()) {
            item {
                Panel {
                    PanelHeader("No workouts yet", PhosphorIcons.Barbell, MetricColors.steps)
                    EmptyHint("Workouts the strap records show up here after a sync, with your heart rate every second and how fast it recovered.")
                }
            }
            return@ScreenList
        }
        val zone = ZoneId.systemDefault()
        workouts.groupBy { Instant.ofEpochSecond(it.workout.start).atZone(zone).toLocalDate() }.forEach { (date, day) ->
            item {
                val today = java.time.LocalDate.now()
                SectionLabel(
                    when (date) {
                        today -> "Today"
                        today.minusDays(1) -> "Yesterday"
                        else -> date.format(DateTimeFormatter.ofPattern("EEE d MMM"))
                    },
                    Modifier.padding(start = 4.dp, top = 8.dp),
                )
            }
            items(day.size) { i -> WorkoutRow(day[i], onOpen) }
        }
    }
}

@Composable
private fun WorkoutRow(a: WorkoutAnalysis, onOpen: (Long) -> Unit) {
    Panel(onClick = { onOpen(a.workout.start) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(PhosphorIcons.Barbell, null, tint = MetricColors.steps)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(a.sportName, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${clock(a.workout.start)} · ${formatDuration(a.durationSeconds)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                a.workout.averageHeartRate?.let { Text("$it bpm", style = MaterialTheme.typography.titleMedium) }
                a.workout.calories?.let { Text("$it kcal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Icon(PhosphorIcons.CaretRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
fun WorkoutScreen(a: WorkoutAnalysis?, contentPadding: PaddingValues, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    ScreenList(contentPadding) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(PhosphorIcons.ArrowLeft, "Back") }
                Column(Modifier.weight(1f)) {
                    Text(a?.sportName ?: "Workout", style = MaterialTheme.typography.headlineMedium)
                    if (a != null) Text(dateLine(a.workout.start), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (a == null) {
            item { Panel { EmptyHint("Loading…") } }
            return@ScreenList
        }
        val w = a.workout
        item {
            Panel {
                Row {
                    BigValue(formatDuration(a.durationSeconds), "", Modifier.weight(1f), caption = "Duration")
                    w.calories?.let { BigValue("$it", "kcal", caption = "Calories") }
                }
                Row {
                    BigValue(w.averageHeartRate?.toString() ?: "—", "bpm", Modifier.weight(1f), caption = "Average HR")
                    BigValue(w.maxHeartRate?.toString() ?: "—", "bpm", caption = "Max HR")
                }
                val active = w.exerciseSeconds?.toLong()
                if (active != null && a.durationSeconds - active > 60) {
                    Text(
                        "${formatDuration(active)} active, ${formatDuration(a.durationSeconds - active)} paused.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            Panel {
                PanelHeader("Heart rate", PhosphorIcons.Heart, MetricColors.heart, trailing = "Every second")
                if (a.heartRate.isEmpty()) EmptyHint("The strap hasn't sent this workout's heart rate yet.")
                else {
                    val last = a.heartRate.last().timestamp
                    LineChart(
                        a.heartRate.map { ChartPoint(it.timestamp, it.bpm.toFloat()) }, MetricColors.heart, w.start..maxOf(last, a.end),
                        height = 180.dp, gapSeconds = 30, band = w.start..a.end, bandColor = MetricColors.heart.copy(alpha = 0.08f), unit = "bpm",
                    )
                    if (last > a.end + 30) {
                        Text(
                            "The shaded part is the workout. The strap kept recording for ${formatDuration((last - a.end + 30) / 60 * 60)} after you stopped.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (a.recovery60 != null) item { RecoveryPanel(a) }
        val zoneTotal = a.zoneSeconds.sum()
        if (zoneTotal > 0) item {
            Panel {
                PanelHeader("Heart-rate zones", PhosphorIcons.ChartLine, MetricColors.heart, trailing = "Max ${a.maxHeartRate} bpm")
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (z in Workouts.ZONES - 1 downTo 0) {
                        val lo = (50 + z * 10) * a.maxHeartRate / 100
                        val hi = if (z == Workouts.ZONES - 1) "" else "–${(60 + z * 10) * a.maxHeartRate / 100 - 1}"
                        StageRow("${ZONE_NAMES[z]} · $lo$hi", a.zoneSeconds[z].toLong(), a.zoneSeconds[z].toFloat() / zoneTotal, ZONE_COLORS[z])
                    }
                }
            }
        }
        item { TrainingPanel(a) }
    }
}

/** A drop as "−24", a rise as "+3". */
internal fun signedDrop(drop: Int) = when { drop > 0 -> "−$drop"; drop < 0 -> "+${-drop}"; else -> "0" }

@Composable
private fun RecoveryPanel(a: WorkoutAnalysis) {
    val drop = a.recovery60 ?: return
    val rated = a.recoveryIsMeaningful
    val (label, color) = when {
        !rated -> null to MaterialTheme.colorScheme.onSurfaceVariant
        drop >= 25 -> "Excellent" to MetricColors.good
        drop >= 18 -> "Good" to MetricColors.good
        drop >= 12 -> "Fair" to MetricColors.warn
        else -> "Low" to MetricColors.bad
    }
    Panel {
        PanelHeader("Recovery heart rate", PhosphorIcons.Heartbeat, MetricColors.hrv, trailing = label)
        Row {
            BigValue(signedDrop(drop), "bpm", Modifier.weight(1f), caption = "After 1 min", captionColor = color)
            a.recovery120?.let { BigValue(signedDrop(it), "bpm", caption = "After 2 min") }
        }
        Text(
            if (rated) {
                "How fast your heart rate fell from ${a.endHeartRate} bpm once you stopped. A bigger drop usually means a " +
                    "fitter heart; under 12 bpm in the first minute is on the low side. Compare it across similar workouts."
            } else {
                "You finished at ${a.endHeartRate ?: "—"} bpm, close to resting, so there was little to recover from. " +
                    "Recovery heart rate is rated after harder efforts."
            },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TrainingPanel(a: WorkoutAnalysis) {
    val w = a.workout
    val e = a.effect
    Panel {
        PanelHeader("Training effect", PhosphorIcons.Barbell, MetricColors.steps)
        Row {
            BigValue(w.aerobicTrainingEffect?.let { "%.1f".format(it) } ?: "—", "/ 5", Modifier.weight(1f), caption = "Aerobic")
            BigValue(w.anaerobicTrainingEffect?.let { "%.1f".format(it) } ?: "—", "/ 5", caption = "Anaerobic")
        }
        Row {
            BigValue(w.trainingLoad?.toString() ?: "—", "", Modifier.weight(1f), caption = "Load")
            e?.recoveryMinutes?.takeIf { it > 0 }?.let { BigValue(formatDuration(it * 60L), "", caption = "Recovery time") }
        }
        val vo2 = e?.vo2max ?: w.vo2max?.toFloat()
        if (vo2 != null) BigValue("%.0f".format(vo2), "ml/kg/min", caption = "VO₂ max")
        val weekly = e?.weeklyLoad
        if (weekly != null && e.optimalLoadMin != null && e.optimalLoadMax != null) {
            Text(
                "Training load this week: ${Math.round(weekly)} (your productive range is ${e.optimalLoadMin}–${e.optimalLoadMax}).",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(
            "As calculated by the strap. VO₂ max shows up once the strap has enough data to estimate it.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
