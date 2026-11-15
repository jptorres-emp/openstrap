package org.openstrap.app.ui.glow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import org.openstrap.app.R
import org.openstrap.app.data.Structure
import org.openstrap.app.data.WorkoutAnalysis
import org.openstrap.app.data.WorkoutSetup
import org.openstrap.app.data.Workouts
import org.openstrap.app.i18n.Strings
import org.openstrap.app.presentation.Pace
import org.openstrap.app.presentation.Units
import org.openstrap.app.ui.components.pressClickable
import org.openstrap.app.ui.glow.live.HeroLine
import org.openstrap.app.ui.glow.live.HeroNumber
import org.openstrap.app.ui.glow.live.HeroTitle
import org.openstrap.app.ui.glow.live.LiveFigures
import org.openstrap.app.ui.glow.live.LiveHero
import org.openstrap.app.ui.glow.live.heroTint
import org.openstrap.app.ui.workout.StartCopy

/*
 * Ready's hero (workout-rules.md, section 3; boards HaloReadyRun, HaloReadyIndoor, HaloReadyHiit, HaloReadyStrength,
 * HaloReadyMind, HaloReadyGames): the goal as the one big number, tapped to edit it in a sheet, and under the plan
 * card last time's figures. Both are read from what Ready already holds and from the workouts on file.
 */

/** Whether the goal's sheet is open, and what Cancel puts back. Ready sets it; the plan card shows the sheet. */
@Stable
internal class GoalEditor {
    var open by mutableStateOf(false)
    var revert: () -> Unit = {}
}

@Suppress("CompositionLocalAllowlist", "ComposeCompositionLocalUsage")
internal val LocalGoalEditor = staticCompositionLocalOf { GoalEditor() }

/** What the hero says: [number] is null where there is no figure ([words] stand in), [note] sits at the right of the title. */
internal class GoalHeroSpec(
    val title: String,
    val number: String?,
    val unit: String?,
    val words: String?,
    val line: String,
    val note: String? = null,
)

/** The hero: lit in the family's colour, the goal as the big number, tapping it opens the goal's sheet ([onEdit]). */
@Composable
internal fun GoalHero(spec: GoalHeroSpec, color: Color, angle: Float, onEdit: () -> Unit) {
    val tint = heroTint(color)
    LiveHero(color, angle, Modifier.pressClickable(onEdit, GlowLayout.CardShape)) {
        HeroTitle(spec.title, spec.note, tint)
        if (spec.number != null) HeroNumber(spec.number, spec.unit, tint, BigNumber.Feature)
        else Text(spec.words.orEmpty(), color = Color.White, style = GlowType.sentence)
        HeroLine(spec.line, tint)
    }
}

/** What the last workout of the sport showed: the weekday and the figures it has (value, label). */
class ReadyLast(val day: String, val figures: List<Pair<String, String>>)

/**
 * The figures of [a] the sport's board shows (boards Ready*, "Last time"): a figure with no data is left out, so a
 * workout that has none of them gives nothing. Distance sports show distance, time and pace; strength sets and time;
 * HIIT the peak and time; mind and body time and average; games time, load and kcal; the rest time, average and kcal.
 */
