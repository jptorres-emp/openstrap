package org.openstrap.app.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openstrap.app.data.Reports
import org.openstrap.app.ui.glow.draw.DotFont
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.Locale

class ReportCopyTest {
    private val week = Reports.Figures(
        span = 7, recovery = 74.2f, scored = 7, ready = 6f, hrv = 56.2f, restingHr = 52.8f, asleep = 463.3f, restorative = 224.7f,
        bedtime = LocalTime.of(23, 20), wake = LocalTime.of(7, 13), bedtimeShift = 44.3f, wakeShift = 33.5f, load = 94.4f,
        usualLoad = 76.6f, workouts = 4f, workoutSeconds = 15_300f, steps = 14_765.8f,
    )
    private val before = week.copy(
        span = 21, recovery = 72.4f, hrv = 53.9f, restingHr = 52.7f, asleep = 449.5f, bedtimeShift = 31.2f, wakeShift = 37.5f,
        load = 69.2f, workouts = 3f, workoutSeconds = 11_700f, steps = 13_731.8f,
    )

    /** Every list value is drawn in dots: only the dot font's glyphs (a locale's grouping space draws as a space). */
    private fun assertDots(value: String?) {
        if (value == null) return
        value.forEach { c -> assertTrue("'$c' in \"$value\" has no dot glyph", DotFont.has(c) || c == ' ' || c == ' ') }
    }

    private fun assertCopy(text: String) {
        assertTrue("\"$text\"", text.none { it in setOf('·', ';', '—', '!') })
    }

    @Test fun `the week's rows compare with an average week before, in dot-safe values`() {
        val rows = ReportCopy.weekRows(week, before)
        rows.forEach { assertDots(it.value); assertCopy(it.status) }
        val byLine = rows.associateBy { it.line }
        assertEquals("74", byLine.getValue(ReportCopy.Line.Recovery).value)
        assertEquals("Average, +2 vs 72", byLine.getValue(ReportCopy.Line.Recovery).status)
        assertEquals("7:43", byLine.getValue(ReportCopy.Line.Asleep).value)
        assertEquals("A night, +13 min vs 7 h 30", byLine.getValue(ReportCopy.Line.Asleep).status)
        assertEquals("bpm a night, as before", byLine.getValue(ReportCopy.Line.RestingHr).status)
        assertEquals("23:20", byLine.getValue(ReportCopy.Line.Bedtime).value)
        assertEquals("Moves 44 min night to night, 31 before", byLine.getValue(ReportCopy.Line.Bedtime).status)
        assertEquals("4:15", byLine.getValue(ReportCopy.Line.Training).value)
        assertEquals("This week, 3 h 15 a week before", byLine.getValue(ReportCopy.Line.Training).status)
        assertTrue(rows.none { it.off })
        // Without a week before, no comparison.
        assertEquals("Average", ReportCopy.weekRows(week, null).first().status)
    }

    @Test fun `a worse week lights its rows, with a true minus`() {
        val worse = week.copy(recovery = 60f, hrv = 48f, restingHr = 56f, asleep = 400f, bedtimeShift = 70f)
        val rows = ReportCopy.weekRows(worse, before).associateBy { it.line }
        assertEquals("Average, −12 vs 72", rows.getValue(ReportCopy.Line.Recovery).status)
        listOf(ReportCopy.Line.Recovery, ReportCopy.Line.Hrv, ReportCopy.Line.RestingHr, ReportCopy.Line.Asleep, ReportCopy.Line.Bedtime).forEach {
            assertTrue("$it lit", rows.getValue(it).off)
        }
        assertTrue(!rows.getValue(ReportCopy.Line.Load).off)
    }

    @Test fun `the month's rows read the 30 days before`() {
        val rows = ReportCopy.monthRows(week.copy(span = 30, ready = 25f, workouts = 13f), before.copy(span = 30, ready = 17f, workouts = 16f))
        rows.forEach { assertDots(it.value); assertCopy(it.status) }
        val byLine = rows.associateBy { it.line }
        assertEquals("Days at 70 or more, +8 vs 17", byLine.getValue(ReportCopy.Line.Ready).status)
        assertEquals("In 30 days, 16 the 30 before", byLine.getValue(ReportCopy.Line.Workouts).status)
        assertEquals("3:45", byLine.getValue(ReportCopy.Line.Restorative).value)
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
        val month = ReportCopy.monthSentence(week.copy(ready = 25f), before.copy(recovery = 67f, asleep = 430f))
        assertEquals("Recovery averaged 74, 7 up on the 30 days before. You slept 33 min more a night. Your Load was 36% higher. 25 days were Ready or better.", month)
    }

