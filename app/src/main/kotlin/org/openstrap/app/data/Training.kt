package org.openstrap.app.data

import java.time.LocalDate

/**
 * The Training tab's numbers, worked out from the reports' days for whatever sports you do (CrossFit, futsal or any
 * other): what today's Load target means in your own sports, how the last week's Load stands against your usual (the
 * acute to chronic ratio, a guide to injury risk), and the energy you spend a day, week by week (for fat loss). Pure,
 * like [Reports]; the words are TrainingCopy's.
 */
object Training {
    /** The weeks your sports are read over, as the Load target's look back. */
    const val SPORT_WEEKS = 6

    /** Days a sport needs on its own (no other sport that day) for its usual day to count. */
    const val MIN_SPORT_DAYS = 2

    /**
     * A sport you do: the [days] you did it in the last [SPORT_WEEKS] weeks, and the Load of a usual day of it
     * ([dayLoad]: the median Load of the days you did it and no other sport), on the scale of the day's Load target.
     */
    data class SportLoad(val sport: Int, val days: Int, val dayLoad: Float)

    /**
     * Your sports over the [SPORT_WEEKS] weeks ending on [end], the most done first: each that you started yourself
     * on at least [MIN_SPORT_DAYS] days with no other sport, so a day's Load can stand for the sport. Days still under
     * way are left out. The strap's own "Activity" never counts ([Reports.sessions] leaves it out).
     */
    fun sports(days: List<Reports.Day>, sessions: List<Reports.Session>, end: LocalDate): List<SportLoad> {
        val from = end.minusDays(SPORT_WEEKS * 7L - 1)
        val loads = days.filter { it.date in from..end && !it.partial }.mapNotNull { d -> d.load?.let { d.date to it } }.toMap()
        val byDay = sessions.filter { it.byYou && it.date in from..end }.groupBy { it.date }
        val daysOf = byDay.values.flatMap { s -> s.map { it.sport }.distinct() }.groupingBy { it }.eachCount()
        val alone = byDay.filterValues { s -> s.map { it.sport }.distinct().size == 1 }
        return alone.entries.groupBy { it.value.first().sport }.mapNotNull { (sport, entries) ->
            val ls = entries.mapNotNull { loads[it.key] }.sorted()
            if (ls.size < MIN_SPORT_DAYS) null else SportLoad(sport, daysOf[sport] ?: ls.size, median(ls))
        }.sortedWith(compareByDescending<SportLoad> { it.days }.thenByDescending { it.dayLoad })
    }

    /** Where a usual day of a sport lands against a target. */
    enum class Fit { Under, Within, Over }

    /** Where [dayLoad], rounded as shown, lands against [target]. */
    fun fit(target: IntRange, dayLoad: Float): Fit {
        val l = Math.round(dayLoad)
        return when {
            l < target.first -> Fit.Under
            l > target.last -> Fit.Over
            else -> Fit.Within
        }
    }

    /* ------------------------------------------- Load and injury risk -------------------------------------- */

    /** The ratio's steady band, the one Activity's Load card calls Steady ([org.openstrap.app.ui.dashboard.TabCopy.loadBalance]). */
    const val STEADY_LOW = 0.75f
    const val STEADY_HIGH = 1.25f

    /** Above this the last week's Load is a spike on your usual: the zone tied most to injuries. */
    const val SPIKE = 1.5f

    /**
     * The last week's Load against your usual as [day] began: fatigue over fitness (ATL over CTL, the acute to chronic
     * ratio). 1 is level; null before there's a usual.
     */
    fun ratio(day: Reports.Day): Float? {
        val c = day.usualLoad?.takeIf { it > 0f } ?: return null
        return day.fatigue?.let { it / c }
    }

    /** Where the ratio stands: easing off, steady, building, or a spike. */
    enum class Zone { Easing, Steady, Building, Spike }

    fun zone(ratio: Float): Zone = when {
        ratio < STEADY_LOW -> Zone.Easing
        ratio <= STEADY_HIGH -> Zone.Steady
        ratio <= SPIKE -> Zone.Building
        else -> Zone.Spike
    }

    /* ------------------------------------------------- Energy ---------------------------------------------- */

    /** The weeks of energy shown, this one last. */
    const val ENERGY_WEEKS = 4

    /** A week of energy ending on [end]: the average kcal a day over its [days] finished days with an estimate. */
    data class EnergyWeek(val end: LocalDate, val kcalADay: Float?, val days: Int)

    /** The [weeks] weeks ending on [end], oldest first; a day still under way is left out of its week. */
    fun energyWeeks(days: List<Reports.Day>, end: LocalDate, weeks: Int = ENERGY_WEEKS): List<EnergyWeek> =
        (weeks - 1 downTo 0).map { k ->
            val to = end.minusDays(7L * k)
            val from = to.minusDays(6)
            val kcal = days.filter { it.date in from..to && !it.partial }.mapNotNull { it.kcal }
            EnergyWeek(to, if (kcal.isEmpty()) null else kcal.average().toFloat(), kcal.size)
        }

    /** The weeks before the last, as one average a day; null without any. */
    fun energyBefore(weeks: List<EnergyWeek>): Float? = weeks.dropLast(1).mapNotNull { it.kcalADay }.takeIf { it.isNotEmpty() }?.average()?.toFloat()

    /* ------------------------------------------------- Weight ---------------------------------------------- */

    /** The weeks of weight shown, this one last. */
    const val WEIGHT_WEEKS = 8

    /** How far back the trend looks: about a month, long enough for fat loss to show past day-to-day water. */
    const val TREND_WEEKS = 4

    /** A week of logged weight ending on [end]: the average of its weights; null without one. */
    data class WeightWeek(val end: LocalDate, val kg: Float?)

    /** The [weeks] weeks ending on [end], oldest first, from the weights logged ([logged]: day to kg). */
    fun weightWeeks(logged: Map<LocalDate, Float>, end: LocalDate, weeks: Int = WEIGHT_WEEKS): List<WeightWeek> =
        (weeks - 1 downTo 0).map { k ->
            val to = end.minusDays(7L * k)
            val kg = logged.filterKeys { it in to.minusDays(6)..to }.values
            WeightWeek(to, if (kg.isEmpty()) null else kg.average().toFloat())
        }

    /**
     * How weight has moved: the [kg] between the average of the last 7 days' weights and that of the first week with
     * a weight in the [TREND_WEEKS] weeks before them, which starts on [since]. Weeks, not single mornings, so a day's
     * water or salt doesn't read as a trend.
     */
    data class WeightTrend(val kg: Float, val since: LocalDate)

    /** The trend to [end]; null without a weight in the last 7 days and one in the weeks before them. */
    fun weightTrend(logged: Map<LocalDate, Float>, end: LocalDate): WeightTrend? {
        val weekFrom = end.minusDays(6)
        val now = logged.filterKeys { it in weekFrom..end }.values.takeIf { it.isNotEmpty() }?.average() ?: return null
        val earlier = logged.filterKeys { it < weekFrom && it >= weekFrom.minusDays(7L * TREND_WEEKS) }
        val first = earlier.keys.minOrNull() ?: return null
        val base = earlier.filterKeys { it <= first.plusDays(6) }.values.average()
        return WeightTrend((now - base).toFloat(), first)
    }

    private fun median(sorted: List<Float>): Float {
        val n = sorted.size
        return if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2f
    }
}
