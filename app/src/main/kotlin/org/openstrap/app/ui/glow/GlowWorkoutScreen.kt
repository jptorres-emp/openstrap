package org.openstrap.app.ui.glow

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import org.openstrap.app.data.WorkoutAnalysis
import org.openstrap.app.data.WorkoutRecorder
import org.openstrap.app.data.Workouts
import org.openstrap.app.ui.components.rememberScrubHaptics
import org.openstrap.app.ui.components.scrub
import org.openstrap.app.ui.dashboard.Metric
import org.openstrap.app.ui.dashboard.Score
import org.openstrap.app.ui.dashboard.ScreenList
import org.openstrap.app.ui.theme.AppIcons
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.app.ui.workout.EffectFigures
import org.openstrap.app.ui.workout.PhoneSummary
import org.openstrap.app.ui.workout.WorkoutDetailCopy
import org.openstrap.app.ui.workout.WorkoutFact
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/*
 * The page of a past workout (spec 6.3, "Workout detail"), one for the strap's and the phone's: the sentence first on
 * a glow as big as the effort, the heart rate in its zones' colours with the drop after the end, the small facts and
 * the map, Load with time to recover and the heart rate drop, time in zones and the splits. Training effect and EPOC
 * sit behind the (i) on the Load card. A workout row grows into this page (spec 3.5).
 */

/** The heart rate line is drawn in ten-second steps: readable on a phone, and the card still says the time. */
private const val HeartStep = 10L

/** What deleting a strap workout does. */
private const val STRAP_DELETE =
    "Its heart rate and times are removed from OpenStrap, and the next sync won't bring it back. The strap's own history is kept."

/**
 * The workout [a]. A phone workout shows from its [summary] (route, laps, sets and the whole recording, for trimming),
 * a strap workout from [a] alone. [workouts] are all of them, for your usual Load, as the rows that open this page
 * use; [usualDrop] your usual heart rate drop. Trim and undo are for phone workouts.
 */
@Composable
fun GlowWorkoutDetailScreen(
    a: WorkoutAnalysis?,
    summary: PhoneSummary?,
    workouts: List<WorkoutAnalysis>,
    usualDrop: Int?,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onTrim: (from: Long, to: Long) -> Unit = { _, _ -> },
    onUndoTrim: () -> Unit = {},
) {
    BackHandler(onBack = onBack)
    val shown = if (a?.fromPhone == true) summary?.analysis else a
    val phone = summary?.takeIf { a?.fromPhone == true }
    val list = rememberLazyListState()
    var info by remember { mutableStateOf(false) }
    val usual = remember(workouts) { Workouts.usualLoad(workouts.map { it.workout.trainingLoad }) }
    // Each kind keeps the zone figures its page always had: a phone workout's live zones, the strap's from its stream.
    val zones = remember(shown) {
        shown?.let { s ->
            if (s.fromPhone) WorkoutRecorder.liveZoneSeconds(s.heartRate.filter { it.timestamp <= s.end }.map { it.timestamp to it.bpm }, s.maxHeartRate).toList()
            else s.zoneSeconds
        }.orEmpty()
    }
    CompositionLocalProvider(LocalGlowPaused provides { list.isScrollInProgress }) {
        ScreenList(contentPadding, list, gutter = GlowLayout.Gutter) {
            item {
                PageHeader(
                    (shown ?: a)?.sportName ?: "Workout", onBack,
                    trailing = if ((shown ?: a)?.startedByYou == false) ({ FoundTag() }) else null,
                )
            }
            if (shown == null) return@ScreenList
            val w = shown.workout
            item { WorkoutHero(shown, usual, zones) }
            if (shown.heartRate.isNotEmpty() || w.averageHeartRate != null) item { HeartRateCard(shown) }
            val facts = WorkoutDetailCopy.facts(
                shown.distanceMeters, shown.distanceFromSteps,
                if (shown.fromPhone) (shown.durationSeconds - shown.activeSeconds).coerceAtLeast(0) else 0L, w.steps, w.calories,
            )
            val factsShown = facts.isNotEmpty() || (phone?.route?.size ?: 0) >= 2
            if (factsShown) item { FactsList(facts, phone) }
            val drop = WorkoutDetailCopy.dropLine(shown.recovery60, shown.recoveryIsMeaningful, usualDrop)
            val loadShown = w.trainingLoad != null || (shown.recoveryMinutes ?: 0) > 0 || drop != null
            if (loadShown) item { LoadCard(shown, usual, best = null, drop = drop, onInfo = { info = true }) }
            // A list right under another would look the same, so the later one sits on a neutral edge card (spec 2.4):
            // the zones when there's no Load card between them and the facts, else the splits under a plain list.
            val zonesShown = zones.sum() > 0
            val zonesOnEdge = zonesShown && factsShown && !loadShown
            val listAbove = if (zonesShown) !zonesOnEdge else factsShown && !loadShown
            if (zonesShown) item {
                CompositionLocalProvider(LocalEdgeOverride provides if (zonesOnEdge) EdgeOverride(edge = Edge.Bottom) else EdgeOverride()) {
                    ZonesList(zones.toIntArray(), shown.maxHeartRate)
                }
            }
            if (phone != null) item {
                CompositionLocalProvider(LocalEdgeOverride provides if (listAbove) EdgeOverride(edge = Edge.Bottom) else EdgeOverride()) {
                    SplitsList(phone, liveRoute = emptyList())
                }
            }
            if (phone != null) item { TrimSection(phone, onTrim, onUndoTrim) }
            item {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (phone != null) SummaryActions(justEnded = false, onDone = onBack, onDelete = onDelete)
                    else DeleteWorkout(STRAP_DELETE, onDelete, Modifier.align(Alignment.CenterHorizontally))
                }
            }
        }
    }
    if (info && shown != null) EffectInfo(shown, usual, phone?.strapWorkout, onDismiss = { info = false })
}

