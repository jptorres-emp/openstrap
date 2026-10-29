package org.openstrap.app.ui.dashboard

import org.openstrap.app.data.Reports
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

/*
 * The Reports screen's words (spec 7): the week and the month against the stretch before them, the day's Load target
 * and tonight's bedtime, how Recovery spread, and what habits do to the next morning. A list row's value is drawn in
 * dots, so values keep to the dot font's glyphs: digits, ":", "-", "−", "+", ",", ".", "%", "k" and space. Built only
 * from the report's numbers; where one is missing the words say what it waits for.
 */
object ReportCopy {
    const val TITLE = "Reports"

    /** What a row of a comparison list is about, for its colour and icon. */
    enum class Line { Recovery, Ready, Hrv, RestingHr, Asleep, Restorative, Bedtime, Wake, Load, Usual, Workouts, Training, Steps }

    /**
     * A row of a comparison list: its [name], its [value] as dot text (null waits, and the [status] says for what),
     * the [status] under the name, and [off] when it moved the wrong way by enough to light (spec 2.5).
     */
    data class Row(val line: Line, val name: String, val value: String?, val status: String, val off: Boolean = false)

    /* ------------------------------------------------ Headline --------------------------------------------- */

    /** Beside the headline's average. */
    fun averageLabel(days: Int) = "average, last $days days"

    /** A chart's band, as the details label theirs ([DetailCopy.bandLabel]): "Your normal, 56 to 78". */
    fun bandLabel(band: ClosedFloatingPointRange<Float>): String = "Your normal, ${whole(band.start)} to ${whole(band.endInclusive)}"

    /** While the report is worked out, and when it couldn't be. */
    const val LOADING = "Working out your report."
    const val FAILED = "The report couldn't be worked out this time. It tries again when you come back."

    /** Under an empty week or month chart. */
    fun noScores(days: Int) = "No Recovery in the last $days days yet."

    /* ------------------------------------------------- Titles ---------------------------------------------- */

    const val TODAY_TITLE = "Today"
    const val THAT_DAY_TITLE = "That day"
    const val TARGET_TITLE = "Load against your target"
    const val WEEK_AGAINST = "Against the 3 weeks before"
    const val MONTH_AGAINST = "Against the 30 days before"
    const val SPREAD_TITLE = "How your mornings went"
    const val HRV_TITLE = "HRV"
    const val RESTING_HR_TITLE = "Resting heart rate"
    const val WEEKDAYS_TITLE = "Days of the week"
    const val SPORTS_TITLE = "Workouts by sport"
    /** Beside the month's sports, whose bars split each one's time by heart rate zone. */
    const val ZONES_NOTE = "Zones, easy to max"
    const val NO_NIGHTS = "No nights with the strap in the last 30 days yet."
    const val NO_SCORES_WORD = "No scores"

    /** Beside a night chart's title. */
    fun nightNote(unit: String) = "$unit a night"

    /** A finished day's Load against its target, under the headline while scrubbing; so far for the day under way. */
    fun versusWord(v: Reports.Versus, partial: Boolean): String = if (partial) "So far today" else when (v) {
        Reports.Versus.Below -> "Below the target"
        Reports.Versus.Within -> "Within the target"
        Reports.Versus.Above -> "Above the target"
    }

    /** Why there's no average, as the headline's state and the line under it. */
    fun noAverage(days: Int, daysWithData: Int): Pair<String, String> =
        if (daysWithData == 0) "No data yet" to "Reports fill in as the strap syncs"
        else "Learning" to "Recovery is scored after 7 nights"

    /** One time-aware sentence under the chart: the week's Recovery against the 3 weeks before, and what moved most. */
    fun weekSentence(now: Reports.Figures, before: Reports.Figures?): String =
        sentence(now, before, "the 3 weeks before", Reports.WEEK_DAYS, "Comparisons start once there's a week before it.")

    fun monthSentence(now: Reports.Figures, before: Reports.Figures?): String {
        val head = sentence(now, before, "the 30 days before", Reports.MONTH_DAYS, "Comparisons start once there are two weeks before it.")
        if (now.scored == 0) return head
        val ready = Math.round(now.ready)
        return "$head ${if (ready == 1) "1 day was" else "$ready days were"} Ready or better."
    }

