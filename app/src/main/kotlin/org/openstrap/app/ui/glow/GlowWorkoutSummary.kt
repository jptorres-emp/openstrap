package org.openstrap.app.ui.glow

import org.openstrap.app.ui.theme.Corner
import org.openstrap.app.ui.theme.Space
import org.openstrap.app.ui.theme.Greys
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.size
import org.openstrap.app.ui.theme.Ink
import org.openstrap.app.ui.workout.WorkoutDetailCopy
import org.openstrap.app.presentation.Ranges
import org.openstrap.app.ui.glow.draw.LightAngle
import org.openstrap.app.ui.glow.draw.heroStyle
import org.openstrap.app.R
import org.openstrap.app.presentation.Units
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import org.openstrap.app.ui.dashboard.Info
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import org.openstrap.app.ui.workout.SplitRow
import org.openstrap.app.ui.workout.ZoneSeconds
import org.openstrap.app.ui.workout.sportFamilyColor
import kotlinx.coroutines.delay
import org.openstrap.app.data.Route
import org.openstrap.app.data.WorkoutAnalysis
import org.openstrap.app.data.Workouts
import org.openstrap.app.device.CalmDown
import org.openstrap.app.device.WorkoutSession
import org.openstrap.app.ui.components.HapticEvent
import org.openstrap.app.ui.components.rememberHaptics
import org.openstrap.app.ui.dashboard.Score
import org.openstrap.app.ui.theme.AppIcons
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.app.ui.workout.CalmDownLine
import org.openstrap.app.presentation.EarnedLine
import org.openstrap.app.presentation.SummaryRewards
import org.openstrap.app.ui.workout.FamilySummary
import org.openstrap.app.ui.workout.PhoneSummary
import org.openstrap.app.ui.workout.SummaryCopy
import org.openstrap.app.ui.dashboard.LocalHeartZones

/*
 * The summary right after a workout (workouts spec 2.8, boards HaloSummaryRun to HaloSummaryGames): back, the sport and
 * when with Trim, a hero with the one number that matters for that kind of workout, then its own card ([FamilySection])
 * and the three figures, then what it earned. Under them the heart rate drop, drawing as it happens, and Load against your
 * usual. A personal best ticks once, no modal. Past workouts open their own page ([GlowWorkoutDetailScreen]), which
 * shares the Load panel and the cards.
 */

/** Where Done has got to: the summary, a personal best's card, the goal moment. */
private enum class SummaryStep { Summary, Best, Moment }

/** The drop line shows the last minute of the workout before the end. */
private const val BeforeS = 60L

/**
 * The summary right after [summary]'s workout ended. [calmDown] is the heart rate the strap sent since, for this
 * workout or another (then ignored), null when nothing is live (a debug preview). [liveRoute] is the route still in
 * memory, so a GPS workout whose route wasn't kept still gets its splits. [workouts] are all of them, for your usual
 * Load, the last time and this week; [usualDrop] your usual heart rate drop.
 */
