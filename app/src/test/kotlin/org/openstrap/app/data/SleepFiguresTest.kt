package org.openstrap.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SleepFiguresTest {
    @Test fun `performance is the time asleep against the need, at most 100`() {
        assertEquals(92, SleepFigures.performance(7 * 3600L + 22 * 60, 480))
        assertEquals(100, SleepFigures.performance(9 * 3600L, 480))
        assertNull(SleepFigures.performance(3600, 0))
    }

    @Test fun `efficiency is the time asleep against the time in bed`() {
        assertEquals(86, SleepFigures.efficiency(483 * 60L, 0, 560 * 60L))
        assertNull(SleepFigures.efficiency(3600, 100, 100))
    }
}
