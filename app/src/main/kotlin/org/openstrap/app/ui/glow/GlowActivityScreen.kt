package org.openstrap.app.ui.glow

import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
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
import org.openstrap.app.data.Pai
import org.openstrap.app.data.RecoveryResult
import org.openstrap.app.data.WorkoutAnalysis
import org.openstrap.app.data.db.DailyPai
import org.openstrap.app.data.db.SportEffectRow
import org.openstrap.app.data.settings.UserProfile
import org.openstrap.app.ui.components.formatDuration
import org.openstrap.app.ui.dashboard.DateHeader
import org.openstrap.app.ui.dashboard.DayPicker
import org.openstrap.app.ui.dashboard.DayUiState
import org.openstrap.app.ui.dashboard.Metric
import org.openstrap.app.ui.dashboard.LocalGoals
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

/** Recent workouts listed under the day's own (scrolling), before "All workouts". */
private const val RECENT_WORKOUTS = 20
/** How many workout rows the Workouts panel shows before it scrolls. */
private const val VISIBLE_WORKOUTS = 4

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
    contentPadding: PaddingValues,
    onOpenMetric: (Metric) -> Unit,
    onOpenWorkout: (Long) -> Unit,
    onAllWorkouts: () -> Unit,
    onEditProfile: () -> Unit,
    onStartWorkout: (Int) -> Unit = {},
    onAllSports: () -> Unit = {},
    suggestion: org.openstrap.app.data.WorkoutSuggestions.Suggestion? = null,
    onSaveSuggestion: () -> Unit = {},
    onDismissSuggestion: () -> Unit = {},
    onAddWorkout: () -> Unit = {},
    /** An effort under way nobody started ([org.openstrap.app.data.AutoStart]); null hides the card. */
    offer: org.openstrap.app.data.AutoStart.Offer? = null,
    onRecordOffer: () -> Unit = {},
    onDeclineOffer: () -> Unit = {},
    autoStart: Boolean? = null,
    onAutoStart: (Boolean) -> Unit = {},
    /** Picks the strap's PAI curve for "drops off tomorrow"; null uses Zepp's default (female). */
    paiSex: UserProfile.Sex? = null,
) {
    val list = rememberLazyListState()
    val sports = remember(workouts) { topSports(workouts) }
    val week = remember(state.date) { paiHistory(state.date.minusDays(6), state.date) }.collectAsState(emptyMap()).value
    val r = recovery?.takeIf { it.date == state.date }
    CompositionLocalProvider(LocalGlowPaused provides { list.isScrollInProgress }) {
        GlowScreenList(contentPadding, list) {
            full { DateHeader(state.date, battery, onPrev, onNext, charging, picker) }
            // Wide: today's movement on the left; load, fitness and workouts on the right.
            // The home for workouts: started from here on today; a past day only shows what was done.
            if (offer != null && state.date == LocalDate.now()) left { OfferPanel(offer, onRecordOffer, onDeclineOffer) }
            if (state.date == LocalDate.now()) left { StartWorkoutPanel(sports, onStartWorkout, onAllSports, autoStart, onAutoStart) }
            if (suggestion != null && state.date == LocalDate.now()) left { SuggestionPanel(suggestion, onSaveSuggestion, onDismissSuggestion) }
            left { StepsCard(state, r?.activity) { onOpenMetric(Metric.Steps) } }
            r?.activity?.let { a -> left { MvpaPanel(a) } }
            left { EnergyPanel(r?.energy, onEditProfile) }
            left { PaiPanel(state, week, paiSex) }
            right { LoadPanel(r, effects.lastOrNull { it.weeklyLoad != null }, onEditProfile) }
            right { FitnessPanel(r, workouts, effects, state.dailyMaxHeartRate) }
            right { WorkoutsPanel(state.date, workouts, onOpenWorkout, onAllWorkouts, onAddWorkout) }
        }
    }
}

private val HOURS = listOf("00", "06", "12", "18", "24")

/** Steps glow and pick up pace with the day's progress; distance, calories and active hours sit under the chart. */
@Composable
private fun StepsCard(state: DayUiState, activity: org.openstrap.app.data.Activity.Day?, onClick: () -> Unit) {
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
                // Ours from step length and height when we can; the strap's own figure otherwise. Always approximate.
                Fact("Distance", (activity?.distanceMeters ?: state.distanceMeters?.toFloat())?.let { "≈ %.1f km".format(it / 1000f) } ?: "—")
                Fact("Active kcal", state.activeCalories?.toString() ?: "—")
            }
            if (org.openstrap.app.data.Activity.stepsDiverge(state.steps, state.minuteSteps)) {
                Spacer(Modifier.height(8.dp))
                Note("The strap's step counter (${"%,d".format(state.steps)}) and its minute records (${"%,d".format(state.minuteSteps)}) differ by more than 5 %.")
            }
            activity?.let { Note("Most of the benefit is reached by about ${"%,d".format(it.stepGoal)} steps a day (Paluch et al. 2022).") }
        }
    }
}

