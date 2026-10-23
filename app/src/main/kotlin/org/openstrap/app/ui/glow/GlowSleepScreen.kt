package org.openstrap.app.ui.glow

import androidx.compose.foundation.layout.size
import org.openstrap.app.ui.theme.PhosphorIcons
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.rotate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.openstrap.app.data.Summaries
import org.openstrap.app.data.db.SleepSession
import org.openstrap.app.ui.components.ChartPoint
import org.openstrap.app.ui.components.StageSpan
import org.openstrap.app.ui.components.formatDuration
import org.openstrap.app.ui.dashboard.DayUiState
import org.openstrap.app.ui.dashboard.DayViewModel
import org.openstrap.app.ui.dashboard.LocalGoals
import org.openstrap.app.ui.dashboard.Metric
import org.openstrap.app.ui.dashboard.Narrative
import org.openstrap.app.ui.dashboard.SLEEP_COLORS
import org.openstrap.app.ui.dashboard.SLEEP_NAMES
import org.openstrap.app.ui.dashboard.SleepVital
import org.openstrap.app.ui.dashboard.SleepWindows
import org.openstrap.app.ui.dashboard.bandColor
import org.openstrap.app.ui.dashboard.isOff
import org.openstrap.app.ui.dashboard.minutesClock
import org.openstrap.app.ui.dashboard.sleepVitals
import org.openstrap.app.ui.dashboard.sleepWeek
import org.openstrap.app.ui.theme.MetricColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * The Sleep tab in the glow language: "how did I sleep and is it trending right". The night glows, score and
 * stages together; how it broke down, what drove the score, your vitals while asleep, naps and the week sit in
 * quiet dot panels underneath.
 */
@Composable
fun GlowSleepScreen(
    state: DayUiState,
    /** The tab's header, or a back header when the screen is opened from a card on another tab. */
    header: @Composable () -> Unit,
    contentPadding: PaddingValues,
    onOpenMetric: (Metric) -> Unit,
    /** Readiness and the nightly model, for the Breathing card and the sleep-health panels. */
    recovery: org.openstrap.app.data.RecoveryResult? = null,
) {
    val list = rememberLazyListState()
    val rec = recovery?.takeIf { it.date == state.date }
    CompositionLocalProvider(LocalGlowPaused provides { list.isScrollInProgress }) {
        GlowScreenList(contentPadding, list) {
            full { header() }
            val session = state.sleep
            // Wide: the night, its stages and vitals on the left; what shaped the score and the night's charts on the right.
            left { NightCard(state) }
            if (session != null) {
                state.sleepBreakdown?.let { b -> left { StagesPanel(b) } }
                val waso = org.openstrap.app.data.Recovery.wasoMinutes(state.sleepStages.map { Triple(it.start, it.end, it.stage) })
                val contributors = Narrative.sleepContributors(
                    session, state.asleepSeconds ?: 0, rec?.sleepNeed ?: org.openstrap.app.data.Recovery.SLEEP_NEED_MINUTES, waso,
                )
                if (session.score != null && contributors.isNotEmpty()) right { ContributorsPanel(contributors) }
                val vitals = sleepVitals(state)
                if (vitals.isNotEmpty()) left { VitalsPanel(vitals, onOpenMetric) }
                right { NightChartsPanel(state, session) }
            }
            if (rec?.regularity != null) right { SleepHealthPanel(rec.regularity, rec.sleepHealth) }
            rec?.breathingDisturbances?.let { b -> right { BreathingCard(b) } }
            if (state.naps.isNotEmpty()) right { NapsPanel(state) }
            full { WeekPanel(state, rec?.regularity) }
        }
    }
}

/* ------------------------------------------------------------------------------------------------ */

/** Sleep glows midnight blue. Shared with Today's sleep card. */
internal val SleepGlow = GlowStyle.Midnight.copy(
    color = MetricColors.sleepDeep,
    coreX = 0.5f, coreY = 0.6f, coreWidth = 1.1f, coreHeight = 0.9f,
)

private fun clock(epoch: Long) = Instant.ofEpochSecond(epoch).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))

/**
 * The night. The score sits centred inside its arc (time asleep against
 * your goal when there's no score); then the headline, asleep / in bed / woke, and the stages.
 * The one centred card: it's the tab's hero.
 */
