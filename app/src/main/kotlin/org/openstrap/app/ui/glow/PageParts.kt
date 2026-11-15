package org.openstrap.app.ui.glow

import androidx.compose.ui.res.stringResource
import org.openstrap.app.R
import org.openstrap.app.presentation.TimeOfDay
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import org.openstrap.app.ui.components.GlassSheet
import org.openstrap.app.ui.components.pressClickable
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.app.ui.theme.AppIcons
import java.time.ZoneId
import org.openstrap.app.ui.theme.ControlSurface

/*
 * The settings pages (the Strap page and what it opens, Profile, You) in the glow language's quiet tier: black, with
 * grouped lists in place of Material panels, a round back button, and plain buttons. One look for every page, so the
 * app doesn't turn into another product at the strap's boundary.
 */

private val PanelShape = GlowLayout.CardShape
/** A settings group's panel (HaloYou board: #0C0D10), and the 1 px line between its rows (#1C1D22). */
private val PanelSurface = Color(0xFF0C0D10)
private val Hairline = Color(0xFF1C1D22)
/** A [PageRow] whose page is open beside the list. */
private val SelectedTint = Color.White.copy(alpha = 0.08f)

/** The dark fill of every secondary button (HaloSummaryRun and HaloQuietHoursSheet boards, #17181C). */
internal val HaloButtonSurface = Color(0xFF17181C)

/** The red of a destructive button's text on its dark pill (HaloDeleteSleepSheet board, #FF5A5F). */
val DestructiveInk = Color(0xFFFF5A5F)

/** The lighter red of a small destructive pill (HaloSummaryRun and HaloPastWorkout boards, #FF7A7E). */
private val DestructiveInkSmall = Color(0xFFFF7A7E)

/** The corner of a header icon button (HaloSummaryRun board: 44 across with a 16 corner), kept in step with the icon's size. */


/** The round back button of every page over a tab (spec 3.4). */
val BackButtonSize = 44.dp

/** The page header's height (HaloProfile and HaloDetailRecovery boards: a 48 dp row, 96 / 1fr / 96 columns). */
val PageHeaderHeight = 48.dp

/** The header's two side columns, equal so the title stays centred whatever sits in them (HaloProfile board). */
private val PageHeaderSide = 96.dp

/**
 * A page's way back (spec 3.4): a [BackButtonSize] round button on the [ControlSurface] with a 20 dp arrow, dimmed
 * and still while not [enabled] (a firmware install running).
 */
@Composable
fun BackButton(onBack: () -> Unit, enabled: Boolean = true) =
    RoundIconButton(
        AppIcons.ArrowLeft, stringResource(R.string.glow_back), onBack,
        size = BackButtonSize, surface = HaloButtonSurface, iconSize = 18.dp,
        enabled = enabled,
    )

/** How much of a [RoundIconButton] its icon takes by default. */
private const val ROUND_ICON_SHARE = 0.47f

/**
 * A button holding just an [icon] (HaloSummaryRun board: a rounded square with a 16 corner at 44, #17181C): [size] across on [surface], the icon [iconSize]
 * across in [tint]. [label] is read out, [clickLabel] says what a tap does when that's different, and [dot] marks the
 * top right with a [NoticeDot]. Back, (i), close and the header's avatar are all this button.
 */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    surface: Color = HaloButtonSurface,
    iconSize: Dp = size * ROUND_ICON_SHARE,
    tint: Color = Color.White,
    clickLabel: String = label,
    enabled: Boolean = true,
    dot: Boolean = false,
) {
    Box(modifier.size(size)) {
        Box(
            Modifier.matchParentSize()
                .clip(GlowLayout.IconShape)
                .background(surface).alpha(if (enabled) 1f else 0.4f)
                .clickable(enabled = enabled, onClickLabel = clickLabel, role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, label, Modifier.size(iconSize), tint = tint) }
        // On the circle's edge at its top right, ringed in the page's black so it reads apart from the button.
        if (dot) NoticeDot(Modifier.align(Alignment.TopEnd), ring = Color.Black)
    }
}

/**
 * The one page header (HaloProfile, HaloAlarms and HaloDetailRecovery boards): a 96 / 1fr / 96 grid with the back
 * button at the left, the [title] centred in Page title type (over the day it's about or a short note, [subtitle],
 * in Secondary), and [trailing] content at the right (a detail's (i), or the Save pill). The Save pill is the page's
 * to show: pass it as [trailing] only while the page has unsaved edits, and the column keeps its width without it.
 * [onTitle] makes the title a button with a caret (a detail's switcher). [buttonSurface] is the back button's fill:
 * the dark #17181C, or translucent black over a detail's wash.
 */
