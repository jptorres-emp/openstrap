package org.openstrap.app.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openstrap.app.ui.glow.BigNumberSpec
import org.openstrap.app.ui.glow.ChipSize
import org.openstrap.app.ui.glow.FIGURE_PART
import org.openstrap.app.ui.glow.ONLY_DIGITS
import org.openstrap.app.ui.glow.draw.DotFont
import org.openstrap.app.ui.glow.ChipSpec
import org.openstrap.app.ui.glow.ChipVariant
import org.openstrap.app.ui.glow.FigureSpec
import org.openstrap.app.ui.glow.GlowType
import org.openstrap.app.ui.glow.NoteSpec
import org.openstrap.app.ui.glow.NoteVariant
import org.openstrap.app.ui.glow.ProgressSpec
import org.openstrap.app.ui.glow.SegmentSize
import org.openstrap.app.ui.glow.SegmentSpec
import org.openstrap.app.ui.glow.SheetSpec
import org.openstrap.app.ui.glow.StepperSize
import org.openstrap.app.ui.glow.StepperSpec
import org.openstrap.app.ui.glow.TagSpec
import org.openstrap.app.ui.glow.TagVariant
import org.openstrap.app.ui.glow.TileSize
import org.openstrap.app.ui.glow.TileSpec
import org.openstrap.app.ui.glow.TokenSize
import org.openstrap.app.presentation.Units
import org.openstrap.app.ui.theme.Greys
import org.openstrap.app.ui.theme.Ink
import org.openstrap.app.ui.theme.Space
import org.openstrap.app.ui.theme.stepUp

/**
 * The small parts (docs/design/halo/components-audit.md, 3.6, 3.8 to 3.13 and 3.17 to 3.22): chips, tags, segments,
 * steppers, notes, figures, progress, tiles, tokens and the big number take their measurements from their specs, one ring
 * width marks every picked state, every grey steps up from the surface it sits on, and the old composables are gone.
 */
class HaloSmallPartsTest {
    private val ui = UiSources()

    @Test fun `a chip is 36, 44 as the main control, 14 at the sides, label 14`() {
        assertEquals(listOf(36.dp, 44.dp), ChipSize.entries.map { it.height })
        assertEquals(14.dp, ChipSpec.Side)
        assertEquals(Space.S12, ChipSpec.SideLead)
        assertEquals(14f, ChipSpec.Label.fontSize.value)
        assertEquals(listOf("Outline", "Filled", "Choice"), ChipVariant.entries.map { it.name })
        val chip = ui.code("glow/HaloChip.kt").substringAfter("internal fun HaloChip(").substringBefore("/** The tag's")
        assertTrue("picked is the Picked grey with the one ring", "OnSurface.picked" in chip && "ChipSpec.Ring, Color.White" in chip)
        assertTrue("a chip's fill steps up from its surface", "OnSurface.control" in chip)
    }

    @Test fun `a tag is 22 high, 8 at the sides, 11 semibold, in six looks`() {
        assertEquals(22.dp, TagSpec.Height)
        assertEquals(Space.S8, TagSpec.Side)
        assertEquals(11f, TagSpec.Label.fontSize.value)
        assertEquals(GlowType.tag, TagSpec.Label)
        assertEquals(listOf("Filled", "Outline", "Text", "Marker", "Quiet", "Status"), TagVariant.entries.map { it.name })
    }

    @Test fun `segments are 44 or 36 on a track padded 4, the detail range Compact`() {
        assertEquals(listOf(44.dp, 36.dp), SegmentSize.entries.map { it.height })
        assertEquals(Space.S4, SegmentSpec.Pad)
        val detail = ui.code("glow/GlowMetricDetail.kt")
        assertTrue("the detail's range is Compact 36", "size = SegmentSize.Compact" in detail)
        assertEquals("one segmented control", 1, ui.find(Regex("""fun GlowSegments\(""")).size)
    }

