package org.openstrap.app.ui.glow

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.openstrap.app.data.Reports
import org.openstrap.app.ui.dashboard.DetailCopy
import org.openstrap.app.ui.dashboard.Info
import org.openstrap.app.ui.dashboard.Metric
import org.openstrap.app.ui.dashboard.Range
import org.openstrap.app.ui.dashboard.ReportCopy
import org.openstrap.app.ui.dashboard.Score
import org.openstrap.app.ui.dashboard.TodayCopy
import org.openstrap.app.ui.theme.AppIcons
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.app.ui.workout.SportIcons
import org.openstrap.app.ui.workout.ZONE_COLORS
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** How the report stands: being worked out, ready, or failed. */
private sealed interface ReportState {
    data object Loading : ReportState
    data class Ready(val report: Reports.Report) : ReportState
    data object Failed : ReportState
}

/**
 * Reports, opened from the header's chart button: the week or the month ending on the day shown, against the stretch
 * before it, as WHOOP's weekly and monthly assessments compare them. Week: Recovery day by day against your normal,
 * the day's plan (its Load target and tonight's bedtime), each day's Load against that morning's target, the week
 * against the 3 before, and its sports. Month: Recovery over 30 days and how it spread, HRV and resting heart rate
 * against your normal, the month against the one before, the days of the week, your sports with their zones, and what
 * your habits do to the next morning. [load] works it out off the main thread. No strip: the report follows the day
 * shown, as the week story does.
 */
@Composable
internal fun ReportsDetail(
    date: LocalDate,
    load: suspend (LocalDate) -> Reports.Report,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val color = MetricColors.readiness
    val ranges = listOf(Range.Week, Range.Month)
    var rangeIndex by rememberSaveable { mutableIntStateOf(0) }
    val range = ranges[rangeIndex.coerceIn(ranges.indices)]
    var scrub by remember(range, date) { mutableStateOf<DetailHeadline?>(null) }
    var info by rememberSaveable { mutableStateOf(false) }
    // Kept while a newer report for the same day is worked out (after a sync); another day starts from Loading.
    val state by produceState<ReportState>(ReportState.Loading, date, load) {
        if ((value as? ReportState.Ready)?.report?.date != date) value = ReportState.Loading
        value = try {
            ReportState.Ready(load(date))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Report for $date failed", e)
            ReportState.Failed
        }
    }
    val report = (state as? ReportState.Ready)?.report
    val isToday = date == LocalDate.now()
    val days = if (range == Range.Week) Reports.WEEK_DAYS else Reports.MONTH_DAYS
    val average = report?.let { if (range == Range.Week) it.week.now.recovery else it.month.now.recovery }
    val headline = scrub ?: when {
        report == null && state == ReportState.Failed -> DetailHeadline.none("No report", "Try again in a moment")
        report == null -> DetailHeadline.none("Loading", ReportCopy.averageLabel(days).replaceFirstChar { it.uppercase() })
        average != null -> DetailHeadline(ReportCopy.whole(average), ReportCopy.averageLabel(days), TodayCopy.recoveryWord(Math.round(average), false))
        else -> ReportCopy.noAverage(days, report.daysWithData).let { (word, why) -> DetailHeadline.none(word, why) }
    }
    DetailTemplate(
        ReportCopy.TITLE, color, emptyList(), onInfo = { info = true }, onBack = onBack, contentPadding = contentPadding,
        headline = headline, strip = emptyList(), onDay = {}, ranges = ranges, range = range, onRange = { rangeIndex = ranges.indexOf(it) },
    ) {
        when {
            report == null && state == ReportState.Failed -> item { DetailEmpty(ReportCopy.FAILED) }
            report == null -> item { DetailEmpty(ReportCopy.LOADING) }
            range == Range.Week -> weekBody(report, isToday) { scrub = it }
            else -> monthBody(report) { scrub = it }
        }
    }
    if (info) InfoSheet(
        Info(ReportCopy.TITLE, ReportCopy.INFO_MEANING, emptyList(), how = "", moves = "", off = ""), color, null, null,
        onDismiss = { info = false }, rows = ReportCopy.INFO_ROWS,
    )
}

/* ------------------------------------------------ Week ---------------------------------------------- */

