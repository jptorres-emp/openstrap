package org.openstrap.app.ui.glow

import org.openstrap.app.ui.theme.IconSize
import org.openstrap.app.ui.theme.Corner
import org.openstrap.app.ui.theme.Space
import org.openstrap.app.ui.theme.Greys
import org.openstrap.app.ui.theme.OnSurface
import androidx.compose.runtime.getValue
import org.openstrap.app.R
import org.openstrap.app.data.Weeks
import org.openstrap.app.i18n.Strings
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import org.openstrap.app.presentation.Ranges
import org.openstrap.app.presentation.Units
import org.openstrap.app.presentation.Pace
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextOverflow
import org.openstrap.app.ui.glow.draw.LightAngle
import org.openstrap.app.ui.glow.draw.heroStyle
import org.openstrap.app.ui.theme.Ink
import org.openstrap.app.ui.workout.SplitRow
import org.openstrap.app.ui.theme.FunnelSans
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import org.openstrap.app.data.Rewards
import org.openstrap.app.data.Route
import org.openstrap.app.data.Structure
import org.openstrap.app.data.WorkoutAnalysis
import org.openstrap.app.data.Workouts
import org.openstrap.app.ui.theme.AppIcons
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.app.presentation.EarnedLine
import org.openstrap.app.ui.workout.ExerciseSets
import org.openstrap.app.ui.workout.FamilySummary
import org.openstrap.app.ui.workout.ZoneSeconds
import org.openstrap.app.ui.workout.PeriodLine
import org.openstrap.app.ui.workout.PhoneSummary
import org.openstrap.app.ui.workout.RoundLine
import org.openstrap.app.ui.workout.ZONE_COLORS
import org.openstrap.app.ui.workout.familyColor
import org.openstrap.app.ui.dashboard.LocalHeartZones

/*
 * The summary's parts that differ by kind of workout (workouts spec 2.8, boards HaloSummaryRun to HaloSummaryGames): the
 * hero's one number, then the kind's own solid card, the figures row, what it earned. One order for every kind (workout
 * rules, Summary order).
 */

/**
 * The hero: its [title], its one number in dots and its word, then a sentence. The [figures] are the three under the
 * kind's card, not part of the hero. A figure's value is null when it's missing (no heart rate, no profile for kcal):
 * it shows the waiting dash.
 */
internal class SummaryHero(
    val value: String,
    val unit: String,
    val figures: List<Pair<String?, String>> = emptyList(),
    val sentence: String? = null,
    val line: String? = null,
    val pace: String? = null,
    val title: String? = null,
)

/** A workout's active kcal as its summary and share card write it: "1,312". */
internal fun kcalFigure(a: WorkoutAnalysis): String? = a.activeKcal?.let { Units.count(Math.round(it)) }

/** A workout's length in whole minutes, or seconds under one, with its unit: "42" to "min". */
internal fun minutesFigure(active: Long, strings: Strings = Strings.app): Pair<String, String> =
    if (active >= 60) "${active / 60}" to strings.get(R.string.glow_unit_min) else "$active" to strings.get(R.string.glow_summary_unit_s)

/**
 * A workout's length as a hero's number and its unit, the same duration the Time figure writes: "1" to "min 19" under an
 * hour, "1" to "h 02" from one, and seconds under a minute. Where the length doesn't start with its leading number (another
 * language's digits), it falls back to whole minutes.
 */
internal fun heroDuration(active: Long, strings: Strings = Strings.app): Pair<String, String> {
    if (active < 60) return minutesFigure(active, strings)
    val lead = if (active < 3600) active / 60 else active / 3600
    val unit = Units.length(active, strings = strings).removePrefix("$lead").trim()
    return if (unit.isEmpty()) minutesFigure(active, strings) else "$lead" to unit
}

/**
 * What the hero shows for [summary]'s [family]; [usualLoad] names a game's Load. When the kind's own number is missing
 * (no heart rate for an indoor workout, no sets or rounds), the hero falls back to the minutes, for every kind.
 */
@Suppress("UnusedParameter", "UNUSED_PARAMETER") // [usualLoad] named a game's Load before the label read plain; callers still pass it.
internal fun summaryHero(family: Workouts.Family, summary: PhoneSummary, usualLoad: Float?, strings: Strings = Strings.app): SummaryHero {
    val h = HeroParts(summary, strings)
    return when (family) {
        Workouts.Family.Distance -> h.distance()
        Workouts.Family.IndoorCardio -> h.indoor()
        Workouts.Family.Strength -> h.strength()
        Workouts.Family.Intervals -> h.intervals()
        Workouts.Family.MindBody -> h.mindBody()
        Workouts.Family.Games -> h.games()
    }
}

/** What every kind's hero draws on, read once: the figures of [summary] and the labels they take. */
private class HeroParts(val summary: PhoneSummary, val strings: Strings) {
    private val a = summary.analysis
    private val active = a.activeSeconds
    private val readings = FamilySummary.readings(summary)
    private val peak = readings.maxOfOrNull { it.second }?.toString()
    private val avg = a.workout.averageHeartRate?.toString()
    private val kcal = kcalFigure(a)
    private val time = Units.length(active)
    private val minutes = heroDuration(active, strings)
    private val w = HeroWords(strings)

    private fun byTime(figures: List<Pair<String?, String>>) =
        SummaryHero(minutes.first, minutes.second, listOf(time to w.time) + figures, title = w.time)