internal fun readyLast(a: WorkoutAnalysis, today: LocalDate, zone: ZoneId, strings: Strings = Strings.app): ReadyLast? {
    val w = a.workout
    val family = Workouts.family(w.sportType)
    val time = Units.workoutClock(a.activeSeconds) to strings.get(R.string.glow_ready_last_time)
    val avg = w.averageHeartRate?.let { "$it" to strings.get(R.string.glow_ready_last_avg_bpm) }
    val peak = w.maxHeartRate?.let { "$it" to strings.get(R.string.glow_ready_last_peak_bpm) }
    val kcal = a.activeKcal?.toInt()?.let { "$it" to strings.get(R.string.glow_ready_last_kcal) }
    val load = w.trainingLoad?.let { "$it" to strings.get(R.string.glow_ready_last_load) }
    val sets = a.sets?.takeIf { it > 0 }?.let { "$it" to strings.get(R.string.glow_ready_last_sets) }
    val distance = a.distanceMeters?.takeIf { it >= Pace.FROM_METERS }
    val figures = when (family) {
        Workouts.Family.Distance -> listOfNotNull(
            distance?.let { Units.distance(it, Units.Precision.Workout).orEmpty() to strings.get(R.string.glow_ready_last_distance) },
            time,
            Pace.clock(Pace.of(distance, a.activeSeconds))?.let { it to strings.get(R.string.glow_ready_last_pace) },
        )
        Workouts.Family.Strength -> listOfNotNull(sets, time)
        Workouts.Family.Intervals -> listOfNotNull(peak, time)
        Workouts.Family.MindBody -> listOfNotNull(time, avg)
        Workouts.Family.Games -> listOfNotNull(time, load, kcal)
        Workouts.Family.IndoorCardio -> listOfNotNull(time, avg, kcal)
    }.filter { it.first.isNotEmpty() }
    if (figures.isEmpty()) return null
    val day = Instant.ofEpochSecond(w.start).atZone(zone).toLocalDate()
    val name = day.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault())
    return ReadyLast(if (day == today) strings.get(R.string.glow_ready_last_today) else name, figures)
}

/** "Last time, Tue" over last time's figures, plain, no card (workout-rules.md, section 2). Nothing without [last]. */
@Composable
internal fun ReadyLastRow(last: ReadyLast?) {
    if (last == null) return
    Column(Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(R.string.glow_ready_last_caption, last.day), color = GlowType.noteColor, style = GlowType.noteSmall,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        LiveFigures(last.figures.map { it.first to it.second })
    }
}

/** "Ends about 18:12" for a goal [seconds] long started at [nowMillis], null where nothing says how long it takes. */
@Composable
@androidx.compose.runtime.ReadOnlyComposable
internal fun endsAbout(seconds: Long?, nowMillis: Long): String? {
    if (seconds == null || seconds <= 0) return null
    val at = Instant.ofEpochMilli(nowMillis + seconds * MS_PER_S).atZone(ZoneId.systemDefault())
    return stringResource(R.string.workout_start_ends_near, DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(at))
}

/** The clock "Ends about" counts from, read again every half minute. */
@Suppress("ForbiddenMethodCall/uiClock")
@Composable
internal fun rememberNowMillis(): androidx.compose.runtime.State<Long> = androidx.compose.runtime.produceState(System.currentTimeMillis()) {
    while (true) {
        value = System.currentTimeMillis()
        kotlinx.coroutines.delay(NOW_REFRESH_MS)
    }
}

private const val NOW_REFRESH_MS = 30_000L
private const val MS_PER_S = 1000L
private const val SECONDS_PER_MINUTE = 60L
private const val UNIT_DIVIDER = "\u00D7"

/** A time goal as the figure: whole minutes in min, else the clock. */
private fun timeFigure(seconds: Long): Pair<String, String?> =
    if (seconds % SECONDS_PER_MINUTE == 0L) "${seconds / SECONDS_PER_MINUTE}" to null else Units.workoutClock(seconds,
        Units.DOT_LOCALE) to null

/**
 * The hero's words for the family's goal (workout-rules.md, section 3 table). [lastPace] is last time's seconds a km, for
 * when a distance goal would end; [nowMillis] is the clock the "Ends about" note counts from.
 */
