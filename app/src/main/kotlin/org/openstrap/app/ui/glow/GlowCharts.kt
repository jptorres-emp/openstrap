package org.openstrap.app.ui.glow

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.openstrap.app.ui.components.scrub
import org.openstrap.app.ui.components.smoothPath
import org.openstrap.app.ui.theme.FunnelSans
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Charts for glow cards. The card's colour carries the mood, so the marks are white: lines get a soft
 * halo, guides are rows of dots, and bars and rings are built from LED-style dots like the numerals.
 */

private val Ink = Color.White
private val Faint = Color.White.copy(alpha = 0.16f)
private val Muted = Color.White.copy(alpha = 0.55f)

private val axisText = TextStyle(color = Muted, fontSize = 10.sp, fontFamily = FunnelSans)
private val valueText = TextStyle(color = Ink, fontSize = 11.sp, fontFamily = FunnelSans)
private val readoutText = TextStyle(color = Ink, fontSize = 12.sp, fontFamily = FunnelSans)

/** A horizontal row of dots, the glow language's grid line. */
private fun DrawScope.dottedLine(y: Float, color: Color = Faint, gap: Dp = 6.dp, x0: Float = 0f, x1: Float = size.width) =
    drawLine(
        color, Offset(x0, y), Offset(x1, y), strokeWidth = 1.6.dp.toPx(), cap = StrokeCap.Round,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(0f, gap.toPx())),
    )

private fun DrawScope.text(m: TextMeasurer, s: String, style: TextStyle, x: Float, y: Float, align: Float = 0f) {
    val r = m.measure(s, style)
    drawText(r, topLeft = Offset((x - r.size.width * align).coerceIn(0f, size.width - r.size.width), y))
}

/** The touch readout: a dark glass pill with white text, above [anchorY] when there's room, else below. */
private fun DrawScope.readout(m: TextMeasurer, s: String, x: Float, anchorY: Float) {
    val r = m.measure(s, readoutText)
    val bw = r.size.width + 18.dp.toPx()
    val bh = r.size.height + 10.dp.toPx()
    val bx = (x - bw / 2).coerceIn(0f, (size.width - bw).coerceAtLeast(0f))
    val by = (anchorY - bh - 12.dp.toPx()).let { if (it < 0f) anchorY + 12.dp.toPx() else it }
    drawRoundRect(Color.Black.copy(alpha = 0.72f), Offset(bx, by), Size(bw, bh), CornerRadius(bh / 2))
    drawRoundRect(Ink.copy(alpha = 0.18f), Offset(bx, by), Size(bw, bh), CornerRadius(bh / 2), style = Stroke(1.dp.toPx()))
    drawText(r, topLeft = Offset(bx + 9.dp.toPx(), by + 5.dp.toPx()))
}

/** A vertical guide made of dots, under the finger. */
private fun DrawScope.guide(x: Float, bottom: Float) = drawLine(
    Ink.copy(alpha = 0.6f), Offset(x, 0f), Offset(x, bottom), strokeWidth = 1.6.dp.toPx(), cap = StrokeCap.Round,
    pathEffect = PathEffect.dashPathEffect(floatArrayOf(0f, 4.dp.toPx())),
)

/** Bottom axis labels spread evenly from the left edge to the right edge. */
private fun DrawScope.axis(m: TextMeasurer, labels: List<String>, y: Float) {
    if (labels.isEmpty()) return
    labels.forEachIndexed { i, l ->
        val f = if (labels.size == 1) 0f else i / (labels.size - 1f)
        text(m, l, axisText, size.width * f, y, align = f)
    }
}

/* ------------------------------------------------------------------------------------------------ */

/**
 * A day of readings as one smooth white line with a faint halo, a soft fill, dotted guides, a dotted average
 * and a glowing dot on the latest value. [values] are evenly spaced; nulls break the line.
 */
