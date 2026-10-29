package org.openstrap.app.ui.glow

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.openstrap.app.data.Reports
import org.openstrap.app.data.Training
import org.openstrap.app.l10n.tr
import org.openstrap.app.ui.dashboard.Detail
import org.openstrap.app.ui.dashboard.DetailCopy
import org.openstrap.app.ui.dashboard.ReportOutcome
import org.openstrap.app.ui.dashboard.Score
import org.openstrap.app.ui.dashboard.TrainingCopy
import org.openstrap.app.ui.theme.AppIcons
import org.openstrap.app.ui.theme.MetricColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/*
 * The Training tab: what the day shown calls for in your own sports, your last week's Load against your usual (the
 * injury risk), the calories you burn week by week (fat loss), and the weekly and monthly reports. Built from the one
 * report the app works out for the day ([ReportOutcome]), so nothing here can disagree with the Reports screen or with
 * tonight's bedtime on Today. Each card opens the detail behind it, and its (i) says what it's for, how it helps and
 * when to look at it. Today's Recovery and the day's Load against your usual stay on Today and Activity: this tab
 * says what to do with them.
 */
@Composable
fun TrainingScreen(
    date: LocalDate,
    outcome: ReportOutcome?,
    /** Whether the profile has age, weight and height, which the energy estimate needs. */
    hasBody: Boolean,
    header: @Composable () -> Unit,
    contentPadding: PaddingValues,
    onOpen: (Detail) -> Unit,
    onEditProfile: () -> Unit,
) {
    val list = rememberLazyListState()
    val report = (outcome as? ReportOutcome.Ready)?.report?.takeIf { it.date == date }
    val failed = outcome is ReportOutcome.Failed && outcome.date == date
    val isToday = date == LocalDate.now()
    var info by rememberSaveable { mutableStateOf<String?>(null) }
    CompositionLocalProvider(LocalGlowPaused provides { list.isScrollInProgress }) {
        GlowScreenList(contentPadding, list) {
            full { header() }
            full { TrainingHero(report, failed, isToday, onOpen = { onOpen(Detail.OfScore(Score.Load)) }, onInfo = { info = INFO_DAY }) }
            if (report != null) {
                full { LoadRiskCard(report.month.days, onOpen = { onOpen(Detail.OfScore(Score.Load)) }, onInfo = { info = INFO_RISK }) }
                full { CaloriesCard(report, hasBody, onOpen = { onOpen(Detail.OfScore(Score.Energy)) }, onEditProfile, onInfo = { info = INFO_CALORIES }) }
            }
            full { ReportsList(onOpen) }
        }
    }
    when (info) {
        INFO_DAY -> InfoSheet(
            TrainingCopy.dayInfo(), MetricColors.load, here = report?.target?.takeIf { it.scored }?.kind?.ordinal, hereLine = null,
            onDismiss = { info = null }, rows = TrainingCopy.DAY_ROWS,
        )
        INFO_RISK -> InfoSheet(
            TrainingCopy.riskInfo(), MetricColors.load, here = report?.month?.days?.lastOrNull()?.let(Training::ratio)?.let(Training::zone)?.ordinal,
            hereLine = null, onDismiss = { info = null }, rows = TrainingCopy.RISK_ROWS,
        )
        INFO_CALORIES -> InfoSheet(TrainingCopy.caloriesInfo(), MetricColors.steps, here = null, hereLine = null, onDismiss = { info = null }, rows = TrainingCopy.CALORIES_ROWS)
    }
}

private const val INFO_DAY = "day"
private const val INFO_RISK = "risk"
private const val INFO_CALORIES = "calories"

/**
 * What the day calls for (the hero): the kind of day by the morning's Recovery, a sentence in your own sports, the Load
 * target in dots with the Load so far, then the Recovery behind it and, today, tonight's bedtime, the night that
 * turns the training into fitness. A level glow in Load's colour, lit from below as far as the day's Load has filled
 * the target. Opens the Load detail.
 */
@Composable
private fun TrainingHero(report: Reports.Report?, failed: Boolean, isToday: Boolean, onOpen: () -> Unit, onInfo: () -> Unit) {
    val t = report?.target
    val load = report?.load
    val finished = report?.finished == true
    val share = if (t != null && load != null && t.range.last > 0) load / t.range.last else 0f
    GlowCard(GlowStyle.level(MetricColors.load, share), Modifier.fillMaxWidth().gridSize(CardSize.Wide), onClick = onOpen) {
        Column {
            GlowHeader(
                TrainingCopy.dayTitle(isToday), trailing = TrainingCopy.kindWord(t),
                trailingContent = {
                    Spacer(Modifier.width(10.dp))
                    InfoButton(tr("About the day's training", "Sobre o treino do dia"), onInfo)
                },
            )
            HeroSentence(
                when {
                    report == null && failed -> TrainingCopy.FAILED
                    report == null -> TrainingCopy.LOADING
                    t == null -> TrainingCopy.NO_TARGET
                    else -> TrainingCopy.plan(t, report.sports, load, finished)
                },
            )
            Spacer(Modifier.height(22.dp))
            HeroFigure(t?.let(TrainingCopy::targetValue), TrainingCopy.targetLabel(load, finished), big = true)
            if (report != null) {
                // Why the target is what it is, and the night that follows: text, the target being the card's number.
                val figures = listOfNotNull(
                    TrainingCopy.recoveryFigure(report.week.days.lastOrNull()),
                    TrainingCopy.bedtimeFigure(report.plan).takeIf { isToday },
                )
                if (figures.isNotEmpty()) DetailFigures(figures, Modifier.padding(top = 18.dp))
                if (isToday) TrainingCopy.planLine(report.plan)?.let {
                    Text(it, Modifier.padding(top = 8.dp), color = Color.White.copy(alpha = GlowType.SECONDARY_ALPHA), style = GlowType.secondary)
                }
            }
        }
    }
}

