package org.openstrap.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
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
import androidx.compose.ui.graphics.drawscope.clipRect
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
 * Scrubbing for every chart: drag sideways to read values straight away, or press and hold first (the
 * older gesture). Vertical drags still scroll the list and a plain tap still reaches the card.
 */
private fun Modifier.scrub(key: Any?, onScrub: (Float?) -> Unit): Modifier = this
    .pointerInput(key) {
        detectHorizontalDragGestures(
            onDragStart = { onScrub(it.x) },
            onDragEnd = { onScrub(null) },
            onDragCancel = { onScrub(null) },
        ) { change, _ -> change.consume(); onScrub(change.position.x) }
    }
    .pointerInput(key) {
        detectDragGesturesAfterLongPress(
            onDragStart = { onScrub(it.x) },
            onDrag = { change, _ -> change.consume(); onScrub(change.position.x) },
            onDragEnd = { onScrub(null) },
            onDragCancel = { onScrub(null) },
        )
    }

/** "62 bpm", "97%", "36.4°C", or the bare value when there's no unit. */
fun withUnit(value: String, unit: String) = when {
    unit.isEmpty() -> value
    unit.startsWith("%") || unit.startsWith("°") -> value + unit
    else -> "$value $unit"
}

/** The touch readout: a pill with [text] centred over [x], above [anchorY] when there's room, else below. */
private fun DrawScope.readout(m: TextMeasurer, text: String, style: TextStyle, bubble: Color, x: Float, anchorY: Float) {
    val layout = m.measure(text, style)
    val bw = layout.size.width + 16.dp.toPx()
    val bh = layout.size.height + 8.dp.toPx()
    val bx = (x - bw / 2).coerceIn(0f, (size.width - bw).coerceAtLeast(0f))
    val by = (anchorY - bh - 12.dp.toPx()).let { if (it < 0f) anchorY + 12.dp.toPx() else it }
    drawRoundRect(bubble, Offset(bx, by), Size(bw, bh), CornerRadius(bh / 2))
    drawText(layout, topLeft = Offset(bx + 8.dp.toPx(), by + 4.dp.toPx()))
}

@Composable
private fun readoutStyle() = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontFamily = org.openstrap.app.ui.theme.FunnelDisplay)

/** A coloured stretch of time drawn as a strip under a [LineChart] (e.g. what Charge was doing). */
data class ChartSegment(val from: Long, val to: Long, val color: Color)

