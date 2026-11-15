package org.openstrap.app.ui.glow.live

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import org.openstrap.app.data.Route
import org.openstrap.app.data.WorkoutRecorder
import org.openstrap.app.data.WorkoutTarget
import org.openstrap.app.data.Workouts
import org.openstrap.app.device.ConnectionState
import org.openstrap.app.device.WorkoutState
import org.openstrap.app.ui.components.HapticEvent
import org.openstrap.app.ui.components.rememberHaptics
import org.openstrap.app.ui.glow.*
import org.openstrap.app.presentation.WorkoutCopy
import org.openstrap.app.ui.workout.ZONE_COLORS
import org.openstrap.app.ui.workout.ZoneSeconds
import org.openstrap.app.ui.glow.draw.GlowMotion
import org.openstrap.app.ui.glow.draw.GlowStyle
import org.openstrap.app.ui.glow.draw.DotSize
import org.openstrap.app.ui.glow.draw.chart
import org.openstrap.app.ui.dashboard.LocalHeartZones
import androidx.compose.ui.res.stringResource
import org.openstrap.app.R

/* -------------------------------------------- Live screen -------------------------------------------- */

/**
 * The workout as it records, in the current zone's colour. The card beats at the live heart rate on its zone arc, the
 * last ten minutes behind the number; the zone, its range and the time spent in it sit under the arc. A GPS workout
 * swipes between that card and the map, which keeps the heart rate on a chip, and opens on the view used last
 * ([liveView], saved through [onLiveView]). Back tucks it away; ending takes a one-second hold on the square so a
 * pocket can't end it. With [split] the heart card is on the other half of a folded phone ([GlowWorkoutGlance]), so
 * this half keeps the kind's own screen, without the heart rate where that's its hero, and all its controls.
 */
@Composable
fun GlowWorkoutLiveScreen(
    r: WorkoutState.Recording,
    liveHeartRate: Int?,
    recent: List<Pair<Long, Int>>,
    stats: WorkoutRecorder.Stats?,
    zoneSeconds: ZoneSeconds,
    connection: ConnectionState,
    contentPadding: PaddingValues,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onEnd: () -> Unit,
    onMinimize: () -> Unit,
    onEndAtPrompt: () -> Unit,
    onKeepGoing: () -> Unit,
    modifier: Modifier = Modifier,
    route: List<Route.Point> = emptyList(),
    history: List<Pair<Long, Int>> = emptyList(),
    onStartSet: () -> Unit = {},
    onSetDone: () -> Unit = {},
    onExtendRest: () -> Unit = {},
    onLap: () -> Unit = {},
    split: Boolean = false,
    liveView: Int? = null,
    onLiveView: (Int) -> Unit = {},
    kcal: WorkoutRecorder.Kcal? = null,
    /** Strength: the exercises logged before, naming the exercise, and the log card's Save. */
    exercises: List<org.openstrap.app.data.db.ExerciseRow> = emptyList(),
    onExercise: (String?) -> Unit = {},
    onLogSet: (index: Int, exercise: String?, reps: Int, kg: Float) -> Unit = { _, _, _, _ -> },
    /** Rounds: Skip, Skip rest, Done or Start rounds; and +10 s on a rest. */
    onSkip: () -> Unit = {},
    onAddRest: () -> Unit = {},
) {
    // Kept across the once-a-second recompositions, so what takes the controls can skip them.
    val controls = remember(onPause, onResume, onEnd, onMinimize, onEndAtPrompt, onKeepGoing) {
        LiveControls(onPause, onResume, onEnd, onMinimize, onEndAtPrompt, onKeepGoing)
    }
    // The merged readings are remembered where a screen takes them: built anew each recomposition, they grow with the
    // workout and stop the screens that take them from skipping.
    // Each kind of workout has its own screen (workouts spec 2.3), split across a fold too: Skip, +10 s, Half time and
    // the log card stay on the half under your hand. Only the screens whose hero is the heart rate leave it to the
    // other half.
    val family = Workouts.family(r.workout.sportType)
    // Under the fold of a phone stood like a laptop the half is short: the screens with a large figure fit a compact one.
    BoxWithConstraints(modifier.fillMaxSize()) {
        val compact = maxHeight < CompactHeight
        // On its side, the whole screen (a folded half keeps the layout made for it) puts the controls beside the
        // content, and keeps clear of a camera cutout, which is then on its left or right. The tree is the same either
        // way, so what a screen saves outlives a turn of the phone.
        val landscape = !split && maxWidth > maxHeight
        val cutout = if (!landscape) Modifier
        else Modifier.background(Color.Black).windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
        CompositionLocalProvider(LocalLiveLandscape provides landscape) {
            Box(Modifier.fillMaxSize().then(cutout)) {
                when {
                    family == Workouts.Family.Distance && r.workout.useGps ->
                        LiveDistanceScreen(r, liveHeartRate, recent, stats, connection, contentPadding, controls, route,
                            history, onLap, liveView, onLiveView, kcal, onSkip, split, compact)
                    family == Workouts.Family.Strength -> LiveStrengthScreen(
                        r, liveHeartRate, remember(history, recent) { WorkoutCopy.readings(history, recent) }, connection, contentPadding,
                        controls, exercises,
                        onStartSet, onSetDone, onExtendRest, onExercise, onLogSet, compact,
                    )
                    family == Workouts.Family.Intervals && r.structure != null ->
                        LiveIntervalsScreen(
                            r, liveHeartRate, remember(history, recent) { WorkoutCopy.readings(history, recent) }, connection,
                            contentPadding, controls, onSkip, onAddRest, split, compact, kcal,
                        )
                    family == Workouts.Family.MindBody -> LiveMindScreen(r, liveHeartRate, recent, connection,
                        contentPadding, controls, split, compact)
                    family == Workouts.Family.Games ->
                        LiveGameScreen(
                            r, liveHeartRate, remember(history, recent) { WorkoutCopy.readings(history, recent) }, stats, zoneSeconds,
                            connection, contentPadding, controls, kcal, onSkip, split, compact,
                        )
                    // Indoor cardio, a distance sport without GPS, and HIIT's Free, which has no timer: heart rate only, laps mark
                    // the rounds.
                    else -> LiveIndoorScreen(r, liveHeartRate, recent, stats, zoneSeconds, connection, contentPadding,
                        controls, onLap, kcal, split, compact)
                }
            }
        }
    }
}

