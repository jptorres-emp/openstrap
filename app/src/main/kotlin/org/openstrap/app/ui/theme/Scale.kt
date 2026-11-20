package org.openstrap.app.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.openstrap.app.ui.glow.CardTuning
import org.openstrap.app.ui.glow.GlowLayout
import sv.lib.squircleshape.SquircleShape

/**
 * The app's spacing scale and the only place a gap is written as a number (SpaceLiteralTest fails on a `.dp` literal
 * inside `padding`, `PaddingValues`, `spacedBy` or a `Spacer` anywhere else under ui/, bar the files it lists). Eight
 * steps, and a card's own padding ([Inset.card]) next to them. 1 dp stays for hairlines and borders and is not a step.
 * Pick by what the gap sits between: tight siblings take the smaller step, separate parts the larger.
 */
internal object Space {
    val S2 = 2.dp
    val S4 = 4.dp
    val S8 = 8.dp
    val S12 = 12.dp
    val S16 = 16.dp
    val S20 = 20.dp
    val S24 = 24.dp
    val S32 = 32.dp

    /** The eight steps in dp, smallest first (what SpaceLiteralTest checks the scale against). */
    val Steps: List<Dp> = listOf(S2, S4, S8, S12, S16, S20, S24, S32)
}

/** What a card, panel or list pads itself with, derived from its corner. */
internal object Inset {
    /** From a card's edge to its content: 16 plus 15% of the card's corner, so about 22 to 26 dp. */
    fun card(cornerDp: Float): Dp = CardTuning.paddingFor(cornerDp).dp

    /** The side inset of a list card and of the rows inside it: a list is tall, so its corner is the cap. */
    val list: Dp get() = CardTuning.listInset
}

/**
 * The corners. Cards follow one rule (36% of the shorter side, capped at 64, smoothing 65: [Card], which lives in
 * GlowLayout), a sheet has a fixed top corner, and what sits inside a card takes [Inner], [Chip] or [Pip]. An icon tile,
 * an icon button, a day chip and a bar are all [Pill]: only cards are squircles.
 */
internal object Corner {
    /** The card rule: [GlowLayout.CardShape]. */
    val Card: Shape get() = GlowLayout.CardShape

    val SheetRadius = 32.dp

    /** A sheet's top corners; its bottom edge is the screen's. */
    val Sheet: Shape = SquircleShape(
        topStart = SheetRadius, topEnd = SheetRadius, bottomStart = 0.dp, bottomEnd = 0.dp, smoothing = GlowLayout.SMOOTHING,
    )

    /** What sits inside a card or a panel and is not a card: a map, a menu, a goal's or a plan's tile. */
    val Inner: Shape = SquircleShape(16.dp, smoothing = GlowLayout.SMOOTHING)

    /** A tappable label or a field inside a row. */
    val Chip: Shape = SquircleShape(10.dp, smoothing = GlowLayout.SMOOTHING)

    /** A colour pip, a legend swatch or a chart column: a barely rounded corner. */
    val Pip: Shape = RoundedCornerShape(4.dp)

    /** An icon tile, an icon button, a day chip, a bar, a grabber: a pill, which is a circle on a square. */
    val Pill: Shape = CircleShape
}

/** The sizes of round things that hold an icon (tiles and buttons), and of the glyphs inside them. */
internal object IconSize {
    /** A row's tile, a chevron circle, an (i) on a card. */
    val TileS = 32.dp

    /** Header and back buttons, steppers, a sport tile in a row, a day chip. */
    val TileM = 44.dp

    /** A sport tile in a grid, Share. */
    val TileL = 64.dp

    /** The live screen's side actions. */
    val Live = 60.dp

    /** The live screen's Pause. */
    val Pause = 88.dp

    /** Glyph sizes: a caret, then the glyphs in a 32, 44, 64 tile (28 in the Pause button). */
    val Caret = 14.dp
    val GlyphS = 16.dp
    val GlyphM = 20.dp
    val GlyphL = 24.dp
    val GlyphXl = 28.dp
}