    private fun sentence(now: Reports.Figures, before: Reports.Figures?, against: String, days: Int, noBefore: String): String {
        val rec = now.recovery ?: return "Recovery shows here once it's scored, after 7 nights with the strap."
        val then = before?.recovery ?: return "Recovery averaged ${whole(rec)} over the last $days days. $noBefore"
        val d = Math.round(rec) - Math.round(then)
        val head = "Recovery averaged ${whole(rec)}, " + when {
            d == 0 -> "level with $against."
            d > 0 -> "$d up on $against."
            else -> "${-d} down on $against."
        }
        val tails = listOfNotNull(
            delta(now.asleep, before.asleep)?.takeIf { abs(it) >= 15 }?.let { "You slept ${TodayCopy.duration(abs(it).toLong() * 60)} ${if (it > 0) "more" else "less"} a night." },
            share(now.hrv, before.hrv)?.takeIf { abs(it) >= 5 }?.let { "HRV was ${abs(it)}% ${if (it > 0) "higher" else "lower"}." },
            share(now.load, before.load)?.takeIf { abs(it) >= 20 }?.let { "Your Load was ${abs(it)}% ${if (it > 0) "higher" else "lower"}." },
        )
        return (listOf(head) + tails.take(2)).joinToString(" ")
    }

    /* ---------------------------------------------- Comparisons -------------------------------------------- */

    fun weekRows(now: Reports.Figures, before: Reports.Figures?): List<Row> = listOfNotNull(
        recoveryRow(now, before),
        hrvRow(now, before),
        restingHrRow(now, before),
        asleepRow(now, before),
        shiftRow(Line.Bedtime, "Bedtime", now.bedtime, now.bedtimeShift, before?.bedtimeShift),
        shiftRow(Line.Wake, "Wake time", now.wake, now.wakeShift, before?.wakeShift),
        loadRow(now, before),
        Row(Line.Workouts, "Workouts", whole(now.workouts), before?.let { "This week, ${count(it.workouts)} a week before" } ?: "This week"),
        Row(
            Line.Training, "Time training", clock(now.workoutSeconds / 60),
            before?.let { "This week, ${TodayCopy.duration(it.workoutSeconds.toLong())} a week before" } ?: "This week",
        ),
        stepsRow(now, before),
    )

    fun monthRows(now: Reports.Figures, before: Reports.Figures?): List<Row> = listOfNotNull(
        recoveryRow(now, before),
        Row(
            Line.Ready, "Ready or better", whole(now.ready),
            before?.let { "Days at 70 or more, ${vs(now.ready, it.ready, ::whole)}" } ?: "Days at 70 or more",
            off = before != null && now.ready - before.ready <= -5,
        ).takeIf { now.scored > 0 },
        hrvRow(now, before),
        restingHrRow(now, before),
        asleepRow(now, before),
        now.restorative?.let { r ->
            Row(
                Line.Restorative, "Deep and REM", clock(r),
                before?.restorative?.let { "A night, ${vs(r, it, ::minutes)}" } ?: "A night",
                off = before?.restorative?.let { r - it <= -15 } == true,
            )
        },
        Row(Line.Workouts, "Workouts", whole(now.workouts), before?.let { "In 30 days, ${count(it.workouts)} the 30 before" } ?: "In 30 days"),
        Row(
            Line.Training, "Time training", clock(now.workoutSeconds / 60),
            before?.let { "In 30 days, ${TodayCopy.duration(it.workoutSeconds.toLong())} the 30 before" } ?: "In 30 days",
        ),
        loadRow(now, before),
        now.usualLoad?.let { u ->
            Row(Line.Usual, "Your usual Load", whole(u), before?.usualLoad?.let { "A day, over 6 weeks, ${whole(it)} a month ago" } ?: "A day, over the last 6 weeks")
        },
        stepsRow(now, before),
    )

    private fun recoveryRow(now: Reports.Figures, before: Reports.Figures?): Row {
        val rec = now.recovery ?: return Row(Line.Recovery, "Recovery", null, "Scored after 7 nights with the strap")
        val then = before?.recovery
        return Row(Line.Recovery, "Recovery", whole(rec), if (then == null) "Average" else "Average, ${vs(rec, then, ::whole)}", off = then != null && rec - then <= -5)
    }

    private fun hrvRow(now: Reports.Figures, before: Reports.Figures?): Row? = now.hrv?.let { h ->
        val then = before?.hrv
        Row(Line.Hrv, "HRV", whole(h), if (then == null) "ms a night" else "ms a night, ${vs(h, then, ::whole)}", off = then != null && (h - then) / then <= -0.05f)
    }

    private fun restingHrRow(now: Reports.Figures, before: Reports.Figures?): Row? = now.restingHr?.let { r ->
        val then = before?.restingHr
        Row(Line.RestingHr, "Resting heart rate", whole(r), if (then == null) "bpm a night" else "bpm a night, ${vs(r, then, ::whole)}", off = then != null && r - then >= 2)
    }