@Composable
fun GlowLineChart(
    values: List<Float?>,
    modifier: Modifier = Modifier,
    height: Dp = 120.dp,
    labels: List<String> = emptyList(),
    showAverage: Boolean = true,
    /** The line's colour; white suits glow cards, a metric colour can suit a black panel. */
    color: Color = Ink,
    /** A fixed value axis (e.g. 0..100 for scores); null fits the data. */
    yRange: ClosedFloatingPointRange<Float>? = null,
    /** A usual range drawn as a faint band between two dotted lines (e.g. your normal). */
    band: ClosedFloatingPointRange<Float>? = null,
    /** How the average label prints a value. */
    format: (Float) -> String = { it.roundToInt().toString() },
    /** Text for the touch readout at a reading; null turns scrubbing off. */
    describe: ((index: Int, value: Float) -> String)? = null,
) {
    val m = rememberTextMeasurer()
    var touchX by remember { mutableStateOf<Float?>(null) }
    Canvas(modifier.fillMaxWidth().height(height).let { if (describe != null) it.scrub(values) { x -> touchX = x } else it }) {
        val known = values.filterNotNull()
        if (known.isEmpty()) return@Canvas
        val axisH = if (labels.isEmpty()) 0f else 18.dp.toPx()
        val top = 10.dp.toPx()
        val h = size.height - axisH
        val lo = yRange?.start ?: minOf(known.min(), band?.start ?: Float.MAX_VALUE)
        val hi = (yRange?.endInclusive ?: maxOf(known.max(), band?.endInclusive ?: -Float.MAX_VALUE)).let { if (it - lo < 1e-3f) lo + 1f else it }
        // Inset by the end dot's halo, so a reading at the very edge isn't cut off.
        val inset = 7.dp.toPx()
        fun xOf(i: Int) = inset + i / (values.size - 1f).coerceAtLeast(1f) * (size.width - inset * 2)
        fun yOf(v: Float) = top + (h - top) * (1f - (v - lo) / (hi - lo))

        listOf(0f, 0.5f, 1f).forEach { dottedLine(top + (h - top) * it) }
        band?.let { b ->
            val y0 = yOf(b.endInclusive)
            val y1 = yOf(b.start)
            drawRect(color.copy(alpha = 0.10f), Offset(0f, y0), Size(size.width, y1 - y0))
            dottedLine(y0, color.copy(alpha = 0.45f), gap = 4.dp)
            dottedLine(y1, color.copy(alpha = 0.45f), gap = 4.dp)
        }

        // Runs of consecutive readings; a gap starts a new run.
        val runs = mutableListOf<MutableList<Offset>>(mutableListOf())
        values.forEachIndexed { i, v ->
            if (v == null) { if (runs.last().isNotEmpty()) runs += mutableListOf<Offset>() } else runs.last() += Offset(xOf(i), yOf(v))
        }
        // A lone reading between gaps is a dot, so a sparse week still shows its days.
        runs.filter { it.size == 1 }.forEach { drawCircle(color, 2.6.dp.toPx(), it.first()) }
        runs.filter { it.size >= 2 }.forEach { pts ->
            val line = smoothPath(pts)
            val fill = Path().apply { addPath(line); lineTo(pts.last().x, h); lineTo(pts.first().x, h); close() }
            drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.14f), Color.Transparent), startY = top, endY = h))
            // A faint halo just wider than the line.
            drawPath(line, color.copy(alpha = 0.08f), style = Stroke(4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(line, color, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        if (showAverage) {
            val avg = known.average().toFloat()
            val y = yOf(avg)
            dottedLine(y, Ink.copy(alpha = 0.5f), gap = 4.dp)
            text(m, "avg ${format(avg)}", axisText.copy(color = Ink.copy(alpha = 0.8f)), size.width, y - 16.dp.toPx(), align = 1f)
        }

        val lastIndex = values.indexOfLast { it != null }
        val last = Offset(xOf(lastIndex), yOf(values[lastIndex]!!))
        drawCircle(color.copy(alpha = 0.2f), 6.5.dp.toPx(), last)
        drawCircle(color, 3.5.dp.toPx(), last)

        axis(m, labels, h + 4.dp.toPx())

        // Scrubbing: the reading nearest the finger (gaps are skipped, not bridged).
        val tx = touchX
        if (describe != null && tx != null) {
            val near = ((tx - inset) / (size.width - inset * 2) * (values.size - 1)).roundToInt().coerceIn(0, values.size - 1)
            val i = (0..values.size).asSequence().flatMap { d -> sequenceOf(near - d, near + d) }
                .firstOrNull { it in values.indices && values[it] != null && kotlin.math.abs(it - near) <= values.size / 48 + 1 }
            if (i != null) {
                val p = Offset(xOf(i), yOf(values[i]!!))
                guide(p.x, h)
                drawCircle(Color.Black.copy(alpha = 0.6f), 6.dp.toPx(), p)
                drawCircle(color, 4.dp.toPx(), p)
                readout(m, describe(i, values[i]!!), p.x, p.y)
            }
        }
    }
}

/* ------------------------------------------------------------------------------------------------ */

/**
 * Bars made of dots, like an LED meter (e.g. steps per hour). Each column has [rows] dots; the lit ones
 * show the value. The [highlight] column (default: the last one with data) is drawn in [accent].
 */
@Composable
fun GlowDotBars(
    values: List<Float>,
    modifier: Modifier = Modifier,
    height: Dp = 96.dp,
    rows: Int = 10,
    labels: List<String> = emptyList(),
    highlight: Int = values.indexOfLast { it > 0f },
    accent: Color = Ink,
    /** Text for the touch readout on a column; null turns scrubbing off. */
    describe: ((index: Int, value: Float) -> String)? = null,
) {
    val m = rememberTextMeasurer()
    var touchX by remember { mutableStateOf<Float?>(null) }
    Canvas(modifier.fillMaxWidth().height(height).let { if (describe != null) it.scrub(values) { x -> touchX = x } else it }) {
        if (values.isEmpty()) return@Canvas
        val axisH = if (labels.isEmpty()) 0f else 18.dp.toPx()
        val h = size.height - axisH
        val pitchX = size.width / values.size
        val pitchY = h / rows
        val r = min(pitchX, pitchY) * 0.32f
        val max = values.max().coerceAtLeast(1f)
        val picked = touchX?.takeIf { describe != null }?.let { (it / pitchX).toInt().coerceIn(0, values.size - 1) }
        values.forEachIndexed { i, v ->
            val lit = if (v > 0f) (v / max * rows).roundToInt().coerceIn(1, rows) else 0
            // While scrubbing, the other columns dim so the picked one stands out.
            val dim = if (picked == null || picked == i) 1f else 0.35f
            val cx = pitchX * i + pitchX / 2
            for (row in 0 until rows) {
                val on = row < lit
                val color = when {
                    !on -> Ink.copy(alpha = 0.10f)
                    i == highlight || i == picked -> accent
                    else -> Ink.copy(alpha = 0.55f + 0.45f * (row + 1f) / rows)
                }
                drawCircle(color.copy(alpha = color.alpha * dim), r, Offset(cx, h - pitchY * row - pitchY / 2))
            }
        }
        axis(m, labels, h + 4.dp.toPx())
        if (picked != null && describe != null) readout(m, describe(picked, values[picked]), pitchX * picked + pitchX / 2, 0f)
    }
}

/* ------------------------------------------------------------------------------------------------ */

/** A day on a [GlowTrend]. */
data class GlowDay(val label: String, val value: Float?)

/**
 * A week as dotted stems rising to each day's value, the value printed above. Today (the last day)
 * gets a glowing ring. [average] draws a dotted line across.
 */
@Composable
fun GlowTrend(
    days: List<GlowDay>,
    modifier: Modifier = Modifier,
    height: Dp = 130.dp,
    average: Float? = null,
) {
    val m = rememberTextMeasurer()
    Canvas(modifier.fillMaxWidth().height(height)) {
        if (days.isEmpty()) return@Canvas
        val axisH = 18.dp.toPx()
        val top = 22.dp.toPx()
        val h = size.height - axisH
        val slot = size.width / days.size
        val known = days.mapNotNull { it.value } + listOfNotNull(average)
        val lo = (known.minOrNull() ?: 0f) * 0.85f
        val hi = (known.maxOrNull() ?: 1f).coerceAtLeast(lo + 1f)
        fun yOf(v: Float) = top + (h - top - 6.dp.toPx()) * (1f - (v - lo) / (hi - lo))

        average?.let { dottedLine(yOf(it), Ink.copy(alpha = 0.45f), gap = 4.dp) }
        days.forEachIndexed { i, d ->
            val cx = slot * i + slot / 2
            val today = i == days.lastIndex
            text(m, d.label, if (today) axisText.copy(color = Ink) else axisText, cx, h + 4.dp.toPx(), align = 0.5f)
            val v = d.value
            if (v == null) {
                drawCircle(Faint, 2.5.dp.toPx(), Offset(cx, h - 3.dp.toPx()))
                return@forEachIndexed
            }
            val y = yOf(v)
            drawLine(
                Ink.copy(alpha = if (today) 0.9f else 0.45f), Offset(cx, h - 3.dp.toPx()), Offset(cx, y),
                strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(0f, 5.dp.toPx())),
            )
            if (today) {
                drawCircle(Ink.copy(alpha = 0.18f), 11.dp.toPx(), Offset(cx, y))
                drawCircle(Ink, 6.dp.toPx(), Offset(cx, y))
            } else {
                drawCircle(Ink, 4.dp.toPx(), Offset(cx, y))
            }
            text(m, v.roundToInt().toString(), valueText.copy(color = if (today) Ink else Ink.copy(alpha = 0.75f)), cx, y - 20.dp.toPx(), align = 0.5f)
        }
    }
}

