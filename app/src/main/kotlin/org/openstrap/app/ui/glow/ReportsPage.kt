package org.openstrap.app.ui.glow

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import org.openstrap.app.R
import org.openstrap.app.data.ForkReports
import org.openstrap.app.presentation.DayWords
import org.openstrap.app.presentation.RecoveryBands
import org.openstrap.app.presentation.ReportsCopy
import org.openstrap.app.presentation.TrainingWords
import org.openstrap.app.presentation.Units
import org.openstrap.app.ui.theme.AppIcons
import org.openstrap.app.ui.theme.Ink
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.app.ui.theme.Space
import kotlin.math.roundToInt

/**
 * The fork's Reports page (jptorres-emp/openstrap), opened from the You page, as WHOOP's weekly and monthly
 * assessments read: the week or the month ([GlowSegments]) in its average Recovery and Load a day, Recovery day by day
 * against your normal, and the sentence on both against the stretch before; then the days' Load against their targets,
 * the stretch against the one before row by row, your mornings, the days of the week and the sports (the month), and
 * what moves your Recovery, with yesterday's journal. The third segment is the old fork's Training tab: today's Load
 * target in your sports, the Recovery behind it and tonight's bedtime, your sports, the last week's Load against your
 * usual and the calories burned a day. [report] is null while it's worked out. [onJournal] opens yesterday's journal.
 */
@Composable
internal fun ReportsPage(report: ForkReports.Report?, contentPadding: PaddingValues, onJournal: () -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var view by rememberSaveable { mutableIntStateOf(WEEK) }
    var about by rememberSaveable { mutableStateOf(false) }
    val title = stringResource(R.string.fork_reports_title)
    val views = listOf(
        stringResource(R.string.fork_rep_week), stringResource(R.string.fork_rep_month), stringResource(R.string.fork_rep_training),
    )
    HaloPageList(contentPadding, variant = PageVariant.Detail) {
        item {
            HaloPageHeader(title, onBack) {
                HaloIconButton(AppIcons.Info, stringResource(R.string.fork_reports_about), { about = true })
            }
        }
        if (report == null) {
            item { DetailEmpty(stringResource(R.string.fork_reports_loading)) }
        } else {
            item { GlowSegments(views, view, { view = it }, role = Role.Tab, size = SegmentSize.Compact) }
            if (view == TRAINING) trainingItems(report) else reportItems(report, view == MONTH, onJournal)
        }
    }
    if (about) ReportsAbout { about = false }
}

/** The page's blocks for the week or the [month], in order. */
private fun LazyListScope.reportItems(report: ForkReports.Report, month: Boolean, onJournal: () -> Unit) {
    val stretch = if (month) report.month else report.week
    item(key = "hero") { ReportsHero(stretch, month) }
    item(key = "chart") { RecoveryChart(stretch, report.normal, month) }
    ReportsCopy.sentence(stretch, month)?.let { item(key = "sentence") { DetailSentence(it) } }
    if (month) item(key = "mornings") { MorningsCard(stretch.now) }
    item(key = "target") { TargetCard(stretch, month) }
    item(key = "compare") { CompareCard(stretch, month) }
    if (month) {
        item(key = "weekdays") { WeekdaysCard(report) }
        if (stretch.sports.isNotEmpty()) item(key = "sports") { SportsCard(stretch) }
    }
    item(key = "effects") { EffectsCard(report, onJournal) }
    effectNotes(report)
}

/**
 * The Training view's blocks, in order: today's Load target and what fits it in your sports, the Recovery behind it and
 * tonight's bedtime, your sports one by one, the last week's Load against your usual, and the calories burned a day.
 */
private fun LazyListScope.trainingItems(report: ForkReports.Report) {
    val plan = report.plan
    val training = report.training
    item(key = "trainingHero") { TrainingHero(plan) }
    plan.target?.let { t -> item(key = "plan") { DetailSentence(TrainingWords.plan(t, training.sports)) } }
    item(key = "today") { TodayCard(plan) }
    if (training.sports.isNotEmpty()) item(key = "fit") { SportsFitCard(training, plan.target) }
    item(key = "risk") { BalanceCard(training) }
    item(key = "kcal") { CaloriesCard(training) }
}

