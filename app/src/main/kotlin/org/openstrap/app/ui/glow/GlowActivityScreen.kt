package org.openstrap.app.ui.glow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import org.openstrap.app.data.RecoveryResult
import org.openstrap.app.data.WorkoutAnalysis
import org.openstrap.app.data.db.DailyPai
import org.openstrap.app.data.db.SportEffectRow
import org.openstrap.app.device.SyncState
import org.openstrap.app.ui.components.formatDuration
import org.openstrap.app.ui.components.pressClickable
import org.openstrap.app.ui.dashboard.DateHeader
import org.openstrap.app.ui.dashboard.DayPicker
import org.openstrap.app.ui.dashboard.DayUiState
import org.openstrap.app.ui.dashboard.Metric
import org.openstrap.app.ui.dashboard.LocalGoals
import org.openstrap.app.ui.dashboard.ScreenList
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.app.ui.theme.PhosphorIcons
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** PAI's weekly target: 100 or more over the last seven days. */
private const val PAI_TARGET = 100

/** Recent workouts listed under the day's own, before "All workouts". */
private const val RECENT_WORKOUTS = 3

/** Each day's PAI between two dates, keyed by date. */
typealias PaiHistory = (LocalDate, LocalDate) -> Flow<Map<LocalDate, DailyPai>>

/**
 * The Activity tab in the glow language: "how much did I move and train". Steps glow; PAI, training load,
 * fitness and workouts sit in quiet dot panels underneath.
 */
@Composable
fun GlowActivityScreen(
    state: DayUiState,
    recovery: RecoveryResult?,
    workouts: List<WorkoutAnalysis>,
    effects: List<SportEffectRow>,
    paiHistory: PaiHistory,
    battery: Int?,
    charging: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    picker: DayPicker,
    sync: SyncState.Running?,
    contentPadding: PaddingValues,
    onOpenMetric: (Metric) -> Unit,
    onOpenWorkout: (Long) -> Unit,
    onAllWorkouts: () -> Unit,
    onEditProfile: () -> Unit,
) {
    val list = rememberLazyListState()
    val week = remember(state.date) { paiHistory(state.date.minusDays(6), state.date) }.collectAsState(emptyMap()).value
    val r = recovery?.takeIf { it.date == state.date }
    CompositionLocalProvider(LocalGlowPaused provides { list.isScrollInProgress }) {
        ScreenList(contentPadding, list, gutter = GlowLayout.Gutter) {
            item { DateHeader(state.date, battery, onPrev, onNext, charging, picker, sync) }
            item { StepsCard(state) { onOpenMetric(Metric.Steps) } }
            item { PaiPanel(state, week) }
            item { LoadPanel(r, effects.lastOrNull { it.weeklyLoad != null }, onEditProfile) }
            item { FitnessPanel(r, workouts, effects, state.dailyMaxHeartRate) }
            item { WorkoutsPanel(state.date, workouts, onOpenWorkout, onAllWorkouts) }
        }
    }
}

private val HOURS = listOf("00", "06", "12", "18", "24")

/** Steps glow and pick up pace with the day's progress; distance, calories and active hours sit under the chart. */
@Composable
private fun StepsCard(state: DayUiState, onClick: () -> Unit) {
    val goals = LocalGoals.current
    val progress = state.steps.toFloat() / goals.steps
    val style = GlowStyle.chart(MetricColors.steps).copy(motion = GlowMotion.Stride(energy = progress.coerceIn(0f, 1f)))
    GlowCard(style, Modifier.fillMaxWidth(), onClick = onClick) {
        Column {
            GlowHeader("Steps", chevron = true)
            val goal = if (state.steps >= goals.steps) "Goal reached" else "%d%% of %,d".format(state.steps * 100 / goals.steps, goals.steps)
            GlowHeroNumber("${state.steps}", "steps", listOf(goal))
            GlowChartGap()
            GlowDotBars(state.hourlySteps, height = GlowLayout.ChartHeight, labels = HOURS, accent = MetricColors.steps) { h, v ->
                "%02d:00–%02d:00 · %,d steps".format(h, (h + 1) % 24, Math.round(v))
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Fact("Active hours", "${state.activeHours} of ${goals.activeHours}")
                Fact("Distance", state.distanceMeters?.let { "%.1f km".format(it / 1000f) } ?: "—")
                Fact("Active kcal", state.activeCalories?.toString() ?: "—")
            }
        }
    }
}