/* ------------------------------------------------------------------------------------------------ */

/**
 * A progress ring made of [dots] dots: lit ones up to [progress], faint ones after, and a glowing dot
 * at the leading edge. [content] sits in the middle (e.g. a [DotMatrixText] score).
 */
@Composable
fun GlowDotRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 150.dp,
    dots: Int = 48,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val r = this.size.minDimension / 2 - 8.dp.toPx()
            val c = center
            val lit = (progress.coerceIn(0f, 1f) * dots).roundToInt()
            for (i in 0 until dots) {
                val a = -PI / 2 + 2 * PI * i / dots
                val o = Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat())
                when {
                    i == lit - 1 -> {
                        drawCircle(Ink.copy(alpha = 0.2f), 9.dp.toPx(), o)
                        drawCircle(Ink, 4.5.dp.toPx(), o)
                    }
                    i < lit -> drawCircle(Ink.copy(alpha = 0.55f + 0.45f * i / lit), 3.dp.toPx(), o)
                    else -> drawCircle(Faint, 2.dp.toPx(), o)
                }
            }
        }
        content()
    }
}

/**
 * The smooth counterpart of [GlowDotRing]: a faint track, a white arc with a faint halo and a
 * dot at its end. Pair it with a dot-matrix number; pair [GlowDotRing] with plain text.
 */
