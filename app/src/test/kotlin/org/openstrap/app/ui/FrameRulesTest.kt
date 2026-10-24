package org.openstrap.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openstrap.app.ui.components.GrowMotion
import org.openstrap.app.ui.components.TabBarFade
import java.io.File

/** The frame's rules (spec 3.2, 3.5): the fade behind the tab bar, and You and Strap growing from their buttons. */
class FrameRulesTest {
    private val root: File = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
    private fun source(path: String) = File(root, "kotlin/org/openstrap/app/$path").readText()

    @Test fun `the fade behind the tab bar is 120 dp, solid black for its lower 45 percent`() {
        assertEquals(120.dp, TabBarFade.Height)
        val stops = TabBarFade.stops
        assertEquals(0f to Color.Transparent, stops.first())
        assertEquals(1f to Color.Black, stops.last())
        // Black from 55% down: the lower 45% is solid.
        assertEquals(0.55f, stops.first { it.second == Color.Black }.first, 1e-4f)
    }

    @Test fun `the tab bar draws its fade`() {
        val bar = source("ui/components/Insights.kt")
        val body = bar.substringAfter("fun FloatingNavBar(").substringBefore("fun FloatingNavRail(")
        assertTrue("FloatingNavBar draws TabBarFade behind the pill", "TabBarFade.brush(" in body && "drawBehind" in body)
    }

    @Test fun `a page starts as its button's circle and ends as the page`() {
        val button = Rect(Offset(16f, 40f), androidx.compose.ui.geometry.Size(38f, 38f))
        val page = Rect(0f, 0f, 400f, 800f)
        val start = GrowMotion.outline(button, page, 0f)
        assertEquals(button, Rect(start.left, start.top, start.right, start.bottom))
        assertEquals(19f, start.topLeftCornerRadius.x, 1e-4f)
        val end = GrowMotion.outline(button, page, 1f)
        assertEquals(page, Rect(end.left, end.top, end.right, end.bottom))
        assertEquals(0f, end.topLeftCornerRadius.x, 1e-4f)
        // A pill (the strap chip) is round at its ends: half its height.
        val chip = Rect(300f, 46f, 380f, 72f)
        assertEquals(13f, GrowMotion.outline(chip, page, 0f).topLeftCornerRadius.x, 1e-4f)
    }

    @Test fun `it grows at 500 and shrinks at 300, fading in only at the start`() {
        assertEquals(500f, GrowMotion.stiffness(opening = true))
        assertEquals(300f, GrowMotion.stiffness(opening = false))
        assertEquals(0f, GrowMotion.alpha(0f))
        assertEquals(1f, GrowMotion.alpha(GrowMotion.FADE_PART))
        assertEquals(1f, GrowMotion.alpha(1f))
    }

    @Test fun `You and Strap grow from the header's buttons`() {
        val header = source("ui/dashboard/MomentHeader.kt")
        assertTrue("the avatar records itself", Regex("""growClick\(onOpenYou\)""").containsMatchIn(header))
        assertTrue("the strap chip records itself", Regex("""growClick\(onOpenStrap\)""").containsMatchIn(header))
        val root = source("MainActivity.kt")
        assertTrue("the pages over the tab grow from the origin", "GrowFromButton(" in root && "growOrigin.take()" in root)
    }
}
