@file:Suppress("MagicNumber") // the drawing's coordinates are the boards' 74 by 92 grid

package org.openstrap.app.ui.glow

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer

/*
 * The strap as a line drawing, shared by the Strap page's card, Today's pairing stage and the header's strap pill.
 */

/**
 * The strap seen from the front, drawn in line (HaloStrap, HaloStrapAway and HaloPair boards, a 74 by 92 grid): the band
 * running off the top and bottom in two edge lines with three faint weave lines at each end, the pod as a rounded
 * rectangle with its inner bevel and one highlight, and its light in [led] with a soft halo. While it syncs, an arc turns
 * round the light. Unlit (not connected) the light is only a ring and the drawing dims. [lineScale] thickens every line,
 * for a drawing too small for the hairlines of the card's.
 */
@Composable
internal fun StrapDrawing(
    led: Color,
    lit: Boolean,
    syncing: Boolean,
    modifier: Modifier,
    lineScale: Float = 1f,
) {
    // The turn is a State read only in draw, so a syncing strap redraws each frame without recomposing.
    val turn: State<Float> = if (syncing) {
        val t = rememberInfiniteTransition(label = "strap sync")
        t.animateFloat(
            0f, 360f, infiniteRepeatable(tween(2400, easing = LinearEasing)),
            label = "strap sync turn",
        )
    } else NoTurn
    Spacer(
        modifier.graphicsLayer { alpha = if (lit) 1f else 0.6f }.drawWithCache {
            val k = size.width / GRID_W
            // Line widths, which [lineScale] thickens; positions stay on the grid.
            val s = k * lineScale
            fun o(x: Float, y: Float) = Offset(x * k, y * k)
            val white = Color.White
            val highlight = Path().apply { moveTo(28f * k, 28f * k); lineTo(28f * k, 40f * k) }
            val c = o(37f, 46f)
            onDrawBehind {
                // The band's edges, and the faint weave at each end.
                for (x in BandEdges) {
                    drawLine(white.copy(alpha = 0.7f), o(x, 0f), o(x, 20f), 1.4f * s)
                    drawLine(white.copy(alpha = 0.7f), o(x, 72f), o(x, 92f), 1.4f * s)
                }
                for (y in WeaveRows) drawLine(white.copy(alpha = 0.12f), o(27f, y), o(47f, y), s)
                // The pod, its bevel and one highlight on the top left.
                drawRoundRect(Color(0xFF0B0C0E), o(21f, 20f), Size(32f * k, 52f * k), CornerRadius(9f * k))
                drawRoundRect(white.copy(alpha = 0.85f), o(21f, 20f), Size(32f * k, 52f * k), CornerRadius(9f * k),
                    style = Stroke(1.4f * s))
                drawRoundRect(white.copy(alpha = 0.18f), o(25f, 24f), Size(24f * k, 44f * k), CornerRadius(6f * k), style = Stroke(s))
                drawPath(highlight, white.copy(alpha = 0.35f), style = Stroke(1.4f * s, cap = StrokeCap.Round))
                // The light.
                if (lit) {
                    drawCircle(led.copy(alpha = 0.35f), 9f * k, c)
                    drawCircle(led, 3.2f * k, c)
                    if (syncing) rotate(turn.value, c) {
                        drawArc(led, -90f, 75f, false, o(c.x / k - 6f, c.y / k - 6f), Size(12f * k, 12f * k), style = Stroke(1.2f * s,
                            cap = StrokeCap.Round))
                    }
                } else drawCircle(white.copy(alpha = 0.6f), 3.2f * k, c, style = Stroke(s))
            }
        },
    )
}

/** The drawing's grid is 74 wide and 92 tall (the boards' strap svg). */
private const val GRID_W = 74f

/** The band's two edge lines. */
private val BandEdges = floatArrayOf(26f, 48f)

/** The weave's rows, three at each end. */
private val WeaveRows = floatArrayOf(4f, 10f, 16f, 76f, 82f, 88f)

/** The resting turn when the strap isn't syncing. */
private val NoTurn: State<Float> = mutableFloatStateOf(0f)

/** The strap's own light, a soft green, as it glows when connected. */
internal val StrapLight = Color(0xFF7DE0C0)

/** The strap card's silver, its light from below (Halo Strap and Pair boards: `rgb(201 206 214)`). */
internal val StrapSilver = Color(0xFFC9CED6)
