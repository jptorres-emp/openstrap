package org.openstrap.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.openstrap.app.data.db.SleepStage
import org.openstrap.app.ui.components.BigValue
import org.openstrap.app.ui.components.ChartPoint
import org.openstrap.app.ui.components.EmptyHint
import org.openstrap.app.ui.components.Hypnogram
import org.openstrap.app.ui.components.LineChart
import org.openstrap.app.ui.components.MetricCard
import org.openstrap.app.ui.components.formatDuration
import org.openstrap.app.ui.theme.MetricColors

@Composable
private fun ScreenList(contentPadding: PaddingValues, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
fun HeartScreen(
    state: DayUiState,
    liveHeartRate: Int?,
    liveEnabled: Boolean,
    canUseLive: Boolean,
    onLiveToggle: (Boolean) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    contentPadding: PaddingValues,
) = ScreenList(contentPadding) {
    item { DateHeader(state.date, onPrev, onNext) }
    item {
        MetricCard("Live heart rate", Icons.Default.Favorite, MetricColors.heart) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BigValue(liveHeartRate?.toString() ?: "—", "bpm", Modifier.weight(1f))
                Switch(checked = liveEnabled, onCheckedChange = onLiveToggle, enabled = canUseLive)
            }
            if (!canUseLive) EmptyHint("Connect your strap to stream live heart rate.")
        }
    }
    item {
        MetricCard("Heart rate", Icons.Default.Favorite, MetricColors.heart) {
            if (state.heartRate.isEmpty()) EmptyHint("No heart-rate data for this day.")
            else {
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    BigValue(state.heartRate.minOf { it.y }.toInt().toString(), "bpm", caption = "Min")
                    BigValue(state.heartRate.maxOf { it.y }.toInt().toString(), "bpm", caption = "Max")
                    BigValue(state.restingHeartRate?.toString() ?: "—", "bpm", caption = "Resting")
                }
                LineChart(state.heartRate, MetricColors.heart, state.dayStart..state.dayEnd, height = 180.dp)
            }
        }
    }
    item { SeriesCard("Heart-rate variability", Icons.Default.Insights, MetricColors.hrv, state.hrv, "ms", state) }
    item { SeriesCard("Blood oxygen", Icons.Default.Air, MetricColors.spo2, state.spo2, "%", state) }
    item { SeriesCard("Stress", Icons.Default.Psychology, MetricColors.stress, state.stress, "", state) }
}

@Composable
private fun SeriesCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    points: List<ChartPoint>,
    unit: String,
    state: DayUiState,
) {
    MetricCard(title, icon, color) {
        if (points.isEmpty()) EmptyHint("No data for this day.")
        else {
            BigValue(points.averageOrNull().toString(), unit, caption = "Average")
            LineChart(points, color, state.dayStart..state.dayEnd, height = 120.dp, gapSeconds = 3 * 3600)
        }
    }
}

@Composable
fun SleepScreen(state: DayUiState, onPrev: () -> Unit, onNext: () -> Unit, contentPadding: PaddingValues) = ScreenList(contentPadding) {
    item { DateHeader(state.date, onPrev, onNext) }
    val session = state.sleep
    if (session == null) {
        item { MetricCard("Sleep", Icons.Default.Bedtime, MetricColors.sleepDeep) { EmptyHint("No sleep recorded for this night.") } }
        return@ScreenList
    }
    item {
        MetricCard("Last night", Icons.Default.Bedtime, MetricColors.sleepDeep) {
            val b = state.sleepBreakdown
            BigValue(
                formatDuration(b?.asleepSeconds ?: (session.end - session.start)), "asleep",
                caption = "${clock(session.start)} – ${clock(session.end)}",
            )
            if (state.sleepStages.isNotEmpty()) {
                Hypnogram(state.sleepStages, SLEEP_COLORS, DayViewModel.SLEEP_STAGE_ROWS, height = 160.dp)
            }
        }
    }
    val b = state.sleepBreakdown ?: return@ScreenList
    item {
        MetricCard("Stages", Icons.Default.Insights, MetricColors.sleepRem) {
            listOf(
                SleepStage.DEEP to "Deep", SleepStage.LIGHT to "Light",
                SleepStage.REM to "REM", SleepStage.AWAKE to "Awake",
            ).forEach { (stage, label) ->
                val secs = b.byStage[stage] ?: 0L
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(SLEEP_COLORS.getValue(stage), CircleShape))
                    Spacer(Modifier.width(10.dp))
                    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text(formatDuration(secs), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

private fun clock(epochSeconds: Long): String =
    java.time.Instant.ofEpochSecond(epochSeconds).atZone(java.time.ZoneId.systemDefault())
        .toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