@Composable
private fun NightCard(state: DayUiState) {
    val session = state.sleep
    val goal = LocalGoals.current.sleepSeconds
    GlowCard(SleepGlow, Modifier.fillMaxWidth()) {
        Column {
            GlowHeader("Sleep", trailing = session?.let { "${clock(it.start)} – ${clock(it.end)}" })
            if (session == null) {
                Spacer(Modifier.height(GlowLayout.TitleGap))
                Text(
                    if (state.naps.isEmpty()) "No sleep recorded for this night. Wear the strap to bed and sync in the morning."
                    else "No night sleep recorded, only naps.",
                    color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium,
                )
                return@Column
            }
            val asleep = state.asleepSeconds ?: (session.end - session.start)
            val score = session.score
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.BottomCenter) {
                    GlowHorizon(score?.let { it / 100f } ?: (asleep.toFloat() / goal), Color.White, height = 100.dp)
                    DotMatrixText(
                        score?.toString() ?: "%d:%02d".format(asleep / 3600, asleep % 3600 / 60),
                        Modifier.padding(bottom = 6.dp), dot = 5.5.dp, gap = 2.dp,
                    )
                }
                // The score's band in Zepp's terms (codemap 06 §4.2), in its colour, after the label.
                val rating = score?.let { Narrative.sleepRating(it) }
                Text(
                    if (rating != null) buildAnnotatedString {
                        append("Sleep score · ")
                        withStyle(SpanStyle(color = rating.color)) { append(rating.label) }
                    } else AnnotatedString("asleep · goal ${formatDuration(goal)}"),
                    color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    Narrative.sleepHeadline(session, asleep) ?: "${formatDuration(asleep)} asleep",
                    color = Color.White, style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    listOfNotNull(
                        if (score != null) "${formatDuration(asleep)} asleep" else null,
                        "${formatDuration(session.end - session.start)} in bed",
                        session.wakeCount?.let { "woke $it×" },
                    ).joinToString(" · "),
                    color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall,
                )
            }
            if (state.sleepStages.isNotEmpty()) {
                GlowChartGap()
                GlowSleepStages(state.sleepStages, GlowLayout.ChartHeight)
            }
        }
    }
}

/** Stages as the glow hypnogram, rows Awake to Deep, with start and end times underneath. Shared with Today. */
@Composable
internal fun GlowSleepStages(spans: List<StageSpan>, height: Dp) {
    val start = spans.minOf { it.start }
    val end = spans.maxOf { it.end }.coerceAtLeast(start + 1)
    val rows = DayViewModel.SLEEP_STAGE_ROWS
    val byRow = rows.entries.associate { it.value to (SLEEP_NAMES[it.key] ?: "") }
    val names = (0 until rows.size).map { byRow[it] ?: "" }
    val stages = remember(spans) {
        spans.map { GlowStage((it.start - start).toFloat() / (end - start), (it.end - start).toFloat() / (end - start), rows[it.stage] ?: 0) }
    }
    fun at(f: Float) = clock(start + ((end - start) * f).toLong())
    GlowHypnogram(stages, names, height = height, labels = listOf(clock(start), clock(end))) { st ->
        val mins = (((end - start) * (st.to - st.from)) / 60).toInt().coerceAtLeast(1)
        "${names[st.row]} · ${at(st.from)}–${at(st.to)} · $mins min"
    }
}

/** Minutes and share of each stage, as dot bars in the stage's colour. */
@Composable
private fun StagesPanel(b: Summaries.SleepBreakdown) {
    DotPanel {
        DotHeader("Stages")
        StageRows(b)
    }
}

@Composable
private fun StageRows(b: Summaries.SleepBreakdown) {
    val total = (b.asleepSeconds + b.awakeSeconds).coerceAtLeast(1)
    SLEEP_NAMES.forEach { (stage, label) ->
        val secs = b.byStage[stage] ?: 0L
        val color = SLEEP_COLORS.getValue(stage)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.padding(end = 8.dp).width(7.dp).height(7.dp)) { drawCircle(color) }
                Text(label, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(formatDuration(secs), color = Color.White, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${secs * 100 / total}%", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 10.dp).width(34.dp),
                )
            }
            GlowBar(secs.toFloat() / total, color)
        }
    }
}

@Composable
private fun ContributorsPanel(contributors: List<Narrative.Contributor>) {
    DotPanel {
        DotHeader("What shaped the score")
        contributors.forEach { c ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(c.label, color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(c.rating.label, color = c.rating.color, style = MaterialTheme.typography.titleSmall)
                }
                Note(c.detail)
                GlowBar(c.fraction, c.rating.color)
            }
        }
        Note("Ratings use general sleep guidance (7 to 9½ hours, under 30 minutes awake). They're a guide, not a medical assessment.")
    }
}

