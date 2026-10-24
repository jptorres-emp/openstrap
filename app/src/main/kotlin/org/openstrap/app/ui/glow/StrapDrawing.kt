package org.openstrap.app.ui.glow

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer

/*
 * The strap as a line drawing, shared by the Strap page's card and Today's pairing stage.
 */

/**
 * The strap seen from the front, drawn in line: the band running off the top and bottom with a faint weave, the pod as
 * a rectangle a little wider than the band with its bevel, one highlight and the lugs, and its light in [led]. While it
 * syncs, an arc turns round the light. When it isn't connected the drawing dims and the light goes out to a ring.
 */
@Composable
internal fun StrapDrawing(led: Color, lit: Boolean, syncing: Boolean, modifier: Modifier) {
    val turn = if (syncing) {
        val t = rememberInfiniteTransition(label = "strap sync")
        t.animateFloat(
            0f, 360f, infiniteRepeatable(tween(2400, easing = LinearEasing)),
            label = "strap sync turn",
        ).value
    } else 0f
    Canvas(modifier.graphicsLayer { alpha = if (lit) 1f else 0.45f }) {
        // Drawn on a 120 by 150 grid, the card slot's shape.
        val k = size.width / 120f
        fun o(x: Float, y: Float) = Offset(x * k, y * k)
        val white = Color.White
        // The band: a faint weave between its two edges, which fade out at the card's top and bottom.
        for (row in 0 until 25) {
            val y = row * 6f
            for (x in listOf(44f, 52f, 60f, 68f)) drawLine(white.copy(alpha = 0.09f), o(x, y + 1.5f), o(x + 3f, y + 1.5f), k, StrokeCap.Round)
            for (x in listOf(48f, 56f, 64f, 72f)) drawLine(white.copy(alpha = 0.09f), o(x, y + 4.5f), o(x + 3f, y + 4.5f), k, StrokeCap.Round)
        }
        val edge = Brush.verticalGradient(
            0f to white.copy(alpha = 0f), 0.22f to white.copy(alpha = 0.55f), 0.78f to white.copy(alpha = 0.55f), 1f to white.copy(alpha = 0f),
            startY = 0f, endY = 150f * k,
        )
        drawLine(edge, o(43f, 0f), o(43f, 150f), 1.4f * k)
        drawLine(edge, o(77f, 0f), o(77f, 150f), 1.4f * k)
        // The lugs, where the band meets the pod.
        for ((x, y) in listOf(43f to 36f, 77f to 36f, 43f to 110f, 77f to 110f)) drawLine(white.copy(alpha = 0.5f), o(x, y), o(x, y + 4f), 1.2f * k, StrokeCap.Round)
        // The pod, its bevel and one highlight on the top left.
        drawRoundRect(Color(0xFF0C0F0E), o(34f, 40f), Size(52f * k, 70f * k), CornerRadius(9f * k))
        drawRoundRect(white.copy(alpha = 0.8f), o(34f, 40f), Size(52f * k, 70f * k), CornerRadius(9f * k), style = Stroke(1.5f * k))
        drawRoundRect(white.copy(alpha = 0.2f), o(39f, 45f), Size(42f * k, 60f * k), CornerRadius(5f * k), style = Stroke(k))
        val highlight = Path().apply {
            moveTo(40f * k, 58f * k); lineTo(40f * k, 52f * k)
            arcTo(Rect(40f * k, 47f * k, 50f * k, 57f * k), 180f, 90f, false)
            lineTo(55f * k, 47f * k)
        }
        drawPath(highlight, white.copy(alpha = 0.45f), style = Stroke(1.2f * k, cap = StrokeCap.Round))
        // The light.
        val c = o(60f, 75f)
        if (lit) {
            drawCircle(Brush.radialGradient(listOf(led.copy(alpha = 0.55f), led.copy(alpha = 0f)), c, 14f * k), 14f * k, c)
            drawCircle(led, 3.2f * k, c)
            if (syncing) rotate(turn, c) {
                drawArc(led, -90f, 75f, false, o(49f, 64f), Size(22f * k, 22f * k), style = Stroke(1.4f * k, cap = StrokeCap.Round))
            }
        } else drawCircle(white.copy(alpha = 0.6f), 3.2f * k, c, style = Stroke(k))
    }
}

/** The strap's own light, a soft green, as it glows when connected. */
internal val StrapLight = Color(0xFF7DE0C0)