    fun distance(): SummaryHero {
        val m = summary.distanceMeters
        val pace = Pace.clock(Pace.of(FamilySummary.gpsDistance(m, summary.distanceFromSteps), active))
        if (m == null || m <= 0f) return byTime(listOf(avg to w.avgBpm, kcal to w.activeKcal(kcal != null)))
        val figure = Units.figure(m, Units.Precision.Workout, Units.DOT_LOCALE, strings)
        return SummaryHero(
            figure?.number ?: m.toInt().toString(), figure?.unit ?: Units.minorUnit(strings),
            listOf(time to w.time, if (pace != null) pace to w.avgPace else kcal to w.activeKcal(kcal != null), avg to w.avgBpm),
            pace = pace, title = strings.get(R.string.glow_summary_hero_distance),
        )
    }

    fun indoor(): SummaryHero {
        val bpm = avg ?: return byTime(listOf(kcal to w.activeKcal(kcal != null), peak to w.peak))
        return SummaryHero(
            bpm, w.avgBpmUnit, listOf(time to w.time, kcal to w.activeKcal(kcal != null), peak to w.peak),
            title = strings.get(R.string.glow_summary_hero_avg_bpm),
        )
    }

    fun strength(): SummaryHero {
        val lifted = org.openstrap.app.data.Strength.kgLifted(summary.sets)
        val sets = FamilySummary.setsDone(summary.sets)
        // Without a log the hero is the sets, and kg lifted isn't shown (spec 2.8); without sets, the minutes.
        return when {
            lifted != null -> SummaryHero(
                Units.count(Math.round(lifted), Units.DOT_LOCALE), strings.get(R.string.glow_summary_unit_kg_lifted),
                listOf(time to w.time, "$sets" to strings.get(R.string.glow_summary_figure_sets), avg to w.avgBpm),
                title = strings.get(R.string.glow_summary_hero_lifted),
            )
            sets > 0 -> SummaryHero(
                "$sets", strings.plural(R.plurals.glow_summary_unit_sets, sets),
                listOf(time to w.time, avg to w.avgBpm, peak to w.peak), title = strings.get(R.string.glow_summary_figure_sets),
            )
            else -> byTime(listOf(avg to w.avgBpm, peak to w.peak))
        }
    }

    fun intervals(): SummaryHero {
        val done = org.openstrap.app.data.Phases.roundsDone(summary.rounds)
        val reps = summary.structure is Structure.Reps
        if (done == 0) return byTime(listOf(avg to w.avgBpm, peak to w.peak))
        return SummaryHero(
            "$done", strings.get(if (reps) R.string.glow_summary_unit_reps_done else R.string.glow_summary_unit_rounds_done),
            listOf(time to w.time, peak to w.peak, kcal to w.activeKcal(kcal != null)),
            title = strings.get(if (reps) R.string.glow_summary_hero_reps else R.string.glow_summary_hero_rounds),
        )
    }

    fun mindBody(): SummaryHero {
        val settled = FamilySummary.settled(readings, a.shownStart, a.end)
        val average = a.workout.averageHeartRate?.let { strings.format(R.string.glow_summary_line_average_bpm, it) }
        val burned = a.activeKcal?.let { strings.format(R.string.glow_summary_line_active_kcal, Math.round(it)) }
        return SummaryHero(
            minutes.first, minutes.second, listOf(avg to w.avgBpm, kcal to w.activeKcal(kcal != null)),
            sentence = settled?.let { (f, t) -> FamilySummary.settledLine(f, t) },
            line = joined(listOfNotNull(average, burned), strings), title = w.time,
        )
    }

    fun games(): SummaryHero {
        val load = a.workout.trainingLoad
        val unit = if (active >= 60) R.string.glow_summary_unit_min_played else R.string.glow_summary_unit_s_played
        return SummaryHero(
            minutes.first, strings.get(unit),
            listOf(load?.toString() to strings.get(R.string.glow_summary_figure_load), kcal to w.activeKcal(kcal != null), peak to w.peak),
            title = strings.get(R.string.glow_summary_hero_played),
        )
    }
}

/** The labels the hero's figures share across the kinds of workout, read once a hero. */
private class HeroWords(strings: Strings) {
    val time = strings.get(R.string.glow_summary_figure_time)
    val avgBpm = strings.get(R.string.glow_summary_figure_avg_bpm)
    val avgBpmUnit = strings.get(R.string.glow_summary_unit_avg_bpm)
    private val activeKcalName = strings.get(R.string.glow_summary_figure_active_kcal)
    private val kcalNeedsProfile = strings.get(R.string.workout_live_figure_kcal_needs_profile)

    /** The label of the Active kcal figure: its name, or why it is a dash (kcal needs weight, height and age). */
    fun activeKcal(hasValue: Boolean) = if (hasValue) activeKcalName else kcalNeedsProfile
    val avgPace = strings.get(R.string.glow_summary_figure_avg_pace)
    val peak = strings.get(R.string.glow_summary_figure_peak_bpm)
}

/** [parts] as one line, each pair joined as "Average 141 bpm, 320 active kcal", null when there are none. */
internal fun joined(parts: List<String>, strings: Strings = Strings.app): String? =
    parts.reduceOrNull { a, b -> strings.format(R.string.glow_summary_join, a, b) }

/* ------------------------------------------------- The hero ------------------------------------------------- */

/** The light angle a family's hero keeps on Ready, Live and Summary (workout rules, Tokens): strength lower left, yoga left. */
private fun heroAngle(family: Workouts.Family?): Float = when (family) {
    Workouts.Family.Strength -> LightAngle.LowerLeft
    Workouts.Family.MindBody -> MIND_ANGLE
    else -> LightAngle.UpperRight
}

private const val MIND_ANGLE = 160f