@Composable
fun PageHeader(
    title: String,
    onBack: () -> Unit,
    backEnabled: Boolean = true,
    subtitle: String? = null,
    onTitle: (() -> Unit)? = null,
    buttonSurface: Color = HaloButtonSurface,
    trailing: (@Composable () -> Unit)? = null,
) {
    val switchLabel = stringResource(R.string.glow_page_switch)
    Row(Modifier.fillMaxWidth().height(PageHeaderHeight), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(PageHeaderSide), contentAlignment = Alignment.CenterStart) {
            RoundIconButton(
                AppIcons.ArrowLeft, stringResource(R.string.glow_back), onBack,
                size = BackButtonSize, surface = buttonSurface, iconSize = 18.dp, enabled = backEnabled,
            )
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                Modifier.clip(RoundedCornerShape(8.dp))
                    .let { if (onTitle != null) it.clickable(onClickLabel = switchLabel, role = Role.Button, onClick = onTitle) else it },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, color = Color.White, style = GlowType.pageTitle, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                if (onTitle != null) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        AppIcons.CaretDown, null, modifier = Modifier.size(12.dp),
                        tint = Color.White.copy(alpha = GlowType.SECONDARY_ALPHA),
                    )
                }
            }
            if (subtitle != null) Text(subtitle, color = Color.White.copy(alpha = GlowType.SECONDARY_ALPHA), style = GlowType.secondary, maxLines = 1)
        }
        Box(Modifier.width(PageHeaderSide), contentAlignment = Alignment.CenterEnd) { trailing?.invoke() }
    }
}

/**
 * A grouped panel for free content (text, fields, buttons): a [QuietPanel] with an optional [title] row and [note]
 * on its right ([GlowHeader]).
 */
@Composable
fun PagePanel(modifier: Modifier = Modifier, title: String? = null, note: String? = null, content: @Composable ColumnScope.() -> Unit) =
    QuietPanel(modifier) {
        if (title != null) GlowHeader(title, note)
        content()
    }

/** A grouped list of [PageRow]s, without a title row (the rows carry the names). */
@Composable
fun PageRows(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        // Clipped, so a selected row's tint keeps to the panel's corners.
        // No rim (HaloSystem board), and the rows carry their own sides, so a divider runs edge to edge (HaloYou board).
        modifier.fillMaxWidth().clip(PanelShape).background(PanelSurface, PanelShape),
        content = { CompositionLocalProvider(LocalRowInset provides PanelRowInset) { content() } },
    )
}

/** How far a settings row's content sits in from its panel's edge (HaloYou board: 16, or 12 before an icon tile). */
internal val LocalRowInset = androidx.compose.runtime.staticCompositionLocalOf { 0.dp }
private val PanelRowInset = 16.dp
/** A row's least height inside a settings panel (HaloYou board: 56). */
private val PanelRowHeight = 56.dp

/**
 * A settings row (HaloYou, HaloProfile and HaloRewardsSettingsRules boards): the [title], and its current [value] either
 * on the right ([below] false: a value row, or an info row when nothing opens) or as a second line ([below] true: a nav
 * row). The title stays on one line (spec 2.3): where it and a value on the right don't both fit on one line, the value
 * drops under it ([RowNameValue]). [trailing] replaces the value on the right (a switch). [divider] draws the 1 px line
 * above it. [icon] sits on a neutral 32 dp tile on the left. A row that opens something ([onClick], no [trailing]) ends in
 * the small chevron. [selected] marks the row whose page is open beside the list on a wide screen: tinted across the
 * panel's width. [dot] puts a [NoticeDot] after the title of a [below] row: something new waits behind it.
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
    dot: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val inPanel = LocalRowInset.current > 0.dp
    // Read only when nothing opens and nothing trails: an info row, grey and without a chevron.
    val info = onClick == null && trailing == null
    // A nav row (an icon tile, a way on, a value) keeps its value under the name (HaloYou board).
    val stacked = below || (icon != null && value != null && onClick != null && trailing == null)
    Row(
        Modifier.fillMaxWidth()
            .selectedTint(selected, inPanel)
            .let { if (onClick != null) it.clickable(enabled = enabled, onClick = onClick) else it }
            .topHairline(divider, Hairline)
            .alpha(if (enabled) 1f else 0.45f)
            .rowFrame(hasIcon = icon != null),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) RowIcon(icon)
        if (stacked) Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(RowLineGap)) {
            if (dot) Row(verticalAlignment = Alignment.CenterVertically) {
                RowName(title, titleColor)
                Spacer(Modifier.width(8.dp))
                NoticeDot()
            } else RowName(title, titleColor)
            if (value != null) Text(value, color = RowValueColor, style = GlowType.secondary)
        } else RowNameValue(
            title, titleColor, value.takeIf { trailing == null }, Modifier.weight(1f),
            valueColor = if (info) RowValueColor else RowPickedValueColor, valueStyle = GlowType.rowValue,
        )
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        } else if (onClick != null) {
            Spacer(Modifier.width(RowCaretGap))
            RowChevron()
        }
    }
}

/** The tint of the [selected] row whose page is open beside the list, across the panel (or the card's padding too, outside a panel). */
private fun Modifier.selectedTint(selected: Boolean, inPanel: Boolean): Modifier =
    if (!selected) this else semantics { this.selected = true }.drawBehind {
        val bleed = if (inPanel) 0f else GlowLayout.CardInset.toPx()
        drawRect(SelectedTint, Offset(-bleed, 0f), Size(size.width + 2 * bleed, size.height))
    }

