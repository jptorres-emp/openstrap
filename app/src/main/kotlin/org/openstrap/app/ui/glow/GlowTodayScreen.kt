package org.openstrap.app.ui.glow

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.openstrap.app.data.RecoveryResult
import org.openstrap.app.device.SyncState
import org.openstrap.app.ui.components.ChartPoint
import org.openstrap.app.ui.components.formatDuration
import org.openstrap.app.ui.dashboard.DateHeader
import org.openstrap.app.ui.dashboard.DayPicker
import org.openstrap.app.ui.dashboard.DayUiState
import org.openstrap.app.ui.dashboard.DayViewModel
import org.openstrap.app.ui.dashboard.Metric
import org.openstrap.app.ui.dashboard.Narrative
import org.openstrap.app.ui.dashboard.SLEEP_NAMES
import org.openstrap.app.ui.dashboard.STEP_GOAL
import org.openstrap.app.ui.dashboard.ScreenList
import org.openstrap.app.ui.dashboard.avg
import org.openstrap.app.ui.dashboard.chargeLeads
import org.openstrap.app.ui.theme.MetricColors

/**
 * Today in the glow language: the home screen answers "how am I, what changed, what should I do".
 * Each thing appears once. The verdict leads; in the morning last night comes next, later in the day
 * your heart and movement do. Key statistics list only what moved away from your normal. Only a few
 * cards glow; the rest is black with dots, so the glow keeps meaning "look here".
 */
@Composable
fun GlowTodayScreen(
    state: DayUiState,
    liveHeartRate: Int?,
    battery: Int?,
    charging: Boolean = false,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    contentPadding: PaddingValues,
    picker: DayPicker = DayPicker(),
    onOpenSleep: () -> Unit = {},
    onOpenMetric: (Metric) -> Unit = {},
    recovery: RecoveryResult? = null,
    onOpenRecovery: () -> Unit = {},
    sync: SyncState.Running? = null,
    workouts: List<org.openstrap.app.data.WorkoutAnalysis> = emptyList(),
    onOpenWorkout: (Long) -> Unit = {},
) {
    // The glow holds still while the list scrolls, so scrolling stays as smooth as on other screens.
    val list = rememberLazyListState()
    CompositionLocalProvider(LocalGlowPaused provides { list.isScrollInProgress }) {
        TodayList(state, liveHeartRate, battery, charging, onPrev, onNext, contentPadding, picker, onOpenSleep, onOpenMetric, recovery, onOpenRecovery, sync, workouts, onOpenWorkout, list)
    }
}

@Composable
private fun TodayList(
    state: DayUiState,
    liveHeartRate: Int?,
    battery: Int?,
    charging: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    contentPadding: PaddingValues,
    picker: DayPicker,
    onOpenSleep: () -> Unit,
    onOpenMetric: (Metric) -> Unit,
    recovery: RecoveryResult?,
    onOpenRecovery: () -> Unit,
    sync: SyncState.Running?,
    workouts: List<org.openstrap.app.data.WorkoutAnalysis>,
    onOpenWorkout: (Long) -> Unit,
    list: LazyListState,
) = ScreenList(contentPadding, list, gutter = GlowLayout.Gutter) {
    val rec = recovery?.takeIf { it.date == state.date }
    item { DateHeader(state.date, battery, onPrev, onNext, charging, picker, sync) }

    // Glow: the day's three scores at a glance, the leading one as the hero. The only place they appear.
    item { OverviewCard(state, rec, chargeLeads(state, rec), onOpenRecovery) }

    // Dark grey glow: last night, naps included. Right after waking it's what you want to know, so it leads.
    val hasSleep = state.asleepSeconds != null || state.naps.isNotEmpty()
    val morning = state.date == LocalDate.now() && LocalTime.now().hour < 12
    if (hasSleep && morning) item { SleepPanel(state, onOpenSleep) }

    // Quiet: what the readiness score means, then only the signals that moved away from your normal.
    item { WhatChanged(state, rec?.readiness?.let { Narrative.readinessBody(it) }, onOpenMetric) }

    // Glow: heart rate, beating at the latest reading.
    item { HeartGlow(state, liveHeartRate) { onOpenMetric(Metric.HeartRate) } }

    // Glow: activity, moving with the day's steps.
    item { ActivityGlow(state) }
    val dayWorkouts = workouts.filter { it.workout.start in state.dayStart..state.dayEnd }.sortedBy { it.workout.start }
    if (dayWorkouts.isNotEmpty()) item { org.openstrap.app.ui.workout.WorkoutsPanel(dayWorkouts, onOpenWorkout) }

    if (hasSleep && !morning) item { SleepPanel(state, onOpenSleep) }
}

