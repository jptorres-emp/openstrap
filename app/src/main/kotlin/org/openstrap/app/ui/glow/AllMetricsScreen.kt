package org.openstrap.app.ui.glow

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import java.time.LocalDate
import kotlin.math.roundToInt
import org.openstrap.app.data.Healthspan
import org.openstrap.app.data.RecoveryResult
import org.openstrap.app.data.WorkoutAnalysis
import org.openstrap.app.ui.dashboard.DayUiState
import org.openstrap.app.ui.dashboard.Detail
import org.openstrap.app.ui.dashboard.Metric
import org.openstrap.app.ui.dashboard.ScreenList
import org.openstrap.app.ui.dashboard.avg
import org.openstrap.app.ui.theme.MetricColors

/*
 * All metrics (decision 3): a map of every number and screen the app already has, grouped by what it's about, so
 * nothing is only reachable from one card. Not a new feature: each row opens a screen that already exists.
 */

/** One row of the index: the metric's [name] and [color], the day's [value] (null when there's none) and [detail] to open. */
data class IndexRow(val name: String, val color: Color, val value: String?, val status: String, val detail: Detail)

data class IndexGroup(val title: String, val rows: List<IndexRow>)

/** Shown under a row's name when the day has no value for it. */
internal const val NO_VALUE = "Nothing for this day"

/**
 * What the Recovery row says under its name: the learning state while the baselines fill, as on the Recovery card
 * ("Learning, 5 of 7 nights"); otherwise that it's the day's score, or null when there's nothing to say.
 */
internal fun recoveryStatus(r: org.openstrap.app.data.Recovery.Readiness?): String? = when {
    r == null -> null
    r.score != null && r.phase == org.openstrap.app.data.Recovery.Phase.Learning ->
        "Learning, ${r.baselineNights} of ${org.openstrap.app.data.Recovery.SETTLED_NIGHTS} nights"
    r.score != null -> "Score for the day"
    r.withheld && r.phase == org.openstrap.app.data.Recovery.Phase.Withheld ->
        "Learning, ${r.baselineNights} of ${org.openstrap.app.data.Recovery.SCORED_NIGHTS} nights"
    else -> null
}

/** The index for the day in [state]: every [Metric] once, plus Recovery, Charge, Habit Age, Sleep and the workouts. */
fun metricIndex(
    state: DayUiState,
    recovery: RecoveryResult?,
    healthspan: Healthspan.Outcome?,
    workouts: List<WorkoutAnalysis>,
    today: LocalDate = LocalDate.now(),
): List<IndexGroup> {
    val rec = recovery?.takeIf { it.date == state.date }
    // A row without a value says why when it knows ([waiting]), otherwise that the day has none.
    fun row(name: String, color: Color, value: String?, status: String, detail: Detail, waiting: String? = null) =
        IndexRow(name, color, value, if (value == null) waiting ?: NO_VALUE else status, detail)
    fun metric(m: Metric, name: String, color: Color, value: Float?, status: String, suffix: String = "") =
        row(name, color, value?.let { m.format(it) + suffix }, status, Detail.OfMetric(m))
    val weekFrom = state.date.minusDays(6)
    val week = workouts.count { a ->
        java.time.Instant.ofEpochSecond(a.workout.start).atZone(java.time.ZoneId.systemDefault()).toLocalDate() in weekFrom..state.date
    }
    return listOf(
        IndexGroup(
            "Recovery",
            listOf(
                recoveryStatus(rec?.readiness).let { status ->
                    row("Recovery", MetricColors.readiness, rec?.readiness?.score?.toString(), status ?: "Score for the day", Detail.Recovery, waiting = status)
                },
                row(
                    "Charge", MetricColors.chargeAt(rec?.charge?.latest), rec?.charge?.latest?.roundToInt()?.toString(),
                    if (state.date == today) "Latest" else "End of day", Detail.Recovery,
                ),
                row("Habit Age", MetricColors.habitAge, healthspan?.result?.estimate?.roundToInt()?.toString(), "Updated weekly", Detail.Healthspan),
            ),
        ),
        IndexGroup(
            "Sleep",
            listOf(row("Sleep", MetricColors.sleepDeep, state.sleep?.score?.toString(), "Score for the night", Detail.Sleep)),
        ),
        IndexGroup(
            "Heart",
            listOf(
                metric(Metric.HeartRate, "Heart rate", MetricColors.heart, state.latestHeartRate?.toFloat(), "Latest reading"),
                metric(Metric.RestingHr, "Resting heart rate", MetricColors.heart, state.restingHeartRate?.toFloat(), "For the day"),
                metric(Metric.Hrv, "HRV", MetricColors.hrv, state.hrv.avg()?.toFloat(), "Day average"),
            ),
        ),
        IndexGroup(
            "Body signals",
            listOf(
                metric(Metric.Spo2, "Blood oxygen", MetricColors.spo2, state.spo2.avg()?.toFloat(), "Day average", suffix = "%"),
                metric(Metric.SkinTemperature, "Skin temperature", MetricColors.temperature, state.skinTemperature.takeIf { it.isNotEmpty() }?.map { it.y }?.average()?.toFloat(), "Day average"),
                metric(Metric.Breathing, "Breathing rate", MetricColors.sleepLight, state.breathRate.avg()?.toFloat(), "Day average"),
                metric(Metric.Stress, "Stress", MetricColors.stress, state.stress.avg()?.toFloat(), "Day average"),
            ),
        ),
        IndexGroup(
            "Activity",
            listOf(
                metric(Metric.Steps, "Steps", MetricColors.steps, state.steps.toFloat(), "For the day"),
                row("Workouts", MetricColors.steps, week.toString(), "Last 7 days", Detail.AllWorkouts),
            ),
        ),
    )
}

/** The index as a page: one grouped list per section, each row opening its screen on the same tab. */
@Composable
fun AllMetricsScreen(
    groups: List<IndexGroup>,
    date: LocalDate,
    contentPadding: PaddingValues,
    onOpen: (Detail) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    ScreenList(contentPadding, gutter = GlowLayout.Gutter) {
        item { PageHeader("All metrics", onBack, subtitle = dayName(date)) }
        groups.forEach { group ->
            item(group.title) {
                ListPanel(title = group.title) {
                    group.rows.forEachIndexed { i, r ->
                        ListRow(r.name, r.color, r.value, r.status, onClick = { onOpen(r.detail) }, divider = i > 0)
                    }
                }
            }
        }
    }
}
