package org.openstrap.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.floor

/** A point on a time axis: x is epoch seconds, y is the value. */
data class ChartPoint(val x: Long, val y: Float)

private val HOUR_FMT = DateTimeFormatter.ofPattern("HH:mm")
private fun hourLabel(epoch: Long) = Instant.ofEpochSecond(epoch).atZone(ZoneId.systemDefault()).format(HOUR_FMT)

@Composable
private fun axisStyle() = TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontFamily = org.openstrap.app.ui.theme.FunnelSans)

private fun DrawScope.label(m: TextMeasurer, text: String, style: TextStyle, x: Float, y: Float, alignEnd: Boolean = false, center: Boolean = false) {
    val layout = m.measure(text, style)
    val dx = when {
        alignEnd -> -layout.size.width.toFloat()
        center -> -layout.size.width / 2f
        else -> 0f
    }
    drawText(layout, topLeft = Offset((x + dx).coerceIn(0f, size.width - layout.size.width), y))
}

/**
 * Time-series line with a gradient fill, light grid with y labels on the right, time labels underneath,
 * an optional dotted average line, a marker at the minimum, and an optional shaded band (e.g. sleep).
 * Gaps longer than [gapSeconds] break the line so off-wrist periods aren't bridged.
 */
@Composable
fun LineChart(
    points: List<ChartPoint>,
    color: Color,
    xRange: LongRange,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
    gapSeconds: Long = 15 * 60,
    showAverage: Boolean = true,
    markMin: Boolean = false,
    band: LongRange? = null,
    bandColor: Color = Color.Unspecified,
) {
    val measurer = rememberTextMeasurer()
    val style = axisStyle()
    val grid = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surfaceContainer
    val bandFill = if (bandColor == Color.Unspecified) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else bandColor
    Canvas(modifier.fillMaxWidth().height(height)) {
        if (xRange.last <= xRange.first) return@Canvas
        val axisW = 28.dp.toPx()
        val axisH = 18.dp.toPx()
        val w = size.width - axisW
        val h = size.height - axisH

        val minV = points.minOfOrNull { it.y } ?: 0f
        val maxV = points.maxOfOrNull { it.y } ?: 1f
        val step = niceStep((maxV - minV).coerceAtLeast(4f) / 3f)
        val bottom = floor(minV / step) * step
        val top = (ceil(maxV / step) * step).coerceAtLeast(bottom + step)
        fun xOf(t: Long) = (t - xRange.first).toFloat() / (xRange.last - xRange.first) * w
        fun yOf(v: Float) = h - (v - bottom) / (top - bottom) * h

        band?.let {
            val x0 = xOf(it.first).coerceIn(0f, w)
            val x1 = xOf(it.last).coerceIn(0f, w)
            if (x1 > x0) drawRect(bandFill, Offset(x0, 0f), Size(x1 - x0, h))
        }

        var v = bottom
        while (v <= top + 0.001f) {
            val y = yOf(v)
            drawLine(grid, Offset(0f, y), Offset(w, y), 1.dp.toPx())
            label(measurer, v.toInt().toString(), style, size.width, y - 7.sp.toPx(), alignEnd = true)
            v += step
        }
        // Inclusive ranges like 00:00..23:59:59 label as 00:00, 06:00, … rather than 05:59.
        val span = (xRange.last - xRange.first + 1)
        for (i in 0..4) {
            val t = (xRange.first + span * i / 4).coerceAtMost(xRange.last)
            label(measurer, hourLabel(t), style, xOf(t), h + 4.dp.toPx(), center = i in 1..3, alignEnd = i == 4)
        }
        if (points.isEmpty()) return@Canvas

        val segments = mutableListOf(mutableListOf(points.first()))
        for (i in 1 until points.size) {
            if (points[i].x - points[i - 1].x > gapSeconds) segments += mutableListOf<ChartPoint>()
            segments.last() += points[i]
        }
        for (segment in segments) {
            if (segment.size < 2) {
                // A lone reading (e.g. a spot SpO2) gets a ringed dot so it's visible on its own.
                val o = Offset(xOf(segment[0].x), yOf(segment[0].y))
                drawCircle(color, 5.dp.toPx(), o)
                drawCircle(surface, 2.dp.toPx(), o)
                continue
            }
            val line = Path()
            segment.forEachIndexed { i, p ->
                if (i == 0) line.moveTo(xOf(p.x), yOf(p.y)) else line.lineTo(xOf(p.x), yOf(p.y))
            }
            val fill = Path().apply {
                addPath(line)
                lineTo(xOf(segment.last().x), h)
                lineTo(xOf(segment.first().x), h)
                close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), Color.Transparent), endY = h))
            drawPath(line, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        if (showAverage) {
            val avgY = yOf(points.map { it.y }.average().toFloat())
            drawLine(
                color.copy(alpha = 0.7f), Offset(0f, avgY), Offset(w, avgY), 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())),
            )
        }
        if (markMin) {
            val p = points.minBy { it.y }
            val o = Offset(xOf(p.x), yOf(p.y))
            drawCircle(color, 6.dp.toPx(), o)
            drawCircle(surface, 3.dp.toPx(), o)
        }
    }
}

