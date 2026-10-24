package org.openstrap.app.ui.glow

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.openstrap.app.ui.components.pressClickable
import org.openstrap.app.ui.theme.AppIcons

/*
 * The settings pages (the Strap page and what it opens, Profile, You) in the glow language's quiet tier: black, with
 * grouped lists in place of Material panels, a round back button, and plain buttons. One look for every page, so the
 * app doesn't turn into another product at the strap's boundary.
 */

private val PanelShape = GlowLayout.CardShape
private val PanelSurface = Color(0xFF0F0F11)
private val ButtonShape = RoundedCornerShape(20.dp)
private val ButtonSurface = Color(0xFF121214)
private val Hairline = Color.White.copy(alpha = 0.05f)

/**
 * The one row or button that can't be undone (Forget, Delete all, Delete workout): plain white at 72%, no colour, since
 * colour in the app means a metric or the strap's state (spec 2.2). The confirmation asks before anything goes.
 */
val DestructiveInk = Color.White.copy(alpha = 0.72f)

/** The Control surface (spec 2.2): round buttons, chips, pills. */
val ControlSurface = Color(0xFF1F1F22)

/** The round back button of every page over a tab (spec 3.4). */
val BackButtonSize = 44.dp

/** The page header's height: the back button with room round it. */
val PageHeaderHeight = 64.dp

/**
 * A page's way back (spec 3.4): a [BackButtonSize] round button on the [ControlSurface] with a 20 dp arrow, dimmed
 * and still while not [enabled] (a firmware install running).
 */
@Composable
fun BackButton(onBack: () -> Unit, enabled: Boolean = true) {
    Box(
        Modifier.size(BackButtonSize).clip(CircleShape).background(ControlSurface).alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, onClickLabel = "Back", role = Role.Button, onClick = onBack),
        contentAlignment = Alignment.Center,
    ) { Icon(AppIcons.ArrowLeft, "Back", Modifier.size(20.dp), tint = Color.White) }
}

/**
 * The one page header (spec 3.4), on every page over a tab, details included: the [BackButton] and the [title] in
 * Page title type, over the day it's about or a short note ([subtitle]) in Secondary when there is one, then optional
 * [trailing] content (Save, a detail's (i)). [onTitle] makes the title a button with a caret (a detail's switcher).
 */
@Composable
fun PageHeader(
    title: String,
    onBack: () -> Unit,
    backEnabled: Boolean = true,
    subtitle: String? = null,
    onTitle: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth().height(PageHeaderHeight), verticalAlignment = Alignment.CenterVertically) {
        BackButton(onBack, backEnabled)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(
                Modifier.clip(RoundedCornerShape(8.dp))
                    .let { if (onTitle != null) it.clickable(onClickLabel = "Switch", role = Role.Button, onClick = onTitle) else it },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, color = Color.White, style = GlowType.pageTitle, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                if (onTitle != null) {
                    Spacer(Modifier.width(6.dp))
                    Icon(AppIcons.CaretDown, null, tint = Color.White.copy(alpha = GlowType.CAPTION_ALPHA), modifier = Modifier.size(16.dp))
                }
            }
            if (subtitle != null) Text(subtitle, color = Color.White.copy(alpha = GlowType.SECONDARY_ALPHA), style = GlowType.secondary, maxLines = 1)
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/**
 * A grouped panel for free content (text, fields, buttons): the [ListPanel] surface with even padding, an optional
 * [title] row and [note] on its right.
 */
@Composable
fun PagePanel(modifier: Modifier = Modifier, title: String? = null, note: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().background(PanelSurface, PanelShape).border(1.dp, Color.White.copy(alpha = 0.06f), PanelShape)
            .padding(GlowLayout.CardPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (title != null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White, style = GlowType.cardTitle, modifier = Modifier.weight(1f))
            if (note != null) Text(note, color = Color.White.copy(alpha = GlowType.SECONDARY_ALPHA), style = GlowType.secondary)
        }
        content()
    }
}

