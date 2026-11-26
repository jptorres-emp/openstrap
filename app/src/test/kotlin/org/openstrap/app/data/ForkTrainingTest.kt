package org.openstrap.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** The fork's Training view: your sports' usual day, the last week's Load against your usual, the calories by week. */
class ForkTrainingTest {
    private val end = LocalDate.of(2026, 11, 20)

    private fun day(back: Long, load: Float?, vararg sessions: ForkReports.Session, kcal: Float? = null, partial: Boolean = false) =
        ForkReports.Day(end.minusDays(back), load = load, sessions = sessions.toList(), kcal = kcal, partial = partial)

    @Test fun `a sport's usual day is the median Load of the days you did only it, over the last 6 weeks`() {
        val football = ForkReports.Session(FOOTBALL, 5400)
        val cross = ForkReports.Session(CROSS, 3600)
        val days = listOf(
            day(1, 150f, football), day(3, 170f, football), day(5, 120f, football),
            // With another sport the day is one of football's, but its Load stands for neither.
            day(7, 260f, football, cross),
            day(2, 90f, cross), day(4, 110f, cross),
            // A workout the strap noticed by itself, a sport done once, a day under way, and one before the 6 weeks.
            day(6, 80f, ForkReports.Session(WALK, 1800, byYou = false)),
            day(8, 60f, ForkReports.Session(HIIT, 1200)),
            day(0, 40f, cross, partial = true),
            day(42, 300f, football),
        )
        val expected = listOf(ForkTraining.SportLoad(FOOTBALL, 4, 150f), ForkTraining.SportLoad(CROSS, 3, 100f))
        assertEquals(expected, ForkTraining.sports(days, end))
        assertEquals(expected, ForkTraining.training(days, end, fatigue = 66f, usual = 55f).sports)
    }

    @Test fun `a usual day lands against the target as it's shown, rounded`() {
        assertEquals(ForkTraining.Fit.Under, ForkTraining.fit(110..205, 109.4f))
        assertEquals(ForkTraining.Fit.Within, ForkTraining.fit(110..205, 109.5f))
        assertEquals(ForkTraining.Fit.Within, ForkTraining.fit(110..205, 205.4f))
        assertEquals(ForkTraining.Fit.Over, ForkTraining.fit(110..205, 205.5f))
    }

    @Test fun `the last week's Load is steady within a quarter of your usual and a spike over half as much again`() {
        assertEquals(1.2f, ForkTraining.ratio(66f, 55f)!!, 1e-4f)
        assertNull(ForkTraining.ratio(null, 55f))
        assertNull(ForkTraining.ratio(60f, null))
        assertNull(ForkTraining.ratio(60f, 0f))
        val zones = listOf(0.74f, 0.75f, 1.25f, 1.26f, 1.5f, 1.51f).map(ForkTraining::zone)
        assertEquals(listOf("Easing", "Steady", "Steady", "Building", "Building", "Spike"), zones.map { it.name })
    }

    @Test fun `the calories are a day's average week by week, the day under way left out`() {
        val days = (0L until 28L).map { back ->
            val kcal = when {
                back == 0L -> 500f
                back < 7 -> 2600f
                back < 14 -> null
                else -> 2400f
            }
            day(back, null, kcal = kcal, partial = back == 0L)
        }
        val weeks = ForkTraining.energyWeeks(days, end)
        assertEquals(listOf(end.minusDays(21), end.minusDays(14), end.minusDays(7), end), weeks.map { it.end })
        assertEquals(listOf(2400f, 2400f, null, 2600f), weeks.map { it.kcalADay })
        assertEquals(listOf(7, 7, 0, 6), weeks.map { it.days })
        assertEquals(2400f, ForkTraining.energyBefore(weeks)!!, 1e-3f)
        assertNull(ForkTraining.energyBefore(weeks.takeLast(2)))
    }

    private companion object {
        const val FOOTBALL = 191
        const val CROSS = 130
        const val WALK = 6
        const val HIIT = 49
    }
}