/** Yoga's rim is calmer (workout rules: "Yoga 160 with k 0.8"). */
private const val MIND_RIM = 0.8f

/**
 * The summary's hero (board HaloSummaryRun): a card lit from one edge in the workout's [color], at the light angle of
 * its [family]. A title row (what the number counts, with [note] on the right), the one number in dots with its unit,
 * then the sentence in the lit card's ink. [effort] and [swell] stay so the callers compile: Halo's hero has no
 * effort light, and a personal best ticks but no longer swells it.
 */
@Suppress("UnusedParameter", "UNUSED_PARAMETER")
@Composable
internal fun SummaryHeroCard(
    hero: SummaryHero, color: Color, effort: Float = 0f, swell: () -> Float = { 0f },
    family: Workouts.Family? = null, note: String? = null,
) {
    val style = remember(color, family) { heroStyle(color, heroAngle(family), if (family == Workouts.Family.MindBody) MIND_RIM else 1f) }
    HaloCard(Modifier.fillMaxWidth(), tone = CardTone.lit(style)) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.S12)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    hero.title.orEmpty(), Modifier.weight(1f, fill = false), color = Color.White, style = GlowType.heroTitle, maxLines = 1,
                )
                if (note != null) {
                    Text(note, Modifier.padding(start = Space.S12), color = GlowType.noteInk, style = GlowType.noteSmall, maxLines = 1)
                }
            }
            HaloBigNumber(hero.value, BigNumber.Detail, unit = hero.unit, unitColor = GlowType.noteInk, unitLines = 2, fit = true)
            val sentence = hero.sentence ?: hero.line
            if (sentence != null) Text(sentence, color = GlowType.noteInk, style = GlowType.secondary)
        }
    }
}

/* ------------------------------------------------ Shared pieces ------------------------------------------------ */



/** The soft white of a sentence, a peak and a chip (boards: #C9CED6). */
internal val SoftInk = Ink.Strong

/** A caption under a chart (boards: 11 px #6E7480). */
private val CaptionInk = GlowType.footnoteColor

/** The hairline between two parts of a card: one step above the surface ([OnSurface.line], section 5 row 19). */
@Composable
internal fun CardDivider() = Box(Modifier.fillMaxWidth().height(RowSpec.Divider).background(OnSurface.line))

/** A short sentence under a card part (boards: 13 px #C9CED6). */
@Composable
private fun CardSentence(text: String) =
    Text(text, color = SoftInk, style = GlowType.secondary)

/** A caption row with the clocks or words under a chart, [start] at the left and [end] at the right. */
@Composable
private fun CaptionRow(start: String, end: String, mid: String? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(start, color = CaptionInk, style = GlowType.caption)
        if (mid != null) Text(mid, color = CaptionInk, style = GlowType.caption)
        Text(end, color = CaptionInk, style = GlowType.caption)
    }
}

/* ---------------------------------------------- Each kind's cards ---------------------------------------------- */

/**
 * The section for [family] (boards HaloSummaryRun to HaloSummaryGames): the kind's own solid card or two, then the
 * [figures] row, then whatever the kind says in a line. [week] is this week's count toward the goal, for the line under
 * a calm session's week.
 */
// The lists arrive whole with the workout and never change in place.
@Suppress("UnstableCollections", "ComposeUnstableCollections")
@Composable
internal fun FamilySection(
    family: Workouts.Family, summary: PhoneSummary, liveRoute: List<Route.Point>, others: List<WorkoutAnalysis>, weekStarts: List<Long>,
    /** False on a past workout's page, whose heart rate card has the line already: the lines under it stay. */
    heartLine: Boolean = true,
    week: Rewards.WeekLine? = null,
    figures: List<Pair<String?, String>> = emptyList(),
) {
    val a = summary.analysis
    val readings = remember(summary) { FamilySummary.readings(summary) }
    // The stored time in zones, as the hero and the share card read it, so the page shows one total.
    val zones = remember(a) { ZoneSeconds(a.zoneSeconds) }
    when (family) {
        // A run with no route, splits or laps (no GPS) has the indoor card: zones and the heart line, as indoors.
        Workouts.Family.Distance -> if (!runCard(summary, liveRoute)) {
            IndoorCard(zones, a.maxHeartRate, readings, indoorTarget(a), chart = heartLine)
        }
        Workouts.Family.IndoorCardio -> IndoorCard(zones, a.maxHeartRate, readings, indoorTarget(a), chart = heartLine)
        Workouts.Family.Strength -> StrengthCard(summary, readings)
        Workouts.Family.Intervals -> IntervalsCard(summary, readings)
        Workouts.Family.MindBody -> {
            val settled = FamilySummary.settled(readings, a.shownStart, a.end)
            HeartLineCard(readings, MetricColors.evening, a.shownStart, a.end, settled, chart = heartLine)
        }
        Workouts.Family.Games -> GamesCard(summary, readings, zones)
    }
    HaloFigures(figures)
    // What each kind says after its figures: how indoor cardio compares with last time, a calm session's week.
    if (family == Workouts.Family.IndoorCardio) {
        FamilySummary.versusLast(a, others)?.let { VersusLast(it.replaceFirstChar { c -> c.lowercase() }) }
    }
    if (family == Workouts.Family.MindBody) {
        val day = java.time.Instant.ofEpochSecond(a.shownStart).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        // This session's own day is lit even when it is too short to count toward the goal. A count of none then says
        // the days with a workout instead, so the line and the marks agree.
        val note = week?.takeIf { it.count > 0 }?.let { FamilySummary.weekGoalLine(it.count, it.goal) }
        WeekDaysPanel((weekStarts + a.shownStart).distinct(), note = note, day = day, color = familyColor(family))
    }
}

