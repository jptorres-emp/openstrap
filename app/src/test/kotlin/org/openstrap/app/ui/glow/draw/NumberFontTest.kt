package org.openstrap.app.ui.glow.draw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openstrap.app.ui.glow.DotSize

/** The numbers' face keeps each size's height and takes less width than the dots did. */
class NumberFontTest {
    @Test fun `a number keeps the height its dot size gave the dots`() {
        DotSize.entries.forEach { size ->
            assertEquals(size.name, dotLayout("72", size, 1f).height, NumberFont.height(size.dot.value), 1e-4f)
        }
        // The hero's digits: 4.5 dp dots, seven rows and six gaps.
        assertEquals(41.76f, NumberFont.height(4.5f), 1e-3f)
        assertEquals(41.76f / 0.7f, NumberFont.em(41.76f), 1e-3f)
    }

    @Test fun `figures are tabular and narrower than the dots`() {
        val em = NumberFont.em(NumberFont.height(3f))
        assertEquals(NumberFont.width("11", em), NumberFont.width("88", em), 1e-4f)
        listOf("72", "8,432", "7:20", "−94", "3 878", "62:07").forEach { text ->
            val dots = dotLayout(text, DotSize.Edge, 1f).width
            val font = NumberFont.width(text, em)
            assertTrue("$text: $font vs $dots", font < dots)
        }
        // A widget's figures leave room for a comma's tail inside the same box.
        val h = NumberFont.height(3f)
        assertEquals(h, NumberFont.widgetEm(h) * (NumberFont.FIGURE_HEIGHT + NumberFont.DESCENT), 1e-4f)
    }
}
