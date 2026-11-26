package org.openstrap.app.presentation

import androidx.annotation.StringRes
import org.openstrap.app.R
import org.openstrap.app.data.ForkTarget
import org.openstrap.app.data.ForkTraining
import org.openstrap.app.data.Workouts
import org.openstrap.app.i18n.Strings
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The words of the Training view on the fork's Reports page (jptorres-emp/openstrap), as the old fork's Training tab
 * said them: what today's Load target means in your own sports, each sport against it, the last week's Load against your
 * usual, and the calories burned a day week by week. Plain Kotlin, for the tests; the target itself is [ReportsCopy].
 */
object TrainingWords {
    /** Under this many kcal a day of change the last week reads as the weeks before. */
    const val SAME_KCAL = 50
    private const val PERCENT = 100f

    /**
     * The sentence under the target: whether a usual day of your most done sport fits it, or of the next one that
     * does too, or, without any sport of yours yet, what the day is for in words.
     */
    fun plan(
        target: ForkTarget.Target,
        sports: List<ForkTraining.SportLoad>,
        locale: Locale = Locale.getDefault(),
        strings: Strings = Strings.app,
    ): String {
        val first = sports.firstOrNull() ?: return strings.get(
            when (target.kind) {
                ForkTarget.DayKind.Recover -> R.string.fork_rep_plan_recover
                ForkTarget.DayKind.Moderate -> R.string.fork_rep_plan_moderate
                ForkTarget.DayKind.Usual -> R.string.fork_rep_plan_usual
                ForkTarget.DayKind.Push -> R.string.fork_rep_plan_push
            },
        )
        val name = sportInSentence(first.sport, locale, strings)
        val load = first.dayLoad.roundToInt()
        val second = sports.drop(1).firstOrNull { ForkTraining.fit(target.range, it.dayLoad) == ForkTraining.Fit.Within }
        return when (ForkTraining.fit(target.range, first.dayLoad)) {
            ForkTraining.Fit.Over -> strings.format(R.string.fork_rep_plan_over, name, load)
            ForkTraining.Fit.Under -> strings.format(R.string.fork_rep_plan_under, name, load)
            ForkTraining.Fit.Within -> if (second == null) strings.format(R.string.fork_rep_plan_fits, name, load)
            else strings.format(
                R.string.fork_rep_plan_fits_two, name, load, sportInSentence(second.sport, locale, strings), second.dayLoad.roundToInt(),
            )
        }
    }

    /** A sport's row: its name, the Load of a usual day of it, where that lands against today's [target] and its days. */
    fun sport(
        s: ForkTraining.SportLoad,
        target: ForkTarget.Target?,
        locale: Locale = Locale.getDefault(),
        strings: Strings = Strings.app,
    ): ReportRow {
        val days = strings.plural(R.plurals.fork_rep_fit_days, s.days, s.days)
        val line = target?.let {
            val fit = when (ForkTraining.fit(it.range, s.dayLoad)) {
                ForkTraining.Fit.Under -> R.string.fork_rep_fit_under
                ForkTraining.Fit.Within -> R.string.fork_rep_fit_within
                ForkTraining.Fit.Over -> R.string.fork_rep_fit_over
            }
            strings.format(R.string.core_today_list_comma, strings.get(fit), days)
        } ?: days
        return ReportRow(Workouts.sportName(s.sport, strings), Units.count(s.dayLoad.roundToInt(), locale), line)
    }

    /** The last week's Load against your usual as a row: its zone, the share ("118%") and what the zone means. */
    fun balance(ratio: Float, strings: Strings = Strings.app): ReportRow {
        val (word, line) = when (ForkTraining.zone(ratio)) {
            ForkTraining.Zone.Easing -> R.string.core_tab_balance_easing to R.string.fork_rep_balance_easing
            ForkTraining.Zone.Steady -> R.string.core_tab_balance_steady to R.string.fork_rep_balance_steady
            ForkTraining.Zone.Building -> R.string.core_tab_balance_building to R.string.fork_rep_balance_building
            ForkTraining.Zone.Spike -> R.string.fork_rep_balance_spike to R.string.fork_rep_balance_spike_line
        }
        return ReportRow(strings.get(word), strings.format(R.string.fork_rep_percent, (ratio * PERCENT).roundToInt()), strings.get(line))
    }

    /** The calories burned a day in the last week against the weeks before it; null without the last week's. */
    fun calories(weeks: List<ForkTraining.EnergyWeek>, locale: Locale = Locale.getDefault(), strings: Strings = Strings.app): String? {
        val now = weeks.lastOrNull()?.kcalADay?.roundToInt() ?: return null
        val before = ForkTraining.energyBefore(weeks)?.roundToInt()
        val shown = Units.count(now, locale)
        return when {
            before == null -> strings.format(R.string.fork_rep_kcal_alone, shown)
            now - before >= SAME_KCAL -> strings.format(R.string.fork_rep_kcal_up, shown, Units.count(now - before, locale))
            before - now >= SAME_KCAL -> strings.format(R.string.fork_rep_kcal_down, shown, Units.count(before - now, locale))
            else -> strings.format(R.string.fork_rep_kcal_same, shown)
        }
    }

    /** The weeks by the day each ends ("21 Nov"), for the columns of the calories. */
    fun calorieLabels(weeks: List<ForkTraining.EnergyWeek>, locale: Locale = Locale.getDefault()): List<String> =
        weeks.map { DayWords.dayMonth(it.end, locale) }

    /** A text with a percent sign in it, which its resource writes twice so it's read through format. */
    fun withPercent(@StringRes id: Int, strings: Strings = Strings.app): String = strings.format(id)

    /** A sport's name mid-sentence: "football", but "CrossFit" and "HIIT" as they're written. */
    private fun sportInSentence(sport: Int, locale: Locale, strings: Strings): String {
        val name = Workouts.sportName(sport, strings)
        return if (name.drop(1).any { it.isUpperCase() }) name else name.replaceFirstChar { it.lowercase(locale) }
    }
}
