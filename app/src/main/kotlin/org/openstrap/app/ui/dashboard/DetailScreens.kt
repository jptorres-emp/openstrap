package org.openstrap.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import org.openstrap.app.ui.components.ContributorRow
import org.openstrap.app.ui.components.HeroPanel
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.openstrap.app.data.db.SleepSession
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
import org.openstrap.app.ui.theme.PhosphorIcons

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
    picker: DayPicker = DayPicker(),
    focus: FocusRequest? = null,
    onFocusDone: () -> Unit = {},
) {
    val list = rememberLazyListState()
    val index = remember { SectionIndex() }
    val f = rememberFocus(focus, list, index, onFocusDone)
    ScreenList(contentPadding, list, index) {
    section(index) { DateHeader(state.date, battery, onPrev, onNext, charging, picker) }

    section(index) {
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

    section(index, FocusTarget.HeartRate) {
        Panel(highlight = MetricColors.heart.takeIf { f.isHighlighted(FocusTarget.HeartRate) }) {
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
                    markMin = true, band = state.sleep?.let { it.start..it.end }, reveal = f.reveal(FocusTarget.HeartRate), unit = "bpm",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LegendDot("Your average", MetricColors.heart.copy(alpha = 0.7f))
                    if (state.sleep != null) LegendDot("Asleep", MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                }
            }
        }
    }

    section(index, FocusTarget.RestingHr) { TrendPanel("Resting heart rate", PhosphorIcons.Heartbeat, MetricColors.heart, state.restingTrend, "bpm", f, FocusTarget.RestingHr) }
    section(index, FocusTarget.Hrv) { TrendPanel("Heart-rate variability", PhosphorIcons.ChartLineUp, MetricColors.hrv, state.hrvTrend, "ms", f, FocusTarget.Hrv) }
    section(index, FocusTarget.Spo2) { SeriesPanel("Blood oxygen", PhosphorIcons.Wind, MetricColors.spo2, state.spo2, "%", state, f, FocusTarget.Spo2) }
    section(index, FocusTarget.Stress) { SeriesPanel("Stress", PhosphorIcons.Brain, MetricColors.stress, state.stress, "", state, f, FocusTarget.Stress) }
    section(index, FocusTarget.SkinTemperature) {
        SeriesPanel("Skin temperature", PhosphorIcons.ThermometerSimple, MetricColors.temperature, state.skinTemperature, "°C", state, f, FocusTarget.SkinTemperature, decimals = 1)
    }
    }
}

/** 7-day trend card; today's value is the headline. */
@Composable
private fun TrendPanel(title: String, icon: ImageVector, color: Color, points: List<TrendPoint>, unit: String, focus: FocusState, target: FocusTarget) {
    Panel(highlight = color.takeIf { focus.isHighlighted(target) }) {
        PanelHeader(title, icon, color, trailing = "Last 7 days")
        val today = points.lastOrNull()?.value
        val known = points.mapNotNull { it.value }
        if (known.isEmpty()) EmptyHint("Not enough data yet.")
        else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                BigValue(today?.toInt()?.toString() ?: "—", unit, caption = "Today")
                BigValue(Math.round(known.average()).toString(), unit, caption = "7-day avg")
            }
            TrendChart(points, color, reveal = focus.reveal(target), unit = unit)
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
    focus: FocusState,
    target: FocusTarget,
    decimals: Int = 0,
) {
    Panel(highlight = color.takeIf { focus.isHighlighted(target) }) {
        PanelHeader(title, icon, color)
        if (points.isEmpty()) EmptyHint("No data for this day.")
        else {
            val average = if (decimals == 0) points.avg().toString() else "%.${decimals}f".format(points.map { it.y }.average())
            BigValue(average, unit, caption = "Average")
            LineChart(
                points, color, state.dayStart..state.dayEnd, height = 130.dp, gapSeconds = 3 * 3600, reveal = focus.reveal(target),
                format = { if (decimals == 0) Math.round(it).toString() else "%.${decimals}f".format(it) }, unit = unit,
            )
        }
    }
}