/** A settings row's [icon] on a neutral tile (HaloYou board: 32 across, #17181C, the icon 17 in #C9CED6), then the gap to its title. */
@Composable
private fun RowIcon(icon: ImageVector) {
    Box(
        Modifier.padding(end = 12.dp).size(IconTileSize).clip(GlowLayout.IconShape).background(HaloButtonSurface),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(17.dp), tint = IconTileInk) }
}

/** A settings icon tile (HaloYou board). */
private val IconTileSize = 32.dp
private val IconTileInk = Color(0xFFC9CED6)

/** The small chevron at the end of a row that opens something (HaloYou board: 14, #6E7480). */
@Composable
internal fun RowChevron() = Icon(AppIcons.TapCaret, null, tint = ChevronInk, modifier = Modifier.size(CaretSize))

private val ChevronInk = Color(0xFF6E7480)

/**
 * Something new waits behind what it sits on (a new version, on the avatar and the Updates row): a small white dot,
 * the size of the strap chip's, with a [ring] of the background around it where it overlaps a button's edge.
 */
@Composable
fun NoticeDot(modifier: Modifier = Modifier, ring: Color? = null) {
    if (ring == null) Box(modifier.size(7.dp).background(Color.White, CircleShape))
    else Box(modifier.size(11.dp).background(ring, CircleShape).padding(2.dp).background(Color.White, CircleShape))
}

/** A row's note, an info row's value and a second line: #9AA0AA (HaloRewardsSettingsRules board). */
private val RowValueColor = GlowType.noteColor

/** A value row's value, which opens a picker: white at 80% (HaloProfile board). */
private val RowPickedValueColor = Color.White.copy(alpha = GlowType.ROW_VALUE_ALPHA)

/** A row's name in Body, on one line (spec 2.3): ellipsized only when it alone is wider than the row. */
@Composable
internal fun RowName(name: String, color: Color = Color.White) =
    Text(name, color = color, style = GlowType.body, maxLines = 1, overflow = TextOverflow.Ellipsis)

/** Between a row's name and the value on its right (HaloProfile board: 10). */
private val RowValueGap = 10.dp
/** Between a row's name and the value dropped under it (HaloYou board: 2). */
private val RowLineGap = 2.dp

/**
 * A settings row's frame (HaloYou board): at least 56 tall with 10 above and below, 16 in from the panel's edge (12 before
 * an icon tile) and 14 at its end. Outside a settings panel (a row on a card or in a sheet) the sides are the container's.
 */
@Composable
@ReadOnlyComposable
private fun Modifier.rowFrame(hasIcon: Boolean): Modifier {
    val inset = LocalRowInset.current
    val inPanel = inset > 0.dp
    return heightIn(min = if (inPanel) PanelRowHeight else 0.dp).padding(
        start = if (!inPanel) 0.dp else if (hasIcon) 12.dp else inset,
        end = if (inPanel) 14.dp else 0.dp,
        top = if (inPanel) 10.dp else GlowLayout.RowPadding, bottom = if (inPanel) 10.dp else GlowLayout.RowPadding,
    )
}

/**
 * Whether a row's value sits on the right of its name ([nameWidth] and [valueWidth] each on one line) in a row
 * [available] wide with [gap] between them. When not, it drops under the name, so the name never wraps.
 */
internal fun valueBeside(nameWidth: Int, valueWidth: Int, available: Int, gap: Int): Boolean = nameWidth + gap + valueWidth <= available

/**
 * A row's name with its [value] on the right, on the name's baseline, when both fit on one line each
 * ([valueBeside]), otherwise the value under the name in Secondary, up to two lines and then ellipsized. The name
 * keeps to one line either way.
 */
