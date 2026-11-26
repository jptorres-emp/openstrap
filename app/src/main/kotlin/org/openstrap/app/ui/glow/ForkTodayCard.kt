package org.openstrap.app.ui.glow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.openstrap.app.R
import org.openstrap.app.data.ForkReports
import org.openstrap.app.presentation.ReportsCopy
import org.openstrap.app.ui.glow.pictures.DayBars
import org.openstrap.app.ui.glow.pictures.PictureSize
import org.openstrap.app.ui.theme.MetricColors

/**
 * The fork's weekly report on Today (jptorres-emp/openstrap), as the old fork's week card had it: the last 7 days'
 * Recovery as columns of dots, today's lit, their average and how it stands against the 3 weeks before. It opens the
 * Reports page; on Mondays it comes right under the hero ([org.openstrap.app.ui.dashboard.todayCards]).
 */
@Composable
internal fun ForkReportCard(week: ForkReports.Stretch, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val (value, line) = remember(week) { ReportsCopy.weekCard(week) }
    val days = remember(week) { week.days.map { it.recovery?.toFloat() } }
    TodayPictureCard(
        MetricColors.readiness, stringResource(R.string.fork_today_report), PictureSize.Wide, value, unit = null, tag = null,
        status = line, modifier = modifier, note = stringResource(R.string.fork_today_report_note), onClick = onClick,
        picture = { m -> DayBars(days, MetricColors.readiness, PictureSize.Wide, m) },
    )
}
