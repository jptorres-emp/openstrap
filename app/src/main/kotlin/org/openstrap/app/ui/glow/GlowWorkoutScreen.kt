package org.openstrap.app.ui.glow

import org.openstrap.app.ui.theme.IconSize
import org.openstrap.app.ui.theme.Space
import org.openstrap.app.ui.theme.Greys
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import org.openstrap.app.ui.theme.Ink
import org.openstrap.app.ui.workout.FamilySummary
import org.openstrap.app.presentation.Units
import org.openstrap.app.presentation.Pace
import androidx.compose.ui.Alignment
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import org.openstrap.app.R
import org.openstrap.app.presentation.TimeOfDay
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import org.openstrap.app.presentation.Ranges
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.clipRect
import org.openstrap.app.presentation.TabCopy
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import org.openstrap.app.data.Route
import org.openstrap.app.data.WorkoutAnalysis
import org.openstrap.app.data.Workouts
import org.openstrap.app.ui.components.rememberScrubHaptics
import org.openstrap.app.ui.components.scrub
import org.openstrap.app.ui.dashboard.CaloriesCopy
import org.openstrap.app.ui.theme.AppIcons
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.app.ui.workout.EffectFigures
import org.openstrap.app.ui.workout.PhoneSummary
import org.openstrap.app.ui.workout.ZoneSeconds
import org.openstrap.app.ui.workout.sportFamilyColor
import org.openstrap.app.ui.workout.WorkoutDetailCopy
import org.openstrap.app.ui.workout.WorkoutFact
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt
import org.openstrap.app.ui.dashboard.LocalHeartZones

/*
 * The page of a past workout (spec 6.3, "Workout detail"), one for the strap's and the phone's: the sentence first on
 * a glow as big as the effort, the heart rate in its zones' colours with the drop after the end, the small facts and
 * the map, Load with time to recover and the heart rate drop, time in zones and the splits. Training effect and EPOC
 * sit behind the (i) on the Load card. A workout row grows into this page (spec 3.5).
 */

/** The heart rate line is drawn in ten-second steps: readable on a phone, and the card still says the time. */
private const val HeartStep = 10L

/**
 * The workout [a]. A phone workout shows from its [summary] (route, laps, sets and the whole recording, for trimming),
 * a strap workout from [a] alone. [workouts] are all of them, for your usual Load, as the rows that open this page
 * use; [usualDrop] your usual heart rate drop. Trim and undo are for phone workouts.
 */