@Composable
internal fun RowNameValue(
    name: String,
    nameColor: Color,
    value: String?,
    modifier: Modifier = Modifier,
    valueColor: Color = RowValueColor,
    valueStyle: TextStyle = GlowType.secondary,
) {
    Layout(
        content = {
            RowName(name, nameColor)
            if (value != null) Text(value, color = valueColor, style = valueStyle, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        modifier = modifier,
    ) { measurables, cs ->
        val width = if (cs.hasBoundedWidth) cs.maxWidth else measurables.sumOf { it.maxIntrinsicWidth(Constraints.Infinity) }
        val loose = Constraints(maxWidth = width)
        val nameP = measurables[0].measure(loose)
        val v = measurables.getOrNull(1)
        if (v == null) return@Layout layout(width, nameP.height) { nameP.place(0, 0) }
        val gap = RowValueGap.roundToPx()
        if (valueBeside(measurables[0].maxIntrinsicWidth(Constraints.Infinity), v.maxIntrinsicWidth(Constraints.Infinity), width, gap)) {
            val valueP = v.measure(Constraints(maxWidth = (width - nameP.width - gap).coerceAtLeast(0)))
            // The value on the name's baseline, at the row's end.
            val shift = nameP[FirstBaseline] - valueP[FirstBaseline]
            val top = minOf(0, shift)
            val height = maxOf(nameP.height, valueP.height + shift) - top
            layout(width, height) {
                nameP.place(0, -top)
                valueP.place(width - valueP.width, shift - top)
            }
        } else {
            val valueP = v.measure(loose)
            val line = RowLineGap.roundToPx()
            layout(width, nameP.height + line + valueP.height) {
                nameP.place(0, 0)
                valueP.place(0, nameP.height + line)
            }
        }
    }
}

/**
 * A setting picked from a short list: the [title] and the chosen value on the right, then a down caret. A tap opens a
 * menu at the row's end with every option and a tick on the chosen one; picking one calls [onPick] and closes it.
 * [note] is a line under the row (shown only when something needs saying). Takes the place of a row of chips.
 */
@Composable
fun <T> PageMenuRow(
    title: String,
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onPick: (T) -> Unit,
    divider: Boolean = true,
    enabled: Boolean = true,
    note: String? = null,
    quiet: (T) -> Boolean = { false },
) {
    var open by remember { mutableStateOf(false) }
    val changeLabel = stringResource(R.string.glow_page_change)
    Column(
        Modifier.fillMaxWidth()
            .topHairline(divider, Hairline),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth()
                    .clickable(enabled = enabled, onClickLabel = changeLabel, role = Role.DropdownList) { open = true }
                    .alpha(if (enabled) 1f else 0.45f)
                    .rowFrame(hasIcon = false),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val value = selected?.let(label)
                Text(title, color = Color.White, style = GlowType.body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (value != null) {
                    Spacer(Modifier.width(RowValueGap))
                    Text(
                        value, style = GlowType.secondary, maxLines = 1,
                        color = if (selected.let(quiet)) Color.White.copy(alpha = 0.4f) else RowPickedValueColor,
                    )
                }
                Spacer(Modifier.width(RowCaretGap))
                Icon(AppIcons.CaretDown, null, tint = ChevronInk, modifier = Modifier.size(CaretSize))
            }
            Box(Modifier.align(Alignment.BottomEnd)) {
                DropdownMenu(
                    expanded = open, onDismissRequest = { open = false },
                    offset = DpOffset(0.dp, 4.dp), shape = MenuShape, containerColor = MenuSurface,
                    tonalElevation = 0.dp, shadowElevation = 12.dp, border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                ) {
                    options.forEach { o ->
                        val on = o == selected
                        DropdownMenuItem(
                            text = { Text(label(o), style = GlowType.body, color = if (on) Color.White else RowPickedValueColor) },
                            onClick = { open = false; if (!on) onPick(o) },
                            trailingIcon = if (on) ({ Icon(AppIcons.Tick02, null, tint = Color.White, modifier = Modifier.size(18.dp)) }) else null,
                            modifier = Modifier.widthIn(min = 180.dp).semantics { this.selected = on },
                        )
                    }
                }
            }
        }
        if (note != null) Text(
            note, color = RowValueColor, style = GlowType.secondary,
            modifier = Modifier.padding(start = LocalRowInset.current, end = 14.dp, bottom = GlowLayout.RowPadding),
        )
    }
}

private val MenuShape = RoundedCornerShape(18.dp)
private val MenuSurface = Color(0xFF232326)

/**
 * A row with a switch (HaloBackup board): the [title], an optional [subtitle] under it, and the switch on the right. A tap anywhere on
 * the row flips it, so a long name is as easy to hit as the switch. [icon] sits on the left as on a [PageRow].
 */
@Composable
fun PageSwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    divider: Boolean = true,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Row(
        Modifier.fillMaxWidth()
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .topHairline(divider, Hairline)
            .rowFrame(hasIcon = icon != null),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) RowIcon(icon)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(RowLineGap)) {
            RowName(title)
            if (subtitle != null) Text(subtitle, color = RowValueColor, style = GlowType.secondary)
        }
        Spacer(Modifier.width(12.dp))
        // The row is the switch to a screen reader, so the knob isn't read a second time.
        GlowSwitch(checked, onChange, Modifier.clearAndSetSemantics {}, enabled = enabled)
    }
}

