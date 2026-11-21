package org.openstrap.app.ui.glow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.openstrap.app.ui.theme.Corner
import org.openstrap.app.ui.theme.IconSize
import org.openstrap.app.ui.theme.Ink
import org.openstrap.app.ui.theme.Space

/*
 * The note (docs/design/halo/components-audit.md, 3.6): a line of explanation in the flow of a page, a footnote under a
 * group, or a caveat with its dot. Every measurement is in [NoteSpec].
 */

/** The note's measurements. */
internal object NoteSpec {
    /** An inline note: Secondary 13 in the boards' grey, no inset. */
    val Inline: TextStyle = GlowType.secondary
    val InlineInk = GlowType.noteColor

    /** A footnote: 12 on 17.4 in [Ink.Tertiary], 4 in from the sides, 8 under its group. */
    val Footnote: TextStyle = GlowType.footnote
    val FootnoteInk = GlowType.footnoteColor
    val FootnoteSide = Space.S4
    val FootnoteGap = Space.S8

    /** A caveat: a 6 dot, 8 before its words in 13 [Ink.Strong], 12 under what it qualifies. */
    val CaveatDot = 6.dp
    val CaveatGap = Space.S8
    val CaveatTop = Space.S12
    val CaveatInk = Ink.Strong

    /** An inline note's leading glyph (the pencil of an edited night), and the gap after it. */
    val Glyph = IconSize.GlyphS
    val GlyphGap = Space.S8
}

/** What kind of note. */
internal enum class NoteVariant(val style: TextStyle, val ink: Color) {
    /** In the flow of a page or a sheet: what a choice does, why something is off. */
    Inline(NoteSpec.Inline, NoteSpec.InlineInk),

    /** The small print under a group's panel. */
    Footnote(NoteSpec.Footnote, NoteSpec.FootnoteInk),

    /** A caveat about a number, led by a dot in its colour: "Learning, 9 of 14 nights". */
    Caveat(NoteSpec.Inline, NoteSpec.CaveatInk),
}

/**
 * A note in its [variant]'s type and grey (a lit card's [LitInk]), or in [color] (a warning in amber). A
 * [NoteVariant.Caveat] leads with a dot in [dot]; an [icon] leads an inline note in its ink. [textAlign] Center centres a note across its column, under a centred value.
 */
@Composable
internal fun HaloNote(
    text: String,
    modifier: Modifier = Modifier,
    variant: NoteVariant = NoteVariant.Inline,
    color: Color? = null,
    dot: Color? = null,
    icon: ImageVector? = null,
    textAlign: TextAlign? = null,
) {
    val ink = color ?: when (variant) {
        NoteVariant.Inline -> GlowType.noteInk
        NoteVariant.Footnote -> GlowType.footnoteInk
        NoteVariant.Caveat -> LocalLitInk.current ?: variant.ink
    }
    when {
        variant == NoteVariant.Caveat -> Row(
            modifier.padding(top = NoteSpec.CaveatTop), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NoteSpec.CaveatGap),
        ) {
            Box(Modifier.size(NoteSpec.CaveatDot).background(dot ?: ink, Corner.Pill))
            Text(text, color = ink, style = variant.style)
        }
        icon != null -> Row(
            modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NoteSpec.GlyphGap),
        ) {
            Icon(icon, null, Modifier.size(NoteSpec.Glyph), tint = ink)
            Text(text, color = ink, style = variant.style)
        }
        else -> Text(
            text,
            when {
                variant == NoteVariant.Footnote -> modifier.padding(horizontal = NoteSpec.FootnoteSide)
                // A centred note spans its column, so it centres under a centred value.
                textAlign == TextAlign.Center -> modifier.fillMaxWidth()
                else -> modifier
            },
            color = ink, style = variant.style, textAlign = textAlign,
        )
    }
}
