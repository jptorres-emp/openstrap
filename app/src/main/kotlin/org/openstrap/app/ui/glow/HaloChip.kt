package org.openstrap.app.ui.glow

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.getValue
import org.openstrap.app.ui.components.Motion
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.openstrap.app.R
import org.openstrap.app.ui.components.pressClickable
import org.openstrap.app.ui.dashboard.NewsTag
import org.openstrap.app.ui.theme.AppIcons
import org.openstrap.app.ui.theme.Corner
import org.openstrap.app.ui.theme.IconSize
import org.openstrap.app.ui.theme.Ink
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.app.ui.theme.OnSurface
import org.openstrap.app.ui.theme.Space

/*
 * The small labels (docs/design/halo/components-audit.md, 3.9, 3.10, 3.22 and 3.24): a tappable label is a [HaloChip],
 * a few words about the data are a [HaloTag], one of a few is [GlowSegments]. Every measurement is in [ChipSpec],
 * [TagSpec] and [SegmentSpec]; every picked state is [OnSurface.picked] with the one ring, [ChipSpec.Ring].
 */

/** The chip's and the day cell's measurements (the HaloComponents board). */
internal object ChipSpec {
    /** A chip's height, and a chip's that is the main control of its place (a day of an alarm, a sport to start). */
    val Height = 36.dp
    val HeightMain = IconSize.TileM

    /** The padding at each side of the label, and at the start when an icon or a dot leads. */
    val Side = 14.dp
    val SideLead = Space.S12

    /** Between a lead, the label and a caret. */
    val Gap = Space.S8

    /** A leading icon, and a leading status dot. */
    val Glyph = 18.dp
    val Dot = Space.S8

    /** The one ring of a picked state, everywhere: a chip, a day, a tile, a segment (section 5 row 20). */
    val Ring = 1.5.dp

    /** An outline chip's hairline. */
    val Outline = 1.dp

    /** A day cell in a detail's week strip, and in the calendar (section 5 row 18). */
    val DayStrip = 62.dp
    val DayCalendar = 54.dp

    val Label: TextStyle = GlowType.chip

    /** A disabled chip fades to this. */
    const val DISABLED = 0.4f
}

/** What a chip looks like. */
internal enum class ChipVariant {
    /** A hairline and grey words: a quiet label beside content. */
    Outline,

    /** The control fill ([OnSurface.control]) and white words: a sport to start, the strap's state, an earned best. */
    Filled,

    /** One of a set: the control fill and grey words, or picked, [OnSurface.picked] with the white [ChipSpec.Ring]. */
    Choice,
}

/** A chip's height. */
internal enum class ChipSize(val height: Dp) {
    Regular(ChipSpec.Height),

    /** The main control of its place. */
    Main(ChipSpec.HeightMain),
}

/**
 * The chip: a pill [size] high with its [label] in 14, an optional [icon] in [tint] or status [dot] before it and a
 * [caret] after it when tapping opens a list. A [ChipVariant.Choice] says [selected] to a screen reader and takes
 * [role] (a checkbox by default); the others are buttons. Without [onClick] it is only a label. [description] is read
 * in place of a label too short to say it (a day's letter); [clickLabel] says what a tap does. A long label ends in an
 * ellipsis and leaves the caret its room.
 */
