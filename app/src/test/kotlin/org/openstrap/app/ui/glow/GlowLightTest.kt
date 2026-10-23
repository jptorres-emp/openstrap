package org.openstrap.app.ui.glow

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlowLightTest {
    private val style = GlowStyle(color = Color(0xFF7CF2C9))

    @Test fun `light level is clamped, with a floor`() {
        assertNull(glowLevel(null))
        assertEquals(MIN_LIGHT_LEVEL, glowLevel(0f))
        assertEquals(MIN_LIGHT_LEVEL, glowLevel(-1f))
        assertEquals(MIN_LIGHT_LEVEL, glowLevel(Float.NaN))
        assertEquals(0.3f, glowLevel(0.3f))
        assertEquals(1f, glowLevel(1.4f))
    }

    @Test fun `the light vector points at the light`() {
        assertEquals(0f to -1f, lightVector(LightAngle.Above))
        assertEquals(0f to 1f, lightVector(LightAngle.Below))
        assertEquals(1f to 0f, lightVector(LightAngle.Right))
        val (x, y) = lightVector(LightAngle.LowerLeft)
        assertTrue(x < 0f && y > 0f)
    }

    @Test fun `the core sits opposite the light`() {
        val core = angleCore(LightAngle.LowerLeft, 0.9f, 0.8f)
        assertTrue(core.x > 0.5f && core.y < 0.5f)
        assertEquals(0.9f, core.width)
        val below = angleCore(LightAngle.Below, 1f, 1f)
        assertEquals(0.5f, below.x, 1e-6f)
        assertTrue(below.y < 0.5f)
    }

    @Test fun `the lit part is about the level's height`() {
        // Measured from the unlit side: 0.3 leaves about 70% dark.
        assertEquals(0.88f, levelEdge(MIN_LIGHT_LEVEL), 0.02f)
        assertEquals(0.7f, levelEdge(0.3f), 0.03f)
        assertEquals(0.3f, levelEdge(0.7f), 0.04f)
        // Full: lit all the way, the soft step past the edge.
        assertEquals(-LEVEL_SOFTNESS, levelEdge(1f), 1e-6f)
        assertTrue(levelEdge(0.3f) > levelEdge(0.7f))
    }

    @Test fun `the level core covers the dark part`() {
        val c = levelCore(0.3f, fromAbove = false)
        assertEquals(0f, c.y - c.height / 2, 1e-6f)
        assertEquals(levelEdge(0.3f), c.y + c.height / 2, 1e-6f)
        assertEquals(1f - c.y, levelCore(0.3f, fromAbove = true).y, 1e-6f)
    }

    @Test fun `old light sides map onto angles and keep their core`() {
        assertEquals(LightAngle.Above, style.light().angle)
        assertEquals(LightAngle.Below, style.copy(lightFrom = LightFrom.Bottom).light().angle)
        assertNull(style.copy(lightFrom = LightFrom.All).light().angle)
        assertEquals(CoreBox(style.coreX, style.coreY, style.coreWidth, style.coreHeight), style.light().core)
        assertEquals(style.glowStrength, style.light().rim, 1e-6f)
        assertEquals(1f, style.light().fill, 1e-6f)
    }

    @Test fun `an angle moves the core`() {
        val lit = style.copy(lightAngle = LightAngle.UpperRight).light()
        assertEquals(LightAngle.UpperRight, lit.angle)
        assertTrue(lit.core.x < 0.5f && lit.core.y > 0.5f)
    }

    @Test fun `a level lights from below`() {
        val lit = style.copy(lightLevel = 0f).light()
        assertEquals(LightAngle.Below, lit.angle)
        assertEquals(MIN_LIGHT_LEVEL, lit.level)
        assertFalse(lit.fromAbove)
        assertTrue(style.copy(lightLevel = 0.5f, lightAngle = LightAngle.Above).light().fromAbove)
        assertNull(style.light().level)
    }

    @Test fun `the second card is dimmer, with a weaker rim and a shorter fill`() {
        val hero = style.light()
        val second = style.copy(second = true).light()
        assertTrue(second.rim < hero.rim)
        assertTrue(second.fill < hero.fill)
        assertTrue(second.reach < hero.reach)
        assertTrue(second.brightness < hero.brightness)
        assertEquals(1f, hero.brightness, 1e-6f)
    }

    @Test fun `moods dim the whole card`() {
        assertEquals(1f, style.copy(mood = GlowMood.Day).light().brightness, 1e-6f)
        val dim = style.copy(mood = GlowMood.Dim).light()
        assertEquals(0.6f, dim.brightness, 1e-6f)
        assertFalse(dim.tone)
        assertNull(dim.level)
        val night = style.copy(lightAngle = LightAngle.UpperRight, mood = GlowMood.Night).light()
        assertEquals(LightAngle.Below, night.angle)
        assertEquals(0.4f, night.brightness, 1e-6f)
        // Lit along the bottom edge only.
        val level = night.level!!
        assertTrue(level < 0.25f)
        assertFalse(night.fromAbove)
        // A level card keeps its level at night.
        assertEquals(0.7f, style.copy(lightLevel = 0.7f, mood = GlowMood.Night).light().level)
        assertNull(style.copy(lightFrom = LightFrom.All, mood = GlowMood.Night).light().angle)
    }
}
