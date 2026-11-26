package org.openstrap.app.presentation

import org.openstrap.app.R
import org.openstrap.app.data.ForkPlan
import org.openstrap.app.data.ForkReports
import org.openstrap.app.data.ForkTally
import org.openstrap.app.data.ForkTarget
import org.openstrap.app.i18n.Strings
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The words of the fork's Reports page (jptorres-emp/openstrap), as plain Kotlin for the tests: the sentence under the
 * chart, today's lines, the days against their target and the days of the week. The rows of the stretch against the
 * one before are [ReportsRows], the habits [HabitWords].
 */
object ReportsCopy {
    /** Under this much of a change the Load reads as the same, in percent. */
    const val SAME_LOAD_PERCENT = 5
    private const val PERCENT = 100f

    /**
     * The sentence under the chart: the stretch's average Recovery against the stretch before ([month] or the week),
     * then its Load a day against the one before. Null without a Recovery.
     */
    fun sentence(stretch: ForkReports.Stretch, month: Boolean, strings: Strings = Strings.app): String? {
        val now = stretch.now.recovery?.roundToInt() ?: return null
        val before = stretch.before?.recovery?.roundToInt()
        val stretchBefore = strings.get(if (month) R.string.fork_rep_before_month else R.string.fork_rep_before_week)
        val recovery = when {
            before == null -> strings.format(R.string.fork_rep_sentence_alone, now)
            now > before -> strings.format(R.string.fork_rep_sentence_up, now, now - before, stretchBefore)
            now < before -> strings.format(R.string.fork_rep_sentence_down, now, before - now, stretchBefore)
            else -> strings.format(R.string.fork_rep_sentence_same, now, stretchBefore)
        }
        val load = loadChange(stretch.now.load, stretch.before?.load)?.let { change ->
            when {
                abs(change) < SAME_LOAD_PERCENT -> strings.get(R.string.fork_rep_load_same)
                change > 0 -> strings.format(R.string.fork_rep_load_higher, change)
                else -> strings.format(R.string.fork_rep_load_lower, -change)
            }
        }
        return listOfNotNull(recovery, load).joinToString(" ")
    }

    /** The Load a day [now] against [before] in whole percent, or null without both. */
    fun loadChange(now: Float?, before: Float?): Int? =
        if (now == null || before == null || before < 1f) null else ((now - before) / before * PERCENT).roundToInt()

    /** What the day is for, in words. */
    fun kind(kind: ForkTarget.DayKind, strings: Strings = Strings.app): String = strings.get(
        when (kind) {
            ForkTarget.DayKind.Recover -> R.string.fork_rep_kind_recover
            ForkTarget.DayKind.Moderate -> R.string.fork_rep_kind_moderate
            ForkTarget.DayKind.Usual -> R.string.fork_rep_kind_usual
            ForkTarget.DayKind.Push -> R.string.fork_rep_kind_push
        },
    )

    /** The Load target's value ("110 to 205") and the line under it (what the day is for, the Load so far). */
    fun target(plan: ForkPlan.Plan, strings: Strings = Strings.app): Pair<String?, String> = plan.target?.let { t ->
        strings.format(R.string.fork_rep_target_value, t.range.first, t.range.last) to
            strings.format(R.string.fork_rep_target_line, kind(t.kind, strings), plan.load.roundToInt())
    } ?: (null to strings.get(R.string.fork_rep_target_none))

    /** Tonight's bedtime ("22:12") and the line under it: the time asleep it allows, the waking, the extra for short nights. */
    fun bedtime(plan: ForkPlan.Plan, strings: Strings = Strings.app): Pair<String?, String> {
        val bedtime = plan.bedtime
        val wake = plan.wake
        if (bedtime == null || wake == null) return null to strings.get(R.string.fork_rep_bedtime_none)
        val asleep = Units.duration(plan.asleep * Units.MINUTE, strings = strings)
        val line = if (plan.extra > 0) {
            strings.format(R.string.fork_rep_bedtime_line_extra, asleep, TimeOfDay.minutes(wake), plan.extra)
        } else {
            strings.format(R.string.fork_rep_bedtime_line, asleep, TimeOfDay.minutes(wake))
        }
        return TimeOfDay.minutes(bedtime) to line
    }

    /** The days against their target in the stretch [before], under the counts of the stretch shown. */
    fun versusBefore(before: ForkReports.Figures, month: Boolean, strings: Strings = Strings.app): String = strings.format(
        if (month) R.string.fork_rep_vs_target_month else R.string.fork_rep_vs_target_week,
        amount(before.within), amount(before.below), amount(before.above),
    )

    /** A count that may be an average: "3", or "2.3" with one decimal when it isn't whole. */
    fun amount(v: Float, locale: Locale = Locale.getDefault()): String =
        if (abs(v - v.roundToInt()) < HALF_TENTH) Units.count(v.roundToInt(), locale) else Units.decimal(v, 1, locale)

    /** The days of the week, Monday first, short as a week's bars name them ("Mon"), for the chart. */
    fun weekdayLabels(locale: Locale = Locale.getDefault()): List<String> =
        DayOfWeek.entries.map { DayWords.weekdayShort(A_MONDAY.plusDays(it.ordinal.toLong()), locale) }

    /** What the days of the week say: the best and the worst for Recovery, or that they're alike. */
    fun weekdays(weekdays: List<Float?>, locale: Locale = Locale.getDefault(), strings: Strings = Strings.app): String? {
        if (weekdays.count { it != null } < 2) return null
        val days = ForkTally.bestAndWorst(weekdays)
        val name = { d: DayOfWeek -> d.getDisplayName(TextStyle.FULL, locale) }
        return if (days == null) strings.get(R.string.fork_rep_weekdays_alike)
        else strings.format(R.string.fork_rep_weekdays_best, name(days.first).replaceFirstChar { it.titlecase(locale) }, name(days.second))
    }

    private const val HALF_TENTH = 0.05f

    /** Any Monday, to name the days of the week from. */
    private val A_MONDAY = LocalDate.of(2024, 1, 1)
}