/** How big a [PageButton] is: its height, side padding, icon and type. */
enum class ButtonSize(val height: Dp, val padding: Dp, val icon: Dp) {
    /** The one white tap at the foot of a page (HaloReadyRun and HaloPastWorkout boards: 64 tall, 17 semibold). */
    Main(64.dp, 22.dp, 18.dp),

    /** A page's own buttons and a sheet's pair (HaloQuietHoursSheet board: 56 tall, 16 semibold). */
    Page(56.dp, 18.dp, 20.dp),

    /** A small pill beside content, like Delete under a summary (HaloSummaryRun board: 44 tall, 14 regular). */
    Compact(44.dp, 18.dp, 16.dp),

    /** On a hero card, like Start or Try again. */
    Hero(48.dp, 18.dp, 16.dp),

    /** Inside a row, like Save beside a found workout. */
    Row(32.dp, 14.dp, 14.dp),
}

/**
 * A page's button, fully round (Halo boards): white with black text when it's the page's main action ([primary]),
 * otherwise dark (#17181C) with white text, or with red text when [destructive], or on glass when it sits [onCard]
 * and its colour shows through.
 * Body type (Secondary at [ButtonSize.Row]), an [icon] before the text when given.
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
    size: ButtonSize = ButtonSize.Page,
    onCard: Boolean = false,
    destructive: Boolean = false,
) {
    val ink = if (destructive) (if (size == ButtonSize.Compact) DestructiveInkSmall else DestructiveInk) else color
    Row(
        // Dimmed before the pill is drawn, so a disabled button fades whole, pill and label (user, 2026-11-03).
        modifier.height(size.height).alpha(if (enabled) 1f else 0.4f).clip(CircleShape)
            .let {
                when {
                    primary -> it.background(Color.White)
                    onCard -> it.background(Color.White.copy(alpha = 0.08f)).border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                    else -> it.background(HaloButtonSurface)
                }
            }
            .let { if (enabled) it.pressClickable(onClick, CircleShape) else it }
            .semantics { role = Role.Button }
            .padding(horizontal = size.padding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(size.icon), tint = ink)
            Spacer(Modifier.width(8.dp))
        }
        val style = when (size) {
            ButtonSize.Row -> GlowType.secondary
            ButtonSize.Compact, ButtonSize.Hero -> GlowType.body
            // Semibold on the page's own buttons and the main tap (Halo boards: 17 and 16). Card title is the nearest role, 17.
            ButtonSize.Main, ButtonSize.Page -> GlowType.button
        }
        Text(text, color = ink, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
 * A text field in the tile style (HaloRewardsSettingsRules board, B5: #17181C, a squircle), the [label] over it in
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
        if (label != null) Text(label, color = GlowType.noteColor, style = GlowType.secondary)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(GlowLayout.TileShape).background(HaloButtonSurface)
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
 * A sheet over the page (HaloQuietHoursSheet and HaloDeleteSleepSheet boards): the solid #111215 sheet with its
 * grabber, the [title] in Sheet title type, an
 * optional [note] under it, then [content], [side] from the edges (narrower for a sheet of grouped lists, which
 * bring their own inset). It scrolls when taller than most of the screen. Without a [title] the content starts at
 * the top, for a sheet that heads itself (the calendar's month, the info sheet's name).
 */
@Composable
fun PageSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    note: String? = null,
    side: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.88f).dp
    GlassSheet(onDismiss) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = maxHeight).verticalScroll(rememberScrollState())
                .padding(horizontal = side).navigationBarsPadding().padding(top = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (title != null) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = Color.White, style = GlowType.sheetTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (note != null) Text(note, color = Color.White.copy(alpha = GlowType.SECONDARY_ALPHA), style = GlowType.secondary)
            }
            content()
        }
    }
}

/**
 * A question before something that can't be taken back (Forget, Disconnect, Delete all): a [PageSheet] with the
 * [text], any extra [content], then Cancel and the [confirm] button side by side: white, or dark with red text when
 * [destructive] (HaloDeleteSleepSheet board).
 */
