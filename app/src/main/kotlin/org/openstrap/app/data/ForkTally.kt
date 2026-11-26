package org.openstrap.app.data

import java.time.DayOfWeek

/**
 * The fork's reports' counts (jptorres-emp/openstrap): the sports of a stretch against the stretch before ([sports])
 * and Recovery by the day of the week ([weekdays]), as WHOOP's monthly assessment has them. Pure, so it's unit tested.
 */
object ForkTally {
    /** The most sports a stretch lists. */
    const val MAX_SPORTS = 5
    /** Scored mornings a day of the week needs before it has an average. */
    const val MIN_MORNINGS = 2
    /** Under this many points between the best and the worst day of the week, the days are alike. */
    const val ALIKE_POINTS = 5f

    /**
     * A [sport] in a stretch: its [sessions] and their [seconds] there, and the sessions in the stretch before ([before],
     * a stretch on average, null without one).
     */
    data class Sport(val sport: Int, val sessions: Int, val seconds: Long, val before: Float?)

    /** The sports of [now], most sessions first then most time, against [before] over its [stretches]. */
    fun sports(now: List<ForkReports.Day>, before: List<ForkReports.Day>?, stretches: Int = 1): List<Sport> {
        val earlier = before?.flatMap { it.sessions }?.groupingBy { it.sport }?.eachCount()
        return now.flatMap { it.sessions }.groupBy { it.sport }
            .map { (sport, list) ->
                Sport(sport, list.size, list.sumOf { it.seconds }, earlier?.let { (it[sport] ?: 0).toFloat() / stretches })
            }
            .sortedWith(compareByDescending<Sport> { it.sessions }.thenByDescending { it.seconds })
            .take(MAX_SPORTS)
    }

    /** Recovery's average on each day of the week, Monday first, from [days]; null for a day with too few mornings. */
    fun weekdays(days: List<ForkReports.Day>): List<Float?> {
        val by = days.filter { it.recovery != null }.groupBy { it.date.dayOfWeek }
        return DayOfWeek.entries.map { d ->
            by[d]?.takeIf { it.size >= MIN_MORNINGS }?.mapNotNull { it.recovery?.toFloat() }?.averageOrNull()
        }
    }

    /**
     * The best and worst days of the week in [weekdays] (Monday first), or null when there aren't two with an average or
     * they're within [ALIKE_POINTS] of each other.
     */
    fun bestAndWorst(weekdays: List<Float?>): Pair<DayOfWeek, DayOfWeek>? {
        val known = weekdays.withIndex().mapNotNull { (i, v) -> v?.let { DayOfWeek.entries[i] to it } }
        if (known.size < 2) return null
        val best = known.maxBy { it.second }
        val worst = known.minBy { it.second }
        return if (best.second - worst.second < ALIKE_POINTS) null else best.first to worst.first
    }
}
