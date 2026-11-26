package org.openstrap.app.ui.glow

import org.openstrap.app.ui.theme.IconSize
import org.openstrap.app.ui.theme.Inset
import org.openstrap.app.ui.theme.Corner
import org.openstrap.app.ui.theme.Space
import org.openstrap.app.ui.theme.Greys
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
import org.openstrap.app.ui.theme.Ink
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.app.ui.theme.AppIcons
import java.time.ZoneId
import org.openstrap.app.ui.theme.ControlSurface

/*
 * The settings pages (the Strap page and what it opens, Profile, You) in the glow language's quiet tier: black, with
 * grouped lists in place of Material panels, a round back button, and plain buttons. One look for every page, so the
 * app doesn't turn into another product at the strap's boundary.
 */



/** The red of a destructive button's text on its dark pill (HaloDeleteSleepSheet board, #FF5A5F). */
val DestructiveInk = MetricColors.heart


/** The small chevron at the end of a row that opens something (HaloYou board: 14, #6E7480). */
@Composable
internal fun RowChevron() = Icon(AppIcons.TapCaret, null, tint = ChevronInk, modifier = Modifier.size(CaretSize))

private val ChevronInk = Ink.Tertiary

/**
 * Something new waits behind what it sits on (a new version, on the avatar and the Updates row): a small white dot,
 * the size of the strap chip's, with a [ring] of the background around it where it overlaps a button's edge.
 */
@Composable
fun NoticeDot(modifier: Modifier = Modifier, ring: Color? = null) {
    if (ring == null) Box(modifier.size(7.dp).background(Color.White, CircleShape))
    else Box(modifier.size(11.dp).background(ring, CircleShape).padding(Space.S2).background(Color.White, CircleShape))
}

/** A row's name in Body, on one line (spec 2.3): ellipsized only when it alone is wider than the row. */
@Composable
internal fun RowName(name: String, color: Color = Color.White) =
    Text(name, color = color, style = GlowType.body, maxLines = 1, overflow = TextOverflow.Ellipsis)

/** Between a row's name and the value on its right: the row's gap between slots. */
private val RowValueGap = RowSpec.Gap
/** A row's note, an info row's value and a second line: #9AA0AA (HaloRewardsSettingsRules board). */
private val RowValueColor = GlowType.noteColor

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
    valueModifier: Modifier = Modifier,
    valueColor: Color = RowValueColor,
    valueStyle: TextStyle = GlowType.secondary,
) {
    Layout(
        content = {
            RowName(name, nameColor)
            if (value != null) {
                Text(value, valueModifier, color = valueColor, style = valueStyle, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
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
            val line = RowSpec.LineGap.roundToPx()
            layout(width, nameP.height + line + valueP.height) {
                nameP.place(0, 0)
                valueP.place(0, nameP.height + line)
            }
        }
    }
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
    Column(modifier.alpha(if (enabled) 1f else 0.45f), verticalArrangement = Arrangement.spacedBy(Space.S8)) {
        if (label != null) Text(label, color = GlowType.noteColor, style = GlowType.secondary)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(GlowLayout.TileShape).background(Greys.Control)
                .let { if (error) it.border(1.dp, MetricColors.warn, GlowLayout.TileShape) else it }
                .padding(start = Space.S16, end = if (trailing != null) Space.S4 else Space.S16),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f).padding(vertical = Space.S12)) {
                if (value.isEmpty() && placeholder != null) {
                    Text(placeholder, color = Ink.Disabled, style = GlowType.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                BasicTextField(
                    value, onValueChange, Modifier.fillMaxWidth(), enabled = enabled,
                    textStyle = GlowType.body.copy(color = Color.White), singleLine = true,
                    keyboardOptions = keyboardOptions, visualTransformation = visualTransformation,
                    cursorBrush = SolidColor(Color.White),
                )
            }
            if (unit != null) {
                Spacer(Modifier.width(Space.S8))
                Text(unit, color = Ink.Secondary, style = GlowType.secondary, maxLines = 1)
            }
            if (trailing != null) {
                Spacer(Modifier.width(Space.S4))
                trailing()
            }
        }
    }
}

/**
 * A status in a few words with a coloured dot (spec 2.1, the strap's state): "Strap ready, 72 bpm", "GPS good". The
 * dot leads, or follows the words where the line is set flush right ([dotAfter], the live screen's corner).
 */
@Composable
internal fun StatusDot(text: String, dot: Color, modifier: Modifier = Modifier, dotAfter: Boolean = false) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S8)) {
        if (!dotAfter) Canvas(Modifier.size(6.dp)) { drawCircle(dot) }
        Text(text, color = GlowType.noteColor, style = GlowType.secondary, maxLines = 1)
        if (dotAfter) Canvas(Modifier.size(6.dp)) { drawCircle(dot) }
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

/**
 * One settings group (HaloRewardsSettingsRules board, B2): a grey [heading] above, the card of [rows] ([HaloCard] of rows), and
 * a [footnote] under it, 8 apart and 4 in. Groups sit 24 apart in the page's column; a destructive group goes last.
 */
@Composable
fun PageGroup(
    modifier: Modifier = Modifier,
    heading: String? = null,
    footnote: String? = null,
    rows: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.S8)) {
        if (heading != null) HaloTitle(heading, variant = TitleVariant.Group)
        HaloCard(fill = CardFill.Rows, content = rows)
        if (footnote != null) HaloNote(footnote, variant = NoteVariant.Footnote)
    }
}

private const val FULL_TURN = 360f
private const val SPINNER_SWEEP = 270f
private const val SPINNER_MS = 1000

/** The 18 dp spinner that replaces an action row's icon while it works: a three-quarter ring turning once a second. */
@Composable
internal fun RowSpinner(color: Color) {
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

/** A list row's divider: a 1 dp [color] line along its top, when [show] (every row but the first). */
internal fun Modifier.topHairline(show: Boolean, color: Color): Modifier =
    if (show) drawBehind { drawLine(color, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) } else this

/** A time of day from [epoch] seconds in the phone's zone, "07:45" or "7:45 AM" by the phone's setting. */
internal fun clock(epoch: Long): String = TimeOfDay.format(epoch, ZoneId.systemDefault())
