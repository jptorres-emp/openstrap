package org.openstrap.app.ui.glow

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import org.openstrap.app.R
import org.openstrap.app.data.ForkHabits
import org.openstrap.app.data.ForkPlan
import org.openstrap.app.data.ForkReports
import org.openstrap.app.presentation.HabitWords
import org.openstrap.app.presentation.RecoveryBands
import org.openstrap.app.presentation.ReportRow
import org.openstrap.app.presentation.ReportsCopy
import org.openstrap.app.presentation.ReportsRows
import org.openstrap.app.ui.theme.MetricColors

/*
 * The cards of the fork's Reports page (jptorres-emp/openstrap, [ReportsPage]): each a HaloCard of rows or of figures
 * with its title, the words from ReportsCopy, ReportsRows and HabitWords.
 */

/** A card's rows, as one stable value for Compose. */
@Immutable
internal class ReportRows(val rows: List<ReportRow>)

/** Today: the morning's Recovery and its word, the Load target with the Load so far, and tonight's bedtime. */
@Composable
internal fun TodayCard(plan: ForkPlan.Plan) {
    val recovery = plan.recovery
    val (target, targetLine) = remember(plan) { ReportsCopy.target(plan) }
    val (bedtime, bedtimeLine) = remember(plan) { ReportsCopy.bedtime(plan) }
    val word = when {
        recovery == null -> null
        plan.fromStrap -> stringResource(R.string.fork_rep_from_strap)
        else -> RecoveryBands.word(recovery, plan.alert)
    }
    RowsCard(
        stringResource(R.string.fork_rep_today),
        ReportRows(
            listOf(
                ReportRow(stringResource(R.string.fork_rep_recovery), recovery?.toString(), word),
                ReportRow(stringResource(R.string.fork_rep_target), target, targetLine),
                ReportRow(stringResource(R.string.fork_rep_bedtime), bedtime, bedtimeLine),
            ),
        ),
    )
}

/** The stretch's days within, below and above their own Load target, and the stretch before's under them. */
@Composable
internal fun TargetCard(stretch: ForkReports.Stretch, month: Boolean) {
    val now = stretch.now
    HaloCard(title = { HaloTitle(stringResource(R.string.fork_rep_vs_target)) }) {
        HaloFigures(
            listOf(
                ReportsCopy.amount(now.within) to stringResource(R.string.fork_rep_within),
                ReportsCopy.amount(now.below) to stringResource(R.string.fork_rep_below),
                ReportsCopy.amount(now.above) to stringResource(R.string.fork_rep_above),
            ),
        )
        stretch.before?.takeIf { it.within + it.below + it.above > 0f }?.let { before ->
            HaloNote(remember(before, month) { ReportsCopy.versusBefore(before, month) })
        }
    }
}

/** The stretch against the one before it, row by row. */
@Composable
internal fun CompareCard(stretch: ForkReports.Stretch, month: Boolean) {
    val rows = remember(stretch, month) { ReportRows(ReportsRows.compare(stretch, month)) }
    RowsCard(stringResource(if (month) R.string.fork_rep_compare_month else R.string.fork_rep_compare_week), rows)
}

/** The month's mornings by Recovery: ready or more, moderate, and to take it easy. */
@Composable
internal fun MorningsCard(now: ForkReports.Figures) {
    HaloCard(title = { HaloTitle(stringResource(R.string.fork_rep_days)) }) {
        HaloFigures(
            listOf(
                ReportsCopy.amount(now.ready) to stringResource(R.string.fork_rep_days_ready),
                ReportsCopy.amount(now.moderate) to stringResource(R.string.fork_rep_days_moderate),
                ReportsCopy.amount(now.low) to stringResource(R.string.fork_rep_days_easy),
            ),
        )
    }
}

/** Recovery by the day of the week over the last 60 days, and which day is best and worst. */
@Composable
internal fun WeekdaysCard(report: ForkReports.Report) {
    val weekdays = report.weekdays
    val labels = remember { ReportsCopy.weekdayLabels() }
    val sentence = remember(weekdays) { ReportsCopy.weekdays(weekdays) }
    HaloCard(
        title = { HaloTitle(stringResource(R.string.fork_rep_weekdays), note = stringResource(R.string.fork_rep_last_60)) },
    ) {
        if (weekdays.all { it == null }) {
            DetailEmpty(stringResource(R.string.fork_rep_hero_none))
        } else {
            DetailColumns(weekdays, MetricColors.readiness, labels, highlight = null, fixed = 0f..SCORE_TOP, valueLabels = true)
        }
        sentence?.let { HaloNote(it) }
    }
}

/** The stretch's sports: each one's sessions, their time and the sessions before. */
@Composable
internal fun SportsCard(stretch: ForkReports.Stretch) {
    val rows = remember(stretch) { ReportRows(ReportsRows.sports(stretch.sports)) }
    RowsCard(stringResource(R.string.fork_rep_sports), rows)
}

/**
 * What moves your Recovery: each habit with a likely or clear effect on the next morning's HRV, its change and how sure
 * it is, then yesterday's journal, with a dot while it's to fill in.
 */
@Composable
internal fun EffectsCard(report: ForkReports.Report, onJournal: () -> Unit) {
    val shown = remember(report) { report.effects.filter { it.verdict >= ForkHabits.Verdict.Likely } }
    HaloCard(
        fill = CardFill.Rows,
        title = { HaloTitle(stringResource(R.string.fork_rep_effects), note = stringResource(R.string.fork_rep_on_hrv)) },
    ) {
        shown.forEachIndexed { i, e ->
            val line = remember(e) { HabitWords.line(e) }
            HaloRow(HabitWords.name(e.habit), value = HabitWords.change(e), line = { RowLine(line) }, divider = i > 0)
        }
        NavRow(
            stringResource(R.string.fork_journal_title),
            stringResource(if (report.yesterday == null) R.string.fork_journal_line_open else R.string.fork_journal_line_done),
            onClick = onJournal, below = true, divider = shown.isNotEmpty(), dot = report.yesterday == null,
        )
    }
}

/** Under the habits: those with no clear effect, those with too few mornings yet, or that it's still too soon. */
internal fun LazyListScope.effectNotes(report: ForkReports.Report) {
    val none = HabitWords.none(report.effects)
    val few = HabitWords.few(report.effects)
    val notes = listOfNotNull(none, few).ifEmpty {
        if (report.effects.none { it.verdict >= ForkHabits.Verdict.Likely }) listOf(HabitWords.empty()) else emptyList()
    }
    notes.forEachIndexed { i, text -> item(key = "effectNote$i") { HaloNote(text, variant = NoteVariant.Footnote) } }
}

/** A card of [rows] under its [title], each row's value on the right and its line under its name. */
@Composable
private fun RowsCard(title: String, rows: ReportRows) {
    HaloCard(fill = CardFill.Rows, title = { HaloTitle(title) }) {
        rows.rows.forEachIndexed { i, r ->
            HaloRow(r.title, value = r.value, line = r.line?.let { l -> { RowLine(l) } }, divider = i > 0)
        }
    }
}

private const val SCORE_TOP = 100f
