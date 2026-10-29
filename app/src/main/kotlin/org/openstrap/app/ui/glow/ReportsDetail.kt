package org.openstrap.app.ui.glow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.openstrap.app.data.Recovery
import org.openstrap.app.data.Reports
import org.openstrap.app.ui.dashboard.Detail
import org.openstrap.app.ui.dashboard.DetailCopy
import org.openstrap.app.ui.dashboard.Info
import org.openstrap.app.ui.dashboard.Metric
import org.openstrap.app.ui.dashboard.Range
import org.openstrap.app.ui.dashboard.ReportCopy
import org.openstrap.app.ui.dashboard.Score
import org.openstrap.app.ui.dashboard.TabCopy
import org.openstrap.app.ui.dashboard.TodayCopy
import org.openstrap.app.ui.theme.AppIcons
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.app.ui.workout.SportIcons
import org.openstrap.app.ui.workout.ZONE_COLORS
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** How the report stands: being worked out, ready, or failed. */
private sealed interface ReportState {
    data object Loading : ReportState
    data class Ready(val report: Reports.Report) : ReportState
    data object Failed : ReportState
}

/**
 * Reports, opened from the header's chart button: the week or the month ending on the day shown, against the stretch
 * before it, as WHOOP's weekly and monthly assessments compare them. Recovery and effort lead: Recovery's average in
 * dots with the Load a day beside it, Recovery day by day against your normal, then each day's Load against the target
 * its morning set. Week: the day itself (its Recovery, Load target and tonight's bedtime) and the week against the 3
 * before. Month: the mornings in Recovery's bands, the month against the one before, the days of the week, your sports
 * with their zones, and what your habits do to the next morning. Rows open the details behind them. A list right after
 * another sits on a neutral edge card (spec 2.5). [load] works the report out off the main thread. No strip: the
 * report follows the day shown, as the week story does.
 */
@Composable
internal fun ReportsDetail(
    date: LocalDate,
    load: suspend (LocalDate) -> Reports.Report,
    contentPadding: PaddingValues,
    onOpen: (Detail) -> Unit,
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
    val figures = report?.let { if (range == Range.Week) it.week.now else it.month.now }
    val average = figures?.recovery
    val headline = scrub ?: when {
        report == null && state == ReportState.Failed -> DetailHeadline.none(ReportCopy.FAILED_WORD, ReportCopy.FAILED_WHY)
        report == null -> DetailHeadline.none(ReportCopy.LOADING_WORD, ReportCopy.averageLabel(days).replaceFirstChar { it.uppercase() })
        average != null -> DetailHeadline(ReportCopy.whole(average), ReportCopy.averageLabel(days), TodayCopy.recoveryWord(Math.round(average), false))
        else -> ReportCopy.noAverage(report.daysWithData).let { (word, why) -> DetailHeadline.none(word, why) }
    }
    // Effort beside Recovery: the Load a day over the same days, as text (the header's number is the one in dots).
    val loadADay = figures?.load
    val side: (@Composable () -> Unit)? = if (loadADay != null && scrub == null) { { HeroLoad(loadADay) } } else null
    DetailTemplate(
        ReportCopy.TITLE, color, emptyList(), onInfo = { info = true }, onBack = onBack, contentPadding = contentPadding,
        headline = headline, strip = emptyList(), onDay = {}, ranges = ranges, range = range, onRange = { rangeIndex = ranges.indexOf(it) },
        heroSide = side,
    ) {
        when {
            report == null && state == ReportState.Failed -> item { DetailEmpty(ReportCopy.FAILED) }
            report == null -> item { DetailEmpty(ReportCopy.LOADING) }
            range == Range.Week -> weekBody(report, isToday, onOpen) { scrub = it }
            else -> monthBody(report, onOpen) { scrub = it }
        }
    }
    if (info) InfoSheet(
        Info(ReportCopy.TITLE, ReportCopy.INFO_MEANING, emptyList(), how = "", moves = "", off = ""), color, null, null,
        onDismiss = { info = false }, rows = ReportCopy.INFO_ROWS,
    )
}

/** The Load a day beside the header's Recovery: the figure in Body, its name under it after Load's colour. */
@Composable
private fun HeroLoad(load: Float) {
    Column(Modifier.padding(start = 12.dp), horizontalAlignment = Alignment.End) {
        Text(ReportCopy.whole(load), color = Color.White, style = GlowType.body.copy(fontFeatureSettings = "tnum"), maxLines = 1)
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(MetricColors.load, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(ReportCopy.LOAD_A_DAY, color = Color.White.copy(alpha = GlowType.SECONDARY_ALPHA), style = GlowType.secondary, maxLines = 1)
        }
    }
}

/* ------------------------------------------------ Week ---------------------------------------------- */