@Composable
fun GlowWorkoutDetailScreen(
    a: WorkoutAnalysis?,
    summary: PhoneSummary?,
    workouts: List<WorkoutAnalysis>,
    usualDrop: Int?,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onTrim: (from: Long, to: Long) -> Unit = { _, _ -> },
    onUndoTrim: () -> Unit = {},
    /** Your BMR a minute, for a strap workout's active kcal; null without a profile. */
    bmrPerMinute: Float? = null,
    /** The active kcal of the workout's day, for In your day, and the way to that day's Calories. */
    dayActiveKcal: Float? = null,
    onOpenDay: (() -> Unit)? = null,
) {
    BackHandler(onBack = onBack)
    // A workout recorded on the phone opens as its summary did, per kind (workouts spec 2.8), with a back arrow.
    if (a?.fromPhone == true && summary != null) {
        PastSummary(
            summary, workouts, contentPadding, onBack, onDelete, onTrim, onUndoTrim, dayActiveKcal, onOpenDay,
            foundTag = !a.startedByYou,
        )
        return
    }
    // A phone workout whose summary hasn't loaded yet shows only its header.
    val shown = a?.takeIf { !it.fromPhone }
    val list = rememberLazyListState()
    var info by rememberSaveable { mutableStateOf(false) }
    val usual = remember(workouts) { Workouts.usualLoad(workouts.map { it.workout.trainingLoad }) }
    val zones = shown?.zoneSeconds.orEmpty()
    CompositionLocalProvider(LocalGlowPaused provides { list.isScrollInProgress }) {
        HaloPageList(contentPadding, variant = PageVariant.Tab, state = list) {
            item {
                // The day and times under the name, as a phone workout's page has them.
                val day = shown?.let {
                    stringResource(
                        R.string.glow_workout_day_times, WorkoutDetailCopy.day(it.shownStart, LocalDate.now()),
                        Ranges.times(it.shownStart, it.end),
                    )
                }
                HaloPageHeader(
                    (shown ?: a)?.sportName ?: stringResource(R.string.glow_workout_untitled), onBack, subtitle = day,
                    titleSuffix = (shown ?: a)?.let { workoutTags(it.workout.sportType) },
                    trailing = if ((shown ?: a)?.startedByYou == false) ({ HaloTag(TabCopy.AUTO_DETECTED) }) else null,
                )
            }
            if (shown == null) return@HaloPageList
            val w = shown.workout
            val family = FamilySummary.familyOf(w.sportType, null)
            item {
                // The summary's hero with the length in dots: a strap workout has no kind of its own to show.
                val sentence = WorkoutDetailCopy.sentence(w.sportType, shown.activeSeconds, w.trainingLoad, usual, zones)
                val title = stringResource(R.string.glow_summary_figure_time)
                val hero = SummaryHero(Units.length(shown.activeSeconds, Units.DOT_LOCALE), "", sentence = sentence, title = title)
                SummaryHeroCard(
                    hero, sportFamilyColor(w.sportType, family), family = family, note = Ranges.times(shown.shownStart, shown.end),
                )
            }
            if (shown.heartRate.isNotEmpty() || w.averageHeartRate != null) item { HeartRateCard(shown) }
            val activeKcal = Workouts.activeKcal(shown, bmrPerMinute)
            val facts = WorkoutDetailCopy.facts(
                shown.distanceMeters, shown.distanceFromSteps,
                0L, w.steps, w.calories,
                activeKcal = activeKcal.takeIf { bmrPerMinute != null },
            ) + listOfNotNull(WorkoutDetailCopy.targetFact(shown.target, shown.targetReachedSeconds, shown.activeKcal?.roundToInt()))
            if (facts.isNotEmpty()) item { WorkoutFacts(facts, null, shown) }
            if (zones.sum() > 0) item { ZonesList(remember(zones) { ZoneSeconds(zones) }, shown.maxHeartRate) }
            // The workout's share of its day, right under its kcal (calories spec 4.2).
            val today = LocalDate.now()
            val inDay = activeKcal?.let { k ->
                val date = Instant.ofEpochSecond(shown.shownStart).atZone(ZoneId.systemDefault()).toLocalDate()
                CaloriesCopy.inYourDay(WorkoutDetailCopy.sport(w.sportType), k, dayActiveKcal?.roundToInt(), date, today, false)
            }
            if (inDay != null) item { InYourDayCard(inDay, onOpenDay) }
            val drop = WorkoutDetailCopy.dropLine(shown.recovery60, shown.recoveryIsMeaningful, usualDrop)
            val loadShown = w.trainingLoad != null || (shown.recoveryMinutes ?: 0) > 0 || drop != null
            if (loadShown) item { WorkoutLoadCard(shown, usual, drop = drop, onInfo = { info = true }) }
            item { DeleteWorkout(stringResource(R.string.glow_workout_delete_strap), onDelete) }
        }
    }
    if (info && shown != null) EffectInfo(shown, usual, emptyList(), false, onDismiss = { info = false })
}

/**
 * A past phone workout (workouts spec 2.8, past): the summary's hero and the kind's own section, then everything this
 * page always had: the heart rate line with the drop after the end and Trim on it, the facts and the map, In your day,
 * Load with its (i), the zones and splits where the kind's section doesn't show them, and Delete. No Done and nothing
 * earned: that was said when it ended.
 */