/**
 * The stretch's average Recovery, the page's one dotted number, with its word and what it averages, and the Load a day
 * beside it.
 */
@Composable
private fun ReportsHero(stretch: ForkReports.Stretch, month: Boolean) {
    val recovery = stretch.now.recovery?.roundToInt()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.S4)) {
            if (recovery != null) {
                HaloBigNumber(recovery.toString(), BigNumber.Detail, color = MetricColors.readiness)
                Text(RecoveryBands.word(recovery), color = MetricColors.readiness, style = GlowType.verdict)
            } else {
                Text(stringResource(R.string.fork_rep_hero_none), color = Ink.Primary, style = GlowType.body)
            }
            Text(
                stringResource(if (month) R.string.fork_rep_hero_month else R.string.fork_rep_hero_week),
                color = GlowType.noteColor, style = GlowType.secondary,
            )
        }
        stretch.now.load?.let { load ->
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Space.S4)) {
                Text(Units.count(load.roundToInt()), color = Ink.Primary, style = GlowType.figure)
                Text(stringResource(R.string.fork_rep_hero_load), color = GlowType.noteColor, style = GlowType.secondary)
            }
        }
    }
}

/** Recovery day by day over the stretch, against your [normal] as a band, the days named under it. */
@Composable
private fun RecoveryChart(stretch: ForkReports.Stretch, normal: IntRange?, month: Boolean) {
    val values = remember(stretch) { stretch.days.map { it.recovery?.toFloat() } }
    val dates = stretch.days.map { it.date }
    val labels = when {
        dates.isEmpty() -> emptyList()
        !month && dates.size == ForkReports.WEEK_DAYS -> dates.map { DayWords.weekdayShort(it) }
        else -> MONTH_LABEL_AT.map { f -> DayWords.dayMonth(dates[((dates.size - 1) * f).roundToInt()]) }.distinct()
    }
    HaloCard(
        title = {
            HaloTitle(
                stringResource(R.string.fork_rep_recovery),
                note = normal?.let { stringResource(R.string.fork_rep_normal, it.first, it.last) },
            )
        },
    ) {
        if (values.all { it == null }) {
            DetailEmpty(stringResource(R.string.fork_rep_hero_none))
        } else {
            DetailLineChart(
                values, MetricColors.readiness, labels, yRange = 0f..SCORE_MAX,
                band = normal?.let { it.first.toFloat()..it.last.toFloat() },
            )
        }
    }
}

/** The (i): how each part of the page is worked out. */
@Composable
private fun ReportsAbout(onDismiss: () -> Unit) {
    HaloSheet(
        onDismiss, stringResource(R.string.fork_rep_info_title), variant = HaloSheetVariant.Info, accent = MetricColors.readiness,
        footer = { HaloButton(stringResource(R.string.glow_close), onDismiss, Modifier.fillMaxWidth()) },
    ) {
        SheetSection(stringResource(R.string.fork_rep_info_stretch_title), stringResource(R.string.fork_rep_info_stretch))
        SheetSection(stringResource(R.string.fork_rep_target), stringResource(R.string.fork_rep_info_target))
        SheetSection(stringResource(R.string.fork_rep_fit_title), stringResource(R.string.fork_rep_info_sports))
        SheetSection(stringResource(R.string.fork_rep_risk_title), remember { TrainingWords.withPercent(R.string.fork_rep_info_risk) })
        SheetSection(stringResource(R.string.fork_rep_kcal_title), remember { TrainingWords.withPercent(R.string.fork_rep_info_kcal) })
        SheetSection(stringResource(R.string.fork_rep_bedtime), stringResource(R.string.fork_rep_info_bedtime))
        SheetSection(stringResource(R.string.fork_rep_effects), stringResource(R.string.fork_rep_info_habits))
    }
}

/** The segments: the week, the month and the Training view. */
private const val WEEK = 0
private const val MONTH = 1
private const val TRAINING = 2

/** Where the month's chart names its days: the first, two between and the last. */
private val MONTH_LABEL_AT = listOf(0f, 1 / 3f, 2 / 3f, 1f)

private const val SCORE_MAX = 100f
