package org.openstrap.app.ui.glow.live

import org.openstrap.app.ui.glow.HaloBigNumber
import org.openstrap.app.ui.glow.BigNumber
import org.openstrap.app.ui.theme.Corner
import org.openstrap.app.ui.theme.Space
import org.openstrap.app.ui.theme.Greys
import org.openstrap.app.presentation.Units
import org.openstrap.app.presentation.Pace
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.openstrap.app.data.Route
import org.openstrap.app.data.WorkoutRecorder
import org.openstrap.app.data.SportTypes
import org.openstrap.app.data.Workouts
import org.openstrap.app.device.ConnectionState
import org.openstrap.app.device.WorkoutState
import org.openstrap.app.ui.components.HapticEvent
import org.openstrap.app.ui.components.rememberHaptics
import org.openstrap.app.ui.glow.GlowLayout
import org.openstrap.app.ui.glow.draw.DotSize
import org.openstrap.app.ui.glow.GlowType
import org.openstrap.app.ui.glow.MediumBody
import org.openstrap.app.ui.glow.TrendBad
import org.openstrap.app.ui.glow.TrendGood
import org.openstrap.app.ui.glow.activeSecondsState
import org.openstrap.app.ui.glow.workoutColor
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.app.ui.glow.dotFitScale
import org.openstrap.app.presentation.LiveView
import org.openstrap.app.presentation.WorkoutCopy
import org.openstrap.app.ui.glow.WaitingDash
import org.openstrap.app.ui.glow.workoutClock
import org.openstrap.app.ui.glow.dotClock
import org.openstrap.app.ui.dashboard.LocalHeartZones
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import org.openstrap.app.R

/**
 * A distance workout with GPS (workouts spec 2.3, boards Run and Map): two views, swiped, under the moving time. Pace:
 * the pace now large on a card lit in the distance lime, the average beside its title and a bar per kilometre, the one
 * under way growing; the heart rate on a strip in its zone's colour; then distance, climb and active kcal. Map: the
 * route in zone colours with the heart rate on it, then distance, pace and the last split. Opens on the view used last
 * ([liveView], saved through [onLiveView]). A ride shows its speed instead of a pace. With [split] the heart rate is on
 * the other half of a folded phone, so both views leave out their own; [compact] (a short half) keeps the moving time small
 * on top and the distance, pace and kcal in one row.
 */
