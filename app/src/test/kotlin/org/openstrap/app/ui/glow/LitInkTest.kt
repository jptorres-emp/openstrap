package org.openstrap.app.ui.glow

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openstrap.app.ui.glow.draw.GlowPalette
import org.openstrap.app.ui.glow.draw.GlowStyle
import org.openstrap.app.ui.theme.Ink
import org.openstrap.app.ui.theme.MetricColors

/** The secondary ink of a lit card ([LitInk]) keeps 4.5:1 on every card colour's light, and beats the dark-card grey. */
class LitInkTest {
    private val colours = mapOf(
        "steps" to MetricColors.steps, "charge" to MetricColors.charge, "load" to MetricColors.load,
        "readiness" to MetricColors.readiness, "heart" to MetricColors.heart, "sleepDeep" to MetricColors.sleepDeep,
        "sleepRem" to MetricColors.sleepRem, "calories" to MetricColors.calories, "hrv" to MetricColors.hrv,
        "spo2" to MetricColors.spo2, "stress" to MetricColors.stress,
    )

    private fun styles(color: Color) = listOf(GlowStyle(color), GlowStyle(color, lightLevel = 0.6f), GlowStyle(color, second = true))

    @Test
    fun litInkKeepsFourPointFiveOnTheBodyOfEveryCard() {
        for ((name, color) in colours) for (style in styles(color)) {
            val body = GlowPalette(style).mid
            val ratio = LitInk.contrast(LitInk.Secondary, body)
            assertTrue("$name on its body: $ratio", ratio >= LitInk.MIN_CONTRAST)
        }
    }

    @Test
    fun litInkBeatsTheGreyOfADarkCardOnTheBrightestLight() {
        for ((name, color) in colours) {
            val light = GlowPalette(GlowStyle(color)).light
            val grey = Ink.Secondary.compositeOver(light)
            assertTrue(
                "$name: lit ink must beat the grey on the rim",
                LitInk.contrast(LitInk.Secondary, light) > LitInk.contrast(grey, light),
            )
        }
    }
}
