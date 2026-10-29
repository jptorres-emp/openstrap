package org.openstrap.app.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The Reports screen's numbers. The last 7 days against the 3 weeks before them and the last 30 against the 30 before,
 * as WHOOP's weekly and monthly assessments compare them, and four things worked out on top of the engine's own
 * numbers: the day's Load target, tonight's sleep plan, how Recovery spread over the month, and what your habits do to
 * the next morning. [RecoveryEngine.reportDays] gathers the [Day]s; everything here is pure, so it's unit-tested.
 */
object Reports {
    /** Mornings the reports read: the month shown and the month before it. */
    const val DAYS = 60
    const val WEEK_DAYS = 7
    /** The week is compared with the average of this many weeks before it. */
    const val WEEKS_BEFORE = 3
    const val MONTH_DAYS = 30

    /** Recovery's bands, as its status words have them ([org.openstrap.app.ui.dashboard.TodayCopy.recoveryWord]). */
    const val READY_FROM = 70
    const val PUSH_FROM = 85

    /**
     * One morning and the day after it. [recovery] is the app's own, scored with the nights and loads before that
     * morning as [RecoveryEngine.compute] scores the day; [strapRecovery] is the strap's, which stands in for the Load
     * target while the app's is learning. The night's [hrv] and [restingHr] are OpenStrap's own on a valid night, and
     * [hrvVsUsual] the change Recovery saw against its baseline, in percent. [partial] marks the day still under way:
     * its Load and steps are so far.
     */
    data class Day(
        val date: LocalDate,
        val recovery: Int? = null,
        val strapRecovery: Int? = null,
        val hrvRange: Recovery.HrvRange? = null,
        val alert: Boolean = false,
        val hrv: Float? = null,
        val restingHr: Float? = null,
        val hrvVsUsual: Int? = null,
        /** The main sleep that ended this morning: when it started and ended, minutes in bed, asleep, and in deep and REM sleep. */
        val bedtime: LocalTime? = null,
        val wake: LocalTime? = null,
        val inBedMinutes: Int? = null,
        val asleepMinutes: Int? = null,
        val restorativeMinutes: Int? = null,
        /** Minutes asleep in naps that day. */
        val napMinutes: Int = 0,
        /** The day's Load (TRIMP); null on a day the strap was mostly off, other than the day shown. */
        val load: Float? = null,
        /** Your usual daily Load (fitness, CTL) as the day began; null before a week of worn days. */
        val usualLoad: Float? = null,
        val steps: Int? = null,
        val partial: Boolean = false,
    ) {
        val hasData: Boolean get() = recovery != null || asleepMinutes != null || load != null || (steps ?: 0) > 0
    }

    /**
     * What [RecoveryEngine.reportDays] reads: the [days] ending on [date], oldest first, one for every date, and the
     * Load of every worn day before [date] as far back as the engine reads, for the Load target's look back.
     */
    data class Input(val date: LocalDate, val days: List<Day>, val loads: Map<LocalDate, Float>)

    /* ----------------------------------------------- Workouts ---------------------------------------------- */

    /**
     * A workout as the reports count it: its day and local start time, sport, active time and seconds in each heart
     * rate zone. Only one you started ([byYou]) makes a training day for the Load target; one the strap found by itself
     * (a walk it noticed) still counts as a workout, as the week story counts it.
     */
    data class Session(
        val date: LocalDate,
        val time: LocalTime,
        val sport: Int,
        val name: String,
        val seconds: Long,
        val zoneSeconds: List<Int>,
        val byYou: Boolean,
    )

    /** [workouts] as sessions, oldest first: every one with a sport, not the strap's plain "Activity". */
    fun sessions(workouts: List<WorkoutAnalysis>, zone: ZoneId): List<Session> = workouts
        .filter { it.workout.sportType != Workouts.GENERIC_ACTIVITY }
        .map { w ->
            val at = Instant.ofEpochSecond(w.workout.start).atZone(zone)
            Session(at.toLocalDate(), at.toLocalTime(), w.workout.sportType, w.sportName, w.activeSeconds, w.zoneSeconds, w.startedByYou)
        }
        .sortedWith(compareBy({ it.date }, { it.time }))

    /* ---------------------------------------------- Load target -------------------------------------------- */

    /** What the morning says the day is for, easiest first. */
    enum class DayKind { Recover, Moderate, Usual, Push }

    /**
     * The day's Load target: a [range] of Load for a [kind] of day, in steps of [TARGET_STEP]. [personal] when the range
     * comes from your own training days, else from your usual daily Load. [fromStrap] when the strap's readiness stood
     * in for a Recovery still learning, and [scored] false when there was no morning score at all, so the day is a usual one.
     */
    data class Target(val kind: DayKind, val range: IntRange, val personal: Boolean, val fromStrap: Boolean, val scored: Boolean)

    /** Training days in the [TARGET_DAYS] before a morning needed for a target of your own. */
    const val MIN_TRAINING_DAYS = 4
    /** How far back the target looks: fitness's six weeks ([Recovery.CTL_DAYS]). */
    const val TARGET_DAYS = Recovery.CTL_DAYS
    const val TARGET_STEP = 5
    const val MIN_TARGET_WIDTH = 20
    /** The share around your usual Load that's still usual, as Today's Load card has it ([org.openstrap.app.ui.dashboard.TodayCopy.LOAD_BAND]). */
    const val USUAL_BAND = 0.25f

    /** The kind of day: an Alert or a low Recovery is for recovering, a fair one or HRV below its range for a moderate day, a high one for pushing. */
    fun kindOf(recovery: Int?, hrvRange: Recovery.HrvRange?, alert: Boolean): DayKind = when {
        alert || (recovery != null && recovery < Recovery.FAIR_FROM) -> DayKind.Recover
        (recovery != null && recovery < READY_FROM) || hrvRange == Recovery.HrvRange.Below -> DayKind.Moderate
        recovery != null && recovery >= PUSH_FROM -> DayKind.Push
        else -> DayKind.Usual
    }

