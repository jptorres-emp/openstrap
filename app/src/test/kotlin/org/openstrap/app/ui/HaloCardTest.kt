package org.openstrap.app.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openstrap.app.ui.glow.BannerKind
import org.openstrap.app.ui.glow.BannerSpec
import org.openstrap.app.ui.glow.CardSpec
import org.openstrap.app.ui.glow.CardTuning
import org.openstrap.app.ui.glow.GlowGrid
import org.openstrap.app.ui.glow.GlowLayout
import org.openstrap.app.ui.glow.GlowType
import org.openstrap.app.ui.glow.HaloCardSize
import org.openstrap.app.ui.glow.PageSpec
import org.openstrap.app.ui.glow.PageVariant
import org.openstrap.app.ui.glow.TitleSpec
import org.openstrap.app.ui.glow.TitleVariant
import org.openstrap.app.ui.theme.Corner
import org.openstrap.app.ui.theme.IconSize
import org.openstrap.app.ui.theme.Inset
import org.openstrap.app.ui.theme.Space

/**
 * The card, the title, the banner and the page frame (docs/design/halo/components-audit.md, 3.1 to 3.4 and 3.15): their
 * measurements come from [CardSpec], [TitleSpec], [BannerSpec] and [PageSpec], and the old composables are gone, so every
 * card shares one corner, one smoothing and one inset, and every page one gutter and one header.
 */
class HaloCardTest {
    private val ui = UiSources()

    @Test fun `a card's corner is 36 percent of its shorter side, at most 64, at one smoothing`() {
        assertEquals(36f, CardSpec.CORNER_PERCENT)
        assertEquals(64.dp, CardSpec.CornerCap)
        assertEquals(65, CardSpec.SMOOTHING)
        assertEquals(GlowLayout.SMOOTHING, CardTuning.SMOOTHING)
        assertEquals(CardTuning.shape, GlowLayout.CardShape)
        assertEquals(CardTuning.shape, Corner.Card)
        assertTrue("no second smoothing", ui.find(Regex("""\bsmoothing\s*=\s*55\b|SOLID_SMOOTHING|\bsolidShape\b|\blitShape\b""")).isEmpty())
    }

    @Test fun `a card's inset is 16 plus 15 percent of its corner, 22 to 26`() {
        assertEquals(21.4f, CardSpec.inset(36f).value, 1e-3f)
        assertEquals(25.6f, CardSpec.inset(64f).value, 1e-3f)
        assertEquals(Inset.card(40f), CardSpec.inset(40f))
        assertEquals(CardSpec.inset(64f), Inset.list)
        assertEquals(Space.S12, CardSpec.Gap)
    }

    @Test fun `a Small is 200, a Wide 160, a strip 64, and the grid takes them from the card`() {
        assertEquals(listOf(200.dp, 160.dp, 64.dp, null), HaloCardSize.entries.map { it.height })
        assertEquals(listOf("Small", "Wide", "Strip", "Free"), HaloCardSize.entries.map { it.name })
        assertEquals(CardSpec.StripHeight, GlowGrid.StripHeight)
        assertEquals(CardSpec.SmallHeight, GlowGrid.SmallHeight)
        assertEquals(CardSpec.WideHeight, GlowGrid.UnitHeight)
        assertEquals(CardSpec.inset(64f * 0.36f).value, CardSpec.StripInset.value, 1e-3f)
    }

    @Test fun `the card is drawn once, its light hosted not redrawn`() {
        val card = ui.code("glow/HaloCard.kt").substringAfter("internal fun HaloCard(")
        assertTrue("one shape for grey and outline", "CardTuning.shape" in card)
        assertTrue("the grey fill one step above its surface and the outline ring", "OnSurface.panel" in card && "CardSpec.OutlineWidth" in card)
        assertTrue("padding by the card rule", "cornerPadding { CardTuning.corner }" in card)
        assertTrue("rows take the list inset", "LocalRowInset provides Inset.list" in card)
        assertTrue("a lit card is LitSurface", "LitSurface(" in card)
        assertTrue("a tapped card grows into what it opens", "cardOpening(onClick, shape)" in card && "pressClickable(" in card)
        val calls = ui.find(Regex("""\bLitSurface\(""")).filter { !it.startsWith("glow/HaloCard.kt:") && !it.startsWith("glow/GlowCard.kt:") }
        assertTrue("Only HaloCard draws a lit card:\n" + calls.joinToString("\n"), calls.isEmpty())
    }