@Composable
fun GlowRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 150.dp,
    stroke: Dp = 7.dp,
    color: Color = Ink,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = stroke.toPx()
            // Room for the end dot and halo, which scale with the stroke.
            val inset = s / 2 + s * 1.2f
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            val sweep = 360f * progress.coerceIn(0f, 1f)
            drawArc(Faint, 0f, 360f, false, topLeft, arcSize, style = Stroke(s))
            if (sweep <= 0f) return@Canvas
            // Rotated so the arc and its fade both start at 12 o'clock (sweep gradients start at 3).
            rotate(-90f) {
                // A faint halo just wider than the arc.
                drawArc(color.copy(alpha = 0.08f), 0f, sweep, false, topLeft, arcSize, style = Stroke(s * 1.7f, cap = StrokeCap.Round))
                drawArc(
                    Brush.sweepGradient(0f to color.copy(alpha = 0.55f), sweep / 360f to color, center = center),
                    0f, sweep, false, topLeft, arcSize, style = Stroke(s, cap = StrokeCap.Round),
                )
            }
            val a = Math.toRadians((sweep - 90f).toDouble())
            val r = arcSize.width / 2
            val end = Offset(center.x + r * cos(a).toFloat(), center.y + r * sin(a).toFloat())
            drawCircle(color.copy(alpha = 0.18f), s * 1.05f, end)
            drawCircle(color, s * 0.6f, end)
        }
        content()
    }
}