    @Test fun `a stepper's buttons are 44, its number 44 on a sheet and 26 in a row`() {
        assertEquals(44.dp, StepperSpec.Button.size)
        assertEquals(44f, StepperSize.Sheet.number.fontSize.value)
        assertEquals(26f, StepperSize.Row.number.fontSize.value)
        assertEquals(104.dp, StepperSpec.RowNumberWidth)
        val buttons = ui.code("glow/HaloStepper.kt")
        assertTrue("minus and plus are HaloIconButton", Regex("""HaloIconButton\(AppIcons\.(Minus|Plus)""").findAll(buttons).count() == 2)
    }

    @Test fun `a note is inline 13, a footnote 12 four in, a caveat a 6 dot`() {
        assertEquals(13f, NoteVariant.Inline.style.fontSize.value)
        assertEquals(12f, NoteVariant.Footnote.style.fontSize.value)
        assertEquals(Ink.Tertiary, NoteVariant.Footnote.ink)
        assertEquals(Space.S4, NoteSpec.FootnoteSide)
        assertEquals(Space.S8, NoteSpec.FootnoteGap)
        assertEquals(6.dp, NoteSpec.CaveatDot)
    }

    @Test fun `figures are 28 over 12, 4 apart, a 1 rule 16 before, three to a row`() {
        assertEquals(28f, FigureSpec.Value.fontSize.value)
        assertEquals(12f, FigureSpec.Label.fontSize.value)
        assertEquals(Space.S4, FigureSpec.Gap)
        assertEquals(1.dp, FigureSpec.Rule)
        assertEquals(Space.S16, FigureSpec.RuleGap)
        assertEquals(3, FigureSpec.COLUMNS)
        assertTrue("the rule steps up from its surface", "OnSurface.line" in ui.code("glow/HaloFigures.kt"))
    }

    @Test fun `progress is a 6 bar, segments 6 high 4 apart, a ring 2_5 in a row`() {
        assertEquals(6.dp, ProgressSpec.BarHeight)
        assertEquals(6.dp, ProgressSpec.SegmentHeight)
        assertEquals(Space.S4, ProgressSpec.SegmentGap)
        assertEquals(2.5.dp, ProgressSpec.RowRingStroke)
        val bar = ui.code("glow/HaloProgress.kt").substringAfter("internal fun HaloProgress(").substringBefore("\n}\n")
        assertTrue("the track steps up from its surface", "OnSurface.line" in bar)
    }

    @Test fun `a tile is a circle of 32, 44 or 64 with a 16, 20 or 24 glyph`() {
        assertEquals(listOf(32.dp, 44.dp, 64.dp), TileSize.entries.map { it.size })
        assertEquals(listOf(16.dp, 20.dp, 24.dp), TileSize.entries.map { it.glyph })
        assertEquals(0.16f, TileSpec.INK_TINT, 1e-6f)
        val tile = ui.code("glow/HaloProgress.kt").substringAfter("internal fun HaloTile(")
        assertTrue("always a circle", "Corner.Pill" in tile)
    }

    @Test fun `tokens are 56, 72, 96 and 160, the waiting dash on a tile 28 by 6`() {
        assertEquals(listOf(56.dp, 72.dp, 96.dp, 160.dp), TokenSize.entries.map { it.size })
        assertEquals(28.dp, BigNumberSpec.DashWidth)
        assertEquals(6.dp, BigNumberSpec.DashHeight)
        assertEquals(62.dp, ChipSpec.DayStrip)
        assertEquals(54.dp, ChipSpec.DayCalendar)
    }

    @Test fun `one ring width marks every picked state`() {
        assertEquals(1.5.dp, ChipSpec.Ring)
        val rings = ui.find(Regex("""border\((1\.5|2|2\.5)\.dp"""), setOf("glow/ShareCards.kt"))
        assertTrue("A picked ring is ChipSpec.Ring:\n" + rings.joinToString("\n"), rings.none { "Color.White" in it })
    }