@Composable
internal fun LiveDistanceScreen(
    r: WorkoutState.Recording,
    liveHeartRate: Int?,
    recent: List<Pair<Long, Int>>,
    stats: WorkoutRecorder.Stats?,
    connection: ConnectionState,
    contentPadding: PaddingValues,
    controls: LiveControls,
    route: List<Route.Point>,
    history: List<Pair<Long, Int>>,
    onLap: () -> Unit,
    liveView: Int?,
    onLiveView: (Int) -> Unit,
    kcal: WorkoutRecorder.Kcal?,
    /** Run reps: Skip the rep or the rest, or start the reps after a warm up. */
    onSkip: () -> Unit = {},
    split: Boolean = false,
    compact: Boolean = false,
) {
    val heartZones = LocalHeartZones.current
    val pager = rememberPagerState(initialPage = LiveView.of(liveView).ordinal) { LiveView.entries.size }
    var viewApplied by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(liveView) {
        if (!viewApplied && liveView != null) {
            pager.scrollToPage(LiveView.of(liveView).ordinal)
            viewApplied = true
        }
    }
    // Only a swipe or a dot saves the view, never the page the screen opened on.
    val saveView by rememberUpdatedState(onLiveView)
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.drop(1).collect { saveView(it) } }
    val scope = rememberCoroutineScope()
    val connected = connection is ConnectionState.Connected
    val color = workoutColor(r, liveHeartRate, connected, heartZones)
    val bpm = liveHeartRate.takeIf { connected && !r.paused }
    ZoneTick(bpm, r)
    val distance = r.workout.distanceMeters ?: 0f
    val splits = remember(route.size, r.workout.pauseRanges) { Route.splits(route, r.workout.pauseRanges) }
    val climb = remember(route.size) { Route.climb(route) }
    val speed = Workouts.tileType(r.workout.sportType) !in SportTypes.ON_FOOT
    val pace = remember(route) { Route.currentPace(route) }
    val active = activeSecondsState(r)
    // Read where it shows, so the screen holds still between seconds.
    val averagePace = { Pace.of(distance, active.value) }
    val accuracy = route.lastOrNull()?.accuracy
    val reps = r.structure is org.openstrap.app.data.Structure.Reps
    val gps = WorkoutCopy.gpsPill(accuracy) to if (accuracy != null && accuracy <= Route.MAX_ACCURACY_M) TrendGood else TrendBad
    // Kept across recompositions, so the frames and pages that take them can skip while nothing they show changed.
    val status = remember(gps) { listOf(gps) }
    val readings = remember(history, recent) { WorkoutCopy.readings(history, recent) }
    // Reps skip instead of marking laps: the rounds are the laps.
    val lap: (@Composable () -> Unit)? = when {
        r.paused -> null
        reps -> ({ LapButton(onSkip, label = true, skip = true) })
        else -> ({ LapButton(onLap, label = true) })
    }
    val battery = (connection as? ConnectionState.Connected)?.batteryPercent
    if (r.paused) {
        LiveFrame(
            r, color, connected, contentPadding, controls, lap, time = { active.value }, battery = battery, status = status,
            paused = {
                LivePaused(r, liveHeartRate.takeIf { connected }, recent, listOf(
                    km(distance) to stringResource(R.string.workout_live_figure_distance),
                    (if (speed) kmh(speedText(averagePace())) else Pace.clock(averagePace())) to
                        stringResource(if (speed) R.string.workout_live_figure_avg_speed else R.string.workout_live_figure_avg_pace),
                    stats?.average?.toString() to stringResource(R.string.workout_live_figure_avg_bpm),
                ))
            },
        ) {}
        return
    }
    val onPage: (Int) -> Unit = { scope.launch { pager.animateScrollToPage(it) } }
    // Only what's above the dots slides: the note, the dots and the controls stay put under the pager, the dots
    // following the swipe. A drag on the map pans the map instead.
    val density = LocalDensity.current
    var measured by remember { mutableStateOf(240.dp) }
    // On its side the bar is a column on the right, so the pages leave no room under them but keep clear of it on
    // their right, the pages' glow still across the whole screen.
    val landscape = LocalLiveLandscape.current
    val bar = if (landscape) 0.dp else measured
    val direction = androidx.compose.ui.platform.LocalLayoutDirection.current
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val side = landscapeControlsWidth(maxWidth)
        val clear = if (landscape) side else 0.dp
        val pagePadding = if (!landscape) contentPadding else PaddingValues(
            start = contentPadding.calculateStartPadding(direction), top = contentPadding.calculateTopPadding(),
            end = side, bottom = contentPadding.calculateBottomPadding(),
        )
        val mapStart = if (landscape) pagePadding.calculateStartPadding(direction) else 0.dp
        HorizontalPager(pager, Modifier.fillMaxSize(), verticalAlignment = Alignment.Top, beyondViewportPageCount = 1) { page ->
            when (LiveView.entries[page]) {
                // Reps (board RunIntervals): the total small on top, the rep the screen's number.
                LiveView.Heart -> if (reps) LiveFrame(
                    r, color, connected, pagePadding, controls, lap, time = { active.value }, smallTime = true, battery = battery,
                    status = status,
                    fill = true, title = stringResource(R.string.workout_live_reps_title, Workouts.sportName(r.workout.sportType)),
                    bottomRoom = bar,
                ) {
                    RepPage(r, route, readings, bpm, color, if (shortLandscape(compact)) Modifier else Modifier.weight(1f))
                } else LiveFrame(
                    r, color, connected, pagePadding, controls, lap, time = { active.value }, smallTime = compact, battery = battery,
                    status = status, fill = true, bottomRoom = bar,
                ) {
                    if (compact) Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        LiveFigures(listOf(
                            km(distance) to stringResource(R.string.workout_live_figure_distance),
                            (if (speed) kmh(speedText(pace)) else Pace.clock(pace)) to
                                stringResource(if (speed) R.string.workout_live_figure_speed else R.string.workout_live_figure_pace),
                            kcal?.active?.toInt()?.toString() to activeKcalLabel(kcal != null),
                        ))
                    } else Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(Space.S12)) {
                        // Board HaloLiveRun: the pace as the lit hero, the splits and the heart rate in a solid card, the figures.
                        PaceHero(r, pace, averagePace, speed, route, readings, Modifier.fillMaxWidth())
                        SplitsCard(splits, bpm, r, showHeart = !split)
                        LiveFigures(listOf(
                            km(distance) to stringResource(R.string.workout_live_figure_distance),
                            climb?.toInt()?.toString() to stringResource(R.string.workout_live_figure_climb),
                            kcal?.active?.toInt()?.toString() to activeKcalLabel(kcal != null),
                        ))
                    }
                    // Each kilometre gets a line for a few seconds, with one tick, in the pace hero. On a short screen the
                    // hero is left out, so the line follows the figures.
                    if (compact) KmSplitLine(route, readings, r.workout.pauseRanges)
                }
                // On its side the map stops short of the bar, so the controls stay on black, and of the nav bar.
                LiveView.Map -> Box(Modifier.fillMaxSize().padding(start = mapStart, end = clear)) {
                    LiveMapScreen(
                        r, route, readings, bpm, connected, contentPadding, controls,
                        time = { active.value }, battery = battery, status = status,
                        figures = listOf(
                            km(distance) to stringResource(R.string.workout_live_figure_distance),
                            (if (speed) kmh(speedText(averagePace())) else Pace.clock(averagePace())) to
                                stringResource(
                                    if (speed) R.string.workout_live_figure_avg_speed else R.string.workout_live_figure_avg_pace,
                                ),
                            kcal?.active?.toInt()?.toString() to activeKcalLabel(kcal != null),
                        ),
                        bottomRoom = bar, heartPill = !split,
                    )
                }
            }
        }
        LiveBottomBar(
            r, connected, contentPadding, controls, lap, { pager.currentPage + pager.currentPageOffsetFraction },
                LiveView.entries.size, onPage,
            onHeight = { if (it != measured) measured = it },
            modifier = if (landscape) Modifier.align(Alignment.CenterEnd).width(side) else Modifier.align(Alignment.BottomCenter),
        )
    }
}