@Composable
private fun PastSummary(
    summary: PhoneSummary,
    workouts: List<WorkoutAnalysis>,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onTrim: (from: Long, to: Long) -> Unit,
    onUndoTrim: () -> Unit,
    dayActiveKcal: Float?,
    onOpenDay: (() -> Unit)?,
    foundTag: Boolean,
) {
    val a = summary.analysis
    val w = a.workout
    val list = rememberLazyListState()
    var info by rememberSaveable { mutableStateOf(false) }
    val others = remember(workouts, w.start) { workouts.filter { it.workout.start != w.start }.sortedByDescending { it.workout.start } }
    val usual = remember(others) { Workouts.usualLoad(others.map { it.workout.trainingLoad }) }
    val family = org.openstrap.app.ui.workout.FamilySummary.familyOf(w.sportType, summary.structure)
    val weekStarts = remember(workouts) { workoutDayStarts(workouts) }
    val trim = rememberWorkoutTrim(summary, onTrim, onUndoTrim)
    // Share opens the share screen in the page's place, as after the workout (board HaloPastWorkout); close comes back.
    var sharing by rememberSaveable { mutableStateOf(false) }
    val shareHero = remember(family, summary, usual) { summaryHero(family, summary, usual) }
    if (sharing) {
        // Full screen over the tabs: just the navigation bar, not the tab page's room for the header and the hidden bar.
        WorkoutShareScreen(
            summary, shareHero, emptyList(), emptyList(),
            androidx.compose.foundation.layout.WindowInsets.navigationBars.asPaddingValues(), onClose = { sharing = false },
        )
        return
    }
    val routeMap = routeMapModel()
    if (routeMap?.isOpen(w.start) == true) {
        RouteMapPage(summary, routeMap, androidx.compose.foundation.layout.WindowInsets.navigationBars.asPaddingValues())
        return
    }
    CompositionLocalProvider(LocalGlowPaused provides { list.isScrollInProgress }) {
        HaloPageList(contentPadding, variant = PageVariant.Tab, state = list) {
            item {
                HaloPageHeader(a.sportName, onBack, titleSuffix = workoutTags(a.workout.sportType), subtitle = stringResource(
                        R.string.glow_workout_day_times, WorkoutDetailCopy.day(a.shownStart, LocalDate.now()),
                        Ranges.times(a.shownStart, a.end),
                    ), // Share in the header's corner, matching the back button, so it is there without scrolling.
                    trailing = {
                        HaloIconButton(
                            AppIcons.UploadSimple, stringResource(R.string.glow_share), { sharing = true },
                        )
                    })
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Space.S12)) {
                    // The header's corner holds Share, so a workout the strap found says so here.
                    if (foundTag) HaloTag(TabCopy.AUTO_DETECTED)
                    // The stored time in zones, as the summary, the share card and the family section read it, so
                    // the page shows one total.
                    val zones = remember(a) { ZoneSeconds(a.zoneSeconds) }
                    // The page's sentence ("An easy 40 min walk") where the kind has none of its own.
                    val hero = pastHero(summaryHero(family, summary, usual), a, usual, zones)
                    SummaryHeroCard(hero, sportFamilyColor(w.sportType, family), family = family, note = Ranges.times(a.shownStart, a.end))
                    FamilySection(family, summary, emptyList(), others, weekStarts, heartLine = false, figures = hero.figures)
                    // Always there, even before any heart rate, since it holds Trim.
                    HeartRateCard(a, trim)
                    val facts = WorkoutDetailCopy.facts(
                        a.distanceMeters, a.distanceFromSteps, (a.durationSeconds - a.activeSeconds).coerceAtLeast(0), w.steps, w.calories,
                        activeKcal = Workouts.activeKcal(a, null),
                    ) + listOfNotNull(WorkoutDetailCopy.targetFact(a.target, a.targetReachedSeconds, a.activeKcal?.roundToInt()))
                    // The map is in the route card for a run; any other kind with a kept route has it here. The figures
                    // the hero's row shows are not said twice: only what it doesn't have.
                    val map = family != Workouts.Family.Distance && Route.mappable(summary.route)
                    val rest = facts.filter { it.kind != WorkoutFact.Kind.Distance && it.kind != WorkoutFact.Kind.Calories }
                    if (rest.isNotEmpty() || map) WorkoutFacts(rest, summary.takeIf { map }, null)
                    val inDay = Workouts.activeKcal(a, null)?.let { k ->
                        val date = Instant.ofEpochSecond(a.shownStart).atZone(ZoneId.systemDefault()).toLocalDate()
                        val sport = WorkoutDetailCopy.sport(w.sportType)
                        CaloriesCopy.inYourDay(sport, k, dayActiveKcal?.roundToInt(), date, LocalDate.now(), true)
                    }
                    // Zones and splits for the kinds whose section doesn't already show them.
                    if (zonesPanelShown(family, summary, zones)) ZoneStripPanel(zones)
                    if (inDay != null) InYourDayCard(inDay, onOpenDay)
                    val drop = WorkoutDetailCopy.dropLine(a.recovery60, a.recoveryIsMeaningful, null)
                    if (w.trainingLoad != null || (a.recoveryMinutes ?: 0) > 0 || drop != null) {
                        WorkoutLoadCard(a, usual, drop = drop, onInfo = { info = true })
                    }
                    if (family != Workouts.Family.Distance && family != Workouts.Family.Strength) SplitsList(summary, liveRoute = emptyList())
                    DeleteWorkout(stringResource(R.string.glow_workout_delete_phone), onDelete)
                }
            }
        }
    }
    if (info) EffectInfo(a, usual, summary.strapWorkouts, summary.effectFromStrap, onDismiss = { info = false })
    HaloTrimSheet(trim)
}