/**
 * PAI: the strap's rolling 7-day score from heart-rate effort, aiming for 100. Shows the total, what each
 * day added, and how much is left to reach the target.
 */
@Composable
private fun PaiPanel(state: DayUiState, week: Map<LocalDate, DailyPai>) {
    val total = state.pai?.total ?: week[state.date]?.total
    DotPanel {
        DotHeader("PAI", MetricColors.heart, trailing = "Last 7 days")
        if (total == null) {
            Note("PAI shows after the strap's next sync. It counts minutes your heart rate is raised, harder minutes counting more.")
            return@DotPanel
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${total.roundToInt()}", color = Color.White, style = MaterialTheme.typography.displaySmall)
            Text(" / $PAI_TARGET", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 6.dp))
            Spacer(Modifier.weight(1f))
            val today = state.pai?.daily ?: week[state.date]?.daily
            if (today != null) Fact("Today", "+${today.roundToInt()}")
        }
        DotBar(total / PAI_TARGET, MetricColors.heart)
        val days = (6 downTo 0).map { state.date.minusDays(it.toLong()) }
        GlowDotBars(
            days.map { week[it]?.daily ?: 0f }, height = 72.dp, rows = 6,
            labels = days.map { it.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(2) },
            accent = MetricColors.heart,
        ) { i, v -> "${days[i].dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())} · +${v.roundToInt()} PAI" }
        Note(
            if (total >= PAI_TARGET) "You're at or above $PAI_TARGET. Keeping it there week after week is what PAI is built around."
            else "${PAI_TARGET - total.roundToInt()} to go. Brisk walking adds a little; anything that gets you out of breath adds more.",
        )
    }
}

/** Today's load from heart rate (ours) next to the strap's weekly load and its productive range. */
@Composable
private fun LoadPanel(r: RecoveryResult?, effect: SportEffectRow?, onEditProfile: () -> Unit) {
    DotPanel {
        DotHeader("Training load", MetricColors.steps)
        if (r != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Fact("Today", "${r.load.toInt()}")
                Fact("Usual day", r.chronicLoad?.toInt()?.toString() ?: "—")
                Spacer(Modifier)
            }
            Note(
                "Each minute counts by how hard your heart works between resting (${r.restingHr.toInt()} bpm) and maximum " +
                    "(${r.maxHr.toInt()} bpm). An hour of moderate exercise is about 60–80.",
            )
        }
        val weekly = effect?.weeklyLoad
        val low = effect?.optimalLoadMin
        val high = effect?.optimalLoadMax
        if (weekly != null && low != null && high != null) {
            DotDivider()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Fact("This week (strap)", "${weekly.roundToInt()}")
                Spacer(Modifier.weight(1f))
                val (word, color) = when {
                    weekly < low -> "Below productive" to TrendBad
                    weekly > high -> "Above productive" to TrendBad
                    else -> "Productive" to TrendGood
                }
                Text(word, color = color, style = MaterialTheme.typography.titleSmall)
            }
            DotBar(weekly / (high * 1.25f), if (weekly in low.toFloat()..high.toFloat()) TrendGood else MetricColors.steps)
            Note("Productive range $low–$high, from the strap (updated ${shortDate(effect.end)}). Its load is on a different scale from today's.")
        }
        if (r != null && !r.maxHrFromAge) {
            Text(
                "Max HR is estimated. Add your age for accurate effort numbers.",
                color = MetricColors.warn, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.pressClickable(onEditProfile),
            )
        }
    }
}

