package org.openstrap.app.ui.glow.live

import org.openstrap.app.ui.glow.HaloBigNumber
import org.openstrap.app.ui.glow.BigNumber
import org.openstrap.app.ui.glow.HaloButtonSize
import org.openstrap.app.ui.glow.HaloButtonVariant
import org.openstrap.app.ui.glow.HaloButton
import org.openstrap.app.ui.theme.Greys
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import org.openstrap.app.ui.theme.Ink
import org.openstrap.app.ui.workout.FamilySummary
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.openstrap.app.data.Games
import org.openstrap.app.data.Phase
import org.openstrap.app.data.Structure
import org.openstrap.app.data.WorkoutRecorder
import org.openstrap.app.device.ConnectionState
import org.openstrap.app.device.WorkoutState
import org.openstrap.app.ui.glow.HeroValue
import org.openstrap.app.ui.glow.draw.DotSize
import org.openstrap.app.ui.glow.draw.GlowMotion
import org.openstrap.app.ui.glow.GlowType
import org.openstrap.app.ui.glow.Segment
import org.openstrap.app.ui.glow.SegmentBar
import org.openstrap.app.ui.glow.draw.LightAngle
import org.openstrap.app.ui.glow.activeSecondsState
import org.openstrap.app.ui.glow.draw.heroStyle
import org.openstrap.app.ui.glow.workoutClock
import org.openstrap.app.ui.glow.workoutColor
import org.openstrap.app.ui.theme.AppIcons
import org.openstrap.app.ui.workout.StartCopy
import org.openstrap.app.presentation.WorkoutCopy
import org.openstrap.app.ui.workout.ZoneSeconds
import org.openstrap.app.ui.dashboard.LocalHeartZones
import androidx.compose.ui.res.stringResource
import org.openstrap.app.R

/**
 * Games and sports (workouts spec 2.7, board LiveGame), made for the phone in a bag: the period and the time in it,
 * the heart rate in dots on a glow in its zone and the zone named, an effort bar (1 to 5 from the time in zones so
 * far), then active kcal, peak bpm and hard efforts (spells of 10 s or more in zone 5). Half time starts the break
 * early. The strap buzzes at the end of each period and break, so nothing needs the phone. Free play has no periods.
 */