@Composable
fun GlowWorkoutSummaryScreen(
    summary: PhoneSummary?,
    calmDown: CalmDown?,
    liveRoute: List<Route.Point>,
    workouts: List<WorkoutAnalysis>,
    usualDrop: Int?,
    contentPadding: PaddingValues,
    onDone: () -> Unit,
    onDelete: () -> Unit,
    onTrim: (from: Long, to: Long) -> Unit,
    onUndoTrim: () -> Unit,
    /** What it earned ([RewardsCopy.summary]), null while it's worked out. */
    rewards: SummaryRewards? = null,
    celebrations: Celebrations? = null,
) {
    val a = summary?.analysis
    // Done goes through the celebrations it earned (workouts spec 3.4): a personal best's card, then the goal moment
    // once a week, then away.
    var step by rememberSaveable { mutableStateOf(SummaryStep.Summary) }
    var settings by rememberSaveable { mutableStateOf(false) }
    val moment = rewards != null && celebrations != null && rewards.goalMet && celebrations.goalMoment && celebrations.momentWeek != rewards.weekStart
    fun next(from: SummaryStep) {
        step = when {
            from < SummaryStep.Best && rewards?.best != null -> SummaryStep.Best
            from < SummaryStep.Moment && moment -> SummaryStep.Moment.also { celebrations.markMoment(rewards.weekStart) }
            else -> { onDone(); return }
        }
    }
    val done = { next(step) }
    val hero = remember(summary, workouts) {
        if (summary == null || a == null) null
        else summaryHero(FamilySummary.familyOf(a.workout.sportType, summary.structure), summary, Workouts.usualLoad(workouts.filter { it.workout.start != a.workout.start }.map { it.workout.trainingLoad }))
    }
    // Share opens the share screen (workouts spec 2.9) in the summary's place; close comes back here.
    var sharing by rememberSaveable { mutableStateOf(false) }
    if (sharing && summary != null && hero != null) {
        WorkoutShareScreen(summary, hero, rewards?.earned.orEmpty(), liveRoute, contentPadding, onClose = { sharing = false })
        return
    }
    if (step == SummaryStep.Best && rewards?.best != null && a != null) {
        BestCardScreen(
            rewards.best, a.sportName, contentPadding, onContinue = done,
            onShare = if (hero != null) ({ sharing = true }) else null,
        )
        return
    }
    if (step == SummaryStep.Moment && rewards != null) {
        val starts = remember(workouts) { goalWeekStarts(workouts) }
        GoalMomentScreen(rewards.week.count, rewards.week.goal, rewards.inARow, starts, contentPadding, onSettings = { settings = true }, onContinue = done)
        if (settings && celebrations != null) CelebrationsSheet(
            celebrations.goalMoment, celebrations.weeklyRecap, celebrations.setGoalMoment, celebrations.setWeeklyRecap, onDismiss = { settings = false },
        )
        return
    }
    BackHandler(onBack = done)
    var info by rememberSaveable { mutableStateOf(false) }
    val trim = summary?.let { rememberWorkoutTrim(it, onTrim, onUndoTrim) }
    // Share and Done stay on the bottom edge; Delete waits at the end of the page. Nothing to share until the workout
    // has loaded.
    HaloPageColumn(contentPadding, pinned = {
            // Share is a round icon at Done's height and Done takes the rest of the row (board HaloSummaryRun).
            if (a != null && hero != null) Row(
                Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.S12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HaloIconButton(AppIcons.UploadSimple, stringResource(R.string.glow_share), { sharing = true }, size = HaloIconSize.Main)
                HaloButton(
                    stringResource(R.string.glow_done), done, Modifier.weight(1f), variant = HaloButtonVariant.Primary,
                    size = HaloButtonSize.Main,
                )
            }
            else HaloButton(stringResource(R.string.glow_done), done, Modifier.fillMaxWidth(), variant = HaloButtonVariant.Primary, size = HaloButtonSize.Main)
        }) {
        // Back, the sport and when, with Trim at the right (board HaloSummaryRun). Done is only on the bottom edge,
        // beside Share.
        HaloPageHeader(
            a?.sportName ?: stringResource(R.string.glow_workout_untitled), done,
            titleSuffix = a?.let { workoutTags(it.workout.sportType) },
            subtitle = a?.let { FamilySummary.whenLine(it.shownStart, System.currentTimeMillis() / 1000) },
            trailing = trim?.let { t ->
                { HaloChip(stringResource(R.string.glow_trim), t.open, icon = AppIcons.Scissors) }
            },
        )
        if (summary == null || a == null || trim == null) {
            HaloNote(stringResource(R.string.glow_workout_loading))
            return@HaloPageColumn
        }
        val w = a.workout
        val others = remember(workouts, w.start) { workouts.filter { it.workout.start != w.start }.sortedByDescending { it.workout.start } }
        val usualLoad = remember(others) { Workouts.usualLoad(others.map { it.workout.trainingLoad }) }
        val earned = rewards?.earned.orEmpty()
        // A personal best ticks once (the hero no longer swells: Halo's hero has no effort light).
        val haptics = rememberHaptics()
        var ticked by rememberSaveable(w.start) { mutableStateOf(false) }
        val anyBest = earned.any { it.kind == EarnedLine.Kind.Best }
        SideEffect {
            if (!anyBest || ticked) return@SideEffect
            ticked = true
            haptics.perform(HapticEvent.PersonalBest)
        }
        val family = FamilySummary.familyOf(w.sportType, summary.structure)
        val shownHero = (hero ?: summaryHero(family, summary, usualLoad)).let { h ->
            // The sentence ("An easy 40 min walk") where the kind has none of its own, as a past workout's page has it.
            if (h.sentence != null) h
            else {
                val said = WorkoutDetailCopy.sentence(w.sportType, a.activeSeconds, w.trainingLoad, usualLoad, a.zoneSeconds)
                SummaryHero(h.value, h.unit, h.figures, said, h.line, h.pace, h.title)
            }
        }
        SummaryHeroCard(shownHero, sportFamilyColor(w.sportType, family), family = family, note = Ranges.times(a.shownStart, a.end))
        // What was done to the times, under the hero that says them.
        TrimNote(summary, trim.undo, Modifier.padding(horizontal = Space.S4), onCard = false)
        val weekStarts = remember(workouts) { goalWeekStarts(workouts) }
        FamilySection(family, summary, liveRoute, others, weekStarts, week = rewards?.week, figures = shownHero.figures)
        EarnedChips(earned)
        // The drop only with heart rate to show: recorded in the workout, or arriving live after it.
        val live = calmDown?.takeIf { it.start == w.start }
        val dropShown = summary.recorded.isNotEmpty() || !live?.readings.isNullOrEmpty()
        if (dropShown) CalmDownHero(summary, live, usualDrop) { info = true }
        WorkoutLoadCard(a, usualLoad)
        DeleteWorkout(stringResource(R.string.glow_workout_delete_phone), onDelete)
    }
    if (info) InfoSheet(
        Info(stringResource(R.string.glow_summary_drop_title), SummaryCopy.DROP_MEANING, emptyList(), "", "", ""),
        MetricColors.heart, null, null,
        onDismiss = { info = false },
    )
    trim?.let { HaloTrimSheet(it) }
}

