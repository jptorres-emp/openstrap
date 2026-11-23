package org.openstrap.app.ui.glow

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.openstrap.app.R
import org.openstrap.app.ui.components.cardNumber
import org.openstrap.app.ui.theme.Corner
import org.openstrap.app.ui.theme.Greys
import org.openstrap.app.ui.theme.IconSize
import org.openstrap.app.ui.theme.Ink

/*
 * The named rows: [HaloRow] with a slot combination each (docs/design/halo/components-audit.md, 3.5). None has a layout of its own.
 */


/**
 * A settings row (the You, Strap, Profile boards): the [title], and its current [value] either on the right (a value
 * row, or an info row when nothing opens) or as a line under it ([below] true, or an [icon] tile with a way on). A row
 * that opens something ([onClick], no [trailing]) ends in the chevron. [selected] tints the row whose page is open beside
 * the list. [dot] puts a [NoticeDot] after the title of a stacked row: something new waits behind it. [trailing]
 * replaces the value and the chevron (a switch). Slots: lead tile, title, line or value, chevron.
 */
@Composable
fun NavRow(
    title: String,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    below: Boolean = false,
    divider: Boolean = true,
    titleColor: Color = Color.White,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    selected: Boolean = false,
    dot: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    // Read only when nothing opens and nothing trails: an info row, grey and without a chevron.
    val info = onClick == null && trailing == null
    // A nav row (an icon tile, a way on, a value) keeps its value under the name (HaloYou board).
    val stacked = below || (icon != null && value != null && onClick != null && trailing == null)
    HaloRow(
        title,
        lead = icon?.let { RowLead.tile(it) },
        titleInk = titleColor,
        titleSuffix = if (dot && stacked) ({ NoticeDot() }) else null,
        line = if (stacked && value != null) ({ RowLine(value) }) else null,
        value = value.takeIf { !stacked && trailing == null },
        valueInk = if (info) GlowType.noteInk else Ink.Strong,
        trail = trailing ?: if (onClick != null) ({ RowTrail.Chevron() }) else null,
        onClick = onClick, divider = divider, enabled = enabled, selected = selected,
    )
}

/** An info row, read only: the [name] on the left, the [value] on the right in grey, no chevron. */
@Composable
@NonRestartableComposable
fun InfoRow(name: String, value: String, divider: Boolean = true) = NavRow(name, value, divider = divider)

/**
 * A row with a switch: the [title], an optional [subtitle] under it, and the switch on the right. A tap anywhere on the
 * row flips it, so a long name is as easy to hit as the switch. [icon] sits on the left as on a [NavRow].
 */
@Composable
fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    divider: Boolean = true,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) = HaloRow(
    title, modifier,
    lead = icon?.let { RowLead.tile(it) },
    line = subtitle?.let { { RowLine(it) } },
    trail = { RowTrail.Toggle(checked, onChange, enabled = enabled) },
    tapModifier = Modifier.toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
    divider = divider,
)

/**
 * An action row, one that does something now: the [icon] and the [label] in white, or both in red when [destructive],
 * and no chevron. While [busy] a spinner takes the icon's place and the row doesn't take taps.
 */
@Composable
fun ActionRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    destructive: Boolean = false,
    busy: Boolean = false,
    enabled: Boolean = true,
    divider: Boolean = true,
) {
    val ink = if (destructive) DestructiveInk else Color.White
    HaloRow(
        label, modifier,
        lead = if (busy) RowLead.custom(IconSize.GlyphM) { RowSpinner(ink) } else icon?.let { RowLead.glyph(it, ink) },
        titleInk = ink,
        onClick = onClick.takeIf { enabled && !busy }, role = Role.Button,
        divider = divider, enabled = enabled,
    )
}

/**
 * An item row, a thing you own: the [name] over its [details], and one action pill on the right ([action], 36 tall), or
 * a [trailing] switch instead.
 */
@Composable
fun ItemRow(
    name: String,
    details: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    divider: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) = HaloRow(
    name, modifier,
    line = { RowLine(details) },
    trail = trailing ?: if (action != null && onAction != null) ({ RowTrail.Action(action, onAction) }) else null,
    onClick = onClick, divider = divider,
)

