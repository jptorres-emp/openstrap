package org.openstrap.app.ui.glow.live

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import org.openstrap.app.R
import org.openstrap.app.data.Phase
import org.openstrap.app.ui.workout.IntervalMoment
import org.openstrap.app.ui.workout.StartCopy

/* The words of HIIT's phase hero (boards HaloLiveHiitWork and HaloLiveHiitRest), apart from the screen that lays them out. */

/** The title and its note on the hero's first row. */
@Composable
@androidx.compose.runtime.ReadOnlyComposable
internal fun phaseTitle(m: IntervalMoment): Pair<String, String> = when (m.phase) {
    is Phase.Work -> stringResource(R.string.workout_live_phase_work) to
        stringResource(R.string.workout_live_round_of, m.round, m.rounds)
    is Phase.Rest -> stringResource(R.string.workout_live_phase_rest) to
        stringResource(R.string.workout_live_round_next, m.round)
    Phase.WarmUp -> stringResource(R.string.workout_live_warm_up) to
        pluralStringResource(R.plurals.workout_live_rounds_after, m.rounds, m.rounds)
    Phase.Done -> stringResource(R.string.workout_live_phase_done) to
        pluralStringResource(R.plurals.workout_live_all_rounds_done, m.rounds, m.rounds)
}

/** The hero's line: the rest to come, the heart rate coming down, or the next step. */
@Composable
internal fun phaseLine(m: IntervalMoment, bpm: Int?): String = when (m.phase) {
    is Phase.Work -> m.nextRest?.let { stringResource(R.string.workout_live_then_rest, StartCopy.restText(it)) }
        ?: stringResource(R.string.workout_live_last_round)
    is Phase.Rest -> bpm?.let { b ->
        m.drop?.takeIf { it > 0 }?.let { stringResource(R.string.workout_live_heart_rate_down, b, it) }
            ?: stringResource(R.string.workout_live_heart_rate, b)
    } ?: stringResource(R.string.workout_live_seconds_left)
    Phase.WarmUp -> stringResource(R.string.workout_live_rounds_start_hint)
    Phase.Done -> stringResource(R.string.workout_live_end_or_keep)
}

/** The unit after the seconds left: rest left, seconds left, or seconds left in the minute. */
internal fun secondsUnit(m: IntervalMoment): Int = when {
    m.phase is Phase.Rest -> R.string.workout_live_unit_rest_left
    m.everyMinute -> R.string.workout_live_seconds_left_minute
    else -> R.string.workout_live_seconds_left
}

