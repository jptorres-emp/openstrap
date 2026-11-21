package org.openstrap.app.ui.glow.live

import androidx.compose.runtime.ReadOnlyComposable
import org.openstrap.app.ui.glow.HaloBigNumber
import org.openstrap.app.ui.glow.BigNumber
import org.openstrap.app.presentation.WorkoutColors
import org.openstrap.app.presentation.dotFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.openstrap.app.data.Phase
import org.openstrap.app.data.Route
import org.openstrap.app.data.WorkoutRecorder
import org.openstrap.app.data.db.AppWorkoutRound
import org.openstrap.app.device.ConnectionState
import org.openstrap.app.device.WorkoutState
import org.openstrap.app.ui.glow.HaloButton
import org.openstrap.app.ui.glow.HaloButtonVariant
import org.openstrap.app.ui.glow.HaloButtonSize
import org.openstrap.app.ui.glow.DotMatrixText
import org.openstrap.app.ui.glow.HeroValue
import org.openstrap.app.ui.glow.draw.DotSize
import org.openstrap.app.ui.glow.GlowType
import org.openstrap.app.ui.glow.PeakBars
import org.openstrap.app.ui.glow.Segment
import org.openstrap.app.ui.glow.SegmentBar
import org.openstrap.app.ui.glow.Raised
import org.openstrap.app.ui.theme.Ink
import org.openstrap.app.ui.workout.PausedGrey
import org.openstrap.app.ui.glow.activeSecondsState
import org.openstrap.app.ui.glow.workoutClock
import org.openstrap.app.ui.glow.dotClock
import org.openstrap.app.ui.theme.AppIcons
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.app.ui.workout.IntervalMoment
import org.openstrap.app.ui.workout.StartCopy
import org.openstrap.app.presentation.WorkoutCopy
import org.openstrap.app.ui.workout.ZONE_COLORS
import org.openstrap.app.ui.workout.intervalMoment
import org.openstrap.app.ui.dashboard.LocalHeartZones
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import org.openstrap.app.R

/**
 * HIIT (workout-rules.md, HIIT; boards HaloLiveHiitWork and HaloLiveHiitRest): the phase's hero lit red for work and
 * lilac for rest, with the round N of M and a bar of the rounds in it, the seconds left as the one dotted number and
 * what's next in its line. Work then has the heart rate in a solid card, rest each round's peak; the plain figures
 * row is Rounds, Peak bpm and Active kcal. Skip (Done, every minute) is the left action of work, +10 s of rest, with Skip
 * rest on the hero's title row. A warm up waits on Start rounds, which is then the white tap. With [split] the heart
 * rate is on the other half of a folded phone, so this half leaves it out.
 */
@Composable
@Suppress("UnusedParameter")
internal fun LiveIntervalsScreen(
    r: WorkoutState.Recording,
    liveHeartRate: Int?,
    readings: List<Pair<Long, Int>>,
    connection: ConnectionState,
    contentPadding: PaddingValues,
    controls: LiveControls,
    onSkip: () -> Unit,
    onAddRest: () -> Unit,
    split: Boolean = false,
    compact: Boolean = false,
    kcal: WorkoutRecorder.Kcal? = null,
) {
    val now = nowTickingState()
    val connected = connection is ConnectionState.Connected
    val bpm = liveHeartRate.takeIf { connected && !r.paused }
    ZoneTick(bpm, r)
    // The phase alone colours the screen and names the left button; the seconds left are read under it ([intervalMoment]
    // in the content), so the screen holds still between seconds.
    val phase = androidx.compose.runtime.remember(r) { androidx.compose.runtime.derivedStateOf { r.phase(now.value) } }.value
    if (r.structure == null || phase == null) return
    val color = when {
        r.paused -> PausedGrey
        phase is Phase.Rest -> RestColor
        else -> liveColor(r)
    }
    // Parsed once with the recording, not on each recomposition.
    val structure = r.structure
    val plan = structure?.let { StartCopy.structureName(it) }
    val active = activeSecondsState(r)
    val warmUp = phase == Phase.WarmUp
    LiveFrame(
        r, color, connected, contentPadding, controls, time = { active.value },
        battery = (connection as? ConnectionState.Connected)?.batteryPercent,
        lap = phaseLap(r, phase, onSkip, onAddRest),
        pauseWhite = !warmUp,
        paused = { LivePaused(r, liveHeartRate.takeIf { connected }, readings, roundFigures(r, kcal)) },
        fill = true,
        title = listOfNotNull(org.openstrap.app.data.Workouts.sportName(r.workout.sportType), plan).joinToString(", "),
    ) {
        val m = intervalMoment(r, now.value, bpm, readings, emptyList()) ?: return@LiveFrame
        PhaseHero(m, color, liveAngle(r), bpm, onSkip)
        if (!split && (m.phase is Phase.Rest || m.phase == Phase.Done)) RoundPeaks(r.rounds, m.rounds)
        if (!split && m.phase !is Phase.Rest) HeartRow(bpm, r.maxHeartRate, r.paused, readings)
        LiveFigures(roundFigures(r, kcal))
        Spacer(Modifier.weight(1f))
        // The phase's own next step as the one white tap: Start rounds after a warm up, Done on every minute's work.
        when (m.phase) {
            Phase.WarmUp -> HaloButton(
                stringResource(R.string.workout_live_start_rounds), onSkip, Modifier.fillMaxWidth(),
                HaloButtonVariant.Primary, HaloButtonSize.Main, icon = AppIcons.PlayFill,
            )
            is Phase.Work -> if (m.everyMinute) HaloButton(
                stringResource(R.string.workout_live_done_button), onSkip, Modifier.fillMaxWidth(),
                HaloButtonVariant.Primary, HaloButtonSize.Main, icon = AppIcons.Tick02,
            )
            else -> Unit
        }
    }
}

