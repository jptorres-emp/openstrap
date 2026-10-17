package org.openstrap.app.ui.dashboard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import org.openstrap.app.data.DailyMetric
import org.openstrap.app.data.DayStat
import org.openstrap.app.data.History
import org.openstrap.app.ui.components.BarChart
import org.openstrap.app.ui.components.BigValue
import org.openstrap.app.ui.components.ChartPoint
import org.openstrap.app.ui.components.EmptyHint
import org.openstrap.app.ui.components.LegendDot
import org.openstrap.app.ui.components.LineChart
import org.openstrap.app.ui.components.Panel
import org.openstrap.app.ui.components.PanelHeader
import org.openstrap.app.ui.components.SectionLabel
import org.openstrap.app.ui.components.SegmentedTabs
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.app.ui.theme.PhosphorIcons
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** How far back a metric detail looks: the selected day, or one value per day (per week, for a year). */
private enum class Range(val label: String, val days: Int) { Day("Day", 1), Week("Week", 7), Month("Month", 30), Year("Year", 365) }

/** Loads one value per day of a metric between two dates; the app root passes the repository's. */
typealias DailyHistory = (DailyMetric, LocalDate, LocalDate) -> Flow<List<DayStat>>

/**
 * One metric, pushed on top of whichever tab opened it: the selected day, or a week, month or year of daily values
 * against your normal (the usual range over the last 30 days), then what the metric means and related metrics.
 */
@Composable
fun MetricDetailScreen(
    metric: Metric,
    state: DayUiState,
    history: DailyHistory,
    liveHeartRate: Int?,
    liveEnabled: Boolean,
    canUseLive: Boolean,
    onLiveToggle: (Boolean) -> Unit,
    onOpenMetric: (Metric) -> Unit,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var rangeIndex by rememberSaveable(metric) { mutableIntStateOf(0) }
    val range = Range.entries[rangeIndex]
    val end = state.date
    // Always at least the normal's 30 days, so the band and status are there whichever range is shown.
    val from = end.minusDays(maxOf(range.days, History.NORMAL_DAYS) - 1L)
    val stats by remember(metric, end, from) { history(metric.daily, from, end) }.collectAsState(null)
    val normal = stats?.let { History.normal(it, end) }

    ScreenList(contentPadding) {
        item { DetailHeader(metric.title, state.date, onBack) }
        item { SegmentedTabs(Range.entries.map { it.label }, rangeIndex, { rangeIndex = it }) }
        if (range == Range.Day) {
            item { NormalPanel(metric, stats?.firstOrNull { it.date == end }, normal) }
            dayContent(metric, state, liveHeartRate, liveEnabled, canUseLive, onLiveToggle)
        } else {
            item { HistoryPanel(metric, range, stats, normal, end) }
        }
        item { AboutPanel(metric) }
        val related = RELATED[metric].orEmpty()
        if (related.isNotEmpty()) item { RelatedPanel(related, onOpenMetric) }
    }
}

/** The selected day's intraday cards, as on the former Heart tab. */
private fun androidx.compose.foundation.lazy.LazyListScope.dayContent(
    metric: Metric, state: DayUiState, liveHeartRate: Int?, liveEnabled: Boolean, canUseLive: Boolean, onLiveToggle: (Boolean) -> Unit,
) {
    when (metric) {
        Metric.HeartRate -> {
            if (state.date == LocalDate.now()) item { LivePanel(liveHeartRate, liveEnabled, canUseLive, onLiveToggle) }
            item { HeartRatePanel(state) }
        }
        Metric.RestingHr -> item { TrendPanel("Resting heart rate", PhosphorIcons.Heartbeat, MetricColors.heart, state.restingTrend, "bpm") }
        Metric.Hrv -> item { SeriesPanel("Through the day", PhosphorIcons.Heartbeat, MetricColors.hrv, state.hrv, "ms", state) }
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
        Metric.Steps -> item {
            Panel {
                PanelHeader("By hour", PhosphorIcons.Footprints, MetricColors.steps)
                BarChart(state.hourlySteps, MetricColors.steps) { hour, steps -> "%02d:00–%02d:00 · %,d steps".format(hour, (hour + 1) % 24, Math.round(steps)) }
            }
        }
    }
}

