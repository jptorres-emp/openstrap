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
import org.openstrap.app.ui.dashboard.openBeside
import org.openstrap.app.ui.glow.GlowLayout
import java.io.File

/**
 * The frame's rules (spec 2.4 Unfolded, 3.2, 3.5): the fade behind the tab bar, You and Strap growing from their
 * buttons, and the layout on an unfolded screen.
 */
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

    @Test fun `tabs keep their phone gutters folded, and unfolded are at most 960 dp with a 24 dp gutter`() {
        assertEquals(20.dp, GlowLayout.dashboardGutter(411.dp))
        assertEquals(16.dp, GlowLayout.dashboardGutter(375.dp))
        assertEquals(24.dp, GlowLayout.dashboardGutter(750.dp))
        // A tablet: the content is 960 dp, centred.
        assertEquals(120.dp, GlowLayout.dashboardGutter(1200.dp))
        assertEquals(960.dp, GlowLayout.MaxContentWidth)
        assertEquals(12.dp, GlowLayout.ColumnGap)
    }

    @Test fun `a page keeps to one centred column of at most 600 dp, and a phone's page is unchanged`() {
        assertEquals(600.dp, GlowLayout.PageMaxWidth)
        assertEquals(20.dp, GlowLayout.pageSide(411.dp, GlowLayout.PageMaxWidth, GlowLayout.Gutter))
        val side = GlowLayout.pageSide(750.dp, GlowLayout.PageMaxWidth, GlowLayout.Gutter)
        assertEquals(560.dp, 750.dp - side * 2)
        // A dashboard clears the column and lays out its own width.
        assertEquals(16.dp, GlowLayout.pageSide(750.dp, androidx.compose.ui.unit.Dp.Unspecified, 16.dp))
    }

    @Test fun `the last block of a column that ends far below the other moves to the shorter one`() {
        // Left: What drove it, Body signals, Habit Age; right: one card. Habit Age moves right.
        assertEquals(listOf(0, 1) to listOf(3, 2), GlowLayout.balanceColumns(listOf(400, 300, 200), listOf(300), gap = 12))
        // The right column longer: its last moves left.
        assertEquals(listOf(0, 3) to listOf(1, 2), GlowLayout.balanceColumns(listOf(300), listOf(400, 300, 200), gap = 12))
    }

    @Test fun `columns stay as they are when a move wouldn't bring their ends closer`() {
        // Moving the 500 would leave the right far longer.
        assertEquals(listOf(0, 1) to listOf(2), GlowLayout.balanceColumns(listOf(100, 500), listOf(400), gap = 12))
        // A column of one block never empties.
        assertEquals(listOf(0) to listOf(1), GlowLayout.balanceColumns(listOf(900), listOf(100), gap = 12))
        // Even columns stay.
        assertEquals(listOf(0, 1) to listOf(2, 3), GlowLayout.balanceColumns(listOf(200, 200), listOf(200, 200), gap = 12))
    }

    @Test fun `a row on the You or Strap page opens its page beside the list, in place of the one there`() {
        assertEquals(listOf("Strap", "Alarms"), openBeside(listOf("Strap"), "Alarms"))
        assertEquals(listOf("You", "Diagnostics"), openBeside(listOf("You", "Profile"), "Diagnostics"))
        assertEquals(listOf("Strap", "Alarms"), openBeside(listOf("Strap", "Firmware", "ZeppAccount"), "Alarms"))
    }

    @Test fun `sheets, workout pages and Profile's Save keep to the page column`() {
        val glass = source("ui/components/Glass.kt").substringAfter("fun GlassSheet(")
        assertTrue("GlassSheet is at most PageMaxWidth", "widthIn(max = org.openstrap.app.ui.glow.GlowLayout.PageMaxWidth)" in glass)
        val workout = source("ui/glow/GlowWorkoutMode.kt").substringAfter("fun WorkoutPage(").substringBefore("\n}\n")
        assertTrue("WorkoutPage centres its content and its pinned buttons", Regex("""pageSide\(""").findAll(workout).count() == 1 && "start = side, end = side" in workout)
        val profile = source("ui/device/ProfileScreen.kt")
        assertTrue("Profile's Save is as wide as the fields", "start = side, end = side, bottom = bottom + 12.dp" in profile && "widthIn(max = 600.dp)" !in profile)
        val screens = source("ui/dashboard/ScreenParts.kt")
        assertTrue("ScreenList centres a page", "LocalPageColumn.current" in screens)
    }
}