/** Energy expenditure: our per-minute estimate from BMR, steps and (narrowly) heart rate. */
@Composable
private fun EnergyPanel(e: org.openstrap.app.data.EnergyExpenditure.Day?, onEditProfile: () -> Unit) {
    DotPanel {
        DotHeader("Energy", trailing = e?.let { if (it.partial) "Partial day" else null })
        if (e == null) {
            Row(Modifier.fillMaxWidth().glass(GlassRowShape, onEditProfile).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Add age, weight and height to estimate the energy you use.", color = MetricColors.warn, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Icon(PhosphorIcons.CaretRight, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(start = 8.dp))
            }
            return@DotPanel
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Fact("Total", "≈ ${e.totalKcal.roundToInt()} kcal")
            Fact("At rest", "${e.bmrKcal.roundToInt()} /day")
            Fact("Activity level", e.pal?.let { "%.2f".format(it) } ?: "—")
        }
        Note(
            listOfNotNull(
                "From your resting metabolism (Mifflin-St Jeor), your steps each minute and, for effort without steps, your heart rate.",
                if (e.partial) "Covers ${formatDuration(e.minutesWithData * 60L)} of wear, so it's a partial total." else null,
                if (e.palImplausible) "That's an unusual total for a full day; check your profile and how the strap was worn." else null,
                if (e.hrAssistedMinutes > 0) "${e.hrAssistedMinutes} min HR-assisted." else null,
                "Food digestion isn't included.",
            ).joinToString(" "),
        )
    }
}

/** Moderate-to-vigorous minutes today and over the week, against the WHO's 150 a week (vigorous counts double). */
@Composable
private fun MvpaPanel(a: org.openstrap.app.data.Activity.Day) {
    val target = org.openstrap.app.data.Activity.MVPA_WEEKLY_TARGET
    DotPanel {
        DotHeader("Active minutes", trailing = "Last 7 days")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Fact("This week", "${a.weekEquivalent} / $target")
            Fact("Moderate", "${a.mvpa.moderateMinutes}")
            Fact("Vigorous", "${a.mvpa.vigorousMinutes}")
        }
        GlowBar(a.weekEquivalent / target.toFloat(), MetricColors.steps)
        Note(
            "Brisk walking (100+ steps a minute) is moderate, 130+ is vigorous and counts double. Effort without steps, " +
                "like cycling, counts from heart rate after 10 minutes" +
                if (a.mvpa.fromHeartRate > 0) " (${a.mvpa.fromHeartRate} min today, HR-assisted)." else ".",
        )
    }
}

/**
 * PAI: the strap's rolling 7-day score from heart-rate effort, aiming for 100. Shows the total as one bar
 * built from the days that make it up, what each day added, and what drops off next.
 */
@Composable
private fun PaiPanel(state: DayUiState, week: Map<LocalDate, DailyPai>, sex: UserProfile.Sex?) {
    val total = state.pai?.total ?: week[state.date]?.total
    DotPanel {
        DotHeader("PAI", trailing = "Last 7 days")
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
        val days = (6 downTo 0).map { state.date.minusDays(it.toLong()) }
        val daily = days.map { week[it]?.daily ?: 0f }
        val names = days.map { it.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(2) }
        PaiStack(daily)
        // Each day's points under the bar, oldest first; today in red like its segment.
        Row(Modifier.fillMaxWidth()) {
            daily.forEachIndexed { i, v ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(names[i], color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.labelSmall)
                    Text(
                        paiPoints(v), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp),
                        color = when { v <= 0f -> Color.White.copy(alpha = 0.35f); i == daily.lastIndex -> MetricColors.heart; else -> Color.White },
                    )
                }
            }
        }
        // The oldest day leaves the rolling total tomorrow; only worth saying when looking at today. Its stored daily
        // isn't what goes (native 08 §7), so re-sum the ring without it.
        val dropping = if (state.date != LocalDate.now() || daily.first() <= 0f) null else
            Pai.dropsOffTomorrow(days.map { d -> week[d]?.let { Pai.Day(it.daily, it.total) } }, sex)
                .toFloat().takeIf { it > 0f }
        Note(
            listOfNotNull(
                dropping?.let { "Tomorrow ${days.first().dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())}'s ${paiPoints(it)} drops off." },
                if (total >= PAI_TARGET) "You're at or above $PAI_TARGET. Keeping it there week after week is what PAI is built around."
                else "${PAI_TARGET - total.roundToInt()} to go. Brisk walking adds a little; anything that gets you out of breath adds more.",
            ).joinToString(" "),
        )
    }
}