/**
 * Time-series chart in the Oura/WHOOP style: a smooth (monotone, never overshooting) line over a soft
 * gradient, faint dashed grid with values on the right, a haloed dot at the latest reading and a
 * labelled average. Drag across it to read any point (see [scrub]). Gaps longer than [gapSeconds] break the line so
 * off-wrist periods aren't bridged. [segments] adds a Garmin-style state strip under the plot.
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
    /** 0..1: how much of the line is drawn, left to right (for the focus animation). */
    reveal: Float = 1f,
    /** A fixed value axis (e.g. 0..100 for scores); null fits the data. */
    yRange: ClosedFloatingPointRange<Float>? = null,
    segments: List<ChartSegment> = emptyList(),
    /** Formats a value for the average label and the touch readout. */
    format: (Float) -> String = { Math.round(it).toString() },
    /** Shown after the value in the touch readout, e.g. "bpm" or "%". */
    unit: String = "",
) {
    val measurer = rememberTextMeasurer()
    val style = axisStyle()
    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
    val surface = MaterialTheme.colorScheme.surfaceContainer
    val onSurface = MaterialTheme.colorScheme.onSurface
    val bubble = MaterialTheme.colorScheme.surfaceContainerHighest
    val readoutStyle = readoutStyle()
    val bandFill = if (bandColor == Color.Unspecified) MaterialTheme.colorScheme.primary.copy(alpha = 0.07f) else bandColor
    var touchX by remember { mutableStateOf<Float?>(null) }
    val stripH = if (segments.isEmpty()) 0.dp else 8.dp

    Canvas(
        modifier.fillMaxWidth().height(height + stripH)
            .scrub(points) { touchX = it },
    ) {
        if (xRange.last <= xRange.first) return@Canvas
        val axisW = 28.dp.toPx()
        val axisH = 18.dp.toPx()
        val strip = stripH.toPx()
        val w = size.width - axisW
        val h = size.height - axisH - strip
        val minV = yRange?.start ?: points.minOfOrNull { it.y } ?: 0f
        val maxV = yRange?.endInclusive ?: points.maxOfOrNull { it.y } ?: 1f
        val step = niceStep((maxV - minV).coerceAtLeast(4f) / (if (yRange != null) 4f else 3f))
        val bottom = floor(minV / step) * step
        val top = (ceil(maxV / step) * step).coerceAtLeast(bottom + step)
        fun xOf(t: Long) = (t - xRange.first).toFloat() / (xRange.last - xRange.first) * w
        fun yOf(v: Float) = h - (v - bottom) / (top - bottom) * h

        band?.let {
            val x0 = xOf(it.first).coerceIn(0f, w)
            val x1 = xOf(it.last).coerceIn(0f, w)
            if (x1 > x0) drawRoundRect(bandFill, Offset(x0, 0f), Size(x1 - x0, h), CornerRadius(6.dp.toPx()))
        }

        val dash = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 5.dp.toPx()))
        var v = bottom
        while (v <= top + 0.001f) {
            val y = yOf(v)
            drawLine(grid, Offset(0f, y), Offset(w, y), 1.dp.toPx(), pathEffect = dash)
            label(measurer, v.toInt().toString(), style, size.width, y - 7.sp.toPx(), alignEnd = true)
            v += step
        }
        // Inclusive ranges like 00:00..23:59:59 label as 00:00, 06:00, … rather than 05:59.
        val span = (xRange.last - xRange.first + 1)
        for (i in 0..4) {
            val t = (xRange.first + span * i / 4).coerceAtMost(xRange.last)
            label(measurer, hourLabel(t), style, xOf(t), h + strip + 4.dp.toPx(), center = i in 1..3, alignEnd = i == 4)
        }

        // State strip: rounded runs of colour under the plot.
        if (segments.isNotEmpty()) {
            val y0 = h + 3.dp.toPx()
            val sh = strip - 3.dp.toPx()
            drawRoundRect(grid.copy(alpha = 0.25f), Offset(0f, y0), Size(w, sh), CornerRadius(sh / 2))
            for (sgm in segments) {
                val x0 = xOf(sgm.from).coerceIn(0f, w)
                val x1 = xOf(sgm.to).coerceIn(0f, w)
                if (x1 - x0 >= 1f) drawRoundRect(sgm.color, Offset(x0, y0), Size(x1 - x0, sh), CornerRadius(sh / 2))
            }
        }
        if (points.isEmpty()) return@Canvas

        val runs = mutableListOf(mutableListOf(points.first()))
        for (i in 1 until points.size) {
            if (points[i].x - points[i - 1].x > gapSeconds) runs += mutableListOf<ChartPoint>()
            runs.last() += points[i]
        }
        clipRect(right = w * reveal.coerceIn(0f, 1f)) {
            for (run in runs) {
                if (run.size < 2) {
                    // A lone reading (e.g. a spot SpO2) gets a ringed dot so it's visible on its own.
                    val o = Offset(xOf(run[0].x), yOf(run[0].y))
                    drawCircle(color, 5.dp.toPx(), o)
                    drawCircle(surface, 2.dp.toPx(), o)
                    continue
                }
                val pts = run.map { Offset(xOf(it.x), yOf(it.y)) }
                val line = smoothPath(pts)
                val fill = Path().apply {
                    addPath(line)
                    lineTo(pts.last().x, h)
                    lineTo(pts.first().x, h)
                    close()
                }
                drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.30f), color.copy(alpha = 0.02f)), startY = 0f, endY = h))
                drawPath(line, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }

            if (showAverage) {
                val avg = points.map { it.y }.average().toFloat()
                val avgY = yOf(avg)
                drawLine(onSurface.copy(alpha = 0.45f), Offset(0f, avgY), Offset(w, avgY), 1.dp.toPx(), pathEffect = dash)
                // A small pill so the label stays readable where it crosses the line.
                val text = measurer.measure("avg ${format(avg)}", style)
                val pw = text.size.width + 10.dp.toPx()
                val ph = text.size.height + 2.dp.toPx()
                val py = (avgY - ph / 2).coerceIn(0f, h - ph)
                drawRoundRect(bubble, Offset(2.dp.toPx(), py), Size(pw, ph), CornerRadius(ph / 2))
                drawText(text, topLeft = Offset(2.dp.toPx() + 5.dp.toPx(), py + 1.dp.toPx()))
            }
            if (markMin) {
                val p = points.minBy { it.y }
                val o = Offset(xOf(p.x), yOf(p.y))
                drawCircle(color, 6.dp.toPx(), o)
                drawCircle(surface, 3.dp.toPx(), o)
            }
            // The latest reading: a solid dot with a soft halo, like Oura's "now".
            val last = points.last()
            val lo = Offset(xOf(last.x), yOf(last.y))
            drawCircle(color.copy(alpha = 0.25f), 8.dp.toPx(), lo)
            drawCircle(color, 4.dp.toPx(), lo)
            drawCircle(surface, 1.5.dp.toPx(), lo)
        }

        // Touch readout: a guide line, a dot on the curve and a bubble with the value and time.
        touchX?.let { tx ->
            val x = tx.coerceIn(0f, w)
            val t = xRange.first + ((x / w) * (xRange.last - xRange.first)).toLong()
            val p = points.minBy { abs(it.x - t) }
            if (abs(p.x - t) > gapSeconds) return@let
            val px = xOf(p.x)
            val py = yOf(p.y)
            drawLine(onSurface.copy(alpha = 0.5f), Offset(px, 0f), Offset(px, h), 1.dp.toPx())
            drawCircle(surface, 6.dp.toPx(), Offset(px, py))
            drawCircle(color, 4.5.dp.toPx(), Offset(px, py))
            readout(measurer, "${withUnit(format(p.y), unit)} at ${hourLabel(p.x)}", readoutStyle, bubble, px, py)
        }
    }
}

