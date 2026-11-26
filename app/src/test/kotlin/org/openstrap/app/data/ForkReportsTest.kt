package org.openstrap.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.random.Random

/**
 * The fork's reports: the week and the month in figures, the day's Load target and each day's against its own,
 * tonight's bedtime, the sports and the days of the week, the habits' effects and the journal's tags.
 */
class ForkReportsTest {
    private val today = LocalDate.of(2026, 11, 21)
    private fun day(back: Long, recovery: Int? = 60, load: Float? = 40f, steps: Int? = 8000, hrv: Float? = 50f) =
        ForkReports.Day(today.minusDays(back), recovery = recovery, load = load, steps = steps, asleepMinutes = 450, hrv = hrv)
    private fun run(seconds: Long = 1800, late: Boolean = false) = ForkReports.Session(RUN, seconds, late)

    @Test fun `the week is the last 7 days, against the 3 weeks before as a week on average`() {
        val days = (27L downTo 0L).map { back ->
            if (back < 7) day(back, recovery = 80).copy(sessions = listOf(run()))
            else day(back, recovery = 50).copy(sessions = if (back % 7 == 0L) listOf(run()) else emptyList())
        }
        val week = ForkReports.week(days, today)
        assertEquals(7, week.days.size)
        assertEquals(80f, week.now.recovery!!, 0.001f)
        assertEquals(7f, week.now.ready, 0.001f)
        assertEquals(7f, week.now.workouts, 0.001f)
        assertEquals(7 * 1800f, week.now.workoutSeconds, 0.001f)
        assertEquals(50f, week.before!!.recovery!!, 0.001f)
        assertEquals(7f, week.before!!.low, 0.001f)
        // Three workouts over the three weeks before: one a week.
        assertEquals(1f, week.before!!.workouts, 0.001f)
        assertEquals(ForkTally.Sport(RUN, 7, 7 * 1800L, 1f), week.sports.single())
    }

    @Test fun `today's Load and steps are left out until the day is over, and a month without one before has none`() {
        val days = listOf(day(1, load = 40f, steps = 6000), day(0, load = 5f, steps = 100).copy(partial = true))
        val month = ForkReports.month(days, today)
        assertEquals(40f, month.now.load!!, 0.001f)
        assertEquals(6000f, month.now.steps!!, 0.001f)
        assertNull(month.before)
    }

    @Test fun `bedtimes and wakings average and vary in minutes, and the usual Load is read at each end`() {
        val days = listOf(
            day(2).copy(bedtime = 600, wakeTime = 400), day(1).copy(bedtime = 660, wakeTime = 420),
            day(0).copy(bedtime = 720, wakeTime = 440, deepRem = 150),
        )
        val usual = mapOf(today.minusDays(10) to 30f, today.minusDays(1) to 42f)
        val week = ForkReports.week(days, today, usual)
        assertEquals(660f, week.now.bedtime!!, 0.001f)
        assertEquals(49f, week.now.bedtimeSpread!!, 0.1f)
        assertEquals(420f, week.now.wakeTime!!, 0.001f)
        assertEquals(150f, week.now.deepRem!!, 0.001f)
        assertEquals(42f, week.usualNow!!, 0.001f)
        assertEquals(30f, week.usualBefore!!, 0.001f)
    }