/** Below this height (half a phone stood like a laptop) a live screen shows its figures compact. */
private val CompactHeight = 480.dp

/**
 * "Km 3 in 5:42, avg 152" under the stats for ten seconds after each kilometre, with one tick. Kilometres done before
 * the screen opened have had theirs.
 */
@Composable
internal fun KmSplitLine(
    route: List<Route.Point>, readings: List<Pair<Long, Int>>, pauses: List<LongRange>,
    /** Its line's height is kept while there's none, so views filling the screen don't shrink when it shows. */
    keepRoom: Boolean = false,
    /** The line's colour and whether it is centred: a hero's line is left aligned in its tint. */
    color: Color = Color.White,
    center: Boolean = true,
) {
    val splits = remember(route.size, pauses) { Route.splits(route, pauses) }
    val now = nowTickingState()
    val first = route.firstOrNull()?.t
    // The second is read by the derived line, so this composes again only when the line comes or goes.
    val line = remember(splits, readings, first) { derivedStateOf { WorkoutCopy.splitLine(splits, readings, first, now.value) } }.value
    val haptics = rememberHaptics()
    var ticked by remember { mutableIntStateOf(WorkoutCopy.wholeKms(splits)) }
    LaunchedEffect(line?.index) {
        if (line != null && WorkoutCopy.splitTick(line, ticked)) {
            haptics.perform(HapticEvent.KmSplit)
            ticked = line.index
        }
    }
    if (line != null || keepRoom) Text(
        line?.text.orEmpty(), color = color,
            style = if (center) MediumBody.copy(fontFeatureSettings = "tnum") else GlowType.secondary.copy(fontFeatureSettings = "tnum"),
        modifier = Modifier.fillMaxWidth(), textAlign = if (center) TextAlign.Center else TextAlign.Start,
    )
}

/**
 * The live heart rate as the hero (workout-rules.md, Rowing; board HaloLiveIndoor): the sport's colour lights it, the
 * bpm is the one dotted number, and one line says the zone, its range and the time in it. With [zoneBar] a solid card
 * under it holds the time in each zone as one bar and the target's note. With [fill] it takes all the height it's
 * given, for the standing half of a fold. Paused, it turns grey.
 */