private fun niceStep(raw: Float): Float {
    val candidates = floatArrayOf(1f, 2f, 5f, 10f, 20f, 25f, 50f, 100f)
    return candidates.firstOrNull { it >= raw } ?: raw
}

/** A day on a weekly trend chart. */
data class TrendPoint(val label: String, val value: Float?)

/** WHOOP-style 7-day trend: dots joined by a line, value printed above each point, last point highlighted. */
@Composable
fun TrendChart(points: List<TrendPoint>, color: Color, modifier: Modifier = Modifier, height: Dp = 150.dp) {
    val measurer = rememberTextMeasurer()
    val axis = axisStyle()
    val valueStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontFamily = org.openstrap.app.ui.theme.FunnelDisplay)
    val surface = MaterialTheme.colorScheme.surfaceContainer
    val highlight = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(modifier.fillMaxWidth().height(height)) {
        if (points.isEmpty()) return@Canvas
        val axisH = 18.dp.toPx()
        val topPad = 20.dp.toPx()
        val h = size.height - axisH
        val slot = size.width / points.size
        val values = points.mapNotNull { it.value }
        val lo = (values.minOrNull() ?: 0f) - 2f
        val hi = (values.maxOrNull() ?: 1f) + 2f
        fun yOf(v: Float) = topPad + (h - topPad) * (1 - (v - lo) / (hi - lo).coerceAtLeast(1f))

        drawRoundRect(highlight, Offset(slot * (points.size - 1), 0f), Size(slot, size.height), CornerRadius(10.dp.toPx()))

        var prev: Offset? = null
        points.forEachIndexed { i, p ->
            val cx = slot * i + slot / 2
            label(measurer, p.label, axis, cx, h + 4.dp.toPx(), center = true)
            val v = p.value ?: run { prev = null; return@forEachIndexed }
            val o = Offset(cx, yOf(v))
            prev?.let { drawLine(color, it, o, 2.dp.toPx(), cap = StrokeCap.Round) }
            prev = o
        }
        points.forEachIndexed { i, p ->
            val v = p.value ?: return@forEachIndexed
            val o = Offset(slot * i + slot / 2, yOf(v))
            drawCircle(color, 5.dp.toPx(), o)
            drawCircle(surface, 2.5.dp.toPx(), o)
            label(measurer, v.toInt().toString(), valueStyle, o.x, o.y - 20.dp.toPx(), center = true)
        }
    }
}

