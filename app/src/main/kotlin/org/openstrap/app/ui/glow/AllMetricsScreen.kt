package org.openstrap.app.ui.glow

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import java.time.LocalDate
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

/**
 * Where an index row goes. All metrics is a map, not a card: a row whose home is a tab goes to that tab ([Home]),
 * one with a detail of its own opens it on the stack the index is on ([Open]).
 */
sealed interface IndexTarget {
    data class Open(val detail: Detail) : IndexTarget

    /** The tab, at its root and top; [charge] scrolls the Recovery tab to its Charge card. */
    data class Home(val tab: HomeTab, val charge: Boolean = false) : IndexTarget
}

/** The tabs an index row can go to. */
enum class HomeTab { Recovery, Sleep }

/** One row of the index: the metric's [name] and [color], the day's [value] (null when there's none) and where it goes. */
data class IndexRow(val name: String, val color: Color, val value: String?, val status: String, val target: IndexTarget)

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

/**
 * The Habit Age row's value and status, the same as its page shows: the range once there's an estimate, your age as
 * the starting point while it learns, otherwise no value and why.
 */
internal fun habitAgeRow(outcome: Healthspan.Outcome?): Pair<String?, String> {
    val r = outcome?.result
    return when {
        r != null -> "${r.low}-${r.high}" to "Updated weekly"
        outcome?.withheld?.learning == true && outcome.age != null -> "${outcome.age}" to "Starting point"
        outcome?.withheld?.profile == true -> null to "Needs your profile"
        else -> null to "Not worked out yet"
    }
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
    fun row(name: String, color: Color, value: String?, status: String, target: IndexTarget, waiting: String? = null) =
        IndexRow(name, color, value, if (value == null) waiting ?: NO_VALUE else status, target)
    fun metric(m: Metric, name: String, color: Color, value: Float?, status: String, suffix: String = "") =
        row(name, color, value?.let { m.format(it) + suffix }, status, IndexTarget.Open(Detail.OfMetric(m)))
    val (habitValue, habitStatus) = habitAgeRow(healthspan)
    val weekFrom = state.date.minusDays(6)
    val week = workouts.count { a ->
        java.time.Instant.ofEpochSecond(a.workout.start).atZone(java.time.ZoneId.systemDefault()).toLocalDate() in weekFrom..state.date
    }
    return listOf(
        IndexGroup(
            "Recovery",
            listOf(
                recoveryStatus(rec?.readiness).let { status ->
                    row("Recovery", MetricColors.readiness, rec?.readiness?.score?.toString(), status ?: "Score for the day", IndexTarget.Home(HomeTab.Recovery), waiting = status)
                },
                row(
                    "Charge", MetricColors.chargeAt(rec?.charge?.latest), rec?.charge?.latest?.let(::chargeShown)?.toString(),
                    if (state.date == today) "Latest" else "End of day", IndexTarget.Home(HomeTab.Recovery, charge = true),
                ),
                row("Habit Age", MetricColors.habitAge, habitValue, habitStatus, IndexTarget.Open(Detail.Healthspan), waiting = habitStatus),
            ),
        ),
        IndexGroup(
            "Sleep",
            listOf(row("Sleep", MetricColors.sleepDeep, state.sleep?.score?.toString(), "Score for the night", IndexTarget.Home(HomeTab.Sleep))),
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
                row("Workouts", MetricColors.steps, week.toString(), "Last 7 days", IndexTarget.Open(Detail.AllWorkouts)),
            ),
        ),
    )
}

/** The index as a page: one grouped list per section, each row going to its tab or opening its detail. */
@Composable
fun AllMetricsScreen(
    groups: List<IndexGroup>,
    date: LocalDate,
    contentPadding: PaddingValues,
    onOpen: (IndexTarget) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    ScreenList(contentPadding, gutter = GlowLayout.Gutter) {
        item { PageHeader("All metrics", onBack, subtitle = dayName(date)) }
        groups.forEach { group ->
            item(group.title) {
                ListPanel(title = group.title) {
                    group.rows.forEachIndexed { i, r ->
                        ListRow(r.name, r.color, r.value, r.status, onClick = { onOpen(r.target) }, divider = i > 0)
                    }
                }
            }
        }
    }
}