private fun LazyListScope.weekBody(r: Reports.Report, isToday: Boolean, onScrub: (DetailHeadline?) -> Unit) {
    val week = r.week
    val values = week.days.map { it.recovery?.toFloat() }
    val labels = week.days.map { shortDay(it.date) }
    item {
        if (values.all { it == null }) DetailEmpty(ReportCopy.noScores(Reports.WEEK_DAYS))
        else NormalTrend(
            values, levelsIn(values, week.normal), MetricColors.readiness, labels = labels,
            band = week.normal, bandLabel = week.normal?.let(ReportCopy::bandLabel),
            onScrub = { i -> onScrub(i?.let { recoveryAt(week.days[it]) }) },
        )
    }
    item { DetailSentence(ReportCopy.weekSentence(week.now, week.before)) }
    item { ReportPlan(r, isToday) }
    item {
        ListPanel(Modifier.padding(top = 8.dp), title = ReportCopy.TARGET_TITLE) {
            TargetBars(
                week.days.map { it.load }, week.targets.map { it?.range }, MetricColors.load, Modifier.padding(top = 6.dp), labels = labels,
                partial = week.days.indexOfFirst { it.partial }.takeIf { it >= 0 },
                onScrub = { i -> onScrub(i?.let { loadAt(week.days[it], week.targets[it]) }) },
            )
            DetailFigures(ReportCopy.balanceFigures(week.balance), Modifier.padding(top = 16.dp, bottom = 12.dp))
            ReportCopy.balanceBefore(week.balanceBefore)?.let { ReportNote(it) }
        }
    }
    item { ReportComparison(ReportCopy.WEEK_AGAINST, ReportCopy.weekRows(week.now, week.before)) }
    if (week.sports.isNotEmpty()) item { ReportSports(week.sports, month = false) }
}

/** The day's plan: the morning's Load target and, today, tonight's bedtime. */
@Composable
private fun ReportPlan(r: Reports.Report, isToday: Boolean) {
    ListPanel(Modifier.padding(top = 8.dp), title = if (isToday) ReportCopy.TODAY_TITLE else ReportCopy.THAT_DAY_TITLE) {
        val t = r.target
        ListRow(
            ReportCopy.TARGET_NAME, MetricColors.load, t?.let(ReportCopy::targetValue),
            t?.let { ReportCopy.targetStatus(it, r.load, r.finished) } ?: ReportCopy.TARGET_WAITING,
            divider = false, icon = Score.Load.rowIcon(),
        )
        if (isToday) {
            val bedtime = ReportCopy.bedtimeValue(r.plan)
            ListRow(
                if (bedtime != null) ReportCopy.BEDTIME_NAME else ReportCopy.SLEEP_NAME, MetricColors.sleepDeep,
                bedtime ?: ReportCopy.clock(r.plan.asleepMinutes.toFloat()), ReportCopy.planStatus(r.plan), icon = Score.Sleep.rowIcon(),
            )
        }
    }
}

/* ------------------------------------------------ Month --------------------------------------------- */

private fun LazyListScope.monthBody(r: Reports.Report, onScrub: (DetailHeadline?) -> Unit) {
    val month = r.month
    val values = month.days.map { it.recovery?.toFloat() }
    val labels = spreadLabels(month.days.map { it.date })
    item {
        if (values.all { it == null }) DetailEmpty(ReportCopy.noScores(Reports.MONTH_DAYS))
        else NormalTrend(
            values, levelsIn(values, month.normal), MetricColors.readiness, labels = labels,
            band = month.normal, bandLabel = month.normal?.let(ReportCopy::bandLabel),
            onScrub = { i -> onScrub(i?.let { recoveryAt(month.days[it]) }) },
        )
    }
    item { DetailSentence(ReportCopy.monthSentence(month.now, month.before)) }
    if (month.now.scored > 0) item { ReportSpread(month) }
    item { ReportNightTrend(ReportCopy.HRV_TITLE, "ms", month.days, month.hrvNormal, MetricColors.hrv, labels, onScrub) { it.hrv } }
    item { ReportNightTrend(ReportCopy.RESTING_HR_TITLE, "bpm", month.days, month.restingHrNormal, MetricColors.heart, labels, onScrub) { it.restingHr } }
    item { ReportComparison(ReportCopy.MONTH_AGAINST, ReportCopy.monthRows(month.now, month.before)) }
    if (month.weekdays.any { it.recovery != null }) item { ReportWeekdays(month.weekdays) }
    if (month.sports.isNotEmpty()) item { ReportSports(month.sports, month = true) }
    item { ReportHabits(r.habits) }
}

/** How the month's mornings spread over Recovery's bands, highest first, each against the month before. */
@Composable
private fun ReportSpread(month: Reports.Month) {
    val scored = month.bands.sum().coerceAtLeast(1)
    ListPanel(Modifier.padding(top = 8.dp), title = ReportCopy.SPREAD_TITLE) {
        month.bands.indices.reversed().forEachIndexed { k, band ->
            ContributorRow(
                ReportCopy.BANDS[band], MetricColors.readiness, ReportCopy.days(month.bands[band]), month.bands[band].toFloat() / scored,
                // The lowest band's days are the ones to notice.
                offNormal = band == 0 && month.bands[0] > 0, divider = k > 0,
                detail = ReportCopy.bandBefore(month.bandsBefore?.getOrNull(band)),
            )
        }
    }
}

