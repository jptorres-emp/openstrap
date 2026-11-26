package org.openstrap.app.data

import java.time.LocalDate
import kotlin.math.sqrt

/**
 * The fork's reports (jptorres-emp/openstrap), as WHOOP's weekly and monthly assessments read a stretch: the last 7
 * days against the 3 weeks before them ([week]) and the last 30 against the 30 before ([month]). [ForkTarget] adds the
 * day's Load target, [ForkPlan] tonight's bedtime, [ForkTally] the sports and the days of the week, and [ForkHabits]
 * what the habits do to the next morning. Pure, so it's unit tested; [ForkReportsSource] reads the days.
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
    /** Scored mornings needed for your normal Recovery, the middle half of them. */
    const val NORMAL_MIN = 14
    private const val LOW_QUARTER = 0.25f
    private const val HIGH_QUARTER = 0.75f

    /**
     * A morning's Recovery as [RecoveryEngine.compute] scores it ([score], null while it can't), whether body signals
     * capped it ([alert]), the strap's own score that morning ([strapScore]) and the night before it: its HRV, resting
     * heart rate, time asleep, when it began and ended ([bed] and [wake], epoch seconds) and its deep and REM minutes.
     */
    data class Morning(
        val date: LocalDate,
        val score: Int?,
        val alert: Boolean,
        val strapScore: Int?,
        val hrv: Float?,
        val restingHr: Float?,
        val asleepMinutes: Int,
        val bed: Long? = null,
        val wake: Long? = null,
        val deepRem: Int? = null,
    )

    /**
     * What [RecoveryEngine.mornings] reads: the [mornings], oldest first, each worn day's [loads], the Load so far and
     * the usual, your usual (fitness) Load through each worn day ([usual]), the sleep you need, and the last week's Load
     * (fatigue) as the usual is read.
     */
    data class Mornings(
        val mornings: List<Morning>,
        val loads: Map<LocalDate, Float>,
        val todayLoad: Float,
        val usualLoad: Float?,
        val usual: Map<LocalDate, Float> = emptyMap(),
        val sleepNeed: Int = Recovery.SLEEP_NEED_MINUTES,
        val fatigue: Float? = null,
    )

    /**
     * A workout on a day: its sport type, how long it lasted, whether it started at 20:00 or later, and whether you
     * started it ([byYou]) rather than the strap noticing it by itself.
     */
    data class Session(val sport: Int, val seconds: Long, val late: Boolean = false, val byYou: Boolean = true)

    /**
     * A date's morning and the day after it: Recovery (the app's, or the strap's while the app's learns), the night's
     * HRV, resting heart rate, time asleep, [bedtime] (minutes after the noon before, so 23:30 is 690 and 00:30 is 750),
     * [wakeTime] (minutes after midnight), time in bed and deep and REM minutes, and the day's Load, steps and workouts.
     * [partial] marks today, whose Load and steps are so far. [journal] is the day's answer, [versus] where its Load
     * ended against its target ([ForkTarget.mark]), [kcal] the energy spent that day.
     */
    data class Day(
        val date: LocalDate,
        val recovery: Int? = null,
        val strapRecovery: Int? = null,
        val alert: Boolean = false,
        val hrv: Float? = null,
        val restingHr: Float? = null,
        val asleepMinutes: Int? = null,
        val bedtime: Int? = null,
        val wakeTime: Int? = null,
        val inBedMinutes: Int? = null,
        val deepRem: Int? = null,
        val load: Float? = null,
        val steps: Int? = null,
        val sessions: List<Session> = emptyList(),
        val partial: Boolean = false,
        val journal: Set<JournalHabit>? = null,
        val versus: ForkTarget.Versus? = null,
        val kcal: Float? = null,
    ) {
        val workouts: Int get() = sessions.size
        val lateWorkout: Boolean get() = sessions.any { it.late }
        val hasData: Boolean get() = recovery != null || asleepMinutes != null || load != null || (steps ?: 0) > 0
    }

    /**
     * A stretch in figures: averages a day (Recovery, HRV, resting heart rate, minutes asleep and in deep and REM sleep,
     * bedtime and waking with how much each varies, Load, steps) and counts a stretch of it ([ready] days at
     * [READY_FROM] or more, [moderate] ones, [low] ones under [Recovery.FAIR_FROM], [workouts] and their time, the days
     * [within], [below] and [above] their Load target). A day still under way is left out of the Load and steps.
     */
    data class Figures(
        val recovery: Float?,
        val ready: Float,
        val moderate: Float,
        val low: Float,
        val hrv: Float?,
        val restingHr: Float?,
        val asleep: Float?,
        val deepRem: Float?,
        val bedtime: Float?,
        val bedtimeSpread: Float?,
        val wakeTime: Float?,
        val wakeSpread: Float?,
        val load: Float?,
        val workouts: Float,
        val workoutSeconds: Float,
        val steps: Float?,
        val within: Float,
        val below: Float,
        val above: Float,
    )

    /**
     * A stretch: its [days], oldest first, in figures ([now]) against the stretch before ([before], a week on average
     * for the week, null without a day of data there), its sports ([ForkTally.sports]) and your usual Load at its end
     * and at its start.
     */
    data class Stretch(
        val days: List<Day>,
        val now: Figures,
        val before: Figures?,
        val sports: List<ForkTally.Sport>,
        val usualNow: Float?,
        val usualBefore: Float?,
    )

    /**
     * Everything the Reports page shows, worked out for [today]: the week and the month, your [normal] Recovery, the
     * days of the week ([ForkTally.weekdays]), today's [plan], the habits' [effects], yesterday's journal and the
     * Training view ([training]).
     */
    data class Report(
        val today: LocalDate,
        val week: Stretch,
        val month: Stretch,
        val normal: IntRange?,
        val weekdays: List<Float?>,
        val plan: ForkPlan.Plan,
        val effects: List<ForkHabits.Effect>,
        val yesterday: Set<JournalHabit>?,
        val training: ForkTraining.Training = ForkTraining.Training(),
    )

    /** [days] in figures, the counts divided by [per] (3 for the three weeks a week is compared with). */
    fun figures(days: List<Day>, per: Int = 1): Figures {
        val scored = days.mapNotNull { it.recovery }
        val done = days.filter { !it.partial }
        val bedtimes = days.mapNotNull { it.bedtime?.toFloat() }
        val wakes = days.mapNotNull { it.wakeTime?.toFloat() }
        fun count(test: (Day) -> Boolean) = days.count(test).toFloat() / per
        return Figures(
            recovery = scored.map { it.toFloat() }.averageOrNull(),
            ready = scored.count { it >= READY_FROM }.toFloat() / per,
            moderate = scored.count { it >= Recovery.FAIR_FROM && it < READY_FROM }.toFloat() / per,
            low = scored.count { it < Recovery.FAIR_FROM }.toFloat() / per,
            hrv = days.mapNotNull { it.hrv }.averageOrNull(),
            restingHr = days.mapNotNull { it.restingHr }.averageOrNull(),
            asleep = days.mapNotNull { it.asleepMinutes?.toFloat() }.averageOrNull(),
            deepRem = days.mapNotNull { it.deepRem?.toFloat() }.averageOrNull(),
            bedtime = bedtimes.averageOrNull(),
            bedtimeSpread = bedtimes.spreadOrNull(),
            wakeTime = wakes.averageOrNull(),
            wakeSpread = wakes.spreadOrNull(),
            load = done.mapNotNull { it.load }.averageOrNull(),
            workouts = days.sumOf { it.workouts }.toFloat() / per,
            workoutSeconds = days.sumOf { d -> d.sessions.sumOf { it.seconds } }.toFloat() / per,
            steps = done.mapNotNull { d -> d.steps?.takeIf { it > 0 }?.toFloat() }.averageOrNull(),
            within = count { it.versus == ForkTarget.Versus.Within },
            below = count { it.versus == ForkTarget.Versus.Below },
            above = count { it.versus == ForkTarget.Versus.Above },
        )
    }

    /** The 7 days up to [today] against the 3 weeks before them, a week on average. [usual] as [Mornings.usual]. */
    fun week(days: List<Day>, today: LocalDate, usual: Map<LocalDate, Float> = emptyMap()): Stretch =
        stretch(days, today, WEEK_DAYS, WEEKS_BEFORE, usual)

    /** The 30 days up to [today] against the 30 before them. */
    fun month(days: List<Day>, today: LocalDate, usual: Map<LocalDate, Float> = emptyMap()): Stretch =
        stretch(days, today, MONTH_DAYS, 1, usual)

    /** Your normal Recovery: the middle half of the scored mornings in [days], once there are [NORMAL_MIN]. */
    fun normal(days: List<Day>): IntRange? {
        val scores = days.mapNotNull { it.recovery?.toFloat() }
        val lo = Recovery.percentile(scores, LOW_QUARTER)
        val hi = Recovery.percentile(scores, HIGH_QUARTER)
        return if (scores.size < NORMAL_MIN || lo == null || hi == null) null else Math.round(lo)..Math.round(hi)
    }

    private fun stretch(days: List<Day>, today: LocalDate, length: Int, stretches: Int, usual: Map<LocalDate, Float>): Stretch {
        val start = today.minusDays(length - 1L)
        val earliest = start.minusDays(length.toLong() * stretches)
        val shown = days.filter { it.date in start..today }
        val earlier = days.filter { it.date >= earliest && it.date < start }.takeIf { e -> e.any { it.hasData } }
        return Stretch(
            shown, figures(shown), earlier?.let { figures(it, stretches) }, ForkTally.sports(shown, earlier, stretches),
            ForkTarget.usualBefore(usual, today), ForkTarget.usualBefore(usual, start),
        )
    }
}

/** The average, or null for none. */
internal fun List<Float>.averageOrNull(): Float? = if (isEmpty()) null else average().toFloat()

/** How much the values vary (their standard deviation), or null for fewer than two. */
internal fun List<Float>.spreadOrNull(): Float? {
    if (size < 2) return null
    val mean = average()
    return sqrt(sumOf { (it - mean) * (it - mean) } / size).toFloat()
}
