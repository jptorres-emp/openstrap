@file:Suppress("MatchingDeclarationName")

package org.openstrap.app.ui.glow

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.openstrap.app.ui.components.HapticEvent
import org.openstrap.app.ui.components.rememberHaptics
import org.openstrap.app.ui.theme.Ink
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/*
 * The ruler (board HaloReadyEveryKind): a value set by dragging a strip of ticks under a white needle in the middle.
 * It snaps to whole steps; its labelled ticks are also tap targets that jump there; both edges fade out. To a screen
 * reader it is a slider, so swiping up or down steps it.
 */

/** The ruler's measurements. */
internal object RulerSpec {
    /** Room above the needle, so it stands apart from the number it sets. */
    val TopGap = 8.dp

    /** The whole strip: the needle and ticks above, the labels under them. */
    val Height = 54.dp

    /** The needle runs from the top; the ticks start this far down, so the needle stands above them. */
    val TickTop = 6.dp
    val TickMinor = 9.dp
    val TickMajor = 16.dp
    val TickWidth = 1.5.dp
    val NeedleWidth = 3.dp
    val NeedleHeight = 24.dp

    /** The labels' line, centred this far down. */
    val LabelCentre = 39.dp

    /** How far from a label a tap still lands on it: half the board's 60 wide label button. */
    val LabelReach = 30.dp

    /** Where the fade at each edge ends, as a share of the width. */
    const val FADE = 0.16f
}

/**
 * A ruler over [count] steps (0 until [count]), at [index]. Dragging moves it a step per [stepWidth] and hands each
 * new step to [onIndex] as it passes the needle; letting go settles on the nearest. [labels] name the major ticks (by
 * step), drawn in [tint] and the one at the needle in white; tapping near one sets it. [valueText] is what a screen
 * reader says for the value, [description] what the ruler sets.
 */