/** An indoor workout's kcal target line and whether it was reached, null without a target. */
private fun indoorTarget(a: WorkoutAnalysis): Pair<String, Boolean>? {
    val kcal = a.activeKcal?.let(Math::round)
    return org.openstrap.app.ui.workout.SummaryCopy.targetLine(a.target, a.targetReachedSeconds, kcal)?.let {
        it to FamilySummary.targetReached(a.target, a.targetReachedSeconds, kcal)
    }
}

/** Strength's card (board HaloSummaryStrength): the sets by exercise, then how the heart rate came down in the rests. */
@Composable
private fun StrengthCard(summary: PhoneSummary, readings: List<Pair<Long, Int>>) {
    val groups = remember(summary) { FamilySummary.exerciseSets(summary.sets, readings) }
    val below = summary.restBelow
    val rest = remember(summary) { FamilySummary.restRecovery(summary.sets, readings, below) }
    val average = FamilySummary.averageRest(summary.sets)
    // Rest on the clock only (no heart rate level set) still says how long it was.
    val restLine = if (below != null && rest != null) FamilySummary.restLine(rest, below, summary.restBefore)
        else average?.let { stringResource(R.string.glow_summary_rest_on_time) }
    if (groups.isEmpty() && restLine == null) return
    HaloCard {
        if (groups.isNotEmpty()) SetsBlock(groups, summary, summary.analysis.maxHeartRate)
        if (restLine != null) {
            if (groups.isNotEmpty()) CardDivider()
            HaloTitle(
                stringResource(R.string.glow_summary_rest_title),
                note = average?.let { stringResource(R.string.glow_summary_rest_average, workoutClock(it)) },
            )
            CardSentence(restLine)
        }
    }
}

/** An interval workout's card (board HaloSummaryHiit): a column for each round or rep, then how the heart rate recovered. */
@Composable
private fun IntervalsCard(summary: PhoneSummary, readings: List<Pair<Long, Int>>) {
    val reps = summary.structure is Structure.Reps
    val lines = remember(summary) { FamilySummary.rounds(summary.rounds, readings) }
    // Without a heart rate the peaks are empty: a run's reps show their times instead, timed rounds nothing.
    val peaks = lines.any { it.peak != null }
    val times = summary.rounds.mapNotNull { r -> r.workEnd?.let { it - r.workStart } }
    val roundsTitle = stringResource(if (reps) R.string.glow_summary_each_rep else R.string.glow_summary_each_round)
    val rest = (summary.structure as? Structure.Timed)?.rest ?: (summary.structure as? Structure.Reps)?.rest
    val line = FamilySummary.roundsLine(lines, rest)
    val showRounds = peaks || (reps && times.isNotEmpty())
    if (!showRounds && line == null) return
    HaloCard {
        if (peaks) RoundsBlock(lines, roundsTitle, summary.analysis.maxHeartRate, summary.structure as? Structure.Timed)
        else if (reps && times.isNotEmpty()) RepTimesBlock(times)
        if (line != null) {
            if (showRounds) CardDivider()
            HaloTitle(stringResource(R.string.glow_summary_recovery_title), note = stringResource(R.string.glow_summary_in_each_rest))
            CardSentence(line)
        }
    }
}

/** A game's card (board HaloSummaryGames): each half or period, then the time in zones. */
@Suppress("UnstableCollections", "ComposeUnstableCollections")
@Composable
private fun GamesCard(summary: PhoneSummary, readings: List<Pair<Long, Int>>, zones: ZoneSeconds) {
    val heartZones = LocalHeartZones.current
    val a = summary.analysis
    // Named out of the periods it was set up with, not the ones played, so ending early doesn't rename them.
    val of = (summary.structure as? Structure.Periods)?.rounds ?: summary.rounds.count { it.workEnd != null }
    val periods = remember(summary, heartZones) { FamilySummary.periods(summary.rounds, readings, a.maxHeartRate, of, heartZones) }
    val read = FamilySummary.gameRead(readings, a.shownStart, a.end)
    val showPeriods = periods.isNotEmpty() || read != null
    val showZones = zones.sum() > 0
    if (!showPeriods && !showZones) return
    HaloCard {
        if (showPeriods) PeriodsBlock(periods, of, read, a.maxHeartRate)
        if (showPeriods && showZones) CardDivider()
        if (showZones) ZoneStripBlock(zones)
    }
}

/** Whether a distance workout has a run card ([runCard]): a route, splits or laps. Without one it has the indoor card. */
internal fun hasRunCard(summary: PhoneSummary, liveRoute: List<Route.Point> = emptyList()): Boolean {
    if (Route.mappable(summary.route)) return true
    val rows = splitRowsOf(summary, liveRoute)
    return rows.isNotEmpty() || manualLaps(summary, rows).isNotEmpty()
}

/**
 * A run's card (board HaloSummaryRun): the route in its zones' colours with the climb under it, then each kilometre as
 * a bar, then the laps when any were marked apart from the splits ([manualLaps]). Draws nothing and returns false
 * without a route, splits or laps.
 */