/** A month of one nightly value against your normal, in its own panel; scrubbing moves the headline. */
@Composable
private fun ReportNightTrend(
    title: String,
    unit: String,
    days: List<Reports.Day>,
    normal: ClosedFloatingPointRange<Float>?,
    color: Color,
    labels: List<String>,
    onScrub: (DetailHeadline?) -> Unit,
    value: (Reports.Day) -> Float?,
) {
    val values = days.map(value)
    ListPanel(Modifier.padding(top = 8.dp), title = title, note = ReportCopy.nightNote(unit)) {
        if (values.all { it == null }) DetailEmpty(ReportCopy.NO_NIGHTS)
        else NormalTrend(
            values, levelsIn(values, normal), color, Modifier.padding(bottom = 8.dp), height = 170.dp, labels = labels,
            band = normal, bandLabel = normal?.let(ReportCopy::bandLabel),
            onScrub = { i ->
                onScrub(
                    i?.let { idx ->
                        values[idx]?.let { v -> DetailHeadline(Math.round(v).toString(), "$unit, ${days[idx].date.format(DAY)}", TodayCopy.normalWord(directionOf(v, normal))) }
                    },
                )
            },
        )
    }
}

/** Each day of the week's average Recovery over the reports' days, its Load and sleep under it; a low one lit. */
@Composable
private fun ReportWeekdays(weekdays: List<Reports.Weekday>) {
    val scored = weekdays.mapNotNull { it.recovery }
    val mean = scored.average()
    val lowest = weekdays.filter { it.recovery != null }.minByOrNull { it.recovery ?: 0f }
    ListPanel(Modifier.padding(top = 8.dp), title = ReportCopy.WEEKDAYS_TITLE, note = ReportCopy.WEEKDAYS_NOTE) {
        weekdays.forEachIndexed { i, w ->
            val rec = w.recovery
            ContributorRow(
                ReportCopy.weekdayName(w.day), MetricColors.readiness, rec?.let(ReportCopy::whole) ?: ReportCopy.NO_SCORES_WORD, (rec ?: 0f) / 100f,
                offNormal = rec != null && w == lowest && mean - rec >= LOW_WEEKDAY_POINTS, divider = i > 0, detail = ReportCopy.weekdayDetail(w),
            )
        }
    }
}

/** What each habit does to the next morning, the clearest first. */
@Composable
private fun ReportHabits(habits: List<Reports.Effect>) {
    ListPanel(Modifier.padding(top = 8.dp), title = ReportCopy.HABITS_TITLE, note = ReportCopy.HABITS_NOTE) {
        if (habits.all { it.recoveryVerdict == Reports.Verdict.TooFew }) ReportNote(ReportCopy.HABITS_WAITING)
        else habits.forEachIndexed { i, e ->
            ListRow(
                ReportCopy.habitName(e.habit), MetricColors.readiness, ReportCopy.habitValue(e), ReportCopy.habitStatus(e),
                offNormal = ReportCopy.habitOff(e), divider = i > 0, dash = false, icon = habitIcon(e.habit), iconTint = SportIconTint,
            )
        }
    }
}

/* ------------------------------------------- Shared parts ------------------------------------------- */

/** A comparison list: each figure with the stretch before it in its status, lit when it moved the wrong way. */
@Composable
private fun ReportComparison(title: String, rows: List<ReportCopy.Row>) {
    ListPanel(Modifier.padding(top = 8.dp), title = title) {
        rows.forEachIndexed { i, row ->
            ListRow(row.name, inkOf(row.line), row.value, row.status, offNormal = row.off, divider = i > 0, icon = iconOf(row.line))
        }
    }
}

/** Sessions by sport, with the month's heart rate zones under each. */
@Composable
private fun ReportSports(sports: List<Reports.SportTotals>, month: Boolean) {
    ListPanel(Modifier.padding(top = 8.dp), title = ReportCopy.SPORTS_TITLE, note = ReportCopy.ZONES_NOTE.takeIf { month }) {
        sports.forEachIndexed { i, s ->
            ListRow(
                s.name, MetricColors.heart, s.sessions.toString(), ReportCopy.sportStatus(s, month), divider = i > 0,
                icon = SportIcons.of(s.sport), iconTint = SportIconTint,
            )
            if (month && s.zoneSeconds.sum() > 0) ZoneBar(s.zoneSeconds, ZONE_COLORS, Modifier.padding(start = RowIcon + RowIconGap, bottom = 12.dp), height = 4.dp)
        }
    }
}

