package org.openstrap.app.ui.glow

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.openstrap.app.data.RecoveryResult
import org.openstrap.app.data.WorkoutAnalysis
import org.openstrap.app.data.db.HealthAlertRow
import org.openstrap.app.ui.components.formatDuration
import org.openstrap.app.ui.dashboard.DailyHistory
import org.openstrap.app.ui.dashboard.DayUiState
import org.openstrap.app.ui.dashboard.Metric
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

/* ---------------------------------------------- Alerts ---------------------------------------------- */

/** What the strap warned about, in the same words as its notification. Codes as in HealthAlert.Type. */
private fun alertText(a: HealthAlertRow): String = when (a.type) {
    1 -> "High heart rate: ${a.value} bpm"
    2 -> "Low heart rate: ${a.value} bpm"
    3 -> "High stress: ${a.value}"
    4 -> "Low blood oxygen: ${a.value}%"
    5 -> "Irregular heart rhythm"
    else -> "Alert from the strap"
}

/** The strap's alerts this day, newest first. Shown only when there are some. */
@Composable
internal fun AlertsPanel(alerts: List<HealthAlertRow>, onOpenMetric: (Metric) -> Unit) {
    DotPanel {
        DotHeader("Strap alerts", trailing = if (alerts.size == 1) "1 today" else "${alerts.size} today", warning = true)
        alerts.sortedByDescending { it.timestamp }.forEach { a ->
            val metric = when (a.type) { 3 -> Metric.Stress; 4 -> Metric.Spo2; else -> Metric.HeartRate }
            Row(Modifier.fillMaxWidth().clickable { onOpenMetric(metric) }, verticalAlignment = Alignment.CenterVertically) {
                Text(alertText(a), color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Note(clockOf(a.timestamp))
            }
        }
        Note("If you feel unwell, checking in with a healthcare professional may be helpful. ${org.openstrap.app.data.BodySignalsCopy.DISCLAIMER}")
    }
}

private fun clockOf(epoch: Long) = Instant.ofEpochSecond(epoch).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))

/* -------------------------------------------- Weekly recap ------------------------------------------ */

/**
 * Monday's look back: the seven days before today against the seven before that. Steps per day, time asleep,
 * the average Recovery and the workouts done.
 */
@Composable
internal fun WeeklyRecapPanel(state: DayUiState, recovery: RecoveryResult?, workouts: List<WorkoutAnalysis>, history: DailyHistory) {
    val end = state.date.minusDays(1)
    val start = end.minusDays(6)
    val before = start.minusDays(7)..start.minusDays(1)
    val steps by remember(end) { history(Metric.Steps.daily, before.start, end) }.collectAsState(emptyList())

    fun stepsAvg(from: java.time.LocalDate, to: java.time.LocalDate) =
        steps.filter { it.date in from..to }.map { it.value }.takeIf { it.isNotEmpty() }?.average()
    val stepsNow = stepsAvg(start, end)
    val stepsThen = stepsAvg(before.start, before.endInclusive)

    // A night belongs to the morning it ended; last week's nights end Tuesday through this morning.
    fun asleepAvg(from: java.time.LocalDate, to: java.time.LocalDate) =
        state.nights.filter { it.date in from..to }.mapNotNull { it.asleepSeconds }.takeIf { it.isNotEmpty() }?.average()
    val sleepNow = asleepAvg(start.plusDays(1), state.date)
    val sleepThen = asleepAvg(before.start.plusDays(1), start)

    val recoveryAvg = recovery?.takeIf { it.date == state.date }?.readinessTrend?.mapNotNull { it.second }?.takeIf { it.isNotEmpty() }?.average()
    val zone = ZoneId.systemDefault()
    val done = workouts.count { Instant.ofEpochSecond(it.workout.start).atZone(zone).toLocalDate() in start..end }

    DotPanel {
        val range = DateTimeFormatter.ofPattern("d MMM")
        DotHeader("Your week", trailing = "${start.format(range)} – ${end.format(range)}")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Fact("Steps a day", stepsNow?.let { "%,d".format(it.roundToInt()) } ?: "—")
                Fact("Recovery", recoveryAvg?.roundToInt()?.toString() ?: "—")
            }
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Fact("Asleep a night", sleepNow?.let { formatDuration(it.toLong()) } ?: "—")
                Fact("Workouts", "$done")
            }
        }
        val changes = listOfNotNull(
            if (stepsNow != null && stepsThen != null && stepsThen > 0) {
                val pct = ((stepsNow - stepsThen) / stepsThen * 100).roundToInt()
                if (pct == 0) "Steps held steady" else "Steps ${if (pct > 0) "up" else "down"} ${abs(pct)}%"
            } else null,
            if (sleepNow != null && sleepThen != null) {
                val d = (sleepNow - sleepThen).toLong()
                if (abs(d) < 5 * 60) "sleep about the same" else "${formatDuration(abs(d))} ${if (d > 0) "more" else "less"} sleep a night"
            } else null,
        )
        Note(
            if (changes.isEmpty()) "Comparisons with the week before start once there are two weeks of data."
            else changes.joinToString(", ").replaceFirstChar { it.uppercase() } + " than the week before.",
        )
    }
}

/** Whether today is the recap's day. */
internal fun isRecapDay(state: DayUiState) = state.date.dayOfWeek == DayOfWeek.MONDAY

/* --------------------------------------------- Tile choice ------------------------------------------ */

/** The metrics What changed can show, in its order. */
internal val TODAY_TILES = listOf(Metric.RestingHr, Metric.Hrv, Metric.Spo2, Metric.SkinTemperature, Metric.Stress, Metric.Breathing)

/** Pick which statistics What changed shows. At least one stays on. */
@Composable
internal fun TilesDialog(chosen: List<Metric>, onSave: (List<Metric>) -> Unit, onDismiss: () -> Unit) {
    var on by rememberSaveable { mutableStateOf(chosen.map { it.name }.toSet()) }
    org.openstrap.app.ui.components.GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What changed shows") },
        text = {
            Column {
                TODAY_TILES.forEach { m ->
                    val checked = m.name in on
                    Row(
                        Modifier.fillMaxWidth().clickable { on = if (checked) on - m.name else on + m.name },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked, { on = if (checked) on - m.name else on + m.name })
                        Text(m.title, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = on.isNotEmpty(), onClick = { onSave(TODAY_TILES.filter { it.name in on }) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