@Composable
private fun runCard(summary: PhoneSummary, liveRoute: List<Route.Point>): Boolean {
    val mapped = Route.mappable(summary.route)
    val rows = remember(summary, liveRoute) { splitRowsOf(summary, liveRoute) }
    val laps = manualLaps(summary, rows)
    if (!mapped && rows.isEmpty() && laps.isEmpty()) return false
    val heartZones = LocalHeartZones.current
    val maxHr = summary.analysis.maxHeartRate
    HaloCard {
        if (mapped) {
            RouteMap(summary)
            val climb = Route.climb(summary.route)?.let { stringResource(R.string.glow_summary_climb, Math.round(it)) }
            HaloTitle(climb ?: stringResource(R.string.glow_summary_route), note = stringResource(R.string.glow_summary_route_zones))
        }
        if (rows.isNotEmpty()) {
            if (mapped) CardDivider()
            HaloTitle(
                stringResource(R.string.glow_summary_splits),
                note = stringResource(Units.res(R.string.glow_summary_splits_note, R.string.glow_summary_splits_note_mi)),
            )
            SplitBars(rows, FamilySummary.fastestSplit(rows), maxHr)
        }
        if (laps.isNotEmpty()) {
            if (mapped || rows.isNotEmpty()) CardDivider()
            HaloTitle(stringResource(R.string.glow_summary_laps), note = "${laps.size}")
            Column {
                laps.forEach { lap ->
                    SplitLine(
                        stringResource(R.string.glow_summary_lap, lap.index + 1), 0f, workoutClock(lap.end - lap.start),
                        lap.averageHeartRate?.let { zoneColor(it, maxHr, heartZones) } ?: EdgeNeutral,
                        bold = false, bar = Color.Transparent,
                    )
                }
            }
        }
    }
    return true
}

/** One row of the splits (board HaloSummaryRun): its name, a bar, its time and a dot in its zone's colour. A compact row. */
@Composable
private fun SplitLine(name: String, share: Float, time: String, dot: Color, bold: Boolean, bar: Color) = HaloRow(
    name, titleInk = GlowType.noteColor, titleWidth = SplitNameWidth,
    middle = {
        Box(Modifier.weight(1f).height(Space.S8)) {
            if (share > 0f) Box(Modifier.fillMaxWidth(share).fillMaxHeight().clip(CircleShape).background(bar))
        }
    },
    value = time, valueInk = Color.White,
    valueStyle = (if (bold) FigureText14.copy(fontWeight = FontWeight.SemiBold) else FigureText14).copy(textAlign = TextAlign.End),
    valueModifier = Modifier.widthIn(min = SplitTimeWidth),
    trail = { Box(Modifier.size(Space.S8).background(dot, CircleShape)) },
    compact = true, divider = false,
)

/** A split's name column and the least its time takes, so the bars line up down the list. */
private val SplitNameWidth = 58.dp
private val SplitTimeWidth = 44.dp

/**
 * Each kilometre as a bar as long as its speed against the fastest one's: grey, the fastest white with its time bold. A
 * part kilometre at the end has its time and no bar. The dot is the zone its average heart rate sat in.
 */
// The rows arrive whole with the workout and never change in place.
@Suppress("UnstableCollections", "ComposeUnstableCollections")
@Composable
private fun SplitBars(rows: List<SplitRow>, fastest: Int?, maxHr: Int) {
    val heartZones = LocalHeartZones.current
    val seconds = rows.map { it.seconds }
    val best = rows.indices.filter { rows[it].kind == SplitRow.Kind.Whole && seconds[it] > 0 }.minOfOrNull { seconds[it] }
    Column {
        rows.forEachIndexed { i, r ->
            val lit = i == fastest
            val share = if (best != null && r.kind == SplitRow.Kind.Whole && seconds[i] > 0) best.toFloat() / seconds[i] else 0f
            SplitLine(
                r.name, share, r.time, r.averageBpm?.let { zoneColor(it, maxHr, heartZones) } ?: EdgeNeutral,
                bold = lit, bar = if (lit) Color.White else SplitBarInk,
            )
        }
    }
}

/** A kilometre's bar (board HaloSummaryRun: #2E3038). */
private val SplitBarInk = Greys.SplitBar

/** A value in a row of a card (boards: 14 px with tabular digits). */
private val FigureText14 = GlowType.buttonSmall.copy(fontWeight = FontWeight.Normal, fontFeatureSettings = "tnum")

/** A period's name and average heart rate (board HaloSummaryGames: 14 px regular). */
private val PeriodText = GlowType.buttonSmall.copy(fontWeight = FontWeight.Normal)

/**
 * Time in zones as the part of a card that opens it (boards HaloSummaryIndoor, HaloPastWorkout): the zone with the most
 * time in the title row, the strip, then each zone with time in it, hardest first, with its range and its time.
 */
@Composable
internal fun ZonesBlock(zones: ZoneSeconds, maxHr: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.S12)) {
        HaloTitle(stringResource(R.string.glow_summary_time_in_zones), note = stringResource(R.string.glow_summary_zones_max, maxHr))
        ZoneBar(zones, ZONE_COLORS, height = 8.dp, gap = 2.dp, corner = 4.dp, ends = 4.dp)
        Column {
            for (z in zones.indices.reversed()) {
                if (zones[z] <= 0) continue
                Row(
                    Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.S8),
                ) {
                    Box(Modifier.size(8.dp).background(ZONE_COLORS[z], CircleShape))
                    Text(
                        buildAnnotatedString {
                            append(stringResource(R.string.glow_summary_zone, z + 1))
                            append(" ")
                            withStyle(SpanStyle(color = GlowType.noteColor)) { append(zoneRange(z, maxHr)) }
                        },
                        Modifier.weight(1f), color = Color.White, style = GlowType.secondary, maxLines = 1,
                    )
                    Text(workoutClock(zones[z].toLong()), color = Color.White, style = FigureText14, maxLines = 1)
                }
            }
        }
    }
}

/**
 * Indoor cardio's card (board HaloSummaryIndoor): the zones, then under a hairline the heart rate with its peak marked,
 * then the kcal [target] with a tick when it was reached. Without the line ([chart] off, or too few readings) the
 * target alone. Nothing when there is none of them.
 */
