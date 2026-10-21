package org.openstrap.app.ui.glow

import androidx.compose.material3.ripple
import androidx.compose.ui.draw.clip
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/* ------------------------------------------------------------------------------------------------ */
/* Dot-matrix numerals                                                                               */
/* ------------------------------------------------------------------------------------------------ */

/** 5×7 glyphs, one string per row. Anything not listed draws as a space. */
private val GLYPHS: Map<Char, List<String>> = mapOf(
    '0' to listOf(".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."),
    '1' to listOf("..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###."),
    '2' to listOf(".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"),
    '3' to listOf("####.", "....#", "....#", ".###.", "....#", "....#", "####."),
    '4' to listOf("...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#."),
    '5' to listOf("#####", "#....", "####.", "....#", "....#", "#...#", ".###."),
    '6' to listOf("..##.", ".#...", "#....", "####.", "#...#", "#...#", ".###."),
    '7' to listOf("#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#..."),
    '8' to listOf(".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###."),
    '9' to listOf(".###.", "#...#", "#...#", ".####", "....#", "...#.", ".##.."),
    '.' to listOf(".", ".", ".", ".", ".", ".", "#"),
    ',' to listOf(".", ".", ".", ".", ".", "#", "#"),
    ':' to listOf(".", ".", "#", ".", ".", "#", "."),
    '-' to listOf("...", "...", "...", "###", "...", "...", "..."),
    'k' to listOf("#....", "#....", "#..#.", "#.#..", "##...", "#.#..", "#..#."),
    '%' to listOf("##...", "##..#", "...#.", "..#..", ".#...", "#..##", "...##"),
    ' ' to listOf("..", "..", "..", "..", "..", "..", ".."),
)
private const val ROWS = 7

/**
 * A number drawn as round dots, like an LED matrix (the "3.3" on the reference cards). [dot] is the
 * dot diameter; the grid pitch is dot + [gap]. Digits, `.`, `,`, `:`, `-` and `%` are supported.
 */
@Composable
fun DotMatrixText(
    text: String,
    modifier: Modifier = Modifier,
    dot: Dp = 5.dp,
    gap: Dp = 2.dp,
    color: Color = Color.White,
) {
    val glyphs = text.map { GLYPHS[it] ?: GLYPHS.getValue(' ') }
    // One empty column between glyphs.
    val columns = glyphs.sumOf { it[0].length } + (glyphs.size - 1).coerceAtLeast(0)
    val pitch = dot + gap
    Canvas(
        modifier
            .size(pitch * columns - gap, pitch * ROWS - gap)
            .semantics { contentDescription = text },
    ) {
        val d = dot.toPx()
        val step = pitch.toPx()
        var x0 = 0f
        glyphs.forEach { rows ->
            rows.forEachIndexed { row, line ->
                line.forEachIndexed { col, c ->
                    if (c == '#') drawCircle(color, d / 2, Offset(x0 + col * step + d / 2, row * step + d / 2))
                }
            }
            x0 += (rows[0].length + 1) * step
        }
    }
}

/* ------------------------------------------------------------------------------------------------ */
/* Decorations                                                                                       */
/* ------------------------------------------------------------------------------------------------ */

private fun DrawScope.dotted(gap: Dp, width: Dp) = Stroke(
    width = width.toPx(),
    cap = StrokeCap.Round,
    pathEffect = PathEffect.dashPathEffect(floatArrayOf(0f, gap.toPx())),
)

/** A marker on an [ArcTimeline]: a position along the line (0..1) and its colour. */
data class ArcMark(val at: Float, val color: Color)

/**
 * A dotted baseline with arcs bridging consecutive [arcs] marks ("Breath Volume"). Marks listed in
 * [marks] are drawn as coloured dots; [arcs] are pairs of positions (0..1) to bridge.
 */
@Composable
fun ArcTimeline(
    marks: List<ArcMark>,
    arcs: List<Pair<Float, Float>>,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
) {
    Canvas(modifier) {
        val y = size.height - 6.dp.toPx()
        drawLine(
            color.copy(alpha = 0.65f), Offset(0f, y), Offset(size.width, y),
            strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(0f, 9.dp.toPx())),
        )
        arcs.forEachIndexed { i, (a, b) ->
            val left = a * size.width
            val diameter = (b - a) * size.width
            drawArc(
                color.copy(alpha = if (i == arcs.lastIndex) 1f else 0.65f),
                startAngle = 180f, sweepAngle = 180f, useCenter = false,
                topLeft = Offset(left, y - diameter / 2), size = androidx.compose.ui.geometry.Size(diameter, diameter),
                style = Stroke(1.5.dp.toPx()),
            )
        }
        marks.forEach { drawCircle(it.color, 4.dp.toPx(), Offset(it.at * size.width, y)) }
    }
}

/* ------------------------------------------------------------------------------------------------ */
/* Switch                                                                                            */
/* ------------------------------------------------------------------------------------------------ */

/**
 * The pill switch, monochrome like the workout buttons: white with a black knob when on, dim grey when off.
 * Inside a glow card, pass the card's colour as [accent]: on, it's a deeper shade of that colour with a white knob.
 */
@Composable
fun GlowSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, accent: Color? = null) {
    val onTrack = accent?.let { lerp(it, Color.Black, 0.25f) } ?: Color.White
    val track by animateColorAsState(if (checked) onTrack else Color.White.copy(alpha = 0.12f), label = "glow switch track")
    val knobColor by animateColorAsState(
        if (!checked) Color.White.copy(alpha = 0.6f) else if (accent != null) Color.White else Color.Black,
        label = "glow switch knob colour",
    )
    val knob by animateDpAsState(if (checked) 26.dp else 0.dp, spring(dampingRatio = 0.6f, stiffness = 500f), label = "glow switch knob")
    Box(
        modifier
            .size(60.dp, 34.dp)
            .alpha(if (enabled) 1f else 0.4f)
            // Clipped so the press ripple stays inside the pill.
            .clip(CircleShape)
            .background(track)
            .toggleable(checked, enabled = enabled, interactionSource = null, indication = ripple(color = Color.Gray), role = Role.Switch, onValueChange = onCheckedChange)
            .padding(3.dp),
    ) {
        Box(
            Modifier
                .offset(x = knob)
                .size(28.dp)
                .background(knobColor, CircleShape),
        )
    }
}