/**
 * The calm down, right after a workout (spec 6.3, board HaloSummaryRun): a solid card, lit teal from below when the drop
 * beats your usual. "Heart rate drop" with its (i) ([onInfo]), the drop in a minute as the figure with what it says
 * ([SummaryCopy.calmDown]) under it, and the line of the last minute and the two after the end at the right, growing as
 * readings arrive. The heart rate now shows under them while it's live.
 */
@Composable
private fun CalmDownHero(summary: PhoneSummary, live: CalmDown?, usualDrop: Int?, onInfo: () -> Unit) {
    val a = summary.analysis
    val now by produceState(System.currentTimeMillis() / 1000, live?.until) {
        // Ticks only while the calm down is still live, for the line and the minute.
        while (live != null && value <= live.until) {
            delay(1000 - System.currentTimeMillis() % 1000)
            value = System.currentTimeMillis() / 1000
        }
    }
    val readings = remember(summary, live?.readings) {
        (summary.recorded.map { it.timestamp to it.bpm } + live?.readings.orEmpty()).distinctBy { it.first }.sortedBy { it.first }
    }
    val line: CalmDownLine = SummaryCopy.calmDown(readings, a.end, now, live != null && now <= live.until, a.maxHeartRate, usualDrop)
    val bpmNow = live?.readings?.lastOrNull()?.takeIf { now <= live.until && now - it.first <= 5 }?.second
    // Lit teal only when the drop beats your usual (workout rules, Colour).
    val beats = usualDrop != null && line.drop != null && line.drop >= usualDrop + SummaryCopy.DROP_BAND
    val ink = if (beats) DropTealInk else GlowType.noteColor
    val body: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(Space.S12)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.glow_summary_drop_title), color = Color.White, style = GlowType.cardTitle, maxLines = 1)
                InfoButton(stringResource(R.string.glow_summary_drop_about), onInfo)
            }
            val shown = readings.filter { it.first in (a.end - BeforeS)..(a.end + WorkoutSession.CALM_S) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.S12), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.S8)) {
                    if (line.drop != null) Row(verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(Space.S8)) {
                        Text("${line.drop}", color = Color.White, style = GlowType.figureLarge)
                        Text(
                            stringResource(R.string.glow_summary_drop_unit), Modifier.padding(bottom = Space.S4), color = ink,
                            style = GlowType.secondary,
                        )
                    }
                    Text(line.text, color = ink, style = GlowType.noteSmall)
                    if (bpmNow != null) Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Space.S8)) {
                        Text("$bpmNow", color = Color.White, style = GlowType.figureInline)
                        Text(stringResource(R.string.glow_summary_unit_bpm_now), color = ink, style = GlowType.noteSmall)
                    }
                }
                if (shown.size >= 2) DropLine(shown, a.end)
            }
        }
    }
    HaloCard(tone = if (beats) CardTone.lit(heroStyle(DropTeal, LightAngle.Below)) else CardTone.Grey) { body() }
}

/** Rest and recovery teal (workout rules, Tokens), and the tint its text takes on the lit card. */
private val DropTeal = MetricColors.readiness
private val DropTealInk = MetricColors.readinessPale

/**
 * The heart rate from a minute before the [end] to two after, small (board HaloSummaryRun: 120 by 52): the workout's
 * last minute dim, the drop bright, a hairline mark at the end. Gaps of more than five seconds break the line.
 */
@Composable
private fun DropLine(readings: List<Pair<Long, Int>>, end: Long, modifier: Modifier = Modifier) {
    val first = end - BeforeS
    val span = (BeforeS + WorkoutSession.CALM_S).toFloat()
    val lo = readings.minOf { it.second } - 5f
    val hi = readings.maxOf { it.second } + 5f
    Canvas(modifier.size(120.dp, 52.dp)) {
        fun x(t: Long) = (t - first) / span * size.width
        fun y(v: Int) = size.height - (v - lo) / (hi - lo) * size.height
        val endX = x(end)
        val inset = 2.dp.toPx()
        drawLine(Ink.Disabled, Offset(endX, inset), Offset(endX, size.height - inset), strokeWidth = 1.dp.toPx())
        fun stroke(part: List<Pair<Long, Int>>, color: Color, width: Float) {
            if (part.size < 2) return
            val path = Path()
            part.forEachIndexed { i, (t, v) ->
                if (i == 0 || t - part[i - 1].first > 5) path.moveTo(x(t), y(v)) else path.lineTo(x(t), y(v))
            }
            drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round))
        }
        stroke(readings.filter { it.first <= end }, Ink.Tertiary, 2.dp.toPx())
        stroke(readings.filter { it.first >= end }, Color.White, 2.4.dp.toPx())
    }
}