    /**
     * The target for the morning [day]: its [DayKind], and a range of your own Load. With at least [MIN_TRAINING_DAYS]
     * training days ([training]) among the worn days ([loads]) of the [TARGET_DAYS] before, the range comes from their
     * Loads: Recover up to half an easy session (or a usual rest day's Load, if more), Moderate from half an easy
     * session to a middling one, Usual from an easy one to a hard one (the middle half), Push from a middling one to
     * your hardest tenth. Without them it's built round your usual daily Load, the ±[USUAL_BAND] band Today calls usual
     * for a usual day. Null before there's either.
     */
    fun target(day: Day, loads: Map<LocalDate, Float>, training: Set<LocalDate>): Target? {
        val score = day.recovery ?: day.strapRecovery
        val kind = kindOf(score, day.hrvRange, day.alert)
        val before = (1..TARGET_DAYS).mapNotNull { back -> day.date.minusDays(back.toLong()).let { d -> loads[d]?.let { d to it } } }
        val sessions = before.filter { it.first in training }.map { it.second }
        val personal = sessions.size >= MIN_TRAINING_DAYS
        val (lo, hi) = if (personal) {
            fun q(p: Float) = Recovery.percentile(sessions, p)!!
            when (kind) {
                DayKind.Recover -> 0f to max(Recovery.percentile(before.filter { it.first !in training }.map { it.second }, 0.5f) ?: 0f, 0.5f * q(0.25f))
                DayKind.Moderate -> 0.5f * q(0.25f) to q(0.5f)
                DayKind.Usual -> q(0.25f) to q(0.75f)
                DayKind.Push -> q(0.5f) to q(0.9f)
            }
        } else {
            val usual = day.usualLoad?.takeIf { it > 0f } ?: return null
            when (kind) {
                DayKind.Recover -> 0f to (1 - 2 * USUAL_BAND) * usual
                DayKind.Moderate -> (1 - 2 * USUAL_BAND) * usual to usual
                DayKind.Usual -> (1 - USUAL_BAND) * usual to (1 + USUAL_BAND) * usual
                DayKind.Push -> usual to (1 + 3 * USUAL_BAND) * usual
            }
        }
        return Target(kind, rounded(lo, hi), personal, fromStrap = day.recovery == null && day.strapRecovery != null, scored = score != null)
    }

    /** [lo]..[hi] out to whole [TARGET_STEP]s, at least [MIN_TARGET_WIDTH] wide (round its middle when sessions are all alike). */
    internal fun rounded(lo: Float, hi: Float): IntRange {
        val a = (floor(lo / TARGET_STEP) * TARGET_STEP).toInt().coerceAtLeast(0)
        val b = (ceil(hi / TARGET_STEP) * TARGET_STEP).toInt()
        if (b - a >= MIN_TARGET_WIDTH) return a..b
        val start = (Math.round(((lo + hi) / 2 - MIN_TARGET_WIDTH / 2f) / TARGET_STEP) * TARGET_STEP).coerceAtLeast(0)
        return start..start + MIN_TARGET_WIDTH
    }

    /** Where a finished day's Load landed against its target, as the Load reads (rounded). */
    enum class Versus { Below, Within, Above }

    fun versus(load: Float, target: IntRange): Versus {
        val shown = Math.round(load)
        return when {
            shown < target.first -> Versus.Below
            shown > target.last -> Versus.Above
            else -> Versus.Within
        }
    }

    /* ----------------------------------------------- Sleep plan -------------------------------------------- */

    /**
     * Tonight's sleep: [asleepMinutes] to aim for (your [needMinutes], plus [shortfallMinutes] for this week's short
     * nights, plus [loadMinutes] after a hard day, less today's [napMinutes]), and the [bedtime] that gets it by your
     * usual [wake] time for tomorrow's kind of day, given how much of your time in bed you sleep ([efficiency]). No
     * times without a wake time to go on.
     */
    data class SleepPlan(
        val asleepMinutes: Int,
        val needMinutes: Int,
        val shortfallMinutes: Int,
        val loadMinutes: Int,
        val napMinutes: Int,
        val efficiency: Float,
        val wake: LocalTime?,
        val bedtime: LocalTime?,
    )

    /** The week's average shortfall a night counts up to this much on top of your need. */
    const val MAX_SHORTFALL_MINUTES = 45
    /** After a hard day ([Recovery.hardDay]) and after one at least [VERY_HARD_RATIO]× your usual Load. */
    const val HARD_DAY_MINUTES = 15
    const val VERY_HARD_MINUTES = 30
    const val VERY_HARD_RATIO = 2f
    /** A usual Load under this counts as this, as [Recovery.hardDay] floors it, so a quiet week doesn't make any walk hard. */
    const val MIN_USUAL_LOAD = 20f
    /** A nap stands in for at most this much of the night. */
    const val MAX_NAP_CREDIT_MINUTES = 60
    const val EFFICIENCY_NIGHTS = 14
    const val DEFAULT_EFFICIENCY = 0.9f
    const val WAKE_NIGHTS = 28
    /** Wake times of tomorrow's kind of day (a weekday or the weekend) needed before only they count. */
    const val MIN_WAKE_NIGHTS = 3

