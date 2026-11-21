package org.openstrap.app.data

import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

/**
 * What a habit does to the next morning's Recovery, for the fork's reports (jptorres-emp/openstrap), as WHOOP's
 * journal reads it: the mornings after a day with it against the mornings after a day without it, with a permutation
 * test for how sure that is. The journal's habits count only the days that were answered. Pure, so it's unit tested.
 */
object ForkHabits {
    /** How sure an effect is: too few mornings to say, no clear effect, likely, or clear. */
    enum class Verdict { TooFew, Unclear, Likely, Clear }

    /** Something done the day before a morning: ticked in the journal ([Logged]) or found in the data. */
    sealed interface Habit {
        data class Logged(val habit: JournalHabit) : Habit

        /** A Load at least [Recovery.HARD_DAY_RATIO] times your usual. */
        data object HardDay : Habit

        /** Under [SHORT_NIGHT_MINUTES] asleep the night before the morning. */
        data object ShortNight : Habit

        /** A workout that started at 20:00 or later. */
        data object LateWorkout : Habit
    }

    /**
     * What a [habit] did over the [mornings] after it and the [others]: Recovery after it and without it on average,
     * and the difference ([points]) with its [verdict]. [percent] reads the difference against the mornings without
     * it, as WHOOP does: 6 points off a usual 60 is −10%.
     */
    data class Effect(
        val habit: Habit,
        val mornings: Int,
        val others: Int,
        val recoveryAfter: Float?,
        val recoveryOthers: Float?,
        val points: Float?,
        val verdict: Verdict,
    ) {
        val percent: Float? get() = points?.let { p -> recoveryOthers?.takeIf { it >= 1f }?.let { p / it * PERCENT } }
    }

    /** Mornings needed both after a habit and without it before it's compared. */
    const val MIN_DAYS = 4
    const val SHORT_NIGHT_MINUTES = 7 * 60
    /** A usual Load under this counts as this, so a quiet stretch doesn't make every walk a hard day. */
    const val MIN_USUAL_LOAD = 20f
    /** Clear: p under [CLEAR_P] and at least [CLEAR_POINTS] points; likely: under [LIKELY_P] and [LIKELY_POINTS]. */
    const val CLEAR_P = 0.05f
    const val CLEAR_POINTS = 3f
    const val LIKELY_P = 0.1f
    const val LIKELY_POINTS = 2f
    const val PERMUTATIONS = 2000
    /** Fixed, so the same days always give the same answer. */
    const val SEED = 7
    private const val PERCENT = 100f
    private const val EPSILON = 1e-9

    /**
     * Each habit's effect over the scored mornings of [days] whose day before is there: the journal's habits ticked at
     * least once, over the answered days, then the hard day, the short night and the late workout. The clearest first.
     */
    fun effects(days: List<ForkReports.Day>, usualLoad: Float?): List<Effect> {
        val byDate = days.associateBy { it.date }
        val pairs = days.filter { it.recovery != null }.mapNotNull { m -> byDate[m.date.minusDays(1)]?.let { m to it } }
        val answered = pairs.filter { it.second.journal != null }
        val random = Random(SEED)
        val ticked = JournalHabit.entries.filter { h -> answered.any { h in it.second.journal.orEmpty() } }
            .map { h -> effect(Habit.Logged(h), answered, random) { _, before -> h in before.journal.orEmpty() } }
        val found = listOf(Habit.HardDay, Habit.ShortNight, Habit.LateWorkout)
            .map { h -> effect(h, pairs, random) { morning, before -> did(h, morning, before, usualLoad) } }
        return (ticked + found).sortedWith(compareByDescending<Effect> { it.verdict }.thenByDescending { abs(it.points ?: 0f) })
    }

    private fun did(h: Habit, morning: ForkReports.Day, before: ForkReports.Day, usualLoad: Float?): Boolean = when (h) {
        Habit.HardDay -> before.load?.let { it >= Recovery.HARD_DAY_RATIO * max(usualLoad ?: 0f, MIN_USUAL_LOAD) } == true
        Habit.ShortNight -> morning.asleepMinutes?.let { it < SHORT_NIGHT_MINUTES } == true
        Habit.LateWorkout -> before.lateWorkout
        is Habit.Logged -> h.habit in before.journal.orEmpty()
    }

    private fun effect(
        habit: Habit,
        pairs: List<Pair<ForkReports.Day, ForkReports.Day>>,
        random: Random,
        did: (ForkReports.Day, ForkReports.Day) -> Boolean,
    ): Effect {
        val (yes, no) = pairs.partition { (morning, before) -> did(morning, before) }
        val after = yes.mapNotNull { it.first.recovery?.toFloat() }
        val others = no.mapNotNull { it.first.recovery?.toFloat() }
        val (points, verdict) = compare(after, others, random)
        return Effect(habit, after.size, others.size, after.averageOrNull(), others.averageOrNull(), points, verdict)
    }

    /** The difference in means, [yes] less [no], and its [Verdict]; none with under [MIN_DAYS] of either. */
    private fun compare(yes: List<Float>, no: List<Float>, random: Random): Pair<Float?, Verdict> {
        if (yes.size < MIN_DAYS || no.size < MIN_DAYS) return null to Verdict.TooFew
        val effect = (yes.average() - no.average()).toFloat()
        val p = permutationP(yes, no, random)
        val verdict = when {
            p < CLEAR_P && abs(effect) >= CLEAR_POINTS -> Verdict.Clear
            p < LIKELY_P && abs(effect) >= LIKELY_POINTS -> Verdict.Likely
            else -> Verdict.Unclear
        }
        return effect to verdict
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
}
