package org.openstrap.app.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openstrap.app.data.Reports
import org.openstrap.app.ui.glow.draw.DotFont
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class ReportCopyTest {
    private val week = Reports.Figures(
        span = 7, recovery = 74.2f, scored = 7, ready = 6f, hrv = 56.2f, restingHr = 52.8f, asleep = 463.3f, restorative = 224.7f,
        bedtime = LocalTime.of(23, 20), wake = LocalTime.of(7, 13), bedtimeShift = 44.3f, wakeShift = 33.5f, load = 94.4f,
        usualLoad = 76.6f, fatigue = 88f, workouts = 4f, workoutSeconds = 15_300f, steps = 14_765.8f,
    )
    private val before = week.copy(
        span = 21, recovery = 72.4f, hrv = 53.9f, restingHr = 52.7f, asleep = 449.5f, bedtimeShift = 31.2f, wakeShift = 37.5f,
        load = 69.2f, workouts = 3f, workoutSeconds = 11_700f, steps = 13_731.8f,
    )
    private val end = LocalDate.of(2026, 10, 28)
    private val target = Reports.Target(Reports.DayKind.Usual, 110..205, personal = true, fromStrap = false, scored = true)

    /** Every list value is drawn in dots: only the dot font's glyphs (a locale's grouping space draws as a space). */
    private fun assertDots(value: String?) {
        if (value == null) return
        value.forEach { c -> assertTrue("'$c' in \"$value\" has no dot glyph", DotFont.has(c) || c == ' ' || c == ' ') }
    }

    private fun assertCopy(text: String) {
        assertTrue("\"$text\"", text.none { it in setOf('·', ';', '—', '!') })
    }

    /** About what fits on one line beside a row's value on a phone (spec 2.5: a short status). */
    private fun assertOneLine(status: String) = assertTrue("\"$status\" is ${status.length} long", status.length <= 30)

    @Test fun `the week's rows compare with an average week before, in dot-safe values on a line each`() {
        val rows = ReportCopy.weekRows(week, before)
        rows.forEach { assertDots(it.value); assertCopy(it.status); assertOneLine(it.status) }
        val byLine = rows.associateBy { it.line }
        assertEquals("56", byLine.getValue(ReportCopy.Line.Hrv).value)
        assertEquals("ms a night, +2 vs 54", byLine.getValue(ReportCopy.Line.Hrv).status)
        assertEquals("bpm a night, same as before", byLine.getValue(ReportCopy.Line.RestingHr).status)
        assertEquals("7:43", byLine.getValue(ReportCopy.Line.Asleep).value)
        // Both sides rounded as shown, so the change adds up: 7:43 is 13 min over 7 h 30.
        assertEquals("A night, +13 min vs 7 h 30", byLine.getValue(ReportCopy.Line.Asleep).status)
        assertEquals("23:20", byLine.getValue(ReportCopy.Line.Bedtime).value)
        assertEquals("Varies by 44 min, vs 31", byLine.getValue(ReportCopy.Line.Bedtime).status)
        assertEquals("A day, +25 vs 69", byLine.getValue(ReportCopy.Line.Load).status)
        assertEquals("4", byLine.getValue(ReportCopy.Line.Workouts).value)
        assertEquals("4 h 15 in all, vs 3 workouts", byLine.getValue(ReportCopy.Line.Workouts).status)
        assertTrue(rows.none { it.off })
        // Recovery is the headline's, not a row.
        assertTrue(rows.none { it.line == ReportCopy.Line.Recovery })
        // Without a week before, no comparison.
        assertEquals("ms a night", ReportCopy.weekRows(week, null).first().status)
        assertEquals("None", ReportCopy.weekRows(week.copy(workouts = 0f, workoutSeconds = 0f), null).first { it.line == ReportCopy.Line.Workouts }.status)
    }

    @Test fun `a worse week lights its rows, with a true minus`() {
        val worse = week.copy(recovery = 60f, hrv = 48f, restingHr = 56f, asleep = 400f, bedtimeShift = 70f)
        val rows = ReportCopy.weekRows(worse, before).associateBy { it.line }
        assertEquals("ms a night, −6 vs 54", rows.getValue(ReportCopy.Line.Hrv).status)
        listOf(ReportCopy.Line.Hrv, ReportCopy.Line.RestingHr, ReportCopy.Line.Asleep, ReportCopy.Line.Bedtime).forEach {
            assertTrue("$it lit", rows.getValue(it).off)
        }
        // More or less Load isn't better or worse.
        assertTrue(!rows.getValue(ReportCopy.Line.Load).off)
    }

    @Test fun `the month's rows read the 30 days before`() {
        val rows = ReportCopy.monthRows(week.copy(span = 30, workouts = 13f), before.copy(span = 30, workouts = 16f))
        rows.forEach { assertDots(it.value); assertCopy(it.status); assertOneLine(it.status) }
        val byLine = rows.associateBy { it.line }
        assertEquals("3:45", byLine.getValue(ReportCopy.Line.Restorative).value)
        assertEquals("A night, same as before", byLine.getValue(ReportCopy.Line.Restorative).status)
        assertEquals("Over 6 weeks, vs 77", byLine.getValue(ReportCopy.Line.Usual).status)
        assertEquals("4 h 15 in all, vs 16 workouts", byLine.getValue(ReportCopy.Line.Workouts).status)
    }

    @Test fun `sentences say what moved, and what they wait for`() {
        val s = ReportCopy.weekSentence(week, before)
        assertEquals("Recovery averaged 74, 2 up on the 3 weeks before. Your Load was 36% higher.", s)
        assertCopy(s)
        assertEquals(
            "Recovery averaged 74 over the last 7 days. Comparisons start once there's a week before it.",
            ReportCopy.weekSentence(week, null),
        )
        assertTrue(ReportCopy.weekSentence(week.copy(recovery = null, scored = 0), null).startsWith("Recovery shows here"))
        assertEquals(
            "Recovery averaged 74, 7 up on the month before. You slept 33 min more a night. Your Load was 36% higher.",
            ReportCopy.monthSentence(week, before.copy(recovery = 67f, asleep = 430f)),
        )
        assertEquals(listOf("13" to "Ready or better", "9" to "Moderate", "8" to "Take it easy"), ReportCopy.spreadFigures(listOf(8, 9, 10, 3)))
    }

    @Test fun `the day's Recovery reads as its word, the strap's while Recovery learns`() {
        val ready = ReportCopy.recoveryRow(Reports.Day(end, recovery = 82), isToday = true)
        assertEquals("82" to "Ready", ready.value to ready.status)
        assertTrue(!ready.off)
        assertTrue(ReportCopy.recoveryRow(Reports.Day(end, recovery = 50), isToday = true).off)
        val alert = ReportCopy.recoveryRow(Reports.Day(end, recovery = 75, alert = true), isToday = true)
        assertEquals("Take it easy", alert.status)
        assertTrue(alert.off)
        assertEquals("Ready, strap's score", ReportCopy.recoveryRow(Reports.Day(end, strapRecovery = 80), isToday = true).status)
        val none = ReportCopy.recoveryRow(Reports.Day(end), isToday = true)
        assertEquals(null to "After last night syncs", none.value to none.status)
        assertEquals("No score that day", ReportCopy.recoveryRow(Reports.Day(end), isToday = false).status)
    }

    @Test fun `the target reads as dots and says what the day is for, on a line`() {
        assertEquals("110-205", ReportCopy.targetValue(target))
        assertDots(ReportCopy.targetValue(target))
        val statuses = mapOf(
            ReportCopy.targetStatus(target, null, finished = false) to "A usual day",
            ReportCopy.targetStatus(target, 35.2f, finished = false) to "A usual day, 35 so far",
            ReportCopy.targetStatus(target, 230f, finished = false) to "230 so far, over it",
            ReportCopy.targetStatus(target, 94f, finished = true) to "Landed at 94, below it",
            ReportCopy.targetStatus(target, 150f, finished = true) to "Landed at 150, within it",
            ReportCopy.targetStatus(target.copy(kind = Reports.DayKind.Recover, fromStrap = true), null, false) to "A day to recover",
            ReportCopy.targetStatus(target.copy(scored = false), null, false) to "No score, a usual day",
        )
        statuses.forEach { (got, want) -> assertEquals(want, got); assertOneLine(got) }
    }

    @Test fun `the day's list holds its Recovery, target and, today, tonight's bedtime`() {
        val first = end.minusDays(Reports.DAYS - 1L)
        val days = (0 until Reports.DAYS).map { i ->
            Reports.Day(
                first.plusDays(i.toLong()), recovery = 70 + i % 10, load = 80f + i % 5 * 10, usualLoad = 90f, fatigue = 95f,
                asleepMinutes = 450, inBedMinutes = 490, bedtime = LocalTime.of(23, 0), wake = LocalTime.of(7, 0), partial = i == Reports.DAYS - 1,
            )
        }
        val r = Reports.build(Reports.Input(end, days, days.associate { it.date to it.load!! }), emptyList(), 480, today = end)
        val today = ReportCopy.dayRows(r, isToday = true)
        assertEquals(listOf(ReportCopy.Line.Recovery, ReportCopy.Line.Target, ReportCopy.Line.Tonight), today.map { it.line })
        today.forEach { assertDots(it.value); assertCopy(it.status) }
        assertEquals(listOf(ReportCopy.Line.Recovery, ReportCopy.Line.Target), ReportCopy.dayRows(r, isToday = false).map { it.line })
        // The week's figures carry the training balance Activity's Load card shows.
        assertEquals("Steady", TabCopy.loadBalance(r.week.now.trend))
    }

    @Test fun `tonight's plan says how long, up when, and what moved it`() {
        val plan = Reports.SleepPlan(505, 480, 25, 0, 0, 0.95f, LocalTime.of(7, 5), LocalTime.of(22, 12))
        assertEquals("22:12", ReportCopy.bedtimeValue(plan))
        assertEquals("8 h 25 asleep, up at 07:05, 25 min more for short nights", ReportCopy.planStatus(plan))
        val plain = plan.copy(asleepMinutes = 480, shortfallMinutes = 0)
        assertEquals("8 h asleep, up at 07:05", ReportCopy.planStatus(plain))
        assertOneLine(ReportCopy.planStatus(plain))
        // Need 480, plus 25 and 15, less a 40 min nap.
        val busy = plan.copy(asleepMinutes = 480, loadMinutes = 15, napMinutes = 40)
        assertEquals("8 h asleep, up at 07:05, 40 min more for short nights and a hard day, 40 min less for a nap", ReportCopy.planStatus(busy))
        assertNull(ReportCopy.bedtimeValue(plan.copy(bedtime = null, wake = null)))
        assertEquals("Bedtime shows after a few nights", ReportCopy.planStatus(plan.copy(bedtime = null, wake = null)))
    }

    @Test fun `the target chart's figures say how the days landed, and follow a held day`() {
        assertEquals(
            listOf("4" to "Within target", "2" to "Below", "0" to "Above"),
            ReportCopy.balanceFigures(Reports.Balance(below = 2f, within = 4f, above = 0f)),
        )
        val held = ReportCopy.dayFigures(Reports.Day(end, load = 94.4f), target)
        assertEquals(listOf("94" to "Load", "110 to 205" to "Target"), held.take(2))
        assertEquals("Below target", held[2].second)
        val underWay = ReportCopy.dayFigures(Reports.Day(end, load = 35f, partial = true), target)
        assertEquals("Load so far" to "Under way", underWay[0].second to underWay[2].second)
        assertEquals("No target", ReportCopy.dayFigures(Reports.Day(end, load = 35f), null)[2].second)
        assertEquals("An average week before had 3 days within, 3 below and 1 above.", ReportCopy.balanceBefore(Reports.Balance(3f, 3.2f, 0.8f), month = false))
        assertEquals("The month before had 18 days within, 8 below and 4 above.", ReportCopy.balanceBefore(Reports.Balance(8f, 18f, 4f), month = true))
        assertNull(ReportCopy.balanceBefore(Reports.Balance(0f, 0f, 0f), month = false))
        assertTrue(ReportCopy.overTarget(Reports.Balance(below = 1f, within = 3f, above = 3f)))
        assertTrue(!ReportCopy.overTarget(Reports.Balance(below = 4f, within = 1f, above = 2f)))
        assertCopy(ReportCopy.describeTargets(listOf(Reports.Day(end, load = 94f)), listOf(target)))
    }

    @Test fun `a habit shows its effect only when it's clear or likely`() {
        fun effect(rec: Float?, rv: Reports.Verdict, hrv: Float?, hv: Reports.Verdict, with: List<Reports.Habit> = emptyList()) =
            Reports.Effect(Reports.Habit.LateWorkout, 9, 50, 64f, 72f, rec, rv, hrv, hv, adjusted = true, with = with)
        val clear = effect(-8.4f, Reports.Verdict.Clear, -12.6f, Reports.Verdict.Likely, listOf(Reports.Habit.Sport(18, "Soccer")))
        assertEquals("−8", ReportCopy.habitValue(clear))
        assertDots(ReportCopy.habitValue(clear))
        assertEquals("Clear over 9 mornings, HRV −13%. Often with soccer", ReportCopy.habitStatus(clear))
        assertTrue(ReportCopy.habitOff(clear))
        val hrvOnly = effect(-1f, Reports.Verdict.Unclear, -12.6f, Reports.Verdict.Likely)
        assertEquals("−13%", ReportCopy.habitValue(hrvOnly))
        assertDots(ReportCopy.habitValue(hrvOnly))
        assertEquals("HRV, likely over 9 mornings", ReportCopy.habitStatus(hrvOnly))
        assertTrue(!ReportCopy.habitOff(hrvOnly))
        val none = effect(1f, Reports.Verdict.Unclear, 0.5f, Reports.Verdict.Unclear)
        assertNull(ReportCopy.habitValue(none))
        assertEquals("No clear effect, 9 mornings", ReportCopy.habitStatus(none))
        val few = Reports.Effect(Reports.Habit.ShortNight, 2, 57, null, null, null, Reports.Verdict.TooFew, null, Reports.Verdict.TooFew, false, emptyList())
        assertEquals("Too few mornings yet, 2 of 4", ReportCopy.habitStatus(few))
        assertEquals("Almost every morning, nothing to compare", ReportCopy.habitStatus(few.copy(mornings = 58, others = 2)))
        // Only the habits whose effect shows get a row; the rest share a row each for what they have in common.
        val all = listOf(clear, hrvOnly, none, few, few.copy(habit = Reports.Habit.Weekend, mornings = 58, others = 2), none.copy(habit = Reports.Habit.HardDay))
        assertEquals(listOf(clear, hrvOnly), ReportCopy.shownHabits(all))
        assertEquals(
            listOf("No clear effect" to "Late workout and hard day", "Too few mornings yet" to "Short night", "After almost every morning" to "Weekend night"),
            ReportCopy.quietHabits(all),
        )
        assertEquals(emptyList<Pair<String, String>>(), ReportCopy.quietHabits(listOf(clear)))
        listOf(Reports.Habit.Workout, Reports.Habit.LateWorkout, Reports.Habit.HardDay, Reports.Habit.LateBedtime, Reports.Habit.ShortNight, Reports.Habit.Weekend, Reports.Habit.ActiveDay)
            .forEach { assertTrue(ReportCopy.habitName(it).length <= 14) }
    }

    @Test fun `the days of the week and the sports read plainly`() {
        fun wd(d: DayOfWeek, r: Float?) = Reports.Weekday(d, r, 80f, 450f, 8)
        val weekdays = listOf(62f, 72f, 74f, 71f, 73f, 75f, 70f).mapIndexed { i, r -> wd(DayOfWeek.of(i + 1), r) }
        assertEquals(DayOfWeek.MONDAY, ReportCopy.lowWeekday(weekdays)?.day)
        assertEquals("Mondays are your lowest, 9 under your average.", ReportCopy.weekdayLine(weekdays))
        val alike = listOf(70f, 72f, 71f, 70f, 72f, 71f, 70f).mapIndexed { i, r -> wd(DayOfWeek.of(i + 1), r) }
        assertNull(ReportCopy.lowWeekday(alike))
        assertEquals("Your days of the week are much alike.", ReportCopy.weekdayLine(alike))
        assertNull(ReportCopy.weekdayLine(weekdays.mapIndexed { i, w -> if (i < 5) w.copy(recovery = null) else w }))
        val sport = Reports.SportTotals(130, "Cross-training", 3, 10_800, listOf(0, 0, 0, 0, 0), 2f)
        assertEquals("3 h in all, vs 2 sessions", ReportCopy.sportStatus(sport))
        assertEquals("3 h in all, vs 1 session", ReportCopy.sportStatus(sport.copy(sessionsBefore = 1f)))
        assertEquals("3 h in all", ReportCopy.sportStatus(sport.copy(sessionsBefore = null)))
        assertEquals("1 day", ReportCopy.days(1))
    }

    @Test fun `the info sheet's words keep to the copy rules`() {
        assertCopy(ReportCopy.INFO_MEANING)
        ReportCopy.INFO_ROWS.forEach { (title, text) -> assertCopy(title); assertCopy(text) }
        assertEquals(5, ReportCopy.INFO_ROWS.size)
    }
}
