package org.openstrap.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openstrap.app.data.Reports.DayKind
import org.openstrap.app.data.Reports.Habit
import org.openstrap.app.data.Reports.Verdict
import org.openstrap.app.data.Reports.Versus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlin.random.Random

class ReportsTest {
    /** A Friday, so the night after it leads into a weekend morning. */
    private val end = LocalDate.of(2026, 10, 23)

    private fun session(date: LocalDate, sport: Int = 130, hour: Int = 19, minutes: Long = 60, byYou: Boolean = true) =
        Reports.Session(date, LocalTime.of(hour, 0), sport, if (sport == 130) "CrossFit" else "Football", minutes * 60, listOf(600, 900, 1200, 600, 300), byYou)

    @Test fun `the morning decides the kind of day`() {
        assertEquals(DayKind.Recover, Reports.kindOf(90, null, alert = true))
        assertEquals(DayKind.Recover, Reports.kindOf(54, null, alert = false))
        assertEquals(DayKind.Moderate, Reports.kindOf(55, null, alert = false))
        assertEquals(DayKind.Moderate, Reports.kindOf(69, Recovery.HrvRange.Within, alert = false))
        assertEquals(DayKind.Usual, Reports.kindOf(70, null, alert = false))
        assertEquals(DayKind.Usual, Reports.kindOf(84, Recovery.HrvRange.Above, alert = false))
        assertEquals(DayKind.Push, Reports.kindOf(85, null, alert = false))
        // HRV below its range holds a high score back to a moderate day.
        assertEquals(DayKind.Moderate, Reports.kindOf(92, Recovery.HrvRange.Below, alert = false))
        assertEquals(DayKind.Usual, Reports.kindOf(null, null, alert = false))
    }

    @Test fun `a target of your own comes from your training days`() {
        // Six weeks: a session every other day at 60, 80 ... 260, rest days at 20.
        val loads = (1..42).associate { back -> end.minusDays(back.toLong()) to if (back % 2 == 0) 40f + 10f * back else 20f }
        val training = loads.keys.filter { (end.toEpochDay() - it.toEpochDay()) % 2 == 0L }.toSet()
        val sessions = loads.filterKeys { it in training }.values.toList()
        fun targetAt(score: Int?) = Reports.target(Reports.Day(end, recovery = score), loads, training)!!
        val usual = targetAt(75)
        assertEquals(DayKind.Usual, usual.kind)
        assertTrue(usual.personal)
        assertEquals(Reports.rounded(Recovery.percentile(sessions, 0.25f)!!, Recovery.percentile(sessions, 0.75f)!!), usual.range)
        assertEquals(DayKind.Push, targetAt(90).kind)
        assertEquals(Recovery.percentile(sessions, 0.9f)!!.let { Math.ceil(it / 5.0).toInt() * 5 }, targetAt(90).range.last)
        // Recover: up to half an easy session, or a usual rest day if that's more.
        val recover = targetAt(40)
        assertEquals(0, recover.range.first)
        assertEquals(Math.ceil(0.5 * Recovery.percentile(sessions, 0.25f)!! / 5).toInt() * 5, recover.range.last)
        // Each kind asks for less than the next.
        val kinds = listOf(40, 60, 75, 90).map { targetAt(it).range }
        kinds.zipWithNext().forEach { (a, b) -> assertTrue("$a then $b", a.last <= b.last && a.first <= b.first) }
    }

    @Test fun `without training days the target is built round your usual Load`() {
        val day = Reports.Day(end, recovery = 75, usualLoad = 80f)
        val t = Reports.target(day, emptyMap(), emptySet())!!
        assertEquals(60..100, t.range)
        assertTrue(!t.personal && t.scored && !t.fromStrap)
        // The strap's score stands in while Recovery is learning; without any score the day is a usual one.
        val learning = Reports.target(Reports.Day(end, strapRecovery = 50, usualLoad = 80f), emptyMap(), emptySet())!!
        assertEquals(DayKind.Recover, learning.kind)
        assertTrue(learning.fromStrap)
        assertEquals(0..40, learning.range)
        val unscored = Reports.target(Reports.Day(end, usualLoad = 80f), emptyMap(), emptySet())!!
        assertEquals(DayKind.Usual, unscored.kind)
        assertTrue(!unscored.scored)
        assertNull(Reports.target(Reports.Day(end, recovery = 75), emptyMap(), emptySet()))
    }