/**
 * Whether a past phone workout's page has its own time in zones panel: not when the family section already shows them,
 * as an indoor card does, and a run with no route, splits or laps has one ([hasRunCard]).
 */
private fun zonesPanelShown(family: Workouts.Family, summary: PhoneSummary, zones: ZoneSeconds): Boolean = when {
    zones.sum() <= 0 -> false
    family == Workouts.Family.IndoorCardio || family == Workouts.Family.Games -> false
    family == Workouts.Family.Distance -> hasRunCard(summary)
    else -> true
}

/**
 * Heart rate over the workout in its zones' colours (board HaloPastWorkout), a solid card: "Heart rate" with how it is
 * coloured, the average as a figure with the highest and lowest under it, then the line with the strap's minutes after
 * the end dimmer, as the drop. Hold the line to scrub and the figure follows the finger (spec 2.7), with the scrub
 * ticks. A phone workout's [trim] puts Trim in the title row and what was trimmed, with Undo, under the chart, there
 * even before any heart rate has arrived.
 */
@Composable
private fun HeartRateCard(a: WorkoutAnalysis, trim: WorkoutTrim? = null) {
    val w = a.workout
    val first = a.shownStart
    val last = (a.heartRate.maxOfOrNull { it.timestamp } ?: a.end).coerceIn(a.end, a.end + Workouts.RECOVERY_TAIL_S)
    val values = remember(a) {
        val n = ((last - first) / HeartStep + 1).toInt().coerceAtLeast(2)
        org.openstrap.app.data.Summaries.bucketMeans(a.heartRate, n, { ((it.timestamp - first) / HeartStep).toInt() }, { it.bpm.toFloat() })
    }
    var scrubbed by remember { mutableStateOf<Int?>(null) }
    val at = scrubbed?.let { i -> values.getOrNull(i)?.let { v -> first + i * HeartStep to v.roundToInt() } }
    HaloCard {
        val byZone = if (a.heartRate.isNotEmpty()) stringResource(R.string.glow_workout_heart_by_zone) else null
        HaloTitle(stringResource(R.string.glow_summary_heart_rate), note = byZone, trailing = trim?.let { {
            HaloButton(
                stringResource(R.string.glow_trim), it.open, variant = HaloButtonVariant.Glass, size = HaloButtonSize.Small,
                icon = AppIcons.Scissors,
            )
        } })
        val figure: String?
        val figureLabel: String
        val under: String?
        if (at != null) {
            figure = at.second.toString()
            figureLabel = stringResource(R.string.glow_unit_bpm)
            val atId = if (at.first > a.end) R.string.glow_workout_heart_at_after else R.string.glow_workout_heart_at
            under = stringResource(atId, clock(at.first))
        } else {
            val max = w.maxHeartRate
            val min = w.minHeartRate
            figure = w.averageHeartRate?.toString()
            figureLabel = stringResource(R.string.glow_workout_unit_bpm_average)
            under = when {
                max != null && min != null -> stringResource(R.string.glow_workout_heart_max_lowest, max, min)
                max != null -> stringResource(R.string.glow_workout_heart_max, max)
                min != null -> stringResource(R.string.glow_workout_heart_lowest, min)
                else -> null
            }
        }
        if (figure != null) Column(verticalArrangement = Arrangement.spacedBy(Space.S4)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Space.S8)) {
                Text(figure, color = Color.White, style = GlowType.figureLarge)
                Text(figureLabel, Modifier.padding(bottom = Space.S4), color = SoftInk, style = GlowType.secondary)
            }
            if (under != null) Text(under, color = GlowType.noteColor, style = GlowType.noteSmall)
        }
        if (a.heartRate.isEmpty()) Text(
            stringResource(R.string.glow_workout_heart_none), color = GlowType.noteColor, style = GlowType.secondary,
        ) else {
            ZoneHeartLine(values, first, a.end, a.maxHeartRate, a.historyRanges, scrubbed, { scrubbed = it })
            Row(Modifier.fillMaxWidth()) {
                HaloNote(clock(first))
                Spacer(Modifier.weight(1f))
                val after = stringResource(R.string.glow_workout_heart_drop_after, clock(a.end))
                HaloNote(if (last > a.end + HeartStep) after else clock(a.end))
            }
            if (a.historyRanges.isNotEmpty()) Text(
                stringResource(
                    R.string.glow_workout_heart_dimmed, a.historyRanges.joinToString(", ") { Ranges.times(it.first, it.last + 1) },
                ),
                color = GlowType.noteColor, style = GlowType.secondary,
            )
        }
        trim?.let { TrimNote(it.summary, it.undo) }
    }
}

