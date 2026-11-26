package org.openstrap.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The sports the fork adds to the original's: futsal, which the strap runs as a sport of its own. */
class ForkSportsTest {
    @Test fun `futsal is offered under More sports, indoors, and the strap is told futsal`() {
        assertEquals("Futsal", Workouts.sportName(FUTSAL))
        assertTrue(FUTSAL in Workouts.MORE)
        assertTrue(FUTSAL in Workouts.search("futs"))
        assertEquals(FUTSAL, Workouts.startable(FUTSAL))
        assertEquals(FUTSAL, Workouts.strapType(FUTSAL))
        assertEquals(Workouts.Family.Games, Workouts.family(FUTSAL))
        assertFalse(FUTSAL in Workouts.OUTDOOR)
        // Football stays as it was, a sport of its own.
        assertEquals("Football", Workouts.sportName(FOOTBALL))
    }

    private companion object {
        const val FUTSAL = 164
        const val FOOTBALL = 191
    }
}
