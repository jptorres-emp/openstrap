package org.openstrap.app.ui.glow

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.openstrap.app.data.WorkoutAnalysis
import org.openstrap.app.data.Workouts
import org.openstrap.app.ui.components.formatDuration
import org.openstrap.app.ui.dashboard.ScreenList
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.app.ui.workout.ZONE_COLORS
import org.openstrap.app.ui.workout.ZONE_NAMES
import org.openstrap.app.ui.workout.signedDrop
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun clock(epoch: Long) = Instant.ofEpochSecond(epoch).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
private fun dateLine(epoch: Long) = Instant.ofEpochSecond(epoch).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE d MMM · HH:mm"))

/**
 * One workout in the glow language. Its heart rate glows: the average, duration and peak, and every second of it.
 * How fast you recovered, time in each zone and the strap's training effect sit in dot panels underneath.
 */
@Composable
fun GlowWorkoutScreen(a: WorkoutAnalysis?, contentPadding: PaddingValues, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val list = rememberLazyListState()
    CompositionLocalProvider(LocalGlowPaused provides { list.isScrollInProgress }) {
        ScreenList(contentPadding, list, gutter = GlowLayout.Gutter) {
            item { GlowDetailHeader(a?.sportName ?: "Workout", a?.let { dateLine(it.workout.start) }, onBack) }
            if (a == null) {
                item { DotPanel { Note("Loading…") } }
                return@ScreenList
            }
            item { HeartCard(a) }
            item { SummaryPanel(a) }
            if (a.recovery60 != null) item { RecoveryPanel(a) }
            if (a.zoneSeconds.sum() > 0) item { ZonesPanel(a) }
            item { EffectPanel(a) }
        }
    }
}

@Composable
private fun HeartCard(a: WorkoutAnalysis) {
    val w = a.workout
    val style = GlowStyle.chart(MetricColors.heart).copy(motion = GlowMotion.Heartbeat(w.averageHeartRate ?: 100))
    GlowCard(style, Modifier.fillMaxWidth()) {
        Column {
            GlowHeader("Heart rate", trailing = "Every second")
            GlowHeroNumber(
                w.averageHeartRate?.toString() ?: "-", "bpm avg",
                listOfNotNull(w.maxHeartRate?.let { "Peak $it" }, listOfNotNull(formatDuration(a.durationSeconds), w.calories?.let { "$it kcal" }).joinToString(" · ")),
            )
            if (a.heartRate.isEmpty()) {
                androidx.compose.foundation.layout.Spacer(Modifier.height(GlowLayout.TitleGap))
                Note("The strap hasn't sent this workout's heart rate yet.")
                return@Column
            }
            val first = w.start
            val last = maxOf(a.heartRate.last().timestamp, a.end)
            // Ten-second buckets keep the line readable on a phone; the readout still says the time.
            val step = 10L
            val values = remember(a.heartRate) {
                val n = ((last - first) / step + 1).toInt().coerceAtLeast(2)
                val sums = FloatArray(n)
                val counts = IntArray(n)
                a.heartRate.forEach { s ->
                    val i = ((s.timestamp - first) / step).toInt()
                    if (i in 0 until n) { sums[i] += s.bpm.toFloat(); counts[i]++ }
                }
                List(n) { if (counts[it] == 0) null else sums[it] / counts[it] }
            }
            GlowChartGap()
            // No average line: it would count the recovery tail and disagree with the strap's average above.
            GlowLineChart(values, height = GlowLayout.ChartHeight, labels = listOf(clock(first), clock(last)), showAverage = false) { i, v ->
                "${v.toInt()} bpm · ${clock(first + i * step)}"
            }
            if (a.heartRate.last().timestamp > a.end + 30) {
                androidx.compose.foundation.layout.Spacer(Modifier.height(10.dp))
                Note("The strap kept recording for ${formatDuration((a.heartRate.last().timestamp - a.end + 30) / 60 * 60)} after you stopped, for recovery heart rate.")
            }
        }
    }
}

@Composable
private fun SummaryPanel(a: WorkoutAnalysis) {
    val w = a.workout
    DotPanel {
        // Duration, calories and peak are on the card; this is how the time split and what else the strap counted.
        val active = w.exerciseSeconds?.toLong()
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.weight(1f)) { Fact("Active", active?.let { formatDuration(it) } ?: "—") }
            Box(Modifier.weight(1f)) { Fact("Paused", active?.let { formatDuration((a.durationSeconds - it).coerceAtLeast(0)) } ?: "—") }
            Box(Modifier.weight(1f)) { Fact("Lowest HR", w.minHeartRate?.let { "$it" } ?: "—") }
        }
        w.steps?.takeIf { it > 0 }?.let { Note("%,d steps.".format(it)) }
    }
}

