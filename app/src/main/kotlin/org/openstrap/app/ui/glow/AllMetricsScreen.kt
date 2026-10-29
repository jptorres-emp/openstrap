package org.openstrap.app.ui.glow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import kotlin.math.roundToInt
import org.openstrap.app.data.Healthspan
import org.openstrap.app.data.RecoveryResult
import org.openstrap.app.data.WorkoutAnalysis
import org.openstrap.app.ui.dashboard.DayUiState
import org.openstrap.app.ui.dashboard.Detail
import org.openstrap.app.ui.dashboard.Metric
import org.openstrap.app.ui.dashboard.avg
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.app.l10n.tr

/*
 * All metrics (decision 3): a map of every number and screen the app already has, grouped by what it's about, so
 * nothing is only reachable from one card. Not a new feature: each row opens a screen that already exists.
 */

/**
 * Where an index row goes. All metrics is a map, not a card: a row whose home is a tab goes to that tab ([Home]),
 * one with a detail of its own opens it on the current tab's stack ([Open]).
 */
sealed interface IndexTarget {
    data class Open(val detail: Detail) : IndexTarget

    /** The tab, at its root and top; [charge] scrolls the Recovery tab to its Charge card. */
    data class Home(val tab: HomeTab, val charge: Boolean = false) : IndexTarget
}

/** The tabs an index row can go to. */
enum class HomeTab { Recovery, Sleep }

/** The row icon for where an index row goes (spec 2.5): its metric's, or the tab's own for a tab. */
internal fun IndexTarget.rowIcon(): androidx.compose.ui.graphics.vector.ImageVector? = when (this) {
    is IndexTarget.Open -> detail.rowIcon()
    is IndexTarget.Home -> when {
        charge -> org.openstrap.app.ui.dashboard.Score.Charge.rowIcon()
        tab == HomeTab.Sleep -> org.openstrap.app.ui.dashboard.Score.Sleep.rowIcon()
        else -> org.openstrap.app.ui.dashboard.Score.Recovery.rowIcon()
    }
}

/** One row of the index: the metric's [name] and [color], the day's [value] (null when there's none) and where it goes. */
data class IndexRow(val name: String, val color: Color, val value: String?, val status: String, val target: IndexTarget)

data class IndexGroup(val title: String, val rows: List<IndexRow>)

/** Shown under a row's name when the day has no value for it. */
internal val NO_VALUE: String get() = tr("Nothing for this day", "Nada para este dia")

/**
 * What the Recovery row says under its name: the learning state while the baselines fill, as on the Recovery card
 * ("Learning, 5 of 7 nights"); otherwise that it's the day's score, or null when there's nothing to say.
 */
internal fun recoveryStatus(r: org.openstrap.app.data.Recovery.Readiness?): String? = when {
    r == null -> null
    r.score != null && r.phase == org.openstrap.app.data.Recovery.Phase.Learning ->
        tr(
            "Learning, ${r.baselineNights} of ${org.openstrap.app.data.Recovery.SETTLED_NIGHTS} nights",
            "A aprender, ${r.baselineNights} de ${org.openstrap.app.data.Recovery.SETTLED_NIGHTS} noites",
        )
    r.score != null -> tr("Score for the day", "Pontuação do dia")
    r.withheld && r.phase == org.openstrap.app.data.Recovery.Phase.Withheld ->
        tr(
            "Learning, ${r.baselineNights} of ${org.openstrap.app.data.Recovery.SCORED_NIGHTS} nights",
            "A aprender, ${r.baselineNights} de ${org.openstrap.app.data.Recovery.SCORED_NIGHTS} noites",
        )
    else -> null
}

/**
 * The Habit Age row's value and status, the same as its page shows: the estimate once there is one, your age as
 * the starting point while it learns, otherwise no value and why.
 */
internal fun habitAgeRow(outcome: Healthspan.Outcome?): Pair<String?, String> {
    val r = outcome?.result
    return when {
        r != null -> "${r.estimate.roundToInt()}" to tr("Updated weekly", "Atualizado todas as semanas")
        outcome?.withheld?.learning == true && outcome.age != null -> "${outcome.age}" to tr("Starting point", "Ponto de partida")
        outcome?.withheld?.profile == true -> null to tr("Needs your profile", "Precisa do teu perfil")
        else -> null to tr("Not worked out yet", "Ainda não calculado")
    }
}

