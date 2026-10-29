package org.openstrap.app.ui.dashboard

import org.openstrap.app.data.Recovery
import org.openstrap.app.data.Reports
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

/*
 * The Reports screen's words (spec 7): the week and the month against the stretch before them, the day's Recovery,
 * Load target and tonight's bedtime, how the days landed against their targets, and what habits do to the next
 * morning. A row's status keeps to a line on a phone where it can (about 28 characters beside a value), as the tabs'
 * lists do, and the method sits in the info sheet. A list row's value is drawn in dots, so values keep to the dot
 * font's glyphs: digits, ":", "-", "−", "+", ",", ".", "%", "k" and space. Built only from the report's numbers: where
 * one is missing the words say what it waits for.
 */
object ReportCopy {
    const val TITLE = "Reports"

    /** What a row of a list is about, for its colour, icon and the detail it opens. */
    enum class Line { Recovery, Target, Tonight, Hrv, RestingHr, Asleep, Restorative, Bedtime, Wake, Load, Usual, Workouts, Steps }

    /**
     * A list row: its [name], its [value] as dot text (null waits, and the [status] says for what), the [status] under
     * the name, and [off] when it's the one to notice: it moved the wrong way by enough to light (spec 2.5).
     */
    data class Row(val line: Line, val name: String, val value: String?, val status: String, val off: Boolean = false)

    /* ------------------------------------------------ Headline --------------------------------------------- */

    /** Beside the headline's average. */
    fun averageLabel(days: Int) = "average, last $days days"

    /** Beside the headline, under the Load a day over the same days: effort next to Recovery. */
    const val LOAD_A_DAY = "Load a day"

    /** Why there's no average, as the headline's state and the line under it. */
    fun noAverage(daysWithData: Int): Pair<String, String> =
        if (daysWithData == 0) "No data yet" to "Reports fill in as the strap syncs"
        else "Learning" to "Recovery is scored after 7 nights"

    const val LOADING_WORD = "Loading"
    const val FAILED_WORD = "No report"
    const val FAILED_WHY = "Try again in a moment"

    /** While the report is worked out, and when it couldn't be. */
    const val LOADING = "Working out your report."
    const val FAILED = "The report couldn't be worked out this time. It tries again when you come back."

    /** A chart's band, as the details label theirs ([DetailCopy.bandLabel]): "Your normal, 56 to 78". */
    fun bandLabel(band: ClosedFloatingPointRange<Float>): String = "Your normal, ${whole(band.start)} to ${whole(band.endInclusive)}"

    /** Under an empty week or month chart. */
    fun noScores(days: Int) = "No Recovery in the last $days days yet."

    /** One time-aware sentence under the chart: the week's Recovery against the 3 weeks before, and what moved most. */
    fun weekSentence(now: Reports.Figures, before: Reports.Figures?): String =
        sentence(now, before, "the 3 weeks before", Reports.WEEK_DAYS, "Comparisons start once there's a week before it.")

    fun monthSentence(now: Reports.Figures, before: Reports.Figures?): String =
        sentence(now, before, "the month before", Reports.MONTH_DAYS, "Comparisons start once there are two weeks before it.")

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

    /** The month's mornings in Recovery's bands, under its chart: Ready or better, Moderate, Take it easy. */
    fun spreadFigures(bands: List<Int>): List<Pair<String, String>> = listOf(
        (bands.getOrElse(2) { 0 } + bands.getOrElse(3) { 0 }).toString() to "Ready or better",
        bands.getOrElse(1) { 0 }.toString() to "Moderate",
        bands.getOrElse(0) { 0 }.toString() to "Take it easy",
    )

    /* ------------------------------------------------- The day --------------------------------------------- */

    /** The day's list's title; another day's is its date ([day]). */
    const val TODAY_TITLE = "Today"

    /**
     * The day's list, what to do with it: its Recovery, the Load target the morning set with the Load so far, and,
     * today, tonight's bedtime.
     */
    fun dayRows(r: Reports.Report, isToday: Boolean): List<Row> = listOfNotNull(
        recoveryRow(r.week.days.lastOrNull(), isToday),
        targetRow(r.target, r.load, r.finished),
        if (isToday) tonightRow(r.plan) else null,
    )

