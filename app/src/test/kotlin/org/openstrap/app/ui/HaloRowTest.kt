package org.openstrap.app.ui

import org.openstrap.app.ui.glow.HaloTile
import org.openstrap.app.ui.theme.OnSurface

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openstrap.app.ui.glow.RowSpec
import org.openstrap.app.ui.theme.IconSize
import org.openstrap.app.ui.theme.Space

/**
 * The one row (docs/design/halo/components-audit.md, 3.5): its measurements come from the scale, and the old row
 * composables are gone, so every list shares one frame and only the data differs.
 */
class HaloRowTest {
    private val ui = UiSources()

    @Test fun `a row is 56 tall, 64 with a line or a slot under the title`() {
        assertEquals(56.dp, RowSpec.MinHeight)
        assertEquals(64.dp, RowSpec.MinHeightTall)
        assertEquals(IconSize.TileS, RowSpec.MinHeightCompact)
    }

    @Test fun `a row pads 12 above and below and keeps 12 between its slots`() {
        assertEquals(Space.S12, RowSpec.Pad)
        assertEquals(Space.S12, RowSpec.Gap)
        assertEquals(Space.S2, RowSpec.LineGap)
        assertEquals(Space.S8, RowSpec.BelowGap)
    }

    @Test fun `a tile is a 32 circle with a 16 glyph, a sport tile 44, and a chevron 14`() {
        assertEquals(32.dp, RowSpec.Tile)
        assertEquals(IconSize.TileS, RowSpec.Tile)
        assertEquals(16.dp, RowSpec.Glyph)
        assertEquals(IconSize.GlyphS, RowSpec.Glyph)
        assertEquals(44.dp, RowSpec.Sport)
        assertEquals(36.dp, RowSpec.Action)
        assertEquals(22.dp, RowSpec.Radio)
        assertEquals(0.16f, RowSpec.TILE_TINT, 1e-6f)
    }

    @Test fun `the row's frame is written once, in HaloRow, from RowSpec`() {
        val row = ui.code("glow/HaloRow.kt")
        val body = row.substringAfter("internal fun HaloRow(").substringBefore("/** The title with what shares")
        assertTrue("min height from RowSpec", "RowSpec.MinHeightTall" in body && "RowSpec.MinHeight" in body)
        assertTrue("vertical padding from RowSpec.Pad", "RowSpec.Pad" in body)
        assertTrue("sides from LocalRowInset", "LocalRowInset.current" in body && "horizontal = inset" in body)
        assertTrue("divider is one step above the surface, inset to the sides", "insetHairline(divider, inset, OnSurface.line)" in body)
        assertTrue("a row's tile is HaloTile at the spec's size", "RowSpec.Tile" in row && "HaloTile(icon" in row)
    }

    @Test fun `the old row composables are gone`() {
        val old = listOf(
            "PageRow", "PageInfoRow", "PageSwitchRow", "PageActionRow", "PageItemRow", "ListRow", "DetailRow", "DriverRow",
            "RadioRow", "RowIcon", "PanelRowInset",
        )
        val found = old.flatMap { name -> ui.find(Regex("""\b(fun|val)\s+$name\b""")) }
        assertTrue("These were folded into HaloRow:\n" + found.joinToString("\n"), found.isEmpty())
        assertTrue("LocalRowBleed is LocalRowInset now", ui.find(Regex("""\bLocalRowBleed\b""")).isEmpty())
    }

    @Test fun `every list card gives its rows the same inset`() {
        val gives = Regex("""LocalRowInset provides (\S+)""")
        val provided = ui.sources.flatMap { (path, lines) -> gives.findAll(lines.joinToString("\n")).map { path to it.groupValues[1] } }
        // Every card of rows is HaloCard's CardFill.Rows, which gives its rows Inset.list: the one place it is provided.
        val lists = provided.filter { it.first == "glow/HaloCard.kt" }
        assertEquals(1, lists.size)
        assertTrue("only HaloCard gives rows their inset:\n$provided", provided.all { it.first == "glow/HaloCard.kt" || it.first == "glow/HaloRow.kt" })
        assertTrue(lists.toString(), lists.all { it.second.trimEnd(')') == "Inset.list" || it.second.trimEnd(')') == "inset" })
    }
}