/**
 * A run's reps (workouts spec 2.5, board RunIntervals): the plan and a bar of the reps, rep N of M and the rest after
 * it, the metres to go in the screen's one dotted number (the seconds of a time rep) with the rep's own pace, the heart
 * rate strip, then each rep done with its time. A rest shows its seconds left and the rep after it.
 */
@Composable
private fun RepPage(
    r: WorkoutState.Recording, route: List<Route.Point>, readings: List<Pair<Long, Int>>, bpm: Int?, color: Color,
    modifier: Modifier,
) {
    val now = nowTicking()
    val m = org.openstrap.app.ui.workout.intervalMoment(r, now, bpm, readings, route) ?: return
    val structure = r.structure as? org.openstrap.app.data.Structure.Reps ?: return
    val (repLength, repUnit) = org.openstrap.app.ui.workout.StartCopy.repParts(structure)
    val repName = stringResource(R.string.workout_live_rep_length, repLength, repUnit)
    val rest = m.phase is org.openstrap.app.data.Phase.Rest
    val sport = if (rest) RestColor else color
    val tint = heroTint(sport)
    // Board HaloLiveRunReps: the rep as the lit hero with its bar, the plan and each rep's time in a solid card with the
    // heart rate under a rule, then the plain figures.
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.S12)) {
        LiveHero(sport, liveAngle(r)) {
            val next = when (m.phase) {
                is org.openstrap.app.data.Phase.Work ->
                    m.nextRest?.let { stringResource(R.string.workout_live_then_rest, org.openstrap.app.ui.workout.StartCopy.restText(it)) }
                        ?: stringResource(R.string.workout_live_last_rep)
                is org.openstrap.app.data.Phase.Rest -> stringResource(R.string.workout_live_rep_next, m.round)
                org.openstrap.app.data.Phase.WarmUp -> pluralStringResource(R.plurals.workout_live_reps_after, m.rounds, m.rounds)
                org.openstrap.app.data.Phase.Done -> ""
            }
            HeroTitle(
                when (m.phase) {
                    is org.openstrap.app.data.Phase.Work -> stringResource(R.string.workout_live_rep_of, m.round, m.rounds)
                    is org.openstrap.app.data.Phase.Rest -> stringResource(R.string.workout_live_done_of, m.round - 1, m.rounds)
                    org.openstrap.app.data.Phase.WarmUp -> stringResource(R.string.workout_live_warm_up)
                    org.openstrap.app.data.Phase.Done -> pluralStringResource(R.plurals.workout_live_all_reps_done, m.rounds, m.rounds)
                },
                next, tint,
            )
            RoundBar(m)
            val (value, unit) = when {
                m.phase is org.openstrap.app.data.Phase.Work &&
                    m.metersLeft != null -> "${m.metersLeft.toInt()}" to stringResource(R.string.workout_live_unit_m_to_go)
                m.left != null -> dotClock(m.left) to stringResource(
                    if (rest) R.string.workout_live_unit_rest_left else R.string.workout_live_unit_left,
                )
                else -> null to ""
            }
            if (value != null) HaloBigNumber(value, BigNumber.Live, unit = unit, unitColor = tint)
            val line = when {
                m.phase is org.openstrap.app.data.Phase.Work -> Route.currentPace(route)?.let { pace ->
                    structure.meters?.let { meters -> workoutClock((pace * meters / 1000).toLong()) }
                        ?.let { stringResource(R.string.workout_live_rep_pace, it, repName) }
                        ?: Pace.clock(pace)?.let { stringResource(R.string.workout_live_rep_pace_km, it) }
                }
                m.phase == org.openstrap.app.data.Phase.WarmUp -> stringResource(R.string.workout_live_reps_skip_hint)
                m.phase == org.openstrap.app.data.Phase.Done -> stringResource(R.string.workout_live_end_or_keep)
                else -> null
            }
            if (line != null) HeroLine(line, tint)
        }
        LiveCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(
                        R.string.workout_live_reps_plan,
                        org.openstrap.app.ui.workout.StartCopy.structureName(structure),
                        org.openstrap.app.ui.workout.StartCopy.restText(structure.rest),
                    ),
                    color = Color.White, style = GlowType.cardTitle, maxLines = 1, modifier = Modifier.weight(1f),
                )
            }
            val done = r.rounds.filter { it.workEnd != null }
            if (done.isNotEmpty()) Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState(Int.MAX_VALUE)),
                horizontalArrangement = Arrangement.spacedBy(Space.S8),
            ) {
                done.forEach { round ->
                    Column(
                        Modifier.clip(Corner.Inner).background(Greys.Control)
                            .padding(horizontal = Space.S16, vertical = Space.S12),
                        verticalArrangement = Arrangement.spacedBy(Space.S4),
                    ) {
                        Text(stringResource(R.string.workout_live_rep_chip, round.index + 1), color = GlowType.noteColor,
                            style = GlowType.noteSmall, maxLines = 1)
                        Text(workoutClock(round.workEnd!! - round.workStart), color = Color.White,
                            style = GlowType.figureInline, maxLines = 1)
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Greys.Control))
            HeartLine(bpm, r.maxHeartRate, r.paused)
        }
    }
}