@Composable
internal fun WorkoutHeartCard(
    r: WorkoutState.Recording,
    liveHeartRate: Int?,
    zoneSeconds: ZoneSeconds,
    connected: Boolean,
    /** The active seconds, read only by the time target's note, so the card isn't composed again each second. */
    active: () -> Long,
    modifier: Modifier,
    fill: Boolean = false,
    /** The live active kcal, for a kcal target's progress. */
    kcal: Float? = null,
    /** The time in each zone as a bar in a card under the hero (indoor cardio, board HaloLiveIndoor). */
    zoneBar: Boolean = false,
) {
    val heartZones = LocalHeartZones.current
    val bpm = liveHeartRate.takeIf { connected && !r.paused }
    val zone = WorkoutRecorder.liveZone(bpm, r.maxHeartRate, heartZones)
    val color = liveColor(r)
    val tint = heroTint(color)
    LiveHero(color, liveAngle(r), if (fill) modifier.fillMaxSize() else modifier) {
        HeroTitle(
            stringResource(R.string.workout_live_heart_title),
            bpm?.let { stringResource(R.string.workout_live_heart_of_max, it * 100 / r.maxHeartRate) }, tint,
        )
        if (bpm != null) HeroNumber("$bpm", stringResource(R.string.workout_live_unit_bpm), tint)
        else Text(WorkoutCopy.noHeartRate(r.paused, connected), color = Color.White, style = GlowType.sentence)
        if (zone != null) {
            val (name, _) = WorkoutCopy.zoneTitle(zone)
            HeroLine("$name, ${zoneRange(zone, r.maxHeartRate)}, ${WorkoutCopy.zoneTime(zoneSeconds[zone])}", tint)
        } else HeroLine(WorkoutCopy.noZone(r.paused), tint)
    }
    if (zoneBar && !fill) LiveCard {
        Text(stringResource(R.string.workout_live_zones_title), color = Color.White, style = GlowType.cardTitle)
        ZoneTimeBar(zoneSeconds, zone)
        HeartNotes(r, zone, zoneSeconds, active, kcal)
    }
}

/** The time in the [zone] you're in on the left of the heart card's foot, and the target's progress on the right. */
@Composable
internal fun HeartNotes(r: WorkoutState.Recording, zone: Int?, zoneSeconds: ZoneSeconds, active: () -> Long, kcal: Float?) {
    Row {
        zone?.let { PageNote(WorkoutCopy.zoneTime(zoneSeconds[it])) }
        Spacer(Modifier.weight(1f))
        when (val t = WorkoutTarget.parse(r.workout.target)) {
            is WorkoutTarget.Zones -> PageNote(
                if (t.low == t.high) stringResource(R.string.workout_live_target_zone, t.low + 1)
                else stringResource(R.string.workout_live_target_zones, t.low + 1, t.high + 1),
            )
            is WorkoutTarget.Time -> TimeTargetNote(active, t.seconds)
            is WorkoutTarget.Kcal -> PageNote(WorkoutCopy.kcalProgress(kcal, t))
            is WorkoutTarget.Distance -> PageNote(WorkoutCopy.distanceProgress(r.workout.distanceMeters, t))
            null -> Unit
        }
    }
}

/**
 * The standing half of a foldable half-open during a workout (on a table, or stood up like a book): the live heart
 * card as big as the half allows, readable from a step away. The controls stay on the other half, under your hand.
 */
@Suppress("UnusedParameter")
@Composable
fun GlowWorkoutGlance(
    r: WorkoutState.Recording,
    liveHeartRate: Int?,
    recent: List<Pair<Long, Int>>,
    zoneSeconds: ZoneSeconds,
    connection: ConnectionState,
    modifier: Modifier = Modifier,
    kcal: WorkoutRecorder.Kcal? = null,
) {
    val connected = connection is ConnectionState.Connected
    val active = activeSecondsState(r)
    Box(modifier.background(Color.Black).statusBarsPadding().padding(horizontal = GlowLayout.Gutter, vertical = 12.dp)) {
        WorkoutHeartCard(
            r, liveHeartRate, zoneSeconds, connected, { active.value }, Modifier.fillMaxSize(), fill = true, kcal = kcal?.active,
        )
    }
}

/** "12:30 of 30 min" under the heart card, [seconds] the goal's: its own scope, so only it reads the time. */
@Composable
private fun TimeTargetNote(active: () -> Long, seconds: Long) {
    val minutes = seconds / 60
    PageNote(stringResource(R.string.workout_live_time_target, workoutClock(active()), minutes))
}

private const val BUCKET_S = 5L
private const val WINDOW_S = 600L

/** The last ten minutes in five-second averages, oldest first; gaps stay null and break the line. */
internal fun bucketRecent(recent: List<Pair<Long, Int>>, now: Long): List<Float?> {
    val from = now - WINDOW_S + 1
    return org.openstrap.app.data.Summaries.bucketMeans(recent, (WINDOW_S / BUCKET_S).toInt(),
        { ((it.first - from) / BUCKET_S).toInt() }, { it.second.toFloat() })
}