    /** The plan for the night after [date], from the [days] up to it and your sleep [need] in minutes. */
    fun sleepPlan(date: LocalDate, days: Map<LocalDate, Day>, need: Int): SleepPlan {
        val today = days[date]
        // Each nap counts once: an earlier day's with that morning's night, today's against tonight.
        val short = (0 until WEEK_DAYS).mapNotNull { back ->
            days[date.minusDays(back.toLong())]?.let { d -> d.asleepMinutes?.let { max(0, need - it - if (back == 0) 0 else d.napMinutes) } }
        }
        val shortfall = if (short.isEmpty()) 0 else (Math.round(short.average() / 5) * 5).toInt().coerceAtMost(MAX_SHORTFALL_MINUTES)
        val load = today?.load
        val usual = today?.usualLoad ?: 0f
        val extra = when {
            load == null -> 0
            load > VERY_HARD_RATIO * max(usual, MIN_USUAL_LOAD) -> VERY_HARD_MINUTES
            Recovery.hardDay(load, usual) -> HARD_DAY_MINUTES
            else -> 0
        }
        val naps = today?.napMinutes ?: 0
        val asleep = (need + shortfall + extra - naps).coerceAtLeast(need - MAX_NAP_CREDIT_MINUTES)
        val recent = (0 until EFFICIENCY_NIGHTS).mapNotNull { days[date.minusDays(it.toLong())] }
        val efficiency = median(
            recent.mapNotNull { d ->
                val a = d.asleepMinutes
                val b = d.inBedMinutes
                if (a != null && b != null && b > 0 && a in 1..b) a.toFloat() / b else null
            },
        ) ?: DEFAULT_EFFICIENCY
        val wakes = (0 until WAKE_NIGHTS).mapNotNull { days[date.minusDays(it.toLong())] }.filter { it.wake != null }
        val weekend = isWeekend(date.plusDays(1))
        val alike = wakes.filter { isWeekend(it.date) == weekend }
        val wake = median((if (alike.size >= MIN_WAKE_NIGHTS) alike else wakes).map { afterNoon(it.wake!!).toFloat() })
        return SleepPlan(
            asleep, need, shortfall, extra, naps, efficiency,
            wake?.let { clockOf(Math.round(it)) },
            wake?.let { clockOf(Math.round(it - asleep / efficiency)) },
        )
    }

    private fun isWeekend(d: LocalDate) = d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY

    /* ------------------------------------------------ Figures ---------------------------------------------- */

    /**
     * A stretch of days in figures: averages a day or a night, totals for the stretch, and how steady the sleep times
     * were ([bedtimeShift], [wakeShift]: the average change from one night to the next, in minutes). [span] is how many
     * of its days come after your first day with data, which totals are per when a stretch isn't all there. A day
     * still under way ([Day.partial]) is left out of the Load and steps a day.
     */
    data class Figures(
        val span: Int,
        val recovery: Float?,
        val scored: Int,
        /** Scored days at [READY_FROM] or more. */
        val ready: Float,
        val hrv: Float?,
        val restingHr: Float?,
        val asleep: Float?,
        val restorative: Float?,
        val bedtime: LocalTime?,
        val wake: LocalTime?,
        val bedtimeShift: Float?,
        val wakeShift: Float?,
        val load: Float?,
        /** Your usual daily Load as the last day of the stretch began. */
        val usualLoad: Float?,
        val workouts: Float,
        val workoutSeconds: Float,
        val steps: Float?,
    ) {
        /** The totals as if the stretch were [days] long: an average week of three, or a month not all there. */
        fun per(days: Int): Figures {
            if (span <= 0 || span == days) return this
            val k = days.toFloat() / span
            return copy(workouts = workouts * k, workoutSeconds = workoutSeconds * k, ready = ready * k)
        }
    }

    fun figures(days: Map<LocalDate, Day>, sessions: List<Session>, from: LocalDate, to: LocalDate, firstData: LocalDate?): Figures {
        val inside = dates(from, to).mapNotNull { days[it] }
        val scored = inside.mapNotNull { it.recovery }
        val done = inside.filter { !it.partial }
        val span = if (firstData == null || firstData > to) 0 else (ChronoUnit.DAYS.between(maxOf(from, firstData), to) + 1).toInt()
        val workouts = sessions.filter { it.date in from..to }
        return Figures(
            span = span,
            recovery = scored.map { it.toFloat() }.averageOrNull(),
            scored = scored.size,
            ready = scored.count { it >= READY_FROM }.toFloat(),
            hrv = inside.mapNotNull { it.hrv }.averageOrNull(),
            restingHr = inside.mapNotNull { it.restingHr }.averageOrNull(),
            asleep = inside.mapNotNull { it.asleepMinutes?.toFloat() }.averageOrNull(),
            restorative = inside.mapNotNull { it.restorativeMinutes?.toFloat() }.averageOrNull(),
            bedtime = median(inside.mapNotNull { it.bedtime?.let { t -> afterNoon(t).toFloat() } })?.let { clockOf(Math.round(it)) },
            wake = median(inside.mapNotNull { it.wake?.let { t -> afterNoon(t).toFloat() } })?.let { clockOf(Math.round(it)) },
            bedtimeShift = shift(inside) { it.bedtime },
            wakeShift = shift(inside) { it.wake },
            load = done.mapNotNull { it.load }.averageOrNull(),
            usualLoad = inside.lastOrNull { it.usualLoad != null }?.usualLoad,
            workouts = workouts.size.toFloat(),
            workoutSeconds = workouts.sumOf { it.seconds }.toFloat(),
            steps = done.mapNotNull { it.steps?.takeIf { s -> s > 0 }?.toFloat() }.averageOrNull(),
        )
    }

    /** The average change in [time] between consecutive mornings that both have one, in minutes; null without a pair. */
    private fun shift(days: List<Day>, time: (Day) -> LocalTime?): Float? = days.zipWithNext().mapNotNull { (a, b) ->
        val x = time(a)
        val y = time(b)
        if (b.date != a.date.plusDays(1) || x == null || y == null) null else abs(afterNoon(y) - afterNoon(x)).toFloat()
    }.averageOrNull()

