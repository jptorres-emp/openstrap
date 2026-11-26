package org.openstrap.app.data

import java.time.DayOfWeek
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

/** A morning and the day before it. */
private typealias DayPair = Pair<ForkReports.Day, ForkReports.Day>

/**
 * What a habit does to the next morning, for the fork's reports (jptorres-emp/openstrap), as WHOOP's journal reads it:
 * the HRV of the mornings after a day with it against the mornings after a day without it, in percent, with a
 * permutation test for how sure that is. HRV rather than Recovery, so a habit isn't scored by what Recovery already
 * counts (a short night's sleep, a hard day's strain). The journal's habits count only the days that were answered.
 * Pure, so it's unit tested.
 */
object ForkHabits {
    /** How sure an effect is: too few mornings to say, no clear effect, likely, or clear. */
    enum class Verdict { TooFew, Unclear, Likely, Clear }

    /** Something done the day before a morning: ticked in the journal ([Logged]) or found in the data. */
    sealed interface Habit {
        data class Logged(val habit: JournalHabit) : Habit

        /** A workout of this sport type. */
        data class Sport(val sport: Int) : Habit

        /** Any workout. */
        data object Workout : Habit

        /** A workout that started at 20:00 or later. */
        data object LateWorkout : Habit

        /** A Load at least [Recovery.HARD_DAY_RATIO] times your usual. */
        data object HardDay : Habit

        /** Steps at least [ACTIVE_RATIO] times your usual day's. */
        data object ActiveDay : Habit

        /** Under [SHORT_NIGHT_MINUTES] asleep the night before the morning. */
        data object ShortNight : Habit

        /** The night before a Saturday or a Sunday. */
        data object WeekendNight : Habit
    }

    /**
     * What a [habit] did over the [mornings] after it and the [others]: HRV after it and without it on average, the
     * difference in [percent] of the mornings without it, with its [verdict], and the habit it most often came [along]
     * with, when most of its days had that one too.
     */
    data class Effect(
        val habit: Habit,
        val mornings: Int,
        val others: Int,
        val after: Float?,
        val without: Float?,
        val percent: Float?,
        val verdict: Verdict,
        val along: Habit? = null,
    )

    /** Mornings needed both after a habit and without it before it's compared. */
    const val MIN_DAYS = 4
    const val SHORT_NIGHT_MINUTES = 7 * 60
    /** A usual Load under this counts as this, so a quiet stretch doesn't make every walk a hard day. */
    const val MIN_USUAL_LOAD = 20f
    const val ACTIVE_RATIO = 1.3f
    /** Clear: p under [CLEAR_P] and at least [CLEAR_PERCENT] percent; likely: under [LIKELY_P] and [LIKELY_PERCENT]. */
    const val CLEAR_P = 0.05f
    const val CLEAR_PERCENT = 3f
    const val LIKELY_P = 0.1f
    const val LIKELY_PERCENT = 2f
    /** A habit came along with another when at least this share of its days had that one too. */
    const val ALONG_SHARE = 0.6f
    const val PERMUTATIONS = 2000
    /** Fixed, so the same days always give the same answer. */
    const val SEED = 7
    private const val PERCENT = 100f
    private const val MIDDLE = 0.5f
    private const val EPSILON = 1e-9

    private class Candidate(val habit: Habit, val pairs: List<DayPair>, val did: Set<Int>)

    /**
     * Each habit's effect over the mornings of [days] with an HRV whose day before is there: the journal's habits ticked
     * at least once, over the answered days, each sport done on at least [MIN_DAYS] days, then any workout, the late
     * workout, the hard and the active day, the short night and the weekend night. The clearest first.
     */
    fun effects(days: List<ForkReports.Day>, usualLoad: Float?): List<Effect> {
        val byDate = days.associateBy { it.date }
        val pairs = days.filter { it.hrv != null }.mapNotNull { m -> byDate[m.date.minusDays(1)]?.let { m to it } }
        val random = Random(SEED)
        val candidates = candidates(pairs, days, usualLoad)
        return candidates.map { c -> effect(c, random).copy(along = along(c, candidates)) }
            .sortedWith(compareByDescending<Effect> { it.verdict }.thenByDescending { abs(it.percent ?: 0f) })
    }