// The lists arrive whole with the workout and never change in place.
@Suppress("UnstableCollections", "ComposeUnstableCollections")
@Composable
private fun IndoorCard(zones: ZoneSeconds, maxHr: Int, readings: List<Pair<Long, Int>>, target: Pair<String, Boolean>?, chart: Boolean) {
    val hasZones = zones.sum() > 0
    val line = chart && readings.size >= 10
    if (!hasZones && !line && target == null) return
    val values = remember(readings) { FamilySummary.heartLine(readings, HeartPoints, window = 3) }
    HaloCard {
        if (hasZones) ZonesBlock(zones, maxHr)
        if (hasZones && (line || target != null)) CardDivider()
        if (line) {
            val range = Ranges.of("${readings.minOf { it.second }}", "${readings.maxOf { it.second }}")
            HaloTitle(stringResource(R.string.glow_summary_heart_rate), note = range)
            PlainLine(values, MetricColors.heart, 90.dp, dots = LineDots.Highest)
            CaptionRow(clock(readings.first().first), clock(readings.last().first))
        }
        target?.let { (text, reached) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(20.dp).background((if (reached) MetricColors.good else Color.White).copy(alpha = 0.16f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(if (reached) AppIcons.Tick02 else AppIcons.Target, null, tint = if (reached) MetricColors.good else Color.White, modifier = Modifier.size(12.dp)) }
                Text(text, color = Color.White, style = GlowType.secondary, modifier = Modifier.padding(start = Space.S8))
            }
        }
    }
}

/** "vs last time:" and how this one compares, as a line of its own under the figures (board HaloSummaryIndoor). */
@Composable
private fun VersusLast(line: String) {
    val full = stringResource(R.string.glow_summary_vs_last, line)
    val split = full.lastIndexOf(line).coerceAtLeast(0)
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = GlowType.noteColor)) { append(full.substring(0, split)) }
            append(full.substring(split))
        },
        color = SoftInk, style = GlowType.secondary, modifier = Modifier.padding(horizontal = Space.S4),
    )
}

/** How many points a heart line is drawn through. */
private const val HeartPoints = 60

/**
 * The sets by exercise (board HaloSummaryStrength): each exercise with the kilograms lifted in it, then each set's
 * number, what was lifted, a bar as long as its peak against the hardest set's in the zone it reached, and the peak.
 */