    private fun asleepRow(now: Reports.Figures, before: Reports.Figures?): Row {
        val a = now.asleep ?: return Row(Line.Asleep, "Asleep", null, "After a night with the strap")
        val then = before?.asleep
        return Row(Line.Asleep, "Asleep", clock(a), if (then == null) "A night" else "A night, ${vs(a, then, ::minutes)}", off = then != null && a - then <= -20)
    }

    private fun shiftRow(line: Line, name: String, time: LocalTime?, shift: Float?, before: Float?): Row? = time?.let { t ->
        val moved = shift?.let { s -> "Moves ${Math.round(s)} min night to night" + (before?.let { ", ${Math.round(it)} before" } ?: "") }
        Row(line, name, t.toString().take(5), moved ?: "Usually", off = shift != null && before != null && shift - before >= 15)
    }

    private fun loadRow(now: Reports.Figures, before: Reports.Figures?): Row {
        val l = now.load ?: return Row(Line.Load, "Load", null, "After a day with the strap on")
        val then = before?.load
        return Row(Line.Load, "Load", whole(l), if (then == null) "A day" else "A day, ${vs(l, then, ::whole)}")
    }

    private fun stepsRow(now: Reports.Figures, before: Reports.Figures?): Row? = now.steps?.let { s ->
        val then = before?.steps
        Row(Line.Steps, "Steps", steps(s), if (then == null) "A day" else "A day, ${vs(s, then, ::steps)}", off = then != null && then > 0 && (s - then) / then <= -0.2f)
    }

    /* ----------------------------------------------- Load target ------------------------------------------- */

    const val TARGET_NAME = "Load target"

    /** The target as dot text: the dot font has the hyphen, not the en dash. */
    fun targetValue(t: Reports.Target) = "${t.range.first}-${t.range.last}"

    /**
     * Under the target: what the day is for, then the day's Load against it, so far today or as the day ended, and where
     * the target came from when it isn't from your own Recovery and workouts.
     */
    fun targetStatus(t: Reports.Target, load: Float?, finished: Boolean): String {
        val kind = when (t.kind) {
            Reports.DayKind.Recover -> if (finished) "A day for recovering." else "A day to recover. Keep it light."
            Reports.DayKind.Moderate -> if (finished) "A day for moderate effort." else "Train, but hold back."
            Reports.DayKind.Usual -> if (finished) "A day for your usual training." else "Ready for your usual training."
            Reports.DayKind.Push -> if (finished) "A day for pushing." else "A good day to push."
        }
        val so = load?.let { l ->
            val shown = Math.round(l)
            when {
                finished -> "Load $shown, " + when (Reports.versus(l, t.range)) {
                    Reports.Versus.Below -> "below it."
                    Reports.Versus.Within -> "within it."
                    Reports.Versus.Above -> "above it."
                }
                shown > t.range.last -> "$shown so far, over it already."
                else -> "$shown so far."
            }
        }
        val from = when {
            !t.scored -> "No Recovery this morning, so a usual day."
            t.fromStrap -> "From the strap's score while Recovery learns."
            !t.personal -> "Built round your usual Load until 4 workouts are on record."
            else -> null
        }
        return listOfNotNull(kind, so, from).joinToString(" ")
    }

    /** Before a target: a week of worn days for your usual Load. */
    const val TARGET_WAITING = "After a week with the strap on"

    /** How the week's finished days landed, for the figures under the target chart. */
    fun balanceFigures(b: Reports.Balance): List<Pair<String, String>> = listOf(
        whole(b.within) to "Within target", whole(b.below) to "Below", whole(b.above) to "Above",
    )

    /** Under the target chart, for the week before: how an average week landed. */
    fun balanceBefore(b: Reports.Balance?): String? = b?.takeIf { it.days > 0f }?.let {
        "An average week before: ${count(it.within)} within, ${count(it.below)} below, ${count(it.above)} above."
    }

    /* ------------------------------------------------ Sleep plan ------------------------------------------- */

    const val BEDTIME_NAME = "Bedtime tonight"
    const val SLEEP_NAME = "Sleep tonight"

    /** The plan's bedtime as dot text, or null without a wake time to count back from. */
    fun bedtimeValue(p: Reports.SleepPlan): String? = p.bedtime?.toString()?.take(5)