    /** A sport in a stretch: its sessions, time and zones, and its sessions in the stretch before, scaled to the same length. */
    data class SportTotals(val sport: Int, val name: String, val sessions: Int, val seconds: Long, val zoneSeconds: List<Int>, val sessionsBefore: Float?)

    private fun sports(sessions: List<Session>, from: LocalDate, to: LocalDate, before: ClosedRange<LocalDate>?, scale: Float): List<SportTotals> {
        val now = sessions.filter { it.date in from..to }.groupBy { it.sport }
        val earlier = before?.let { r -> sessions.filter { it.date in r }.groupingBy { it.sport }.eachCount() }
        return now.map { (sport, list) ->
            SportTotals(
                sport, list.first().name, list.size, list.sumOf { it.seconds },
                (0 until Workouts.ZONES).map { z -> list.sumOf { it.zoneSeconds.getOrElse(z) { 0 } } },
                earlier?.let { (it[sport] ?: 0) * scale },
            )
        }.sortedWith(compareByDescending<SportTotals> { it.sessions }.thenByDescending { it.seconds })
    }

    /* -------------------------------------------------- Week ----------------------------------------------- */

    /** How many finished days landed below, within and above their target. Averaged weeks have fractions. */
    data class Balance(val below: Float, val within: Float, val above: Float) {
        val days: Float get() = below + within + above
    }

    private fun balance(versus: List<Versus?>) = Balance(
        versus.count { it == Versus.Below }.toFloat(), versus.count { it == Versus.Within }.toFloat(), versus.count { it == Versus.Above }.toFloat(),
    )

    /**
     * The last [WEEK_DAYS] days ending on [end] against the [WEEKS_BEFORE] weeks before them as one average week ([before]
     * is null with less than a week of them): the [days] with each morning's [targets] and where each finished day's Load
     * landed ([versus]), and the week's sports. [normal] is your normal Recovery over the last [MONTH_DAYS] days ([normal]).
     */
    data class Week(
        val now: Figures,
        val before: Figures?,
        val days: List<Day>,
        val targets: List<Target?>,
        val versus: List<Versus?>,
        val balance: Balance,
        val balanceBefore: Balance?,
        val sports: List<SportTotals>,
        val normal: ClosedFloatingPointRange<Float>?,
    )

    fun week(end: LocalDate, days: Map<LocalDate, Day>, sessions: List<Session>, targets: Map<LocalDate, Target?>, firstData: LocalDate?): Week {
        val from = end.minusDays(WEEK_DAYS - 1L)
        val beforeFrom = from.minusDays(WEEK_DAYS.toLong() * WEEKS_BEFORE)
        val beforeTo = from.minusDays(1)
        val earlier = figures(days, sessions, beforeFrom, beforeTo, firstData)
        val before = earlier.takeIf { it.span >= WEEK_DAYS }
        val week = dates(from, end).map { days[it] ?: Day(it) }
        fun versusOf(d: Day) = d.load?.takeIf { !d.partial }?.let { l -> targets[d.date]?.let { versus(l, it.range) } }
        val scale = before?.let { WEEK_DAYS.toFloat() / it.span } ?: 1f
        return Week(
            now = figures(days, sessions, from, end, firstData),
            before = before?.per(WEEK_DAYS),
            days = week,
            targets = week.map { targets[it.date] },
            versus = week.map(::versusOf),
            balance = balance(week.map(::versusOf)),
            balanceBefore = before?.let {
                val b = balance(dates(beforeFrom, beforeTo).map { d -> days[d]?.let(::versusOf) })
                Balance(b.below * scale, b.within * scale, b.above * scale)
            },
            sports = sports(sessions, from, end, if (before != null) beforeFrom..beforeTo else null, scale),
            normal = normal(dates(end.minusDays(MONTH_DAYS - 1L), end).mapNotNull { days[it]?.recovery?.toFloat() }),
        )
    }

    /* -------------------------------------------------- Month ---------------------------------------------- */

    /** A day of the week over the reports' days: average Recovery, Load and sleep, and the days behind them. */
    data class Weekday(val day: DayOfWeek, val recovery: Float?, val load: Float?, val asleep: Float?, val days: Int)

    /**
     * The last [MONTH_DAYS] days ending on [end] against the [MONTH_DAYS] before them ([before] null with under two weeks of
     * them): the [days], Recovery's spread over its four bands ([bands], lowest first: under [Recovery.FAIR_FROM], then
     * to [READY_FROM], to [PUSH_FROM], and above), each day of the week over all the reports' days, and the sports with
     * their zones. [normal], [hrvNormal] and [restingHrNormal] are your normal Recovery, HRV and resting heart rate over
     * the month ([normal]), as the metric details draw theirs.
     */
    data class Month(
        val now: Figures,
        val before: Figures?,
        val days: List<Day>,
        val bands: List<Int>,
        val bandsBefore: List<Int>?,
        val weekdays: List<Weekday>,
        val sports: List<SportTotals>,
        val normal: ClosedFloatingPointRange<Float>?,
        val hrvNormal: ClosedFloatingPointRange<Float>?,
        val restingHrNormal: ClosedFloatingPointRange<Float>?,
    )

    /** Days of the month before needed for the month to be compared with it. */
    const val MIN_MONTH_BEFORE = 14