/** "+12", "+<1" for a sliver of a point, "–" for none. */
private fun paiPoints(v: Float) = when {
    v <= 0f -> "–"
    v < 0.5f -> "+<1"
    else -> "+${v.roundToInt()}"
}

/**
 * The rolling total as one bar to [PAI_TARGET], built from the days that fill it: one segment per day,
 * oldest first and faintest (it drops off next), today in heart red. Past the target it's simply full.
 */
@Composable
private fun PaiStack(daily: List<Float>) {
    Canvas(Modifier.fillMaxWidth().height(14.dp)) {
        val corner = CornerRadius(size.height / 2)
        drawRoundRect(Color.White.copy(alpha = 0.07f), cornerRadius = corner)
        val track = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, corner)) }
        clipPath(track) {
            val gap = 1.5.dp.toPx()
            // A day with any points shows at least as a sliver, so tiny days don't vanish.
            val sliver = 3.dp.toPx()
            var x = 0f
            daily.forEachIndexed { i, v ->
                if (v <= 0f || x >= size.width) return@forEachIndexed
                val w = maxOf(size.width * v / PAI_TARGET, sliver)
                val color = when (i) {
                    daily.lastIndex -> MetricColors.heart
                    0 -> Color.White.copy(alpha = 0.35f)
                    else -> Color.White.copy(alpha = 0.55f + 0.45f * i / daily.lastIndex)
                }
                drawRect(color, Offset(x, 0f), Size(w, size.height))
                x += w + gap
            }
        }
    }
}

/** Today's load from heart rate (ours) next to the strap's weekly load and its productive range. */
@Composable
private fun LoadPanel(r: RecoveryResult?, effect: SportEffectRow?, onEditProfile: () -> Unit) {
    DotPanel {
        DotHeader("Training load")
        if (r != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Fact("Strain", "%.1f".format(r.strain))
                Fact("Load today", "${r.load.toInt()}")
                Fact("vs your usual", r.strainPercentile?.let { "${it}th pct" } ?: "—")
            }
            r.loadTrend?.let { t ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Fact("Fitness", "${t.ctl.toInt()}")
                    Fact("Fatigue", "${t.atl.toInt()}")
                    Fact("Form", "${if (t.form > 0.5f) "+" else ""}${t.form.toInt()}")
                }
                Note("Fitness and fatigue are 42- and 7-day averages of your daily load; form is fitness minus fatigue.")
            }
            Note(
                "Each minute counts by how hard your heart works between resting (${r.restingHr.toInt()} bpm) and maximum " +
                    "(${r.maxHr.toInt()} bpm${if (r.maxHrTested) ", from your test" else ""}). Strain runs 0–21 on a fixed curve: an hour at 60 % of reserve is about 7.",
            )
        }
        r?.acwr?.let { ratio ->
            DotDivider()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Fact("Acute:chronic", "%.2f".format(ratio))
                Spacer(Modifier.weight(1f))
                StatusPill("Contested", Color.White.copy(alpha = 0.7f))
            }
            Note("Last 7 days against the last 4 weeks. Shown for interest only: the evidence for a \"safe\" ratio is disputed, so it never enters Recovery.")
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
            GlowBar(weekly / (high * 1.25f), if (weekly in low.toFloat()..high.toFloat()) TrendGood else MetricColors.steps)
            Note("Productive range $low–$high, from the strap (updated ${shortDate(effect.end)}). Its load is on a different scale from today's.")
        }
        if (r != null && !r.maxHrFromAge) {
            Row(Modifier.fillMaxWidth().glass(GlassRowShape, onEditProfile).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Max HR is estimated. Add your age for accurate effort numbers.",
                    color = MetricColors.warn, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
                )
                Icon(PhosphorIcons.CaretRight, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(start = 8.dp))
            }
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
        DotHeader("Fitness")
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
        if (r != null) {
            DotDivider()
            val est = r.vo2max
            if (est != null) {
                Fact("Estimated fitness (VO2 max)", "≈ ${est.low}–${est.high}")
                Note(
                    "From your age, sex, BMI, resting heart rate (${est.restingHr.toInt()} bpm, while asleep) and usual activity " +
                        "(Jurca et al. 2005). Typical error is about ±5 ml/kg/min. Updated weekly." +
                        (est.band?.let { " ${it.label} for your age and sex (FRIEND 2022, treadmill)." } ?: "") +
                        (est.fitnessAge?.let { a -> est.sex?.let { " That's ${org.openstrap.app.data.Vo2max.Friend.fitnessAgeText(a, it)}." } } ?: ""),
                )
            } else r.vo2maxWithheld?.let { Note("Estimated fitness: ${it.reason}") }
        }
        Note(
            listOfNotNull(
                if (vo2.isEmpty()) "VO2 max appears after a workout the strap can estimate it from." else null,
                last?.let { "Recovery time is the strap's estimate of how long your last effort needs." },
                highestToday?.let { "Highest heart rate today $it bpm." },
            ).joinToString(" ").ifEmpty { "Record a workout on the strap to see VO2 max and recovery time." },
        )
    }
}