/** The index for the day in [state]: every [Metric] once, plus Recovery, Charge, Habit Age, Sleep and the workouts. */
fun metricIndex(
    state: DayUiState,
    recovery: RecoveryResult?,
    healthspan: Healthspan.Outcome?,
    workouts: List<WorkoutAnalysis>,
    today: LocalDate = LocalDate.now(),
    /** The strap's own Recovery for the day, shown as its estimate while OpenStrap's score is withheld, as on Today. */
    strapScore: Int? = null,
): List<IndexGroup> {
    val rec = recovery?.takeIf { it.date == state.date }
    // A row without a value says why when it knows ([waiting]), otherwise that the day has none.
    fun row(name: String, color: Color, value: String?, status: String, target: IndexTarget, waiting: String? = null) =
        IndexRow(name, color, value, if (value == null) waiting ?: NO_VALUE else status, target)
    fun metric(m: Metric, name: String, color: Color, value: Float?, status: String, suffix: String = "") =
        row(name, color, value?.let { m.format(it) + suffix }, status, IndexTarget.Open(Detail.OfMetric(m)))
    val (habitValue, habitStatus) = habitAgeRow(healthspan)
    val weekFrom = state.date.minusDays(6)
    // The 7 days up to the day shown, not Your week's Monday to Sunday, so the row says so. The strap's own
    // "Activity" records aren't workouts, as on Today and Your week.
    val week = workouts.count { a ->
        a.workout.sportType != org.openstrap.app.data.Workouts.GENERIC_ACTIVITY &&
            java.time.Instant.ofEpochSecond(a.workout.start).atZone(java.time.ZoneId.systemDefault()).toLocalDate() in weekFrom..state.date
    }
    return listOf(
        IndexGroup(
            tr("Recovery", "Recuperação"),
            listOf(
                recoveryStatus(rec?.readiness).let { status ->
                    val shown = org.openstrap.app.ui.dashboard.TodayCopy.recoveryShown(rec?.readiness, strapScore, NO_VALUE)
                    row(
                        tr("Recovery", "Recuperação"), MetricColors.readiness, shown.value?.toString(),
                        shown.word.takeIf { shown.estimate } ?: status ?: tr("Score for the day", "Pontuação do dia"), IndexTarget.Home(HomeTab.Recovery), waiting = status,
                    )
                },
                // Before the first night Charge is only the simulation's starting guess: a dash, as on the Recovery tab's battery.
                row(
                    tr("Energy", "Energia"), MetricColors.charge, rec?.charge?.takeIf { it.anchored }?.latest?.let(::chargeShown)?.toString(),
                    if (state.date == today) tr("Latest", "Mais recente") else tr("End of day", "Fim do dia"), IndexTarget.Home(HomeTab.Recovery, charge = true),
                    waiting = org.openstrap.app.ui.dashboard.AFTER_FIRST_NIGHT.takeIf { rec?.charge?.anchored == false },
                ),
                row(tr("Habit Age", "Idade dos hábitos"), MetricColors.habitAge, habitValue, habitStatus, IndexTarget.Open(Detail.Healthspan), waiting = habitStatus),
            ),
        ),
        IndexGroup(
            tr("Sleep", "Sono"),
            listOf(row(tr("Sleep", "Sono"), MetricColors.sleepDeep, state.sleep?.score?.toString(), tr("Score for the night", "Pontuação da noite"), IndexTarget.Home(HomeTab.Sleep))),
        ),
        IndexGroup(
            tr("Heart", "Coração"),
            listOf(
                metric(Metric.HeartRate, tr("Heart rate", "Frequência cardíaca"), MetricColors.heart, state.latestHeartRate?.toFloat(), tr("Latest reading", "Última leitura")),
                metric(Metric.RestingHr, tr("Resting heart rate", "FC em repouso"), MetricColors.heart, state.restingHeartRate?.toFloat(), tr("For the day", "Do dia")),
                metric(Metric.Hrv, tr("HRV", "VFC"), MetricColors.hrv, state.hrv.avg()?.toFloat(), tr("Day average", "Média do dia")),
            ),
        ),
        IndexGroup(
            tr("Body signals", "Sinais do corpo"),
            listOf(
                metric(Metric.Spo2, tr("Blood oxygen", "Oxigénio no sangue"), MetricColors.spo2, state.spo2.avg()?.toFloat(), tr("Day average", "Média do dia"), suffix = "%"),
                metric(Metric.SkinTemperature, tr("Skin temperature", "Temperatura da pele"), MetricColors.temperature, state.skinTemperature.takeIf { it.isNotEmpty() }?.map { it.y }?.average()?.toFloat(), tr("Day average", "Média do dia")),
                metric(Metric.Breathing, tr("Breathing rate", "Frequência respiratória"), MetricColors.sleepLight, state.breathRate.avg()?.toFloat(), tr("Day average", "Média do dia")),
                metric(Metric.Stress, tr("Stress", "Stress"), MetricColors.stress, state.stress.avg()?.toFloat(), tr("Day average", "Média do dia")),
            ),
        ),
        IndexGroup(
            tr("Activity", "Atividade"),
            listOf(
                metric(Metric.Steps, tr("Steps", "Passos"), MetricColors.steps, state.steps.toFloat(), tr("For the day", "Do dia")),
                row(tr("Workouts", "Treinos"), MetricColors.heart, week.toString(), tr("Last 7 days", "Últimos 7 dias"), IndexTarget.Open(Detail.AllWorkouts)),
            ),
        ),
    )
}

/**
 * The index as a sheet over the tab (spec 3.2), from the grid button in the header: one grouped list per section.
 * [onOpen] gets the tapped row's target; the caller closes the sheet and goes there.
 */
@Composable
fun AllMetricsSheet(
    groups: List<IndexGroup>,
    date: LocalDate,
    onDismiss: () -> Unit,
    onOpen: (IndexTarget) -> Unit,
) {
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.9f).dp
    org.openstrap.app.ui.components.GlassSheet(onDismiss) {
        Column(
            Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(tr("All metrics", "Todas as métricas"), color = Color.White, style = GlowType.label.copy(fontSize = 20.sp, lineHeight = 26.sp))
                Text(dayName(date), color = Color.White.copy(alpha = GlowType.SECONDARY_ALPHA), style = GlowType.secondary)
            }
            groups.forEach { group ->
                ListPanel(title = group.title) {
                    group.rows.forEachIndexed { i, r ->
                        ListRow(r.name, r.color, r.value, r.status, onClick = { onOpen(r.target) }, divider = i > 0, icon = r.target.rowIcon())
                    }
                }
            }
        }
    }
}