    @Test fun `a target is whole steps of five and never too narrow`() {
        assertEquals(60..105, Reports.rounded(61f, 101f))
        // Sessions all alike: 20 wide round the middle.
        assertEquals(90..110, Reports.rounded(100f, 102f))
        assertEquals(0..20, Reports.rounded(0f, 4f))
    }

    @Test fun `a day's Load is read against its target as it shows`() {
        assertEquals(Versus.Within, Reports.versus(59.6f, 60..100))
        assertEquals(Versus.Below, Reports.versus(59.4f, 60..100))
        assertEquals(Versus.Within, Reports.versus(100.4f, 60..100))
        assertEquals(Versus.Above, Reports.versus(100.6f, 60..100))
    }

    @Test fun `noon-based clock minutes keep a night in one piece`() {
        assertEquals(690, Reports.afterNoon(LocalTime.of(23, 30)))
        assertEquals(780, Reports.afterNoon(LocalTime.of(1, 0)))
        assertEquals(1140, Reports.afterNoon(LocalTime.of(7, 0)))
        assertEquals(LocalTime.of(23, 30), Reports.clockOf(690))
        assertEquals(LocalTime.of(22, 0), Reports.clockOf(-1440 + 600))
        assertEquals(LocalTime.of(12, 0), Reports.clockOf(1440))
    }

    @Test fun `tonight's plan adds the week's shortfall and a hard day, less today's nap`() {
        // Weekdays only: 7:30 asleep of 8:20 in bed, up at 07:00. The day shown is a Wednesday.
        val wednesday = LocalDate.of(2026, 10, 21)
        val days = (0..27).map { back ->
            val d = wednesday.minusDays(back.toLong())
            val weekend = d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY
            Reports.Day(d, asleepMinutes = 450, inBedMinutes = 500, wake = if (weekend) LocalTime.of(9, 30) else LocalTime.of(7, 0))
        }.associateBy { it.date }
        val plain = Reports.sleepPlan(wednesday, days, 480)
        assertEquals(30, plain.shortfallMinutes)
        assertEquals(0, plain.loadMinutes)
        assertEquals(510, plain.asleepMinutes)
        assertEquals(0.9f, plain.efficiency, 1e-4f)
        assertEquals(LocalTime.of(7, 0), plain.wake)
        // 07:00 less 510 / 0.9 = 566.7 minutes: 21:33.
        assertEquals(LocalTime.of(21, 33), plain.bedtime)

        val hard = days + (wednesday to days.getValue(wednesday).copy(load = 200f, usualLoad = 80f, napMinutes = 40))
        val after = Reports.sleepPlan(wednesday, hard, 480)
        assertEquals(30, after.loadMinutes)
        assertEquals(40, after.napMinutes)
        assertEquals(480 + 30 + 30 - 40, after.asleepMinutes)
        // A nap stands in for an hour of the night at most.
        val napped = Reports.sleepPlan(wednesday, days + (wednesday to days.getValue(wednesday).copy(napMinutes = 200)), 480)
        assertEquals(420, napped.asleepMinutes)

        // The night before a Saturday goes by weekend mornings.
        val friday = LocalDate.of(2026, 10, 23)
        val fridayDays = days + (friday to Reports.Day(friday, asleepMinutes = 450, inBedMinutes = 500, wake = LocalTime.of(7, 0))) +
            (LocalDate.of(2026, 10, 22) to Reports.Day(LocalDate.of(2026, 10, 22), asleepMinutes = 450, inBedMinutes = 500, wake = LocalTime.of(7, 0)))
        assertEquals(LocalTime.of(9, 30), Reports.sleepPlan(friday, fridayDays, 480).wake)
        // Without a night on record there are no times.
        val empty = Reports.sleepPlan(wednesday, emptyMap(), 480)
        assertNull(empty.bedtime)
        assertEquals(480, empty.asleepMinutes)
    }

