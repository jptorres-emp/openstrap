package org.openstrap.app.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openstrap.app.ui.theme.Greys

class GreysTest {
    @Test
    fun theFiveStepsHoldTheirValues() {
        assertEquals(Color(0xFF000000), Greys.Page)
        assertEquals(Color(0xFF141414), Greys.Card)
        assertEquals(Color(0xFF1E1E1E), Greys.Raised)
        assertEquals(Color(0xFF282828), Greys.Control)
        assertEquals(Color(0xFF323232), Greys.Picked)
    }

    @Test
    fun eachStepIsLighterThanTheOneBefore() {
        val steps = listOf(Greys.Page, Greys.Card, Greys.Raised, Greys.Control, Greys.Picked)
        steps.zipWithNext().forEach { (lower, higher) -> assertTrue(higher.red > lower.red) }
    }
}
