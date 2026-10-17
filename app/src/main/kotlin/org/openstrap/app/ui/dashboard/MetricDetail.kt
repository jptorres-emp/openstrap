package org.openstrap.app.ui.dashboard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.openstrap.app.ui.components.BigValue
import org.openstrap.app.ui.components.ChartPoint
import org.openstrap.app.ui.components.EmptyHint
import org.openstrap.app.ui.components.LegendDot
import org.openstrap.app.ui.components.LineChart
import org.openstrap.app.ui.components.Panel
import org.openstrap.app.ui.components.PanelHeader
import org.openstrap.app.ui.components.SectionLabel
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.app.ui.theme.PhosphorIcons
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * One metric for the selected day, pushed on top of whichever tab opened it. A first cut that reuses the
 * former Heart tab's cards; the range switch and baseline band come with the metric-detail template.
 */
@Composable
fun MetricDetailScreen(
    metric: Metric,
    state: DayUiState,
    liveHeartRate: Int?,
    liveEnabled: Boolean,
    canUseLive: Boolean,
    onLiveToggle: (Boolean) -> Unit,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    ScreenList(contentPadding) {
        item { DetailHeader(metric.title, state.date, onBack) }
        when (metric) {
            Metric.HeartRate -> {
                if (state.date == LocalDate.now()) item { LivePanel(liveHeartRate, liveEnabled, canUseLive, onLiveToggle) }
                item { HeartRatePanel(state) }
            }
            Metric.RestingHr -> item { TrendPanel("Resting heart rate", PhosphorIcons.Heartbeat, MetricColors.heart, state.restingTrend, "bpm") }
            Metric.Hrv -> {
                item { TrendPanel("Heart-rate variability", PhosphorIcons.ChartLineUp, MetricColors.hrv, state.hrvTrend, "ms") }
                item { SeriesPanel("Through the day", PhosphorIcons.Heartbeat, MetricColors.hrv, state.hrv, "ms", state) }
            }
            Metric.Spo2 -> item { SeriesPanel("Blood oxygen", PhosphorIcons.Wind, MetricColors.spo2, state.spo2, "%", state) }
            Metric.Stress -> item { SeriesPanel("Stress", PhosphorIcons.Brain, MetricColors.stress, state.stress, "", state) }
            Metric.SkinTemperature -> item {
                SeriesPanel("Skin temperature", PhosphorIcons.ThermometerSimple, MetricColors.temperature, state.skinTemperature, "°C", state, decimals = 1)
            }
            Metric.Breathing -> item {
                val window = state.sleep?.let { it.start..it.end } ?: state.dayStart..state.dayEnd
                if (state.breathRate.isEmpty()) Panel { PanelHeader("Breathing rate", PhosphorIcons.Wind, MetricColors.hrv); EmptyHint("Breathing rate is measured during sleep.") }
                else BreathingPanel(state.breathRate, window)
            }
        }
    }
}

/** Back arrow, the screen's title, and the day it shows. */
@Composable
fun DetailHeader(title: String, date: LocalDate, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(PhosphorIcons.ArrowLeft, "Back") }
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
        SectionLabel(
            when (date) {
                LocalDate.now() -> "Today"
                LocalDate.now().minusDays(1) -> "Yesterday"
                else -> date.format(DateTimeFormatter.ofPattern("EEE d MMM"))
            },
        )
        Spacer(Modifier.width(8.dp))
    }
}

@Composable
private fun LivePanel(liveHeartRate: Int?, liveEnabled: Boolean, canUseLive: Boolean, onLiveToggle: (Boolean) -> Unit) {
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

@Composable
private fun HeartRatePanel(state: DayUiState) {
    Panel {
        PanelHeader("Heart rate", PhosphorIcons.Heart, MetricColors.heart)
        if (state.heartRate.isEmpty()) EmptyHint("No heart-rate data for this day.")
        else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                BigValue(state.heartRate.minOf { it.y }.toInt().toString(), "bpm", caption = "Lowest")
                BigValue(state.heartRate.avg().toString(), "bpm", caption = "Average")
                BigValue(state.heartRate.maxOf { it.y }.toInt().toString(), "bpm", caption = "Highest")
            }
            LineChart(
                state.heartRate, MetricColors.heart, state.dayStart..state.dayEnd, height = 190.dp,
                markMin = true, band = state.sleep?.let { it.start..it.end }, unit = "bpm",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendDot("Your average", MetricColors.heart.copy(alpha = 0.7f))
                if (state.sleep != null) LegendDot("Asleep", MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
            }
        }
    }
}

/** Breathing rate across [window], usually the night's sleep. */
@Composable
internal fun BreathingPanel(points: List<ChartPoint>, window: LongRange) {
    Panel {
        PanelHeader("Breathing rate", PhosphorIcons.Wind, MetricColors.hrv)
        BigValue(points.avg().toString(), "br/min", caption = "Average during sleep")
        LineChart(points, MetricColors.hrv, window, height = 110.dp, gapSeconds = 2 * 3600, unit = "br/min")
    }
}