    /** The day's Recovery and its word; the strap's own score while Recovery learns. Lit on a day to take it easy. */
    fun recoveryRow(day: Reports.Day?, isToday: Boolean): Row {
        val score = day?.recovery
        val strap = day?.strapRecovery
        return when {
            score != null -> TodayCopy.recoveryWord(score, day.alert).let { word ->
                Row(Line.Recovery, "Recovery", score.toString(), word, off = day.alert || score < Recovery.FAIR_FROM)
            }
            strap != null -> Row(Line.Recovery, "Recovery", strap.toString(), "${TodayCopy.recoveryWord(strap, false)}, strap's score")
            else -> Row(Line.Recovery, "Recovery", null, if (isToday) "After last night syncs" else "No score that day")
        }
    }

    const val TARGET_NAME = "Load target"

    private fun targetRow(t: Reports.Target?, load: Float?, finished: Boolean): Row =
        if (t == null) Row(Line.Target, TARGET_NAME, null, TARGET_WAITING)
        else Row(Line.Target, TARGET_NAME, targetValue(t), targetStatus(t, load, finished), off = load != null && Math.round(load) > t.range.last)

    /** The target as dot text: the dot font has the hyphen, not the en dash. */
    fun targetValue(t: Reports.Target) = "${t.range.first}-${t.range.last}"

    /**
     * Under the target: what the morning made the day for, with the Load so far today, or where the day's Load landed
     * once it's over. The strap's score standing in for Recovery is the Recovery row's to say.
     */
    fun targetStatus(t: Reports.Target, load: Float?, finished: Boolean): String {
        val kind = if (!t.scored) "No score, a usual day" else when (t.kind) {
            Reports.DayKind.Recover -> "A day to recover"
            Reports.DayKind.Moderate -> "A moderate day"
            Reports.DayKind.Usual -> "A usual day"
            Reports.DayKind.Push -> "A day to push"
        }
        val shown = load?.let { Math.round(it) } ?: return kind
        return when {
            finished -> "Landed at $shown, " + when (Reports.versus(load, t.range)) {
                Reports.Versus.Below -> "below it"
                Reports.Versus.Within -> "within it"
                Reports.Versus.Above -> "above it"
            }
            shown > t.range.last -> "$shown so far, over it"
            else -> "$kind, $shown so far"
        }
    }

    /** Before a target: a week of worn days for your usual Load. */
    const val TARGET_WAITING = "After a week with the strap on"

    const val BEDTIME_NAME = "Bedtime tonight"
    const val SLEEP_NAME = "Sleep tonight"

    private fun tonightRow(p: Reports.SleepPlan): Row {
        val bedtime = bedtimeValue(p)
        return Row(Line.Tonight, if (bedtime != null) BEDTIME_NAME else SLEEP_NAME, bedtime ?: clock(p.asleepMinutes.toFloat()), planStatus(p))
    }

    /** The plan's bedtime as dot text, or null without a wake time to count back from. */
    fun bedtimeValue(p: Reports.SleepPlan): String? = p.bedtime?.toString()?.take(5)

    /** Under the bedtime: how long and up when, then what moved it off your need, if anything did. */
    fun planStatus(p: Reports.SleepPlan): String {
        val wake = p.wake ?: return "Bedtime shows after a few nights"
        val more = p.shortfallMinutes + p.loadMinutes
        val reasons = listOfNotNull("short nights".takeIf { p.shortfallMinutes > 0 }, "a hard day".takeIf { p.loadMinutes > 0 })
        // The nap's share of the night as the plan took it off: never more than the floor under your need allows.
        val nap = (p.needMinutes + more - p.asleepMinutes).coerceAtLeast(0)
        val why = listOfNotNull(
            "$more min more for ${reasons.joinToString(" and ")}".takeIf { more > 0 },
            "${TodayCopy.duration(nap * 60L)} less for a nap".takeIf { nap > 0 },
        )
        return (listOf("${TodayCopy.duration(p.asleepMinutes * 60L)} asleep, up at ${wake.toString().take(5)}") + why).joinToString(", ")
    }

    /* --------------------------------------------- Load and target ----------------------------------------- */

    const val TARGET_TITLE = "Load against your target"

    /** How the finished days landed, for the figures over the target chart. */
    fun balanceFigures(b: Reports.Balance): List<Pair<String, String>> = listOf(
        whole(b.within) to "Within target", whole(b.below) to "Below", whole(b.above) to "Above",
    )