@Composable
fun PageConfirm(
    title: String,
    text: String?,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismiss: String = stringResource(R.string.glow_cancel),
    confirmEnabled: Boolean = true,
    destructive: Boolean = false,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    PageSheet(onDismiss, title) {
        if (text != null) Text(text, color = Color.White.copy(alpha = 0.8f), style = GlowType.body)
        content?.invoke(this)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PageButton(dismiss, onDismiss, Modifier.weight(1f))
            PageButton(
                confirm, onConfirm, Modifier.weight(1f), enabled = confirmEnabled, primary = !destructive, destructive = destructive,
            )
        }
    }
}

/**
 * A chip (spec 2.4, fully round): a hairline and 72% text, or, when [selected], dark (#34363E) with a 1.5 dp white ring
 * and white text, because a picked chip is not the next tap (HaloRewardsSettingsRules board, picked state). A [filled]
 * chip sits on the dark button surface instead of the hairline, for a lone chip like Live. Secondary type, at least 36 dp tall.
 * A [description] is what TalkBack reads in place of a [text] too short to say it, like a day's letter.
 */
@Composable
fun PageChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    role: Role = Role.Checkbox,
    filled: Boolean = false,
    description: String? = null,
) {
    Box(
        modifier.heightIn(min = 36.dp).clip(CircleShape)
            .background(if (selected) PickedSurface else if (filled) HaloButtonSurface else Color.Transparent)
            .let {
                when {
                    selected -> it.border(PickedRing, Color.White, CircleShape)
                    filled -> it
                    else -> it.border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
                }
            }
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, role = role, onClick = onClick)
            .semantics {
                this.selected = selected
                description?.let { contentDescription = it }
            }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, color = if (selected) Color.White else Color.White.copy(alpha = GlowType.SECONDARY_ALPHA),
            style = GlowType.secondary, maxLines = 1,
            modifier = if (description != null) Modifier.clearAndSetSemantics {} else Modifier,
        )
    }
}

/** A picked chip or option's fill and ring (HaloManualPair board: #34363E with a 1.5 px white ring). */
internal val PickedSurface = Color(0xFF34363E)
internal val PickedRing = 1.5.dp

/**
 * A pill that names a choice or does one thing: an [icon] in [tint] or a [dot], the [label] in [style] (Body), and a
 * [caret] when tapping opens a list. [height] tall on [surface] (the Control surface), with a [hairline] on a quiet
 * card. Without [onClick] it's only a label, like the live screen's sport or a summary's earned best. A long label
 * ends in an ellipsis and leaves the caret its room. Small pills (Done over a summary, the earned chips) take
 * Secondary.
 */
@Composable
internal fun IconPill(
    label: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color = Color.White,
    dot: Color? = null,
    caret: Boolean = false,
    height: Dp = 44.dp,
    surface: Color = HaloButtonSurface,
    hairline: Boolean = false,
    description: String? = null,
    style: TextStyle = GlowType.body,
) {
    Row(
        modifier.height(height).clip(CircleShape).background(surface)
            .let { if (hairline) it.border(1.dp, RowHairline, CircleShape) else it }
            .let { if (onClick != null) it.pressClickable(onClick, CircleShape) else it }
            .semantics {
                if (onClick != null) role = Role.Button
                if (description != null) contentDescription = description
            }
            .padding(start = if (icon != null || dot != null) 12.dp else 16.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(18.dp), tint = tint)
        if (dot != null) Box(Modifier.size(8.dp).background(dot, CircleShape))
        // Weighted only beside a caret, so a squeezed label gives way to it; a weight in a scrolling row would be zero wide.
        Text(
            label, if (caret) Modifier.weight(1f, fill = false) else Modifier, color = Color.White, style = style, maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (caret) Icon(AppIcons.CaretDown, null, Modifier.size(14.dp), tint = Color.White)
    }
}

/** A segmented control's track (HaloRewardsSettingsRules board, B4: #111215, the picked segment white). */
private val SegmentTrack = Color(0xFF111215)

/**
 * A row of choices on a dark track, the chosen one on a white pill: Female or Male, Day to Year, a route on or
 * off, how you feel. [selected] -1 leaves none chosen. [role] is what each choice is to a screen reader (a tab for
 * the ranges that switch a detail's chart).
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
) {
    Row(modifier.fillMaxWidth().clip(CircleShape).background(SegmentTrack).padding(4.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).clip(CircleShape)
                    .background(if (on) Color.White else Color.Transparent)
                    .pressClickable({ onSelect(i) }, CircleShape, scale = 0.97f)
                    .semantics { this.role = role; this.selected = on }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (on) Color.Black else Color.White.copy(alpha = 0.6f), style = GlowType.body, maxLines = 1)
            }
        }
    }
}

/**
 * A small heading in Secondary over what it names: a panel of rows (so two panels never sit back to back), a row of
 * chips, what a workout earned. [inset] lines it up with what's under it: a group heading sits 4 in (HaloYou board).
 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, inset: Dp = 4.dp) {
    Text(
        text, modifier.padding(start = inset), color = GlowType.noteColor, style = GlowType.secondary,
        maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

/** How far a [SectionLabel] over a panel of rows sits in (HaloYou board: 4). */
val PanelLabelInset = 4.dp

/**
 * A status in a few words with a coloured dot (spec 2.1, the strap's state): "Strap ready, 72 bpm", "GPS good". The
 * dot leads, or follows the words where the line is set flush right ([dotAfter], the live screen's corner).
 */
@Composable
internal fun StatusDot(text: String, dot: Color, modifier: Modifier = Modifier, dotAfter: Boolean = false) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!dotAfter) Canvas(Modifier.size(6.dp)) { drawCircle(dot) }
        Text(text, color = GlowType.noteColor, style = GlowType.secondary, maxLines = 1)
        if (dotAfter) Canvas(Modifier.size(6.dp)) { drawCircle(dot) }
    }
}

