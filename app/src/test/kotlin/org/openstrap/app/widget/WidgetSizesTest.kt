package org.openstrap.app.widget

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.w3c.dom.Element

/**
 * Widgets spec 2.1 and 6.1: every widget has the same three sizes and resizes the same way. The provider check reads
 * every `appwidget-provider` in res/xml, so each widget step 4 on adds is held to it too, and a failure names it.
 */
class WidgetSizesTest {
    private val root: File = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
    private val android = "http://schemas.android.com/apk/res/android"

    @Test fun `the three sizes are the spec's`() {
        assertEquals(listOf(150f to 150f, 300f to 160f, 300f to 260f), WidgetSizes.all.map { it.width.value to it.height.value })
        assertEquals(WidgetSizes.Small, WidgetSizes.all.first())
        assertEquals(WidgetSizes.Large, WidgetSizes.all.last())
    }

    /** The provider XML files, by name: those whose root is `appwidget-provider`. */
    private fun providers(): Map<String, Element> {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        return File(root, "res/xml").listFiles().orEmpty().filter { it.extension == "xml" }.mapNotNull { f ->
            val doc = factory.newDocumentBuilder().parse(f).documentElement
            if (doc.tagName == "appwidget-provider") f.name to doc else null
        }.toMap()
    }

    @Test fun `every provider declares the same sizes and resize limits`() {
        val all = providers()
        assertTrue("no widget providers in res/xml", all.containsKey("widget_recovery.xml"))
        val wrong = mutableListOf<String>()
        val max = WidgetSizes.MaxResize
        val expected = mapOf(
            "targetCellWidth" to "${WidgetSizes.TARGET_CELLS}",
            "targetCellHeight" to "${WidgetSizes.TARGET_CELLS}",
            "minWidth" to "${WidgetSizes.MIN_DP}dp",
            "minHeight" to "${WidgetSizes.MIN_DP}dp",
            "minResizeWidth" to "${WidgetSizes.MIN_DP}dp",
            "minResizeHeight" to "${WidgetSizes.MIN_DP}dp",
            "maxResizeWidth" to "${max.width.value.toInt()}dp",
            "maxResizeHeight" to "${max.height.value.toInt()}dp",
            "resizeMode" to "horizontal|vertical",
            "updatePeriodMillis" to "0",
            "widgetCategory" to "home_screen",
        )
        all.forEach { (name, e) ->
            expected.forEach { (attr, value) ->
                val got = e.getAttributeNS(android, attr)
                if (got != value) wrong += "$name: $attr is \"$got\", the spec's is \"$value\""
            }
            if (!e.getAttributeNS(android, "description").startsWith("@string/")) wrong += "$name: no description string"
        }
        if (wrong.isNotEmpty()) fail(wrong.joinToString("\n"))
    }

    @Test fun `the layout picked is the largest that fits, else the Small`() {
        assertEquals(WidgetSizes.Small, WidgetSizes.bucket(173f, 176f))
        assertEquals(WidgetSizes.Wide, WidgetSizes.bucket(300f, 250f))
        // One UI on the Fold's cover screen: 2 × 2 is 167 × 201, 4 × 2 is 353 × 201, 4 × 3 is 353 × 375.
        assertEquals(WidgetSizes.Small, WidgetSizes.bucket(167.6f, 201.1f))
        assertEquals(WidgetSizes.Wide, WidgetSizes.bucket(353.5f, 201.1f))
        assertEquals(WidgetSizes.Large, WidgetSizes.bucket(353.5f, 375.8f))
        assertEquals(WidgetSizes.Wide, WidgetSizes.bucket(400f, 200f))
        assertEquals(WidgetSizes.Large, WidgetSizes.bucket(358f, 272f))
        assertEquals(WidgetSizes.Large, WidgetSizes.bucket(357.8f, 271.6f))
        assertEquals(WidgetSizes.Small, WidgetSizes.bucket(120f, 120f))
    }

    @Test fun `bitmaps are drawn at the cell's real size when the launcher reports it`() {
        assertEquals(390f to 190f, WidgetSizes.drawSize(WidgetSizes.Wide, listOf(180f to 400f, 390f to 190f)))
        assertEquals(300f to 160f, WidgetSizes.drawSize(WidgetSizes.Wide, emptyList()))
        assertEquals(300f to 160f, WidgetSizes.drawSize(WidgetSizes.Wide, listOf(0f to 0f)))
    }

    @Test fun `the corner is 28 dp, or the launcher's when smaller`() {
        assertEquals(28f, WidgetSizes.corner(null), 0f)
        assertEquals(16f, WidgetSizes.corner(16f), 0f)
        assertEquals(28f, WidgetSizes.corner(32f), 0f)
        assertEquals(28f, WidgetSizes.corner(0f), 0f)
    }

    @Test fun `bitmaps stay inside the RemoteViews limit`() {
        val budget = WidgetSizes.bitmapBudget(1080, 2400)
        assertEquals(1080L * 2400 * 6, budget)
        assertEquals(1f, WidgetSizes.bitmapScale(1_000_000, budget), 0f)
        val big = 10_000_000L
        val s = WidgetSizes.bitmapScale(big, budget)
        assertTrue(s < 1f)
        assertTrue(big * s * s * 4 <= budget)
    }
}