/** A grouped list of [PageRow]s, without a title row (the rows carry the names). */
@Composable
fun PageRows(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().background(PanelSurface, PanelShape).border(1.dp, Color.White.copy(alpha = 0.06f), PanelShape)
            .padding(GlowLayout.ListPadding),
        content = content,
    )
}

/**
 * A settings row: the [title], and its current [value] either on the right ([below] false, the Strap page) or as a
 * second line ([below] true, the You page). [trailing] replaces the value on the right (a switch). [divider] draws
 * the hairline above it. [icon] sits quietly on the left, centred on the row, with the title and value lined up after it.
 * A row that opens something ([onClick], no [trailing]) ends in the tap caret (spec 2.5).
 */
@Composable
fun PageRow(
    title: String,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    below: Boolean = false,
    divider: Boolean = true,
    titleColor: Color = Color.White,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth()
            .let { if (onClick != null) it.clickable(enabled = enabled, onClick = onClick) else it }
            .let { if (divider) it.drawBehind { drawLine(Hairline, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) } else it }
            .alpha(if (enabled) 1f else 0.45f)
            .padding(vertical = GlowLayout.RowPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(Modifier.width(20.dp), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(20.dp), tint = Color.White.copy(alpha = 0.7f))
            }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = titleColor, style = GlowType.body)
            if (below && value != null) Text(value, color = Color.White.copy(alpha = 0.6f), style = GlowType.secondary)
        }
        if (!below && value != null && trailing == null) {
            Spacer(Modifier.width(12.dp))
            // A long value wraps onto a second line before it ever makes the title wrap.
            Text(
                value, Modifier.widthIn(max = 160.dp), color = Color.White.copy(alpha = 0.6f),
                style = GlowType.secondary, textAlign = TextAlign.End,
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        } else if (onClick != null) {
            Spacer(Modifier.width(RowCaretGap))
            TapCaret(ROW_CARET_ALPHA)
        }
    }
}

/** A row with a switch: the [title], an optional [subtitle] under it, and the switch on the right. */
@Composable
fun PageSwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit, divider: Boolean = true, enabled: Boolean = true) {
    Row(
        Modifier.fillMaxWidth()
            .let { if (divider) it.drawBehind { drawLine(Hairline, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) } else it }
            .padding(vertical = GlowLayout.RowPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = Color.White, style = GlowType.body)
            if (subtitle != null) Text(subtitle, color = Color.White.copy(alpha = 0.6f), style = GlowType.secondary)
        }
        Spacer(Modifier.width(12.dp))
        GlowSwitch(checked, onChange, enabled = enabled)
    }
}

/** A plain button: dark with a hairline, or white when it's the page's main action ([primary]). */
@Composable
fun PageButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
    color: Color = if (primary) Color.Black else Color.White,
    icon: ImageVector? = null,
) {
    Row(
        modifier.height(52.dp).clip(ButtonShape)
            .background(if (primary) Color.White else ButtonSurface)
            .let { if (primary) it else it.border(1.dp, Color.White.copy(alpha = 0.08f), ButtonShape) }
            .alpha(if (enabled) 1f else 0.4f)
            .let { if (enabled) it.pressClickable(onClick, ButtonShape) else it }
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp), tint = color)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = color, style = GlowType.body, maxLines = 1)
    }
}

/** A secondary line of explanation on a page. */
@Composable
fun PageNote(text: String, color: Color = Color.White.copy(alpha = 0.6f)) {
    Text(text, color = color, style = GlowType.secondary)
}

/** A short choice between a few values, as pills; the chosen one is soft white. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoicePills(options: List<T>, selected: T?, label: (T) -> String, onPick: (T) -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { v ->
            val on = v == selected
            Box(
                Modifier.clip(CircleShape)
                    .background(if (on) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.05f))
                    .border(1.dp, if (on) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f), CircleShape)
                    .alpha(if (enabled) 1f else 0.4f)
                    .clickable(enabled = enabled, role = Role.RadioButton) { if (!on) onPick(v) }
                    .semantics { this.selected = on }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) { Text(label(v), color = Color.White, style = GlowType.secondary) }
        }
    }
}