    @Test fun `a sheet is Card and everything on it steps up the ladder`() {
        assertEquals(Greys.Card, SheetSpec.Fill)
        // On the page, on a card and on a sheet: a panel one up, a line and a track at least Raised and Control.
        assertEquals(Greys.Card, stepUp(Greys.Page, 1))
        assertEquals(Greys.Raised, stepUp(SheetSpec.Fill, 1))
        assertEquals(Greys.Raised, stepUp(Greys.Page, 1, Greys.Raised))
        assertEquals(Greys.Control, stepUp(Greys.Card, 1, Greys.Control))
        // A panel on a sheet is Raised, so what sits on it is Control, then Picked: never back down.
        assertEquals(Greys.Control, stepUp(Greys.Raised, 1))
        assertEquals(Greys.Picked, stepUp(Greys.Control, 1))
        assertEquals(Greys.Picked, stepUp(Greys.Picked, 1))
        val card = ui.code("glow/HaloCard.kt")
        assertTrue("a grey card is one step above its surface and becomes the surface", "OnSurface.panel" in card && "LocalSurface provides" in card)
        assertTrue("the sheet provides its fill as the surface", "LocalSurface provides SheetSpec.Fill" in ui.code("components/Glass.kt"))
        assertTrue("a row's divider steps up", "OnSurface.line" in ui.code("glow/HaloRow.kt"))
        assertTrue("segments' track steps up", "OnSurface.track" in ui.code("glow/HaloChip.kt"))
    }

    @Test fun `the old small parts are gone`() {
        val old = listOf(
            "PageChip", "IconPill", "RecentPill", "DayChip", "OutlineTag", "NewsPill", "RowTag", "StateMarker", "StatusPill",
            "MinusPlus", "BpmStepper", "GoalStepper", "ValueStepper", "LogStepper", "PageNote", "PageFootnote", "GoalNote",
            "RowNote", "EditedNote", "CaveatLine", "DetailFigures", "SummaryFigures", "LiveStat", "LiveValue", "StatRule",
            "RecapFigures", "LoadFigure", "PageProgress", "ShareBar", "HaloNumber", "DotFigure", "HeroNumber", "EffortLight",
            "OptionButton", "PickedRing", "RowHairline",
        )
        val found = old.flatMap { name -> ui.find(Regex("""\b(fun|val)\s+(\w+\.)?$name\b""")) }
        assertTrue("These were folded into the small parts:\n" + found.joinToString("\n"), found.isEmpty())
        val used = old.flatMap { name -> ui.find(Regex("""(?<![\w.])$name\(""")) }
        assertTrue("Still called:\n" + used.joinToString("\n"), used.isEmpty())
        val dots = ui.find(Regex("""(?<![\w.])BigNumberText\("""), setOf("glow/ShareCards.kt", "glow/HaloBigNumber.kt"))
            .filterNot { it.startsWith("glow/GlowType.kt:") && "fun BigNumberText" in it }
        assertTrue("Only HaloBigNumber and the share pictures draw BigNumberText:\n" + dots.joinToString("\n"), dots.isEmpty())
    }

    @Test fun `a grouped big number is one dot number, its groups two columns apart, not two numbers edge to edge`() {
        val steps = Units.count(25_559, Units.DOT_LOCALE)
        assertEquals("25\u2009559", steps)
        assertTrue("A grouped number is dots alone", ONLY_DIGITS.matches(steps))
        val figure = Units.count(25_559, Units.DOT_LOCALE) + " of " + Units.count(30_000, Units.DOT_LOCALE)
        assertEquals(listOf(steps, " of ", "30\u2009000"), FIGURE_PART.findAll(figure).map { it.value }.toList())
        // DotFont spaces the groups: the thin space adds one empty column to the one between digits.
        assertEquals(DotFont.columns("25") + 2 + DotFont.columns("559"), DotFont.columns(steps))
    }
}
