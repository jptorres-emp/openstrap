package org.openstrap.app.ui

import org.openstrap.app.ui.theme.LocalSurface
import androidx.compose.ui.text.style.TextAlign

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openstrap.app.ui.glow.GlowType
import org.openstrap.app.ui.glow.HaloSheetVariant
import org.openstrap.app.ui.glow.SheetSpec
import org.openstrap.app.ui.theme.Corner
import org.openstrap.app.ui.theme.Space

/**
 * The one sheet (docs/design/halo/components-audit.md, 3.14): its measurements come from [SheetSpec], its variants are
 * [HaloSheetVariant], and the old sheet composables are gone, so every sheet shares one corner, one grabber, one set
 * of sides and one title.
 */
class HaloSheetTest {
    private val ui = UiSources()

    @Test fun `a sheet has a top corner of 32`() {
        assertEquals(32.dp, Corner.SheetRadius)
        assertTrue("the surface is Corner.Sheet", "shape = Corner.Sheet" in ui.code("components/Glass.kt").substringAfter("fun GlassSheet("))
        assertTrue("no other sheet corner", ui.find(Regex("""\bSheetCorner\b|\bSheetShape\b""")).isEmpty())
    }

    @Test fun `the grabber is 36 by 4 in the line ink, 10 from the top, and written once`() {
        assertEquals(36.dp, SheetSpec.GrabberWidth)
        assertEquals(4.dp, SheetSpec.GrabberHeight)
        assertEquals(10.dp, SheetSpec.Top)
        val sheet = ui.code("components/Glass.kt").substringAfter("fun GlassSheet(")
        assertTrue("size, top and colour from the spec", "SheetSpec.GrabberWidth, SheetSpec.GrabberHeight" in sheet && "padding(top = SheetSpec.Top)" in sheet && "Ink.Line" in sheet)
        assertTrue("no grabber literal", "40.dp" !in sheet && "5.dp" !in sheet)
    }

    @Test fun `a sheet pads 20 at the sides, 16 for grouped lists, 32 at the foot, with 16 between blocks`() {
        assertEquals(Space.S20, SheetSpec.Side)
        assertEquals(Space.S16, SheetSpec.SideGrouped)
        assertEquals(Space.S32, SheetSpec.Bottom)
        assertEquals(Space.S16, SheetSpec.Gap)
        assertEquals(listOf(20.dp, 16.dp, 20.dp, 20.dp), HaloSheetVariant.entries.map { it.side })
        assertEquals(listOf("Page", "Grouped", "Info", "Token"), HaloSheetVariant.entries.map { it.name })
        assertEquals(HaloSheetVariant.Page, HaloSheetVariant.entries.first())
    }

    @Test fun `a token sheet is a centred column with 8 between its parts, the rest keep 16`() {
        assertEquals(Space.S8, SheetSpec.TokenGap)
        assertEquals(listOf(16.dp, 16.dp, 16.dp, 8.dp), HaloSheetVariant.entries.map { it.gap })
        assertEquals(listOf(false, false, false, true), HaloSheetVariant.entries.map { it.centred })
        assertEquals(Space.S8, SheetSpec.SectionGap)
        assertEquals(Space.S4, SheetSpec.TitleNoteGap)
    }

    @Test fun `every variant's title is the 22 sheet title, a token's centred`() {
        assertEquals(22f, SheetSpec.Title.fontSize.value)
        assertEquals(GlowType.sheetTitle, SheetSpec.Title)
        val sheet = ui.code("glow/HaloSheet.kt").substringAfter("internal fun HaloSheet(").substringBefore("internal fun HaloConfirmSheet(")
        assertEquals("the title is drawn once, for every variant", 1, Regex("""style = SheetSpec\.Title""").findAll(sheet).count())
        assertTrue("centred on a token, from the variant", "variant.centred" in sheet && "TextAlign.Center" in sheet)
        assertTrue("sides, gap and lines from the variant", "variant.side" in sheet && "variant.gap" in sheet && "variant.titleLines" in sheet)
        val badges = ui.code("glow/BadgeSheets.kt")
        assertTrue("the badge sheets pass their title, not a 17 of their own", "pageTitle" !in badges && "variant = HaloSheetVariant.Token" in badges)
    }

    @Test fun `a sheet keeps its scrolling, its width cap, its pull and its insets`() {
        val sheet = ui.code("glow/HaloSheet.kt")
        assertEquals(0.88f, SheetSpec.MAX_HEIGHT)
        assertTrue("scrolls past 88% of the screen", "heightIn(max = maxHeight).verticalScroll(" in sheet && "SheetSpec.MAX_HEIGHT" in sheet)
        assertTrue("clear of the navigation bar", "navigationBarsPadding()" in sheet)
        val glass = ui.code("components/Glass.kt").substringAfter("fun GlassSheet(")
        assertTrue("at most a page wide", "widthIn(max = org.openstrap.app.ui.glow.GlowLayout.PageMaxWidth)" in glass)
        assertTrue("pulls down to dismiss", "nestedScroll(pullOnTop)" in glass && "draggable(" in glass)
        assertTrue("the grabber's colour is Ink.Line and the fill SheetSpec.Fill", "color = SheetSpec.Fill" in glass)
        assertEquals(org.openstrap.app.ui.theme.Greys.Card, org.openstrap.app.ui.glow.SheetSpec.Fill)
        assertTrue("the sheet is the surface what sits on it steps up from", "LocalSurface provides SheetSpec.Fill" in glass)
    }

    @Test fun `the footer is the sheet's last block and the confirm sheet is a pair`() {
        val sheet = ui.code("glow/HaloSheet.kt")
        assertTrue("content then footer", Regex("""content\(\)\s+footer\?\.invoke\(\)""").containsMatchIn(sheet))
        val confirm = sheet.substringAfter("internal fun HaloConfirmSheet(").substringBefore("internal fun SheetSection(")
        assertTrue("Cancel and the confirm are a ButtonPair", "ButtonPair(" in confirm && "HaloButtonVariant.Destructive" in confirm)
    }

    @Test fun `the old sheet composables are gone`() {
        val old = listOf("PageSheet", "PageConfirm", "SheetColumn", "SheetTitle", "SheetText", "SheetActions", "SaveCancel")
        val found = old.flatMap { name -> ui.find(Regex("""\b(fun|val|class)\s+(\w+\.)?$name\b""")) }
        assertTrue("These were folded into HaloSheet:\n" + found.joinToString("\n"), found.isEmpty())
        val used = old.flatMap { name -> ui.find(Regex("""\b$name\(""")) }
        assertTrue("Still called:\n" + used.joinToString("\n"), used.isEmpty())
    }

    @Test fun `GlassSheet is called by HaloSheet alone`() {
        val calls = ui.find(Regex("""\bGlassSheet\("""), setOf("components/Glass.kt")).filter { !it.startsWith("glow/HaloSheet.kt:") }
        assertTrue("A sheet is HaloSheet:\n" + calls.joinToString("\n"), calls.isEmpty())
    }
}