@Composable
private fun RecoveryPanel(a: WorkoutAnalysis) {
    val drop = a.recovery60 ?: return
    val rated = a.recoveryIsMeaningful
    val (label, color) = when {
        !rated -> null to Color.White.copy(alpha = 0.6f)
        drop >= 25 -> "Excellent" to TrendGood
        drop >= 18 -> "Good" to TrendGood
        drop >= 12 -> "Fair" to TrendBad
        else -> "Low" to TrendBad
    }
    DotPanel {
        DotHeader("Recovery heart rate", trailing = label)
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.weight(1f)) { Fact("After 1 min", "${signedDrop(drop)} bpm") }
            a.recovery120?.let { Box(Modifier.weight(1f)) { Fact("After 2 min", "${signedDrop(it)} bpm") } }
        }
        if (rated) GlowBar(drop / 40f, color)
        Note(
            if (rated) {
                "How fast your heart rate fell from ${a.endHeartRate} bpm once you stopped. A bigger drop usually means a " +
                    "fitter heart; under 12 bpm in the first minute is on the low side. Compare it across similar workouts."
            } else {
                "You finished at ${a.endHeartRate ?: "—"} bpm, close to resting, so there was little to recover from. " +
                    "Recovery heart rate is rated after harder efforts."
            },
        )
    }
}

@Composable
private fun ZonesPanel(a: WorkoutAnalysis) {
    val total = a.zoneSeconds.sum().coerceAtLeast(1)
    DotPanel {
        DotHeader("Heart-rate zones", trailing = "Max ${a.maxHeartRate} bpm")
        for (z in Workouts.ZONES - 1 downTo 0) {
            val lo = (50 + z * 10) * a.maxHeartRate / 100
            val hi = if (z == Workouts.ZONES - 1) "+" else "–${(60 + z * 10) * a.maxHeartRate / 100 - 1}"
            val secs = a.zoneSeconds[z]
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.padding(end = 8.dp).size(7.dp)) { drawCircle(ZONE_COLORS[z]) }
                    Text(ZONE_NAMES[z], color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Note("$lo$hi")
                    Text(formatDuration(secs.toLong()), color = Color.White, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 12.dp).width(56.dp))
                }
                GlowBar(secs.toFloat() / total, ZONE_COLORS[z])
            }
        }
    }
}

@Composable
private fun EffectPanel(a: WorkoutAnalysis) {
    val w = a.workout
    val e = a.effect
    DotPanel {
        DotHeader("Training effect", trailing = "From the strap")
        EffectFacts(w, e?.vo2max ?: w.vo2max?.toFloat(), a.recoveryMinutes)
        Note("Training effect runs 0–5 for each system. VO₂ max shows up once the strap has enough data to estimate it.")
    }
}

/**
 * Aerobic and anaerobic training effect, load, VO₂ max and recovery time, laid out the same wherever they come from:
 * the strap's sport effect, or OpenStrap's own for a phone workout ([org.openstrap.app.data.WorkoutEffect]).
 */
@Composable
internal fun EffectFacts(w: org.openstrap.app.data.db.Workout, vo2: Float?, recoveryMinutes: Int?) {
    Row(Modifier.fillMaxWidth()) {
        Box(Modifier.weight(1f)) { Fact("Aerobic", w.aerobicTrainingEffect?.let { "%.1f".format(it) } ?: "—") }
        Box(Modifier.weight(1f)) { Fact("Anaerobic", w.anaerobicTrainingEffect?.let { "%.1f".format(it) } ?: "—") }
        Box(Modifier.weight(1f)) { Fact("Load", w.trainingLoad?.toString() ?: "—") }
    }
    Row(Modifier.fillMaxWidth()) {
        Box(Modifier.weight(1f)) { Fact("VO₂ max", vo2?.let { "%.0f".format(it) } ?: "—") }
        Box(Modifier.weight(2f)) { Fact("Recovery time", recoveryMinutes?.takeIf { it > 0 }?.let { formatDuration(it * 60L) } ?: "—") }
    }
}

/** Every workout, newest first, one dot panel per day. */
@Composable
fun GlowWorkoutsScreen(workouts: List<WorkoutAnalysis>, contentPadding: PaddingValues, onOpen: (Long) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val zone = ZoneId.systemDefault()
    ScreenList(contentPadding, gutter = GlowLayout.Gutter) {
        item { GlowDetailHeader("Workouts", "${workouts.size} recorded", onBack) }
        if (workouts.isEmpty()) {
            item { DotPanel { Note("Workouts you start in OpenStrap show up here, and the strap's own after a sync.") } }
            return@ScreenList
        }
        workouts.groupBy { Instant.ofEpochSecond(it.workout.start).atZone(zone).toLocalDate() }.forEach { (date, day) ->
            item {
                DotPanel {
                    DotHeader(dayName(date), trailing = if (date.year != LocalDate.now().year) "${date.year}" else null)
                    day.forEachIndexed { i, a ->
                        if (i > 0) DotDivider()
                        WorkoutLine(a, showDate = false) { onOpen(a.workout.start) }
                    }
                }
            }
        }
    }
}
