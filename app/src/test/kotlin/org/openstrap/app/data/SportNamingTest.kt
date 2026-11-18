package org.openstrap.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openstrap.app.ui.workout.colorFamily
import org.openstrap.app.ui.workout.familyColor
import org.openstrap.app.ui.workout.sportFamilyColor

/**
 * One name for a sport with two sides (board HaloRunNaming): "Run" and "Cycling" everywhere people read them, the
 * indoor side marked by a tag and coloured as its sport, while the stored type and what leaves the app stay exact.
 */
class SportNamingTest {
    @Test fun `both sides of a run or a ride read as one name`() {
        assertEquals("Run", Workouts.sportName(1))
        assertEquals("Run", Workouts.sportName(2))
        assertEquals("Cycling", Workouts.sportName(4))
        assertEquals("Cycling", Workouts.sportName(8))
        // A sport with one kind keeps its own name.
        assertEquals("Strength training", Workouts.sportName(52))
        assertEquals("Walk", Workouts.sportName(40))
        assertEquals("Indoor walk", Workouts.exactName(40))
    }

    @Test fun `the exact name stays for exports and search`() {
        assertEquals("Outdoor run", Workouts.exactName(1))
        assertEquals("Treadmill", Workouts.exactName(2))
        assertEquals("Outdoor cycling", Workouts.exactName(4))
        assertEquals("Indoor cycling", Workouts.exactName(8))
    }

    @Test fun `only the indoor side gets the Indoor tag`() {
        assertTrue(Workouts.isIndoorSide(2))
        assertTrue(Workouts.isIndoorSide(8))
        assertTrue(Workouts.isIndoorSide(40))
        listOf(1, 4, 3, 6, 52).forEach { assertFalse("$it", Workouts.isIndoorSide(it)) }
    }

    @Test fun `search still finds Treadmill and Indoor cycling, as the indoor side`() {
        assertTrue(2 in Workouts.search("treadmill"))
        assertTrue(8 in Workouts.search("indoor cycling"))
        assertTrue(1 in Workouts.search("run"))
    }

    @Test fun `one sport, one colour`() {
        assertEquals(Workouts.Family.IndoorCardio, Workouts.family(2))
        assertEquals(Workouts.Family.Distance, colorFamily(2))
        assertEquals(familyColor(Workouts.Family.Distance), sportFamilyColor(2))
        assertEquals(familyColor(Workouts.Family.Distance), sportFamilyColor(8))
        // A family the structure gave the workout keeps its own colour.
        assertEquals(familyColor(Workouts.Family.Intervals), sportFamilyColor(2, Workouts.Family.Intervals))
        assertEquals(familyColor(Workouts.Family.Strength), sportFamilyColor(52))
    }

    @Test fun `walk has an indoor side, sent to the strap as walk`() {
        assertTrue(Workouts.isIndoorSide(40))
        assertEquals(3, Workouts.tileType(40))
        assertEquals(3, Workouts.strapType(40))
        assertEquals(2, Workouts.strapType(2))
        assertEquals(40, Workouts.startable(40))
    }
}
