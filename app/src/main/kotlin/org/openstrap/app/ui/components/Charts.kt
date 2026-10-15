package org.openstrap.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A point on a time axis: x is epoch seconds, y is the value. */
data class ChartPoint(val x: Long, val y: Float)

/**
 * Smooth-ish line with a gradient fill. Gaps longer than [gapSeconds] break the line so that
 * periods where the strap was off the wrist don't get bridged.
 */
@Composable
fun LineChart(
    points: List<ChartPoint>,
    color: Color,
    xRange: LongRange,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
    gapSeconds: Long = 15 * 60,
) {
    Canvas(modifier.fillMaxWidth().height(height)) {
        if (points.size < 2 || xRange.last <= xRange.first) return@Canvas
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }
        val span = (maxY - minY).coerceAtLeast(1f)
        val top = minY - span * 0.1f
        val bottom = maxY + span * 0.1f
        fun px(p: ChartPoint) = Offset(
            x = (p.x - xRange.first).toFloat() / (xRange.last - xRange.first) * size.width,
            y = size.height - (p.y - top) / (bottom - top) * size.height,
        )

        // Split into contiguous segments.
        val segments = mutableListOf(mutableListOf(points.first()))
        for (i in 1 until points.size) {
            if (points[i].x - points[i - 1].x > gapSeconds) segments += mutableListOf<ChartPoint>()
            segments.last() += points[i]
        }

        for (segment in segments) {
            if (segment.size < 2) continue
            val line = Path()
            segment.forEachIndexed { i, p ->
                val o = px(p)
                if (i == 0) line.moveTo(o.x, o.y) else line.lineTo(o.x, o.y)
            }
            val fill = Path().apply {
                addPath(line)
                lineTo(px(segment.last()).x, size.height)
                lineTo(px(segment.first()).x, size.height)
                close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent)))
            drawPath(line, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

/** Evenly spaced rounded bars, e.g. steps per hour. */
@Composable
fun BarChart(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 120.dp,
) {
    Canvas(modifier.fillMaxWidth().height(height)) {
        if (values.isEmpty()) return@Canvas
        val max = values.max().coerceAtLeast(1f)
        val slot = size.width / values.size
        val barWidth = slot * 0.6f
        values.forEachIndexed { i, v ->
            val h = (v / max) * size.height
            drawRoundRect(
                color = if (v > 0f) color else color.copy(alpha = 0.15f),
                topLeft = Offset(i * slot + (slot - barWidth) / 2, size.height - h.coerceAtLeast(2.dp.toPx())),
                size = Size(barWidth, h.coerceAtLeast(2.dp.toPx())),
                cornerRadius = CornerRadius(barWidth / 2),
            )
        }
    }
}

/** A sleep stage interval for the hypnogram. */
data class StageSpan(val start: Long, val end: Long, val stage: Int)

/**
 * Hypnogram: each stage drawn on its own row (awake on top, deep at the bottom).
 * [stageColors] is indexed by stage id; [rowForStage] maps a stage id to a row from the top.
 */
@Composable
fun Hypnogram(
    spans: List<StageSpan>,
    stageColors: Map<Int, Color>,
    rowForStage: Map<Int, Int>,
    modifier: Modifier = Modifier,
    height: Dp = 150.dp,
) {
    Canvas(modifier.fillMaxWidth().height(height)) {
        if (spans.isEmpty()) return@Canvas
        val start = spans.minOf { it.start }
        val end = spans.maxOf { it.end }
        if (end <= start) return@Canvas
        val rows = rowForStage.values.max() + 1
        val rowHeight = size.height / rows
        for (s in spans) {
            val row = rowForStage[s.stage] ?: continue
            val x0 = (s.start - start).toFloat() / (end - start) * size.width
            val x1 = (s.end - start).toFloat() / (end - start) * size.width
            drawRoundRect(
                color = stageColors[s.stage] ?: Color.Gray,
                topLeft = Offset(x0, row * rowHeight + rowHeight * 0.15f),
                size = Size((x1 - x0).coerceAtLeast(1.5f), rowHeight * 0.7f),
                cornerRadius = CornerRadius(4.dp.toPx()),
            )
        }
    }
}

/** Circular progress ring, e.g. steps vs goal. */
@Composable
fun ProgressRing(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    stroke: Dp = 12.dp,
) {
    Canvas(modifier.size(size)) {
        val s = stroke.toPx()
        val arcSize = Size(this.size.width - s, this.size.height - s)
        val topLeft = Offset(s / 2, s / 2)
        drawArc(color.copy(alpha = 0.15f), 0f, 360f, false, topLeft, arcSize, style = Stroke(s))
        drawArc(
            color, -90f, 360f * progress.coerceIn(0f, 1f), false, topLeft, arcSize,
            style = Stroke(s, cap = StrokeCap.Round),
        )
    }
}
