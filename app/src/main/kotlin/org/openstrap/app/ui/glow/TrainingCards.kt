package org.openstrap.app.ui.glow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.openstrap.app.R
import org.openstrap.app.data.ForkPlan
import org.openstrap.app.data.ForkTarget
import org.openstrap.app.data.ForkTraining
import org.openstrap.app.presentation.ReportsCopy
import org.openstrap.app.presentation.TrainingWords
import org.openstrap.app.presentation.Units
import org.openstrap.app.ui.theme.Ink
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.app.ui.theme.Space
import kotlin.math.roundToInt

/*
 * The Training view of the fork's Reports page (jptorres-emp/openstrap, [ReportsPage]), as the old fork's Training tab
 * had it: the day's Load target, your sports against it, the last week's Load against your usual and the calories
 * burned a day week by week. The words are ReportsCopy's and TrainingWords'.
 */

/** The day's Load target, the view's one dotted number, with what the day is for and the Load so far. */
@Composable
internal fun TrainingHero(plan: ForkPlan.Plan) {
    val words = remember(plan) { ReportsCopy.target(plan) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.S4)) {
        if (words != null) {
            HaloBigNumber(words.value, BigNumber.Detail, color = MetricColors.load)
            Text(words.word, color = MetricColors.load, style = GlowType.verdict)
            Text(words.note, color = GlowType.noteColor, style = GlowType.secondary)
        } else {
            Text(stringResource(R.string.fork_rep_target_none), color = Ink.Primary, style = GlowType.body)
            Text(stringResource(R.string.fork_rep_target), color = GlowType.noteColor, style = GlowType.secondary)
        }
    }
}

/** Your sports: each one's usual day of Load, where it lands against today's [target], and the days you did it. */
@Composable
internal fun SportsFitCard(training: ForkTraining.Training, target: ForkTarget.Target?) {
    val rows = remember(training, target) { training.sports.map { TrainingWords.sport(it, target) } }
    HaloCard(
        fill = CardFill.Rows,
        title = { HaloTitle(stringResource(R.string.fork_rep_fit_title), note = stringResource(R.string.fork_rep_fit_note)) },
    ) {
        rows.forEachIndexed { i, r ->
            HaloRow(r.title, value = r.value, line = r.line?.let { l -> { RowLine(l) } }, divider = i > 0)
        }
    }
}

/** The last week's Load against your usual: its zone, the share and what the zone means, or that it waits for a week. */
@Composable
internal fun BalanceCard(training: ForkTraining.Training) {
    val row = remember(training) { training.balance?.let { TrainingWords.balance(it) } }
    val title = stringResource(R.string.fork_rep_risk_title)
    val note = stringResource(R.string.fork_rep_risk_note)
    if (row == null) {
        HaloCard(title = { HaloTitle(title, note = note) }) { HaloNote(stringResource(R.string.fork_rep_target_none)) }
    } else {
        HaloCard(fill = CardFill.Rows, title = { HaloTitle(title, note = note) }) {
            HaloRow(row.title, value = row.value, line = row.line?.let { l -> { RowLine(l) } }, divider = false)
        }
    }
}

/** The calories burned a day, week by week with the last one lit, and the last week against the weeks before. */
@Composable
internal fun CaloriesCard(training: ForkTraining.Training) {
    val weeks = training.energy
    val values = remember(weeks) { weeks.map { it.kcalADay } }
    val labels = remember(weeks) { TrainingWords.calorieLabels(weeks) }
    val sentence = remember(weeks) { TrainingWords.calories(weeks) }
    HaloCard(
        title = { HaloTitle(stringResource(R.string.fork_rep_kcal_title), note = stringResource(R.string.fork_rep_kcal_note)) },
    ) {
        if (values.all { it == null }) {
            DetailEmpty(stringResource(R.string.fork_rep_kcal_none))
        } else {
            DetailColumns(values, MetricColors.calories, labels, valueLabels = true, format = { Units.count(it.roundToInt()) })
            sentence?.let { HaloNote(it) }
        }
    }
}