/** A tick when the zone changes (spec 3.6); a pause forgets the zone, so resuming in another doesn't tick. */
@Composable
internal fun ZoneTick(bpm: Int?, r: WorkoutState.Recording) {
    val heartZones = LocalHeartZones.current
    val haptics = rememberHaptics()
    val zone = WorkoutRecorder.liveZone(bpm, r.maxHeartRate, heartZones)
    var lastZone by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(zone, r.paused) {
        if (WorkoutCopy.zoneTick(lastZone, zone)) haptics.perform(HapticEvent.ZoneChange)
        lastZone = if (r.paused) null else zone ?: lastZone
    }
}

/** km/h from seconds per kilometre, one decimal; null with no pace. */
internal fun speedText(secondsPerKm: Double?, locale: java.util.Locale = java.util.Locale.getDefault()): String? =
    secondsPerKm?.takeIf { it > 0 && it < Pace.SLOWEST_S }?.let { String.format(locale, "%.1f", 3600 / it) }

/**
 * The pace as the hero (workout-rules.md, Run; board HaloLiveRun): "Pace now" with the average beside it, the pace in
 * the one dotted number, and the latest kilometre's line under it. Lit in the sport's lime from its edge.
 */
@Suppress("UnstableCollections", "ComposeUnstableCollections")
@Composable
private fun PaceHero(
    r: WorkoutState.Recording, pace: Double?, averagePace: () -> Double?, speed: Boolean, route: List<Route.Point>,
    readings: List<Pair<Long, Int>>, modifier: Modifier = Modifier,
) {
    val color = liveColor(r)
    val tint = heroTint(color)
    LiveHero(color, liveAngle(r), modifier) {
        HeroTitle(
            stringResource(if (speed) R.string.workout_live_speed_title else R.string.workout_live_pace_title), null, tint,
            trailing = { AveragePace(averagePace, speed, tint) },
        )
        val now = if (speed) speedText(pace, Units.DOT_LOCALE) else Pace.clock(pace, Units.DOT_LOCALE)
        if (now != null) HaloBigNumber(
            now, BigNumber.Live, unitColor = tint,
            unit = stringResource(if (speed) R.string.workout_live_unit_kmh else R.string.workout_live_unit_per_km),
        ) else WaitingDash(Modifier.padding(vertical = Space.S16))
        KmSplitLine(route, readings, r.workout.pauseRanges, keepRoom = true, color = tint, center = false)
    }
}