    @Test fun `the target reads as dots and says what the day is for`() {
        val t = Reports.Target(Reports.DayKind.Usual, 110..205, personal = true, fromStrap = false, scored = true)
        assertEquals("110-205", ReportCopy.targetValue(t))
        assertDots(ReportCopy.targetValue(t))
        assertEquals("Ready for your usual training. 35 so far.", ReportCopy.targetStatus(t, 35.2f, finished = false))
        assertEquals("Ready for your usual training. 230 so far, over it already.", ReportCopy.targetStatus(t, 230f, finished = false))
        assertEquals("A day for your usual training. Load 94, below it.", ReportCopy.targetStatus(t, 94f, finished = true))
        val strap = t.copy(kind = Reports.DayKind.Recover, fromStrap = true, personal = false)
        assertEquals("A day to recover. Keep it light. From the strap's score while Recovery learns.", ReportCopy.targetStatus(strap, null, false))
        assertEquals(
            listOf("4" to "Within target", "2" to "Below", "0" to "Above"),
            ReportCopy.balanceFigures(Reports.Balance(below = 2f, within = 4f, above = 0f)),
        )
    }

    @Test fun `tonight's plan explains what it adds`() {
        val plan = Reports.SleepPlan(505, 480, 25, 0, 0, 0.95f, LocalTime.of(7, 5), LocalTime.of(22, 12))
        assertEquals("22:12", ReportCopy.bedtimeValue(plan))
        assertEquals("For 8 h 25 asleep, up at 07:05. Includes 25 min for this week's short nights.", ReportCopy.planStatus(plan))
        val busy = plan.copy(loadMinutes = 15, napMinutes = 40)
        assertEquals(
            "For 8 h 25 asleep, up at 07:05. Includes 25 min for this week's short nights and 15 min after a hard day. Less 40 min for today's nap.",
            ReportCopy.planStatus(busy),
        )
        assertNull(ReportCopy.bedtimeValue(plan.copy(bedtime = null, wake = null)))
        assertEquals("Aim for 8 h 25 asleep. Bedtime shows after a few nights.", ReportCopy.planStatus(plan.copy(bedtime = null, wake = null, shortfallMinutes = 0)))
    }

    @Test fun `a habit shows its effect only when it's clear or likely`() {
        fun effect(rec: Float?, rv: Reports.Verdict, hrv: Float?, hv: Reports.Verdict, with: List<Reports.Habit> = emptyList()) =
            Reports.Effect(Reports.Habit.LateWorkout, 9, 50, 64f, 72f, rec, rv, hrv, hv, adjusted = true, with = with)
        val clear = effect(-8.4f, Reports.Verdict.Clear, -12.6f, Reports.Verdict.Likely, listOf(Reports.Habit.Sport(18, "Soccer")))
        assertEquals("−8", ReportCopy.habitValue(clear))
        assertDots(ReportCopy.habitValue(clear))
        assertEquals("Recovery points the next morning, clear over 9 mornings. HRV −13%. Often with soccer.", ReportCopy.habitStatus(clear))
        assertTrue(ReportCopy.habitOff(clear))
        val hrvOnly = effect(-1f, Reports.Verdict.Unclear, -12.6f, Reports.Verdict.Likely)
        assertEquals("−13%", ReportCopy.habitValue(hrvOnly))
        assertDots(ReportCopy.habitValue(hrvOnly))
        assertEquals("HRV the next morning, likely over 9 mornings. No clear effect on Recovery.", ReportCopy.habitStatus(hrvOnly))
        assertTrue(!ReportCopy.habitOff(hrvOnly))
        val none = effect(1f, Reports.Verdict.Unclear, 0.5f, Reports.Verdict.Unclear)
        assertNull(ReportCopy.habitValue(none))
        assertEquals("No clear effect on the next morning, over 9 mornings.", ReportCopy.habitStatus(none))
        val few = Reports.Effect(Reports.Habit.ShortNight, 2, 57, null, null, null, Reports.Verdict.TooFew, null, Reports.Verdict.TooFew, false, emptyList())
        assertEquals("Too few mornings after it to tell yet, 2 mornings so far", ReportCopy.habitStatus(few))
        listOf(Reports.Habit.Workout, Reports.Habit.LateWorkout, Reports.Habit.HardDay, Reports.Habit.LateBedtime, Reports.Habit.ShortNight, Reports.Habit.Weekend, Reports.Habit.ActiveDay)
            .forEach { assertTrue(ReportCopy.habitName(it).length <= 14) }
    }

    @Test fun `the spread and the days of the week read plainly`() {
        assertEquals(listOf("Take it easy", "Moderate", "Ready", "Ready for more"), ReportCopy.BANDS)
        assertEquals("1 day", ReportCopy.days(1))
        assertEquals("4 days in the 30 days before", ReportCopy.bandBefore(4))
        assertEquals("Monday", ReportCopy.weekdayName(DayOfWeek.MONDAY, Locale.UK))
        assertEquals("Load 77, 7 h 34 asleep, 8 days", ReportCopy.weekdayDetail(Reports.Weekday(DayOfWeek.MONDAY, 70f, 76.8f, 454.3f, 8)))
        val sport = Reports.SportTotals(130, "Cross-training", 3, 10_800, listOf(0, 0, 0, 0, 0), 2f)
        assertEquals("3 h in total, 2 a week before", ReportCopy.sportStatus(sport, month = false))
    }

    @Test fun `the info sheet's words keep to the copy rules`() {
        assertCopy(ReportCopy.INFO_MEANING)
        ReportCopy.INFO_ROWS.forEach { (title, text) -> assertCopy(title); assertCopy(text) }
        assertEquals(5, ReportCopy.INFO_ROWS.size)
    }
}
