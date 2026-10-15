package org.openstrap.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.openstrap.app.data.db.SleepStage
import org.openstrap.app.ui.components.BigValue
import org.openstrap.app.ui.components.ChartPoint
import org.openstrap.app.ui.components.EmptyHint
import org.openstrap.app.ui.components.Hypnogram
import org.openstrap.app.ui.components.LegendDot
import org.openstrap.app.ui.components.LineChart
import org.openstrap.app.ui.components.Panel
import org.openstrap.app.ui.components.PanelHeader
import org.openstrap.app.ui.components.SegmentedTabs
import org.openstrap.app.ui.components.StageRow
import org.openstrap.app.ui.components.TrendChart
import org.openstrap.app.ui.components.TrendPoint
import org.openstrap.app.ui.components.formatDuration
import org.openstrap.app.ui.theme.MetricColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HeartScreen(
    state: DayUiState,
    liveHeartRate: Int?,
    liveEnabled: Boolean,
    canUseLive: Boolean,
    battery: Int?,
    charging: Boolean = false,
    onLiveToggle: (Boolean) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    contentPadding: PaddingValues,
) = ScreenList(contentPadding) {
    item { DateHeader(state.date, battery, onPrev, onNext, charging) }

    item {
        Panel {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BigValue(
                    liveHeartRate?.toString() ?: "—", "bpm", Modifier.weight(1f),
                    caption = if (liveEnabled) "Live heart rate" else "Live heart rate · off",
                    captionColor = if (liveHeartRate != null) MetricColors.heart else MaterialTheme.colorScheme.onSurfaceVariant,
                    large = true,
                )
                Switch(
                    checked = liveEnabled, onCheckedChange = onLiveToggle, enabled = canUseLive,
                    colors = SwitchDefaults.colors(checkedTrackColor = MetricColors.heart),
                )
            }
            if (!canUseLive) EmptyHint("Connect your strap to stream live heart rate.")
        }
    }

    item {
        Panel {
            PanelHeader("Heart rate", Icons.Default.Favorite, MetricColors.heart)
            if (state.heartRate.isEmpty()) EmptyHint("No heart-rate data for this day.")
            else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    BigValue(state.heartRate.minOf { it.y }.toInt().toString(), "bpm", caption = "Lowest")
                    BigValue(state.heartRate.avg().toString(), "bpm", caption = "Average")
                    BigValue(state.heartRate.maxOf { it.y }.toInt().toString(), "bpm", caption = "Highest")
                }
                LineChart(
                    state.heartRate, MetricColors.heart, state.dayStart..state.dayEnd, height = 190.dp,
                    markMin = true, band = state.sleep?.let { it.start..it.end },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LegendDot("Your average", MetricColors.heart.copy(alpha = 0.7f))
                    if (state.sleep != null) LegendDot("Asleep", MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                }
            }
        }
    }

    item { TrendPanel("Resting heart rate", Icons.Default.MonitorHeart, MetricColors.heart, state.restingTrend, "bpm") }
    item { TrendPanel("Heart-rate variability", Icons.Default.Insights, MetricColors.hrv, state.hrvTrend, "ms") }
    item { SeriesPanel("Blood oxygen", Icons.Default.Air, MetricColors.spo2, state.spo2, "%", state) }
    item { SeriesPanel("Stress", Icons.Default.Psychology, MetricColors.stress, state.stress, "", state) }
    item { SeriesPanel("Skin temperature", Icons.Default.Thermostat, MetricColors.temperature, state.skinTemperature, "°C", state, decimals = 1) }
}

/** 7-day trend card; today's value is the headline. */
@Composable
private fun TrendPanel(title: String, icon: ImageVector, color: Color, points: List<TrendPoint>, unit: String) {
    Panel {
        PanelHeader(title, icon, color, trailing = "Last 7 days")
        val today = points.lastOrNull()?.value
        val known = points.mapNotNull { it.value }
        if (known.isEmpty()) EmptyHint("Not enough data yet.")
        else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                BigValue(today?.toInt()?.toString() ?: "—", unit, caption = "Today")
                BigValue(Math.round(known.average()).toString(), unit, caption = "7-day avg")
            }
            TrendChart(points, color)
        }
    }
}

