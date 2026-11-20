package org.openstrap.app.ui.glow

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import org.openstrap.app.R
import org.openstrap.app.ui.components.pressClickable
import org.openstrap.app.ui.theme.Corner
import org.openstrap.app.ui.theme.Space

/*
 * The hero's goal typed in (board HaloReadyEveryKind): tapping the big number opens the number keyboard on it, with
 * what it holds selected. Done, or tapping elsewhere, sets the goal and the ruler slides to it; a value that can't be
 * read leaves the goal as it was.
 */

/**
 * How a hero's number is typed: [start] is what the field opens with, [unit] the unit shown while typing (minutes for
 * a time shown in hours), [decimal] lets a point in, and [onText] reads what was typed, holds it to the goal's range
 * and sets it (or leaves the goal as it was when it can't be read).
 */
@Immutable
internal class HeroInput(
    val start: String,
    val unit: String?,
    val decimal: Boolean,
    val onText: (String) -> Unit,
)

/** At most this many figures in a typed goal. */
private const val MAX_FIGURES = 5

/**
 * The big [number] and its [unit] in [size], which turns into a field with the number keyboard when tapped ([input]).
 * While typing, a hairline in [tint] rings it.
 */
@Composable
internal fun TypedGoalNumber(number: String, unit: String?, size: BigNumber, tint: Color, input: HeroInput) {
    var typing by rememberSaveable { mutableStateOf(false) }
    val label = stringResource(R.string.ready_type_goal)
    if (!typing) {
        Box(
            Modifier.fillMaxWidth().pressClickable({ typing = true }, Corner.Inner, onClickLabel = label, role = Role.Button),
        ) { HaloBigNumber(number, size, unit = unit, unitColor = tint, fit = true) }
        return
    }
    var field by remember { mutableStateOf(TextFieldValue(input.start, TextRange(0, input.start.length))) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var focused by remember { mutableStateOf(false) }
    fun done() {
        if (!typing) return
        typing = false
        input.onText(field.text.replace(',', '.'))
        keyboard?.hide()
    }
    LaunchedEffect(Unit) {
        // After the field's first frame, so it is there to take the focus.
        androidx.compose.runtime.withFrameNanos { }
        focus.requestFocus()
        keyboard?.show()
    }
    val noSelection = TextSelectionColors(Color.Transparent, Color.Transparent)
    CompositionLocalProvider(LocalTextSelectionColors provides noSelection) {
    BasicTextField(
        field,
        { v ->
            val kept = v.text.filter { it.isDigit() || (input.decimal && (it == '.' || it == ',')) }.take(MAX_FIGURES)
            if (kept.count { it == '.' || it == ',' } <= 1) field = v.copy(text = kept)
        },
        Modifier.fillMaxWidth().focusRequester(focus).onFocusChanged {
            if (focused && !it.isFocused) done()
            focused = it.isFocused
        },
        textStyle = GlowType.body.copy(color = Color.Transparent),
        cursorBrush = SolidColor(Color.Transparent),
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (input.decimal) KeyboardType.Decimal else KeyboardType.NumberPassword, imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { done() }),
        decorationBox = { inner ->
            // The ring sits round the number without moving it: drawn outside its bounds.
            Box(
                Modifier.fillMaxWidth().drawBehind {
                    val out = RING_OUT.toPx()
                    drawRoundRect(
                        tint, androidx.compose.ui.geometry.Offset(-out, -out),
                        androidx.compose.ui.geometry.Size(this.size.width + 2 * out, this.size.height + 2 * out),
                        androidx.compose.ui.geometry.CornerRadius(RING_RADIUS.toPx()),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
                    )
                },
            ) {
                HaloBigNumber(field.text.ifEmpty { " " }, size, unit = input.unit, unitColor = tint, fit = true)
                Box(Modifier.matchParentSize()) { inner() }
            }
        },
    )
    }
}

private val RING_OUT = Space.S8
private val RING_RADIUS = 16.dp

/** A typed count, held to [range]; null when it isn't a number. */
internal fun typedCount(text: String, range: IntRange): Int? = text.trim().toIntOrNull()?.coerceIn(range)

/** A typed distance in km, to the nearest 100 m and held to [maxKm]; null when it isn't a number above 0. */
internal fun typedMeters(text: String, maxKm: Int): Int? =
    text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
        ?.let { (kotlin.math.round(it * TENTHS_PER_KM) * METERS_PER_TENTH).toInt().coerceIn(METERS_PER_TENTH, maxKm * METERS_PER_KM) }

/** Typed minutes as seconds, held to 1 minute to [maxHours]; null when it isn't a whole number above 0. */
internal fun typedSeconds(text: String, maxHours: Int): Long? =
    text.trim().toLongOrNull()?.takeIf { it > 0 }?.coerceAtMost(maxHours * MINUTES_AN_HOUR)?.let { it * SECONDS_A_MINUTE }

/** Typing a count in [range]. */
internal fun countInput(value: Int, range: IntRange, onValue: (Int) -> Unit) =
    HeroInput("$value", null, decimal = false) { t -> typedCount(t, range)?.let(onValue) }

private const val TENTHS_PER_KM = 10.0
private const val METERS_PER_TENTH = 100
private const val METERS_PER_KM = 1000
private const val MINUTES_AN_HOUR = 60L
private const val SECONDS_A_MINUTE = 60L