@Suppress("UnstableCollections", "ComposeUnstableCollections")
@Composable
internal fun HaloRuler(
    index: Int,
    count: Int,
    onIndex: (Int) -> Unit,
    labels: Map<Int, String>,
    stepWidth: Dp,
    valueText: String,
    tint: Color,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    val last = (count - 1).coerceAtLeast(0)
    val stepPx = with(LocalDensity.current) { stepWidth.toPx() }
    val position = remember { Animatable(index.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    // A tapped label slides the ruler there under the finger's say-so, so it ticks too; a value set from outside doesn't.
    var tapped by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val onPick by rememberUpdatedState(onIndex)
    val now by rememberUpdatedState(index)
    // A value set from outside (a label, a tab, last time's) slides the ruler there; a drag moves it itself.
    LaunchedEffect(index) {
        if (!dragging) position.animateTo(index.toFloat(), spring())
        tapped = false
    }
    val labelled by rememberUpdatedState(labels)
    // A click for each step that passes the needle while you move it, a firmer one on a labelled tick.
    LaunchedEffect(Unit) {
        snapshotFlow { position.value.roundToInt() }.distinctUntilChanged().drop(1).collect { step ->
            if (dragging || tapped) haptics.perform(if (step in labelled) HapticEvent.ScrubMark else HapticEvent.ScrubStep)
        }
    }
    val measurer = rememberTextMeasurer()
    val labelStyle = GlowType.noteSmall
    Spacer(
        modifier.padding(top = RulerSpec.TopGap).fillMaxWidth().height(RulerSpec.Height)
            .semantics {
                if (description != null) contentDescription = description
                stateDescription = valueText
                progressBarRangeInfo = ProgressBarRangeInfo(index.toFloat(), 0f..last.toFloat(), steps = (last - 1).coerceAtLeast(0))
                setProgress { v ->
                    val i = v.roundToInt().coerceIn(0, last)
                    if (i != now) onPick(i)
                    true
                }
            }
            .pointerInput(last, stepPx) {
                var at = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true; at = position.value },
                    onDragEnd = {
                        dragging = false
                        scope.launch { position.animateTo(sticky(at, labelled.keys).roundToInt().coerceIn(0, last).toFloat(), spring()) }
                    },
                    onDragCancel = { dragging = false; scope.launch { position.animateTo(now.toFloat(), spring()) } },
                ) { change, dx ->
                    change.consume()
                    at = (at - dx / stepPx).coerceIn(0f, last.toFloat())
                    // Under the finger, but held near a labelled tick, so the ruler sticks there.
                    val target = sticky(at, labelled.keys)
                    scope.launch { position.snapTo(target) }
                    val i = target.roundToInt()
                    if (i != now) onPick(i)
                }
            }
            .pointerInput(labels, stepPx) {
                val reach = RulerSpec.LabelReach.toPx()
                detectTapGestures { tap ->
                    val at = position.value + (tap.x - size.width / 2f) / stepPx
                    val nearest = labels.keys.minByOrNull { abs(it - at) } ?: return@detectTapGestures
                    if (abs(nearest - at) * stepPx <= reach && nearest != now) {
                        tapped = true
                        onPick(nearest)
                    }
                }
            }
            // Drawn in its own layer, so the fade at the edges takes the ticks and labels out with it.
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithCache {
                val fade = Brush.horizontalGradient(
                    0f to Color.Transparent, RulerSpec.FADE to Color.Black, 1f - RulerSpec.FADE to Color.Black, 1f to Color.Transparent,
                )
                val tickTop = RulerSpec.TickTop.toPx()
                val tickW = RulerSpec.TickWidth.toPx()
                val minor = RulerSpec.TickMinor.toPx()
                val major = RulerSpec.TickMajor.toPx()
                val needleW = RulerSpec.NeedleWidth.toPx()
                val needleH = RulerSpec.NeedleHeight.toPx()
                val labelY = RulerSpec.LabelCentre.toPx()
                val picked = labelStyle.copy(color = Color.White, fontWeight = FontWeight.SemiBold)
                val plain = labelStyle.copy(color = tint)
                onDrawBehind {
                    val centre = size.width / 2f
                    val pos = position.value
                    val half = centre / stepPx
                    val from = floor(pos - half).toInt().coerceAtLeast(0)
                    val to = ceil(pos + half).toInt().coerceAtMost(last)
                    val atNeedle = pos.roundToInt()
                    for (i in from..to) {
                        val x = centre + (i - pos) * stepPx
                        val label = labels[i]
                        drawRoundRect(
                            if (label != null) Ink.Strong else Ink.Tertiary, Offset(x - tickW / 2f, tickTop),
                            Size(tickW, if (label != null) major else minor), CornerRadius(tickW / 2f),
                        )
                        if (label != null) {
                            val text = measurer.measure(label, if (i == atNeedle) picked else plain)
                            drawText(text, topLeft = Offset(x - text.size.width / 2f, labelY - text.size.height / 2f))
                        }
                    }
                    drawRoundRect(Color.White, Offset(centre - needleW / 2f, 0f), Size(needleW, needleH), CornerRadius(needleW / 2f))
                    drawRect(fade, blendMode = BlendMode.DstIn)
                }
            },
    )
}

/** How far either side of a labelled tick, in steps, it pulls the ruler. */
internal const val STICK_REACH = 1.5f

/**
 * Where the ruler shows for the finger at [at] (in steps): the same, except within [STICK_REACH] of one of the
 * [labelled] ticks, where it is drawn towards it, flat at the tick and easing back to the finger at the edge. So a
 * drag hangs on 1, 3 and 5 km for a moment, and letting go nearby settles there.
 */
internal fun sticky(at: Float, labelled: Set<Int>): Float {
    val nearest = labelled.minByOrNull { abs(it - at) } ?: return at
    val d = at - nearest
    return if (abs(d) >= STICK_REACH) at else nearest + d * abs(d) / STICK_REACH
}
