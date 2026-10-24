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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import org.openstrap.app.ui.components.GlassSheet
import org.openstrap.app.ui.components.pressClickable
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.app.ui.theme.AppIcons

/*
 * The settings pages (the Strap page and what it opens, Profile, You) in the glow language's quiet tier: black, with
 * grouped lists in place of Material panels, a round back button, and plain buttons. One look for every page, so the
 * app doesn't turn into another product at the strap's boundary.
 */

private val PanelShape = GlowLayout.CardShape
private val PanelSurface = Color(0xFF0F0F11)
/** The Control surface (spec 2.2): buttons, chips, pills and text fields on a page. */
internal val ControlSurface = Color(0xFF1F1F22)
private val Hairline = Color.White.copy(alpha = 0.05f)
/** A [PageRow] whose page is open beside the list. */
private val SelectedTint = Color.White.copy(alpha = 0.08f)

/**
 * The one row or button that can't be undone (Forget, Delete all, Delete workout): plain white at 72%, no colour, since
 * colour in the app means a metric or the strap's state (spec 2.2). The confirmation asks before anything goes.
 */
val DestructiveInk = Color.White.copy(alpha = 0.72f)

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
            Text(title, color = Color.White, style = GlowType.cardTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (note != null) Text(note, color = Color.White.copy(alpha = GlowType.SECONDARY_ALPHA), style = GlowType.secondary)
        }
        content()
    }
}

/** A grouped list of [PageRow]s, without a title row (the rows carry the names). */
@Composable
fun PageRows(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        // Clipped, so a selected row's tint keeps to the panel's corners.
        modifier.fillMaxWidth().clip(PanelShape).background(PanelSurface, PanelShape).border(1.dp, Color.White.copy(alpha = 0.06f), PanelShape)
            .padding(GlowLayout.ListPadding),
        content = content,
    )
}

/**
 * A settings row: the [title], and its current [value] either on the right ([below] false, the Strap page) or as a
 * second line ([below] true, the You page). [trailing] replaces the value on the right (a switch). [divider] draws
 * the hairline above it. [icon] sits quietly on the left, centred on the row, with the title and value lined up after it.
 * A row that opens something ([onClick], no [trailing]) ends in the tap caret (spec 2.5). [selected] marks the row
 * whose page is open beside the list on a wide screen: tinted across the panel's width.
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
    selected: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth()
            .let {
                if (!selected) it else it.semantics { this.selected = true }.drawBehind {
                    val inset = GlowLayout.CardInset.toPx()
                    drawRect(SelectedTint, Offset(-inset, 0f), Size(size.width + 2 * inset, size.height))
                }
            }
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

/**
 * A page's button, fully round (spec 2.4): white with black text when it's the page's main action ([primary]),
 * otherwise on the Control surface with white text. Body type, a 20 dp [icon] before the text when given.
 */
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
        modifier.height(52.dp).clip(CircleShape)
            .background(if (primary) Color.White else ControlSurface)
            .alpha(if (enabled) 1f else 0.4f)
            .let { if (enabled) it.pressClickable(onClick, CircleShape) else it }
            .semantics { role = Role.Button }
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp), tint = color)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = color, style = GlowType.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * A quiet action that is only its words, in Body at 72% (Delete under Save, Reset): no surface, so the page's buttons
 * stay the loudest thing. At least 44 dp tall to tap.
 */
@Composable
fun PageTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Color.White.copy(alpha = GlowType.SECONDARY_ALPHA),
) {
    Box(
        modifier.heightIn(min = 44.dp).clip(CircleShape)
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = color, style = GlowType.body, maxLines = 1) }
}

/**
 * A text field in the tile style (spec 2.4): the Control surface with 16 dp corners, the [label] over it in
 * Secondary, the text in Body, an optional [unit] after it. [error] draws an amber hairline round it.
 */