/**
 * The line itself: each ten-second step in its zone's colour up to the [end], dim white after it with a solid hairline
 * mark at the end; minutes from the strap's [history] dimmed. [scrubbed] is the step under the finger, [onScrub] follows it.
 */
@Composable
private fun ZoneHeartLine(
    values: List<Float?>, first: Long, end: Long, maxHr: Int, history: List<LongRange>,
    scrubbed: Int?, onScrub: (Int?) -> Unit, modifier: Modifier = Modifier,
) {
    val heartZones = LocalHeartZones.current
    var width by remember { mutableStateOf(0f) }
    val tick = rememberScrubHaptics()
    val known = values.filterNotNull()
    val lo = (known.minOrNull() ?: 60f) - 5f
    val hi = (known.maxOrNull() ?: 180f) + 5f
    val endIndex = ((end - first) / HeartStep).toInt()
    Canvas(
        modifier.fillMaxWidth().height(96.dp).onSizeChanged { width = it.width.toFloat() }.scrub(values) { x ->
            val i = x?.let { nearestReading(values, it, width, 0f) }
            tick(i, values, null, true)
            onScrub(i)
        },
    ) {
        val dx = size.width / (values.size - 1).coerceAtLeast(1)
        fun y(v: Float) = size.height - (v - lo) / (hi - lo) * size.height
        if (endIndex in 1 until values.lastIndex) drawLine(
            Ink.Disabled, Offset(endIndex * dx, 0f), Offset(endIndex * dx, size.height), strokeWidth = 1.dp.toPx(),
        )
        drawZoneLine(values.size, 2.5.dp.toPx(), at = { i -> values[i]?.let { Offset(i * dx, y(it)) } }) { i ->
            val v = values[i] ?: return@drawZoneLine null
            val t = first + i * HeartStep
            val color = if (i >= endIndex) Ink.Tertiary else zoneColor(v.roundToInt(), maxHr, heartZones)
            // The strap's history, filled in after: the same line, dimmed (spec 2.7, no dashes).
            if (history.any { t in it }) color.copy(alpha = color.alpha * 0.45f) else color
        }
        scrubbed?.let { i ->
            val v = values.getOrNull(i) ?: return@let
            val p = Offset(i * dx, y(v))
            drawLine(Ink.Tertiary, Offset(p.x, 0f), Offset(p.x, size.height), strokeWidth = 1.dp.toPx())
            drawCircle(Ink.ScrimDeep, 6.dp.toPx(), p)
            drawCircle(Color.White, 4.dp.toPx(), p)
        }
    }
}

