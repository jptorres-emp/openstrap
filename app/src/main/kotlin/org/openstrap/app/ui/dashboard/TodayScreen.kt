package org.openstrap.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.openstrap.app.data.db.SleepStage
import org.openstrap.app.ui.components.BarChart
import org.openstrap.app.ui.components.BigValue
import org.openstrap.app.ui.components.EmptyHint
import org.openstrap.app.ui.components.Hypnogram
import org.openstrap.app.ui.components.LineChart
import org.openstrap.app.ui.components.MetricCard
import org.openstrap.app.ui.components.ProgressRing
import org.openstrap.app.ui.components.formatDuration
import org.openstrap.app.ui.theme.MetricColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter

const val STEP_GOAL = 8000

val SLEEP_COLORS = mapOf(
    SleepStage.AWAKE to MetricColors.sleepAwake,
    SleepStage.REM to MetricColors.sleepRem,
    SleepStage.LIGHT to MetricColors.sleepLight,
    SleepStage.DEEP to MetricColors.sleepDeep,
)

@Composable
fun DateHeader(date: LocalDate, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) { Icon(Icons.Default.ChevronLeft, "Previous day") }
        Text(
            when (date) {
                LocalDate.now() -> "Today"
                LocalDate.now().minusDays(1) -> "Yesterday"
                else -> date.format(DateTimeFormatter.ofPattern("EEE, d MMM"))
            },
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNext, enabled = date < LocalDate.now()) { Icon(Icons.Default.ChevronRight, "Next day") }
    }
}

@Composable
fun TodayScreen(state: DayUiState, liveHeartRate: Int?, onPrev: () -> Unit, onNext: () -> Unit, contentPadding: PaddingValues) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { DateHeader(state.date, onPrev, onNext) }

        item {
            MetricCard("Steps", Icons.AutoMirrored.Filled.DirectionsWalk, MetricColors.steps) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        ProgressRing(state.steps / STEP_GOAL.toFloat(), MetricColors.steps)
                        Text("${(state.steps * 100 / STEP_GOAL)}%", style = MaterialTheme.typography.titleMedium)
                    }
                    BigValue("%,d".format(state.steps), "steps", caption = "Goal %,d".format(STEP_GOAL))
                }
                BarChart(state.hourlySteps, MetricColors.steps, height = 80.dp)
            }
        }

        item {
            MetricCard("Heart rate", Icons.Default.Favorite, MetricColors.heart) {
                val shown = liveHeartRate ?: state.latestHeartRate
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    BigValue(shown?.toString() ?: "—", "bpm", caption = if (liveHeartRate != null) "Live" else "Latest")
                    BigValue(state.restingHeartRate?.toString() ?: "—", "bpm", caption = "Resting")
                }
                if (state.heartRate.isEmpty()) EmptyHint("No heart-rate data yet. Sync your strap.")
                else LineChart(state.heartRate, MetricColors.heart, state.dayStart..state.dayEnd, height = 100.dp)
            }
        }

        item {
            MetricCard("Sleep", Icons.Default.Bedtime, MetricColors.sleepDeep) {
                val breakdown = state.sleepBreakdown
                val session = state.sleep
                if (session == null) {
                    EmptyHint("No sleep recorded for this night.")
                } else {
                    BigValue(formatDuration(breakdown?.asleepSeconds ?: (session.end - session.start)), "asleep")
                    if (state.sleepStages.isNotEmpty()) {
                        Hypnogram(state.sleepStages, SLEEP_COLORS, DayViewModel.SLEEP_STAGE_ROWS, height = 90.dp)
                    }
                }
            }
        }

        item {
            MetricCard("Vitals", Icons.Default.MonitorHeart, MetricColors.hrv) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    VitalValue("SpO₂", state.spo2.averageOrNull()?.let { "$it%" })
                    VitalValue("Stress", state.stress.averageOrNull()?.toString())
                    VitalValue("HRV", state.hrv.averageOrNull()?.let { "$it ms" })
                }
            }
        }
    }
}

@Composable
private fun VitalValue(label: String, value: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 8.dp)) {
        Text(value ?: "—", style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

fun List<org.openstrap.app.ui.components.ChartPoint>.averageOrNull(): Int? =
    if (isEmpty()) null else Math.round(map { it.y }.average()).toInt()
