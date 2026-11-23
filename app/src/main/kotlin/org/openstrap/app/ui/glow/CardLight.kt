package org.openstrap.app.ui.glow

import org.openstrap.app.ui.theme.Greys
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import org.openstrap.app.ui.glow.draw.GlowStyle
import org.openstrap.app.ui.glow.draw.LightAngle
import org.openstrap.app.ui.glow.draw.chart

/** The grey card as a brush, for the draw/ code that fills a card outline itself. */
internal val SolidSurface: Brush = Brush.verticalGradient(listOf(Greys.Card, Greys.Card))

/**
 * A card lit by a soft light off its edge, as Ready's heart and the share cards are from below and the Heart rate zones
 * hero from the left: an ellipse [radiusX] across and [radiusY] tall around [center] in [color], through [stops]
 * (distance out from the centre, 0 to 1, to the colour's alpha there) to nothing at its rim. Written once so the three
 * lights stay one light.
 */
internal fun DrawScope.drawCardLight(color: Color, center: Offset, radiusX: Float, radiusY: Float, stops: List<Pair<Float, Float>>) {
    val colors = stops.map { (at, alpha) -> at to color.copy(alpha = alpha) } + (1f to Color.Transparent)
    // A circle as tall as the ellipse, stretched across to its width: the gradient stretches with it.
    scale(radiusX / radiusY, 1f, center) {
        @Suppress("SpreadOperator") // Brush takes its stops only as varargs, and there are three or four of them.
        val brush = Brush.radialGradient(*colors.toTypedArray(), center = center, radius = radiusY)
        drawCircle(brush, radiusY, center)
    }
}

/**
 * A workout's hero glow (spec 2.5): [color] lit from below as high as the workout's [effort] (0..1, more is held at
 * full), so an easy walk leaves most of the card dark and a hard run floods it. The past workout's hero, the summary's
 * and its heart rate drop all light this way.
 */
internal fun effortGlow(color: Color, effort: Float) =
    GlowStyle.chart(color).copy(lightAngle = LightAngle.Below, lightLevel = effort.coerceAtMost(1f))