/** Vitals during sleep as a two-column grid; each opens its detail. */
@Composable
private fun VitalsPanel(vitals: List<SleepVital>, onOpen: (Metric) -> Unit) {
    DotPanel {
        DotHeader("Sleep vitals", trailing = "During sleep")
        TileGrid(vitals) { v, m -> VitalTile(v, m) { onOpen(v.metric) } }
    }
}

@Composable
private fun VitalTile(v: SleepVital, modifier: Modifier, onClick: () -> Unit) {
    GlowTile(v.label, v.accent, onClick, modifier) {
        Text(v.value, color = Color.White, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
        v.note?.let {
            if (v.isOff) StatusPill(it.removeSuffix(" than usual"), TrendBad, Modifier.padding(top = 4.dp))
            else Text(it, color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Heart rate and breathing across the sleep, each a line in its metric colour on the black panel. */
@Composable
private fun NightChartsPanel(state: DayUiState, session: SleepSession) {
    val window = session.start..session.end
    val hr = state.sleepHeartRate.filter { it.x in window }
    val breath = state.breathRate.filter { it.x in window }
    if (hr.isEmpty() && breath.isEmpty()) return
    val labels = listOf(clock(session.start), clock(session.end))
    DotPanel {
        if (hr.isNotEmpty()) {
            DotHeader("Heart rate", trailing = "Lowest ${hr.minOf { it.y }.toInt()} · avg ${hr.map { it.y }.average().toInt()} bpm")
            val values = remember(hr, window) { evenly(hr, window, 5 * 60L) }
            GlowLineChart(values, height = 110.dp, labels = labels, color = MetricColors.heart) { i, v ->
                "${v.toInt()} bpm · ${clock(window.first + i * 5 * 60L)}"
            }
        }
        if (hr.isNotEmpty() && breath.isNotEmpty()) DotDivider()
        if (breath.isNotEmpty()) {
            DotHeader("Breathing", trailing = "avg %.1f br/min".format(breath.map { it.y }.average()))
            val values = remember(breath, window) { evenly(breath, window, 5 * 60L) }
            GlowLineChart(values, height = 90.dp, labels = labels, color = MetricColors.sleepLight) { i, v ->
                "${v.toInt()} br/min · ${clock(window.first + i * 5 * 60L)}"
            }
        }
    }
}

/** Averages timed points into evenly spaced buckets across [range]; empty buckets are gaps. */
private fun evenly(points: List<ChartPoint>, range: LongRange, seconds: Long): List<Float?> {
    val n = ((range.last - range.first + 1) / seconds).toInt().coerceAtLeast(2)
    val sums = FloatArray(n)
    val counts = IntArray(n)
    points.forEach {
        val i = ((it.x - range.first) / seconds).toInt()
        if (i in 0 until n) { sums[i] += it.y; counts[i]++ }
    }
    return List(n) { if (counts[it] == 0) null else sums[it] / counts[it] }
}

/** The day's naps; tap one to see its stages and heart rate. A lone nap starts open. */
@Composable
private fun NapsPanel(state: DayUiState) {
    var open by rememberSaveable(state.date) { mutableLongStateOf(state.naps.singleOrNull()?.start ?: -1L) }
    DotPanel {
        DotHeader("Naps", trailing = formatDuration(state.naps.sumOf { it.end - it.start }))
        state.naps.forEachIndexed { i, nap ->
            if (i > 0) DotDivider()
            val expanded = open == nap.start
            Row(
                Modifier.fillMaxWidth().glass(GlassRowShape, { open = if (expanded) -1L else nap.start }).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${clock(nap.start)} – ${clock(nap.end)} · ${formatDuration(nap.end - nap.start)}",
                    color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                )
                val turn by animateFloatAsState(if (expanded) 180f else 0f, label = "nap caret")
                Icon(PhosphorIcons.CaretDown, if (expanded) "Hide" else "Details", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(16.dp).rotate(turn))
            }
            if (expanded) {
                val stages = state.napStages[nap.start].orEmpty()
                if (stages.isEmpty()) Note("The strap didn't record stages for this nap.")
                else {
                    GlowSleepStages(stages, 110.dp)
                    state.napBreakdowns[nap.start]?.let { StageRows(it) }
                }
                // The strap reports no average for naps, so it comes from the day's heart rate.
                val hr = state.heartRate.filter { it.x in nap.start..nap.end }
                if (hr.isNotEmpty()) {
                    val values = remember(hr) { evenly(hr, nap.start..nap.end, 5 * 60L) }
                    Note("Heart rate · lowest ${hr.minOf { it.y }.toInt()} bpm")
                    GlowLineChart(values, height = 80.dp, labels = listOf(clock(nap.start), clock(nap.end)), color = MetricColors.heart)
                }
            }
        }
    }
}

/**
 * The last seven nights on a shared clock, the week's facts and how regular the week was: the Sleep Regularity Index
 * ([regularity], from the selected day's recovery; null while it loads).
 */
@Composable
private fun WeekPanel(state: DayUiState, regularity: org.openstrap.app.data.SleepRegularity.Result?) {
    val zone = ZoneId.systemDefault()
    val goal = LocalGoals.current.sleepSeconds
    val w = sleepWeek(state, goal, regularity, zone)
    DotPanel {
        DotHeader("This week", trailing = "Last 7 nights")
        if (w == null) {
            Note("No sleep recorded in the last 7 nights.")
            return@DotPanel
        }
        SleepWindows(w.nights, w.usual, zone)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Fact("Avg. asleep", w.averageAsleep)
            Fact("Avg. score", w.averageScore)
            Fact("Reached ${formatDuration(goal)}", "${w.goalNights} of ${w.slept}")
        }
        val sri = w.sri
        val band = w.band
        DotDivider()
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Fact("Regularity", sri?.let { "${it.roundToInt()}" } ?: "—")
                if (band != null) Text(band.label, color = bandColor(band), style = MaterialTheme.typography.bodySmall)
            }
            w.usual?.let { u ->
                Column(horizontalAlignment = Alignment.End) {
                    Note("Fall asleep ${minutesClock(u.bedtime)}")
                    Note("Wake up ${minutesClock(u.wake)}")
                }
            }
        }
        if (sri != null && band != null) GlowBar(sri.coerceIn(0f, 100f) / 100f, bandColor(band))
        (w.regularityNote ?: w.learningNote)?.let { Note(it) }
    }
}

/**
 * Breathing disturbances: the strap's count of oxygen-desaturation events per hour asleep, against your own nights.
 * No normal / mild / moderate / severe words: those are apnea severity bands, and this is a wellness estimate.
 */
@Composable
private fun BreathingCard(b: org.openstrap.app.data.Recovery.BreathingDisturbances) {
    DotPanel {
        DotHeader("Breathing", trailing = "vs. your nights")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Fact("Disturbances", "%.1f /h".format(b.perHour))
            Fact("Your usual", b.usual?.let { "%.1f /h".format(it) } ?: "—")
        }
        Note(
            if (b.usual == null) "Your usual builds after ${org.openstrap.app.data.Recovery.SCORED_NIGHTS} nights with a breathing summary."
            else "Dips in blood oxygen during sleep, counted by the strap. They are fairly steady for each person, so they're shown here rather than in Recovery. " +
                org.openstrap.app.data.BodySignalsCopy.DISCLAIMER,
        )
    }
}