private fun LazyListScope.weekBody(r: Reports.Report, isToday: Boolean, onOpen: (Detail) -> Unit, onScrub: (DetailHeadline?) -> Unit) {
    val week = r.week
    val labels = week.days.map { ReportCopy.shortDay(it.date.dayOfWeek) }
    val loads = hasLoads(week.days)
    item { RecoveryTrend(week.days, week.normal, labels, normalDays(r), onScrub) }
    item { DetailSentence(ReportCopy.weekSentence(week.now, week.before)) }
    item { ReportList(if (isToday) ReportCopy.TODAY_TITLE else ReportCopy.day(r.date), null, ReportCopy.dayRows(r, isToday), onOpen) }
    if (loads) item { ReportLoad(week.days, week.targets, week.balance, week.balanceBefore, week.now.trend, labels, month = false, onOpen) }
    // Without the Load card between them, the week's list sits right under the day's: on a neutral edge card.
    item { ReportList(ReportCopy.WEEK_TITLE, ReportCopy.WEEK_NOTE, ReportCopy.weekRows(week.now, week.before), onOpen, onEdge = !loads) }
}

/* ------------------------------------------------ Month --------------------------------------------- */

private fun LazyListScope.monthBody(r: Reports.Report, onOpen: (Detail) -> Unit, onScrub: (DetailHeadline?) -> Unit) {
    val month = r.month
    val labels = spreadLabels(month.days.map { it.date })
    val weekdays = month.weekdays.count { it.recovery != null } >= 3
    val sports = month.sports.isNotEmpty()
    item {
        Column {
            RecoveryTrend(month.days, month.normal, labels, normalDays(r), onScrub)
            if (month.now.scored > 0) DetailFigures(ReportCopy.spreadFigures(month.bands), Modifier.padding(top = 16.dp))
        }
    }
    item { DetailSentence(ReportCopy.monthSentence(month.now, month.before)) }
    if (hasLoads(month.days)) item {
        ReportLoad(month.days, month.targets, month.balance, month.balanceBefore, month.now.trend, labels, month = true, onOpen)
    }
    item { ReportList(ReportCopy.MONTH_TITLE, ReportCopy.MONTH_NOTE, ReportCopy.monthRows(month.now, month.before), onOpen) }
    if (weekdays) item { ReportWeekdays(month.weekdays) }
    // A list right after a list sits on a neutral edge card: the sports when nothing comes between them and the
    // month's list, else the habits when they follow the sports.
    val sportsOnEdge = sports && !weekdays
    if (sports) item { ReportSports(month.sports, onEdge = sportsOnEdge) }
    item { ReportHabits(r.habits, onEdge = (sports && !sportsOnEdge) || (!sports && !weekdays)) }
}

/** Each day of the week's average Recovery with its value over it, a low one lit; lit from the left, the weeks behind. */
@Composable
private fun ReportWeekdays(weekdays: List<Reports.Weekday>) {
    val low = ReportCopy.lowWeekday(weekdays)
    EdgeCard(MetricColors.readiness, Modifier.fillMaxWidth().padding(top = 8.dp), kind = EdgeKind.Earlier) {
        EdgeTitle(ReportCopy.WEEKDAYS_TITLE, ReportCopy.WEEKDAYS_NOTE)
        GlowBars(
            weekdays.map { it.recovery ?: 0f },
            Modifier.padding(top = 16.dp).semantics { contentDescription = ReportCopy.describeWeekdays(weekdays) },
            height = 150.dp, labels = weekdays.map { ReportCopy.shortDay(it.day) },
            highlight = low?.let { weekdays.indexOf(it) } ?: -1, accent = MetricColors.readiness,
            valueLabel = { i -> weekdays[i].recovery?.let(ReportCopy::whole) },
        )
        ReportCopy.weekdayLine(weekdays)?.let { Text(it, Modifier.padding(top = 14.dp), color = Color.White, style = GlowType.body) }
    }
}

/** Sessions by sport, each one's heart rate zones under it, as Activity's workouts have theirs. */
@Composable
private fun ReportSports(sports: List<Reports.SportTotals>, onEdge: Boolean) {
    OnNeutralEdge(onEdge) {
        ListPanel(Modifier.padding(top = 8.dp), title = ReportCopy.SPORTS_TITLE, note = ReportCopy.ZONES_NOTE) {
            sports.forEachIndexed { i, s ->
                ListRow(
                    s.name, MetricColors.heart, s.sessions.toString(), ReportCopy.sportStatus(s), divider = i > 0,
                    icon = SportIcons.of(s.sport), iconTint = SportIconTint,
                )
                // Lined up with the name, as under a workout's row.
                ZoneBar(s.zoneSeconds, ZONE_COLORS, Modifier.padding(start = RowIcon + RowIconGap, bottom = 12.dp), height = 4.dp)
            }
        }
    }
}