/**
 * In your day (board HaloPastWorkout): what share of its day's active kcal the workout is ([CaloriesCopy.inYourDay]), on a
 * plain panel with a caret when it opens that day's Calories.
 */
@Composable
private fun InYourDayCard(text: String, onOpen: (() -> Unit)?) {
    HaloCard(onClick = onOpen) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.glow_workout_in_your_day), color = Color.White, style = GlowType.cardTitle, maxLines = 1)
            if (onOpen != null) Icon(AppIcons.TapCaret, null, Modifier.size(IconSize.Caret), tint = Ink.Tertiary)
        }
        Text(text, color = SoftInk, style = GlowType.buttonSmall.copy(fontWeight = FontWeight.Normal))
    }
}

/**
 * The small facts ([WorkoutDetailCopy.facts]) as the board draws them (HaloPastWorkout): distance, average pace and
 * kcal as the figures row for a strap [workout] (a phone workout's are in its hero's), the rest (paused, steps, target) as
 * rows on a solid card, and the map under them when the route was kept.
 */
@Composable
private fun WorkoutFacts(facts: List<WorkoutFact>, summary: PhoneSummary?, workout: WorkoutAnalysis?) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.S12)) {
        if (workout != null) {
            val distance = facts.firstOrNull { it.kind == WorkoutFact.Kind.Distance }
            val kcal = facts.firstOrNull { it.kind == WorkoutFact.Kind.Calories }
            val pace = distance?.let {
                Pace.clock(Pace.of(FamilySummary.gpsDistance(workout.distanceMeters, workout.distanceFromSteps), workout.activeSeconds))
            }
            // Distance carries its unit ("km"); pace and kcal have none.
            val shown = listOfNotNull(
                distance?.let { d -> Triple(d.value, d.name, d.status?.takeIf { ' ' !in it && ',' !in it }) },
                pace?.let { Triple(it, stringResource(R.string.glow_summary_figure_avg_pace), null) },
                kcal?.let { Triple(it.value, it.name, null) },
            )
            val figures = shown.map { it.first to it.second }
            val padded = shown.map { it.third }
            HaloFigures(figures, units = padded)
        }
        val rest = facts.filter { workout == null || (it.kind != WorkoutFact.Kind.Distance && it.kind != WorkoutFact.Kind.Calories) }
        if (rest.isNotEmpty() || summary != null) HaloCard {
            if (rest.isNotEmpty()) Column {
                rest.forEach { f -> FactRow(f.name, listOfNotNull(f.value, f.status).joinToString(" ")) }
            }
            summary?.let { RouteMap(it) }
        }
    }
}

/** The (i) on the Load card: Load, training effect, time to recover, the heart rate drop and VO2 max explained. */
@Composable
private fun EffectInfo(a: WorkoutAnalysis, usual: Float?, strap: List<WorkoutAnalysis>, fromStrap: Boolean, onDismiss: () -> Unit) {
    val w = a.workout
    val sheet = WorkoutDetailCopy.effectSheet(
        aerobic = w.aerobicTrainingEffect, anaerobic = w.anaerobicTrainingEffect, load = w.trainingLoad, usualLoad = usual,
        recoveryMinutes = a.recoveryMinutes, drop60 = a.recovery60, drop120 = a.recovery120, endBpm = a.endHeartRate,
        meaningfulDrop = a.recoveryIsMeaningful, vo2max = a.effect?.vo2max ?: w.vo2max?.toFloat(), fromPhone = a.fromPhone,
        strap = strap.map { s ->
            EffectFigures(
                s.workout.aerobicTrainingEffect, s.workout.anaerobicTrainingEffect, s.workout.trainingLoad, s.recoveryMinutes,
                from = TimeOfDay.format(s.workout.start, ZoneId.systemDefault()),
            )
        },
        effectFromStrap = fromStrap, ownRecoveryMinutes = a.ownRecoveryMinutes, sportType = w.sportType,
    )
    InfoSheet(sheet.info, MetricColors.load, sheet.here, sheet.hereLine, onDismiss, rows = sheet.rows)
}