/**
 * The controls row's left action: Skip, Done (every minute's work, with a tick) or +10 s on a rest (board
 * HaloLiveHiitRest); nothing paused, in a warm up or when done.
 */
@Composable
private fun phaseLap(r: WorkoutState.Recording, phase: Phase, onSkip: () -> Unit, onAddRest: () -> Unit): (@Composable () -> Unit)? {
    if (r.paused) return null
    val done = phase is Phase.Work && r.structure is org.openstrap.app.data.Structure.EveryMinute
    return when (phase) {
        is Phase.Work -> {
            val label = stringResource(if (done) R.string.workout_live_done_button else R.string.workout_live_skip)
            val action: @Composable () -> Unit = { SideAction(label, if (done) AppIcons.Tick02 else AppIcons.SkipFill, onSkip) }
            action
        }
        is Phase.Rest -> {
            val label = stringResource(R.string.workout_live_add_10_s)
            val action: @Composable () -> Unit = { SideAction(label, AppIcons.Plus, onAddRest) }
            action
        }
        else -> null
    }
}

/** Rounds, Peak bpm and Active kcal (workout-rules.md, section 5), the figures row of both phases and of the paused view. */
@Composable
@ReadOnlyComposable
private fun roundFigures(r: WorkoutState.Recording, kcal: WorkoutRecorder.Kcal?): List<Pair<String?, String>> = listOf(
    "${r.roundsCounted}" to stringResource(R.string.workout_live_figure_rounds),
    (r.rounds.mapNotNull { it.peakBpm }.maxOrNull() ?: r.workout.maxHeartRate)?.toString() to
        stringResource(R.string.workout_live_figure_peak_bpm),
    kcal?.active?.toInt()?.toString() to stringResource(R.string.workout_live_figure_active_kcal),
)

/** The phase's hero (boards HaloLiveHiitWork and HaloLiveHiitRest): its word, the rounds, the seconds and what is next. */
@Composable
private fun PhaseHero(m: IntervalMoment, color: Color, angle: Float, bpm: Int?, onSkip: () -> Unit) {
    val tint = heroTint(color)
    LiveHero(color, angle) {
        val (title, note) = phaseTitle(m)
        HeroTitle(title, note, tint)
        RoundBar(m)
        val seconds = m.left?.let { dotFormat("%d", it) }
        if (seconds != null) HaloBigNumber(seconds, BigNumber.Live, unit = stringResource(secondsUnit(m)), unitColor = tint)
        HeroLine(phaseLine(m, bpm), tint)
        if (m.phase is Phase.Rest) HaloButton(
            stringResource(R.string.workout_live_skip_rest), onSkip, Modifier.fillMaxWidth(), size = HaloButtonSize.Card,
            variant = HaloButtonVariant.Glass,
        )
    }
}

/**
 * The rounds as a segmented bar: done at 55 percent, the one under way white,
 * a run rep's share of its metres), those to go faint.
 */
@Composable
internal fun RoundBar(m: IntervalMoment) {
    val done = when (m.phase) {
        is Phase.Work, is Phase.Rest -> m.round - 1
        Phase.Done -> m.rounds
        Phase.WarmUp -> 0
    }
    val described = pluralStringResource(R.plurals.workout_live_rounds_done_description, m.rounds, done, m.rounds)
    SegmentBar(
        List(m.rounds.coerceIn(1, MAX_ROUNDS_SHOWN)) { i ->
            // Done at 55 percent, the one under way white, the rest 18 percent (workout-rules.md, Progress); never dots.
            Segment(1f, if (i < done) Ink.Tertiary else if (i == done && m.phase !is Phase.Done) Ink.Primary else Ink.Line)
        },
        Modifier.semantics { contentDescription = described }, height = 6.dp, gap = 4.dp, corner = 3.dp,
    )
}