/* ------------------------------------------------------------------------------------------------ */

private val HOURS = listOf("00", "06", "12", "18", "24")
private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")
private fun clock(epoch: Long) = Instant.ofEpochSecond(epoch).atZone(ZoneId.systemDefault()).format(CLOCK)

/** Averages timed points into evenly spaced buckets across [range]; empty buckets are null (gaps). */
private fun bucket(points: List<ChartPoint>, range: LongRange, seconds: Long): List<Float?> {
    val n = ((range.last - range.first + 1) / seconds).toInt().coerceAtLeast(2)
    val sums = FloatArray(n)
    val counts = IntArray(n)
    points.forEach {
        val i = ((it.x - range.first) / seconds).toInt()
        if (i in 0 until n) { sums[i] += it.y; counts[i]++ }
    }
    return List(n) { if (counts[it] == 0) null else sums[it] / counts[it] }
}

/**
 * The top card, built like every glow card: the leading score as title and hero number with how it
 * moved, the other two as facts beside it, and the three scores as nested half arcs underneath (the
 * rings laid flat), outside in: leader, second score, sleep.
 */
@Composable
private fun OverviewCard(state: DayUiState, rec: RecoveryResult?, chargeLeads: Boolean, onClick: () -> Unit) {
    val c = rec?.charge
    val chargeNow = c?.latest?.toInt()
    val readinessNow = rec?.readiness?.score
    val readinessBefore = rec?.readinessTrend?.dropLast(1)?.lastOrNull()?.second
    val sleepNow = state.sleep?.score
    val charge = Score("Charge", chargeNow, MetricColors.charge, chargeNow.minus(c?.atWake?.toInt()), "since waking")
    val recovery = Score("Recovery", readinessNow, MetricColors.readiness, readinessNow.minus(readinessBefore), "vs yesterday")
    val sleep = Score("Sleep", sleepNow, MetricColors.sleepLight, sleepNow.minus(state.previousSleepScore), "vs night before")
    val (lead, second) = if (chargeLeads) charge to recovery else recovery to charge
    val strain = rec?.readiness?.strainAlert == true

    // The glow sits low and central, under the arcs.
    val style = GlowStyle.Bloom.copy(
        color = overviewColor(lead.value, strain),
        coreShape = CoreShape.Radial, coreX = 0.5f, coreY = 0.8f, coreWidth = 0.95f, coreHeight = 0.75f,
        coreBlur = 30.dp, toneWidth = 16.dp, toneShift = -10f, glowStrength = 0.6f, bevel = 0.6f,
        motion = GlowMotion.Breath(5f), depth = 6.dp,
    )
    GlowCard(style, Modifier.fillMaxWidth(), onClick = onClick) {
        Column {
            GlowHeader(
                lead.label,
                trailing = if (strain) "Signs of strain" else null,
                trailingContent = if (strain) null else ({ Change(lead, withNote = true) }),
            )
            GlowHeroNumber(lead.value?.toString() ?: "-", "/ 100") {
                // A small table: dots, labels, values and changes each line up in their own column.
                Column(Modifier.width(IntrinsicSize.Max), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FactScore(second)
                    FactScore(sleep)
                }
            }
            GlowChartGap()
            GlowHalfArcs(listOf(lead, second, sleep).map { (it.value ?: 0) / 100f to it.color }, height = GlowLayout.ChartHeight)
        }
    }
}

private fun Int?.minus(other: Int?): Int? = if (this == null || other == null) null else this - other

/** A score on the overview, with how far it moved and against what. */
private class Score(val label: String, val value: Int?, val color: Color, val change: Int?, val against: String)