/**
 * What the habits do to the next morning: each one whose effect shows on its own row, the clearest first, then the
 * rest a row each for what they have in common, so the ones that matter stand out.
 */
@Composable
private fun ReportHabits(habits: List<Reports.Effect>, onEdge: Boolean) {
    OnNeutralEdge(onEdge) {
        ListPanel(Modifier.padding(top = 8.dp), title = ReportCopy.HABITS_TITLE, note = ReportCopy.HABITS_NOTE) {
            if (habits.all { it.recoveryVerdict == Reports.Verdict.TooFew }) {
                ReportNote(ReportCopy.HABITS_WAITING, Modifier.padding(bottom = 14.dp))
            } else {
                val shown = ReportCopy.shownHabits(habits)
                shown.forEachIndexed { i, e ->
                    ListRow(
                        ReportCopy.habitName(e.habit), MetricColors.readiness, ReportCopy.habitValue(e), ReportCopy.habitStatus(e),
                        offNormal = ReportCopy.habitOff(e), divider = i > 0, icon = habitIcon(e.habit), iconTint = SportIconTint,
                    )
                }
                ReportCopy.quietHabits(habits).forEachIndexed { i, (title, names) ->
                    ListRow(title, MetricColors.readiness, null, names, divider = shown.isNotEmpty() || i > 0, dash = false, icon = AppIcons.DotsThree, iconTint = SportIconTint)
                }
            }
        }
    }
}

/* ------------------------------------------- Shared parts ------------------------------------------- */

/**
 * Recovery day by day against your normal (spec 2.7): days inside it dim, outside it lit, the band labelled; before
 * there's a normal, how far it has got over [normalDays]. Holding moves the headline, just above it.
 */
@Composable
private fun RecoveryTrend(
    days: List<Reports.Day>,
    normal: ClosedFloatingPointRange<Float>?,
    labels: List<String>,
    normalDays: Int,
    onScrub: (DetailHeadline?) -> Unit,
) {
    val values = days.map { it.recovery?.toFloat() }
    if (values.all { it == null }) return DetailEmpty(ReportCopy.noScores(days.size))
    Column {
        NormalTrend(
            values, levelsIn(values, normal), MetricColors.readiness,
            Modifier.semantics { contentDescription = ReportCopy.describeRecovery(days) },
            height = 210.dp, labels = labels, band = normal, bandLabel = normal?.let(ReportCopy::bandLabel),
            onScrub = { i -> onScrub(i?.let { recoveryAt(days[it]) }) },
        )
        if (normal == null) ReportNote(DetailCopy.learningNormal(normalDays, nightly = false), Modifier.padding(top = 6.dp))
    }
}

/**
 * Each day's Load against the target its morning set, on an edge card in Load's colour: lit from the top when the
 * days went over their targets more than under them, as a value above your normal, else from below. The figures over
 * the chart say how the days landed and follow the finger while it holds a day, so they stay in sight above it; a week's
 * bars carry their Load. The training balance (Building, Steady, Easing off) is the title's note, as on Activity's
 * Load card. Opens the Load detail.
 */
@Composable
private fun ReportLoad(
    days: List<Reports.Day>,
    targets: List<Reports.Target?>,
    balance: Reports.Balance,
    before: Reports.Balance?,
    trend: Recovery.LoadTrend?,
    labels: List<String>,
    month: Boolean,
    onOpen: (Detail) -> Unit,
) {
    var held by remember(days) { mutableStateOf<Int?>(null) }
    EdgeCard(
        MetricColors.load, Modifier.fillMaxWidth().padding(top = 8.dp),
        status = if (ReportCopy.overTarget(balance)) EdgeStatus.Above else EdgeStatus.Usual,
        onClick = { onOpen(Detail.OfScore(Score.Load)) },
    ) {
        EdgeTitle(ReportCopy.TARGET_TITLE, TabCopy.loadBalance(trend))
        DetailFigures(
            held?.let { ReportCopy.dayFigures(days[it], targets.getOrNull(it)) } ?: ReportCopy.balanceFigures(balance),
            Modifier.padding(top = 12.dp),
        )
        TargetBars(
            days.map { it.load }, targets.map { it?.range }, MetricColors.load,
            Modifier.padding(top = 16.dp).semantics { contentDescription = ReportCopy.describeTargets(days, targets) },
            height = if (month) 160.dp else 190.dp, labels = labels,
            partial = days.indexOfFirst { it.partial }.takeIf { it >= 0 }, values = !month, bandLabel = ReportCopy.TARGET_BAND,
            onScrub = { held = it },
        )
        ReportCopy.balanceBefore(before, month)?.let { ReportNote(it, Modifier.padding(top = 12.dp)) }
    }
}