/**
 * A smooth path through [pts] using monotone cubic interpolation (Fritsch–Carlson): curves like a spline
 * but never overshoots the data, so a smoothed heart rate can't show a peak or dip that didn't happen.
 */
private fun smoothPath(pts: List<Offset>): Path {
    val path = Path()
    if (pts.isEmpty()) return path
    path.moveTo(pts[0].x, pts[0].y)
    if (pts.size == 2) { path.lineTo(pts[1].x, pts[1].y); return path }
    val n = pts.size
    val d = FloatArray(n - 1) { i -> (pts[i + 1].y - pts[i].y) / (pts[i + 1].x - pts[i].x).coerceAtLeast(0.001f) }
    val m = FloatArray(n)
    m[0] = d[0]
    m[n - 1] = d[n - 2]
    for (i in 1 until n - 1) m[i] = if (d[i - 1] * d[i] <= 0f) 0f else (d[i - 1] + d[i]) / 2f
    for (i in 0 until n - 1) {
        if (d[i] == 0f) { m[i] = 0f; m[i + 1] = 0f; continue }
        val a = m[i] / d[i]
        val b = m[i + 1] / d[i]
        val s = a * a + b * b
        if (s > 9f) {
            val t = 3f / kotlin.math.sqrt(s)
            m[i] = t * a * d[i]
            m[i + 1] = t * b * d[i]
        }
    }
    for (i in 0 until n - 1) {
        val dx = (pts[i + 1].x - pts[i].x) / 3f
        path.cubicTo(pts[i].x + dx, pts[i].y + m[i] * dx, pts[i + 1].x - dx, pts[i + 1].y - m[i + 1] * dx, pts[i + 1].x, pts[i + 1].y)
    }
    return path
}

private fun niceStep(raw: Float): Float {
    val candidates = floatArrayOf(1f, 2f, 5f, 10f, 20f, 25f, 50f, 100f)
    return candidates.firstOrNull { it >= raw } ?: raw
}

/** A day on a weekly trend chart. */
data class TrendPoint(val label: String, val value: Float?)

/**
 * WHOOP-style 7-day trend: dots joined by a line, value printed above each point, last point highlighted.
 * Dragging across it picks a day and shows its value with [unit] (and its full name from [dayName]).
 */
