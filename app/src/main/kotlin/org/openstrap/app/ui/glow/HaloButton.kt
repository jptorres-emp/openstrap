package org.openstrap.app.ui.glow

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.openstrap.app.ui.components.pressClickable
import org.openstrap.app.ui.theme.Corner
import org.openstrap.app.ui.theme.Greys
import org.openstrap.app.ui.theme.IconSize
import org.openstrap.app.ui.theme.Ink
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.app.ui.theme.Space

/*
 * The one button and the one icon button (docs/design/halo/components-audit.md, 3.7): a text button is [HaloButton] with
 * a variant and one of four sizes, a round button holding only an icon is [HaloIconButton], and two side by side are a
 * [ButtonPair]. Every measurement is in [ButtonSpec], so every button of a kind shares one height, one padding, one
 * type and one glyph; only the words and the icon differ. Buttons are pills and icon buttons circles, never squircles.
 * One [HaloButtonVariant.Primary] to a screen is the guideline; nothing enforces it.
 */

/** The measurements of every button, from the scale (ui/theme/Scale.kt) bar the three heights and sides it has no step for. */
internal object ButtonSpec {
    /** A text button's heights: the one white tap, a page's own, one on a card, a small pill beside content. */
    val HeightMain = IconSize.TileL
    val HeightPage = 56.dp
    val HeightCard = IconSize.TileM
    val HeightSmall = 36.dp

    /** The padding at each side of the label, by size. */
    val SideMain = 22.dp
    val SidePage = 18.dp
    val SideCard = 18.dp
    val SideSmall = 14.dp

    /** A leading icon, and the gap between it and the label. */
    val Glyph = IconSize.GlyphS
    val IconGap = Space.S8

    /** A [HaloButtonVariant.Text] button: only its words, at least this tall to tap, with this padding at each side. */
    val TextMinHeight = IconSize.TileM
    val TextSide = Space.S16

    /** Between the two buttons of a [ButtonPair]. */
    val PairGap = Space.S12

    /** An icon button's sizes (round) and the glyph inside each: 16 in 32, 20 in 44, 22 in 60, 28 in 88. */
    val IconSmall = IconSize.TileS
    val IconDefault = IconSize.TileM
    val IconLive = IconSize.Live
    val IconPause = IconSize.Pause
    val GlyphSmall = IconSize.GlyphS
    val GlyphDefault = IconSize.GlyphM
    val GlyphLive = 22.dp
    val GlyphPause = IconSize.GlyphXl

    /** What a disabled button fades to, pill and label together. */
    const val DISABLED = 0.4f
}

/** What a button is for: the one white tap, an ordinary one, one on a lit card, one that deletes, or only words. */
internal enum class HaloButtonVariant {
    /** White fill, black label: the screen's one main action. */
    Primary,

    /** [Greys.Control] fill, white label. */
    Secondary,

    /** On a lit card: the translucent [Ink.Faint] fill with a hairline, so the card's colour shows through. */
    Glass,

    /** [Secondary]'s fill with a red label. */
    Destructive,

    /** No fill, a 72% white label, at least 44 tall to tap. */
    Text,
}

/** A text button's height, side padding and label, by size (the HaloComponents board). */
internal enum class HaloButtonSize(val height: Dp, val side: Dp, val label: TextStyle) {
    /** The one white tap at the foot of a page: 64 tall, 17 semibold. */
    Main(ButtonSpec.HeightMain, ButtonSpec.SideMain, GlowType.buttonMain),

    /** A page's own buttons and a sheet's pair: 56 tall, 16 semibold. */
    Page(ButtonSpec.HeightPage, ButtonSpec.SidePage, GlowType.button),

    /** A button on a card (Start, Try again, Connect): 44 tall, 14 semibold. */
    Card(ButtonSpec.HeightCard, ButtonSpec.SideCard, GlowType.buttonSmall),

    /** A small pill beside content (Save in a row, Trim, Undo): 36 tall, 13 semibold. */
    Small(ButtonSpec.HeightSmall, ButtonSpec.SideSmall, GlowType.buttonMini),
}

/** The lighter red a small destructive pill's label takes (HaloSummaryRun and HaloPastWorkout boards, #FF7A7E). */
private val DestructiveInkSmall = MetricColors.heartLight

/**
 * The app's button: a pill [size] tall, a [label] in that size's type, an [icon] before it when given. [variant] says what
 * it is for. [enabled] false fades the whole button, so it never reads half on. Give it a width through [modifier]
 * (`fillMaxWidth`, or `weight(1f)` in a row); alone it is as wide as its label.
 */