    @Test fun `your normal is the middle half of the scored mornings, once there are 14`() {
        val days = (0L until 20L).map { day(it, recovery = 41 + it.toInt()) }
        val normal = ForkReports.normal(days)!!
        assertTrue(normal.first in 45..47 && normal.last in 54..56)
        assertNull(ForkReports.normal(days.take(13)))
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

    @Test fun `each finished day is read against its own target, and the counts follow`() {
        // A usual Load of 40 and a usual morning (75): the target is 30 to 50, built round the usual.
        val usual = (10L downTo 1L).associate { today.minusDays(it + 7) to 40f }
        val days = listOf(day(3, recovery = 75, load = 10f), day(2, recovery = 75, load = 40f), day(1, recovery = 75, load = 90f))
        val marked = ForkTarget.mark(days + day(0, recovery = 75).copy(partial = true), emptyMap(), emptySet(), usual)
        assertEquals(
            listOf(ForkTarget.Versus.Below, ForkTarget.Versus.Within, ForkTarget.Versus.Above, null), marked.map { it.versus },
        )
        val week = ForkReports.figures(marked)
        assertEquals(listOf(1f, 1f, 1f), listOf(week.below, week.within, week.above))
        assertEquals(40f, ForkTarget.usualBefore(usual, today)!!, 0.001f)
        assertNull(ForkTarget.usualBefore(usual, today.minusDays(20)))
    }

    @Test fun `tonight's bedtime is the sleep you need and the week's shortfall before your usual waking`() {
        // 7 h 30 asleep a night against a need of 8 h, 500 min in bed (90%), up at 07:05.
        val days = (13L downTo 0L).map { day(it).copy(wakeTime = 425, inBedMinutes = 500) }
        assertEquals(30, ForkPlan.extra(days, need = 480))
        assertEquals(0.9f, ForkPlan.efficiency(days), 0.001f)
        val plan = ForkPlan.plan(days, need = 480, target = null, load = 12f)
        assertEquals(425, plan.wake)
        assertEquals(510, plan.asleep)
        // 510 asleep at 90% is 567 in bed: 07:05 less 9 h 27 is 21:38.
        assertEquals(21 * 60 + 38, plan.bedtime)
        assertEquals(60, plan.recovery)
        assertEquals(60, ForkPlan.extra(days.map { it.copy(asleepMinutes = 300) }, need = 480))
        assertNull(ForkPlan.plan(days.map { it.copy(wakeTime = null) }, need = 480, target = null, load = 0f).bedtime)
    }

    @Test fun `the sports go most sessions first, and the days of the week say which is best`() {
        val now = listOf(
            day(1).copy(sessions = listOf(ForkReports.Session(SOCCER, 3600), run())),
            day(0).copy(sessions = listOf(ForkReports.Session(SOCCER, 3600), run(), run())),
        )
        val before = (8L..28L step 7).map { day(it).copy(sessions = listOf(run(), run())) }
        val sports = ForkTally.sports(now, before, stretches = 3)
        assertEquals(listOf(RUN, SOCCER), sports.map { it.sport })
        assertEquals(2f, sports[0].before!!, 0.001f)
        assertEquals(0f, sports[1].before!!, 0.001f)
        val days = (0L until 28L).map { b -> day(b, recovery = if (today.minusDays(b).dayOfWeek == DayOfWeek.MONDAY) 50 else 70) }
        val weekdays = ForkTally.weekdays(days)
        assertEquals(50f, weekdays[0]!!, 0.001f)
        assertEquals(DayOfWeek.MONDAY, ForkTally.bestAndWorst(weekdays)!!.second)
        assertNull(ForkTally.bestAndWorst(weekdays.map { it?.let { 70f } }))
    }

    @Test fun `a ticked habit that drops the next morning's HRV shows as a clear effect, counted only over answered days`() {
        val days = (40L downTo 0L).map { back ->
            val alcohol = back % 3 == 0L
            val answered = back < 30
            day(back).copy(
                journal = if (answered) (if (alcohol) setOf(JournalHabit.Alcohol) else emptySet()) else null,
                hrv = if ((back + 1) % 3 == 0L && back + 1 < 30) 37.5f else 50f,
            )
        }
        val alcohol = ForkHabits.effects(days, usualLoad = 40f).first { it.habit == ForkHabits.Habit.Logged(JournalHabit.Alcohol) }
        assertEquals(ForkHabits.Verdict.Clear, alcohol.verdict)
        assertEquals(-25f, alcohol.percent!!, 0.001f)
        assertTrue(alcohol.mornings + alcohol.others <= 30)
    }

    @Test fun `a sport and the late workout it comes with are each other's company`() {
        val days = (30L downTo 0L).map { back ->
            val soccer = back % 3 == 0L
            day(back).copy(
                sessions = if (soccer) listOf(ForkReports.Session(SOCCER, 5400, late = true)) else emptyList(),
                hrv = if ((back + 1) % 3 == 0L) 40f else 50f,
            )
        }
        val effects = ForkHabits.effects(days, usualLoad = 40f)
        val sport = effects.first { it.habit == ForkHabits.Habit.Sport(SOCCER) }
        assertEquals(ForkHabits.Verdict.Clear, sport.verdict)
        assertEquals(ForkHabits.Habit.LateWorkout, sport.along)
        assertEquals(ForkHabits.Habit.Sport(SOCCER), effects.first { it.habit == ForkHabits.Habit.LateWorkout }.along)
        assertNull(effects.first { it.habit == ForkHabits.Habit.Workout }.along)
    }

    @Test fun `too few mornings either way say nothing, and alike groups find no effect`() {
        val few = (5L downTo 0L).map { back -> day(back).copy(journal = setOf(JournalHabit.Travel)) }
        val travel = ForkHabits.effects(few, usualLoad = null).first { it.habit == ForkHabits.Habit.Logged(JournalHabit.Travel) }
        assertEquals(ForkHabits.Verdict.TooFew, travel.verdict)
        assertNull(travel.percent)
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

    private companion object {
        const val RUN = 1
        const val SOCCER = 52
    }
}