    @Test fun `figures average the stretch and tell how steady bedtimes were`() {
        val from = end.minusDays(6)
        val beds = listOf("23:00", "23:30", "23:00", null, "00:30", "23:30", "23:00")
        val days = beds.mapIndexed { i, b ->
            Reports.Day(
                from.plusDays(i.toLong()), recovery = 60 + i * 5, hrv = 50f + i, bedtime = b?.let(LocalTime::parse),
                asleepMinutes = 420 + i * 10, load = 100f, steps = 8000, partial = i == 6,
            )
        }.associateBy { it.date }
        val sessions = listOf(session(from), session(from.plusDays(2)), session(from.minusDays(1)))
        val f = Reports.figures(days, sessions, from, end, firstData = from.minusDays(30))
        assertEquals(75f, f.recovery!!, 1e-4f)
        assertEquals(7, f.scored)
        assertEquals(5f, f.ready, 1e-4f)
        assertEquals(53f, f.hrv!!, 1e-4f)
        assertEquals(450f, f.asleep!!, 1e-4f)
        // 30, 30, a missing night, then 60 and 30: pairs of consecutive nights only.
        assertEquals((30f + 30f + 60f + 30f) / 4, f.bedtimeShift!!, 1e-4f)
        assertEquals(LocalTime.of(23, 15), f.bedtime)
        assertEquals(2f, f.workouts, 1e-4f)
        assertEquals(7200f, f.workoutSeconds, 1e-4f)
        assertEquals(7, f.span)
        // An average week of three: totals scaled.
        val three = Reports.figures(days, sessions, from.minusDays(14), end, firstData = from.minusDays(30)).per(7)
        assertEquals(1f, three.workouts, 1e-4f)
        // A stretch before your first day counts no days.
        assertEquals(0, Reports.figures(days, sessions, from.minusDays(60), from.minusDays(40), firstData = from).span)
    }

    @Test fun `the week is compared only once a week before it is on record`() {
        val days = (0..9).map { back -> Reports.Day(end.minusDays(back.toLong()), recovery = 70, load = 100f, usualLoad = 90f) }.associateBy { it.date }
        val first = end.minusDays(9)
        val young = Reports.week(end, days, emptyList(), emptyMap(), first)
        assertNull(young.before)
        assertEquals(7, young.days.size)
        val older = (0..40).map { back -> Reports.Day(end.minusDays(back.toLong()), recovery = 70, load = 100f, usualLoad = 90f) }.associateBy { it.date }
        val targets = older.keys.associateWith { Reports.Target(DayKind.Usual, 60..90, true, false, true) }
        val week = Reports.week(end, older, emptyList(), targets, end.minusDays(40))
        assertNotNull(week.before)
        assertEquals(7f, week.balance.above, 1e-4f)
        assertEquals(7f, week.balanceBefore!!.above, 1e-4f)
        assertEquals(70f..70f, week.normal)
        assertNull(Reports.normal(listOf(60f, 70f, 80f)))
        // 56, 58, 60, 60, 60, 62, 64: mean 60, sample sd 2.58, as History.normal.
        val n = Reports.normal(listOf(56f, 58f, 60f, 60f, 60f, 62f, 64f))!!
        assertEquals(57.42f, n.start, 0.01f)
        assertEquals(62.58f, n.endInclusive, 0.01f)
    }