/**
 * Nested half arcs, outermost first, drawn like the other glow charts: dotted tracks, a thin white
 * line with a faint halo up to each value, and a glowing dot at its end in the value's colour (the
 * only colour on the chart). The feet stand on a dotted baseline marked 0 and 100.
 */
@Composable
fun GlowHalfArcs(
    arcs: List<Pair<Float, Color>>,
    modifier: Modifier = Modifier,
    height: Dp = 116.dp,
    gap: Dp = 14.dp,
) {
    val m = rememberTextMeasurer()
    Canvas(modifier.fillMaxWidth().height(height)) {
        val axisH = 18.dp.toPx()
        val baseY = size.height - axisH
        val pad = 7.dp.toPx()
        val outer = min(size.width / 2 - pad, baseY - pad)
        val base = Offset(center.x, baseY)
        dottedLine(baseY)
        text(m, "0", axisText, base.x - outer, baseY + 4.dp.toPx(), align = 0.5f)
        text(m, "100", axisText, base.x + outer, baseY + 4.dp.toPx(), align = 0.5f)
        arcs.forEachIndexed { i, (progress, color) ->
            val r = outer - i * gap.toPx()
            if (r <= gap.toPx()) return@forEachIndexed
            val topLeft = Offset(base.x - r, base.y - r)
            val box = Size(r * 2, r * 2)
            drawArc(
                Faint, 180f, 180f, false, topLeft, box,
                style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(0f, 6.dp.toPx()))),
            )
            val sweep = 180f * progress.coerceIn(0f, 1f)
            if (sweep <= 0f) return@forEachIndexed
            // Outer arcs brightest, like the leading line on a chart.
            val ink = Ink.copy(alpha = 1f - 0.2f * i)
            drawArc(ink.copy(alpha = 0.08f), 180f, sweep, false, topLeft, box, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
            drawArc(ink, 180f, sweep, false, topLeft, box, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))
            val a = Math.toRadians((180f + sweep).toDouble())
            val end = Offset(base.x + r * cos(a).toFloat(), base.y + r * sin(a).toFloat())
            drawCircle(color.copy(alpha = 0.25f), 6.5.dp.toPx(), end)
            drawCircle(color, 3.5.dp.toPx(), end)
        }
    }
}

/* ------------------------------------------------------------------------------------------------ */

/** A stretch of sleep on a [GlowHypnogram]: start and end as fractions of the night, and its row. */
data class GlowStage(val from: Float, val to: Float, val row: Int)

/**
 * Sleep stages as white pills on dotted rows (row 0 at the top, e.g. Awake), joined by thin
 * connectors. Deeper rows are drawn brighter so deep sleep stands out on the dark core.
 */