    fun planStatus(p: Reports.SleepPlan): String {
        val asleep = TodayCopy.duration(p.asleepMinutes * 60L)
        val head = p.wake?.let { "For $asleep asleep, up at ${it.toString().take(5)}." } ?: "Aim for $asleep asleep. Bedtime shows after a few nights."
        val adds = listOfNotNull(
            p.shortfallMinutes.takeIf { it > 0 }?.let { "$it min for this week's short nights" },
            p.loadMinutes.takeIf { it > 0 }?.let { "$it min after a hard day" },
        )
        val more = when (adds.size) {
            0 -> null
            1 -> "Includes ${adds[0]}."
            else -> "Includes ${adds[0]} and ${adds[1]}."
        }
        val nap = p.napMinutes.takeIf { it > 0 }?.let { "Less ${TodayCopy.duration(minOf(it, Reports.MAX_NAP_CREDIT_MINUTES) * 60L)} for today's nap." }
        return listOfNotNull(head, more, nap).joinToString(" ")
    }

    /* ------------------------------------------------- Spread ---------------------------------------------- */

    /** Recovery's four bands, lowest first, by their status words ([TodayCopy.recoveryWord]). */
    val BANDS = listOf("Take it easy", "Moderate", "Ready", "Ready for more")

    fun days(n: Int) = if (n == 1) "1 day" else "$n days"

    fun bandBefore(n: Int?) = n?.let { "${days(it)} in the 30 days before" }