@Composable
@Suppress("UnusedParameter")
internal fun LiveGameScreen(
    r: WorkoutState.Recording,
    liveHeartRate: Int?,
    readings: List<Pair<Long, Int>>,
    stats: WorkoutRecorder.Stats?,
    zoneSeconds: ZoneSeconds,
    connection: ConnectionState,
    contentPadding: PaddingValues,
    controls: LiveControls,
    kcal: WorkoutRecorder.Kcal?,
    onHalfTime: () -> Unit,
    /** Split across a fold: the heart rate is on the other half, so only the effort is left of the card. */
    split: Boolean = false,
    /** A short half: the total small on top, so the controls keep their labels. */
    compact: Boolean = false,
) {
    val heartZones = LocalHeartZones.current
    val now = nowTickingState()
    val connected = connection is ConnectionState.Connected
    val bpm = liveHeartRate.takeIf { connected && !r.paused }
    ZoneTick(bpm, r)
    val color = workoutColor(r, liveHeartRate, connected, heartZones)
    val periods = r.structure as? Structure.Periods
    // The phase alone picks the buttons; its clock is read in [PeriodRow], so the screen holds still between seconds.
    val phase = remember(r) { androidx.compose.runtime.derivedStateOf { r.phase(now.value) } }.value
    val active = activeSecondsState(r)
    val effort = Games.effort(zoneSeconds.toIntArray())
    val hard = remember(readings.size / 10, heartZones) { Games.hardEfforts(readings, r.maxHeartRate, heartZones) }
    val figures = listOf(
        kcal?.active?.toInt()?.toString() to activeKcalLabel(kcal != null),
        stats?.max?.toString() to stringResource(R.string.workout_live_figure_peak_bpm),
        hard.toString() to stringResource(R.string.workout_live_figure_hard_efforts),
    )
    val sport = liveColor(r)
    val tint = heroTint(sport)
    val inBreak = periods != null && phase is Phase.Rest
    LiveFrame(
        r, color, connected, contentPadding, controls, time = { active.value },
        battery = (connection as? ConnectionState.Connected)?.batteryPercent,
        // Half time stays on the left, the way every sport's own action does; a break's next step is the white tap.
        lap = if (periods != null && phase is Phase.Work && !r.paused) ({ SideAction(StartCopy.breakName(periods.rounds),
            AppIcons.PauseFill, onHalfTime) }) else null,
        pauseWhite = !inBreak,
        paused = { LivePaused(r, liveHeartRate.takeIf { connected }, readings, figures) },
    ) {
        val zone = WorkoutRecorder.liveZone(bpm, r.maxHeartRate, heartZones)
        // Board HaloLiveGame: the heart rate as the hero, the period in its one line, then the effort in a solid card.
        LiveHero(sport, liveAngle(r)) {
            HeroTitle(stringResource(R.string.workout_live_heart_title), null, tint)
            if (bpm != null) androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                HaloBigNumber(
                    "$bpm", BigNumber.Live, Modifier.weight(1f), unit = stringResource(R.string.workout_live_unit_bpm), unitColor = tint,
                )
                zone?.let { org.openstrap.app.ui.glow.ZoneChip(it, Modifier.padding(bottom = org.openstrap.app.ui.theme.Space.S8)) }
            }
            else Text(WorkoutCopy.noHeartRate(r.paused, connected), color = Color.White, style = GlowType.sentence)
            if (periods != null && phase != null) PeriodLine(r, periods, phase, { now.value }, { active.value }, tint)
        }
        if (!split) LiveCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(stringResource(R.string.workout_live_effort), color = Color.White, style = GlowType.cardTitle, maxLines = 1,
                    modifier = Modifier.weight(1f))
                Text(
                    effort?.let { stringResource(R.string.workout_live_effort_of,
                        it) } ?: stringResource(R.string.workout_live_effort_none),
                    color = GlowType.noteColor, style = GlowType.noteSmall,
                )
            }
            EffortBar(effort, tint)
            Box(Modifier.fillMaxWidth().height(1.dp).background(Greys.Control))
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(stringResource(R.string.workout_live_zones_title), color = GlowType.noteColor, style = GlowType.noteSmall,
                    maxLines = 1, modifier = Modifier.weight(1f))
                FamilySummary.mostlyZone(zoneSeconds)?.let {
                    Text(it, color = GlowType.noteColor, style = GlowType.noteSmall, maxLines = 1)
                }
            }
            ZoneTimeBar(zoneSeconds, zone)
        }
        LiveFigures(figures)
        Spacer(Modifier.weight(1f))
        if (periods != null && phase is Phase.Rest) HaloButton(
            stringResource(R.string.workout_live_game_start_period, StartCopy.periodName(phase.round + 1, periods.rounds).lowercase()),
            onHalfTime, Modifier.fillMaxWidth(), HaloButtonVariant.Primary, HaloButtonSize.Main, icon = AppIcons.PlayFill,
        )
    }
}

/** The period and the time in it, or the break and its time left, as the hero's line: its own scope, so only it reads each second. */
@Composable
private fun PeriodLine(r: WorkoutState.Recording, periods: Structure.Periods, phase: Phase, now: () -> Long,
    active: () -> Long, tint: Color) {
    val (title, clock) = when (phase) {
        is Phase.Work -> StartCopy.periodName(phase.round, periods.rounds) to
            workoutClock((WorkoutRecorder.activeSeconds(r.workout, now()) - WorkoutRecorder.activeSeconds(r.workout,
                phase.start)).coerceAtLeast(0))
        is Phase.Rest -> StartCopy.breakName(periods.rounds) to
            stringResource(
                R.string.workout_live_game_break_left,
                workoutClock((phase.endsAt - WorkoutRecorder.activeSeconds(r.workout, now())).coerceAtLeast(0)),
            )
        else -> stringResource(R.string.workout_live_game_full_time) to workoutClock(active())
    }
    HeroLine("$title, $clock", tint)
}

/** Effort 1 to 5 as five steps, lit up to it in the sport's colour (zone is only ever a chip); the words are the card's note. */
@Composable
internal fun EffortBar(effort: Int?, tint: Color = Color.White) {
    val described = effort?.let { stringResource(R.string.workout_live_effort_of, it) } ?: stringResource(R.string.workout_live_effort_none)
    SegmentBar(
        List(5) { i -> Segment(1f, if (effort != null && i < effort) tint else Ink.Edge) },
        Modifier.fillMaxWidth().semantics { contentDescription = described },
        height = 14.dp, gap = 4.dp, corner = 4.dp,
    )
}