    /** The figures while a day is held on the chart: its Load, its target, and the day with where it landed. */
    fun dayFigures(d: Reports.Day, t: Reports.Target?): List<Pair<String?, String>> = listOf(
        d.load?.let(::whole) to if (d.partial) "Load so far" else "Load",
        t?.let { "${it.range.first} to ${it.range.last}" } to "Target",
        day(d.date) to when {
            d.partial -> "Under way"
            t == null || d.load == null -> "No target"
            else -> when (Reports.versus(d.load, t.range)) {
                Reports.Versus.Below -> "Below target"
                Reports.Versus.Within -> "Within target"
                Reports.Versus.Above -> "Above target"
            }
        },
    )

    /** Under the chart: how the stretch before landed, in whole days. */
    fun balanceBefore(b: Reports.Balance?, month: Boolean): String? = b?.takeIf { it.days >= 0.5f }?.let {
        val split = "${days(Math.round(it.within))} within, ${Math.round(it.below)} below and ${Math.round(it.above)} above."
        if (month) "The month before had $split" else "An average week before had $split"
    }

    /** Over its targets more than under them: the card lights from the top, as a value above your normal. */
    fun overTarget(b: Reports.Balance): Boolean = b.above >= 2f && b.above > b.below

    /* ---------------------------------------------- Comparisons -------------------------------------------- */

    const val WEEK_TITLE = "This week"
    const val WEEK_NOTE = "Against the 3 before"
    const val MONTH_TITLE = "This month"
    const val MONTH_NOTE = "Against the one before"

    /** The week's figures, each against an average week of the 3 before it. Recovery is the headline's, so it's not here. */
    fun weekRows(now: Reports.Figures, before: Reports.Figures?): List<Row> = listOfNotNull(
        hrvRow(now, before),
        restingHrRow(now, before),
        asleepRow(now, before),
        shiftRow(Line.Bedtime, "Bedtime", now.bedtime, now.bedtimeShift, before?.bedtimeShift),
        shiftRow(Line.Wake, "Wake time", now.wake, now.wakeShift, before?.wakeShift),
        loadRow(now, before),
        workoutsRow(now, before),
        stepsRow(now, before),
    )

    /** The month's figures against the month before; Recovery and its spread are the headline's and the figures'. */
    fun monthRows(now: Reports.Figures, before: Reports.Figures?): List<Row> = listOfNotNull(
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
        shiftRow(Line.Bedtime, "Bedtime", now.bedtime, now.bedtimeShift, before?.bedtimeShift),
        shiftRow(Line.Wake, "Wake time", now.wake, now.wakeShift, before?.wakeShift),
        loadRow(now, before),
        now.usualLoad?.let { u -> Row(Line.Usual, "Your usual Load", whole(u), before?.usualLoad?.let { "Over 6 weeks, vs ${whole(it)}" } ?: "Over 6 weeks") },
        workoutsRow(now, before),
        stepsRow(now, before),
    )

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

    /** A sleep time and how much it varies night to night: the less, the steadier. */
    private fun shiftRow(line: Line, name: String, time: LocalTime?, shift: Float?, before: Float?): Row? = time?.let { t ->
        val varies = shift?.let { s -> "Varies by ${Math.round(s)} min" + (before?.let { ", vs ${Math.round(it)}" } ?: "") }
        Row(line, name, t.toString().take(5), varies ?: "Your usual time", off = shift != null && before != null && shift - before >= 15)
    }

    private fun loadRow(now: Reports.Figures, before: Reports.Figures?): Row {
        val l = now.load ?: return Row(Line.Load, "Load", null, "After a day with the strap on")
        val then = before?.load
        return Row(Line.Load, "Load", whole(l), if (then == null) "A day" else "A day, ${vs(l, then, ::whole)}")
    }

    /** How many workouts, their time in all, and how many the stretch before had: the count is the value. */
    private fun workoutsRow(now: Reports.Figures, before: Reports.Figures?): Row {
        val time = if (now.workoutSeconds > 0f) "${TodayCopy.duration(now.workoutSeconds.toLong())} in all" else "None"
        return Row(Line.Workouts, "Workouts", whole(now.workouts), before?.let { "$time, vs ${count(it.workouts)} workouts" } ?: time)
    }

    private fun stepsRow(now: Reports.Figures, before: Reports.Figures?): Row? = now.steps?.let { s ->
        val then = before?.steps
        Row(Line.Steps, "Steps", steps(s), if (then == null) "A day" else "A day, ${vs(s, then, ::steps)}", off = then != null && then > 0 && (s - then) / then <= -0.2f)
    }

    /* ----------------------------------------------- Weekdays ---------------------------------------------- */

    const val WEEKDAYS_TITLE = "By day of the week"
    /** Over all the reports' days. */
    const val WEEKDAYS_NOTE = "Last 60 days"