/** VO2 max and its trend across workouts, recovery time left from the last one, and max heart rate. */
@Composable
private fun FitnessPanel(r: RecoveryResult?, workouts: List<WorkoutAnalysis>, effects: List<SportEffectRow>, highestToday: Int?) {
    val vo2 = workouts.sortedBy { it.workout.start }.mapNotNull { a -> (a.workout.vo2max ?: a.effect?.vo2max?.roundToInt())?.takeIf { it > 0 } }
    val last = effects.lastOrNull { it.recoveryMinutes != null }
    val now = System.currentTimeMillis() / 1000
    val left = last?.let { it.end + it.recoveryMinutes!! * 60L - now }
    DotPanel {
        DotHeader("Fitness", MetricColors.hrv)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Fact("VO2 max", vo2.lastOrNull()?.toString() ?: "—")
            Fact("Recovery", when {
                left == null -> "—"
                left <= 0 -> "Done"
                else -> "${formatDuration(left)} left"
            })
            Fact("Max HR", r?.let { "${it.maxHr.toInt()}" } ?: "—")
        }
        if (vo2.size >= 2) Sparkline(vo2.takeLast(7).map { it.toFloat() }, MetricColors.hrv, Modifier.fillMaxWidth().height(24.dp))
        Note(
            listOfNotNull(
                if (vo2.isEmpty()) "VO2 max appears after a workout the strap can estimate it from." else null,
                last?.let { "Recovery time is the strap's estimate of how long your last effort needs." },
                highestToday?.let { "Highest heart rate today $it bpm." },
            ).joinToString(" ").ifEmpty { "Record a workout on the strap to see VO2 max and recovery time." },
        )
    }
}

/** The day's workouts, then the most recent others, then a way to the full list. */
@Composable
private fun WorkoutsPanel(date: LocalDate, workouts: List<WorkoutAnalysis>, onOpen: (Long) -> Unit, onAll: () -> Unit) {
    val zone = ZoneId.systemDefault()
    fun dayOf(a: WorkoutAnalysis) = Instant.ofEpochSecond(a.workout.start).atZone(zone).toLocalDate()
    val today = workouts.filter { dayOf(it) == date }
    val recent = workouts.filter { dayOf(it) != date }.take(RECENT_WORKOUTS)
    DotPanel {
        DotHeader("Workouts", MetricColors.steps, trailing = if (workouts.isEmpty()) null else "${workouts.size} recorded")
        if (workouts.isEmpty()) {
            Note("Workouts you start on the strap show up here after a sync, with your heart rate every second and how fast it recovered.")
            return@DotPanel
        }
        if (today.isEmpty()) Note("No workouts this day.")
        today.forEach { WorkoutLine(it, showDate = false) { onOpen(it.workout.start) } }
        if (recent.isNotEmpty()) {
            DotDivider()
            Text("RECENT", color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.labelSmall)
            recent.forEach { WorkoutLine(it, showDate = true) { onOpen(it.workout.start) } }
        }
        if (workouts.size > today.size + recent.size) {
            DotDivider()
            Row(Modifier.fillMaxWidth().pressClickable(onAll, RoundedCornerShape(12.dp), scale = 0.98f), verticalAlignment = Alignment.CenterVertically) {
                Text("All workouts", color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Icon(PhosphorIcons.CaretRight, null, tint = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
internal fun WorkoutLine(a: WorkoutAnalysis, showDate: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressClickable(onClick, RoundedCornerShape(12.dp), scale = 0.98f).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(a.sportName, color = Color.White, style = MaterialTheme.typography.titleSmall)
            val start = Instant.ofEpochSecond(a.workout.start).atZone(ZoneId.systemDefault())
            Note(
                listOfNotNull(
                    start.format(DateTimeFormatter.ofPattern(if (showDate) "EEE d MMM · HH:mm" else "HH:mm")),
                    formatDuration(a.durationSeconds),
                    a.workout.averageHeartRate?.let { "avg $it bpm" },
                    a.workout.calories?.let { "$it kcal" },
                ).joinToString(" · "),
            )
        }
        Icon(PhosphorIcons.CaretRight, null, tint = Color.White.copy(alpha = 0.7f))
    }
}

private fun shortDate(epoch: Long) = Instant.ofEpochSecond(epoch).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE d MMM"))
