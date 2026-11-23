package org.openstrap.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openstrap.app.presentation.LoadBand
import org.openstrap.app.presentation.LoadBands

class WorkoutLoadBandTest {
    @Test fun edgesAreAQuarterEitherSideOfTheMedian() {
        assertEquals(75 to 125, WorkoutLoadBand.of(listOf(100, 90, 110, 100)))
    }

    @Test fun edgesRoundToWholeNumbers() {
        assertEquals(38 to 63, WorkoutLoadBand.edges(50f))
        assertEquals(15 to 26, WorkoutLoadBand.edges(20.4f))
    }

    @Test fun fewerThanThreeLoadsHaveNoBand() {
        assertNull(WorkoutLoadBand.of(listOf(100, 120)))
        assertNull(WorkoutLoadBand.of(listOf(null, 0, 100, 110)))
        assertNull(WorkoutLoadBand.of(emptyList()))
    }

    @Test fun missingAndZeroLoadsAreIgnored() {
        assertEquals(75 to 125, WorkoutLoadBand.of(listOf(null, 100, 0, 100, 100)))
    }

    @Test fun theShareIsTheDayBands() = assertEquals(LoadBands.BAND, WorkoutLoadBand.BAND)

    @Test fun noUsualHasNoEdges() {
        assertNull(WorkoutLoadBand.edges(null))
        assertNull(WorkoutLoadBand.edges(0f))
    }

    @Test fun aLoadOnAnEdgeIsUsualToTheTag() {
        val (low, high) = WorkoutLoadBand.edges(100f)!!
        assertEquals(LoadBand.Usual, LoadBands.of(low.toFloat(), 100f))
        assertEquals(LoadBand.Usual, LoadBands.of(high.toFloat(), 100f))
        assertTrue(LoadBands.of(high + 1f, 100f) == LoadBand.Above)
    }
}