    @Test fun `a title is 15, 16 on a hero, its note 13, 12 from it or 2 under it`() {
        assertEquals(GlowType.cardTitle, TitleSpec.Text)
        assertEquals(15f, TitleSpec.Text.fontSize.value)
        assertEquals(16f, TitleSpec.Hero.fontSize.value)
        assertEquals(GlowType.heroTitle, TitleSpec.Hero)
        assertEquals(13f, TitleSpec.Note.fontSize.value)
        assertEquals(13f, TitleSpec.Group.fontSize.value)
        assertEquals(Space.S12, TitleSpec.NoteGap)
        assertEquals(Space.S2, TitleSpec.Drop)
        assertEquals(Space.S4, TitleSpec.OutSide)
        assertEquals(listOf("InCard", "OutOfCard", "Group"), TitleVariant.entries.map { it.name })
    }

    @Test fun `a banner is a grey card with a 44 tile at 16 percent and a 36 action`() {
        assertEquals(44.dp, BannerSpec.Tile)
        assertEquals(IconSize.TileM, BannerSpec.Tile)
        assertEquals(0.16f, BannerSpec.TINT, 1e-6f)
        assertEquals(36.dp, BannerSpec.Action.height)
        assertEquals(15f, BannerSpec.Title.fontSize.value)
        assertEquals(13f, BannerSpec.Note.fontSize.value)
        assertEquals(listOf("Good", "Warn", "Problem", "Info"), BannerKind.entries.map { it.name })
        assertTrue("a banner is a HaloCard", "HaloCard(modifier" in ui.code("glow/HaloBanner.kt"))
    }

    @Test fun `a page is 16 from the edges with its family's gap`() {
        assertEquals(16.dp, PageSpec.Gutter)
        assertEquals(GlowLayout.Gutter, PageSpec.Gutter)
        assertEquals(listOf(12.dp, 16.dp, 24.dp, 12.dp), PageVariant.entries.map { it.gap })
        assertEquals(listOf("Tab", "Detail", "Settings", "Workout"), PageVariant.entries.map { it.name })
        val page = ui.code("glow/HaloPage.kt")
        assertTrue("the gap is the variant's", Regex("""spacedBy\(variant\.gap\)""").findAll(page).count() == 2)
    }

    @Test fun `a header is 48 high, 60 on a tab, 96 a side, with 44 buttons`() {
        assertEquals(48.dp, PageSpec.HeaderHeight)
        assertEquals(60.dp, PageSpec.TabHeaderHeight)
        assertEquals(96.dp, PageSpec.HeaderSide)
        assertEquals(44.dp, PageSpec.HeaderButton)
        assertTrue("the tab header takes its height from PageSpec", "PageSpec.TabHeaderHeight" in ui.code("dashboard/MomentHeader.kt"))
        for (f in listOf("glow/ReadyScreen.kt", "glow/live/LiveFrame.kt", "glow/GlowWorkoutSummary.kt")) {
            assertTrue("$f's top bar is HaloPageHeader", "HaloPageHeader(" in ui.code(f))
        }
    }

    @Test fun `the old card, title, banner and page composables are gone`() {
        val old = listOf(
            "GlowCard", "PictureCard", "QuietPanel", "LitPanel", "OutlineCard", "PagePanel", "PageTextPanel", "PageRows",
            "GlowHeader", "CardTitle", "TitleRow", "EdgeTitle", "CardHead", "DetailHeading", "SectionHead", "SectionLabel",
            "StripLine", "WorkoutDayHeader", "StatusBanner", "ErrorPanel", "InfoLine", "ScreenList", "WorkoutPage", "PageHeader",
            "DetailNav", "solidCard",
        )
        val found = old.flatMap { name -> ui.find(Regex("""\b(fun|val)\s+(\w+\.)?$name\b""")) }
        assertTrue("These were folded into the Halo components:\n" + found.joinToString("\n"), found.isEmpty())
        val used = old.flatMap { name -> ui.find(Regex("""(?<![\w.])$name\(""")) }
        assertTrue("Still called:\n" + used.joinToString("\n"), used.isEmpty())
    }
}
