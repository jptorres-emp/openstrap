package org.openstrap.app.ui.glow.live

import org.openstrap.app.ui.theme.Space
import org.openstrap.app.presentation.Units
import androidx.compose.foundation.layout.Arrangement
import org.openstrap.app.ui.glow.draw.DotSize
import org.openstrap.app.ui.glow.HeroValue
import org.openstrap.app.ui.glow.GlowType
import org.openstrap.app.ui.glow.Segment
import org.openstrap.app.ui.glow.SegmentBar
import org.openstrap.app.presentation.WorkoutCopy
import androidx.compose.ui.Alignment
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.openstrap.app.data.WorkoutRecorder
import org.openstrap.app.device.ConnectionState
import org.openstrap.app.device.WorkoutState
import org.openstrap.app.ui.glow.activeSecondsState
import org.openstrap.app.ui.glow.workoutColor
import org.openstrap.app.ui.theme.Ink
import org.openstrap.app.ui.workout.ZONE_COLORS
import org.openstrap.app.ui.workout.ZoneSeconds
import org.openstrap.app.ui.dashboard.LocalHeartZones
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import org.openstrap.app.R

/**
 * Indoor cardio, and a distance sport without GPS (workouts spec 2.3, board Main): one view under the moving time.
 * Heart rate is the hero, on its zone arc with the time in each zone as a bar and the target if one is set; then
 * the last ten minutes of heart rate as a line over it, then the average and peak heart rate and active kcal, the
 * distance taking the peak's place when there is one.
 */
@Composable
internal fun LiveIndoorScreen(
    r: WorkoutState.Recording,
    liveHeartRate: Int?,
    recent: List<Pair<Long, Int>>,
    stats: WorkoutRecorder.Stats?,
    zoneSeconds: ZoneSeconds,
    connection: ConnectionState,
    contentPadding: PaddingValues,
    controls: LiveControls,
    onLap: () -> Unit,
    kcal: WorkoutRecorder.Kcal?,
    /** Split across a fold: the heart card is on the other half, so this one keeps the time in zones. */
    split: Boolean = false,
    /** A short screen: the total small on top, so the controls keep their labels, and the heart card on one row. */
    compact: Boolean = false,
) {
    val heartZones = LocalHeartZones.current
    val connected = connection is ConnectionState.Connected
    val color = workoutColor(r, liveHeartRate, connected, heartZones)
    ZoneTick(liveHeartRate.takeIf { connected && !r.paused }, r)
    val active = activeSecondsState(r)
    // Board HaloLiveIndoor: the average and the peak heart rate and the active kcal. A treadmill or a sport counted in
    // steps has a distance, which takes the peak's place ("2.14 km" from steps, or metres under a kilometre).
    val distance = r.workout.distanceMeters?.takeIf { it >= Units.SHOWN_FROM_METERS }?.let { m ->
        val shown = if (Units.isMajor(m)) liveDistance(m) else Units.minor(m) + " " + Units.minorUnit()
        shown to stringResource(
            if (r.workout.distanceFromSteps) R.string.workout_live_figure_from_steps else R.string.workout_live_figure_distance,
        )
    }
    val average = stats?.average?.toString() to stringResource(R.string.workout_live_figure_avg_bpm)
    val activeKcal = kcal?.active?.toInt()?.toString() to activeKcalLabel(kcal != null)
    val figures = if (distance != null) listOf(distance, average, activeKcal)
    else listOf(average, stats?.max?.toString() to stringResource(R.string.workout_live_figure_peak_bpm), activeKcal)
    LiveFrame(
        r, color, connected, contentPadding, controls,
        lap = if (!r.paused) ({ LapButton(onLap, label = true) }) else null,
        time = { active.value }, smallTime = compact, battery = (connection as? ConnectionState.Connected)?.batteryPercent,
        paused = { LivePaused(r, liveHeartRate.takeIf { connected }, recent, figures) },
    ) {
        // Time reps on the treadmill side: the rep above the heart.
        if (r.structure is org.openstrap.app.data.Structure.Reps) RepCard(r, emptyList(), recent,
            liveHeartRate.takeIf { connected && !r.paused })
        val reading = liveHeartRate.takeIf { connected && !r.paused }
        if (split) ZoneTimeBar(zoneSeconds, WorkoutRecorder.liveZone(reading, r.maxHeartRate, heartZones))
        else WorkoutHeartCard(
            r, liveHeartRate, zoneSeconds, connected, { active.value }, Modifier.fillMaxWidth(),
            kcal = kcal?.active, zoneBar = !compact, recent = recent,
        )
        LiveFigures(figures, Modifier.padding(top = Space.S4))
    }
}

/**
 * The time in each zone as one bar split into the five zones' colours, each as wide as its share; the zone you're in
 * is white so it stands out against its neighbours, and an empty zone keeps a sliver so the bar always shows all five.
 */
@Composable
internal fun ZoneTimeBar(zoneSeconds: ZoneSeconds, current: Int?) {
    val total = zoneSeconds.sum().coerceAtLeast(1)
    val res = LocalResources.current
    val each = zoneSeconds.withIndex().joinToString(", ") { (z, s) ->
        if (s < 60) res.getString(R.string.workout_live_zone_time_none, z + 1)
        else res.getString(R.string.workout_live_zone_time_minutes, z + 1, s / 60)
    }
    val described = stringResource(R.string.workout_live_zone_times_description, each)
    val segments = zoneSeconds.mapIndexed { z, s ->
        val color = when {
            z == current -> Color.White
            s == 0 -> Ink.Line
            else -> ZONE_COLORS[z]
        }
        Segment(maxOf(s.toFloat() / total, 0.04f), color)
    }
    SegmentBar(segments, Modifier.semantics { contentDescription = described }, height = 10.dp, gap = Space.S2, corner = 3.dp)
}
