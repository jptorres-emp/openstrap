package org.openstrap.app.ui.glow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import org.openstrap.app.ui.theme.Corner
import org.openstrap.app.ui.theme.Ink
import org.openstrap.app.ui.theme.Space

/* More named rows: a contributor bar, a zone, a radio choice, a fact. Each is [HaloRow] with its slots. */

/**
 * A contributor bar: the [name], a dim [detail] under it (the reading behind it), then a bar filled to [fraction]
 * (0..1) with the [word] after it, or the off [tag] in the word's place. Bar and word take [color] only when
 * [offNormal]; otherwise they stay grey. [onClick] opens what the row is part of, with the chevron. Slots: title, line,
 * below the bar, chevron.
 */
@Composable
internal fun ContributorRow(
    name: String, color: Color, word: String, fraction: Float, modifier: Modifier = Modifier, offNormal: Boolean = false,
    divider: Boolean = true, detail: String? = null, onClick: (() -> Unit)? = null, tag: org.openstrap.app.ui.dashboard.NewsTag? = null,
) {
    val accent = if (offNormal) color else Ink.Secondary
    HaloRow(
        name, modifier,
        line = detail?.let { { RowLine(it, Ink.Tertiary) } },
        below = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(Space.S8).background(Ink.Faint, Corner.Pill)) {
                    Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(Space.S8).background(accent, Corner.Pill))
                }
                Spacer(Modifier.width(RowSpec.Gap))
                if (tag != null) NewsTagView(tag)
                else Text(word, color = if (offNormal) color else Ink.Secondary, style = GlowType.secondary, maxLines = 1)
            }
        },
        trail = if (onClick != null) ({ RowTrail.Chevron() }) else null,
        onClick = onClick, divider = divider, grows = true,
    )
}

/** A zone on the list (board HaloHeartZones): its colour as a pip, "Zone 2, Aerobic", its heart rates and a chevron. */
@Composable
internal fun ZoneRow(
    title: String, color: Color, bpm: String, onClick: () -> Unit, modifier: Modifier = Modifier, divider: Boolean = true,
) = HaloRow(
    title, modifier, lead = RowLead.pip(color), value = bpm, trail = { RowTrail.Chevron() }, onClick = onClick, divider = divider,
)

/** A radio row: the [name] and a ring that fills white when [picked]. The row is the radio button to a screen reader. */
@Composable
internal fun ChoiceRow(
    name: String, picked: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, divider: Boolean = true,
) = HaloRow(
    name, modifier, titleLines = 2,
    trail = { RowTrail.Radio(picked) },
    onClick = onClick, role = Role.RadioButton,
    tapModifier = Modifier.semantics { this.selected = picked; this.role = Role.RadioButton },
    divider = divider,
)

/**
 * A fact of a card (a name and its value): a compact row, 32 tall with no padding, the name grey and the value white.
 */
@Composable
internal fun FactRow(name: String, value: String, modifier: Modifier = Modifier) = HaloRow(
    name, modifier, titleInk = GlowType.noteColor, value = value, valueInk = Color.White, compact = true, divider = false,
)
