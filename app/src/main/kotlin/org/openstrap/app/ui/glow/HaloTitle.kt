package org.openstrap.app.ui.glow

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import org.openstrap.app.ui.components.cardOpening
import org.openstrap.app.ui.theme.Ink
import org.openstrap.app.ui.theme.Space

/*
 * The one title (docs/design/halo/components-audit.md, 3.4): a card's title row, a heading over a card, a settings
 * group's label. Text on the left with its mark, the note on the right, dropping under the text when they can't share a
 * line, and what trails it (an (i), an action, the chevron circle of a card that opens) at the end.
 */

/** The title's measurements. */
internal object TitleSpec {
    /** A card's title, 15, and a hero's, 16. */
    val Text: TextStyle = GlowType.cardTitle
    val Hero: TextStyle = GlowType.heroTitle

    /** The note, 13, and a group's label, 13 grey. */
    val Note: TextStyle = GlowType.secondary
    val Group: TextStyle = GlowType.secondary

    /** Between the text and its note on one line, and from the text to a note dropped under it. */
    val NoteGap = Space.S12
    val Drop = Space.S2

    /** Between the text and its mark, the note and what trails it. */
    val Gap = Space.S8

    /** How far a title over a card sits in from the card's edge. */
    val OutSide = Space.S4

    /** Every title is one line, ellipsised, so a card's height never depends on the font size or the words. */
    const val CARD_LINES = 1
}

/** Where a title sits. */
internal enum class TitleVariant {
    /** In a card: white, 15 (16 on a hero, [CardTone.Lit]). */
    InCard,

    /** A heading above a card: white 15, 4 in. */
    OutOfCard,

    /** A settings group's label above its card: grey 13, 4 in. */
    Group,
}

/**
 * The one title. [text] with an optional sync [marker] or neutral [tag] beside it ([TitleMark]), a [note] on the right
 * (a way on with [onNote], with a caret), and [trailing] after it. In a card that opens something ([LocalCardOpens]),
 * the chevron circle ends the row.
 */
@Composable
internal fun HaloTitle(
    text: String,
    modifier: Modifier = Modifier,
    variant: TitleVariant = TitleVariant.InCard,
    note: String? = null,
    onNote: (() -> Unit)? = null,
    marker: String? = null,
    tag: TitleTag? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val inCard = variant == TitleVariant.InCard
    val style = when {
        variant == TitleVariant.Group -> TitleSpec.Group
        inCard && LocalCardHero.current -> TitleSpec.Hero
        else -> TitleSpec.Text
    }
    val color = if (variant == TitleVariant.Group) GlowType.noteInk else Color.White
    val lines = if (inCard) TitleSpec.CARD_LINES else 1
    val chevron = inCard && LocalCardOpens.current && LocalTitleChevron.current
    val marked = marker != null || tag != null
    val start: @Composable () -> Unit = {
        val title: @Composable () -> Unit = {
            Text(text, color = color, style = style, maxLines = lines, overflow = TextOverflow.Ellipsis)
        }
        // The mark goes beside the text, or under it on a narrow card, so it never squeezes the text.
        if (marked) FlowRow(
            horizontalArrangement = Arrangement.spacedBy(TitleSpec.Gap), verticalArrangement = Arrangement.spacedBy(Space.S4),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            title()
            TitleMark(marker, tag)
        } else title()
    }
    val noteEnd: (@Composable () -> Unit)? = note?.let { n -> { TitleNote(n, onNote) } }
    val sides = if (inCard) modifier else modifier.padding(horizontal = TitleSpec.OutSide)
    // A title inside places the chevron itself; a title further in (a row's) leaves it out.
    CompositionLocalProvider(LocalTitleChevron provides false) {
        if (trailing == null && !chevron) TitleLine(start, noteEnd, bottom = !inCard, modifier = sides)
        // What trails the title stays on its first line at the right; only the note drops under the text.
        else Row(
            sides.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TitleSpec.Gap),
        ) {
            TitleLine(start, noteEnd, bottom = !inCard, modifier = Modifier.weight(1f))
            trailing?.invoke()
            if (chevron) ChevronCircle()
        }
    }
}

/** A title's note: a way on ("All 9") with its caret when [onNote] is given, else plain Secondary. */
@Composable
private fun TitleNote(note: String, onNote: (() -> Unit)?) {
    if (onNote == null) return Text(note, color = GlowType.noteInk, style = TitleSpec.Note, maxLines = 1)
    Row(
        // Tapped, the note grows into what it opens (spec 3.5).
        cardOpening(onNote).let { o -> Modifier.then(o?.modifier ?: Modifier).clickable(onClick = o?.onClick ?: onNote) }
            .padding(vertical = Space.S4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(note, color = Color.White, style = TitleSpec.Note, maxLines = 1)
        Spacer(Modifier.width(TitleSpec.Gap))
        TapCaret(Ink.Tertiary)
    }
}

/**
 * [start] on the left and [end] on the right when both fit on one line; when they don't, [end] drops under [start]
 * rather than cut it. [bottom] lines them
 * up on their foot (a heading), else on their middle (a card's title).
 */
// One layout or the other, by whether there is an end; the same title keeps to one of them.
@Suppress("ContentSlotReused", "ComposeContentSlotReused")
@Composable
private fun TitleLine(
    start: @Composable () -> Unit,
    end: (@Composable () -> Unit)?,
    bottom: Boolean,
    modifier: Modifier = Modifier,
) {
    if (end == null) {
        Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { start() }
        return
    }
    Layout(content = { start(); end() }, modifier = modifier.fillMaxWidth()) { (s, e), cs ->
        val gap = TitleSpec.NoteGap.roundToPx()
        val note = e.measure(Constraints(maxWidth = cs.maxWidth))
        val wanted = s.maxIntrinsicWidth(cs.maxHeight) + gap + note.width
        // Asked how wide it would like to be (an intrinsic measure), it's the one line.
        val w = if (cs.hasBoundedWidth) cs.maxWidth else wanted
        // The note sits beside the title when both fit on the line at the font size in use, else under it, so a title
        // is never cut to make room for its note: "Your body this morning" stays whole at 1.3 (release check 2026-11-22).
        if (wanted <= w) {
            val title = s.measure(Constraints(maxWidth = w - gap - note.width))
            val h = maxOf(title.height, note.height)
            layout(w, h) {
                title.place(0, if (bottom) h - title.height else (h - title.height) / 2)
                note.place(w - note.width, if (bottom) h - note.height else (h - note.height) / 2)
            }
        } else {
            val title = s.measure(Constraints(maxWidth = w))
            val under = TitleSpec.Drop.roundToPx()
            layout(w, title.height + under + note.height) {
                title.place(0, 0)
                note.place(0, title.height + under)
            }
        }
    }
}