/**
 * A secondary choice: a tile on the dark button surface (HaloRewardsSettingsRules board, B5) with a [caption] in Secondary
 * over the [label] in Body,
 * and the tap caret at its end, so it reads as something that opens. As wide as it's given.
 */
@Composable
fun OptionButton(caption: String, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(GlowLayout.TileShape).background(HaloButtonSurface).pressClickable(onClick, GlowLayout.TileShape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(caption, color = GlowType.noteColor, style = GlowType.secondary)
            Text(label, color = Color.White, style = GlowType.body)
        }
        Spacer(Modifier.width(RowCaretGap))
        RowChevron()
    }
}

/**
 * A [PageField] for a whole number, or a [decimal] one: only digits (and a point) get in, at most five, and the
 * keyboard is the number pad. As wide as it's given. An empty [label] leaves the field unlabelled, for a pair like
 * hours and minutes.
 */
@Composable
internal fun PageNumberField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    unit: String,
    error: Boolean,
    modifier: Modifier = Modifier,
    decimal: Boolean = false,
) {
    PageField(
        value, { v -> onChange(v.filter { it.isDigit() || (decimal && (it == '.' || it == ',')) }.take(NUMBER_FIELD_LENGTH)) },
        label.ifEmpty { null }, modifier.fillMaxWidth(), unit = unit, error = error,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
    )
}

/** The most characters a [PageNumberField] takes: a weight like 158.7, or five digits. */
private const val NUMBER_FIELD_LENGTH = 5

/** A secondary line of explanation on a page. */
@Composable
fun PageNote(text: String, color: Color = GlowType.noteColor) {
    Text(text, color = color, style = GlowType.secondary)
}

/** A footnote under a group's panel (HaloYou board: 12 px, #6E7480, 4 in) for a panel that [PageGroup] doesn't build. */
@Composable
fun PageFootnote(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier.padding(horizontal = 4.dp), color = FootnoteInk, style = GlowType.footnote)

/**
 * The header's Save (HaloRewardsSettingsRules board, B1): a text pill on the right of a page that edits. Show it only
 * while the page has unsaved edits: then it's the page's one white thing, and while [enabled] is false (edits that
 * can't be saved yet) it stays dark and still.
 */
@Composable
@NonRestartableComposable
fun PageSaveButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) =
    PageButton(text, onClick, modifier, enabled = enabled, primary = enabled, size = ButtonSize.Row)

/** A footnote under a group's panel (HaloYou board: 12, #6E7480). */
private val FootnoteInk = GlowType.footnoteColor

/**
 * One settings group (HaloRewardsSettingsRules board, B2): a grey [heading] above, the panel of [rows] ([PageRows]), and
 * a [footnote] under it, 8 apart and 4 in. Groups sit 24 apart in the page's column; a destructive group goes last.
 */
@Composable
fun PageGroup(
    modifier: Modifier = Modifier,
    heading: String? = null,
    footnote: String? = null,
    rows: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (heading != null) SectionLabel(heading)
        PageRows(content = rows)
        if (footnote != null) Text(footnote, Modifier.padding(horizontal = 4.dp), color = GlowType.footnoteColor, style = GlowType.footnote)
    }
}

/**
 * An action row, one that does something now (HaloBackup board): the [icon] and the [label] in white at 15 medium, or
 * both in red when [destructive], and no chevron. While [busy] a spinner takes the icon's place and the row doesn't take taps.
 */