    fun month(end: LocalDate, days: Map<LocalDate, Day>, sessions: List<Session>, firstData: LocalDate?): Month {
        val from = end.minusDays(MONTH_DAYS - 1L)
        val beforeTo = from.minusDays(1)
        val beforeFrom = from.minusDays(MONTH_DAYS.toLong())
        val earlier = figures(days, sessions, beforeFrom, beforeTo, firstData)
        val before = earlier.takeIf { it.span >= MIN_MONTH_BEFORE }
        val month = dates(from, end).map { days[it] ?: Day(it) }
        fun bands(list: List<Day>) = list.mapNotNull { it.recovery }.let { s ->
            listOf(s.count { it < Recovery.FAIR_FROM }, s.count { it in Recovery.FAIR_FROM until READY_FROM }, s.count { it in READY_FROM until PUSH_FROM }, s.count { it >= PUSH_FROM })
        }
        val all = days.values.filter { it.date <= end }
        return Month(
            now = figures(days, sessions, from, end, firstData),
            before = before?.per(MONTH_DAYS),
            days = month,
            bands = bands(month),
            bandsBefore = before?.let { bands(dates(beforeFrom, beforeTo).mapNotNull { days[it] }) },
            weekdays = DayOfWeek.entries.map { wd ->
                val on = all.filter { it.date.dayOfWeek == wd }
                Weekday(
                    wd, on.mapNotNull { it.recovery?.toFloat() }.averageOrNull(), on.filter { !it.partial }.mapNotNull { it.load }.averageOrNull(),
                    on.mapNotNull { it.asleepMinutes?.toFloat() }.averageOrNull(), on.count { it.recovery != null },
                )
            },
            sports = sports(sessions, from, end, if (before != null) beforeFrom..beforeTo else null, before?.let { MONTH_DAYS.toFloat() / it.span } ?: 1f),
            normal = normal(month.mapNotNull { it.recovery?.toFloat() }),
            hrvNormal = normal(month.mapNotNull { it.hrv }),
            restingHrNormal = normal(month.mapNotNull { it.restingHr }),
        )
    }

    /**
     * Your normal over [values], one a day: the mean ± one standard deviation, as a metric's normal is worked out
     * ([History.normal]). Null with under [History.MIN_NORMAL_DAYS] of them.
     */
    fun normal(values: List<Float>): ClosedFloatingPointRange<Float>? {
        if (values.size < History.MIN_NORMAL_DAYS) return null
        val mean = values.average()
        val sd = sqrt(values.sumOf { (it - mean) * (it - mean) } / (values.size - 1))
        return (mean - sd).toFloat()..(mean + sd).toFloat()
    }

    /* ------------------------------------------------- Habits ---------------------------------------------- */

    /**
     * Something you did, found in your data (the app keeps no journal), whose next mornings are compared with the rest.
     * Each is about the day before the morning, except the night's own [LateBedtime] and [ShortNight].
     */
    sealed interface Habit {
        /** Any workout. */
        data object Workout : Habit
        /** One of the sports you do most, when you do more than one. */
        data class Sport(val sport: Int, val name: String) : Habit
        /** A workout that started at [LATE_WORKOUT_HOUR] or later. */
        data object LateWorkout : Habit
        /** A Load above [Recovery.HARD_DAY_RATIO]× your usual: the hard day the body signals note as a likely cause. */
        data object HardDay : Habit
        /** To bed [LATE_BEDTIME_MINUTES] or more after your usual bedtime. */
        data object LateBedtime : Habit
        /** Under [SHORT_NIGHT_MINUTES] asleep. */
        data object ShortNight : Habit
        /** A Friday or Saturday night. */
        data object Weekend : Habit
        /** Steps in your top quarter of days. */
        data object ActiveDay : Habit
    }

    /** How sure the reports are of an effect: too few mornings to say, no clear effect, likely, or clear. */
    enum class Verdict { TooFew, Unclear, Likely, Clear }

    /**
     * What a [habit] did to the next morning, over the [mornings] after it and the [others]: Recovery after it and
     * without it on average ([recoveryAfter], [recoveryOthers]), and the difference it makes in Recovery ([recovery],
     * points) and in HRV against its baseline ([hrv], percentage points), each with how sure that is. When [adjusted],
     * the differences are with the other habits held steady (a fit over all of them, [ols]), so a habit that only
     * keeps company with another doesn't take its effect; else they're the plain differences ([permutationP]). [with]
     * lists the habits that mostly come with it, whose effects can't be told apart from its own.
     */
    data class Effect(
        val habit: Habit,
        val mornings: Int,
        val others: Int,
        val recoveryAfter: Float?,
        val recoveryOthers: Float?,
        val recovery: Float?,
        val recoveryVerdict: Verdict,
        val hrv: Float?,
        val hrvVerdict: Verdict,
        val adjusted: Boolean,
        val with: List<Habit>,
    ) {
        val verdict: Verdict get() = maxOf(recoveryVerdict, hrvVerdict)
    }

    /** Mornings needed both after a habit and without it before it's compared. */
    const val MIN_HABIT_DAYS = 4
    const val LATE_WORKOUT_HOUR = 20
    const val LATE_BEDTIME_MINUTES = 45
    const val SHORT_NIGHT_MINUTES = 7 * 60
    const val ACTIVE_DAY_SHARE = 0.75f
    const val MAX_SPORT_HABITS = 2
    /** Clear: p under [CLEAR_P] and at least [CLEAR_POINTS] points (or %); likely: under [LIKELY_P] and [LIKELY_POINTS]. */
    const val CLEAR_P = 0.05f
    const val CLEAR_POINTS = 3f
    const val LIKELY_P = 0.1f
    const val LIKELY_POINTS = 2f
    /** Two habits this correlated (phi) mostly happen together. */
    const val TOGETHER_PHI = 0.6f
    const val PERMUTATIONS = 2000
    /** Fixed, so the same days always give the same answer. */
    const val PERMUTATION_SEED = 7
    /** Mornings needed before the habits are fitted together, and the degrees of freedom the fit must leave. */
    const val MIN_FIT_MORNINGS = 20
    const val MIN_FIT_DF = 10