@Composable
@androidx.compose.runtime.ReadOnlyComposable
@Suppress("LongParameterList", "CyclomaticComplexMethod")
internal fun goalHeroSpec(
    type: Int,
    family: Workouts.Family,
    kind: WorkoutSetup.GoalKind,
    draft: GoalDraft,
    sets: Int?,
    restSeconds: Long,
    rounds: Structure?,
    hasDistance: Boolean,
    zoneLine: String?,
    lastPace: Double?,
    nowMillis: Long,
): GoalHeroSpec {
    val title = StartCopy.planTitle(family)
    val min = stringResource(R.string.glow_unit_min)
    return when (family) {
        Workouts.Family.Distance, Workouts.Family.IndoorCardio -> when (kind) {
            WorkoutSetup.GoalKind.Distance -> GoalHeroSpec(
                title, StartCopy.distanceValue(draft.meters), stringResource(R.string.glow_unit_km), null,
                StartCopy.distanceNote(draft.meters), endsAbout(lastPace?.let { (draft.meters / METERS_PER_KM * it).toLong() }, nowMillis),
            )
            WorkoutSetup.GoalKind.Time -> {
                val (n, _) = timeFigure(draft.seconds)
                GoalHeroSpec(title, n, if (draft.seconds % SECONDS_PER_MINUTE == 0L) min else null, null,
                    StartCopy.TIME_KIND_NOTE, endsAbout(draft.seconds, nowMillis))
            }
            WorkoutSetup.GoalKind.Zone -> {
                val z = draft.zones
                GoalHeroSpec(
                    title, if (z.low == z.high) "${z.low + 1}" else stringResource(R.string.glow_ready_hero_zone_range,
                        z.low + 1, z.high + 1),
                    stringResource(R.string.glow_ready_hero_zones), null, zoneLine ?: StartCopy.ZONE_NOTE,
                )
            }
            WorkoutSetup.GoalKind.Intervals -> {
                val (len, unit) = StartCopy.repParts(draft.reps)
                GoalHeroSpec(title, "${draft.reps.rounds}$UNIT_DIVIDER$len", unit, null, StartCopy.structureName(draft.reps))
            }
            WorkoutSetup.GoalKind.None -> GoalHeroSpec(title, null, null, StartCopy.noGoal(type), StartCopy.noGoalNote(hasDistance))
        }
        Workouts.Family.Strength ->
            if (sets != null) GoalHeroSpec(
                title, "$sets", stringResource(R.string.glow_ready_hero_sets_rest, StartCopy.restText(restSeconds)), null,
                    StartCopy.STRENGTH_STATUS,
            ) else GoalHeroSpec(title, null, null, StartCopy.OPEN, StartCopy.OPEN_SETS_NOTE)
        Workouts.Family.Intervals -> {
            val total = rounds?.let { StartCopy.totalTime(it) }
            if (rounds != null && total != null) GoalHeroSpec(
                title, Units.workoutClock(total, Units.DOT_LOCALE), StartCopy.hiitName(rounds), null, StartCopy.INTERVALS_STATUS,
                endsAbout(total, nowMillis),
            ) else GoalHeroSpec(title, null, null, stringResource(R.string.glow_ready_hero_free), StartCopy.HIIT_FREE_NOTE)
        }
        Workouts.Family.MindBody ->
            if (kind == WorkoutSetup.GoalKind.Time) GoalHeroSpec(
                title, timeFigure(draft.seconds).first, if (draft.seconds % SECONDS_PER_MINUTE == 0L) min else null, null,
                    StartCopy.MIND_STATUS,
                endsAbout(draft.seconds, nowMillis),
            ) else GoalHeroSpec(title, null, null, StartCopy.OPEN, StartCopy.MIND_OPEN_NOTE)
        Workouts.Family.Games -> {
            val p = rounds as? Structure.Periods
            if (p != null) GoalHeroSpec(
                title, "${p.rounds} $UNIT_DIVIDER ${p.length / SECONDS_PER_MINUTE}", stringResource(R.string.glow_ready_hero_periods,
                    StartCopy.periodsName(p).lowercase()),
                null, StartCopy.gamesNote(p), endsAbout(StartCopy.totalTime(p), nowMillis),
            ) else GoalHeroSpec(title, null, null, StartCopy.FREE_PLAY, StartCopy.gamesNote(null))
        }
    }
}

private const val METERS_PER_KM = 1000.0