@Composable
fun PageField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String?,
    modifier: Modifier = Modifier,
    unit: String? = null,
    placeholder: String? = null,
    error: Boolean = false,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(modifier.alpha(if (enabled) 1f else 0.45f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) Text(label, color = Color.White.copy(alpha = GlowType.SECONDARY_ALPHA), style = GlowType.secondary)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(GlowLayout.TileShape).background(ControlSurface)
                .let { if (error) it.border(1.dp, MetricColors.warn, GlowLayout.TileShape) else it }
                .padding(start = 16.dp, end = if (trailing != null) 4.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f).padding(vertical = 14.dp)) {
                if (value.isEmpty() && placeholder != null) {
                    Text(placeholder, color = Color.White.copy(alpha = 0.3f), style = GlowType.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                BasicTextField(
                    value, onValueChange, Modifier.fillMaxWidth(), enabled = enabled,
                    textStyle = GlowType.body.copy(color = Color.White), singleLine = true,
                    keyboardOptions = keyboardOptions, visualTransformation = visualTransformation,
                    cursorBrush = SolidColor(Color.White),
                )
            }
            if (unit != null) {
                Spacer(Modifier.width(8.dp))
                Text(unit, color = Color.White.copy(alpha = GlowType.SECONDARY_ALPHA), style = GlowType.secondary, maxLines = 1)
            }
            if (trailing != null) {
                Spacer(Modifier.width(4.dp))
                trailing()
            }
        }
    }
}

/**
 * A thin progress bar: a 4 dp track at 10% white, filled white to [fraction]. With no [fraction] (how long isn't
 * known), a third of the track slides across.
 */
@Composable
fun PageProgress(fraction: Float? = null, modifier: Modifier = Modifier.fillMaxWidth()) {
    val slide = if (fraction == null) {
        rememberInfiniteTransition(label = "page progress")
            .animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "page progress slide")
    } else null
    Canvas(modifier.height(4.dp).clip(CircleShape)) {
        val r = CornerRadius(size.height / 2f)
        drawRoundRect(Color.White.copy(alpha = 0.1f), cornerRadius = r)
        if (fraction != null) {
            drawRoundRect(Color.White, size = Size(size.width * fraction.coerceIn(0f, 1f), size.height), cornerRadius = r)
        } else if (slide != null) {
            val w = size.width / 3f
            drawRoundRect(Color.White, topLeft = Offset(-w + (size.width + w) * slide.value, 0f), size = Size(w, size.height), cornerRadius = r)
        }
    }
}

/**
 * A sheet over the page (spec 2.5, sheets): the glass sheet with its handle, the [title] as a card title, an
 * optional [note] under it, then [content], 20 dp from the edges. It scrolls when taller than most of the screen.
 */
@Composable
fun PageSheet(onDismiss: () -> Unit, title: String, note: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.88f).dp
    GlassSheet(onDismiss) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = maxHeight).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).navigationBarsPadding().padding(top = 4.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = Color.White, style = GlowType.cardTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (note != null) Text(note, color = Color.White.copy(alpha = GlowType.SECONDARY_ALPHA), style = GlowType.secondary)
            }
            content()
        }
    }
}

/**
 * A question before something that can't be taken back (Forget, Disconnect, Delete all): a [PageSheet] with the
 * [text], any extra [content], then Cancel and the [confirm] button, the white one, side by side.
 */
@Composable
fun PageConfirm(
    title: String,
    text: String?,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismiss: String = "Cancel",
    confirmEnabled: Boolean = true,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    PageSheet(onDismiss, title) {
        if (text != null) Text(text, color = Color.White.copy(alpha = GlowType.SECONDARY_ALPHA), style = GlowType.body)
        content?.invoke(this)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PageButton(dismiss, onDismiss, Modifier.weight(1f))
            PageButton(confirm, onConfirm, Modifier.weight(1f), enabled = confirmEnabled, primary = true)
        }
    }
}

/**
 * A chip (spec 2.4, fully round): a hairline and 72% text, or white with black text when [selected]. Secondary type,
 * at least 36 dp tall.
 */
@Composable
fun PageChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, role: Role = Role.Checkbox) {
    Box(
        modifier.heightIn(min = 36.dp).clip(CircleShape)
            .background(if (selected) Color.White else Color.Transparent)
            .let { if (selected) it else it.border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape) }
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, role = role, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, color = if (selected) Color.Black else Color.White.copy(alpha = GlowType.SECONDARY_ALPHA),
            style = GlowType.secondary, maxLines = 1,
        )
    }
}

/** A secondary line of explanation on a page. */
@Composable
fun PageNote(text: String, color: Color = Color.White.copy(alpha = 0.6f)) {
    Text(text, color = color, style = GlowType.secondary)
}

/** A short choice between a few values, as [PageChip]s; the chosen one is white. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoicePills(options: List<T>, selected: T?, label: (T) -> String, onPick: (T) -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { v ->
            val on = v == selected
            PageChip(label(v), on, { if (!on) onPick(v) }, enabled = enabled, role = Role.RadioButton)
        }
    }
}