/**
 * Sleep health: how regular the last week was (the Sleep Regularity Index) and how many of three dimensions were good
 * last night. Shown here, not in Recovery. Timing is informational only.
 */
@Composable
private fun SleepHealthPanel(reg: org.openstrap.app.data.SleepRegularity.Result, health: org.openstrap.app.data.SleepRegularity.Health?) {
    DotPanel {
        DotHeader("Sleep health", trailing = health?.let { "${it.count} of 3 good" })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Fact("Regularity", reg.sri?.let { "${it.roundToInt()}" } ?: reg.midpointSdMinutes?.let { "±${it.toInt()} min" } ?: "—")
            Fact("Band", reg.band?.label ?: if (reg.midpointSdMinutes != null) (if (reg.good == true) "Steady" else "Variable") else "—")
        }
        Note(
            when {
                reg.sri != null -> "How alike your sleep and wake times were, minute by minute, across the last 7 days (${reg.validPairs / 60} h compared)."
                reg.midpointSdMinutes != null -> "Not enough wear for the regularity index yet, so this is how much your sleep's midpoint moved this week."
                else -> "Regularity needs about 5 days of wear around the clock, night and day."
            },
        )
        if (health != null) {
            DotDivider()
            fun word(v: Boolean?) = when (v) { true -> "Good"; false -> "Not quite"; null -> "—" }
            HealthRow("Duration", word(health.duration))
            HealthRow("Continuity", word(health.continuity))
            HealthRow("Regularity", word(health.regularity))
            Note(
                "Midpoint of your sleep ${org.openstrap.app.data.SleepRegularity.clock(health.midpointMinutes)}" +
                    if (health.midpointOutside) ", outside 01:30–04:30. Shown for interest; it isn't counted." else ".",
            )
        }
    }
}

@Composable
private fun HealthRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(label, color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.titleSmall)
    }
}