@Composable
private fun SetsBlock(groups: List<ExerciseSets>, summary: PhoneSummary, maxHr: Int) {
    val heartZones = LocalHeartZones.current
    val shares = remember(groups) { FamilySummary.peakShares(groups.flatMap { g -> g.sets.map { it.peak } }) }
    HaloTitle(stringResource(R.string.glow_summary_sets_title), note = stringResource(R.string.glow_summary_peak_heart_rate))
    var i = 0
    groups.forEach { g ->
        Column(verticalArrangement = Arrangement.spacedBy(Space.S2)) {
            if (g.name != null) {
                val kg = remember(summary, g.name) {
                    val mine = summary.sets.filter { it.exercise?.trim()?.lowercase() == g.name.trim().lowercase() }
                    org.openstrap.app.data.Strength.kgLifted(mine)
                }
                Row(Modifier.fillMaxWidth().padding(bottom = Space.S4), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        g.name, Modifier.weight(1f), color = Color.White, style = GlowType.buttonSmall, maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (kg != null) Text(
                        stringResource(R.string.glow_summary_exercise_total, Units.count(Math.round(kg))),
                        Modifier.padding(start = Space.S12), color = GlowType.noteColor, style = GlowType.noteSmall, maxLines = 1,
                    )
                }
            }
            g.sets.forEach { s ->
                val share = shares[i++]
                // Heights and columns are minimums: at a large font a set's words and numbers take what they need.
                Row(Modifier.fillMaxWidth().heightIn(min = 22.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.S8)) {
                    Text("${s.number}", Modifier.widthIn(min = 14.dp), color = CaptionInk, style = GlowType.secondary, maxLines = 1)
                    Text(s.text, Modifier.widthIn(min = 80.dp), color = Color.White, style = GlowType.secondary, maxLines = 1)
                    Box(Modifier.weight(1f).height(6.dp)) {
                        if (share != null) {
                            val bar = zoneColor(s.peak, maxHr, heartZones)
                            Box(Modifier.fillMaxWidth(share).fillMaxHeight().clip(CircleShape).background(bar))
                        }
                    }
                    Text(
                        s.peak?.toString().orEmpty(), Modifier.widthIn(min = 30.dp), color = SoftInk,
                        style = GlowType.secondary, maxLines = 1,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

/** Rounds shown with their figures; past this many there's no room, and the columns stand alone. */
private const val LabelledRounds = 10

/** The hardest round's column; the rest are as tall as their peak against it. */
private val RoundColumnHeight = 96.dp

/**
 * Each round as a column (board HaloSummaryHiit): its peak over it, as tall as the peak against the hardest round's, in
 * the zone it reached, the hardest outlined. Under them what the rounds were set up as and which are drawn.
 */
@Composable
private fun RoundsBlock(lines: List<RoundLine>, title: String, maxHr: Int, structure: Structure.Timed?) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.S12)) {
        val heartZones = LocalHeartZones.current
        val shares = remember(lines) { FamilySummary.peakShares(lines.map { it.peak }) }
        val hardest = remember(lines) { FamilySummary.hardestRound(lines) }
        val labelled = lines.size <= LabelledRounds
        val gap = if (labelled) 6.dp else 3.dp
        val shape = Corner.Pip
        HaloTitle(title, note = stringResource(R.string.glow_summary_figure_peak_bpm))
        Row(Modifier.fillMaxWidth().height(RoundColumnHeight + 24.dp), horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.Bottom) {
            lines.forEachIndexed { i, r ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Space.S4)) {
                    if (labelled && r.peak != null) Text(
                        "${r.peak}", color = SoftInk, maxLines = 1,
                        style = if (i == hardest) GlowType.caption.copy(fontWeight = FontWeight.SemiBold) else GlowType.caption,
                    )
                    shares[i]?.let { share ->
                        Box(
                            Modifier.fillMaxWidth().height(RoundColumnHeight * share).clip(shape)
                                .background(zoneColor(r.peak, maxHr, heartZones))
                                .let { if (i == hardest) it.border(ChipSpec.Ring, Color.White, shape) else it },
                        )
                    }
                }
            }
        }
        CaptionRow(
            structure?.let { stringResource(R.string.glow_summary_work_rest, it.work, it.rest) }.orEmpty(),
            stringResource(R.string.glow_summary_rounds_span, 1, lines.size),
        )
    }
}

/**
 * A run's reps without a heart rate: each rep's time as a column, the fastest full height, bright and outlined, the
 * rest as tall as the fastest against them, with the time under each.
 */
@Composable
private fun RepTimesBlock(times: List<Long>) {
    val fastest = times.filter { it > 0 }.minOrNull() ?: return
    val best = times.indexOf(fastest)
    val labelled = times.size <= LabelledRounds
    val gap = if (labelled) 6.dp else 3.dp
    val shape = Corner.Pip
    val color = familyColor(Workouts.Family.Intervals)
    Column(verticalArrangement = Arrangement.spacedBy(Space.S12)) {
        HaloTitle(stringResource(R.string.glow_summary_each_rep), note = stringResource(R.string.glow_summary_rep_times_time))
        Row(Modifier.fillMaxWidth().height(RoundColumnHeight), horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.Bottom) {
            times.forEachIndexed { i, t ->
                val share = if (t > 0) (fastest.toFloat() / t).coerceIn(0.3f, 1f) else 0.3f
                Box(
                    Modifier.weight(1f).height(RoundColumnHeight * share).clip(shape).background(if (i == best) color else color.copy(alpha = 0.45f))
                        .let { if (i == best) it.border(ChipSpec.Ring, Color.White, shape) else it },
                )
            }
        }
        if (labelled) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            times.forEachIndexed { i, t ->
                Text(
                    workoutClock(t), color = if (i == best) Color.White else CaptionInk, maxLines = 1,
                    style = if (i == best) GlowType.caption.copy(fontWeight = FontWeight.SemiBold) else GlowType.caption,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * A game's halves or periods (board HaloSummaryGames): each one's name and time with its average heart rate, a bar as
 * long as it against the highest in its zone's colour. Then how the game went.
 */
@Composable
private fun PeriodsBlock(periods: List<PeriodLine>, of: Int, read: String?, maxHr: Int) {
    val heartZones = LocalHeartZones.current
    val shares = remember(periods) { FamilySummary.peakShares(periods.map { it.averageBpm }) }
    HaloTitle(stringResource(
            when (of) {
                2 -> R.string.glow_summary_each_half
                4 -> R.string.glow_summary_each_quarter
                else -> R.string.glow_summary_each_period
            },
        ), note = stringResource(R.string.glow_summary_period_note))
    periods.forEachIndexed { i, p ->
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.S8)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    buildAnnotatedString {
                        append(p.name)
                        append(" ")
                        withStyle(SpanStyle(color = GlowType.noteColor)) { append(workoutClock(p.seconds)) }
                    },
                    color = Color.White, style = PeriodText, maxLines = 1,
                )
                p.averageBpm?.let { Text("$it", color = Color.White, style = PeriodText, maxLines = 1) }
            }
            Box(Modifier.fillMaxWidth().height(8.dp)) {
                shares[i]?.let { share ->
                    val bar = zoneColor(p.averageBpm, maxHr, heartZones)
                    Box(Modifier.fillMaxWidth(share).fillMaxHeight().clip(CircleShape).background(bar))
                }
            }
        }
    }
    read?.let { CardSentence(it) }
}

/**
 * Time in zones for a game or a past workout, as the part of a card (boards HaloSummaryGames, HaloPastWorkout): the zone
 * with the most time in the title row, the strip, then the lowest, the longest and the highest zone's minutes under it.
 */
@Composable
internal fun ZoneStripBlock(zones: ZoneSeconds) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.S12)) {
        val minutes = remember(zones) { FamilySummary.zoneMinutes(zones) }
        HaloTitle(stringResource(R.string.glow_summary_time_in_zones), note = FamilySummary.mostlyZone(zones))
        ZoneBar(zones, ZONE_COLORS, height = 8.dp, gap = 2.dp, corner = 4.dp, ends = 4.dp)
        val shown = zones.indices.filter { zones[it] > 0 }
        val most = shown.maxByOrNull { zones[it] }
        val keys = listOfNotNull(shown.firstOrNull(), most, shown.lastOrNull()).distinct().sorted()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (keys.size == 1) Arrangement.Start else Arrangement.SpaceBetween) {
            keys.forEach { z ->
                Text(stringResource(R.string.glow_summary_zone_minutes, z + 1, minutes[z]), color = CaptionInk, style = GlowType.caption)
            }
        }
    }
}