/**
 * The sentence ([WorkoutDetailCopy.sentence]), the day and time range, and the length in dots. Lit from below, the
 * light as high as the effort (spec 2.5): an easy walk leaves most of the card dark, a hard run floods it.
 */
@Composable
private fun WorkoutHero(a: WorkoutAnalysis, usual: Float?, zones: List<Int>) {
    val w = a.workout
    val style = GlowStyle.chart(MetricColors.heart).copy(lightAngle = LightAngle.Below, lightLevel = Workouts.effort(w.trainingLoad, usual))
    GlowCard(style, Modifier.fillMaxWidth()) {
        Column {
            GlowHeader(WorkoutDetailCopy.day(a.shownStart, LocalDate.now()), trailing = "${clockAt(a.shownStart)}–${clockAt(a.end)}")
            HeroSentence(WorkoutDetailCopy.sentence(w.sportType, a.activeSeconds, w.trainingLoad, usual, zones))
            DotMatrixText(workoutClock(a.activeSeconds), Modifier.padding(top = 16.dp), dot = DotSize.Hero)
        }
    }
}

/**
 * Heart rate over the workout in its zones' colours, and the strap's minutes after the end, dimmer, as the drop. The
 * number is the average; hold the line to scrub and it follows the finger (spec 2.7), with the scrub ticks. Lit from
 * the left: about earlier.
 */