    @Test fun `the month spreads Recovery over its bands and days of the week`() {
        val scores = listOf(40, 60, 75, 90)
        val days = (0..59).map { back -> Reports.Day(end.minusDays(back.toLong()), recovery = scores[back % 4], asleepMinutes = 420) }.associateBy { it.date }
        val m = Reports.month(end, days, emptyList(), end.minusDays(59))
        assertEquals(30, m.bands.sum())
        assertEquals(listOf(8, 8, 7, 7).sorted(), m.bands.sorted())
        assertNotNull(m.bandsBefore)
        assertEquals(7, m.weekdays.size)
        assertEquals(DayOfWeek.MONDAY, m.weekdays.first().day)
        assertTrue(m.weekdays.all { it.days in 8..9 })
        assertNotNull(m.normal)
    }

    @Test fun `a habit that costs Recovery shows up, one that doesn't stays unclear`() {
        // 60 mornings: a late football game on Tuesdays and Thursdays takes 12 points off the next morning; CrossFit
        // early on Mondays and Wednesdays does nothing. Noise from a fixed seed.
        val noise = Random(3)
        val first = end.minusDays(59)
        val sessions = mutableListOf<Reports.Session>()
        val days = (0..59).map { i ->
            val d = first.plusDays(i.toLong())
            val before = d.minusDays(1)
            if (before.dayOfWeek == DayOfWeek.TUESDAY || before.dayOfWeek == DayOfWeek.THURSDAY) sessions += session(before, sport = 18, hour = 21)
            if (before.dayOfWeek == DayOfWeek.MONDAY || before.dayOfWeek == DayOfWeek.WEDNESDAY) sessions += session(before, sport = 130, hour = 7)
            val late = before.dayOfWeek == DayOfWeek.TUESDAY || before.dayOfWeek == DayOfWeek.THURSDAY
            val score = 72 - (if (late) 12 else 0) + noise.nextInt(-5, 6)
            Reports.Day(d, recovery = score, hrvVsUsual = (if (late) -10 else 0) + noise.nextInt(-4, 5), asleepMinutes = 450, bedtime = LocalTime.of(23, 0))
        }
        val effects = Reports.habits(days, sessions)
        val byHabit = effects.associateBy { it.habit }
        val late = byHabit.getValue(Habit.LateWorkout)
        assertEquals(Verdict.Clear, late.recoveryVerdict)
        assertEquals(-12f, late.recovery!!, 3f)
        assertTrue(late.adjusted)
        assertEquals(Verdict.Clear, late.hrvVerdict)
        // Football is its own habit (more than one sport), and comes with late workouts: the two share one effect.
        val football = byHabit.getValue(Habit.Sport(18, "Football"))
        assertTrue(Habit.LateWorkout in football.with)
        assertTrue(Habit.Sport(18, "Football") in late.with)
        assertEquals(football.recovery!!, late.recovery!!, 1e-4f)
        // CrossFit mornings are never after a game, so on its own CrossFit looks good; held against the games, it does nothing.
        val crossfit = byHabit.getValue(Habit.Sport(130, "CrossFit"))
        assertTrue("plain ${crossfit.recoveryAfter} vs ${crossfit.recoveryOthers}", crossfit.recoveryAfter!! - crossfit.recoveryOthers!! > 3f)
        assertEquals(Verdict.Unclear, crossfit.recoveryVerdict)
        // A workout, the umbrella, keeps its plain difference.
        assertTrue(!byHabit.getValue(Habit.Workout).adjusted)
        // Every night alike: never short, never late.
        assertEquals(Verdict.TooFew, byHabit.getValue(Habit.ShortNight).recoveryVerdict)
        assertEquals(0, byHabit.getValue(Habit.LateBedtime).mornings)
        // The clearest come first; the same days always give the same answer.
        assertTrue(effects.first().verdict == Verdict.Clear)
        assertEquals(effects, Reports.habits(days, sessions))
    }

