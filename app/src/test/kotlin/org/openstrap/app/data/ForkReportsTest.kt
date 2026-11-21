package org.openstrap.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

/** The fork's reports: the week and the month in figures, the day's Load target, the habits' effects and the journal's tags. */
class ForkReportsTest {
    private val today = LocalDate.of(2026, 11, 21)
    private fun day(back: Long, recovery: Int? = 60, load: Float? = 40f, steps: Int? = 8000) =
        ForkReports.Day(today.minusDays(back), recovery = recovery, load = load, steps = steps, asleepMinutes = 450)

    @Test fun `the week is the last 7 days, against the 3 weeks before as a week on average`() {
        val days = (27L downTo 0L).map { back ->
            if (back < 7) day(back, recovery = 80).copy(workouts = 1) else day(back, recovery = 50).copy(workouts = if (back % 7 == 0L) 1 else 0)
        }
        val week = ForkReports.week(days, today)
        assertEquals(80f, week.now.recovery!!, 0.001f)
        assertEquals(7f, week.now.ready, 0.001f)
        assertEquals(7f, week.now.workouts, 0.001f)
        assertEquals(50f, week.before!!.recovery!!, 0.001f)
        assertEquals(7f, week.before!!.low, 0.001f)
        // Three workouts over the three weeks before: one a week.
        assertEquals(1f, week.before!!.workouts, 0.001f)
    }

    @Test fun `today's Load and steps are left out until the day is over, and a month without one before has none`() {
        val days = listOf(day(1, load = 40f, steps = 6000), day(0, load = 5f, steps = 100).copy(partial = true))
        val month = ForkReports.month(days, today)
        assertEquals(40f, month.now.load!!, 0.001f)
        assertEquals(6000f, month.now.steps!!, 0.001f)
        assertNull(month.before)
    }

    @Test fun `the target comes from your own training days, and from your usual Load before there are four`() {
        val loads = (1L..20L).associate { today.minusDays(it) to if (it % 2 == 0L) 80f else 10f }
        val training = loads.filterValues { it == 80f }.keys
        val usual = ForkTarget.target(day(0, recovery = 75), loads, training, usualLoad = 40f)!!
        assertEquals(ForkTarget.DayKind.Usual, usual.kind)
        assertTrue(usual.personal)
        assertEquals(70..90, usual.range)
        val recover = ForkTarget.target(day(0, recovery = 40), loads, training, usualLoad = 40f)!!
        assertEquals(ForkTarget.DayKind.Recover, recover.kind)
        assertEquals(0..40, recover.range)
        val early = ForkTarget.target(day(0, recovery = 90), emptyMap(), emptySet(), usualLoad = 40f)!!
        assertEquals(ForkTarget.DayKind.Push, early.kind)
        assertEquals(false, early.personal)
        assertEquals(40..70, early.range)
        assertNull(ForkTarget.target(day(0), emptyMap(), emptySet(), usualLoad = null))
    }

    @Test fun `a target is at least 20 wide, in steps of 5, and a Load reads against it rounded`() {
        assertEquals(30..50, ForkTarget.rounded(38f, 42f))
        assertEquals(0..20, ForkTarget.rounded(0f, 3f))
        assertEquals(ForkTarget.Versus.Within, ForkTarget.versus(50.4f, 30..50))
        assertEquals(ForkTarget.Versus.Above, ForkTarget.versus(50.6f, 30..50))
        assertEquals(ForkTarget.Versus.Below, ForkTarget.versus(29.4f, 30..50))
        assertEquals(ForkTarget.DayKind.Recover, ForkTarget.kindOf(90, alert = true))
        assertEquals(ForkTarget.DayKind.Moderate, ForkTarget.kindOf(60, alert = false))
        assertEquals(ForkTarget.DayKind.Usual, ForkTarget.kindOf(null, alert = false))
    }

    @Test fun `a ticked habit that drops the next morning shows as a clear effect, counted only over answered days`() {
        val days = (40L downTo 0L).map { back ->
            val alcohol = back % 3 == 0L
            val answered = back < 30
            day(back).copy(
                journal = if (answered) (if (alcohol) setOf(JournalHabit.Alcohol) else emptySet()) else null,
                recovery = if ((back + 1) % 3 == 0L && back + 1 < 30) 45 else 70,
            )
        }
        val alcohol = ForkHabits.effects(days, usualLoad = 40f).first { it.habit == ForkHabits.Habit.Logged(JournalHabit.Alcohol) }
        assertEquals(ForkHabits.Verdict.Clear, alcohol.verdict)
        assertEquals(-25f, alcohol.points!!, 0.001f)
        assertTrue(alcohol.percent!! < -30f)
        assertTrue(alcohol.mornings + alcohol.others <= 30)
    }

    @Test fun `too few mornings either way say nothing, and alike groups find no effect`() {
        val few = (5L downTo 0L).map { back -> day(back).copy(journal = setOf(JournalHabit.Travel)) }
        val travel = ForkHabits.effects(few, usualLoad = null).first { it.habit == ForkHabits.Habit.Logged(JournalHabit.Travel) }
        assertEquals(ForkHabits.Verdict.TooFew, travel.verdict)
        assertNull(travel.points)
        val p = ForkHabits.permutationP(listOf(60f, 61f, 59f, 60f), listOf(60f, 60f, 61f, 59f), Random(ForkHabits.SEED))
        assertTrue(p > 0.5f)
    }

    @Test fun `the journal keeps a day's answer as tags, nothing ticked still answered`() {
        assertNull(JournalHabit.ofDay(emptyList()))
        assertEquals(emptySet<JournalHabit>(), JournalHabit.ofDay(JournalHabit.tags(emptySet())))
        val tags = JournalHabit.tags(setOf(JournalHabit.Alcohol, JournalHabit.Meditation))
        assertEquals(setOf(JournalHabit.Alcohol, JournalHabit.Meditation), JournalHabit.ofDay(tags + "other"))
        // A habit tagged before the journal kept answers counts as answered.
        assertEquals(setOf(JournalHabit.Stress), JournalHabit.ofDay(listOf("stress")))
    }
}