/**
 * A setting picked from a short list: the [title] and the chosen value on the right, then a down caret. A tap opens a
 * menu at the row's end with every option and a tick on the chosen one; picking one calls [onPick] and closes it.
 * [note] is a line under the row (shown only when something needs saying).
 */
@Suppress("UnstableCollections", "ComposeUnstableCollections")
@Composable
fun <T> PageMenuRow(
    title: String,
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onPick: (T) -> Unit,
    modifier: Modifier = Modifier,
    divider: Boolean = true,
    enabled: Boolean = true,
    note: String? = null,
    quiet: (T) -> Boolean = { false },
) {
    var open by remember { mutableStateOf(false) }
    val changeLabel = stringResource(R.string.glow_page_change)
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth()) {
            HaloRow(
                title, value = selected?.let(label),
                valueInk = if (selected?.let(quiet) == true) Ink.Tertiary else Ink.Strong,
                trail = { RowTrail.Down() },
                onClick = { open = true }, role = Role.DropdownList, clickLabel = changeLabel,
                divider = divider, enabled = enabled,
            )
            Box(Modifier.align(Alignment.BottomEnd).rowSides()) {
                DropdownMenu(
                    expanded = open, onDismissRequest = { open = false },
                    offset = DpOffset(0.dp, 4.dp), shape = Corner.Inner, containerColor = Greys.Control,
                    tonalElevation = 0.dp, shadowElevation = 12.dp, border = BorderStroke(1.dp, Ink.Edge),
                ) {
                    options.forEach { o ->
                        val on = o == selected
                        DropdownMenuItem(
                            text = { Text(label(o), style = GlowType.body, color = if (on) Color.White else Ink.Strong) },
                            onClick = { open = false; if (!on) onPick(o) },
                            trailingIcon = if (on) ({ RowTrail.Check() }) else null,
                            modifier = Modifier.widthIn(min = 180.dp).semantics { this.selected = on },
                        )
                    }
                }
            }
        }
        if (note != null) Text(
            note, color = GlowType.noteInk, style = GlowType.secondary,
            modifier = Modifier.rowSides().padding(bottom = RowSpec.Pad),
        )
    }
}

/**
 * A metric as a row of a list or a detail's related list: the metric's [icon] on a tile in its ink [color] (or in
 * [iconTint]), or a ring showing a goal's share ([ring]); the [name] with its [status] under it, or the off [tag] in the
 * status's place; the [value] on the right and the chevron when [onClick] opens something. A null [value] is a waiting
 * row: a dim dash, the glyph at 30%, and [status] says what it waits for. [dash] false leaves the value column empty
 * for a row whose [status] is the answer. Slots: lead tile or ring, title, line or tag, value, chevron.
 */
@Composable
fun MetricRow(
    name: String,
    color: Color,
    value: String?,
    status: String?,
    modifier: Modifier = Modifier,
    tag: org.openstrap.app.ui.dashboard.NewsTag? = null,
    onClick: (() -> Unit)? = null,
    divider: Boolean = true,
    dash: Boolean = true,
    icon: ImageVector? = null,
    iconTint: Color = color,
    ring: Float? = null,
) {
    val waiting = value == null && dash
    HaloRow(
        name, modifier,
        lead = when {
            ring != null -> RowLead.ring(ring, color)
            icon != null -> RowLead.tile(icon, iconTint, dim = waiting)
            else -> null
        },
        line = when {
            // Off your normal, the tag says it in place of the status, in the row's colour.
            tag != null && !waiting -> ({ HaloTag(tag.text, variant = TagVariant.Text, color = iconTint) })
            status != null -> ({ RowLine(status, if (waiting) Ink.Tertiary else GlowType.noteInk) })
            else -> null
        },
        value = value, valueInk = Color.White, valueModifier = Modifier.cardNumber(value), dash = dash,
        trail = if (onClick != null) ({ RowTrail.Chevron() }) else null,
        onClick = onClick, divider = divider, grows = true,
    )
}