private fun colorOf(metric: Metric): Color = when (metric) {
    Metric.HeartRate, Metric.RestingHr -> MetricColors.heart
    Metric.Hrv, Metric.Breathing -> MetricColors.hrv
    Metric.Spo2 -> MetricColors.spo2
    Metric.Stress -> MetricColors.stress
    Metric.SkinTemperature -> MetricColors.temperature
    Metric.Steps -> MetricColors.steps
}

/** "Within your normal", "Higher than your normal" or "Lower than your normal", with the range itself. */
private fun statusLine(metric: Metric, value: Float?, normal: History.Normal?): String? {
    normal ?: return null
    val range = "${metric.format(normal.low)}–${metric.format(normal.high)}${metric.unit.let { if (it.isEmpty()) "" else " $it" }}"
    return when (History.status(value, normal)) {
        1 -> "Higher than your normal ($range)"
        -1 -> "Lower than your normal ($range)"
        0 -> "Within your normal ($range)"
        else -> "Your normal: $range"
    }
}

private fun statusColor(value: Float?, normal: History.Normal?): Color = when (History.status(value, normal)) {
    1, -1 -> MetricColors.warn
    0 -> MetricColors.readiness
    else -> Color.Unspecified
}

/**
 * The day against your normal. The cards below already show the day's own numbers, so this shows the usual value and
 * where the day sits; steps, whose card is only the hourly bars, also get the day's total.
 */
@Composable
private fun NormalPanel(metric: Metric, today: DayStat?, normal: History.Normal?) {
    Panel {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            if (metric == Metric.Steps) BigValue(today?.let { metric.format(it.value) } ?: "—", metric.unit, Modifier.weight(1f), caption = "Today's total")
            normal?.let { BigValue(metric.format(it.mean), metric.unit, if (metric == Metric.Steps) Modifier else Modifier.weight(1f), caption = "Your usual") }
        }
        val line = statusLine(metric, today?.value, normal)
        if (line != null) Text(line, style = MaterialTheme.typography.bodyMedium, color = statusColor(today?.value, normal))
        else NormalPending(null)
    }
}

/** A week, month or year of daily values, with your normal as a band. */
@Composable
private fun HistoryPanel(metric: Metric, range: Range, stats: List<DayStat>?, normal: History.Normal?, end: LocalDate) {
    val color = colorOf(metric)
    val start = end.minusDays(range.days - 1L)
    Panel {
        PanelHeader(metric.title, PhosphorIcons.ChartLine, color, trailing = when (range) {
            Range.Week -> "Last 7 days"
            Range.Month -> "Last 30 days"
            else -> "Last 12 months"
        })
        if (stats == null) { EmptyHint("Loading…"); return@Panel }
        val summary = History.summary(stats, start, end)
        if (summary == null) { EmptyHint("No ${metric.title.lowercase()} in this range yet."); return@Panel }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            BigValue(metric.format(summary.average), metric.unit, Modifier.weight(1f), caption = "Daily average")
            if (metric == Metric.Steps) BigValue("%,d".format(Math.round(summary.total)), "", caption = "Total")
        }
        // Over a year, a month's average sitting inside last month's normal says nothing; status is for weeks and months.
        if (range != Range.Year) statusLine(metric, summary.average, normal)?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = statusColor(summary.average, normal))
        }

        val zone = ZoneId.systemDefault()
        val shown = stats.filter { it.date in start..end }.let { if (range == Range.Year) History.weekly(it) else it }
        val points = shown.map { ChartPoint(it.date.atTime(12, 0).atZone(zone).toEpochSecond(), it.value) }
        val xRange = start.atStartOfDay(zone).toEpochSecond()..end.plusDays(1).atStartOfDay(zone).toEpochSecond() - 1
        val fmt = DateTimeFormatter.ofPattern(if (range == Range.Year) "MMM" else if (range == Range.Week) "EEE" else "d MMM")
        LineChart(
            points, color, xRange, height = 170.dp,
            // A missing day breaks the line; a year is drawn by week.
            gapSeconds = if (range == Range.Year) 8 * 86_400L else 36 * 3600L,
            format = metric::format, unit = metric.unit,
            xLabel = { java.time.Instant.ofEpochSecond(it).atZone(zone).format(fmt) },
            normal = normal?.let { it.low..it.high },
        )
        if (normal != null) LegendDot("Your normal", color.copy(alpha = 0.35f)) else NormalPending(stats.count { it.date in end.minusDays(History.NORMAL_DAYS - 1L)..end })

        val day = DateTimeFormatter.ofPattern("EEE d MMM")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Fact("Lowest", metric, summary.lowest, day, range)
            Fact("Highest", metric, summary.highest, day, range)
        }
    }
}