@Composable
fun SleepScreen(
    state: DayUiState,
    battery: Int?,
    charging: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    contentPadding: PaddingValues,
    focus: FocusRequest? = null,
    onFocusDone: () -> Unit = {},
    picker: DayPicker = DayPicker(),
) {
    val list = rememberLazyListState()
    val index = remember { SectionIndex() }
    val f = rememberFocus(focus, list, index, onFocusDone)
    SleepList(state, battery, charging, onPrev, onNext, contentPadding, list, index, f, picker)
}

@Composable
private fun SleepList(
    state: DayUiState,
    battery: Int?,
    charging: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    contentPadding: PaddingValues,
    list: LazyListState,
    index: SectionIndex,
    f: FocusState,
    picker: DayPicker,
) = ScreenList(contentPadding, list, index) {
    section(index) { DateHeader(state.date, battery, onPrev, onNext, charging, picker) }
    val session = state.sleep
    if (session == null) {
        section(index) { Panel { PanelHeader("Sleep", PhosphorIcons.Moon, MetricColors.sleepDeep); EmptyHint("No sleep recorded for this night.") } }
        if (state.naps.isNotEmpty()) section(index) { NapDetailsPanel(state) }
        return@ScreenList
    }

    section(index) {
        val score = session.score
        val asleep = state.asleepSeconds ?: (session.end - session.start)
        if (score != null) {
            HeroPanel(
                label = "Sleep score", value = "$score", progress = score / 100f, accent = MetricColors.sleepDeep,
                headline = Narrative.sleepHeadline(score), body = Narrative.sleepBody(session, asleep),
            )
        } else {
            HeroPanel(
                label = "Time asleep", value = "%d:%02d".format(asleep / 3600, (asleep % 3600) / 60), progress = asleep / SLEEP_GOAL_SECONDS.toFloat(),
                accent = MetricColors.sleepDeep, headline = "${formatDuration(asleep)} asleep", body = "Your goal is ${formatDuration(SLEEP_GOAL_SECONDS)}.",
            )
        }
    }

    // Bevel: time in bed and time asleep side by side.
    section(index) {
        Panel {
            Row {
                Column(Modifier.weight(1f)) {
                    BigValue(formatDuration(session.end - session.start), "", caption = "Time in bed")
                    Text("${clock(session.start)} – ${clock(session.end)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(Modifier.width(1.dp).height(64.dp).background(MaterialTheme.colorScheme.outlineVariant))
                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    BigValue(formatDuration(state.asleepSeconds ?: 0), "", caption = "Time asleep")
                    session.averageHeartRate?.let {
                        Text("Avg heart rate $it bpm", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    if (state.sleepStages.isNotEmpty()) section(index) {
        Panel {
            PanelHeader("Sleep stages", PhosphorIcons.Moon, MetricColors.sleepDeep, trailing = session.wakeCount?.let { "Woke $it×" })
            Hypnogram(state.sleepStages, SLEEP_COLORS, DayViewModel.SLEEP_STAGE_ROWS, height = 180.dp, stageNames = SLEEP_NAMES)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LegendDot("Awake", MetricColors.sleepAwake)
                LegendDot("REM", MetricColors.sleepRem)
                LegendDot("Light", MetricColors.sleepLight)
                LegendDot("Deep", MetricColors.sleepDeep)
            }
            state.sleepBreakdown?.let { b ->
                val total = (b.asleepSeconds + b.awakeSeconds).coerceAtLeast(1)
                SLEEP_NAMES.forEach { (stage, label) ->
                    val secs = b.byStage[stage] ?: 0L
                    StageRow(label, secs, secs.toFloat() / total, SLEEP_COLORS.getValue(stage))
                }
            }
        }
    }

    val contributors = Narrative.sleepContributors(session, state.asleepSeconds ?: 0)
    if (session.score != null && contributors.isNotEmpty()) section(index) {
        Panel {
            PanelHeader("Contributors", PhosphorIcons.ChartLineUp, MetricColors.sleepRem)
            contributors.forEach { ContributorRow(it.label, it.rating.label, it.fraction, it.rating.color, it.detail) }
            Text(
                "Ratings use general sleep guidance (7+ hours, about a fifth each of deep and REM sleep). They're a guide, not a medical assessment.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (state.breathRate.isNotEmpty()) section(index, FocusTarget.Breathing) {
        Panel(highlight = MetricColors.hrv.takeIf { f.isHighlighted(FocusTarget.Breathing) }) {
            PanelHeader("Breathing rate", PhosphorIcons.Wind, MetricColors.hrv)
            BigValue(state.breathRate.avg().toString(), "br/min", caption = "Average during sleep")
            LineChart(state.breathRate, MetricColors.hrv, session.start..session.end, height = 110.dp, gapSeconds = 2 * 3600, reveal = f.reveal(FocusTarget.Breathing), unit = "br/min")
        }
    }

    if (state.naps.isNotEmpty()) section(index) { NapDetailsPanel(state) }

    section(index) {
        var range by rememberSaveable { mutableIntStateOf(0) }
        Panel {
            PanelHeader("Heart rate during sleep", PhosphorIcons.Heart, MetricColors.heart)
            SegmentedTabs(listOf("Sleep", "Whole day"), range, { range = it })
            val window = if (range == 0) session.start..session.end else state.dayStart..state.dayEnd
            val pts = state.heartRate.filter { it.x in window }
            if (pts.isEmpty()) EmptyHint("No heart-rate data in this window.")
            else {
                BigValue(pts.minOf { it.y }.toInt().toString(), "bpm", caption = "Lowest")
                LineChart(pts, MetricColors.heart, window, height = 150.dp, markMin = true, unit = "bpm")
            }
        }
    }
}

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

/** The day's naps on the Sleep tab; tap one to see its stages and heart rate. A lone nap starts open. */
@Composable
fun NapDetailsPanel(state: DayUiState) {
    var open by rememberSaveable(state.date) { mutableLongStateOf(state.naps.singleOrNull()?.start ?: -1L) }
    Panel {
        PanelHeader("Naps", PhosphorIcons.Moon, MetricColors.sleepLight)
        state.naps.forEach { nap ->
            val expanded = open == nap.start
            Row(
                Modifier.fillMaxWidth().clickable { open = if (expanded) -1L else nap.start }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${clock(nap.start)} – ${clock(nap.end)} · ${formatDuration(nap.end - nap.start)}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(if (expanded) "Hide" else "Details", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            if (expanded) NapDetail(nap, state)
        }
    }
}

@Composable
private fun NapDetail(nap: SleepSession, state: DayUiState) {
    val stages = state.napStages[nap.start].orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (stages.isEmpty()) EmptyHint("The strap didn't record stages for this nap.")
        else {
            Hypnogram(stages, SLEEP_COLORS, DayViewModel.SLEEP_STAGE_ROWS, height = 120.dp, stageNames = SLEEP_NAMES)
            state.napBreakdowns[nap.start]?.let { b ->
                val total = (b.asleepSeconds + b.awakeSeconds).coerceAtLeast(1)
                SLEEP_NAMES.forEach { (stage, label) ->
                    val secs = b.byStage[stage] ?: 0L
                    StageRow(label, secs, secs.toFloat() / total, SLEEP_COLORS.getValue(stage))
                }
            }
        }
        // The strap reports no average for naps, so it comes from the day's heart rate.
        val hr = state.heartRate.filter { it.x in nap.start..nap.end }
        if (hr.isNotEmpty()) {
            Text("Heart rate", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LineChart(hr, MetricColors.heart, nap.start..nap.end, height = 110.dp, markMin = true, unit = "bpm")
        }
    }
}
