package org.openstrap.app.ui.dashboard

import androidx.compose.ui.graphics.Color
import org.openstrap.app.data.db.SleepSession
import org.openstrap.app.ui.components.formatDuration
import org.openstrap.app.ui.theme.MetricColors
import kotlin.math.abs

/**
 * Plain-language headlines and ratings for the dashboard. The thresholds are general sleep and activity
 * guidance, not anything the strap reports; the UI says so where ratings appear.
 */
object Narrative {
    enum class Rating(val label: String, val color: Color) {
        OPTIMAL("Optimal", MetricColors.good),
        GOOD("Good", MetricColors.hrv),
        FAIR("Fair", MetricColors.warn),
        LOW("Pay attention", MetricColors.stress),
    }

    fun rate(value: Float, optimal: Float, good: Float, fair: Float): Rating = when {
        value >= optimal -> Rating.OPTIMAL
        value >= good -> Rating.GOOD
        value >= fair -> Rating.FAIR
        else -> Rating.LOW
    }

    fun sleepHeadline(score: Int): String = when {
        score >= 85 -> "Restful night"
        score >= 70 -> "Good sleep"
        score >= 60 -> "Fair sleep"
        else -> "Restless night"
    }

    fun sleepBody(session: SleepSession, asleepSeconds: Long): String = buildString {
        append("You slept ${formatDuration(asleepSeconds)}")
        session.deepMinutes?.takeIf { it > 0 }?.let { append(" with ${formatDuration(it * 60L)} of deep sleep") }
        append(".")
        session.wakeCount?.takeIf { it > 0 }?.let { append(" You woke $it ${if (it == 1) "time" else "times"}.") }
    }

    fun stepsHeadline(steps: Int, goal: Int): String = when {
        steps >= goal -> "Goal reached"
        steps >= goal / 2 -> "Making progress"
        steps > 0 -> "Getting started"
        else -> "Time to move"
    }

    fun stepsBody(steps: Int, goal: Int, activeHours: Int): String =
        if (steps >= goal) "You've passed your ${"%,d".format(goal)}-step goal" + (if (activeHours > 0) " across $activeHours active ${if (activeHours == 1) "hour" else "hours"}." else ".")
        else "${"%,d".format(goal - steps)} steps to go to reach your daily goal of ${"%,d".format(goal)}."

    /** One sleep contributor: rating plus how full its bar is. */
    data class Contributor(val label: String, val rating: Rating, val fraction: Float, val detail: String)

    fun sleepContributors(session: SleepSession, asleepSeconds: Long): List<Contributor> = buildList {
        val asleepMin = asleepSeconds / 60f
        add(Contributor("Total sleep", rate(asleepMin / 60f, 7f, 6f, 5f), asleepMin / (8 * 60f), formatDuration(asleepSeconds)))
        if (asleepMin > 0) {
            session.deepMinutes?.let { deep ->
                val share = deep / asleepMin
                add(Contributor("Deep sleep", rate(share, 0.16f, 0.12f, 0.08f), share / 0.25f, "${(share * 100).toInt()}%"))
            }
            session.remMinutes?.let { rem ->
                val share = rem / asleepMin
                add(Contributor("REM sleep", rate(share, 0.20f, 0.15f, 0.10f), share / 0.25f, "${(share * 100).toInt()}%"))
            }
            session.awakeMinutes?.let { awake ->
                val efficiency = asleepMin / (asleepMin + awake)
                add(Contributor("Efficiency", rate(efficiency, 0.90f, 0.85f, 0.75f), efficiency, "${(efficiency * 100).toInt()}%"))
            }
        }
    }

    /** +1 / -1 when [today] differs from [baseline] by more than [tolerance], else 0; null if either is missing. */
    fun direction(today: Float?, baseline: Float?, tolerance: Float): Int? {
        if (today == null || baseline == null) return null
        val d = today - baseline
        return if (abs(d) < tolerance) 0 else if (d > 0) 1 else -1
    }

    /** A 0-100 contributor score as a rating word. */
    fun ratingFor(score: Int): Rating = when {
        score >= 85 -> Rating.OPTIMAL
        score >= 70 -> Rating.GOOD
        score >= 55 -> Rating.FAIR
        else -> Rating.LOW
    }

    fun readinessHeadline(score: Int, alert: Boolean): String = when {
        alert -> "Signs of strain"
        score >= 85 -> "Primed"
        score >= 70 -> "Ready"
        score >= 55 -> "Take it steady"
        else -> "Prioritise recovery"
    }

    fun readinessBody(r: org.openstrap.app.data.Recovery.Readiness): String {
        val score = r.score ?: return ""
        val advice = when {
            r.strainAlert -> "Several body signals moved away from your normal at once. Rest, drink fluids and keep it easy today."
            score >= 85 -> "You're well recovered. A good day to push hard."
            score >= 70 -> "Recovered enough for your usual training."
            score >= 55 -> "Recovery isn't complete. Keep intensity moderate."
            else -> "Your body is still catching up. Favour rest, light movement and an early night."
        }
        val ranked = r.contributors.filter { it.factor.weight >= 0.07f }.sortedBy { it.score }
        val weakest = ranked.firstOrNull()?.takeIf { it.score < 70 }
        // A quiet training day isn't something to praise, so load never counts as a strength.
        val strongest = ranked.lastOrNull { it.factor.physiological || it.factor == org.openstrap.app.data.Recovery.Factor.Sleep }
            ?.takeIf { it.score >= 85 && it != weakest }
        return buildString {
            append(advice)
            if (weakest != null) append(" ${weakest.factor.label} is holding you back")
            if (weakest != null && strongest != null) append("; ${strongest.factor.label.replaceFirstChar { it.lowercase() }} is a strength.")
            else if (weakest != null) append(".")
            else if (strongest != null) append(" ${strongest.factor.label} looks strong.")
        }
    }

    fun chargeHeadline(charge: Int): String = when {
        charge >= 75 -> "Fully charged"
        charge >= 50 -> "Good energy"
        charge >= 25 -> "Running low"
        else -> "Depleted"
    }
}