@Composable
private fun Fact(label: String, metric: Metric, stat: DayStat, day: DateTimeFormatter, range: Range) {
    Column {
        BigValue(metric.format(stat.value), metric.unit, caption = if (range == Range.Year) "$label day" else label)
        Text(stat.date.format(day), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NormalPending(days: Int?) {
    Text(
        "Your normal shows after ${History.MIN_NORMAL_DAYS} days of data" + (days?.let { " ($it so far)." } ?: "."),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** What the metric is and what moves it. */
@Composable
private fun AboutPanel(metric: Metric) {
    val (what, moves) = ABOUT.getValue(metric)
    Panel {
        PanelHeader("About ${metric.title.lowercase()}", PhosphorIcons.Info)
        Text(what, style = MaterialTheme.typography.bodyMedium)
        Text(moves, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RelatedPanel(related: List<Metric>, onOpen: (Metric) -> Unit) {
    Panel {
        SectionLabel("Related")
        related.forEach { m ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(m) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) { LegendDot(m.title, colorOf(m)) }
                Icon(PhosphorIcons.CaretRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private val RELATED = mapOf(
    Metric.HeartRate to listOf(Metric.RestingHr, Metric.Hrv, Metric.Stress),
    Metric.RestingHr to listOf(Metric.Hrv, Metric.HeartRate, Metric.SkinTemperature),
    Metric.Hrv to listOf(Metric.RestingHr, Metric.Stress, Metric.Breathing),
    Metric.Spo2 to listOf(Metric.Breathing, Metric.RestingHr),
    Metric.Stress to listOf(Metric.Hrv, Metric.HeartRate),
    Metric.SkinTemperature to listOf(Metric.RestingHr, Metric.Breathing),
    Metric.Breathing to listOf(Metric.Spo2, Metric.SkinTemperature, Metric.Hrv),
    Metric.Steps to listOf(Metric.HeartRate),
)

private val ABOUT = mapOf(
    Metric.HeartRate to (
        "How fast your heart beats, measured all day by the strap." to
            "Movement, stress, caffeine, heat and illness raise it; rest and sleep bring it down."
        ),
    Metric.RestingHr to (
        "Your heart rate at its calmest, which the strap works out each day. Lower usually means a fitter, well-rested heart." to
            "It rises after hard training, late meals, alcohol, poor sleep or when you're getting ill, and falls as fitness builds."
        ),
    Metric.Hrv to (
        "Heart-rate variability: how much the time between beats varies. Higher than your usual means your body has recovered." to
            "Good sleep and rest raise it. Stress, hard training, alcohol and illness lower it. Compare it with your own normal, not other people's."
        ),
    Metric.Spo2 to (
        "Blood oxygen: how much of your blood's capacity is carrying oxygen. Most people sit at 95–100%." to
            "Altitude, breathing pauses in sleep and illness can lower it. A loose strap can also give low readings."
        ),
    Metric.Stress to (
        "The strap's 0–100 stress level, worked out from your heart rate and its variability while you're still." to
            "Pressure, caffeine and too little sleep raise it; calm breathing, rest and time outdoors bring it down."
        ),
    Metric.SkinTemperature to (
        "The temperature of your skin under the strap. The level depends on fit and room; what matters is the change from your normal." to
            "A rise of half a degree or more can come with illness, alcohol, a warm room or the menstrual cycle."
        ),
    Metric.Breathing to (
        "How many breaths you take a minute while asleep. It stays very steady for most people." to
            "A rise above your normal can mean illness, a hard training day or a warm, stuffy room."
        ),
    Metric.Steps to (
        "Steps counted by the strap through the day." to
            "Short walks spread across the day add up; most people aim for 7,000–10,000."
        ),
)

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
