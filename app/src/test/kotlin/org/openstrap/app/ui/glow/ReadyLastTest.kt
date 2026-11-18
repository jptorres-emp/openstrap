package org.openstrap.app.ui.glow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.openstrap.app.data.WorkoutAnalysis
import org.openstrap.app.data.db.Workout
import java.time.LocalDate
import java.time.ZoneOffset

/** Ready's hero with no goal shows last time (board HaloReadyEveryKind): one big figure, the rest in a line. */
class ReadyLastTest {
    @get:org.junit.Rule val localeRule = org.openstrap.app.testing.LocaleRule()

    private val today = LocalDate.of(2026, 11, 13)
    private val wednesday = LocalDate.of(2026, 11, 11).atTime(18, 0).toEpochSecond(ZoneOffset.UTC)

    private fun analysis(sport: Int, seconds: Long, meters: Float? = null, sets: Int? = null) = WorkoutAnalysis(
        Workout(wednesday, wednesday + seconds, sport, seconds.toInt(), null, null, null, null, null, null, null, null, null),
        wednesday + seconds, emptyList(), null, null, null, null, List(5) { 0 }, 190, fromPhone = true,
        distanceMeters = meters, sets = sets,
    )

    @Test fun `a walk shows its distance, then its time and pace`() {
        val hero = readyLast(analysis(3, 28 * 60 + 10, meters = 2400f), today, ZoneOffset.UTC)!!.hero!!
        assertEquals("2.4", hero.number)
        assertEquals("km", hero.unit)
        assertEquals("28:10 at 11:44 per km", hero.line)
    }

    @Test fun `strength shows its sets and its time`() {
        val hero = readyLast(analysis(52, 43 * 60 + 5, sets = 15), today, ZoneOffset.UTC)!!.hero!!
        assertEquals("15", hero.number)
        assertEquals("sets", hero.unit)
        assertEquals("43:05 in all", hero.line)
    }

    @Test fun `anything else shows its minutes`() {
        val hero = readyLast(analysis(60, 30 * 60), today, ZoneOffset.UTC)!!.hero!!
        assertEquals("30", hero.number)
        assertEquals("min", hero.unit)
        assertNull(hero.line)
    }
}
