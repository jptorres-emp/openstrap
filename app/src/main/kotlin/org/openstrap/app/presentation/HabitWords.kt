package org.openstrap.app.presentation

import org.openstrap.app.R
import org.openstrap.app.data.ForkHabits
import org.openstrap.app.data.Workouts
import org.openstrap.app.i18n.Strings
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The words for the habits on the fork's Reports page (jptorres-emp/openstrap): each one's name, its change in HRV the
 * morning after ("−7%"), how sure that is and what it came with, and the lists of the habits with no clear effect and
 * with too few mornings yet.
 */
object HabitWords {
    /** A habit's name: the journal's, the sport's, or one found in the data. */
    fun name(habit: ForkHabits.Habit, strings: Strings = Strings.app): String = when (habit) {
        is ForkHabits.Habit.Logged -> strings.get(habit.habit.label)
        is ForkHabits.Habit.Sport -> Workouts.sportName(habit.sport, strings)
        ForkHabits.Habit.Workout -> strings.get(R.string.fork_habit_workout)
        ForkHabits.Habit.LateWorkout -> strings.get(R.string.fork_habit_late_workout)
        ForkHabits.Habit.HardDay -> strings.get(R.string.fork_habit_hard_day)
        ForkHabits.Habit.ActiveDay -> strings.get(R.string.fork_habit_active_day)
        ForkHabits.Habit.ShortNight -> strings.get(R.string.fork_habit_short_night)
        ForkHabits.Habit.WeekendNight -> strings.get(R.string.fork_habit_weekend)
    }

    /** The change in HRV, signed and whole: "+7%", "−13%". Null without one. */
    fun change(effect: ForkHabits.Effect, strings: Strings = Strings.app): String? = effect.percent?.roundToInt()?.let {
        strings.format(if (it < 0) R.string.fork_rep_percent_down else R.string.fork_rep_percent_up, abs(it))
    }

    /** How sure the effect is and over how many mornings, then what it most often came with. */
    fun line(effect: ForkHabits.Effect, locale: Locale = Locale.getDefault(), strings: Strings = Strings.app): String {
        val sure = strings.plural(
            if (effect.verdict == ForkHabits.Verdict.Clear) R.plurals.fork_rep_effect_clear else R.plurals.fork_rep_effect_likely,
            effect.mornings, effect.mornings,
        )
        return effect.along?.let { strings.format(R.string.fork_rep_effect_along, sure, inSentence(name(it, strings), locale)) } ?: sure
    }

    /** The habits with no clear effect, as one sentence, or null for none. */
    fun none(effects: List<ForkHabits.Effect>, locale: Locale = Locale.getDefault(), strings: Strings = Strings.app): String? =
        listed(effects.filter { it.verdict == ForkHabits.Verdict.Unclear }, R.string.fork_rep_effect_none, locale, strings)

    /** The journal's habits with too few mornings to say yet, as one sentence, or null for none. */
    fun few(effects: List<ForkHabits.Effect>, locale: Locale = Locale.getDefault(), strings: Strings = Strings.app): String? = listed(
        effects.filter { it.verdict == ForkHabits.Verdict.TooFew && it.habit is ForkHabits.Habit.Logged }, R.string.fork_rep_effect_few,
        locale, strings,
    )

    /** Before any habit has a likely or a clear effect. */
    fun empty(strings: Strings = Strings.app): String = strings.get(R.string.fork_rep_effects_empty)

    private fun listed(effects: List<ForkHabits.Effect>, sentence: Int, locale: Locale, strings: Strings): String? {
        if (effects.isEmpty()) return null
        val names = effects.map { name(it.habit, strings) }.mapIndexed { i, n -> if (i == 0) n else inSentence(n, locale) }
        val list = if (names.size == 1) names.single() else strings.format(
            R.string.core_today_list_and,
            names.dropLast(1).reduce { a, b -> strings.format(R.string.core_today_list_comma, a, b) }, names.last(),
        )
        return strings.format(sentence, list)
    }

    /** A name inside a sentence: its first letter in lower case, unless the whole first word is capitals ("HRV"). */
    private fun inSentence(name: String, locale: Locale): String {
        val first = name.substringBefore(' ')
        val capitals = first.length > 1 && first.all { !it.isLetter() || it.isUpperCase() }
        return if (capitals) name else name.replaceFirstChar { it.lowercase(locale) }
    }
}