/**
 * The last week's Load against your usual (the acute to chronic ratio), in percent, with what the zone means for the
 * days ahead, then the last 30 days of it against the steady band, as a trend against your normal is drawn. Lit from
 * the top when it's building or a spike, from below when easing off, as a value above or below your normal. Its word is
 * the one Activity's Load card gives the week. Opens the Load detail, where fitness and fatigue are.
 */
@Composable
private fun LoadRiskCard(days: List<Reports.Day>, onOpen: () -> Unit, onInfo: () -> Unit) {
    val ratio = days.lastOrNull()?.let(Training::ratio)
    val zone = ratio?.let(Training::zone)
    EdgeCard(
        MetricColors.load, Modifier.fillMaxWidth(),
        status = when (zone) {
            Training.Zone.Building, Training.Zone.Spike -> EdgeStatus.Above
            Training.Zone.Easing -> EdgeStatus.Below
            else -> EdgeStatus.Usual
        },
        onClick = onOpen,
    ) {
        EdgeTitle(TrainingCopy.RISK_TITLE, zone?.let(TrainingCopy::zoneWord), onInfo = onInfo, infoLabel = tr("About Load and injury risk", "Sobre Carga e risco de lesão"))
        EdgeValue(ratio?.let(TrainingCopy::ratioValue), TrainingCopy.RATIO_UNIT, zone?.let(TrainingCopy::riskStatus) ?: TrainingCopy.RISK_WAITING)
        val values = days.map { d -> Training.ratio(d)?.let { it * 100f } }
        if (values.count { it != null } >= 2) {
            val band = Training.STEADY_LOW * 100f..Training.STEADY_HIGH * 100f
            NormalTrend(
                values, values.map { v -> v?.let { if (it > band.endInclusive) DetailCopy.Level.Above else if (it < band.start) DetailCopy.Level.Below else DetailCopy.Level.Inside } },
                MetricColors.load, Modifier.padding(top = 16.dp).semantics { contentDescription = TrainingCopy.describeRatios(days) },
                height = 150.dp, labels = spreadDates(days.map { it.date }), band = band, bandLabel = TrainingCopy.RISK_BAND,
            )
        }
    }
}

/**
 * The calories you burn a day, the last 7 days' in dots against the 3 weeks before, then the four weeks as bars with
 * their values, and what food has to do with it. In Calories' colour; without age, weight and height there's no estimate,
 * and a row to add them. Opens the Calories detail.
 */
@Composable
private fun CaloriesCard(report: Reports.Report, hasBody: Boolean, onOpen: () -> Unit, onEditProfile: () -> Unit, onInfo: () -> Unit) {
    val weeks = Training.energyWeeks(report.month.days, report.date)
    val now = weeks.last().kcalADay
    EdgeCard(MetricColors.steps, Modifier.fillMaxWidth(), onClick = onOpen) {
        EdgeTitle(TrainingCopy.CALORIES_TITLE, TrainingCopy.CALORIES_NOTE, onInfo = onInfo, infoLabel = tr("About calories burned", "Sobre as calorias gastas"))
        if (!hasBody) {
            EdgeValue(null, null, TrainingCopy.CALORIES_NO_BODY, waiting = false)
            LinkRow(TrainingCopy.ADD_PROFILE, onEditProfile, divider = false)
        } else {
            EdgeValue(now?.let(TrainingCopy::kcal), TrainingCopy.CALORIES_UNIT, now?.let { TrainingCopy.caloriesStatus(it, Training.energyBefore(weeks)) } ?: TrainingCopy.CALORIES_WAITING)
            if (weeks.count { it.kcalADay != null } >= 2) GlowBars(
                weeks.map { it.kcalADay ?: 0f },
                Modifier.padding(top = 16.dp).semantics { contentDescription = TrainingCopy.describeCalories(weeks) },
                height = 130.dp, labels = weeks.map { TrainingCopy.weekLabel(it.end) },
                highlight = weeks.lastIndex, accent = MetricColors.steps,
                valueLabel = { i -> weeks[i].kcalADay?.let(TrainingCopy::kcal) },
            )
            Text(TrainingCopy.CALORIES_LINE, Modifier.padding(top = 14.dp), color = Color.White.copy(alpha = GlowType.SECONDARY_ALPHA), style = GlowType.secondary)
        }
    }
}

/** The weekly and monthly reports, a row each. */
@Composable
private fun ReportsList(onOpen: (Detail) -> Unit) {
    ListPanel(title = TrainingCopy.REPORTS_TITLE) {
        ListRow(
            TrainingCopy.WEEK_REPORT, MetricColors.readiness, null, TrainingCopy.WEEK_REPORT_NOTE, divider = false, dash = false,
            onClick = { onOpen(Detail.Reports()) }, icon = AppIcons.ChartLineUp, iconTint = SportIconTint,
        )
        ListRow(
            TrainingCopy.MONTH_REPORT, MetricColors.readiness, null, TrainingCopy.MONTH_REPORT_NOTE, dash = false,
            onClick = { onOpen(Detail.Reports(month = true)) }, icon = AppIcons.CalendarBlank, iconTint = SportIconTint,
        )
    }
}

/** Four dates spread across a month's chart, as the Load detail labels its month. */
private fun spreadDates(dates: List<LocalDate>): List<String> = if (dates.isEmpty()) emptyList()
else listOf(0f, 1 / 3f, 2 / 3f, 1f).map { dates[((dates.size - 1) * it).toInt()].format(DateTimeFormatter.ofPattern("d MMM")) }