@Composable
private fun HeartRateCard(a: WorkoutAnalysis) {
    val w = a.workout
    val first = a.shownStart
    val last = (a.heartRate.maxOfOrNull { it.timestamp } ?: a.end).coerceIn(a.end, a.end + Workouts.RECOVERY_TAIL_S)
    val values = remember(a) {
        val n = ((last - first) / HeartStep + 1).toInt().coerceAtLeast(2)
        val sums = FloatArray(n)
        val counts = IntArray(n)
        a.heartRate.forEach { s ->
            val i = ((s.timestamp - first) / HeartStep).toInt()
            if (i in 0 until n) { sums[i] += s.bpm.toFloat(); counts[i]++ }
        }
        List(n) { if (counts[it] == 0) null else sums[it] / counts[it] }
    }
    var scrubbed by remember { mutableStateOf<Int?>(null) }
    val at = scrubbed?.let { i -> values.getOrNull(i)?.let { v -> first + i * HeartStep to v.roundToInt() } }
    EdgeCard(MetricColors.heart, Modifier.fillMaxWidth(), kind = EdgeKind.Earlier) {
        EdgeTitle("Heart rate", if (a.heartRate.isNotEmpty()) "Coloured by zone" else null)
        if (at != null) {
            EdgeValue(at.second.toString(), "bpm", if (at.first > a.end) "At ${clockAt(at.first)}, after the end" else "At ${clockAt(at.first)}")
        } else {
            val range = listOfNotNull(w.maxHeartRate?.let { "Max $it" }, w.minHeartRate?.let { "lowest $it" }).joinToString(", ")
            EdgeValue(w.averageHeartRate?.toString(), "bpm average", range.ifEmpty { null }?.let { "$it bpm" }, waiting = false)
        }
        if (a.heartRate.isEmpty()) {
            Text(
                "The strap hasn't sent this workout's heart rate yet.", color = Color.White.copy(alpha = GlowType.SECONDARY_ALPHA),
                style = GlowType.secondary, modifier = Modifier.padding(top = 10.dp),
            )
            return@EdgeCard
        }
        ZoneHeartLine(values, first, a.end, a.maxHeartRate, a.historyRanges, scrubbed, { scrubbed = it }, Modifier.padding(top = 16.dp))
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Note(clockAt(first))
            Spacer(Modifier.weight(1f))
            Note(if (last > a.end + HeartStep) "Drop after ${clockAt(a.end)}" else clockAt(a.end))
        }
        if (a.historyRanges.isNotEmpty()) Text(
            "Dimmed: " + a.historyRanges.joinToString(", ") { "${clockAt(it.first)}–${clockAt(it.last + 1)}" } + " came from the strap's history.",
            color = Color.White.copy(alpha = GlowType.SECONDARY_ALPHA), style = GlowType.secondary, modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/**
 * The line itself: each ten-second step in its zone's colour up to the [end], dim white after it with a solid hairline
 * mark at the end; minutes from the strap's [history] dimmed. [scrubbed] is the step under the finger, [onScrub] follows it.
 */
@Composable
private fun ZoneHeartLine(
    values: List<Float?>, first: Long, end: Long, maxHr: Int, history: List<LongRange>,
    scrubbed: Int?, onScrub: (Int?) -> Unit, modifier: Modifier = Modifier,
) {
    var width by remember { mutableStateOf(0f) }
    val tick = rememberScrubHaptics()
    val known = values.filterNotNull()
    val lo = (known.minOrNull() ?: 60f) - 5f
    val hi = (known.maxOrNull() ?: 180f) + 5f
    val endIndex = ((end - first) / HeartStep).toInt()
    Canvas(
        modifier.fillMaxWidth().height(96.dp).onSizeChanged { width = it.width.toFloat() }.scrub(values) { x ->
            val i = x?.let { nearestReading(values, it, width, 0f) }
            tick(i, values, null, true)
            onScrub(i)
        },
    ) {
        val dx = size.width / (values.size - 1).coerceAtLeast(1)
        fun y(v: Float) = size.height - (v - lo) / (hi - lo) * size.height
        if (endIndex in 1 until values.lastIndex) drawLine(
            Color.White.copy(alpha = 0.35f), Offset(endIndex * dx, 0f), Offset(endIndex * dx, size.height), strokeWidth = 1.dp.toPx(),
        )
        for (i in 0 until values.lastIndex) {
            val v0 = values[i] ?: continue
            val v1 = values[i + 1] ?: continue
            val t = first + i * HeartStep
            val color = if (i >= endIndex) Color.White.copy(alpha = 0.55f) else zoneColor(v0.roundToInt(), maxHr)
            drawLine(
                // The strap's history, filled in after: the same line, dimmed (spec 2.7, no dashes).
                if (history.any { t in it }) color.copy(alpha = color.alpha * 0.45f) else color,
                Offset(i * dx, y(v0)), Offset((i + 1) * dx, y(v1)), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round,
            )
        }
        scrubbed?.let { i ->
            val v = values.getOrNull(i) ?: return@let
            val p = Offset(i * dx, y(v))
            drawLine(Color.White.copy(alpha = 0.4f), Offset(p.x, 0f), Offset(p.x, size.height), strokeWidth = 1.dp.toPx())
            drawCircle(Color.Black.copy(alpha = 0.6f), 6.dp.toPx(), p)
            drawCircle(Color.White, 4.dp.toPx(), p)
        }
    }
}

/** The small facts ([WorkoutDetailCopy.facts]) as rows, and the map under them when the route was kept. */
@Composable
private fun FactsList(facts: List<WorkoutFact>, summary: PhoneSummary?) {
    ListPanel(title = "The workout") {
        facts.forEachIndexed { i, f ->
            val color = if (f.name == "Paused") PausedColor else MetricColors.steps
            val icon = when (f.name) {
                "Distance" -> Score.Distance.rowIcon()
                "Paused" -> AppIcons.Hourglass
                "Steps" -> Metric.Steps.rowIcon()
                "Energy" -> Score.Energy.rowIcon()
                else -> null
            }
            ListRow(f.name, color, f.value, f.status, divider = i > 0, icon = icon)
        }
        summary?.let { RouteMap(it, Modifier.padding(top = 6.dp, bottom = 10.dp)) }
    }
}

/** The (i) on the Load card: Load, training effect, time to recover, the heart rate drop and VO2 max explained. */
@Composable
private fun EffectInfo(a: WorkoutAnalysis, usual: Float?, strap: WorkoutAnalysis?, onDismiss: () -> Unit) {
    val w = a.workout
    val sheet = WorkoutDetailCopy.effectSheet(
        aerobic = w.aerobicTrainingEffect, anaerobic = w.anaerobicTrainingEffect, load = w.trainingLoad, usualLoad = usual,
        recoveryMinutes = a.recoveryMinutes, drop60 = a.recovery60, drop120 = a.recovery120, endBpm = a.endHeartRate,
        meaningfulDrop = a.recoveryIsMeaningful, vo2max = a.effect?.vo2max ?: w.vo2max?.toFloat(), fromPhone = a.fromPhone,
        strap = strap?.let { s ->
            EffectFigures(s.workout.aerobicTrainingEffect, s.workout.anaerobicTrainingEffect, s.workout.trainingLoad, s.recoveryMinutes)
        },
    )
    InfoSheet(sheet.info, MetricColors.load, sheet.here, sheet.hereLine, onDismiss, rows = sheet.rows)
}

/** Every workout, newest first, one dot panel per day, each a row as on the Activity tab's Workouts card ([WorkoutRow]). */
@Composable
fun GlowWorkoutsScreen(workouts: List<WorkoutAnalysis>, contentPadding: PaddingValues, onOpen: (Long) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val zone = ZoneId.systemDefault()
    val usual = remember(workouts) { Workouts.usualLoad(workouts.map { it.workout.trainingLoad }) }
    ScreenList(contentPadding, gutter = GlowLayout.Gutter) {
        item { PageHeader("Workouts", onBack, subtitle = "${workouts.size} recorded") }
        if (workouts.isEmpty()) {
            item { QuietPanel { Note("Workouts you start in OpenStrap show up here, and the strap's own after a sync.") } }
            return@ScreenList
        }
        workouts.groupBy { Instant.ofEpochSecond(it.workout.start).atZone(zone).toLocalDate() }.forEach { (date, day) ->
            item {
                QuietPanel {
                    PanelTitle(dayName(date), trailing = if (date.year != LocalDate.now().year) "${date.year}" else null)
                    day.forEachIndexed { i, a ->
                        if (i > 0) PanelDivider()
                        WorkoutRow(a, usual, divider = false) { onOpen(a.workout.start) }
                    }
                }
            }
        }
    }
}