@Composable
internal fun HaloButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: HaloButtonVariant = HaloButtonVariant.Secondary,
    size: HaloButtonSize = HaloButtonSize.Page,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    if (variant == HaloButtonVariant.Text) {
        Box(
            modifier.heightIn(min = ButtonSpec.TextMinHeight).alpha(if (enabled) 1f else ButtonSpec.DISABLED).clip(Corner.Pill)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = ButtonSpec.TextSide),
            contentAlignment = Alignment.Center,
        ) { Text(label, color = Ink.Secondary, style = GlowType.body, maxLines = 1) }
        return
    }
    val ink = when (variant) {
        HaloButtonVariant.Primary -> Color.Black
        HaloButtonVariant.Destructive ->
            if (size == HaloButtonSize.Card || size == HaloButtonSize.Small) DestructiveInkSmall else DestructiveInk
        else -> Color.White
    }
    Row(
        // Dimmed before the pill is drawn, so a disabled button fades whole, pill and label (user, 2026-11-03).
        modifier.height(size.height).alpha(if (enabled) 1f else ButtonSpec.DISABLED).clip(Corner.Pill)
            .let {
                when (variant) {
                    HaloButtonVariant.Primary -> it.background(Color.White)
                    HaloButtonVariant.Glass -> it.background(Ink.Faint).border(1.dp, Ink.Edge, Corner.Pill)
                    else -> it.background(Greys.Control)
                }
            }
            .pressClickable(onClick, Corner.Pill, enabled = enabled)
            .semantics { role = Role.Button }
            .padding(horizontal = size.side),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(ButtonSpec.Glyph), tint = ink)
            Spacer(Modifier.width(ButtonSpec.IconGap))
        }
        Text(label, color = ink, style = size.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** What an icon button is on: a page, the main tap, a picture or lit card, or a detail's wash. */
internal enum class HaloIconVariant(val fill: Color, val ink: Color) {
    /** [Greys.Control] fill, white glyph: a header's back, a stepper, a close. */
    Control(Greys.Control, Color.White),

    /** White fill, black glyph: Pause, or a map button that is on. */
    Primary(Color.White, Color.Black),

    /** Translucent near black over a picture or a map ([Greys.OverMap]). */
    Glass(Greys.OverMap, Color.White),

    /** Translucent black over a detail's coloured wash ([Ink.Scrim]). */
    OverWash(Ink.Scrim, Color.White),
}

/** An icon button's size across and its glyph: 32 and 16, 44 and 20 (the default), 60 and 22 (live), 88 and 28 (pause). */
internal enum class HaloIconSize(val size: Dp, val glyph: Dp) {
    Small(ButtonSpec.IconSmall, ButtonSpec.GlyphSmall),
    Default(ButtonSpec.IconDefault, ButtonSpec.GlyphDefault),
    Live(ButtonSpec.IconLive, ButtonSpec.GlyphLive),
    Pause(ButtonSpec.IconPause, ButtonSpec.GlyphPause),
}

/**
 * The circle of an icon button with nothing inside it yet: [size] across on [variant]'s fill, [content] centred. The
 * button builds on it, and so does the live screen's hold-to-end, which has its own press handling and ring.
 */
@Composable
internal fun HaloIconSurface(
    modifier: Modifier = Modifier,
    variant: HaloIconVariant = HaloIconVariant.Control,
    size: HaloIconSize = HaloIconSize.Default,
    content: @Composable () -> Unit,
) {
    // A flat circle, no glass and no rim: only the tab bar is glass (16 Nov 2026).
    Box(modifier.size(size.size).clip(Corner.Pill).background(variant.fill), contentAlignment = Alignment.Center) { content() }
}

/**
 * A round button holding only an [icon] ([size] across, a circle always). [contentDescription] is read out, [clickLabel]
 * says what a tap does when that is different, and [dot] marks the top right with a [NoticeDot]. Back, (i), close,
 * the header's avatar, a stepper's minus and plus, the live screen's side buttons and Pause are all this button. [tint]
 * overrides the glyph's colour (a quiet (i)).
 */
@Composable
internal fun HaloIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: HaloIconVariant = HaloIconVariant.Control,
    size: HaloIconSize = HaloIconSize.Default,
    dot: Boolean = false,
    enabled: Boolean = true,
    clickLabel: String = contentDescription,
    tint: Color = variant.ink,
) {
    Box(modifier.size(size.size)) {
        HaloIconSurface(
            Modifier.matchParentSize().alpha(if (enabled) 1f else ButtonSpec.DISABLED)
                .pressClickable(onClick, Corner.Pill, enabled = enabled, onClickLabel = clickLabel, role = Role.Button),
            variant, size,
        ) { Icon(icon, contentDescription, Modifier.size(size.glyph), tint = tint) }
        // On the circle's edge at its top right, ringed in the page's black so it reads apart from the button.
        if (dot) NoticeDot(Modifier.align(Alignment.TopEnd), ring = Color.Black)
    }
}

/**
 * Two buttons side by side, half and half, [ButtonSpec.PairGap] apart: Cancel and Save, Share and Done, Save and Not a
 * workout. The right one is the pair's one white tap unless [rightVariant] says otherwise (a confirm that deletes).
 */
@Composable
internal fun ButtonPair(
    left: String,
    onLeft: () -> Unit,
    right: String,
    onRight: () -> Unit,
    modifier: Modifier = Modifier,
    size: HaloButtonSize = HaloButtonSize.Page,
    leftVariant: HaloButtonVariant = HaloButtonVariant.Secondary,
    rightVariant: HaloButtonVariant = HaloButtonVariant.Primary,
    leftIcon: ImageVector? = null,
    rightIcon: ImageVector? = null,
    leftEnabled: Boolean = true,
    rightEnabled: Boolean = true,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonSpec.PairGap)) {
        HaloButton(left, onLeft, Modifier.weight(1f), leftVariant, size, leftIcon, leftEnabled)
        HaloButton(right, onRight, Modifier.weight(1f), rightVariant, size, rightIcon, rightEnabled)
    }
}
