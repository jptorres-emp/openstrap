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

    /**
     * A sleep score in Zepp's bands: 90+ Optimal, 80-89 Good, 60-79 Fair, below 60 Attention (codemap 06 §4.2). Stricter
     * than [ratingFor] because that's the scale the strap's own score is built for.
     */
    fun sleepRating(score: Int): Rating = when {
        score >= 90 -> Rating.OPTIMAL
        score >= 80 -> Rating.GOOD
        score >= 60 -> Rating.FAIR
        else -> Rating.LOW
    }

    /** Under 6 h asleep: the bottom of Zepp's duration bands (codemap 06 §4.2), and 56 on [org.openstrap.app.data.Recovery.durationScore]. */
    const val SHORT_NIGHT_MINUTES = 6 * 60

    /** Awake after falling asleep from here on: where [org.openstrap.app.data.Recovery.continuityScore] starts taking points off. */
    const val BROKEN_AWAKE_MINUTES = 30

    /**
     * This many wake-ups is broken sleep even when they were brief. Set high on purpose: everyone wakes a few times a
     * night and wake detection is the weakest part of wearable staging (evidence-review.md, "Sleep"), so only about
     * one every hour of a normal night counts. Short wake-ups that add up to 30 min are caught by [BROKEN_AWAKE_MINUTES].
     */
    const val BROKEN_WAKE_COUNT = 6

    /**
     * Deep + REM below this share of the time asleep is a light night. Adults usually spend 40-50 % there; a quarter
     * leaves room for the strap misreading deep as light (evidence-review.md, "Sleep").
     */
    const val LIGHT_SLEEP_SHARE = 0.25f

    /**
     * What pulled the night down, in a few words. The strap's score mostly tracks duration, so a calm short night
     * says "Short night" rather than "Restless". Short and broken outrank the score; light sleep is the weakest
     * cause, since stages aren't rated (see [sleepContributors]), so it's named only when nothing else is and the
     * score is below Good. Null without a score; callers fall back to time asleep.
     */
    fun sleepHeadline(session: SleepSession, asleepSeconds: Long): String? {
        val score = session.score ?: return null
        val asleepMin = asleepSeconds / 60
        val short = asleepMin < SHORT_NIGHT_MINUTES
        val broken = (session.awakeMinutes ?: 0) >= BROKEN_AWAKE_MINUTES || (session.wakeCount ?: 0) >= BROKEN_WAKE_COUNT
        val deep = session.deepMinutes
        val rem = session.remMinutes
        val light = deep != null && rem != null && asleepMin > 0 && (deep + rem) < LIGHT_SLEEP_SHARE * asleepMin
        return when {
            short && broken -> "Short, broken night"
            short -> "Short night"
            broken -> "Broken sleep"
            score >= 90 -> "Great night"
            score >= 80 -> "Good night"
            light -> "Light sleep"
            else -> "Okay night"
        }
    }

    fun sleepBody(session: SleepSession, asleepSeconds: Long): String = buildString {
        append("You slept ${formatDuration(asleepSeconds)}")
        session.deepMinutes?.takeIf { it > 0 }?.let { append(" with ${formatDuration(it * 60L)} of deep sleep") }
        append(".")
        session.wakeCount?.takeIf { it > 0 }?.let { append(" You woke $it ${if (it == 1) "time" else "times"}.") }
    }

    /** One sleep contributor: rating plus how full its bar is. */
    data class Contributor(val label: String, val rating: Rating, val fraction: Float, val detail: String)

    /**
     * What shaped the night: total sleep against need (and too much of it) and continuity (wake after sleep onset). Deep and REM aren't
     * rated: wearable staging is too unreliable to score (evidence-review.md, "Sleep"), so the stages panel shows them
     * without a target.
     */
    fun sleepContributors(
        session: SleepSession,
        asleepSeconds: Long,
        needMinutes: Int = org.openstrap.app.data.Recovery.SLEEP_NEED_MINUTES,
        wasoMinutes: Int? = null,
    ): List<Contributor> = buildList {
        val asleepMin = (asleepSeconds / 60).toInt()
        val duration = org.openstrap.app.data.Recovery.durationScore(asleepMin, needMinutes)
        add(Contributor("Total sleep", totalSleepRating(asleepMin, duration), asleepMin / needMinutes.toFloat(), formatDuration(asleepSeconds) + " of ${formatDuration(needMinutes * 60L)}"))
        if (wasoMinutes != null && asleepMin > 0) {
            val c = org.openstrap.app.data.Recovery.continuityScore(wasoMinutes)
            add(Contributor("Continuity", ratingFor(Math.round(c)), c / 100f, "${formatDuration(wasoMinutes * 60L)} awake after falling asleep"))
        }
    }

    /**
     * Total sleep against need, plus Zepp's oversleep bands: over 10 h is Attention, over 9 h 30 Fair (codemap 06
     * §4.2). Need tops out at 9 h, so these only ever lower a night that met it.
     */
    private fun totalSleepRating(asleepMinutes: Int, durationScore: Float): Rating = when {
        asleepMinutes > OVERSLEEP_ATTENTION_MINUTES -> Rating.LOW
        asleepMinutes > OVERSLEEP_FAIR_MINUTES -> Rating.FAIR
        else -> ratingFor(Math.round(durationScore))
    }

    const val OVERSLEEP_FAIR_MINUTES = 570
    const val OVERSLEEP_ATTENTION_MINUTES = 600

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
        score >= org.openstrap.app.data.Recovery.FAIR_FROM -> Rating.FAIR
        else -> Rating.LOW
    }

    /**
     * The strap's own readiness score or one of its parts (HRV, resting HR, skin temperature, AHI) in Zepp's three
     * bands: 85+ Optimal, 70-84 Good, below 70 Pay attention (codemap 06 §2.6). Only for the strap's values; OpenStrap's
     * own score and contributors use [ratingFor].
     */
    fun zeppReadinessRating(score: Int): Rating = when {
        score >= 85 -> Rating.OPTIMAL
        score >= 70 -> Rating.GOOD
        else -> Rating.LOW
    }

    fun readinessHeadline(score: Int, alert: Boolean): String = when {
        alert -> org.openstrap.app.data.BodySignalsCopy.SHORT
        score >= 85 -> "Primed"
        score >= 70 -> "Ready"
        score >= org.openstrap.app.data.Recovery.FAIR_FROM -> "Take it steady"
        else -> "Prioritise recovery"
    }

    fun readinessBody(r: org.openstrap.app.data.Recovery.Readiness): String {
        val score = r.score ?: return ""
        val advice = when {
            r.strainAlert -> "Several overnight signals are outside your usual range. Consider an easier day."
            score >= 85 -> "You're well recovered. A good day to push hard."
            score >= 70 -> "Recovered enough for your usual training."
            score >= org.openstrap.app.data.Recovery.FAIR_FROM -> "Recovery isn't complete. Keep intensity moderate."
            else -> "Your body is still catching up. Favour rest, light movement and an early night."
        }
        val ranked = r.contributors.filter { it.factor.weight >= 0.06f }.sortedBy { it.score }
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

    /** Where stress starts counting, and why: the user's calm level, or how long until there is one. */
    fun stressThreshold(t: org.openstrap.app.data.Recovery.StressThreshold): String {
        val calm = t.calmLevel
        val left = org.openstrap.app.data.Recovery.MIN_STRESS_DAYS - t.days
        return when {
            calm != null -> "Stress counts from ${t.value}: ${org.openstrap.app.data.Recovery.STRESS_MARGIN} above your calm level of $calm over the last ${t.days} days."
            left > 0 -> "Stress counts from ${t.value} for now. After ${if (left == 1) "1 more day" else "$left more days"} of wear it adjusts to your own calm level."
            else -> "Stress counts from ${t.value} for now, until there are enough calm readings to find your own level."
        }
    }

    /** One sentence on where the day's charge went, naming the biggest drain. */
    fun chargeBody(c: org.openstrap.app.data.Recovery.ChargeDay): String {
        val now = c.latest?.toInt() ?: return ""
        val used = (c.drainedExercise + c.drainedStress + c.drainedLiving).toInt()
        val start = c.atWake?.let {
            if (c.strapOffOvernight) "With the strap off overnight, today started from an estimated ${it.toInt()}; you've used $used since"
            else "You woke at ${it.toInt()} and have used $used since"
        } ?: "You've used $used today"
        val biggest = listOf("movement" to c.drainedExercise, "stress" to c.drainedStress).maxBy { it.second }
        val cause = if (biggest.second >= 8) ", most of it on ${biggest.first}." else "."
        val advice = when {
            now < 25 -> " Wind down and get to bed early."
            now < 50 -> " Pace yourself for the rest of the day."
            else -> " Plenty left for the rest of the day."
        }
        return start + cause + advice
    }
}