    @Test fun `with one sport there are no sport habits`() {
        val first = end.minusDays(29)
        val sessions = (0..29 step 2).map { session(first.plusDays(it.toLong())) }
        val days = (0..29).map { Reports.Day(first.plusDays(it.toLong()), recovery = 70) }
        assertTrue(Reports.habits(days, sessions).none { it.habit is Habit.Sport })
        assertTrue(Reports.habits(emptyList(), sessions).isEmpty())
    }

    @Test fun `the permutation test tells a real difference from none`() {
        val random = Random(1)
        val same = Reports.permutationP(listOf(1f, 2f, 3f, 4f, 5f), listOf(1f, 2f, 3f, 4f, 5f), random)
        assertTrue("p $same", same > 0.9f)
        val apart = Reports.permutationP(List(10) { 80f + it % 3 }, List(10) { 60f + it % 3 }, random)
        assertTrue("p $apart", apart < 0.01f)
        // Never zero: the real split counts.
        assertTrue(apart > 0f)
    }

    @Test fun `least squares holds the other columns steady and drops copies`() {
        // y = 10 + 3a - 2b exactly, plus a little noise; c is a copy of a.
        val noise = Random(11)
        val a = DoubleArray(40) { if (it % 2 == 0) 1.0 else 0.0 }
        val b = DoubleArray(40) { if (it % 3 == 0) 1.0 else 0.0 }
        val y = DoubleArray(40) { 10 + 3 * a[it] - 2 * b[it] + noise.nextDouble(-0.1, 0.1) }
        val fit = Reports.ols(listOf(a, b, a.copyOf()), y)!!
        assertEquals(3.0, fit.coefficients[0]!!, 0.05)
        assertEquals(-2.0, fit.coefficients[1]!!, 0.05)
        assertNull(fit.coefficients[2])
        assertTrue(fit.p[0]!! < 1e-6)
        // Too few rows for the columns: no fit.
        assertNull(Reports.ols(listOf(a.copyOfRange(0, 10)), y.copyOfRange(0, 10)))
    }

    @Test fun `Student's t gives the textbook p-values`() {
        assertEquals(1.0, Reports.tTwoSided(0.0, 30), 1e-9)
        assertEquals(0.0510, Reports.tTwoSided(2.0, 50), 5e-4)
        assertEquals(0.0734, Reports.tTwoSided(2.0, 10), 5e-4)
        assertEquals(0.05, Reports.tTwoSided(2.228, 10), 5e-4)
        assertEquals(Reports.tTwoSided(-2.5, 20), Reports.tTwoSided(2.5, 20), 1e-12)
    }

    @Test fun `phi tells habits that come together`() {
        assertEquals(1f, Reports.phi(listOf(true, false, true, false), listOf(true, false, true, false))!!, 1e-6f)
        assertEquals(-1f, Reports.phi(listOf(true, false, true, false), listOf(false, true, false, true))!!, 1e-6f)
        assertNull(Reports.phi(listOf(true, true), listOf(true, false)))
    }

    @Test fun `the report reads the day shown and counts the days behind it`() {
        val first = end.minusDays(Reports.DAYS - 1L)
        val days = (0 until Reports.DAYS).map { i ->
            val d = first.plusDays(i.toLong())
            Reports.Day(d, recovery = if (i >= 10) 70 + i % 10 else null, load = 90f + i % 5 * 10, usualLoad = 100f, asleepMinutes = 450, inBedMinutes = 500, wake = LocalTime.of(7, 0))
        }
        val loads = days.associate { it.date to it.load!! }
        val report = Reports.build(Reports.Input(end, days, loads), emptyList(), 480, today = end)
        assertEquals(Reports.DAYS, report.daysWithData)
        assertEquals(Reports.DAYS - 10, report.scoredDays)
        assertTrue(!report.finished)
        assertEquals(days.last().load, report.load)
        assertNotNull(report.target)
        assertEquals(7, report.week.days.size)
        assertEquals(30, report.month.days.size)
        assertNotNull(report.plan.bedtime)
    }
}