/** Evenly spaced rounded bars, e.g. steps per hour. */
@Composable
fun BarChart(values: List<Float>, color: Color, modifier: Modifier = Modifier, height: Dp = 64.dp) {
    Canvas(modifier.fillMaxWidth().height(height)) {
        if (values.isEmpty()) return@Canvas
        val max = values.max().coerceAtLeast(1f)
        val slot = size.width / values.size
        val barWidth = slot * 0.55f
        values.forEachIndexed { i, v ->
            val h = ((v / max) * size.height).coerceAtLeast(3.dp.toPx())
            drawRoundRect(
                color = if (v > 0f) color else color.copy(alpha = 0.12f),
                topLeft = Offset(i * slot + (slot - barWidth) / 2, size.height - h),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2),
            )
        }
    }
}

/** A sleep stage interval for the hypnogram. */
data class StageSpan(val start: Long, val end: Long, val stage: Int)

/**
 * Stepped hypnogram (Oura/Bevel style): one row per stage, rounded blocks joined by thin vertical
 * connectors in a gradient between the two stage colours, with start/end times underneath.
 */
@Composable
fun Hypnogram(
    spans: List<StageSpan>,
    stageColors: Map<Int, Color>,
    rowForStage: Map<Int, Int>,
    modifier: Modifier = Modifier,
    height: Dp = 170.dp,
) {
    val measurer = rememberTextMeasurer()
    val style = axisStyle()
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.fillMaxWidth().height(height)) {
        if (spans.isEmpty()) return@Canvas
        val sorted = spans.sortedBy { it.start }
        val start = sorted.first().start
        val end = sorted.maxOf { it.end }
        if (end <= start) return@Canvas
        val axisH = 18.dp.toPx()
        val h = size.height - axisH
        val rows = rowForStage.values.max() + 1
        val rowH = h / rows
        val blockH = rowH * 0.5f
        fun xOf(t: Long) = (t - start).toFloat() / (end - start) * size.width
        fun centerY(stage: Int) = (rowForStage[stage] ?: 0) * rowH + rowH / 2

        for (r in 0 until rows) {
            val y = r * rowH + rowH / 2
            drawLine(grid, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 4.dp.toPx())))
        }
        for (i in 1 until sorted.size) {
            val a = sorted[i - 1]
            val b = sorted[i]
            if (b.start - a.end > 120) continue
            val x = xOf(b.start)
            val ya = centerY(a.stage)
            val yb = centerY(b.stage)
            if (ya == yb) continue
            drawLine(
                Brush.verticalGradient(listOf(stageColors[a.stage] ?: Color.Gray, stageColors[b.stage] ?: Color.Gray), startY = ya, endY = yb),
                Offset(x, ya), Offset(x, yb), 1.5.dp.toPx(),
            )
        }
        for (s in sorted) {
            val x0 = xOf(s.start)
            val x1 = xOf(s.end)
            drawRoundRect(
                color = stageColors[s.stage] ?: Color.Gray,
                topLeft = Offset(x0, centerY(s.stage) - blockH / 2),
                size = Size((x1 - x0).coerceAtLeast(2.dp.toPx()), blockH),
                cornerRadius = CornerRadius(3.dp.toPx()),
            )
        }
        label(measurer, hourLabel(start), style, 0f, h + 4.dp.toPx())
        label(measurer, hourLabel(end), style, size.width, h + 4.dp.toPx(), alignEnd = true)
    }
}

/** Circular progress ring with a faint track and a round cap. */
@Composable
fun ProgressRing(progress: Float, color: Color, modifier: Modifier = Modifier, size: Dp = 120.dp, stroke: Dp = 12.dp) {
    Canvas(modifier.size(size)) {
        val s = stroke.toPx()
        val arcSize = Size(this.size.width - s, this.size.height - s)
        val topLeft = Offset(s / 2, s / 2)
        drawArc(color.copy(alpha = 0.14f), 0f, 360f, false, topLeft, arcSize, style = Stroke(s))
        val sweep = 360f * progress.coerceIn(0f, 1f)
        if (sweep > 0f) {
            drawArc(color, -90f, sweep, false, topLeft, arcSize, style = Stroke(s, cap = StrokeCap.Round))
        }
    }
}