/** "Avg 5:42" on the hero's title row: the average moves with the clock, so it's read in its own scope. */
@Composable
private fun AveragePace(averagePace: () -> Double?, speed: Boolean, tint: Color) {
    val average = averagePace()
    val avg = (if (speed) speedText(average) else Pace.clock(average))?.let { stringResource(R.string.workout_live_avg, it) }
    if (avg != null) Text(avg, color = tint, style = GlowType.secondary)
}

/** The splits card: the kilometres as bars, and under a rule the heart rate with its zone (board HaloLiveRun). */
@Composable
private fun SplitsCard(splits: List<Route.Split>, bpm: Int?, r: WorkoutState.Recording, showHeart: Boolean) {
    LiveCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.workout_live_splits_title), color = Color.White, style = GlowType.cardTitle, maxLines = 1,
                modifier = Modifier.weight(1f))
            Text(stringResource(R.string.workout_live_splits_note), color = GlowType.noteColor, style = GlowType.noteSmall)
        }
        KmSplitBars(splits)
        if (showHeart) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Greys.Control))
            HeartLine(bpm, r.maxHeartRate, r.paused)
        }
    }
}

/**
 * The last five kilometres as bars, taller for faster, each with its time under it; the one under way is white and
 * filling from the bottom, named "Now" (board HaloLiveRun). Never dotted.
 */
@Composable
private fun KmSplitBars(splits: List<Route.Split>) {
    val shown = splits.takeLast(SPLIT_SLOTS)
    val done = splits.filter { it.meters >= 1000 }
    val slowest = done.maxOfOrNull { it.pace } ?: 0.0
    val quickest = done.minByOrNull { it.pace }?.pace ?: 0.0
    val described = stringResource(
        R.string.workout_live_splits_description, done.takeLast(4).joinToString(", ") { workoutClock(it.seconds) },
    )
    Row(
        Modifier.fillMaxWidth().semantics { contentDescription = described },
        horizontalArrangement = Arrangement.spacedBy(Space.S8),
    ) {
        shown.forEach { s ->
            val underWay = s.meters < 1000
            // Faster is taller: from 40 percent for the slowest to full for the fastest.
            val height = when {
                underWay -> (s.meters / 1000).toFloat().coerceIn(0.1f, 1f)
                slowest <= quickest -> 1f
                else -> (0.4f + 0.6f * ((slowest - s.pace) / (slowest - quickest)).toFloat()).coerceIn(0.4f, 1f)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Space.S8)) {
                Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier.fillMaxWidth().fillMaxHeight(height).clip(Corner.Pip)
                            .background(if (underWay) Color.White else SplitBar),
                    )
                }
                Text(
                    if (underWay) stringResource(R.string.workout_live_split_now) else
                        stringResource(R.string.workout_live_split_km, s.index + 1),
                    color = if (underWay) Color.White else GlowType.noteColor, style = GlowType.caption, maxLines = 1,
                )
            }
        }
        // Empty slots keep each bar a fifth of the card, so the first kilometre isn't a line across it.
        repeat(SPLIT_SLOTS - shown.size) { Spacer(Modifier.weight(1f)) }
    }
}

private val SplitBar = Greys.SplitBar

private const val SPLIT_SLOTS = 5