@Composable
private fun ReportNote(text: String) {
    Text(text, color = Color.White.copy(alpha = GlowType.SECONDARY_ALPHA), style = GlowType.secondary, modifier = Modifier.padding(bottom = 14.dp))
}

private fun recoveryAt(day: Reports.Day): DetailHeadline? =
    day.recovery?.let { DetailHeadline(it.toString(), day.date.format(DAY), TodayCopy.recoveryWord(it, day.alert)) }

private fun loadAt(day: Reports.Day, target: Reports.Target?): DetailHeadline? = day.load?.let { l ->
    DetailHeadline(
        Math.round(l).toString(), "Load, ${day.date.format(DAY)}",
        target?.let { ReportCopy.versusWord(Reports.versus(l, it.range), day.partial) },
    )
}

/** Each value's place against [band]: above or below it lit, inside dim, as the details' trends draw them. */
private fun levelsIn(values: List<Float?>, band: ClosedFloatingPointRange<Float>?): List<DetailCopy.Level?> = values.map { v ->
    when {
        v == null -> null
        band != null && v > band.endInclusive -> DetailCopy.Level.Above
        band != null && v < band.start -> DetailCopy.Level.Below
        else -> DetailCopy.Level.Inside
    }
}

private fun directionOf(v: Float, band: ClosedFloatingPointRange<Float>?): Int? = when {
    band == null -> null
    v > band.endInclusive -> 1
    v < band.start -> -1
    else -> 0
}

private fun inkOf(line: ReportCopy.Line): Color = when (line) {
    ReportCopy.Line.Recovery, ReportCopy.Line.Ready -> MetricColors.readiness
    ReportCopy.Line.Hrv -> MetricColors.hrv
    ReportCopy.Line.RestingHr, ReportCopy.Line.Workouts, ReportCopy.Line.Training -> MetricColors.heart
    ReportCopy.Line.Asleep, ReportCopy.Line.Restorative, ReportCopy.Line.Bedtime, ReportCopy.Line.Wake -> MetricColors.sleepDeep
    ReportCopy.Line.Load, ReportCopy.Line.Usual -> MetricColors.load
    ReportCopy.Line.Steps -> MetricColors.steps
}

private fun iconOf(line: ReportCopy.Line): ImageVector = when (line) {
    ReportCopy.Line.Recovery, ReportCopy.Line.Ready -> Score.Recovery.rowIcon()
    ReportCopy.Line.Hrv -> Metric.Hrv.rowIcon()
    ReportCopy.Line.RestingHr -> Metric.RestingHr.rowIcon()
    ReportCopy.Line.Asleep, ReportCopy.Line.Restorative -> Score.Sleep.rowIcon()
    ReportCopy.Line.Bedtime, ReportCopy.Line.Wake -> Score.Regularity.rowIcon()
    ReportCopy.Line.Load, ReportCopy.Line.Usual -> Score.Load.rowIcon()
    ReportCopy.Line.Workouts -> AppIcons.PersonSimpleRun
    ReportCopy.Line.Training -> AppIcons.Timer
    ReportCopy.Line.Steps -> Metric.Steps.rowIcon()
}

private fun habitIcon(h: Reports.Habit): ImageVector = when (h) {
    Reports.Habit.Workout -> AppIcons.PersonSimpleRun
    is Reports.Habit.Sport -> SportIcons.of(h.sport)
    Reports.Habit.LateWorkout -> AppIcons.Timer
    Reports.Habit.HardDay -> AppIcons.Barbell
    Reports.Habit.LateBedtime -> AppIcons.Moon
    Reports.Habit.ShortNight -> AppIcons.Hourglass
    Reports.Habit.Weekend -> AppIcons.CalendarBlank
    Reports.Habit.ActiveDay -> AppIcons.Footprints
}

/** Four dates spread across a month's chart, as the Load detail labels its month. */
private fun spreadLabels(dates: List<LocalDate>): List<String> = if (dates.isEmpty()) emptyList()
else listOf(0f, 1 / 3f, 2 / 3f, 1f).map { dates[((dates.size - 1) * it).toInt()].format(DateTimeFormatter.ofPattern("d MMM")) }

private fun shortDay(d: LocalDate) = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())

private val DAY = DateTimeFormatter.ofPattern("EEE d MMM")

/** A day of the week this far under the average is lit. */
private const val LOW_WEEKDAY_POINTS = 5.0

private const val TAG = "Reports"