    fun weekdayName(d: DayOfWeek, locale: Locale = Locale.getDefault()): String =
        d.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.titlecase(locale) }

    fun weekdayDetail(w: Reports.Weekday): String = listOfNotNull(
        w.load?.let { "Load ${whole(it)}" },
        w.asleep?.let { "${TodayCopy.duration(Math.round(it) * 60L)} asleep" },
        days(w.days),
    ).joinToString(", ")

    /** Over all the reports' days, under the days of the week's title. */
    const val WEEKDAYS_NOTE = "Last 60 days"

    /* ------------------------------------------------- Sports ---------------------------------------------- */

    fun sportStatus(s: Reports.SportTotals, month: Boolean): String {
        val time = "${TodayCopy.duration(s.seconds)} in total"
        val before = s.sessionsBefore?.let { if (month) "${count(it)} the 30 days before" else "${count(it)} a week before" }
        return listOfNotNull(time, before).joinToString(", ")
    }

    fun zoneTime(seconds: Int) = TodayCopy.duration(seconds.toLong())

    /* ------------------------------------------------- Habits ---------------------------------------------- */

    const val HABITS_TITLE = "What moves your Recovery"
    const val HABITS_NOTE = "Last 60 days"
    const val HABITS_WAITING = "Habits show once a few weeks of mornings are scored."

    fun habitName(h: Reports.Habit): String = when (h) {
        Reports.Habit.Workout -> "A workout"
        is Reports.Habit.Sport -> h.name
        Reports.Habit.LateWorkout -> "Late workout"
        Reports.Habit.HardDay -> "Hard day"
        Reports.Habit.LateBedtime -> "Late bedtime"
        Reports.Habit.ShortNight -> "Short night"
        Reports.Habit.Weekend -> "Weekend night"
        Reports.Habit.ActiveDay -> "Active day"
    }

    private fun decisive(v: Reports.Verdict) = v == Reports.Verdict.Clear || v == Reports.Verdict.Likely

    /** The habit's effect as dot text: Recovery points when that's decisive, else HRV in percent when that is, else none. */
    fun habitValue(e: Reports.Effect): String? = when {
        decisive(e.recoveryVerdict) && e.recovery != null -> signed(Math.round(e.recovery))
        decisive(e.hrvVerdict) && e.hrv != null -> signed(Math.round(e.hrv)) + "%"
        else -> null
    }

    fun habitStatus(e: Reports.Effect): String {
        val mornings = if (e.mornings == 1) "1 morning" else "${e.mornings} mornings"
        if (e.recoveryVerdict == Reports.Verdict.TooFew) return when {
            e.mornings < Reports.MIN_HABIT_DAYS -> "Too few mornings after it to tell yet, $mornings so far"
            else -> "Almost every morning, nothing to compare with"
        }
        fun sure(v: Reports.Verdict) = if (v == Reports.Verdict.Clear) "clear" else "likely"
        val parts = mutableListOf<String>()
        when {
            decisive(e.recoveryVerdict) -> {
                parts += "Recovery points the next morning, ${sure(e.recoveryVerdict)} over $mornings."
                if (decisive(e.hrvVerdict) && e.hrv != null) parts += "HRV ${signed(Math.round(e.hrv))}%."
            }
            decisive(e.hrvVerdict) -> parts += "HRV the next morning, ${sure(e.hrvVerdict)} over $mornings. No clear effect on Recovery."
            else -> parts += "No clear effect on the next morning, over $mornings."
        }
        e.with.firstOrNull()?.let { parts += "Often with ${habitName(it).replaceFirstChar { c -> c.lowercase() }}." }
        return parts.joinToString(" ")
    }

    /** Lit when the effect is clear and costs Recovery or HRV. */
    fun habitOff(e: Reports.Effect): Boolean =
        (e.recoveryVerdict == Reports.Verdict.Clear && (e.recovery ?: 0f) < 0f) || (e.hrvVerdict == Reports.Verdict.Clear && (e.hrv ?: 0f) < 0f)

    /* -------------------------------------------------- Info ----------------------------------------------- */

    /** The (i) sheet's meaning line and its folds, in place of how, moves and off. */
    const val INFO_MEANING =
        "Your last 7 and 30 days against the stretch before them, the day's Load target, tonight's bedtime, and what your habits do to the next morning."

    val INFO_ROWS = listOf(
        "The week and the month" to
            "Recovery, HRV, resting heart rate, sleep and Load are the app's own, each morning worked out again as it was that " +
            "day. The last 7 days are compared with the average week of the 3 before them, the last 30 with the 30 before. Bedtime " +
            "and wake time show how far they move from one night to the next: the less, the steadier your sleep.",
        "Load target" to
            "From your Load on training days over the last 6 weeks. A usual day is the middle half of your sessions. A low Recovery " +
            "or an alert asks for recovery, up to half an easy session. A fair one, or HRV below its range, asks for a moderate day, " +
            "and 85 or more for a hard one. Until 4 workouts are on record it's built round your usual Load, and while Recovery " +
            "learns it goes by the strap's own score.",
        "Bedtime tonight" to
            "Your sleep need, plus up to 45 min for this week's short nights and 15 to 30 min after a hard day, less today's naps. " +
            "Counted back from your usual wake time for tomorrow's kind of day, a weekday or the weekend, allowing for the time you " +
            "lie awake in bed.",
        "Habits" to
            "The app keeps no journal, so habits are found in your data: workouts and the sports you do most, late workouts " +
            "(from 20:00), hard days (a Load 1.5 times your usual), late bedtimes (45 min after your usual), short nights (under " +
            "7 h asleep), weekend nights and active days (your top quarter of steps). Each is read on the next morning, held " +
            "against the others, so one that only keeps company with another doesn't take its effect.",
        "Why it might be off" to
            "Clear means a difference of at least 3 points (or 3%) that chance would give less than 1 time in 20, likely less " +
            "than 1 in 10. With several habits tested one may still look clear by chance, and a pattern isn't proof of cause. " +
            "Sleep counts toward Recovery, so short nights and late bedtimes move it partly by definition: HRV is the cleaner sign " +
            "there. A day with the strap off reads low on Load and steps.",
    )

    /* ------------------------------------------------- Formats --------------------------------------------- */

    /** A whole number. */
    fun whole(v: Float): String = Math.round(v).toString()

    /** Minutes as a clock-style duration for dots: "7:43". */
    fun clock(minutes: Float): String = Math.round(minutes).let { "%d:%02d".format(it / 60, it % 60) }

    /** Steps as the Steps rows show them. */
    fun steps(v: Float): String = "%,d".format(Math.round(v))

    private fun minutes(v: Float): String = TodayCopy.duration(Math.round(v) * 60L)

    /** A count that may be an average: whole when it is one, else to one decimal. */
    private fun count(v: Float): String = if (abs(v - Math.round(v)) < 0.05f) whole(v) else "%.1f".format(v)

    /** A signed whole number with a true minus (spec 2.3): "+4", "−3", "0". */
    fun signed(n: Int): String = when {
        n > 0 -> "+$n"
        n < 0 -> "−${-n}"
        else -> "0"
    }

    /**
     * [now] against [before] in words: "+4 vs 70", "−12 min vs 7 h 30", or "as before" when the change wouldn't show.
     * Both are rounded as shown first, so the change adds up with the values on screen. [shown] formats a whole value
     * and the size of the change alike.
     */
    private fun vs(now: Float, before: Float, shown: (Float) -> String): String {
        val a = Math.round(now)
        val b = Math.round(before)
        if (a == b) return "as before"
        return "${if (a < b) "−" else "+"}${shown(abs(a - b).toFloat())} vs ${shown(b.toFloat())}"
    }

    private fun delta(now: Float?, before: Float?): Int? = if (now != null && before != null) Math.round(now - before) else null

    /** [now] against [before] in whole percent. */
    private fun share(now: Float?, before: Float?): Int? =
        if (now != null && before != null && before > 0f) Math.round((now - before) / before * 100) else null
}