@Composable
fun GlowHypnogram(
    stages: List<GlowStage>,
    rowNames: List<String>,
    modifier: Modifier = Modifier,
    height: Dp = 130.dp,
    labels: List<String> = emptyList(),
    /** Text for the touch readout on a stage; null turns scrubbing off. */
    describe: ((GlowStage) -> String)? = null,
) {
    val m = rememberTextMeasurer()
    var touchX by remember { mutableStateOf<Float?>(null) }
    Canvas(modifier.fillMaxWidth().height(height).let { if (describe != null) it.scrub(stages) { x -> touchX = x } else it }) {
        if (stages.isEmpty()) return@Canvas
        val axisH = if (labels.isEmpty()) 0f else 18.dp.toPx()
        val nameW = 46.dp.toPx()
        val h = size.height - axisH
        val w = size.width - nameW
        val rowH = h / rowNames.size
        val pillH = rowH * 0.46f
        fun yOf(row: Int) = rowH * row + rowH / 2
        fun xOf(f: Float) = nameW + f * w

        rowNames.forEachIndexed { row, name ->
            dottedLine(yOf(row), x0 = nameW)
            text(m, name, axisText, 0f, yOf(row) - 7.dp.toPx())
        }
        val sorted = stages.sortedBy { it.from }
        for (i in 1 until sorted.size) {
            val a = sorted[i - 1]
            val b = sorted[i]
            if (a.row != b.row) drawLine(Ink.copy(alpha = 0.3f), Offset(xOf(b.from), yOf(a.row)), Offset(xOf(b.from), yOf(b.row)), 1.dp.toPx())
        }
        sorted.forEach { s ->
            val alpha = 0.55f + 0.45f * s.row / (rowNames.size - 1).coerceAtLeast(1)
            drawRoundRect(
                Ink.copy(alpha = alpha),
                topLeft = Offset(xOf(s.from), yOf(s.row) - pillH / 2),
                size = Size((xOf(s.to) - xOf(s.from)).coerceAtLeast(pillH), pillH),
                cornerRadius = CornerRadius(pillH / 2),
            )
        }
        if (labels.isNotEmpty()) {
            labels.forEachIndexed { i, l ->
                val f = if (labels.size == 1) 0f else i / (labels.size - 1f)
                text(m, l, axisText, xOf(f), h + 4.dp.toPx(), align = f)
            }
        }
        val tx = touchX
        if (describe != null && tx != null) {
            val x = tx.coerceIn(nameW, size.width)
            val f = (x - nameW) / w
            // The stage under the finger, or the nearest one when the finger is in a gap.
            val s = sorted.firstOrNull { f >= it.from && f < it.to } ?: sorted.minBy { minOf(kotlin.math.abs(it.from - f), kotlin.math.abs(it.to - f)) }
            guide(x, h)
            readout(m, describe(s), x, yOf(s.row) - pillH / 2)
        }
    }
}

/* ------------------------------------------------------------------------------------------------ */

/**
 * The score as a wide, shallow arc across the card, like the classic sleep hero: a dotted track, ticks above it,
 * a white fill with a soft halo up to the score, and a knob glowing in [color] where it lands. The fill sweeps in
 * when the card appears.
 */
@Composable
fun GlowHorizon(progress: Float?, color: Color, modifier: Modifier = Modifier, height: Dp = 72.dp) {
    val m = rememberTextMeasurer()
    val target = progress?.coerceIn(0f, 1f) ?: 0f
    val anim = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(target) {
        anim.animateTo(target, androidx.compose.animation.core.tween(700, easing = androidx.compose.animation.core.FastOutSlowInEasing))
    }
    Canvas(modifier.fillMaxWidth().height(height)) {
        val sweep = 84f
        val start = 270f - sweep / 2
        val chord = size.width - 28.dp.toPx()
        val r = chord / 2f / sin(Math.toRadians(sweep / 2.0)).toFloat()
        val sag = r * (1f - cos(Math.toRadians(sweep / 2.0)).toFloat())
        val top = 14.dp.toPx()
        val c = Offset(size.width / 2f, top + r)
        val topLeft = Offset(c.x - r, c.y - r)
        val box = Size(r * 2, r * 2)
        fun at(f: Float, radius: Float = r): Offset {
            val a = Math.toRadians((start + sweep * f).toDouble())
            return Offset(c.x + radius * cos(a).toFloat(), c.y + radius * sin(a).toFloat())
        }
        drawArc(
            Faint, start, sweep, false, topLeft, box,
            style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(0f, 6.dp.toPx()))),
        )
        for (i in 0..4) drawCircle(Muted, 1.5.dp.toPx(), at(i / 4f, r + 9.dp.toPx()))
        if (progress != null) {
            val p = anim.value
            if (p > 0.001f) {
                drawArc(Ink.copy(alpha = 0.10f), start, sweep * p, false, topLeft, box, style = Stroke(9.dp.toPx(), cap = StrokeCap.Round))
                drawArc(Ink, start, sweep * p, false, topLeft, box, style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round))
            }
            val k = at(p)
            drawCircle(color.copy(alpha = 0.30f), 11.dp.toPx(), k)
            drawCircle(color, 6.5.dp.toPx(), k)
            drawCircle(Ink, 2.6.dp.toPx(), k)
        }
        val labelY = top + sag + 8.dp.toPx()
        text(m, "0", axisText, at(0f).x, labelY, align = 0.5f)
        text(m, "100", axisText, at(1f).x, labelY, align = 0.5f)
    }
}
