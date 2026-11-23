package org.openstrap.app.ui.glow

import org.openstrap.app.ui.theme.Space
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.openstrap.app.R
import org.openstrap.app.data.WorkoutAnalysis
import org.openstrap.app.data.Workouts
import org.openstrap.app.i18n.Strings
import org.openstrap.app.presentation.Pace
import org.openstrap.app.presentation.TimeOfDay
import org.openstrap.app.presentation.Units
import org.openstrap.app.ui.glow.live.HeroLine
import org.openstrap.app.ui.glow.live.LiveFigures
import org.openstrap.app.ui.glow.live.LiveHero
import org.openstrap.app.ui.glow.live.heroTint

/*
 * Ready's hero (board HaloReadyEveryKind): lit in the sport's colour, it holds the goal as the one big number, the
 * ruler that sets it (or the zone buttons) and one line under them. With no goal it shows last time instead. Under
 * the settings, last time's figures. Both are read from what Ready already holds and from the workouts on file.
 */

/**
 * The hero: [caption] over the big [number] and its [unit] (or [words] where there is no number), then [control]
 * (a ruler, the zone buttons) and the one [line] with an optional [lineAction] beside it, all lit in [color]. With
 * [input] the number can be typed ([TypedGoalNumber]).
 */
@Composable
internal fun GoalHero(
    color: Color,
    angle: Float,
    number: String?,
    unit: String?,
    line: String?,
    caption: String? = null,
    words: String? = null,
    size: BigNumber = BigNumber.Live,
    lineAction: (@Composable () -> Unit)? = null,
    input: HeroInput? = null,
    control: (@Composable () -> Unit)? = null,
) {
    val tint = heroTint(color)
    LiveHero(color, angle) {
        if (caption != null) HeroLine(caption, tint)
        if (number != null && input != null) TypedGoalNumber(number, unit, size, tint, input)
        else if (number != null) HaloBigNumber(number, size, unit = unit, unitColor = tint, fit = true)
        else if (words != null) Text(words, color = Color.White, style = GlowType.sentence)
        control?.invoke()
        if (lineAction == null) { if (line != null) HeroLine(line, tint) } else {
            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.S12),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                androidx.compose.foundation.layout.Box(Modifier.weight(1f)) { if (line != null) HeroLine(line, tint) }
                lineAction()
            }
        }
    }
}

/** What the last workout of the sport showed: the weekday, the figures it has (value, label) and the hero's version. */
class ReadyLast(val day: String, val figures: List<Pair<String, String>>, val hero: LastHero? = null)

/** Last time as the hero shows it with no goal: one big [number] and its [unit], and the rest in one [line]. */
class LastHero(val number: String, val unit: String?, val line: String?)

/**
 * The figures of [a] the sport's board shows (boards Ready*, "Last time"): a figure with no data is left out, so a
 * workout that has none of them gives nothing. Distance sports show distance, time and pace; strength sets and time;
 * HIIT the peak and time; mind and body time and average; games time, load and kcal; the rest time, average and kcal.
 */
internal fun readyLast(a: WorkoutAnalysis, today: LocalDate, zone: ZoneId, strings: Strings = Strings.app): ReadyLast? {
    val w = a.workout
    val family = Workouts.family(w.sportType)
    val clock = Units.length(a.activeSeconds)
    val time = clock to strings.get(R.string.glow_ready_last_time)
    val avg = w.averageHeartRate?.let { "$it" to strings.get(R.string.glow_ready_last_avg_bpm) }
    val peak = w.maxHeartRate?.let { "$it" to strings.get(R.string.glow_ready_last_peak_bpm) }
    val kcal = a.activeKcal?.toInt()?.let { "$it" to strings.get(R.string.glow_ready_last_kcal) }
    val load = w.trainingLoad?.let { "$it" to strings.get(R.string.glow_ready_last_load) }
    val sets = a.sets?.takeIf { it > 0 }?.let { "$it" to strings.get(R.string.glow_ready_last_sets) }
    val distance = a.distanceMeters?.takeIf { it >= Pace.FROM_METERS }
    val pace = Pace.clock(Pace.of(distance, a.activeSeconds))
    val figures = when (family) {
        Workouts.Family.Distance -> listOfNotNull(
            distance?.let { Units.distance(it, Units.Precision.Workout).orEmpty() to strings.get(R.string.glow_ready_last_distance) },
            time,
            pace?.let { it to strings.get(R.string.glow_ready_last_pace) },
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
    return ReadyLast(
        if (day == today) strings.get(R.string.glow_ready_last_today) else name, figures,
        lastHero(a, family, distance, pace, clock, figures.filter { it !== time }, strings),
    )
}

/** Last time as the hero shows it: a distance sport's distance, strength's sets, else the minutes; [rest] are the other figures. */
private fun lastHero(
    a: WorkoutAnalysis,
    family: Workouts.Family,
    distance: Float?,
    pace: String?,
    clock: String,
    rest: List<Pair<String, String>>,
    strings: Strings,
): LastHero = when {
    family == Workouts.Family.Distance && distance != null -> {
        val figure = Units.figure(distance, Units.Precision.Day, Units.DOT_LOCALE, strings)
        LastHero(
            figure?.number.orEmpty(), figure?.unit,
            pace?.let { strings.format(Units.res(R.string.ready_last_pace_line, R.string.ready_last_pace_line_mi), clock, it) } ?: clock,
        )
    }
    family == Workouts.Family.Strength && a.sets != null && a.sets > 0 -> LastHero(
        "${a.sets}", strings.plural(R.plurals.ready_hero_sets, a.sets), strings.format(R.string.ready_hero_in_all, clock),
    )
    else -> {
        // Load keeps its capital, as it is written everywhere else.
        val load = strings.get(R.string.glow_ready_last_load)
        LastHero(
            "${(a.activeSeconds / READY_S_PER_MIN).coerceAtLeast(1)}", strings.get(R.string.workout_start_unit_min),
            rest.joinToString(", ") { "${it.first} ${if (it.second == load) it.second else it.second.lowercase()}" }.ifEmpty { null },
        )
    }
}

/** "Last time, Tue" over last time's figures, plain, no card (workout-rules.md, section 2). Nothing without [last]. */
@Composable
internal fun ReadyLastRow(last: ReadyLast?) {
    if (last == null) return
    Column(Modifier.fillMaxWidth().padding(top = Space.S8), verticalArrangement = Arrangement.spacedBy(Space.S12)) {
        Text(
            stringResource(R.string.glow_ready_last_caption, last.day), color = GlowType.noteColor, style = GlowType.noteSmall,
            modifier = Modifier.padding(horizontal = Space.S4),
        )
        LiveFigures(last.figures.map { it.first to it.second })
    }
}

/** "Ends about 18:12" for a goal [seconds] long started at [nowMillis], null where nothing says how long it takes. */
@Composable
@androidx.compose.runtime.ReadOnlyComposable
internal fun endsAbout(seconds: Long?, nowMillis: Long): String? {
    if (seconds == null || seconds <= 0) return null
    // The phone's 12 or 24 hour setting, as every other time of day in the app.
    val at = (nowMillis + seconds * MS_PER_S) / MS_PER_S
    return stringResource(R.string.workout_start_ends_near, TimeOfDay.format(at, ZoneId.systemDefault()))
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
internal const val READY_S_PER_MIN = 60L