    /**
     * Each habit's effect on the next morning over the scored mornings of [days] whose day before is on record, the
     * clearest first. Sport habits are the [MAX_SPORT_HABITS] sports you did most on days before a scored morning, only
     * when you do more than one sport. The habits are fitted together, all but "a workout", which every sport and late
     * workout is part of: its effect stays the plain difference.
     */
    fun habits(days: List<Day>, sessions: List<Session>): List<Effect> {
        val byDate = days.associateBy { it.date }
        val sessionsOn = sessions.groupBy { it.date }
        val mornings = days.filter { it.recovery != null }.mapNotNull { m -> byDate[m.date.minusDays(1)]?.let { m to it } }
        if (mornings.isEmpty()) return emptyList()
        val usualBed = median(days.mapNotNull { it.bedtime?.let { t -> afterNoon(t).toFloat() } })
        val activeFrom = Recovery.percentile(days.filter { !it.partial }.mapNotNull { it.steps?.takeIf { s -> s > 0 }?.toFloat() }, ACTIVE_DAY_SHARE)
        val before = mornings.flatMap { (_, p) -> sessionsOn[p.date].orEmpty() }
        val sports = before.groupBy { it.sport }.takeIf { it.size > 1 }.orEmpty()
            .map { (sport, list) -> Triple(sport, list.first().name, list.map { it.date }.distinct().size) }
            .filter { it.third >= MIN_HABIT_DAYS }
            .sortedByDescending { it.third }.take(MAX_SPORT_HABITS)
            .map { Habit.Sport(it.first, it.second) }
        val habits = listOf(Habit.Workout) + sports +
            listOf(Habit.LateWorkout, Habit.HardDay, Habit.LateBedtime, Habit.ShortNight, Habit.Weekend, Habit.ActiveDay)

        fun did(h: Habit, m: Day, p: Day): Boolean {
            val workouts = sessionsOn[p.date].orEmpty()
            return when (h) {
                Habit.Workout -> workouts.isNotEmpty()
                is Habit.Sport -> workouts.any { it.sport == h.sport }
                Habit.LateWorkout -> workouts.any { it.time.hour >= LATE_WORKOUT_HOUR }
                Habit.HardDay -> p.load?.let { Recovery.hardDay(it, p.usualLoad ?: 0f) } == true
                Habit.LateBedtime -> usualBed != null && m.bedtime?.let { afterNoon(it) - usualBed >= LATE_BEDTIME_MINUTES } == true
                Habit.ShortNight -> m.asleepMinutes?.let { it < SHORT_NIGHT_MINUTES } == true
                Habit.Weekend -> m.date.dayOfWeek == DayOfWeek.SATURDAY || m.date.dayOfWeek == DayOfWeek.SUNDAY
                Habit.ActiveDay -> activeFrom != null && p.steps?.let { it >= activeFrom } == true
            }
        }

        val flags = habits.associateWith { h -> mornings.map { (m, p) -> did(h, m, p) } }
        val fitted = habits.filter { h -> h != Habit.Workout && flags.getValue(h).count { it }.let { it >= MIN_HABIT_DAYS && it <= mornings.size - MIN_HABIT_DAYS } }
        val recoveryFit = fit(fitted, flags, mornings.map { it.first.recovery!!.toDouble() })
        val hrvRows = mornings.indices.filter { mornings[it].first.hrvVsUsual != null }
        val hrvFit = fit(fitted, flags.mapValues { (_, f) -> hrvRows.map { f[it] } }, hrvRows.map { mornings[it].first.hrvVsUsual!!.toDouble() })
        val random = Random(PERMUTATION_SEED)
        return habits.map { h ->
            val f = flags.getValue(h)
            val yes = mornings.filterIndexed { i, _ -> f[i] }.map { it.first }
            val no = mornings.filterIndexed { i, _ -> !f[i] }.map { it.first }
            val yesRecovery = yes.map { it.recovery!!.toFloat() }
            val noRecovery = no.map { it.recovery!!.toFloat() }
            val tooFew = yes.size < MIN_HABIT_DAYS || no.size < MIN_HABIT_DAYS
            val recovery = recoveryFit[h]?.takeIf { !tooFew }
            val hrv = hrvFit[h]?.takeIf { !tooFew }
            val (plainRecovery, plainRecoveryVerdict) = compare(yesRecovery, noRecovery, random)
            val (plainHrv, plainHrvVerdict) = compare(yes.mapNotNull { it.hrvVsUsual?.toFloat() }, no.mapNotNull { it.hrvVsUsual?.toFloat() }, random)
            // "A workout" comes with every sport and late workout by definition, so it's left out of their company.
            val with = habits.filter { o -> o != h && h != Habit.Workout && o != Habit.Workout && (phi(f, flags.getValue(o)) ?: 0f) >= TOGETHER_PHI }
            Effect(
                h, yes.size, no.size, yesRecovery.averageOrNull(), noRecovery.averageOrNull(),
                recovery?.first ?: plainRecovery, recovery?.let { verdict(it.first, it.second) } ?: plainRecoveryVerdict,
                hrv?.first ?: plainHrv, hrv?.let { verdict(it.first, it.second) } ?: plainHrvVerdict,
                adjusted = recovery != null, with = with,
            )
        }.sortedWith(compareByDescending<Effect> { it.verdict }.thenByDescending { abs(it.recovery ?: 0f) })
    }

    /**
     * Each of [habits]' effect on [y] (one value a morning, the [flags] for the same mornings) with the others held
     * steady, and its p-value: an [ols] fit over all of them. A habit that's a copy of one fitted before it (late
     * workouts that are all football) gets that one's effect, as the two can't be told apart. Empty without enough
     * mornings for the fit.
     */
    private fun fit(habits: List<Habit>, flags: Map<Habit, List<Boolean>>, y: List<Double>): Map<Habit, Pair<Float, Float>> {
        if (habits.isEmpty() || y.size < MIN_FIT_MORNINGS) return emptyMap()
        val columns = habits.map { h -> flags.getValue(h).map { if (it) 1.0 else 0.0 }.toDoubleArray() }
        val result = ols(columns, y.toDoubleArray()) ?: return emptyMap()
        return habits.indices.mapNotNull { j ->
            val k = if (result.coefficients[j] != null) j else (0 until j).firstOrNull { flags.getValue(habits[it]) == flags.getValue(habits[j]) && result.coefficients[it] != null }
            k?.let { habits[j] to (result.coefficients[it]!!.toFloat() to result.p[it]!!.toFloat()) }
        }.toMap()
    }