@Composable
fun PageActionRow(
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
    val inset = LocalRowInset.current
    Row(
        modifier.fillMaxWidth()
            .clickable(enabled = enabled && !busy, role = Role.Button, onClick = onClick)
            .topHairline(divider, Hairline)
            .alpha(if (enabled) 1f else 0.45f)
            .heightIn(min = PanelRowHeight)
            .padding(horizontal = if (inset > 0.dp) inset else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (busy) RowSpinner(ink) else if (icon != null) Icon(icon, null, Modifier.size(18.dp), tint = ink)
        Text(label, color = ink, style = GlowType.body.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private const val FULL_TURN = 360f
private const val SPINNER_SWEEP = 270f
private const val SPINNER_MS = 1000

/** The 18 dp spinner that replaces an action row's icon while it works: a three-quarter ring turning once a second. */
@Composable
private fun RowSpinner(color: Color) {
    val turn by rememberInfiniteTransition(label = "row spinner")
        .animateFloat(0f, FULL_TURN, infiniteRepeatable(tween(SPINNER_MS, easing = LinearEasing)), label = "row spinner turn")
    Canvas(Modifier.size(18.dp)) {
        val stroke = 2.dp.toPx()
        drawArc(
            color, startAngle = turn, sweepAngle = SPINNER_SWEEP, useCenter = false,
            topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - stroke, size.height - stroke),
            style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
        )
    }
}

/** An info row, read only (HaloRewardsSettingsRules board): the [name] on the left, the [value] on the right in #9AA0AA, no chevron. */
@Composable
@NonRestartableComposable
fun PageInfoRow(name: String, value: String, divider: Boolean = true) = PageRow(name, value, divider = divider)

/**
 * An item row, a thing you own (HaloDeletedSleep and HaloAlarms boards): the [name] at 15 over its [details] at 13 in
 * #9AA0AA, and one action pill on the right ([action], #17181C and 36 tall), or a [trailing] switch instead.
 */
@Composable
fun PageItemRow(
    name: String,
    details: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    divider: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val inset = LocalRowInset.current
    Row(
        modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .topHairline(divider, Hairline).heightIn(min = 64.dp)
            .padding(start = inset, end = if (inset > 0.dp) 12.dp else 0.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(RowLineGap)) {
            RowName(name)
            Text(details, color = RowValueColor, style = GlowType.secondary)
        }
        if (trailing != null) trailing()
        else if (action != null && onAction != null) PageButton(action, onAction, size = ButtonSize.Row, modifier = Modifier.height(36.dp))
    }
}

/** A text panel, for paragraphs such as release notes (HaloUpdates board): a plain panel, a 15 semibold [title] and 14 px text, no rows. */
@Suppress("UnstableCollections", "ComposeUnstableCollections")
@Composable
fun PageTextPanel(title: String, paragraphs: List<String>, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().clip(PanelShape).background(PanelSurface, PanelShape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, color = Color.White, style = GlowType.cardTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        paragraphs.forEach { Text(it, color = TextPanelInk, style = GlowType.body) }
    }
}

private val TextPanelInk = Color(0xFFC9CED6)

/** How a [StatusBanner] reads: its bar is [color] (HaloRewardsSettingsRules board, B1). */
enum class BannerKind(val color: Color) {
    Good(MetricColors.good),
    Warn(MetricColors.warn),
    Problem(DestructiveInk),
}

/**
 * A status banner, only when there's news (an update ready, a backup that failed, the strap away): a plain panel with a
 * 4 dp bar on its left in the [kind]'s colour, a 15 semibold [title], a 13 px [note] and one action pill on the right
 * when [action] is given.
 */
@Composable
fun StatusBanner(
    kind: BannerKind,
    title: String,
    modifier: Modifier = Modifier,
    note: String? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().clip(PanelShape).background(PanelSurface, PanelShape).heightIn(min = 56.dp)
            .padding(end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).height(56.dp).background(kind.color))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = Color.White, style = GlowType.cardTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (note != null) Text(note, color = RowValueColor, style = GlowType.secondary)
        }
        if (action != null && onAction != null) {
            Spacer(Modifier.width(12.dp))
            PageButton(action, onAction, size = ButtonSize.Row, modifier = Modifier.height(36.dp))
        }
    }
}

/** A list row's divider: a 1 dp [color] line along its top, when [show] (every row but the first). */
internal fun Modifier.topHairline(show: Boolean, color: Color = RowHairline): Modifier =
    if (show) drawBehind { drawLine(color, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) } else this

/** A time of day from [epoch] seconds in the phone's zone, "07:45" or "7:45 AM" by the phone's setting. */
internal fun clock(epoch: Long): String = TimeOfDay.format(epoch, ZoneId.systemDefault())
