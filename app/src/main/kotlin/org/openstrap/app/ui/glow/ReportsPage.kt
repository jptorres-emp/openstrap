package org.openstrap.app.ui.glow

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.openstrap.app.R
import org.openstrap.app.data.ForkHabits
import org.openstrap.app.data.ForkReports
import org.openstrap.app.data.ForkTarget
import org.openstrap.app.presentation.Units
import org.openstrap.app.ui.dashboard.ScreenList
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The fork's Reports page (jptorres-emp/openstrap), opened from the You page: today's ideal effort, the week and the
 * month against the stretch before them, and what the journal's habits do to the next morning. [report] is null while
 * it's worked out. [onJournal] opens yesterday's journal.
 */
@Composable
internal fun ReportsPage(report: ForkReports.Report?, contentPadding: PaddingValues, onJournal: () -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    ScreenList(contentPadding, gutter = GlowLayout.Gutter) {
        item { PageHeader(stringResource(R.string.fork_reports_title), onBack) }
        if (report == null) {
            item { PageNote(stringResource(R.string.fork_reports_loading)) }
        } else {
            item { TargetPanel(report) }
            item { ComparePanel(stringResource(R.string.fork_week_title), stringResource(R.string.fork_week_note), report.week) }
            item { ComparePanel(stringResource(R.string.fork_month_title), stringResource(R.string.fork_month_note), report.month) }
            item { HabitsPanel(report, onJournal) }
        }
    }
}

/** Today's ideal effort: the Load to aim for, the kind of day, where the Load is so far and what the range comes from. */
@Composable
private fun TargetPanel(report: ForkReports.Report) {
    PagePanel(title = stringResource(R.string.fork_target_title)) {
        val target = report.target
        if (target == null) {
            Text(stringResource(R.string.fork_target_none), color = Color.White, style = GlowType.body)
        } else {
            val range = stringResource(R.string.fork_target_range, target.range.first, target.range.last)
            Text(range, color = Color.White, style = GlowType.sentence)
            Text(stringResource(kindWord(target.kind)), color = Color.White, style = GlowType.body)
            val load = report.todayLoad.roundToInt()
            val soFar = when (ForkTarget.versus(report.todayLoad, target.range)) {
                ForkTarget.Versus.Below -> R.string.fork_target_so_far_below
                ForkTarget.Versus.Within -> R.string.fork_target_so_far_within
                ForkTarget.Versus.Above -> R.string.fork_target_so_far_above
            }
            Quiet(stringResource(soFar, load))
            val from = if (target.personal) {
                stringResource(R.string.fork_target_personal)
            } else {
                stringResource(R.string.fork_target_usual, (report.usualLoad ?: 0f).roundToInt())
            }
            Quiet(from)
            if (target.fromStrap) Quiet(stringResource(R.string.fork_target_strap))
        }
    }
}

@Composable
private fun Quiet(text: String) = Text(text, color = Color.White.copy(alpha = GlowType.SECONDARY_ALPHA), style = GlowType.secondary)

private fun kindWord(kind: ForkTarget.DayKind): Int = when (kind) {
    ForkTarget.DayKind.Recover -> R.string.fork_target_kind_recover
    ForkTarget.DayKind.Moderate -> R.string.fork_target_kind_moderate
    ForkTarget.DayKind.Usual -> R.string.fork_target_kind_usual
    ForkTarget.DayKind.Push -> R.string.fork_target_kind_push
}

/** A stretch's figures, each with the stretch before it. */
@Composable
private fun ComparePanel(title: String, note: String, compare: ForkReports.Compare) {
    val now = compare.now
    val before = compare.before
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(title, Modifier.padding(top = 4.dp), PanelLabelInset)
        PageRows {
            CompareRow(R.string.fork_metric_recovery, whole(now.recovery), whole(before?.recovery), divider = false)
            CompareRow(R.string.fork_metric_ready, count(now.ready), before?.let { count(it.ready) })
            CompareRow(R.string.fork_metric_low, count(now.low), before?.let { count(it.low) })
            CompareRow(R.string.fork_metric_hrv, unit(now.hrv, R.string.core_unit_ms), unit(before?.hrv, R.string.core_unit_ms))
            val bpm = R.string.core_unit_bpm
            CompareRow(R.string.fork_metric_resting_hr, unit(now.restingHr, bpm), unit(before?.restingHr, bpm))
            CompareRow(R.string.fork_metric_asleep, asleep(now.asleep), asleep(before?.asleep))
            CompareRow(R.string.fork_metric_load, whole(now.load), whole(before?.load))
            CompareRow(R.string.fork_metric_workouts, count(now.workouts), before?.let { count(it.workouts) })
            CompareRow(R.string.fork_metric_steps, grouped(now.steps), grouped(before?.steps))
        }
        PageNote(note)
    }
}

