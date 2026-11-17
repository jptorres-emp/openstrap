package org.openstrap.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openstrap.app.ui.glow.ButtonSpec
import org.openstrap.app.ui.glow.GlowType
import org.openstrap.app.ui.glow.HaloButtonSize
import org.openstrap.app.ui.glow.HaloIconSize
import org.openstrap.app.ui.glow.HaloIconVariant
import org.openstrap.app.ui.theme.Greys
import org.openstrap.app.ui.theme.IconSize
import org.openstrap.app.ui.theme.Space

/**
 * The one button and the one icon button (docs/design/halo/components-audit.md, 3.7): their measurements come from
 * [ButtonSpec], and the old button composables are gone, so every button of a kind shares one frame and only the words
 * and the icon differ.
 */
class HaloButtonTest {
    private val ui = UiSources()

    @Test fun `a button is 64, 56, 44 or 36 tall`() {
        assertEquals(listOf(64.dp, 56.dp, 44.dp, 36.dp), HaloButtonSize.entries.map { it.height })
        assertEquals(listOf("Main", "Page", "Card", "Small"), HaloButtonSize.entries.map { it.name })
    }

    @Test fun `a button's label is 17, 16, 14 or 13 semibold`() {
        assertEquals(listOf(17f, 16f, 14f, 13f), HaloButtonSize.entries.map { it.label.fontSize.value })
        assertTrue(HaloButtonSize.entries.all { it.label.fontWeight == FontWeight.SemiBold })
        assertEquals(GlowType.button, HaloButtonSize.Page.label)
    }

    @Test fun `a button pads 22, 18, 18 or 14 at each side`() {
        assertEquals(listOf(22.dp, 18.dp, 18.dp, 14.dp), HaloButtonSize.entries.map { it.side })
    }

    @Test fun `a leading icon is 16 with a gap of 8, a pair keeps 12 between its buttons, a text button is 44 to tap`() {
        assertEquals(16.dp, ButtonSpec.Glyph)
        assertEquals(Space.S8, ButtonSpec.IconGap)
        assertEquals(Space.S12, ButtonSpec.PairGap)
        assertEquals(44.dp, ButtonSpec.TextMinHeight)
    }

    @Test fun `an icon button is 32, 44, 60 or 88 across with a 16, 20, 22 or 28 glyph`() {
        assertEquals(listOf(32.dp, 44.dp, 60.dp, 88.dp), HaloIconSize.entries.map { it.size })
        assertEquals(listOf(16.dp, 20.dp, 22.dp, 28.dp), HaloIconSize.entries.map { it.glyph })
        assertEquals(IconSize.Live, HaloIconSize.Live.size)
        assertEquals(IconSize.Pause, HaloIconSize.Pause.size)
        assertEquals(HaloIconSize.Default, HaloIconSize.entries[1])
    }

    @Test fun `an icon button is on the control surface, white, or over a picture or a wash`() {
        assertEquals(Greys.Control, HaloIconVariant.Control.fill)
        assertEquals(Color.White, HaloIconVariant.Primary.fill)
        assertEquals(Color.Black, HaloIconVariant.Primary.ink)
        assertEquals(Greys.OverMap, HaloIconVariant.Glass.fill)
    }

    @Test fun `buttons are pills and icon buttons circles, written once`() {
        val code = ui.code("glow/HaloButton.kt")
        val button = code.substringAfter("internal fun HaloButton(").substringBefore("internal enum class HaloIconVariant")
        assertTrue("a pill", "Corner.Pill" in button && "CircleShape" !in button && "RoundedCornerShape" !in button && "SquircleShape" !in button)
        assertTrue("height, sides and type from the size", "size.height" in button && "size.side" in button && "size.label" in button)
        val surface = code.substringAfter("internal fun HaloIconSurface(").substringBefore("internal fun HaloIconButton(")
        assertTrue("a circle of the size", "Corner.Pill" in surface && "size.size" in surface)
        assertTrue("a notice dot on the top right", "NoticeDot(Modifier.align(Alignment.TopEnd)" in code)
        assertTrue("a button is a button to a screen reader", "Role.Button" in code)
    }

    @Test fun `the old button composables are gone`() {
        val old = listOf(
            "PageButton", "ButtonSize", "PageSaveButton", "PageTextButton", "WorkoutButton", "SaveCancel", "SheetActions",
            "SummaryButtons", "HaloDeleteWorkout", "EditTodayButton", "CardButton", "TrimButton", "PhaseAction", "MapButton",
            "RoundIconButton", "BackButton", "RoundIcon", "FlatButton",
        )
        val found = old.flatMap { name -> ui.find(Regex("""\b(fun|val|class)\s+(\w+\.)?$name\b""")) }
        assertTrue("These were folded into HaloButton and HaloIconButton:\n" + found.joinToString("\n"), found.isEmpty())
        val used = old.flatMap { name -> ui.find(Regex("""\b$name\(""")) }
        assertTrue("Still called:\n" + used.joinToString("\n"), used.isEmpty())
    }

    @Test fun `a round icon button is not drawn by hand`() {
        val own = ui.find(Regex("""Modifier\.size\((60|88)\.dp\)\.clip\((Corner\.Pill|CircleShape)\)"""))
        assertTrue("An icon button is HaloIconButton:\n" + own.joinToString("\n"), own.isEmpty())
    }
}