/** Time in zones on its own solid card, for a past workout whose kind has no section showing it. */
@Composable
internal fun ZoneStripPanel(zones: ZoneSeconds) = HaloCard { ZoneStripBlock(zones) }

/**
 * The heart rate over a calm session (board HaloSummaryMind): its range or how far it came down in the title row, a
 * smooth line with its two ends marked, then the clocks of its start and end. Without the line ([chart] off, or too few
 * readings) nothing.
 */
@Suppress("UnstableCollections", "ComposeUnstableCollections")
@Composable
private fun HeartLineCard(
    readings: List<Pair<Long, Int>>, color: Color, start: Long, end: Long, settled: Pair<Int, Int>? = null, chart: Boolean = true,
) {
    if (readings.size < 10 || !chart) return
    val values = remember(readings) { FamilySummary.heartLine(readings, HeartPoints, window = 9) }
    HaloCard {
        val drop = settled?.let { FamilySummary.settledDrop(it.first, it.second) }
        val range = Ranges.of("${readings.minOf { it.second }}", "${readings.maxOf { it.second }}")
        val down = drop?.let { stringResource(R.string.glow_summary_heart_down, it) }
        HaloTitle(stringResource(R.string.glow_summary_heart_rate), note = down ?: range)
        PlainLine(values, color, 100.dp, dots = LineDots.Ends)
        CaptionRow(clock(start), clock(end))
    }
}

/**
 * The starts of the [workouts] that count toward the weekly goal by the rule the week count uses
 * ([Rewards.countsTowardGoal]), for the goal moment's week.
 */
internal fun goalWeekStarts(workouts: List<WorkoutAnalysis>): List<Long> =
    workouts.filter { Rewards.countsTowardGoal(it.startedByYou, it.activeSeconds) }.map { it.shownStart }

/** The starts of every one of [workouts]: a day with any workout is lit on [WeekDaysPanel], however short. */
internal fun workoutDayStarts(workouts: List<WorkoutAnalysis>): List<Long> = workouts.map { it.shownStart }

/**
 * The days of [day]'s week as seven rounded marks from the locale's first day (board HaloSummaryMind), no card: a day
 * with a workout filled in [color], today ringed in white, each day's letter under it, and the [note] beside the title.
 */
@Composable
internal fun WeekDaysPanel(weekStarts: List<Long>, note: String? = null, day: LocalDate = LocalDate.now(), color: Color = Color.White) {
    val firstDay = remember { Weeks.firstDay() }
    val days = remember(weekStarts, day) { FamilySummary.weekDays(weekStarts, day, firstDay) }
    val today = LocalDate.now()
    val thisWeek = days.any { it.first == today }
    val n = days.count { it.second }
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.S4), verticalArrangement = Arrangement.spacedBy(Space.S12)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                stringResource(if (thisWeek) R.string.glow_summary_this_week else R.string.glow_summary_that_week),
                color = Color.White, style = GlowType.secondary.copy(fontWeight = FontWeight.SemiBold),
            )
            val right = note ?: pluralStringResource(R.plurals.glow_summary_week_sessions, n, n)
            Text(right, color = GlowType.noteColor, style = GlowType.secondary)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.S8)) {
            days.forEach { (d, on) ->
                val isToday = d == today
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Space.S4)) {
                    Box(
                        Modifier.fillMaxWidth().height(22.dp).clip(Corner.Pill).background(if (on) color else Greys.Control)
                            .let { if (isToday) it.border(ChipSpec.Ring, Color.White, Corner.Pill) else it },
                    )
                    Text(
                        d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        color = if (isToday) Color.White else CaptionInk, style = GlowType.caption, textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * A week as seven round marks, each day's letter under it; a day with a workout is filled, with a tick in it when
 * [ticks]. The [today] mark is ringed and its letter white. The share card's week (board HaloWeekShare).
 */
@Composable
internal fun WeekDots(
    days: List<Pair<String, Boolean>>, color: Color = Color.White, modifier: Modifier = Modifier, today: Int? = null, ticks: Boolean = true, dot: Dp = 28.dp,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        days.forEachIndexed { i, (letter, on) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.S8)) {
                Box(
                    Modifier.size(dot)
                        // Today's ring sits just outside the mark, so it keeps its size.
                        .let { if (i == today) it.drawBehind { drawCircle((if (on) color else Color.White).copy(alpha = 0.3f), radius = size.minDimension / 2 + 3.dp.toPx()) } else it }
                        .background(if (on) color else Ink.Line, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { if (on && ticks) Icon(AppIcons.Tick02, null, tint = Color.Black, modifier = Modifier.size(IconSize.GlyphS)) }
                Text(
                    letter, color = if (i == today) Color.White else Ink.Secondary, style = GlowType.caption,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * What this workout earned (boards HaloSummaryRun to HaloSummaryGames): each new best, badge and the weekly goal as a
 * dark chip under the figures, its icon in its colour; a best's words in white, the rest in #C9CED6.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EarnedChips(lines: List<EarnedLine>) {
    if (lines.isEmpty()) return
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.S8),
        verticalArrangement = Arrangement.spacedBy(Space.S8)) {
        lines.forEach { l ->
            val (icon, color) = when (l.kind) {
                EarnedLine.Kind.Best -> AppIcons.FlagCheckered to MetricColors.calories
                EarnedLine.Kind.Badge -> AppIcons.CheckCircle to MetricColors.good
                EarnedLine.Kind.Goal -> AppIcons.Target to if (l.met) MetricColors.good else Color.White
            }
            val text = l.value?.let { stringResource(R.string.glow_summary_join, l.text, it) } ?: l.text
            HaloChip(text, null, icon = icon, tint = color)
        }
    }
}