/** The corner of the map inside a card (board HaloSummaryRun: 18 across the card's 36, so half). */

/** The kept route in its zones' colours on the map's own near black; nothing without a route that goes somewhere. */
@Composable
internal fun RouteMap(summary: PhoneSummary, modifier: Modifier = Modifier) {
    val heartZones = LocalHeartZones.current
    if (!Route.mappable(summary.route)) return
    val stretches = remember(summary, heartZones) {
        Route.byZone(summary.route, summary.history, summary.analysis.maxHeartRate, heartZones)
    }
    Box(modifier.fillMaxWidth().height(170.dp).clip(Corner.Inner).background(Greys.Card)) {
        WorkoutMap(stretches, routeOnly = false, Modifier.fillMaxSize())
    }
}


/** Time in zones on a solid card: the strip, then each zone with time in it, hardest first. */
@Composable
internal fun ZonesList(zones: ZoneSeconds, maxHr: Int) = HaloCard { ZonesBlock(zones, maxHr) }

/** The kilometres of [summary]'s route with their times, for a run's splits ([SplitsList], the run card). */
internal fun splitRowsOf(summary: PhoneSummary, liveRoute: List<Route.Point>): List<SplitRow> {
    val a = summary.analysis
    val route = summary.route.ifEmpty { liveRoute.filter { it.t in a.shownStart..a.end } }
    return SummaryCopy.splitRows(
        Route.splits(route, summary.pauses), summary.history, route.firstOrNull()?.t, summary.distanceMeters?.takeIf { !summary.distanceFromSteps },
    )
}

/**
 * Laps without GPS, and sets for strength, as a list (a past workout whose kind's own section doesn't show them).
 * Nothing without any of them. Returns whether it showed anything.
 */
@Composable
internal fun SplitsList(summary: PhoneSummary, liveRoute: List<Route.Point>): Boolean {
    val heartZones = LocalHeartZones.current
    val a = summary.analysis
    val maxHr = a.maxHeartRate
    val rows = remember(summary, liveRoute) { splitRowsOf(summary, liveRoute) }
    val laps = summary.laps.takeIf { it.size > 1 }.orEmpty()
    val sets = summary.sets.filter { it.end != null }
    // Each row's heart takes the zone its average heart rate sat in.
    fun dot(bpm: Int?) = bpm?.let { zoneColor(it, maxHr, heartZones) } ?: EdgeNeutral
    when {
        rows.isNotEmpty() || laps.isNotEmpty() -> ListPanel(
            title = stringResource(if (rows.isNotEmpty()) R.string.glow_summary_splits else R.string.glow_summary_laps),
            note = if (rows.isNotEmpty()) summary.distanceMeters?.let { Units.distance(it, Units.Precision.Workout) } else "${laps.size}",
        ) {
            rows.forEachIndexed { i, r ->
                MetricRow(
                    r.name, dot(r.averageBpm), r.time, r.averageBpm?.let { stringResource(R.string.glow_summary_split_average, it) },
                    divider = i > 0, icon = AppIcons.Heart,
                )
            }
            if (rows.isNotEmpty() && laps.isNotEmpty()) HaloTitle(
                stringResource(R.string.glow_summary_laps), Modifier.rowSides().padding(top = Space.S12),
                variant = TitleVariant.Group,
            )
            laps.forEachIndexed { i, lap ->
                MetricRow(
                    stringResource(R.string.glow_summary_lap, lap.index + 1), dot(lap.averageHeartRate), workoutClock(lap.end - lap.start),
                    SummaryCopy.lapLine(lap.meters, lap.averageHeartRate),
                    divider = i > 0, icon = AppIcons.Heart,
                )
            }
        }
        sets.isNotEmpty() -> ListPanel(
            title = stringResource(R.string.glow_summary_sets_title),
            note = pluralStringResource(R.plurals.glow_summary_sets_done, sets.size, sets.size),
        ) {
            sets.forEachIndexed { i, set ->
                MetricRow(
                    stringResource(R.string.glow_summary_set, set.index + 1), MetricColors.heart, workoutClock(set.end!! - set.start),
                    set.restEnd?.let { stringResource(R.string.glow_summary_set_rest, workoutClock(it - set.end)) },
                    divider = i > 0, icon = Score.Load.rowIcon(),
                )
            }
        }
        else -> return false
    }
    return true
}