    /** A day of the week this far under the average is the one to notice. */
    const val LOW_WEEKDAY_POINTS = 5f

    /** The day of the week to notice: the lowest, when it's at least [LOW_WEEKDAY_POINTS] under the average of them all. */
    fun lowWeekday(weekdays: List<Reports.Weekday>): Reports.Weekday? {
        val scored = weekdays.filter { it.recovery != null }
        if (scored.size < 3) return null
        val low = scored.minBy { it.recovery!! }
        val mean = scored.map { it.recovery!! }.average().toFloat()
        return low.takeIf { mean - it.recovery!! >= LOW_WEEKDAY_POINTS }
    }

    /** Under the bars: the low day, or that the days are alike. */
    fun weekdayLine(weekdays: List<Reports.Weekday>): String? {
        val scored = weekdays.mapNotNull { it.recovery }
        if (scored.size < 3) return null
        val low = lowWeekday(weekdays) ?: return "Your days of the week are much alike."
        val mean = scored.average().toFloat()
        return "${plural(low.day)} are your lowest, ${Math.round(mean - low.recovery!!)} under your average."
    }

    /** "Mondays": sentences are English, as the rest of the app's. */
    private fun plural(d: DayOfWeek) = d.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + "s"

    fun shortDay(d: DayOfWeek, locale: Locale = Locale.getDefault()): String = d.getDisplayName(TextStyle.SHORT, locale)

    /* ------------------------------------------------- Sports ---------------------------------------------- */

    const val SPORTS_TITLE = "Workouts by sport"
    /** Beside the month's sports, whose bars split each one's time by heart rate zone. */
    const val ZONES_NOTE = "Zones, easy to max"

    /** A sport's time in all and how many sessions the month before had: the count is the value. */
    fun sportStatus(s: Reports.SportTotals): String {
        val time = "${TodayCopy.duration(s.seconds)} in all"
        return s.sessionsBefore?.let { "$time, vs ${count(it)} ${if (count(it) == "1") "session" else "sessions"}" } ?: time
    }

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

    /** How sure, over how many mornings, and what it keeps company with: the value says how much. */
    fun habitStatus(e: Reports.Effect): String {
        if (e.recoveryVerdict == Reports.Verdict.TooFew) return when {
            e.mornings < Reports.MIN_HABIT_DAYS -> "Too few mornings yet, ${e.mornings} of ${Reports.MIN_HABIT_DAYS}"
            else -> "Almost every morning, nothing to compare"
        }
        fun sure(v: Reports.Verdict) = if (v == Reports.Verdict.Clear) "Clear" else "Likely"
        val head = when {
            decisive(e.recoveryVerdict) -> "${sure(e.recoveryVerdict)} over ${e.mornings} mornings" +
                (if (decisive(e.hrvVerdict) && e.hrv != null) ", HRV ${signed(Math.round(e.hrv))}%" else "")
            decisive(e.hrvVerdict) -> "HRV, ${sure(e.hrvVerdict).lowercase()} over ${e.mornings} mornings"
            else -> "No clear effect, ${e.mornings} mornings"
        }
        val with = e.with.firstOrNull()?.let { ". Often with ${habitName(it).replaceFirstChar { c -> c.lowercase() }}" } ?: ""
        return head + with
    }

    /** Lit when the effect is clear and costs Recovery or HRV. */
    fun habitOff(e: Reports.Effect): Boolean =
        (e.recoveryVerdict == Reports.Verdict.Clear && (e.recovery ?: 0f) < 0f) || (e.hrvVerdict == Reports.Verdict.Clear && (e.hrv ?: 0f) < 0f)

    /* ---------------------------------------------- Accessibility ------------------------------------------ */

    /** What a screen reader says for a chart of Recovery by day. */
    fun describeRecovery(days: List<Reports.Day>, locale: Locale = Locale.getDefault()): String =
        "Recovery by day. " + days.joinToString(", ") { d -> "${shortDay(d.date.dayOfWeek, locale)} ${d.recovery ?: "no score"}" } + "."

    /** What a screen reader says for the days' Load against their targets. */
    fun describeTargets(days: List<Reports.Day>, targets: List<Reports.Target?>, locale: Locale = Locale.getDefault()): String =
        "Load against your target by day. " + days.mapIndexed { i, d ->
            val t = targets.getOrNull(i)?.range
            val load = d.load?.let { Math.round(it).toString() } ?: "no reading"
            "${shortDay(d.date.dayOfWeek, locale)} $load" + (t?.let { ", target ${it.first} to ${it.last}" } ?: "")
        }.joinToString(". ") + "."