    private fun verdict(effect: Float, p: Float): Verdict = when {
        p < CLEAR_P && abs(effect) >= CLEAR_POINTS -> Verdict.Clear
        p < LIKELY_P && abs(effect) >= LIKELY_POINTS -> Verdict.Likely
        else -> Verdict.Unclear
    }

    /** The difference in means, [yes] less [no], and its [Verdict] by [permutationP]; none with under [MIN_HABIT_DAYS] of either. */
    private fun compare(yes: List<Float>, no: List<Float>, random: Random): Pair<Float?, Verdict> {
        if (yes.size < MIN_HABIT_DAYS || no.size < MIN_HABIT_DAYS) return null to Verdict.TooFew
        val effect = (yes.average() - no.average()).toFloat()
        return effect to verdict(effect, permutationP(yes, no, random))
    }

    /** A least-squares fit: each column's coefficient and two-sided p-value, null for a column that added nothing. */
    class Fit(val coefficients: List<Double?>, val p: List<Double?>)

    /**
     * Ordinary least squares of [y] on an intercept and [columns], with each coefficient's t-test. A column that's
     * constant, or made of the ones before it, adds nothing and is left out (null), so the fit always solves. Null when
     * fewer than [MIN_FIT_DF] residual degrees of freedom are left.
     */
    fun ols(columns: List<DoubleArray>, y: DoubleArray): Fit? {
        val n = y.size
        // Gram-Schmidt over the intercept and the columns in order, keeping each that brings something new.
        val basis = mutableListOf(DoubleArray(n) { 1.0 / sqrt(n.toDouble()) })
        val kept = mutableListOf<Int>()
        columns.forEachIndexed { j, c ->
            val r = c.copyOf()
            for (q in basis) {
                val d = dot(q, r)
                for (i in 0 until n) r[i] -= d * q[i]
            }
            val norm = sqrt(dot(r, r))
            if (norm > 1e-6 * max(1.0, sqrt(dot(c, c)))) {
                basis += DoubleArray(n) { r[it] / norm }
                kept += j
            }
        }
        val p = kept.size + 1
        val df = n - p
        if (df < MIN_FIT_DF) return null
        val x = listOf(DoubleArray(n) { 1.0 }) + kept.map { columns[it] }
        val inverse = invert(Array(p) { a -> DoubleArray(p) { b -> dot(x[a], x[b]) } }) ?: return null
        val xty = DoubleArray(p) { dot(x[it], y) }
        val beta = DoubleArray(p) { a -> (0 until p).sumOf { b -> inverse[a][b] * xty[b] } }
        val rss = (0 until n).sumOf { i ->
            val e = y[i] - (0 until p).sumOf { a -> beta[a] * x[a][i] }
            e * e
        }
        val s2 = rss / df
        val coefficients = MutableList<Double?>(columns.size) { null }
        val pValues = MutableList<Double?>(columns.size) { null }
        kept.forEachIndexed { k, j ->
            val b = beta[k + 1]
            val se = sqrt(s2 * inverse[k + 1][k + 1])
            coefficients[j] = b
            pValues[j] = if (se > 0) tTwoSided(b / se, df) else if (b == 0.0) 1.0 else 0.0
        }
        return Fit(coefficients, pValues)
    }

    private fun dot(a: DoubleArray, b: DoubleArray): Double {
        var s = 0.0
        for (i in a.indices) s += a[i] * b[i]
        return s
    }

    /** [m]'s inverse by Gauss-Jordan elimination with partial pivoting; null when it's singular. */
    private fun invert(m: Array<DoubleArray>): Array<DoubleArray>? {
        val n = m.size
        val a = Array(n) { i -> DoubleArray(2 * n) { j -> if (j < n) m[i][j] else if (j - n == i) 1.0 else 0.0 } }
        for (c in 0 until n) {
            val pivot = (c until n).maxBy { abs(a[it][c]) }
            if (abs(a[pivot][c]) < 1e-12) return null
            val t = a[c]
            a[c] = a[pivot]
            a[pivot] = t
            val d = a[c][c]
            for (j in 0 until 2 * n) a[c][j] /= d
            for (r in 0 until n) if (r != c) {
                val f = a[r][c]
                if (f != 0.0) for (j in 0 until 2 * n) a[r][j] -= f * a[c][j]
            }
        }
        return Array(n) { i -> DoubleArray(n) { j -> a[i][n + j] } }
    }

    /** The two-sided p-value of Student's t with [df] degrees of freedom: the regularized incomplete beta I_x(df/2, 1/2), x = df/(df + t²). */
    fun tTwoSided(t: Double, df: Int): Double = regularizedBeta(df / (df + t * t), df / 2.0, 0.5)

    private fun regularizedBeta(x: Double, a: Double, b: Double): Double {
        if (x <= 0.0) return 0.0
        if (x >= 1.0) return 1.0
        val front = exp(lnGamma(a + b) - lnGamma(a) - lnGamma(b) + a * ln(x) + b * ln(1 - x))
        return if (x < (a + 1) / (a + b + 2)) front * betaFraction(x, a, b) / a else 1 - front * betaFraction(1 - x, b, a) / b
    }