/** A list of [rows], each in its metric's ink with its icon, opening the detail behind it where there is one. */
@Composable
private fun ReportList(title: String, note: String?, rows: List<ReportCopy.Row>, onOpen: (Detail) -> Unit, onEdge: Boolean = false) {
    OnNeutralEdge(onEdge) {
        ListPanel(Modifier.padding(top = 8.dp), title = title, note = note) {
            rows.forEachIndexed { i, row ->
                val opens = openOf(row.line)
                ListRow(
                    row.name, inkOf(row.line), row.value, row.status, offNormal = row.off, divider = i > 0,
                    onClick = opens?.let { d -> { onOpen(d) } }, icon = iconOf(row.line),
                )
            }
        }
    }
}

/** A list on a neutral edge card lit from the top, when [on]: one right after another list (spec 2.5). */
@Composable
private fun OnNeutralEdge(on: Boolean, content: @Composable () -> Unit) {
    if (on) CompositionLocalProvider(LocalEdgeOverride provides EdgeOverride(edge = Edge.Top), content = content) else content()
}

@Composable
private fun ReportNote(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, color = Color.White.copy(alpha = GlowType.SECONDARY_ALPHA), style = GlowType.secondary)
}

/** Whether any of [days] has a Load to draw. */
private fun hasLoads(days: List<Reports.Day>) = days.any { it.load != null }

/** The mornings your normal is worked out over (the 30 days to the day shown) that have a score. */
private fun normalDays(r: Reports.Report) = r.month.days.count { it.recovery != null }

private fun recoveryAt(day: Reports.Day): DetailHeadline? =
    day.recovery?.let { DetailHeadline(it.toString(), ReportCopy.day(day.date), TodayCopy.recoveryWord(it, day.alert)) }

/** Each value's place against [band]: above or below it lit, inside dim, as the details' trends draw them. */
private fun levelsIn(values: List<Float?>, band: ClosedFloatingPointRange<Float>?): List<DetailCopy.Level?> = values.map { v ->
    when {
        v == null -> null
        band != null && v > band.endInclusive -> DetailCopy.Level.Above
        band != null && v < band.start -> DetailCopy.Level.Below
        else -> DetailCopy.Level.Inside
    }
}

/** The detail a row opens: the one that shows the same figure day by day, where there is one. */
private fun openOf(line: ReportCopy.Line): Detail? = when (line) {
    ReportCopy.Line.Recovery -> Detail.OfScore(Score.Recovery)
    ReportCopy.Line.Target, ReportCopy.Line.Load, ReportCopy.Line.Usual -> Detail.OfScore(Score.Load)
    ReportCopy.Line.Tonight, ReportCopy.Line.Bedtime, ReportCopy.Line.Wake -> Detail.OfScore(Score.Regularity)
    ReportCopy.Line.Hrv -> Detail.OfMetric(Metric.Hrv, night = true)
    ReportCopy.Line.RestingHr -> Detail.OfMetric(Metric.RestingHr)
    ReportCopy.Line.Asleep, ReportCopy.Line.Restorative -> Detail.OfScore(Score.Sleep)
    ReportCopy.Line.Workouts -> Detail.AllWorkouts
    ReportCopy.Line.Steps -> Detail.OfMetric(Metric.Steps)
}

private fun inkOf(line: ReportCopy.Line): Color = when (line) {
    ReportCopy.Line.Recovery -> MetricColors.readiness
    ReportCopy.Line.Hrv -> MetricColors.hrv
    ReportCopy.Line.RestingHr, ReportCopy.Line.Workouts -> MetricColors.heart
    ReportCopy.Line.Tonight, ReportCopy.Line.Asleep, ReportCopy.Line.Restorative, ReportCopy.Line.Bedtime, ReportCopy.Line.Wake -> MetricColors.sleepDeep
    ReportCopy.Line.Target, ReportCopy.Line.Load, ReportCopy.Line.Usual -> MetricColors.load
    ReportCopy.Line.Steps -> MetricColors.steps
}

private fun iconOf(line: ReportCopy.Line): ImageVector = when (line) {
    ReportCopy.Line.Recovery -> Score.Recovery.rowIcon()
    ReportCopy.Line.Hrv -> Metric.Hrv.rowIcon()
    ReportCopy.Line.RestingHr -> Metric.RestingHr.rowIcon()
    ReportCopy.Line.Tonight, ReportCopy.Line.Asleep, ReportCopy.Line.Restorative -> Score.Sleep.rowIcon()
    ReportCopy.Line.Bedtime, ReportCopy.Line.Wake -> Score.Regularity.rowIcon()
    ReportCopy.Line.Target, ReportCopy.Line.Load, ReportCopy.Line.Usual -> Score.Load.rowIcon()
    ReportCopy.Line.Workouts -> AppIcons.PersonSimpleRun
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

private const val TAG = "Reports"