/** The most rounds the bar splits into, so each stays wide enough to read. */
private const val MAX_ROUNDS_SHOWN = 20

/** How many seconds of heart rate the Interval card's line shows: two minutes. */
private const val HEART_LINE_WINDOW_S = 120L

/** The heart rate in a solid card with its last two minutes as a line in the zone's colour (board HaloLiveHiitWork). */
@Composable
private fun HeartRow(bpm: Int?, maxHr: Int, paused: Boolean, readings: List<Pair<Long, Int>>) = LiveCard {
    HeartLine(bpm, maxHr, paused)
    val zone = org.openstrap.app.data.WorkoutRecorder.liveZone(bpm, maxHr, org.openstrap.app.ui.dashboard.LocalHeartZones.current)
    RecentHeartLine(readings, zone?.let { org.openstrap.app.ui.workout.ZONE_COLORS[it] } ?: Color.White, 48.dp, HEART_LINE_WINDOW_S, 2.4.dp)
    Text(stringResource(R.string.workout_live_last_2_min), color = GlowType.noteColor, style = GlowType.noteSmall)
}

/**
 * Each round's peak heart rate so far as bars in the peak's zone colour, rounds to come as faint stubs, with how far
 * the heart rate came down in each rest.
 */
@Composable
private fun RoundPeaks(rounds: List<AppWorkoutRound>, total: Int) {
    val peaks = rounds.filter { it.workEnd != null }.map { it.peakBpm }
    if (peaks.all { it == null }) return
    val shown = peaks.filterNotNull().joinToString(", ")
    val described = stringResource(R.string.workout_live_peaks_description, shown)
    LiveCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.workout_live_peaks_title), color = Color.White, style = GlowType.cardTitle,
                maxLines = 1, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.workout_live_unit_bpm), color = GlowType.noteColor, style = GlowType.noteSmall)
        }
        PeakBars(
            peaks, { MetricColors.heart },
            Modifier.semantics { contentDescription = described },
            height = 56.dp, rounds = total, corner = 3.dp,
        )
    }
}

/**
 * A run rep (workouts spec 2.5, board HaloLiveRunReps), in place of the Pace card: the hero lit in the run's lime, or
 * the rest's lilac, with rep N of M and the pace now, the metres to go (or the seconds of a time rep) as the one dotted
 * number, and the reps done with their times in its line.
 */
// The route and readings arrive whole with each update and never change in place.
@Suppress("UnstableCollections", "ComposeUnstableCollections")
@Composable
internal fun RepCard(r: WorkoutState.Recording, route: List<Route.Point>, readings: List<Pair<Long, Int>>, bpm: Int?) {
    val now = nowTicking()
    val m = intervalMoment(r, now, bpm, readings, route) ?: return
    val resting = m.phase is Phase.Rest
    val color = if (resting) RestColor else liveColor(r)
    val tint = heroTint(color)
    LiveHero(color, liveAngle(r)) {
        val pace = if (m.phase is Phase.Work) org.openstrap.app.presentation.Pace.clock(Route.currentPace(route)) else null
        HeroTitle(
            when (m.phase) {
                is Phase.Work -> stringResource(R.string.workout_live_rep_of, m.round, m.rounds)
                is Phase.Rest -> stringResource(R.string.workout_live_rest_rep_next, m.round)
                Phase.WarmUp -> stringResource(R.string.workout_live_warm_up)
                Phase.Done -> pluralStringResource(R.plurals.workout_live_all_reps_done, m.rounds, m.rounds)
            },
            pace?.let { stringResource(R.string.workout_live_pace_now, it) }, tint,
        )
        val (value, unit) = when {
            m.metersLeft != null && m.phase is Phase.Work -> "${m.metersLeft.toInt()}" to stringResource(R.string.workout_live_unit_m_to_go)
            m.left != null -> dotClock(m.left) to stringResource(
                if (resting) R.string.workout_live_unit_rest_left else R.string.workout_live_unit_left,
            )
            else -> null to ""
        }
        if (value != null) HaloBigNumber(value, BigNumber.Live, unit = unit, unitColor = tint)
        val done = r.rounds.filter { it.workEnd != null }
        val line = when {
            done.isNotEmpty() -> done.takeLast(6).joinToString(", ") { "${it.index + 1}: ${workoutClock(it.workEnd!! - it.workStart)}" }
            m.phase == Phase.WarmUp -> stringResource(R.string.workout_live_reps_start_hint)
            else -> ""
        }
        if (line.isNotEmpty()) HeroLine(line, tint)
    }
}
