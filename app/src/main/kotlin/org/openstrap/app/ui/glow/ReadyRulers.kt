@file:Suppress("MatchingDeclarationName")

package org.openstrap.app.ui.glow

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Locale
import org.openstrap.app.R
import org.openstrap.app.presentation.Units
import org.openstrap.app.ui.workout.StartCopy

/*
 * Ready's rulers (board HaloReadyEveryKind): a time, a distance or a count on [HaloRuler], each with its steps and its
 * labelled ticks, and the big number each sets as it reads.
 */

/** The room each step takes on a ruler, by what it counts (board HaloReadyEveryKind). */
internal object ReadyRulerSpec {
    val TimeStep = 18.dp
    val DistanceStep = 14.dp
    val SetStep = 34.dp
    val RoundStep = 16.dp
    val RepStep = 30.dp
    val PeriodStep = 44.dp
}

/** A time goal: 5 minute steps from 5 min to [StartCopy.MAX_HOURS], labelled every quarter hour ("15", "1 h", "1 h 15"). */
@Composable
internal fun TimeRuler(seconds: Long, onSeconds: (Long) -> Unit, tint: Color) {
    val count = (StartCopy.MAX_HOURS * SECONDS_PER_HOUR / TIME_STEP_S).toInt()
    val labels = (0 until count).filter { (it + 1) % STEPS_A_QUARTER == 0 }.associateWith { i ->
        val minutes = (i + 1) * TIME_STEP_S / READY_S_PER_MIN
        val h = (minutes / MINUTES_PER_HOUR).toInt()
        val m = (minutes % MINUTES_PER_HOUR).toInt()
        when {
            h == 0 -> "$m"
            m == 0 -> stringResource(R.string.workout_start_hours, h)
            else -> stringResource(R.string.workout_start_hours_minutes, h, m)
        }
    }
    val (n, unit) = timeFigure(seconds)
    HaloRuler(
        (seconds / TIME_STEP_S - 1).toInt().coerceIn(0, count - 1), count, { onSeconds((it + 1) * TIME_STEP_S) }, labels,
        ReadyRulerSpec.TimeStep, "$n $unit", tint, description = stringResource(R.string.ready_goal),
    )
}

/** A distance goal: half km steps from 0.5 km to [StartCopy.MAX_KM], labelled at 1, 3 and 5 km, then every 5 km. */
@Composable
internal fun DistanceRuler(meters: Int, onMeters: (Int) -> Unit, tint: Color) {
    val count = StartCopy.MAX_KM * 1000 / DISTANCE_STEP_M
    val labels = (0 until count).mapNotNull { i ->
        val m = (i + 1) * DISTANCE_STEP_M
        val km = m / 1000
        (i to "$km").takeIf { m % 1000 == 0 && (km in DISTANCE_LABELS || km % 5 == 0) }
    }.toMap()
    HaloRuler(
        ((meters + DISTANCE_STEP_M / 2) / DISTANCE_STEP_M - 1).coerceIn(0, count - 1), count, { onMeters((it + 1) * DISTANCE_STEP_M) },
        labels, ReadyRulerSpec.DistanceStep, "${distanceFigure(meters)} ${stringResource(R.string.glow_unit_km)}", tint,
        description = stringResource(R.string.ready_goal),
    )
}

/**
 * A count in [range] a step at a time ([stepWidth] each), labelled every [labelEvery] from the start of it (every one
 * by default); [valueText] says the value with its unit.
 */
@Composable
internal fun CountRuler(
    value: Int, range: IntRange, onValue: (Int) -> Unit, stepWidth: Dp, valueText: String, tint: Color, labelEvery: Int = 1,
) {
    val count = range.last - range.first + 1
    val labels = (0 until count).filter { (range.first + it) % labelEvery == 0 }.associateWith { "${range.first + it}" }
    HaloRuler(
        (value - range.first).coerceIn(0, count - 1), count, { onValue(range.first + it) }, labels, stepWidth, valueText, tint,
        description = stringResource(R.string.ready_goal),
    )
}

/** A time goal as the big number and its unit: "30" min under an hour, else "1:15" h. */
@Composable
@androidx.compose.runtime.ReadOnlyComposable
internal fun timeFigure(seconds: Long): Pair<String, String> {
    val minutes = seconds / READY_S_PER_MIN
    return if (minutes < MINUTES_PER_HOUR) "$minutes" to stringResource(R.string.glow_unit_min)
    else String.format(Locale.ROOT, "%d:%02d", minutes / MINUTES_PER_HOUR, minutes % MINUTES_PER_HOUR) to
        stringResource(R.string.glow_unit_h)
}

/** A distance goal as the big number, to a tenth of a km in the dots: "5.0", "12.5". */
internal fun distanceFigure(meters: Int): String = Units.km(meters.toFloat(), Units.Precision.Day, Units.DOT_LOCALE)

/** "6 reps of 400 m" after the count. */
@Composable
@androidx.compose.runtime.ReadOnlyComposable
internal fun repsUnit(reps: org.openstrap.app.data.Structure.Reps): String =
    pluralStringResource(R.plurals.ready_hero_reps_of, reps.rounds, StartCopy.repText(reps))

private const val MINUTES_PER_HOUR = 60L
private const val SECONDS_PER_HOUR = 3600L
private const val TIME_STEP_S = 300L
private const val STEPS_A_QUARTER = 3
private const val DISTANCE_STEP_M = 500
private val DISTANCE_LABELS = setOf(1, 3, 5)