/** How a score moved: a dot arrow (green up, amber down) and the amount; "steady" when it didn't. */
@Composable
private fun Change(score: Score, withNote: Boolean) {
    val d = score.change ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (d != 0) {
            DotArrow(d > 0, if (d > 0) TrendGood else TrendBad)
            Spacer(Modifier.width(6.dp))
        }
        val amount = if (d == 0) "Steady" else "${kotlin.math.abs(d)}"
        Text(
            if (withNote) "$amount ${score.against}" else amount,
            color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** One of the other two scores, as a fact beside the hero: its arc's dot, label, value and change. */
@Composable
private fun FactScore(score: Score) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // The same glowing dot as the end of this score's arc, so it reads as the legend.
        Canvas(Modifier.size(13.dp)) {
            drawCircle(score.color.copy(alpha = 0.25f), size.minDimension / 2)
            drawCircle(score.color, size.minDimension * 0.27f)
        }
        Spacer(Modifier.width(8.dp))
        Text(score.label, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        Text(
            "${score.value ?: "-"}", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.End, modifier = Modifier.width(24.dp),
        )
        Box(Modifier.padding(start = 10.dp).width(36.dp)) { Change(score, withNote = false) }
    }
}

/**
 * The card's colour tells how the day looks before you read a number: teal when the leading score is
 * good, amber when it's middling or there are signs of strain, coral when it's low. The rings sit on
 * the dark core, so they stay readable whatever the colour.
 */
private fun overviewColor(score: Int?, strain: Boolean) = when {
    score == null -> Color(0xFF6E7280)
    strain -> Color(0xFFF2A33A)
    score >= 70 -> MetricColors.hrv
    score >= 50 -> Color(0xFFF2A33A)
    else -> Color(0xFFF0605A)
}

@Composable
private fun HeartGlow(state: DayUiState, live: Int?, onClick: () -> Unit) {
    val now = live ?: state.latestHeartRate
    val style = GlowStyle.chart(MetricColors.heart).copy(motion = GlowMotion.Heartbeat(now ?: 62))
    GlowCard(style, Modifier.fillMaxWidth(), onClick = onClick) {
        Column {
            // A live reading needs no label (the beating glow says it); an older one says so.
            GlowHeader("Heart rate", trailing = if (live == null) "Latest" else null)
            if (now != null) GlowHeroNumber("$now", "bpm", listOfNotNull(state.restingHeartRate?.let { "Resting $it" }))
            GlowChartGap()
            if (state.heartRate.isEmpty()) {
                Text("No heart-rate data yet. Sync your strap.", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyMedium)
            } else {
                val values = remember(state.heartRate, state.dayStart) { bucket(state.heartRate, state.dayStart..state.dayEnd, 5 * 60) }
                GlowLineChart(values, height = GlowLayout.ChartHeight, labels = HOURS) { i, v -> "${v.toInt()} bpm · ${clock(state.dayStart + i * 5 * 60L)}" }
            }
        }
    }
}

/** One key statistic against its 7-day baseline. */
private class Signal(
    val label: String, val accent: Color, val value: Float?, val baseline: Float?, val unit: String,
    val decimals: Int, val tolerance: Float, val higherIsBetter: Boolean?, val target: Metric,
) {
    val direction get() = Narrative.direction(value, baseline, tolerance)
    fun fmt(v: Float?) = v?.let { "%.${decimals}f".format(it) }
}

/**
 * Key statistics against your normal, the same ones as the classic Today: each value with its 7-day
 * baseline, and an arrow on the ones that moved. A tile shows as soon as there is a value or a baseline.
 */
@Composable
private fun WhatChanged(state: DayUiState, advice: String?, onOpenMetric: (Metric) -> Unit) {
    val b = state.baselines
    val signals = listOfNotNull(
        Signal("Resting HR", MetricColors.heart, state.restingHeartRate?.toFloat(), b.restingHr, "bpm", 0, 1.5f, false, Metric.RestingHr),
        Signal("HRV", MetricColors.hrv, state.hrv.avg()?.toFloat(), b.hrv, "ms", 0, 3f, true, Metric.Hrv),
        Signal("Blood oxygen", MetricColors.spo2, state.spo2.avg()?.toFloat(), b.spo2, "%", 0, 1f, true, Metric.Spo2),
        Signal(
            "Skin temp", MetricColors.temperature, state.skinTemperature.takeIf { it.isNotEmpty() }?.map { it.y }?.average()?.toFloat(),
            b.skinTemperature, "°C", 1, 0.3f, null, Metric.SkinTemperature,
        ),
        Signal("Stress", MetricColors.stress, state.stress.avg()?.toFloat(), b.stress, "", 0, 4f, false, Metric.Stress),
        Signal("Breathing", MetricColors.sleepLight, state.breathRate.avg()?.toFloat(), b.breathRate, "/min", 0, 1f, null, Metric.Breathing),
    ).filter { it.value != null || it.baseline != null }
    if (signals.isEmpty() && advice.isNullOrEmpty()) return

    DotPanel {
        DotHeader("What changed", Color.White.copy(alpha = 0.7f), trailing = "vs. your last 7 days")
        if (!advice.isNullOrEmpty()) Text(advice, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
        // Two columns, so six statistics take three rows instead of a screenful.
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            signals.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    pair.forEach { sg ->
                        DotStatTile(
                            sg.label, sg.accent, sg.fmt(sg.value), sg.unit, sg.fmt(sg.baseline), sg.direction, sg.higherIsBetter,
                            Modifier.weight(1f),
                        ) { onOpenMetric(sg.target) }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        if (signals.isNotEmpty() && signals.all { it.baseline == null }) {
            Text("Your normal builds over the first week of wear.", color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ActivityGlow(state: DayUiState) {
    val progress = state.steps.toFloat() / STEP_GOAL
    val style = GlowStyle.chart(MetricColors.steps).copy(motion = GlowMotion.Stride(energy = progress.coerceIn(0f, 1f)))
    GlowCard(style, Modifier.fillMaxWidth()) {
        Column {
            GlowHeader("Activity")
            // How far along the day's goal is; the hourly chart below shows when you moved.
            val goal = if (state.steps >= STEP_GOAL) "Goal reached" else "%d%% of %,d".format(state.steps * 100 / STEP_GOAL, STEP_GOAL)
            GlowHeroNumber("${state.steps}", "steps", listOf(goal))
            GlowChartGap()
            GlowDotBars(state.hourlySteps, height = GlowLayout.ChartHeight, labels = HOURS, accent = MetricColors.steps) { h, v ->
                "%02d:00–%02d:00 · %,d steps".format(h, (h + 1) % 24, Math.round(v))
            }
        }
    }
}

/** Sleep glows midnight blue, the same as the Design lab's sleep card, breathing slowly. */
private val SleepGlow = GlowStyle.Midnight.copy(
    color = MetricColors.sleepDeep,
    coreX = 0.5f, coreY = 0.6f, coreWidth = 1.1f, coreHeight = 0.9f,
    motion = GlowMotion.Breath(5f),
)

@Composable
private fun SleepPanel(state: DayUiState, onClick: () -> Unit) {
    val session = state.sleep
    GlowCard(SleepGlow, Modifier.fillMaxWidth(), onClick = onClick) {
    Column {
        GlowHeader("Sleep", trailing = session?.let { "${clock(it.start)} – ${clock(it.end)}" }, chevron = true)
        state.asleepSeconds?.let { s ->
            GlowHeroNumber(
                "%d:%02d".format(s / 3600, (s % 3600) / 60), "asleep",
                session?.score?.let { listOf("Score $it", Narrative.sleepHeadline(it)) } ?: emptyList(),
            )
        }
        val spans = state.sleepStages
        if (spans.isNotEmpty()) {
            val start = spans.minOf { it.start }
            val end = spans.maxOf { it.end }.coerceAtLeast(start + 1)
            val rows = DayViewModel.SLEEP_STAGE_ROWS
            val byRow = rows.entries.associate { it.value to (SLEEP_NAMES[it.key] ?: "") }
            val names = (0 until rows.size).map { byRow[it] ?: "" }
            val stages = remember(spans) {
                spans.map { GlowStage((it.start - start).toFloat() / (end - start), (it.end - start).toFloat() / (end - start), rows[it.stage] ?: 0) }
            }
            fun at(f: Float) = clock(start + ((end - start) * f).toLong())
            GlowChartGap()
            GlowHypnogram(stages, names, height = GlowLayout.ChartHeight, labels = listOf(clock(start), clock(end))) { st ->
                val mins = (((end - start) * (st.to - st.from)) / 60).toInt().coerceAtLeast(1)
                "${names[st.row]} · ${at(st.from)}–${at(st.to)} · $mins min"
            }
        }
        if (state.naps.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            DotDivider()
            Spacer(Modifier.height(14.dp))
            val total = state.naps.sumOf { it.end - it.start }
            Text(
                "${if (state.naps.size == 1) "1 nap" else "${state.naps.size} naps"} · ${formatDuration(total)} · " +
                    state.naps.joinToString(", ") { clock(it.start) },
                color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    }
}