    /** The incomplete beta function's continued fraction, by the modified Lentz method. */
    private fun betaFraction(x: Double, a: Double, b: Double): Double {
        val tiny = 1e-30
        fun nz(v: Double) = if (abs(v) < tiny) tiny else v
        var c = 1.0
        var d = 1 / nz(1 - (a + b) * x / (a + 1))
        var h = d
        for (m in 1..300) {
            val m2 = 2 * m
            var aa = m * (b - m) * x / ((a + m2 - 1) * (a + m2))
            d = 1 / nz(1 + aa * d)
            c = nz(1 + aa / c)
            h *= d * c
            aa = -(a + m) * (a + b + m) * x / ((a + m2) * (a + m2 + 1))
            d = 1 / nz(1 + aa * d)
            c = nz(1 + aa / c)
            val step = d * c
            h *= step
            if (abs(step - 1) < 1e-12) break
        }
        return h
    }

    /** ln Γ(x) for x ≥ 0.5, by Lanczos's approximation (g = 7). */
    private fun lnGamma(x: Double): Double {
        val g = doubleArrayOf(
            0.99999999999980993, 676.5203681218851, -1259.1392167224028, 771.32342877765313, -176.61502916214059,
            12.507343278686905, -0.13857109526572012, 9.9843695780195716e-6, 1.5056327351493116e-7,
        )
        val z = x - 1
        var a = g[0]
        for (i in 1 until g.size) a += g[i] / (z + i)
        val t = z + 7.5
        return 0.5 * ln(2 * Math.PI) + (z + 0.5) * ln(t) - t + ln(a)
    }

    /**
     * Two-sided permutation test for a difference in means: the share of [rounds] random splits of the pooled values into
     * groups the sizes of [yes] and [no] whose difference is at least as large as the real one, counting the real split
     * (so never 0). Each round draws the first group by a partial Fisher-Yates shuffle, which picks a uniformly random
     * group of its size whatever order the pool is left in.
     */
    fun permutationP(yes: List<Float>, no: List<Float>, random: Random, rounds: Int = PERMUTATIONS): Float {
        val pool = (yes + no).map { it.toDouble() }.toDoubleArray()
        val n = pool.size
        val k = yes.size
        if (k == 0 || k == n) return 1f
        val total = pool.sum()
        val observed = abs(yes.average() - no.average())
        var hits = 0
        repeat(rounds) {
            var sum = 0.0
            for (i in 0 until k) {
                val j = i + random.nextInt(n - i)
                val t = pool[i]
                pool[i] = pool[j]
                pool[j] = t
                sum += pool[i]
            }
            if (abs(sum / k - (total - sum) / (n - k)) >= observed - 1e-9) hits++
        }
        return (hits + 1f) / (rounds + 1f)
    }

    /** The phi coefficient of two yes/no series of the same mornings; null when either never or always happens. */
    internal fun phi(a: List<Boolean>, b: List<Boolean>): Float? {
        var n11 = 0
        var n10 = 0
        var n01 = 0
        var n00 = 0
        for (i in a.indices) when {
            a[i] && b[i] -> n11++
            a[i] -> n10++
            b[i] -> n01++
            else -> n00++
        }
        val den = sqrt((n11 + n10).toDouble() * (n01 + n00) * (n11 + n01) * (n10 + n00))
        return if (den == 0.0) null else ((n11.toDouble() * n00 - n10.toDouble() * n01) / den).toFloat()
    }

    /* ------------------------------------------------- Report ---------------------------------------------- */

    /**
     * Everything the Reports screen shows for the day [date]: the [week] and [month] ending on it, that morning's Load
     * [target] and the day's [load] (so far, unless [finished]), the [plan] for the night after it, and the [habits].
     * [daysWithData] and [scoredDays] count the reports' days with any data and with a Recovery.
     */
    data class Report(
        val date: LocalDate,
        val week: Week,
        val month: Month,
        val target: Target?,
        val load: Float?,
        val finished: Boolean,
        val plan: SleepPlan,
        val habits: List<Effect>,
        val daysWithData: Int,
        val scoredDays: Int,
    )

    /** The report from the engine's [input], your [sessions], your sleep need in minutes, and [today]'s date. */
    fun build(input: Input, sessions: List<Session>, sleepNeed: Int, today: LocalDate): Report {
        val date = input.date
        val days = input.days.associateBy { it.date }
        val training = sessions.filter { it.byYou }.mapTo(HashSet()) { it.date }
        val targets = input.days.associate { it.date to target(it, input.loads, training) }
        val firstData = (input.days.filter { it.hasData }.map { it.date } + input.loads.keys).minOrNull()
        val shown = input.days.filter { it.date <= date }
        return Report(
            date = date,
            week = week(date, days, sessions, targets, firstData),
            month = month(date, days, sessions, firstData),
            target = targets[date],
            load = days[date]?.load,
            finished = date < today,
            plan = sleepPlan(date, days, sleepNeed),
            habits = habits(shown, sessions),
            daysWithData = shown.count { it.hasData },
            scoredDays = shown.count { it.recovery != null },
        )
    }

    /* ------------------------------------------------- Helpers --------------------------------------------- */

    /** Minutes from local noon to [t], so a night's times never wrap round midnight. */
    fun afterNoon(t: LocalTime): Int = ((t.toSecondOfDay() / 60 - 720) % 1440 + 1440) % 1440

    /** The clock time [minutes] after local noon, wrapping round the day. */
    fun clockOf(minutes: Int): LocalTime = LocalTime.of(0, 0).plusMinutes(((minutes + 720) % 1440 + 1440) % 1440L)

    private fun dates(from: LocalDate, to: LocalDate): List<LocalDate> =
        if (to < from) emptyList() else (0..ChronoUnit.DAYS.between(from, to)).map { from.plusDays(it) }

    private fun List<Float>.averageOrNull(): Float? = if (isEmpty()) null else average().toFloat()

    private fun median(values: List<Float>): Float? = Recovery.percentile(values, 0.5f)
}
