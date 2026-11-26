package org.openstrap.app.data

import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * The Training view of the fork's reports (jptorres-emp/openstrap), as the old fork's Training tab had it: what
 * today's Load target means in your own sports ([sports], [fit]), how the last week's Load stands against your usual
 * (fatigue over fitness, a guide to injury risk: [ratio], [zone]), and the energy you spend a day, week by week (for
 * fat loss, [energyWeeks]). Pure, so it's unit tested; the words are ReportsCopy's and TrainingWords'.
 */
object ForkTraining {
    /** The weeks your sports are read over, as the Load target's look back. */
    const val SPORT_WEEKS = 6

    /** Days a sport needs on its own (no other sport that day) for its usual day to count. */
    const val MIN_SPORT_DAYS = 2

    /** Steady is within this share of your usual either way, the band Activity's Load card uses (LoadBands.BAND). */
    const val STEADY_BAND = 0.25f

    /** Above this the last week's Load is a spike on your usual: the zone tied most to injuries. */
    const val SPIKE = 1.5f

    /** The weeks of energy shown, this one last. */
    const val ENERGY_WEEKS = 4

    private const val WEEK = 7

    /**
     * A sport you do: the [days] you did it in the last [SPORT_WEEKS] weeks, and the Load of a usual day of it
     * ([dayLoad]: the median Load of the days you did it and no other sport), on the scale of the day's Load target.
     */
    data class SportLoad(val sport: Int, val days: Int, val dayLoad: Float)

    /** Where a usual day of a sport lands against the day's target. */
    enum class Fit { Under, Within, Over }

    /** Where the last week's Load stands against your usual: easing off, steady, building, or a spike. */
    enum class Zone { Easing, Steady, Building, Spike }

    /** A week of energy ending on [end]: the average kcal a day over its [days] finished days with an estimate. */
    data class EnergyWeek(val end: LocalDate, val kcalADay: Float?, val days: Int)

    /** The Training view's numbers: your sports, the week's Load against your usual ([balance]), and the energy weeks. */
    data class Training(
        val sports: List<SportLoad> = emptyList(),
        val balance: Float? = null,
        val energy: List<EnergyWeek> = emptyList(),
    )

    /** The Training view for the [days] up to [end] (the last finished day), with fatigue and fitness as the morning had them. */
    fun training(days: List<ForkReports.Day>, end: LocalDate, fatigue: Float?, usual: Float?): Training =
        Training(sports(days, end), ratio(fatigue, usual), energyWeeks(days, end))

    /**
     * Your sports over the [SPORT_WEEKS] weeks ending on [end], the most done first: each one you started yourself on at
     * least [MIN_SPORT_DAYS] days with no other sport, so a day's Load can stand for the sport. Days still under way are
     * left out.
     */
    fun sports(days: List<ForkReports.Day>, end: LocalDate): List<SportLoad> {
        val from = end.minusDays(SPORT_WEEKS * WEEK - 1L)
        val shown = days.filter { it.date in from..end && !it.partial }
        val mine = shown.associate { d -> d.date to d.sessions.filter { it.byYou }.map { it.sport }.distinct() }
            .filterValues { it.isNotEmpty() }
        val daysOf = mine.values.flatten().groupingBy { it }.eachCount()
        val loads = shown.mapNotNull { d -> d.load?.let { d.date to it } }.toMap()
        return mine.filterValues { it.size == 1 }.entries.groupBy({ it.value.single() }, { loads[it.key] })
            .mapNotNull { (sport, list) ->
                val known = list.filterNotNull()
                val usual = Stats.median(known)
                if (known.size < MIN_SPORT_DAYS || usual == null) null else SportLoad(sport, daysOf[sport] ?: known.size, usual)
            }
            .sortedWith(compareByDescending<SportLoad> { it.days }.thenByDescending { it.dayLoad })
    }

    /** Where [dayLoad], rounded as shown, lands against [target]. */
    fun fit(target: IntRange, dayLoad: Float): Fit {
        val load = dayLoad.roundToInt()
        return when {
            load < target.first -> Fit.Under
            load > target.last -> Fit.Over
            else -> Fit.Within
        }
    }

    /** The last week's Load against your usual: [fatigue] over [usual] (fitness), 1 being level; null without a usual. */
    fun ratio(fatigue: Float?, usual: Float?): Float? = if (fatigue == null || usual == null || usual <= 0f) null else fatigue / usual

    /** Where [ratio] stands. */
    fun zone(ratio: Float): Zone = when {
        ratio < 1 - STEADY_BAND -> Zone.Easing
        ratio <= 1 + STEADY_BAND -> Zone.Steady
        ratio <= SPIKE -> Zone.Building
        else -> Zone.Spike
    }

    /** The [weeks] weeks ending on [end], oldest first; a day still under way is left out of its week. */
    fun energyWeeks(days: List<ForkReports.Day>, end: LocalDate, weeks: Int = ENERGY_WEEKS): List<EnergyWeek> =
        (weeks - 1 downTo 0).map { k ->
            val to = end.minusDays(WEEK.toLong() * k)
            val kcal = days.filter { it.date in to.minusDays(WEEK - 1L)..to && !it.partial }.mapNotNull { it.kcal }
            EnergyWeek(to, kcal.averageOrNull(), kcal.size)
        }

    /** The weeks before the last, as one average a day; null without any. */
    fun energyBefore(weeks: List<EnergyWeek>): Float? = weeks.dropLast(1).mapNotNull { it.kcalADay }.averageOrNull()
}