    private fun candidates(pairs: List<DayPair>, days: List<ForkReports.Day>, usualLoad: Float?): List<Candidate> {
        val answered = pairs.filter { it.second.journal != null }
        val hard = Recovery.HARD_DAY_RATIO * max(usualLoad ?: 0f, MIN_USUAL_LOAD)
        val usualSteps = Recovery.percentile(days.filter { !it.partial }.mapNotNull { d -> d.steps?.takeIf { it > 0 }?.toFloat() }, MIDDLE)
        fun of(habit: Habit, from: List<DayPair>, test: (ForkReports.Day, ForkReports.Day) -> Boolean) =
            Candidate(habit, from, from.indices.filter { test(from[it].first, from[it].second) }.toSet())
        val logged = JournalHabit.entries.filter { h -> answered.any { h in it.second.journal.orEmpty() } }
            .map { h -> of(Habit.Logged(h), answered) { _, before -> h in before.journal.orEmpty() } }
        val sports = pairs.flatMap { p -> p.second.sessions.map { it.sport }.distinct() }.groupingBy { it }.eachCount()
            .filterValues { it >= MIN_DAYS }.keys.sorted()
            .map { s -> of(Habit.Sport(s), pairs) { _, before -> before.sessions.any { it.sport == s } } }
        val found = listOf(
            of(Habit.Workout, pairs) { _, before -> before.workouts > 0 },
            of(Habit.LateWorkout, pairs) { _, before -> before.lateWorkout },
            of(Habit.HardDay, pairs) { _, before -> before.load?.let { it >= hard } == true },
            of(Habit.ActiveDay, pairs) { _, before -> usualSteps != null && (before.steps ?: 0) >= ACTIVE_RATIO * usualSteps },
            of(Habit.ShortNight, pairs) { morning, _ -> morning.asleepMinutes?.let { it < SHORT_NIGHT_MINUTES } == true },
            of(Habit.WeekendNight, pairs) { morning, _ -> morning.date.dayOfWeek in WEEKEND },
        )
        return logged + sports + found
    }

    private fun effect(c: Candidate, random: Random): Effect {
        val after = c.did.mapNotNull { c.pairs[it].first.hrv }
        val others = c.pairs.indices.filter { it !in c.did }.mapNotNull { c.pairs[it].first.hrv }
        val (percent, verdict) = compare(after, others, random)
        return Effect(c.habit, after.size, others.size, after.averageOrNull(), others.averageOrNull(), percent, verdict)
    }

    /**
     * The habit [c] most often came with: of the others found in the data, the one on the most of its days, when that's
     * at least [ALONG_SHARE] of them. Any workout is left out, as every sport and late workout is one.
     */
    private fun along(c: Candidate, all: List<Candidate>): Habit? {
        if (c.did.isEmpty() || c.habit == Habit.Workout) return null
        val days = c.did.map { c.pairs[it].second.date }.toSet()
        return all.filter { it.habit != c.habit && it.habit != Habit.Workout && it.habit !is Habit.Logged }
            .map { o -> o.habit to o.did.count { o.pairs[it].second.date in days }.toFloat() / days.size }
            .filter { it.second >= ALONG_SHARE }
            .maxByOrNull { it.second }?.first
    }

    /** The difference in mean HRV, [yes] against [no] in percent, and its [Verdict]; none with under [MIN_DAYS] of either. */
    private fun compare(yes: List<Float>, no: List<Float>, random: Random): Pair<Float?, Verdict> {
        val base = no.average()
        if (yes.size < MIN_DAYS || no.size < MIN_DAYS || base <= 0) return null to Verdict.TooFew
        val percent = ((yes.average() - base) / base * PERCENT).toFloat()
        val p = permutationP(yes, no, random)
        val verdict = when {
            p < CLEAR_P && abs(percent) >= CLEAR_PERCENT -> Verdict.Clear
            p < LIKELY_P && abs(percent) >= LIKELY_PERCENT -> Verdict.Likely
            else -> Verdict.Unclear
        }
        return percent to verdict
    }

    /** A two-sided permutation test of the difference in means: the share of [PERMUTATIONS] shuffles at least as far apart. */
    internal fun permutationP(yes: List<Float>, no: List<Float>, random: Random): Float {
        val all = (yes + no).toMutableList()
        val observed = abs(yes.average() - no.average())
        var hits = 0
        repeat(PERMUTATIONS) {
            all.shuffle(random)
            val a = all.subList(0, yes.size).average()
            val b = all.subList(yes.size, all.size).average()
            if (abs(a - b) >= observed - EPSILON) hits++
        }
        return (hits + 1f) / (PERMUTATIONS + 1f)
    }

    private val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
}