@Composable
private fun SeriesPanel(
    title: String,
    icon: ImageVector,
    color: Color,
    points: List<ChartPoint>,
    unit: String,
    state: DayUiState,
    decimals: Int = 0,
) {
    Panel {
        PanelHeader(title, icon, color)
        if (points.isEmpty()) EmptyHint("No data for this day.")
        else {
            val average = if (decimals == 0) points.avg().toString() else "%.${decimals}f".format(points.map { it.y }.average())
            BigValue(average, unit, caption = "Average")
            LineChart(points, color, state.dayStart..state.dayEnd, height = 130.dp, gapSeconds = 3 * 3600)
        }
    }
}

@Composable
fun SleepScreen(state: DayUiState, battery: Int?, charging: Boolean, onPrev: () -> Unit, onNext: () -> Unit, contentPadding: PaddingValues) = ScreenList(contentPadding) {
    item { DateHeader(state.date, battery, onPrev, onNext, charging) }
    val session = state.sleep
    if (session == null) {
        item { Panel { PanelHeader("Sleep", Icons.Default.Bedtime, MetricColors.sleepDeep); EmptyHint("No sleep recorded for this night.") } }
        return@ScreenList
    }

    item {
        Panel {
            PanelHeader("Time asleep", Icons.Default.Bedtime, MetricColors.sleepDeep, trailing = "${clock(session.start)} – ${clock(session.end)}")
            session.score?.let { score ->
                Text("Sleep score $score" + (session.wakeCount?.let { " · woke $it×" } ?: ""), style = MaterialTheme.typography.titleMedium)
            }
            BigValue(formatDuration(state.asleepSeconds ?: 0), "", large = true)
            Text(
                "Total duration ${formatDuration(session.end - session.start)} · goal ${formatDuration(SLEEP_GOAL_SECONDS)}",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.sleepStages.isNotEmpty()) {
                Hypnogram(state.sleepStages, SLEEP_COLORS, DayViewModel.SLEEP_STAGE_ROWS, height = 180.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    LegendDot("Awake", MetricColors.sleepAwake)
                    LegendDot("REM", MetricColors.sleepRem)
                    LegendDot("Light", MetricColors.sleepLight)
                    LegendDot("Deep", MetricColors.sleepDeep)
                }
            }
        }
    }

    val b = state.sleepBreakdown
    if (b != null) item {
        Panel {
            PanelHeader("Stages", Icons.Default.Insights, MetricColors.sleepRem)
            val total = (b.asleepSeconds + b.awakeSeconds).coerceAtLeast(1)
            listOf(
                SleepStage.AWAKE to "Awake", SleepStage.REM to "REM",
                SleepStage.LIGHT to "Light", SleepStage.DEEP to "Deep",
            ).forEach { (stage, label) ->
                val secs = b.byStage[stage] ?: 0L
                StageRow(label, secs, secs.toFloat() / total, SLEEP_COLORS.getValue(stage))
            }
        }
    }

    if (state.breathRate.isNotEmpty()) item {
        Panel {
            PanelHeader("Breathing rate", Icons.Default.Air, MetricColors.hrv)
            BigValue(state.breathRate.avg().toString(), "br/min", caption = "Average during sleep")
            LineChart(state.breathRate, MetricColors.hrv, session.start..session.end, height = 110.dp)
        }
    }

    if (state.naps.isNotEmpty()) item {
        Panel {
            PanelHeader("Naps", Icons.Default.Bedtime, MetricColors.sleepLight)
            state.naps.forEach { nap ->
                Text("${clock(nap.start)} – ${clock(nap.end)} · ${formatDuration(nap.end - nap.start)}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    item {
        var range by rememberSaveable { mutableIntStateOf(0) }
        Panel {
            PanelHeader("Heart rate during sleep", Icons.Default.Favorite, MetricColors.heart)
            SegmentedTabs(listOf("Sleep", "Whole day"), range, { range = it })
            val window = if (range == 0) session.start..session.end else state.dayStart..state.dayEnd
            val pts = state.heartRate.filter { it.x in window }
            if (pts.isEmpty()) EmptyHint("No heart-rate data in this window.")
            else {
                BigValue(pts.minOf { it.y }.toInt().toString(), "bpm", caption = "Lowest")
                LineChart(pts, MetricColors.heart, window, height = 150.dp, markMin = true)
            }
        }
    }
}

private fun clock(epochSeconds: Long): String =
    Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