    /** What a screen reader says for the days of the week. */
    fun describeWeekdays(weekdays: List<Reports.Weekday>, locale: Locale = Locale.getDefault()): String =
        "Average Recovery by day of the week. " + weekdays.joinToString(", ") { w -> "${shortDay(w.day, locale)} ${w.recovery?.let(::whole) ?: "no score"}" } + "."

    /* -------------------------------------------------- Info ----------------------------------------------- */

    /** The (i) sheet's meaning line and its folds, in place of how, moves and off. */
    const val INFO_MEANING =
        "Your last 7 or 30 days against the stretch before them, a Load target for the day, tonight's bedtime, and what your habits do to the next morning."

    val INFO_ROWS = listOf(
        "The week and the month" to
            "Recovery, HRV, resting heart rate, sleep and Load are the app's own, each morning worked out as it was that day. " +
            "The last 7 days are compared with an average week of the 3 before them, the last 30 with the 30 before. Your normal " +
            "on the chart is the last 30 days. Bedtime and wake time say how much they vary from one night to the next: the " +
            "less, the steadier your sleep.",
        "Load target" to
            "From your Load on training days over the last 6 weeks. A usual day is the middle half of your sessions. A low " +
            "Recovery or an alert makes it a day to recover, up to half an easy session. A fair one, or HRV below its range, " +
            "makes it a moderate day, and 85 or more a day to push. Until 4 workouts are on record it's built round your usual " +
            "Load, and while Recovery learns it goes by the strap's own score. On the chart each day's band is the target " +
            "that morning set and the bar is the Load you did, lit when it landed outside.",
        "Bedtime tonight" to
            "Your sleep need, plus up to 45 min for this week's short nights and 15 to 30 min after a hard day, less today's " +
            "naps. Counted back from your usual wake time for tomorrow's kind of day, a weekday or the weekend, allowing for " +
            "the time you lie awake in bed.",
        "Habits" to
            "The app keeps no journal, so habits are found in your data, each on the day before the morning: a workout and " +
            "the sports you do most, a late workout (from 20:00), a hard day (a Load 1.5 times your usual), an active day " +
            "(your top quarter of steps). And on the night itself: a late bedtime (45 min after your usual), a short night " +
            "(under 7 h asleep), a weekend night. They're read together, so a habit that only keeps company with another " +
            "doesn't take its effect. The number is Recovery points, or HRV in percent when only HRV moved.",
        "Why it might be off" to
            "Clear means a difference of at least 3 points (or 3%) that chance would give less than 1 time in 20, likely less " +
            "than 1 in 10. With several habits tested one may still look clear by chance, and a pattern isn't proof of cause. " +
            "Sleep counts toward Recovery, so short nights and late bedtimes move it partly by definition: HRV is the cleaner " +
            "sign there. A day with the strap off reads low on Load and steps.",
    )

    /* ------------------------------------------------- Formats --------------------------------------------- */

    /** A whole number. */
    fun whole(v: Float): String = Math.round(v).toString()

    /** Minutes as a clock-style duration for dots: "7:43". */
    fun clock(minutes: Float): String = Math.round(minutes).let { "%d:%02d".format(it / 60, it % 60) }

    /** Steps as the Steps rows show them. */
    fun steps(v: Float): String = "%,d".format(Math.round(v))

    fun days(n: Int) = if (n == 1) "1 day" else "$n days"

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
     * [now] against [before] in words: "+4 vs 70", "−12 min vs 7 h 30", or "same as before" when the change wouldn't
     * show (spec 2.3, never "+0"). Both are rounded as shown first, so the change adds up with the values on screen.
     * [shown] formats a whole value and the size of the change alike.
     */
    private fun vs(now: Float, before: Float, shown: (Float) -> String): String {
        val a = Math.round(now)
        val b = Math.round(before)
        if (a == b) return "same as before"
        return "${if (a < b) "−" else "+"}${shown(abs(a - b).toFloat())} vs ${shown(b.toFloat())}"
    }

    private fun delta(now: Float?, before: Float?): Int? = if (now != null && before != null) Math.round(now - before) else null

    /** [now] against [before] in whole percent. */
    private fun share(now: Float?, before: Float?): Int? =
        if (now != null && before != null && before > 0f) Math.round((now - before) / before * 100) else null

    /** A date as the details write it under a scrubbed value. */
    fun day(d: LocalDate): String = d.format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM"))
}