@Composable
private fun CompareRow(name: Int, now: String?, before: String?, divider: Boolean = true) {
    val shown = now ?: stringResource(R.string.fork_value_none)
    val value = if (before == null) shown else stringResource(R.string.fork_value_vs, shown, before)
    PageRow(stringResource(name), value, below = true, divider = divider)
}

private fun whole(v: Float?): String? = v?.let { "%d".format(Locale.getDefault(), it.roundToInt()) }

private fun grouped(v: Float?): String? = v?.let { "%,d".format(Locale.getDefault(), it.roundToInt()) }

/** A count a stretch, or a week of it: whole when it is, else to one decimal. */
private fun count(v: Float): String =
    if (abs(v - v.roundToInt()) < HALF_TENTH) "%d".format(Locale.getDefault(), v.roundToInt()) else "%.1f".format(Locale.getDefault(), v)

@Composable
private fun unit(v: Float?, unit: Int): String? = v?.let { "${whole(it)} ${stringResource(unit)}" }

private fun asleep(minutes: Float?): String? = minutes?.let { Units.duration(it.roundToInt() * SECONDS_A_MINUTE) }

private const val HALF_TENTH = 0.05f
private const val SECONDS_A_MINUTE = 60L

/** What each habit does to the next morning, with the way into yesterday's journal. */
@Composable
private fun HabitsPanel(report: ForkReports.Report, onJournal: () -> Unit) {
    val shown = report.effects.filter { it.habit is ForkHabits.Habit.Logged || it.verdict != ForkHabits.Verdict.TooFew }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(stringResource(R.string.fork_habits_title), Modifier.padding(top = 4.dp), PanelLabelInset)
        if (shown.isEmpty()) {
            PageNote(stringResource(R.string.fork_habits_none))
        } else {
            PageRows {
                shown.forEachIndexed { i, e -> PageRow(habitName(e.habit), effectLine(e), below = true, divider = i > 0) }
            }
            PageNote(stringResource(R.string.fork_habits_note))
        }
        PageButton(stringResource(R.string.fork_habits_fill_in), onJournal, primary = report.yesterday == null)
    }
}

@Composable
private fun habitName(h: ForkHabits.Habit): String = stringResource(
    when (h) {
        is ForkHabits.Habit.Logged -> h.habit.label
        ForkHabits.Habit.HardDay -> R.string.fork_habit_hard_day
        ForkHabits.Habit.ShortNight -> R.string.fork_habit_short_night
        ForkHabits.Habit.LateWorkout -> R.string.fork_habit_late_workout
    },
)

/** "−12% Recovery the morning after, clear", or how far it has got before an effect shows. */
@Composable
private fun effectLine(e: ForkHabits.Effect): String {
    val percent = e.percent?.roundToInt()
    return when {
        percent != null && e.verdict == ForkHabits.Verdict.Clear -> stringResource(R.string.fork_effect_clear, signed(percent))
        percent != null && e.verdict == ForkHabits.Verdict.Likely -> stringResource(R.string.fork_effect_likely, signed(percent))
        e.verdict == ForkHabits.Verdict.Unclear -> stringResource(R.string.fork_effect_unclear)
        e.mornings < ForkHabits.MIN_DAYS -> stringResource(R.string.fork_effect_learning, e.mornings, ForkHabits.MIN_DAYS)
        else -> stringResource(R.string.fork_effect_needs_without)
    }
}

@Composable
private fun signed(v: Int): String =
    if (v < 0) stringResource(R.string.fork_signed_down, -v) else stringResource(R.string.fork_signed_up, v)