/**
 * Every workout, newest first, as one list under small day headers ("Today", "Yesterday", "Wed 21 Oct"), each a row
 * as on the Activity tab's Workouts card ([WorkoutRow]). One list rather than a panel a day, so no two cards in a row
 * look the same (spec 2.5); each day is still its own item, so a long history scrolls lazily.
 */
// A whole screen, placed by the host, which sizes it.
@Suppress("ModifierMissing", "ComposeModifierMissing")
@Composable
fun GlowWorkoutsScreen(workouts: List<WorkoutAnalysis>, contentPadding: PaddingValues, onOpen: (Long) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val zone = ZoneId.systemDefault()
    val usual = remember(workouts) { Workouts.usualLoad(workouts.map { it.workout.trainingLoad }) }
    val days = remember(workouts) { workouts.groupBy { Instant.ofEpochSecond(it.workout.start).atZone(zone).toLocalDate() }.toList() }
    val today = LocalDate.now()
    HaloPageList(contentPadding, variant = PageVariant.Tab) {
        item {
            HaloPageHeader(
                stringResource(R.string.glow_workout_list_title), onBack,
                subtitle = pluralStringResource(R.plurals.glow_workout_list_recorded, workouts.size, workouts.size),
            )
        }
        if (workouts.isEmpty()) {
            item {
                ListPanel {
                    MetricRow(
                        stringResource(R.string.glow_workout_list_empty), MetricColors.heart, null,
                        stringResource(R.string.glow_workout_list_empty_text),
                        dash = false, divider = false,
                    )
                }
            }
            return@HaloPageList
        }
        days.forEachIndexed { i, (date, day) ->
            item(key = date.toEpochDay()) {
                ListSegment(first = i == 0, last = i == days.lastIndex) {
                    HaloTitle(TabCopy.workoutDay(date, today), Modifier.rowSides().padding(top = Space.S12), variant = TitleVariant.Group)
                    day.forEachIndexed { j, a -> WorkoutRow(a, usual, divider = j > 0) { onOpen(a.workout.start) } }
                }
            }
        }
    }
}

/** The space [ScreenList] leaves between items, which a [ListSegment] fills to join the next. */
private val ListItemGap = 12.dp

/**
 * One stretch of a list split over several lazy items: the [ListPanel] surface and hairline, rounded at the top on
 * the [first] and at the bottom on the [last], and reaching down through the gap to the next stretch otherwise, so
 * the stretches read as one panel.
 */
@Composable
private fun ListSegment(first: Boolean, last: Boolean, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .drawBehind {
                val bottom = if (last) size.height else size.height + ListItemGap.toPx()
                // The squircle drawn past the ends that join another stretch, then cut back to this one's span. Its
                // size is that of a whole tall panel, so its corners are the card's own (the cap) (Halo System board, Solid card).
                val r = GlowLayout.cornerPx(androidx.compose.ui.geometry.Size(size.width, size.width), this)
                val top = if (first) 0f else -r
                val end = if (last) bottom else bottom + r
                val panel = androidx.compose.ui.geometry.Size(size.width, end - top)
                clipRect(top = 0f, bottom = bottom) {
                    translate(top = top) { drawOutline(CardTuning.shape.createOutline(panel, layoutDirection, this), Greys.Card) }
                }
            }
            .padding(horizontal = GlowLayout.CardInset)
            .padding(top = if (first) GlowLayout.CardInset - GlowLayout.RowPadding else 0.dp, bottom = if (last) GlowLayout.CardInset - GlowLayout.RowPadding else 0.dp),
        content = content,
    )
}

/** The past workout's hero, with the page's sentence ("An easy 40 min walk") where its kind has none of its own. */
private fun pastHero(h: SummaryHero, a: WorkoutAnalysis, usual: Float?, zones: ZoneSeconds): SummaryHero =
    if (h.sentence != null) h
    else SummaryHero(
        h.value, h.unit, h.figures,
        WorkoutDetailCopy.sentence(a.workout.sportType, a.activeSeconds, a.workout.trainingLoad, usual, zones.toList()),
        h.line, h.pace, h.title,
    )
