package org.openstrap.app.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.openstrap.app.data.ForkHabits
import org.openstrap.app.data.ForkPlan
import org.openstrap.app.data.ForkReports
import org.openstrap.app.data.ForkTarget
import org.openstrap.app.testing.LocaleRule
import java.time.DayOfWeek
import java.util.Locale

/** The words of the fork's Reports page, in English: the sentence, today's lines, the rows and the habits. */
class ReportsCopyTest {
    @get:Rule val localeRule = LocaleRule()

    private fun figures(
        recovery: Float? = 74f, load: Float? = 34f, hrv: Float? = 52f, bedtime: Float? = 700f, spread: Float? = 44f,
        within: Float = 3f, below: Float = 4f, above: Float = 0f,
    ) = ForkReports.Figures(
        recovery, 3f, 3f, 1f, hrv, 54f, 452f, 145f, bedtime, spread, 425f, 20f, load, 4f, 15_300f, 8432f, within, below, above,
    )

    private fun stretch(now: ForkReports.Figures, before: ForkReports.Figures?) =
        ForkReports.Stretch(emptyList(), now, before, emptyList(), 41f, 38f)

    private fun plan(target: ForkTarget.Target?, bedtime: Int? = 22 * 60 + 12, extra: Int = 25) =
        ForkPlan.Plan(82, fromStrap = false, alert = false, target, 11.4f, bedtime, 505, 7 * 60 + 5, extra)

    @Test fun `the sentence reads Recovery and Load against the stretch before`() {
        val week = stretch(figures(), figures(recovery = 72f, load = 25f))
        assertEquals(
            "Recovery averaged 74, 2 up on the 3 weeks before. Your Load was 36% higher.", ReportsCopy.sentence(week, month = false),
        )
        val month = stretch(figures(recovery = 70f, load = 25.5f), figures(recovery = 73f, load = 25f))
        assertEquals("Recovery averaged 70, 3 down on the 30 days before. Your Load was about the same.", ReportsCopy.sentence(month, month = true))
        assertEquals("Recovery averaged 74.", ReportsCopy.sentence(stretch(figures(), null), month = true))
        assertNull(ReportsCopy.sentence(stretch(figures(recovery = null), null), month = false))
    }

    @Test fun `today says the target with the Load so far, and the bedtime with the sleep it allows`() {
        val target = ForkTarget.Target(ForkTarget.DayKind.Usual, 110..205, personal = true, fromStrap = false)
        assertEquals("110 to 205" to "A usual day, 11 so far", ReportsCopy.target(plan(target)))
        assertEquals(null to "After a week of wear", ReportsCopy.target(plan(null)))
        assertEquals("22:12" to "8 h 25 asleep, up at 07:05, 25 min more for short nights", ReportsCopy.bedtime(plan(target)))
        assertEquals("22:12" to "8 h 25 asleep, up at 07:05", ReportsCopy.bedtime(plan(target, extra = 0)))
        assertEquals(null to "After a night with its waking", ReportsCopy.bedtime(plan(target, bedtime = null)))
    }

    @Test fun `counts that are averages keep a decimal, and the days of the week name the best and the worst`() {
        assertEquals("3", ReportsCopy.amount(3f))
        assertEquals("2.3", ReportsCopy.amount(2.3f))
        assertEquals(
            "An average week before had 3 within, 2.3 below and 0 above.",
            ReportsCopy.versusBefore(figures(within = 3f, below = 2.3f, above = 0f), month = false),
        )
        val weekdays = listOf(55f, 70f, 70f, 70f, 70f, 70f, 80f)
        assertEquals("Sunday is your best day for Recovery, Monday your worst.", ReportsCopy.weekdays(weekdays, Locale.UK))
        assertEquals("Your days of the week are much alike.", ReportsCopy.weekdays(List(7) { 70f }, Locale.UK))
        assertNull(ReportsCopy.weekdays(List(7) { null }, Locale.UK))
        assertEquals(DayOfWeek.MONDAY.getDisplayName(java.time.format.TextStyle.SHORT, Locale.UK), ReportsCopy.weekdayLabels(Locale.UK).first())
    }

    @Test fun `the stretch's rows have a value and the stretch before under it`() {
        val rows = ReportsRows.compare(stretch(figures(), figures(hrv = 49f, spread = 31f)), month = true)
        assertEquals(ReportRow("HRV", "52 ms", "vs 49 ms"), rows.first())
        assertEquals(ReportRow("Bedtime", "23:40", "Varies by 44 min, vs 31"), rows.first { it.title == "Bedtime" })
        assertEquals(ReportRow("Your usual Load", "41", "vs 38"), rows.first { it.title == "Your usual Load" })
        assertEquals("4 h 15 in all, vs 4", rows.first { it.title == "Workouts" }.line)
        // The week has no deep and REM row and no usual Load, and a row without a value is left out.
        val week = ReportsRows.compare(stretch(figures(hrv = null), null), month = false)
        assertEquals(listOf("Resting heart rate", "Asleep", "Bedtime", "Wake time", "Load", "Workouts", "Steps"), week.map { it.title })
        assertNull(week.first().line)
    }

    @Test fun `a habit's change is signed, its line says how sure and what it came with, and the rest are listed`() {
        val sport = ForkHabits.Effect(
            ForkHabits.Habit.LateWorkout, 20, 30, 45f, 50f, -7.4f, ForkHabits.Verdict.Clear, along = ForkHabits.Habit.ShortNight,
        )
        assertEquals("−7%", HabitWords.change(sport))
        assertEquals("Clear, over 20 mornings, often with under 7 h asleep", HabitWords.line(sport))
        assertEquals("+3%", HabitWords.change(sport.copy(percent = 2.6f)))
        assertEquals("Likely, over 1 morning", HabitWords.line(sport.copy(mornings = 1, verdict = ForkHabits.Verdict.Likely, along = null)))
        val unclear = listOf(ForkHabits.Habit.WeekendNight, ForkHabits.Habit.ShortNight, ForkHabits.Habit.HardDay)
            .map { ForkHabits.Effect(it, 10, 40, 50f, 50f, 0.5f, ForkHabits.Verdict.Unclear) }
        assertEquals("No clear effect: Weekend night, under 7 h asleep and hard training day.", HabitWords.none(unclear))
        assertNull(HabitWords.none(listOf(sport)))
        assertNull(HabitWords.few(unclear))
    }
}
