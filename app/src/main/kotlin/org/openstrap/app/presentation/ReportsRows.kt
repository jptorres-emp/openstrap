package org.openstrap.app.presentation

import org.openstrap.app.R
import org.openstrap.app.data.ForkReports
import org.openstrap.app.data.ForkTally
import org.openstrap.app.data.Workouts
import org.openstrap.app.i18n.Strings
import java.util.Locale
import kotlin.math.roundToInt

/** A row of the Reports page: its [title], its [value] on the right and the [line] under the title. */
data class ReportRow(val title: String, val value: String?, val line: String?)

/**
 * The rows of the fork's Reports page (jptorres-emp/openstrap) that read a stretch against the one before: HRV,
 * resting heart rate, time asleep (and deep and REM sleep in the [month]), bedtime and waking with how much they vary,
 * Load (and your usual Load in the month), workouts and steps; and the sports. A row without a value now is left out.
 */
object ReportsRows {
    private const val NOON = 12 * 60

    /** The rows of [stretch] against the one before it. */
    fun compare(
        stretch: ForkReports.Stretch,
        month: Boolean,
        locale: Locale = Locale.getDefault(),
        strings: Strings = Strings.app,
    ): List<ReportRow> {
        val now = stretch.now
        val before = stretch.before
        fun row(title: Int, value: Float?, was: Float?, text: (Float) -> String) = value?.let {
            ReportRow(strings.get(title), text(it), was?.let { w -> strings.format(R.string.fork_rep_vs, text(w)) })
        }
        val ms = { v: Float -> strings.format(R.string.fork_rep_ms, v.roundToInt()) }
        val bpm = { v: Float -> strings.format(R.string.fork_rep_bpm, v.roundToInt()) }
        val time = { v: Float -> Units.duration(v.roundToInt() * Units.MINUTE, locale, strings) }
        val whole = { v: Float -> Units.count(v.roundToInt(), locale) }
        return listOfNotNull(
            row(R.string.fork_rep_hrv, now.hrv, before?.hrv, ms),
            row(R.string.fork_rep_resting_hr, now.restingHr, before?.restingHr, bpm),
            row(R.string.fork_rep_asleep, now.asleep, before?.asleep, time),
            if (month) row(R.string.fork_rep_deep_rem, now.deepRem, before?.deepRem, time) else null,
            clock(R.string.fork_rep_bedtime_row, now.bedtime?.plus(NOON), now.bedtimeSpread, before?.bedtimeSpread, strings),
            clock(R.string.fork_rep_wake_row, now.wakeTime, now.wakeSpread, before?.wakeSpread, strings),
            row(R.string.fork_rep_load, now.load, before?.load, whole),
            if (month) row(R.string.fork_rep_usual_load, stretch.usualNow, stretch.usualBefore, whole) else null,
            workouts(now, before, locale, strings),
            row(R.string.fork_rep_steps, now.steps, before?.steps, whole),
        )
    }

    /** The rows of the stretch's sports: each one's sessions and their time, against the sessions before. */
    fun sports(sports: List<ForkTally.Sport>, locale: Locale = Locale.getDefault(), strings: Strings = Strings.app): List<ReportRow> =
        sports.map { s ->
            ReportRow(
                Workouts.sportName(s.sport, strings), Units.count(s.sessions, locale),
                timeLine(s.seconds.toFloat(), s.before, locale, strings),
            )
        }

    /** A bedtime or a waking: the average time of day, and how much it varies now and before. */
    private fun clock(title: Int, minutes: Float?, spread: Float?, spreadBefore: Float?, strings: Strings): ReportRow? = minutes?.let {
        val line = spread?.let { s ->
            spreadBefore?.let { strings.format(R.string.fork_rep_varies, s.roundToInt(), it.roundToInt()) }
                ?: strings.format(R.string.fork_rep_varies_alone, s.roundToInt())
        }
        ReportRow(strings.get(title), TimeOfDay.minutes(it.roundToInt()), line)
    }

    private fun workouts(now: ForkReports.Figures, before: ForkReports.Figures?, locale: Locale, strings: Strings): ReportRow =
        ReportRow(
            strings.get(R.string.fork_rep_workouts), ReportsCopy.amount(now.workouts, locale),
            timeLine(now.workoutSeconds, before?.workouts, locale, strings),
        )

    /** Under a count of sessions: their time in all, and how many there were before. */
    private fun timeLine(seconds: Float, before: Float?, locale: Locale, strings: Strings): String {
        val time = Units.duration(seconds.roundToInt().toLong(), locale, strings)
        return before?.let { strings.format(R.string.fork_rep_time_line, time, it.roundToInt()) }
            ?: strings.format(R.string.fork_rep_time_line_alone, time)
    }
}