// One chip for three looks and an optional lead, label and caret: each branch is one slot or one look.
@Suppress("CyclomaticComplexMethod")
@Composable
internal fun HaloChip(
    label: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    variant: ChipVariant = ChipVariant.Filled,
    size: ChipSize = ChipSize.Regular,
    selected: Boolean = false,
    icon: ImageVector? = null,
    tint: Color = Color.White,
    dot: Color? = null,
    caret: Boolean = false,
    enabled: Boolean = true,
    role: Role = if (variant == ChipVariant.Choice) Role.Checkbox else Role.Button,
    description: String? = null,
    clickLabel: String? = null,
) {
    val picked = variant == ChipVariant.Choice && selected
    val fill = when {
        picked -> OnSurface.picked
        variant == ChipVariant.Outline -> Color.Transparent
        else -> OnSurface.control
    }
    val ink = if (picked || variant == ChipVariant.Filled) Color.White else Ink.Secondary
    val lead = icon != null || dot != null
    Row(
        modifier.let { if (onClick != null) it.minimumInteractiveComponentSize() else it }
            .height(size.height).alpha(if (enabled) 1f else ChipSpec.DISABLED).clip(Corner.Pill).background(fill)
            .let {
                when {
                    picked -> it.border(ChipSpec.Ring, Color.White, Corner.Pill)
                    variant == ChipVariant.Outline -> it.border(ChipSpec.Outline, Ink.Line, Corner.Pill)
                    else -> it
                }
            }
            .let {
                if (onClick == null) it
                else it.pressClickable(onClick, Corner.Pill, enabled = enabled, onClickLabel = clickLabel, role = role)
            }
            .semantics {
                if (variant == ChipVariant.Choice) this.selected = selected
                if (description != null) contentDescription = description
            }
            .padding(start = if (lead) ChipSpec.SideLead else ChipSpec.Side, end = ChipSpec.Side),
        horizontalArrangement = Arrangement.spacedBy(ChipSpec.Gap, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(ChipSpec.Glyph), tint = tint)
        if (dot != null) Box(Modifier.size(ChipSpec.Dot).background(dot, Corner.Pill))
        if (label.isNotEmpty()) {
            // Weighted only beside a caret, so a squeezed label gives way to it; a weight in a scrolling row would be zero wide.
            Text(
                label,
                (if (caret) Modifier.weight(1f, fill = false) else Modifier)
                    .let { if (description != null) it.clearAndSetSemantics {} else it },
                color = ink, style = ChipSpec.Label, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (caret) Icon(AppIcons.CaretDown, null, Modifier.size(IconSize.Caret), tint = ink)
    }
}

/** The tag's measurements (the HaloComponents board). */
internal object TagSpec {
    val Height = 22.dp
    val Side = Space.S8
    val Label: TextStyle = GlowType.tag

    /** An outline tag's hairline. */
    val Outline = 1.dp

    /** A filled or status tag's fill: its colour at this share. A news tag (white) keeps [Ink.Edge]. */
    const val FILL = 0.16f

    /** A status tag's and a text tag's words: the colour lifted this far toward white. */
    const val LIFT = 0.55f

    /** Beside a number: after it on its line, or under it when the line can't hold both. */
    val Gap = Space.S8
    val Drop = Space.S12
}

/** What a tag looks like. */
internal enum class TagVariant {
    /** A pill in [HaloTag]'s colour at 16% with white words: news about you ("New best"). */
    Filled,

    /** A hairline and grey words: about the data, beside a title ("Auto-detected"). */
    Outline,

    /** The words alone, the colour lifted toward white: a list row's off tag ("Above normal"). */
    Text,

    /** Amber words alone: about the sync, not the body ("Up to 09:40"). */
    Marker,

    /** A pill on the control grey with silver words: where a workout was done, beside its sport ("Indoor"). */
    Quiet,

    /** A pill in the colour at 16% with its words lifted toward white: what happened ("Earned 3 Nov"). */
    Status,
}

/**
 * The tag: a few words about what it sits beside, 22 high, 11 semibold. [color] is the data's colour for a filled, text
 * or status tag. With [onClick] (an outline tag) tapping it explains it.
 */
@Suppress("CyclomaticComplexMethod") // one branch per TagVariant, kept together so each look reads in one place
@Composable
internal fun HaloTag(
    text: String,
    modifier: Modifier = Modifier,
    variant: TagVariant = TagVariant.Outline,
    color: Color = Color.White,
    onClick: (() -> Unit)? = null,
) {
    val ink = when (variant) {
        TagVariant.Filled -> Color.White
        TagVariant.Outline -> Ink.Secondary
        TagVariant.Quiet -> Ink.Silver
        TagVariant.Text, TagVariant.Status -> lerp(color, Color.White, TagSpec.LIFT)
        TagVariant.Marker -> MetricColors.warn
    }
    val boxed = variant == TagVariant.Filled || variant == TagVariant.Outline || variant == TagVariant.Quiet || variant == TagVariant.Status
    val about = if (onClick != null) stringResource(R.string.glow_tag_about, text.lowercase()) else null
    val quietFill = OnSurface.control
    Box(
        modifier
            .let { if (boxed) it.heightIn(min = TagSpec.Height).clip(Corner.Pill) else it }
            .let {
                when (variant) {
                    TagVariant.Outline -> it.border(TagSpec.Outline, Ink.Line, Corner.Pill)
                    TagVariant.Filled -> it.background(if (color == Color.White) Ink.Edge else color.copy(alpha = TagSpec.FILL))
                    TagVariant.Status -> it.background(color.copy(alpha = TagSpec.FILL))
                    TagVariant.Quiet -> it.background(quietFill)
                    else -> it
                }
            }
            .let { if (onClick != null) it.clickable(onClickLabel = about, onClick = onClick) else it }
            .let { if (boxed) it.padding(horizontal = TagSpec.Side) else it },
        contentAlignment = Alignment.Center,
    ) { Text(text, color = ink, style = TagSpec.Label, maxLines = 1) }
}

/** A news tag: [HaloTag] filled, in white. */
@Composable
internal fun NewsTagView(tag: NewsTag, modifier: Modifier = Modifier) = HaloTag(tag.text, modifier, TagVariant.Filled)

/**
 * A card's number with its news tag (spec 2.5, Tags): [number], then the tag [TagSpec.Gap] after it, dropping
 * [TagSpec.Drop] under the number when the line can't hold both, as on a Small. The drop is wider than the side gap
 * because dotted digits read as a block and crowd a pill tucked right under them.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun NumberWithTag(tag: NewsTag?, modifier: Modifier = Modifier, number: @Composable () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(TagSpec.Gap),
        verticalArrangement = Arrangement.spacedBy(TagSpec.Drop),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        number()
        if (tag != null) NewsTagView(tag)
    }
}

/** The segmented control's measurements. */
internal object SegmentSpec {
    /** Between the track's edge and the segments. */
    val Pad = Space.S4

    /** The press scale of a segment. */
    const val PRESS = 0.97f
}

/** A segmented control's height and its label: a sheet's or a page's 44, a detail's range 36 (section 5 row 13). */
internal enum class SegmentSize(val height: Dp, val label: TextStyle) {
    Regular(IconSize.TileM, GlowType.body),
    Compact(ChipSpec.Height, GlowType.chip),
}

/**
 * A row of choices on a track ([OnSurface.track]), the chosen one on a white pill with a black label: Female or Male,
 * Day to Year, a route on or off, how you feel. [selected] -1 leaves none chosen. [role] is what each choice is to a
 * screen reader (a tab for the ranges that switch a detail's chart).
 */
// Every caller builds its labels in place, so a stable list type wouldn't let this skip any more often.
@Suppress("UnstableCollections", "ComposeUnstableCollections")
@Composable
internal fun GlowSegments(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    role: Role = Role.RadioButton,
    size: SegmentSize = SegmentSize.Regular,
) {
    // The track is drawn at [size]; the control's touch area is at least 48 high.
    Box(
        modifier.minimumInteractiveComponentSize().fillMaxWidth().height(size.height).clip(Corner.Pill)
            .background(OnSurface.track).padding(SegmentSpec.Pad),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val segment = maxWidth / options.size.coerceAtLeast(1)
            // The chosen pill slides to the new segment (P6) instead of snapping.
            val x by animateDpAsState(segment * selected.coerceAtLeast(0), tween(Motion.SWIFT, easing = Motion.easing), label = "segment")
            if (selected >= 0) Box(Modifier.offset(x).width(segment).fillMaxHeight().clip(Corner.Pill).background(Color.White))
            Row(Modifier.fillMaxSize()) {
                options.forEachIndexed { i, label ->
                    val on = i == selected
                    val ink by animateColorAsState(
                        if (on) Color.Black else Ink.Secondary, tween(Motion.QUICK, easing = Motion.easing), label = "segmentInk",
                    )
                    Box(
                        Modifier.weight(1f).fillMaxHeight().clip(Corner.Pill)
                            .pressClickable({ onSelect(i) }, Corner.Pill, scale = SegmentSpec.PRESS)
                            .semantics { this.role = role; this.selected = on },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, color = ink, style = size.label, maxLines = 1)
                    }
                }
            }
        }
    }
}