/**
 * The day's workouts, then the most recent others, in a window [VISIBLE_WORKOUTS] rows tall that scrolls inside the
 * panel; then a way to add one and to the full list.
 */
@Composable
private fun WorkoutsPanel(date: LocalDate, workouts: List<WorkoutAnalysis>, onOpen: (Long) -> Unit, onAll: () -> Unit, onAdd: () -> Unit) {
    val zone = ZoneId.systemDefault()
    fun dayOf(a: WorkoutAnalysis) = Instant.ofEpochSecond(a.workout.start).atZone(zone).toLocalDate()
    val today = workouts.filter { dayOf(it) == date }
    val recent = workouts.filter { dayOf(it) != date }.take(RECENT_WORKOUTS)
    DotPanel {
        DotHeader("Workouts", trailing = if (workouts.isEmpty()) null else "${workouts.size} recorded")
        if (workouts.isEmpty()) Note("Workouts you start above show up here, and the strap's own after a sync, with your heart rate every second.")
        if (today.isEmpty() && workouts.isNotEmpty()) Note("No workouts this day.")
        if (today.isNotEmpty() || recent.isNotEmpty()) {
            // Sized from a row as laid out, so the window always shows whole rows whatever the font size.
            val density = androidx.compose.ui.platform.LocalDensity.current
            var rowHeight by remember { mutableStateOf(64.dp) }
            val gap = 10.dp
            val scroll = rememberScrollState()
            Column(
                Modifier.fillMaxWidth().heightIn(max = rowHeight * VISIBLE_WORKOUTS + gap * (VISIBLE_WORKOUTS - 1))
                    .scrollBar(scroll).nestedScroll(KeepScroll).verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                val measure = Modifier.onSizeChanged { rowHeight = with(density) { it.height.toDp() } }
                today.forEachIndexed { i, a -> Box(if (i == 0) measure else Modifier) { WorkoutLine(a, showDate = false) { onOpen(a.workout.start) } } }
                if (recent.isNotEmpty()) {
                    Text("RECENT", color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                    recent.forEachIndexed { i, a -> Box(if (i == 0 && today.isEmpty()) measure else Modifier) { WorkoutLine(a, showDate = true) { onOpen(a.workout.start) } } }
                }
            }
        }
        Row(Modifier.fillMaxWidth().glass(GlassRowShape, onAdd).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Add a workout you didn't record", color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Icon(PhosphorIcons.CaretRight, null, tint = Color.White.copy(alpha = 0.7f))
        }
        if (workouts.size > today.size + recent.size) {
            Row(Modifier.fillMaxWidth().glass(GlassRowShape, onAll).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("All workouts", color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Icon(PhosphorIcons.CaretRight, null, tint = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
internal fun WorkoutLine(a: WorkoutAnalysis, showDate: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().glass(GlassRowShape, onClick).padding(horizontal = 14.dp, vertical = 10.dp),
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

/**
 * A thin bar just outside a scrolling box's right edge, in the panel's margin, its length the share of the content in
 * view and its place the scroll position, so a list that scrolls inside a panel says so. Nothing when everything fits.
 */
private fun Modifier.scrollBar(state: androidx.compose.foundation.ScrollState): Modifier = drawWithContent {
    drawContent()
    val content = size.height + state.maxValue
    if (state.maxValue == 0) return@drawWithContent
    val width = 3.dp.toPx()
    val length = (size.height * size.height / content).coerceAtLeast(24.dp.toPx())
    val top = (size.height - length) * state.value / state.maxValue
    drawRoundRect(
        Color.White.copy(alpha = 0.35f), topLeft = androidx.compose.ui.geometry.Offset(size.width + 8.dp.toPx(), top),
        size = androidx.compose.ui.geometry.Size(width, length), cornerRadius = androidx.compose.ui.geometry.CornerRadius(width / 2),
    )
}

/**
 * Keeps a list's scroll to itself: what's left at either end (and a fling's leftover speed) is used up here rather than
 * handed on, so the page doesn't move under a drag that started in the list.
 */
private object KeepScroll : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
    override fun onPostScroll(consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource) =
        available.copy(x = 0f)

    override suspend fun onPostFling(consumed: androidx.compose.ui.unit.Velocity, available: androidx.compose.ui.unit.Velocity) =
        available.copy(x = 0f)
}