@Composable
fun TrendChart(
    points: List<TrendPoint>,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 150.dp,
    reveal: Float = 1f,
    unit: String = "",
    dayName: (Int) -> String = { points[it].label },
) {
    val measurer = rememberTextMeasurer()
    val axis = axisStyle()
    val valueStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontFamily = org.openstrap.app.ui.theme.FunnelDisplay)
    val surface = MaterialTheme.colorScheme.surfaceContainer
    val highlight = MaterialTheme.colorScheme.surfaceContainerHighest
    val onSurface = MaterialTheme.colorScheme.onSurface
    val readoutStyle = readoutStyle()
    var touchX by remember { mutableStateOf<Float?>(null) }
    Canvas(modifier.fillMaxWidth().height(height).scrub(points) { touchX = it }) {
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
        points.forEachIndexed { i, p -> label(measurer, p.label, axis, slot * i + slot / 2, h + 4.dp.toPx(), center = true) }

        clipRect(right = size.width * reveal.coerceIn(0f, 1f)) {
        var prev: Offset? = null
        points.forEachIndexed { i, p ->
            val cx = slot * i + slot / 2
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
        touchX?.let { tx ->
            val i = (tx / slot).toInt().coerceIn(0, points.size - 1)
            val cx = slot * i + slot / 2
            drawRoundRect(onSurface.copy(alpha = 0.06f), Offset(slot * i, 0f), Size(slot, size.height), CornerRadius(10.dp.toPx()))
            val v = points[i].value
            val text = if (v == null) "${dayName(i)}: no data" else "${dayName(i)}: ${withUnit(Math.round(v).toString(), unit)}"
            readout(measurer, text, readoutStyle, highlight, cx, v?.let { yOf(it) - 14.dp.toPx() } ?: (h / 2))
        }
    }
}

/**
 * Evenly spaced rounded bars, e.g. steps per hour. Dragging across it picks a bar, dims the others and shows
 * [describe] for it over the bars.
 */
@Composable
fun BarChart(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 64.dp,
    describe: (index: Int, value: Float) -> String = { _, v -> Math.round(v).toString() },
) {
    val measurer = rememberTextMeasurer()
    val readoutStyle = readoutStyle()
    val bubble = MaterialTheme.colorScheme.surfaceContainerHighest
    var touchX by remember { mutableStateOf<Float?>(null) }
    Canvas(modifier.fillMaxWidth().height(height).scrub(values) { touchX = it }) {
        if (values.isEmpty()) return@Canvas
        val max = values.max().coerceAtLeast(1f)
        val slot = size.width / values.size
        val barWidth = slot * 0.55f
        val picked = touchX?.let { (it / slot).toInt().coerceIn(0, values.size - 1) }
        values.forEachIndexed { i, v ->
            val h = ((v / max) * size.height).coerceAtLeast(3.dp.toPx())
            val base = if (v > 0f) color else color.copy(alpha = 0.12f)
            drawRoundRect(
                color = if (picked == null || picked == i) base else base.copy(alpha = base.alpha * 0.4f),
                topLeft = Offset(i * slot + (slot - barWidth) / 2, size.height - h),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2),
            )
        }
        picked?.let { i -> readout(measurer, describe(i, values[i]), readoutStyle, bubble, i * slot + slot / 2, 0f) }
    }
}

/** A sleep stage interval for the hypnogram. */
data class StageSpan(val start: Long, val end: Long, val stage: Int)

/**
 * Stepped hypnogram (Oura/Bevel style): one row per stage, rounded blocks joined by thin vertical
 * connectors in a gradient between the two stage colours, with start/end times underneath.
 * Dragging across it shows the stage under the finger (named by [stageNames]) and when it ran.
 */
@Composable
fun Hypnogram(
    spans: List<StageSpan>,
    stageColors: Map<Int, Color>,
    rowForStage: Map<Int, Int>,
    modifier: Modifier = Modifier,
    height: Dp = 170.dp,
    stageNames: Map<Int, String> = emptyMap(),
) {
    val measurer = rememberTextMeasurer()
    val style = axisStyle()
    val grid = MaterialTheme.colorScheme.outlineVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val bubble = MaterialTheme.colorScheme.surfaceContainerHighest
    val readoutStyle = readoutStyle()
    var touchX by remember { mutableStateOf<Float?>(null) }
    Canvas(modifier.fillMaxWidth().height(height).scrub(spans) { touchX = it }) {
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

        touchX?.let { tx ->
            val x = tx.coerceIn(0f, size.width)
            val t = start + ((x / size.width) * (end - start)).toLong()
            // The block under the finger, or the nearest one when the finger is in a gap.
            val s = sorted.firstOrNull { t in it.start until it.end } ?: sorted.minBy { minOf(abs(it.start - t), abs(it.end - t)) }
            drawLine(onSurface.copy(alpha = 0.5f), Offset(x, 0f), Offset(x, h), 1.dp.toPx())
            val name = stageNames[s.stage] ?: "Stage ${s.stage}"
            val mins = ((s.end - s.start) / 60).coerceAtLeast(1)
            readout(measurer, "$name · ${hourLabel(s.start)}–${hourLabel(s.end)} · $mins min", readoutStyle, bubble, x, centerY(s.stage) - blockH / 2)
        }
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
