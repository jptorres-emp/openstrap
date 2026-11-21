package org.openstrap.app.data

import java.time.LocalDate

/**
 * The fork's reports (jptorres-emp/openstrap), as WHOOP's weekly and monthly assessments read a stretch: the last 7
 * days against the 3 weeks before them ([week]) and the last 30 against the 30 before ([month]). [ForkTarget] adds the
 * day's Load target and [ForkHabits] what the journal's habits do to the next morning. Pure, so it's unit tested;
 * [ForkReportsSource] reads the days.
 */
object ForkReports {
    /** The days the reports read: the month shown and the month before it. */
    const val DAYS = 60
    const val WEEK_DAYS = 7
    /** The week is compared with the average of this many weeks before it. */
    const val WEEKS_BEFORE = 3
    const val MONTH_DAYS = 30
    /** Recovery's bands, as its words have them ([Recovery.FAIR_FROM] is where "Take it easy" ends). */
    const val READY_FROM = 70
    const val PUSH_FROM = 85

    /**
     * A morning's Recovery as [RecoveryEngine.compute] scores it ([score], null while it can't), whether body signals
     * capped it ([alert]), the strap's own score that morning ([strapScore]) and the night before it.
     */
    data class Morning(
        val date: LocalDate,
        val score: Int?,
        val alert: Boolean,
        val strapScore: Int?,
        val hrv: Float?,
        val restingHr: Float?,
        val asleepMinutes: Int,
    )

    /** What [RecoveryEngine.mornings] reads: the [mornings], oldest first, each worn day's [loads], the Load so far and the usual. */
    data class Mornings(val mornings: List<Morning>, val loads: Map<LocalDate, Float>, val todayLoad: Float, val usualLoad: Float?)

    /**
     * A date's morning and the day after it: Recovery (the app's, or the strap's while the app's learns), the night's
     * HRV, resting heart rate and time asleep, and the day's Load, steps and workouts ([lateWorkout] when one started at
     * 20:00 or later). [partial] marks today, whose Load and steps are so far. [journal] is the day's answer.
     */
    data class Day(
        val date: LocalDate,
        val recovery: Int? = null,
        val strapRecovery: Int? = null,
        val alert: Boolean = false,
        val hrv: Float? = null,
        val restingHr: Float? = null,
        val asleepMinutes: Int? = null,
        val load: Float? = null,
        val steps: Int? = null,
        val workouts: Int = 0,
        val lateWorkout: Boolean = false,
        val partial: Boolean = false,
        val journal: Set<JournalHabit>? = null,
    ) {
        val hasData: Boolean get() = recovery != null || asleepMinutes != null || load != null || (steps ?: 0) > 0
    }

    /**
     * A stretch in figures: averages a day (Recovery, HRV, resting heart rate, minutes asleep, Load, steps) and counts a
     * week of it or the whole of it ([ready] days at [READY_FROM] or more, [low] ones under [Recovery.FAIR_FROM], and
     * [workouts]). A day still under way is left out of the Load and steps a day.
     */
    data class Figures(
        val recovery: Float?,
        val ready: Float,
        val low: Float,
        val hrv: Float?,
        val restingHr: Float?,
        val asleep: Float?,
        val load: Float?,
        val workouts: Float,
        val steps: Float?,
    )

    /** A stretch [now] against the one before it ([before], null without a day of data there). */
    data class Compare(val now: Figures, val before: Figures?)

    /** Everything the Reports page shows, worked out for [today]. */
    data class Report(
        val today: LocalDate,
        val week: Compare,
        val month: Compare,
        val target: ForkTarget.Target?,
        val todayLoad: Float,
        val usualLoad: Float?,
        val effects: List<ForkHabits.Effect>,
        val yesterday: Set<JournalHabit>?,
    )

    /** [days] in figures, the counts divided by [per] (3 for the three weeks a week is compared with). */
    fun figures(days: List<Day>, per: Int = 1): Figures {
        val scored = days.mapNotNull { it.recovery }
        val done = days.filter { !it.partial }
        return Figures(
            recovery = scored.map { it.toFloat() }.averageOrNull(),
            ready = scored.count { it >= READY_FROM }.toFloat() / per,
            low = scored.count { it < Recovery.FAIR_FROM }.toFloat() / per,
            hrv = days.mapNotNull { it.hrv }.averageOrNull(),
            restingHr = days.mapNotNull { it.restingHr }.averageOrNull(),
            asleep = days.mapNotNull { it.asleepMinutes?.toFloat() }.averageOrNull(),
            load = done.mapNotNull { it.load }.averageOrNull(),
            workouts = days.sumOf { it.workouts }.toFloat() / per,
            steps = done.mapNotNull { d -> d.steps?.takeIf { it > 0 }?.toFloat() }.averageOrNull(),
        )
    }

    /** The 7 days up to [today] against the 3 weeks before them, a week on average. */
    fun week(days: List<Day>, today: LocalDate): Compare = compare(days, today, WEEK_DAYS, WEEKS_BEFORE)

    /** The 30 days up to [today] against the 30 before them. */
    fun month(days: List<Day>, today: LocalDate): Compare = compare(days, today, MONTH_DAYS, 1)

    private fun compare(days: List<Day>, today: LocalDate, length: Int, stretches: Int): Compare {
        val start = today.minusDays(length - 1L)
        val earliest = start.minusDays(length.toLong() * stretches)
        val earlier = days.filter { it.date >= earliest && it.date < start }.takeIf { e -> e.any { it.hasData } }
        return Compare(figures(days.filter { it.date in start..today }), earlier?.let { figures(it, stretches) })
    }
}

/** The average, or null for none. */
internal fun List<Float>.averageOrNull(): Float? = if (isEmpty()) null else average().toFloat()
