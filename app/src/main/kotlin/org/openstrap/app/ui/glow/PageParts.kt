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
import androidx.compose.ui.unit.sp
import org.openstrap.app.ui.components.pressClickable
import org.openstrap.app.ui.theme.PhosphorIcons

/*
 * The settings pages (the Strap page and what it opens, Profile, You) in the glow language's quiet tier: black, with
 * grouped lists in place of Material panels, a round back button, and plain buttons. One look for every page, so the
 * app doesn't turn into another product at the strap's boundary.
 */

private val PanelShape = RoundedCornerShape(28.dp)
private val PanelSurface = Color(0xFF0F0F11)
private val ButtonShape = RoundedCornerShape(20.dp)
private val ButtonSurface = Color(0xFF121214)
private val Hairline = Color.White.copy(alpha = 0.05f)

/** Red for the one row or button that can't be undone (Forget, Delete all). */
val DangerColor = Color(0xFFFF8A8D)

/**
 * A page's top: a round back button and the [title], over the day it's about ([subtitle]) when it's about one, with
 * optional [trailing] content (Save).
 */
@Composable
fun PageHeader(
    title: String,
    onBack: () -> Unit,
    backEnabled: Boolean = true,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth().height(60.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(Color(0xFF1C1C1E)).alpha(if (backEnabled) 1f else 0.4f)
                .clickable(enabled = backEnabled, onClickLabel = "Back", onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { Icon(PhosphorIcons.ArrowLeft, "Back", Modifier.size(18.dp), tint = Color.White) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, style = GlowType.label.copy(fontSize = 20.sp, lineHeight = 26.sp), maxLines = 1)
            if (subtitle != null) Text(subtitle, color = Color.White.copy(alpha = 0.6f), style = GlowType.secondary, maxLines = 1)
        }
        trailing?.invoke()
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
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (title != null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White, style = GlowType.label, modifier = Modifier.weight(1f))
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
            .padding(horizontal = 18.dp, vertical = 4.dp),
        content = content,
    )
}

/**
 * A settings row: the [title], and its current [value] either on the right ([below] false, the Strap page) or as a
 * second line ([below] true, the You page). [trailing] replaces the value on the right (a switch). [divider] draws
 * the hairline above it. [icon] sits quietly on the left, centred on the row, with the title and value lined up after it.
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
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(Modifier.width(20.dp), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(20.dp), tint = Color.White.copy(alpha = 0.7f))
            }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = titleColor, style = GlowType.label.copy(fontWeight = null))
            if (below && value != null) Text(value, color = Color.White.copy(alpha = 0.6f), style = GlowType.secondary)
        }
        if (!below && value != null && trailing == null) {
            Spacer(Modifier.width(12.dp))
            // A long value wraps onto a second line before it ever makes the title wrap.
            Text(
                value, Modifier.widthIn(max = 160.dp), color = Color.White.copy(alpha = 0.6f),
                style = GlowType.secondary.copy(fontSize = 13.5.sp), textAlign = TextAlign.End,
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/** A row with a switch: the [title], an optional [subtitle] under it, and the switch on the right. */
@Composable
fun PageSwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit, divider: Boolean = true, enabled: Boolean = true) {
    Row(
        Modifier.fillMaxWidth()
            .let { if (divider) it.drawBehind { drawLine(Hairline, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) } else it }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = Color.White, style = GlowType.label.copy(fontWeight = null))
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
        Text(text, color = color, style = GlowType.label.copy(fontWeight = null), maxLines = 1)
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
            ) { Text(label(v), color = Color.White, style = GlowType.secondary.copy(fontSize = 13.5.sp)) }
        }
    }
}
