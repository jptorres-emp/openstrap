package org.openstrap.app.ui.glow

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Path
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
import org.openstrap.app.ui.components.rememberScrubHaptics
import org.openstrap.app.ui.components.scrub
import org.openstrap.app.ui.components.smoothPath
import org.openstrap.app.ui.theme.FunnelSans
import org.openstrap.app.ui.theme.MetricColors
import kotlin.math.roundToInt

/*
 * Charts for glow cards. The card's colour carries the mood, so the marks are white: lines get a soft halo, and grids,
 * guides and bars are solid (spec 2.7). Only the digits are made of dots.
 */

private val Ink = Color.White
private val Muted = Color.White.copy(alpha = 0.55f)
/** Grid rows and stage guides: solid, white at 8%, 1 dp. */
private val Grid = Color.White.copy(alpha = GlowLayout.HairlineAlpha)
/** A target, goal or average across a chart: solid, white at 40%, 1 dp. */
private val Target = Color.White.copy(alpha = 0.40f)

private val axisText = TextStyle(color = Muted, fontSize = 10.sp, fontFamily = FunnelSans)
private val valueText = TextStyle(color = Ink, fontSize = 11.sp, fontFamily = FunnelSans)
private val readoutText = TextStyle(color = Ink, fontSize = 12.sp, fontFamily = FunnelSans)

/** A solid horizontal hairline across the chart: a grid row by default, or a target in [color]. */
private fun DrawScope.gridLine(y: Float, color: Color = Grid, x0: Float = 0f, x1: Float = size.width) =
    drawLine(color, Offset(x0, y), Offset(x1, y), strokeWidth = 1.dp.toPx())

/** The hairline under a chart's bars, for bar charts drawn outside this file. */
internal fun DrawScope.barBaseline(y: Float) = gridLine(y)

/** The widest a bar gets (spec 2.7, pill bars): a wide card spaces its bars out but keeps them thin. */
internal val BarMaxWidth = 6.dp

/**
 * One pill bar in a column [pitch] wide starting at [x], standing on [bottom], [height] tall for its value. At most
 * [BarMaxWidth] wide, and never shorter than twice its width, so a small value still reads as a bar, not a dot.
 */
internal fun DrawScope.pillBar(color: Color, x: Float, pitch: Float, bottom: Float, height: Float) {
    val w = (pitch * 0.56f).coerceAtMost(BarMaxWidth.toPx())
    val h = height.coerceAtLeast(w * 2)
    drawRoundRect(color, topLeft = Offset(x + (pitch - w) / 2, bottom - h), size = Size(w, h), cornerRadius = CornerRadius(w / 2))
}

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

/** A solid vertical guide under the finger. */
private fun DrawScope.guide(x: Float, bottom: Float) = drawLine(Target, Offset(x, 0f), Offset(x, bottom), strokeWidth = 1.dp.toPx())

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
 * A day of readings as one smooth white line with a faint halo, a soft fill, three hairline grid rows, a solid
 * average line and a glowing dot on the latest value. [values] are evenly spaced; nulls break the line.
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
    /** A usual range drawn as a band of the colour at 12%, no edge lines (e.g. your normal). */
    band: ClosedFloatingPointRange<Float>? = null,
    /** How the average label prints a value. */
    format: (Float) -> String = { it.roundToInt().toString() },
    /**
     * Scrubbing that moves the card's headline instead of a readout (spec 2.7): the reading under the finger, or null
     * on release. Used without [describe].
     */
    onScrub: ((Int?) -> Unit)? = null,
    /** Text for the touch readout at a reading; null turns scrubbing off. */
    describe: ((index: Int, value: Float) -> String)? = null,
) {
    val m = rememberTextMeasurer()
    var touchX by remember { mutableStateOf<Float?>(null) }
    var width by remember { mutableStateOf(0f) }
    val inset = with(androidx.compose.ui.platform.LocalDensity.current) { 7.dp.toPx() }
    val scrubbing = describe != null || onScrub != null
    val tick = rememberScrubHaptics()
    Canvas(
        modifier.fillMaxWidth().height(height)
            .onSizeChanged { width = it.width.toFloat() }
            .let {
                if (!scrubbing) it else it.scrub(values) { x ->
                    touchX = x
                    val i = x?.let { tx -> nearestReading(values, tx, width, inset) }
                    tick(i, values, band, true)
                    onScrub?.invoke(i)
                }
            },
    ) {
        val known = values.filterNotNull()
        if (known.isEmpty()) return@Canvas
        val axisH = if (labels.isEmpty()) 0f else 18.dp.toPx()
        val top = 10.dp.toPx()
        val h = size.height - axisH
        val lo = yRange?.start ?: minOf(known.min(), band?.start ?: Float.MAX_VALUE)
        val hi = (yRange?.endInclusive ?: maxOf(known.max(), band?.endInclusive ?: -Float.MAX_VALUE)).let { if (it - lo < 1e-3f) lo + 1f else it }
        // Inset by the end dot's halo, so a reading at the very edge isn't cut off.
        fun xOf(i: Int) = inset + i / (values.size - 1f).coerceAtLeast(1f) * (size.width - inset * 2)
        fun yOf(v: Float) = top + (h - top) * (1f - (v - lo) / (hi - lo))

        listOf(0f, 0.5f, 1f).forEach { gridLine(top + (h - top) * it) }
        band?.let { b ->
            val y0 = yOf(b.endInclusive)
            val y1 = yOf(b.start)
            drawRect(color.copy(alpha = NormalBandAlpha), Offset(0f, y0), Size(size.width, y1 - y0))
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
            gridLine(y, Target)
            text(m, "avg ${format(avg)}", axisText.copy(color = Ink.copy(alpha = 0.8f)), size.width, y - 16.dp.toPx(), align = 1f)
        }

        val lastIndex = values.indexOfLast { it != null }
        val last = Offset(xOf(lastIndex), yOf(values[lastIndex]!!))
        drawCircle(color.copy(alpha = 0.2f), 6.5.dp.toPx(), last)
        drawCircle(color, 3.5.dp.toPx(), last)

        axis(m, labels, h + 4.dp.toPx())

        // Scrubbing: the reading nearest the finger (gaps are skipped, not bridged).
        val tx = touchX
        if (scrubbing && tx != null) {
            val i = nearestReading(values, tx, size.width, inset)
            if (i != null) {
                val p = Offset(xOf(i), yOf(values[i]!!))
                guide(p.x, h)
                drawCircle(Color.Black.copy(alpha = 0.6f), 6.dp.toPx(), p)
                drawCircle(color, 4.dp.toPx(), p)
                if (describe != null) readout(m, describe(i, values[i]!!), p.x, p.y)
            }
        }
    }
}

/** Your normal behind a chart: a band of the ink at 12% (spec 2.7). */
private const val NormalBandAlpha = 0.12f

/** The reading nearest [x] on a line chart [width] wide, its ends [inset] in; gaps are skipped, not bridged. */
internal fun nearestReading(values: List<Float?>, x: Float, width: Float, inset: Float): Int? {
    if (values.isEmpty() || width <= inset * 2) return null
    val near = ((x - inset) / (width - inset * 2) * (values.size - 1)).roundToInt().coerceIn(0, values.size - 1)
    return (0..values.size).asSequence().flatMap { d -> sequenceOf(near - d, near + d) }
        .firstOrNull { it in values.indices && values[it] != null && kotlin.math.abs(it - near) <= values.size / 48 + 1 }
}

/* ------------------------------------------------------------------------------------------------ */

/**
 * Solid rounded bars (e.g. steps per hour, active minutes a day), each as tall as its value, on a hairline baseline.
 * An empty column isn't drawn. The [highlight] column (default: the last one with data) is drawn in [accent].
 */
@Composable
fun GlowBars(
    values: List<Float>,
    modifier: Modifier = Modifier,
    height: Dp = 96.dp,
    labels: List<String> = emptyList(),
    highlight: Int = values.indexOfLast { it > 0f },
    accent: Color = Ink,
    /** Text for the touch readout on a column; null turns scrubbing off. */
    describe: ((index: Int, value: Float) -> String)? = null,
    /** Scrubbing that moves the headline instead of a readout (spec 2.7): the column under the finger, or null on release. */
    onScrub: ((Int?) -> Unit)? = null,
) {
    val m = rememberTextMeasurer()
    var touchX by remember { mutableStateOf<Float?>(null) }
    var width by remember { mutableStateOf(0f) }
    val scrubbing = describe != null || onScrub != null
    val tick = rememberScrubHaptics()
    Canvas(
        modifier.fillMaxWidth().height(height).onSizeChanged { width = it.width.toFloat() }.let {
            if (!scrubbing) it else it.scrub(values) { x ->
                touchX = x
                val i = x?.takeIf { values.isNotEmpty() && width > 0f }?.let { tx -> (tx / (width / values.size)).toInt().coerceIn(0, values.size - 1) }
                // Many hours sit at zero, so only the busiest is marked.
                tick(i, values, null, false)
                onScrub?.invoke(i)
            }
        },
    ) {
        if (values.isEmpty()) return@Canvas
        val axisH = if (labels.isEmpty()) 0f else 18.dp.toPx()
        val top = 4.dp.toPx()
        val h = size.height - axisH
        val pitchX = size.width / values.size
        val max = values.max().coerceAtLeast(1f)
        val picked = touchX?.takeIf { scrubbing }?.let { (it / pitchX).toInt().coerceIn(0, values.size - 1) }
        gridLine(h - 0.5.dp.toPx())
        values.forEachIndexed { i, v ->
            if (v <= 0f) return@forEachIndexed
            // While scrubbing, the other columns dim so the picked one stands out.
            val dim = if (picked == null || picked == i) 1f else 0.35f
            val color = if (i == highlight || i == picked) accent else Ink.copy(alpha = 0.6f)
            pillBar(color.copy(alpha = color.alpha * dim), pitchX * i, pitchX, h, (h - top) * v / max)
        }
        axis(m, labels, h + 4.dp.toPx())
        if (picked != null && describe != null) readout(m, describe(picked, values[picked]), pitchX * picked + pitchX / 2, 0f)
    }
}

/* ------------------------------------------------------------------------------------------------ */

/** A day on a [GlowTrend]. */
data class GlowDay(val label: String, val value: Float?)

/**
 * A week as solid stems rising to each day's value, the value printed above. Today (the last day) gets a glowing
 * ring. A day with no value is left empty. [average] draws a solid line across.
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

        average?.let { gridLine(yOf(it), Target) }
        days.forEachIndexed { i, d ->
            val cx = slot * i + slot / 2
            val today = i == days.lastIndex
            text(m, d.label, if (today) axisText.copy(color = Ink) else axisText, cx, h + 4.dp.toPx(), align = 0.5f)
            val v = d.value ?: return@forEachIndexed
            val y = yOf(v)
            drawLine(
                Ink.copy(alpha = if (today) 0.9f else 0.45f), Offset(cx, h - 3.dp.toPx()), Offset(cx, y),
                strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round,
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
 * The arc gauge (chart kit, spec 2.7): a symmetric 240 degree arch open at the foot, with round caps. A solid track of
 * white at 8% the whole way round and, over it, the value up to [progress] (0..1) in the metric's [color], both the
 * same weight. [content], the score in dots, sits centred inside. [GaugeWidth] wide, so the Recovery and Sleep score
 * details draw the same arch.
 */
@Composable
fun GlowArcGauge(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val stroke = GaugeStroke
    // The circle's radius to the middle of the stroke; the arch's feet end half a radius under its centre.
    val r = GaugeWidth / 2 - stroke / 2
    val centreY = stroke / 2 + r
    val height = centreY + r * GAUGE_FOOT + stroke / 2
    Box(modifier.size(GaugeWidth, height)) {
        Canvas(Modifier.matchParentSize()) {
            val w = stroke.toPx()
            val rad = r.toPx()
            val topLeft = Offset(size.width / 2 - rad, centreY.toPx() - rad)
            val box = Size(rad * 2, rad * 2)
            drawArc(Grid, GAUGE_START, GAUGE_SWEEP, false, topLeft, box, style = Stroke(w, cap = StrokeCap.Round))
            val sweep = GAUGE_SWEEP * progress.coerceIn(0f, 1f)
            if (sweep > 0f) drawArc(color, GAUGE_START, sweep, false, topLeft, box, style = Stroke(w, cap = StrokeCap.Round))
        }
        // Centred on the circle's centre, not the box's.
        Box(Modifier.fillMaxWidth().height(centreY * 2), contentAlignment = Alignment.Center, content = content)
    }
}

/** The arch's width on a detail header, and its stroke. */
internal val GaugeWidth = 200.dp
internal val GaugeStroke = 8.dp
/** From lower left (150 degrees, clockwise from 3 o'clock) over the top to lower right: 240 degrees, symmetric. */
internal const val GAUGE_START = 150f
internal const val GAUGE_SWEEP = 240f
/** How far under the centre the feet reach, as a share of the radius: sin 30 degrees. */
private const val GAUGE_FOOT = 0.5f

/* ------------------------------------------------------------------------------------------------ */

/** A stretch of sleep on a [GlowHypnogram]: start and end as fractions of the night, and its row. */
data class GlowStage(val from: Float, val to: Float, val row: Int)

/**
 * Sleep stages as white pills on hairline rows (row 0 at the top, e.g. Awake), joined by thin connectors, deeper rows
 * drawn brighter so deep sleep stands out on the dark core; or, with [rowColors], as solid bands in the stage colours.
 */
@Composable
fun GlowHypnogram(
    stages: List<GlowStage>,
    rowNames: List<String>,
    modifier: Modifier = Modifier,
    height: Dp = 130.dp,
    labels: List<String> = emptyList(),
    /** The stage names down the left; too small a chart has no room for them, so it leaves them out. */
    showNames: Boolean = true,
    /**
     * Each row's stage colour. Given, every stage is a solid band in its colour with nothing joining them (the sleep
     * hero, spec 2.7); null keeps the white pills, brighter the deeper, joined by thin connectors.
     */
    rowColors: List<Color>? = null,
    /** Text for the touch readout on a stage; null turns scrubbing off. */
    describe: ((GlowStage) -> String)? = null,
) {
    val m = rememberTextMeasurer()
    var touchX by remember { mutableStateOf<Float?>(null) }
    var width by remember { mutableStateOf(0f) }
    val nameWidth = with(androidx.compose.ui.platform.LocalDensity.current) { if (showNames) 46.dp.toPx() else 0f }
    val haptics = org.openstrap.app.ui.components.rememberHaptics()
    var lastStage by remember { mutableStateOf<Int?>(null) }
    Canvas(
        modifier.fillMaxWidth().height(height).onSizeChanged { width = it.width.toFloat() }.let {
            if (describe != null) it.scrub(stages) { x ->
                touchX = x
                // A tick each time the finger crosses into another stage (spec 3.6).
                val stage = x?.takeIf { width > nameWidth }?.let { tx ->
                    val f = (tx - nameWidth) / (width - nameWidth)
                    stages.indexOfFirst { f >= it.from && f <= it.to }.takeIf { it >= 0 }
                }
                if (stage != null && lastStage != null && stage != lastStage) haptics.perform(org.openstrap.app.ui.components.HapticEvent.ScrubStep)
                lastStage = if (x == null) null else stage ?: lastStage
            } else it
        },
    ) {
        if (stages.isEmpty()) return@Canvas
        val axisH = if (labels.isEmpty()) 0f else 18.dp.toPx()
        val nameW = if (showNames) 46.dp.toPx() else 0f
        val h = size.height - axisH
        val w = size.width - nameW
        val rowH = h / rowNames.size
        val pillH = rowH * 0.46f
        fun yOf(row: Int) = rowH * row + rowH / 2
        fun xOf(f: Float) = nameW + f * w

        rowNames.forEachIndexed { row, name ->
            gridLine(yOf(row), x0 = nameW)
            if (showNames) text(m, name, axisText, 0f, yOf(row) - 7.dp.toPx())
        }
        val sorted = stages.sortedBy { it.from }
        if (rowColors == null) for (i in 1 until sorted.size) {
            val a = sorted[i - 1]
            val b = sorted[i]
            if (a.row != b.row) drawLine(Ink.copy(alpha = 0.3f), Offset(xOf(b.from), yOf(a.row)), Offset(xOf(b.from), yOf(b.row)), 1.dp.toPx())
        }
        sorted.forEach { s ->
            val alpha = 0.55f + 0.45f * s.row / (rowNames.size - 1).coerceAtLeast(1)
            drawRoundRect(
                rowColors?.getOrNull(s.row) ?: Ink.copy(alpha = alpha),
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
 * The ghost line of your usual (chart kit): [today] so far as a solid line in [color] ending in a dot, and [usual]
 * by this time of day as a solid ghost line at 35% white behind it, when there is one. Both are evenly spaced over the same span
 * (a day, say) on a value axis from 0 to [max]; nulls in [today] are the hours still to come.
 */
@Composable
fun GhostLine(today: List<Float?>, color: Color, max: Float, modifier: Modifier = Modifier, usual: List<Float>? = null, height: Dp = 56.dp) {
    Canvas(modifier.fillMaxWidth().height(height)) {
        if (today.size < 2) return@Canvas
        val r = 4.5.dp.toPx()
        val top = r
        val h = size.height - r
        val hi = max.coerceAtLeast(1f)
        fun xOf(i: Int, n: Int) = r + i / (n - 1f) * (size.width - r * 2)
        fun yOf(v: Float) = top + (h - top) * (1f - (v / hi).coerceIn(0f, 1f))
        usual?.takeIf { it.size >= 2 }?.let { u ->
            val path = Path().apply { u.forEachIndexed { i, v -> if (i == 0) moveTo(xOf(i, u.size), yOf(v)) else lineTo(xOf(i, u.size), yOf(v)) } }
            drawPath(path, Ink.copy(alpha = 0.35f), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        val pts = today.mapIndexedNotNull { i, v -> v?.let { Offset(xOf(i, today.size), yOf(it)) } }
        if (pts.isEmpty()) return@Canvas
        if (pts.size >= 2) drawPath(
            Path().apply { pts.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } },
            color, style = Stroke(2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawCircle(color, r, pts.last())
    }
}

/* ------------------------------------------------------------------------------------------------ */

/**
 * Trend dots (chart kit): a few days as dots joined by a faint line, the last (today) the only full dot, with a ring.
 * Days with no value are left out of the line. With a [band] (your normal) it's drawn dim behind the dots, and days
 * outside it are lit up while those inside stay dim.
 */
@Composable
fun TrendDots(values: List<Int?>, modifier: Modifier = Modifier, height: Dp = 52.dp, band: ClosedFloatingPointRange<Float>? = null) {
    Canvas(modifier.fillMaxWidth().height(height)) {
        val known = values.filterNotNull()
        if (known.isEmpty()) return@Canvas
        val ring = 11.dp.toPx()
        val lo = minOf(known.min().toFloat(), band?.start ?: Float.MAX_VALUE) - 5f
        val hi = maxOf(known.max().toFloat(), band?.endInclusive ?: -Float.MAX_VALUE).plus(5f).coerceAtLeast(lo + 1f)
        fun xOf(i: Int) = ring + i / (values.size - 1f).coerceAtLeast(1f) * (size.width - ring * 2)
        fun yOf(v: Float) = ring + (size.height - ring * 2) * (1f - (v - lo) / (hi - lo))
        band?.let { b ->
            val y0 = yOf(b.endInclusive)
            drawRoundRect(Ink.copy(alpha = 0.10f), Offset(0f, y0), Size(size.width, yOf(b.start) - y0), CornerRadius(6.dp.toPx()))
        }
        val pts = values.mapIndexedNotNull { i, v -> v?.let { i to Offset(xOf(i), yOf(it.toFloat())) } }
        pts.zipWithNext { (_, a), (_, b) -> drawLine(Ink.copy(alpha = 0.25f), a, b, 1.5.dp.toPx()) }
        pts.forEach { (i, p) ->
            val outside = band != null && values[i]!!.toFloat() !in band
            if (i == values.lastIndex) {
                drawCircle(Ink.copy(alpha = 0.5f), ring, p, style = Stroke(1.dp.toPx()))
                drawCircle(Ink, 6.dp.toPx(), p)
            } else drawCircle(Ink.copy(alpha = if (outside) 0.95f else 0.45f), 4.dp.toPx(), p)
        }
    }
}

/**
 * A detail's chart over days (chart kit, trend dots on a detail): each day's value as a dot on a line in [color], your
 * normal as a dim band behind them with its [bandLabel], days inside it dim and days outside it lit with a halo
 * ([levels]). At a gap in the days the line stops and starts again, nothing joins it. [labels] go under each dot when there's one a day,
 * otherwise spread across. Holding scrubs: [onScrub] gets the point under the finger, or null on release, so the
 * headline can follow it (spec 2.7).
 */
@Composable
internal fun NormalTrend(
    values: List<Float?>,
    levels: List<org.openstrap.app.ui.dashboard.DetailCopy.Level?>,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 230.dp,
    labels: List<String> = emptyList(),
    band: ClosedFloatingPointRange<Float>? = null,
    bandLabel: String? = null,
    onScrub: ((Int?) -> Unit)? = null,
) {
    val m = rememberTextMeasurer()
    var touchX by remember { mutableStateOf<Float?>(null) }
    var width by remember { mutableStateOf(0f) }
    val inset = with(androidx.compose.ui.platform.LocalDensity.current) { 16.dp.toPx() }
    val tick = rememberScrubHaptics()
    Canvas(
        modifier.fillMaxWidth().height(height)
            .onSizeChanged { width = it.width.toFloat() }
            .let {
                if (onScrub == null) it else it.scrub(values) { x ->
                    touchX = x
                    val i = x?.let { tx -> nearestReading(values, tx, width, inset) }
                    tick(i, values, band, true)
                    onScrub(i)
                }
            },
    ) {
        val known = values.filterNotNull()
        if (known.isEmpty()) return@Canvas
        val axisH = if (labels.isEmpty()) 0f else 24.dp.toPx()
        // Fewer, bigger dots for a week; a month or a year of them get smaller.
        val scale = when {
            values.size <= 7 -> 1f
            values.size <= 31 -> 0.55f
            else -> 0.4f
        }
        // The band's label has a row of its own above the plot, clear of the biggest halo, so it never sits on a point
        // or the line wherever the data goes. A swatch of the band's colour ties it to the band.
        val labelStyle = axisText.copy(fontSize = 12.sp)
        val labelH = bandLabel?.takeIf { band != null }?.let { m.measure(it, labelStyle).size.height.toFloat() }
        val top = (labelH?.plus(6.dp.toPx()) ?: 0f) + 14.dp.toPx() * scale + 4.dp.toPx()
        val h = size.height - axisH - 16.dp.toPx()
        val lo0 = minOf(known.min(), band?.start ?: Float.MAX_VALUE)
        val hi0 = maxOf(known.max(), band?.endInclusive ?: -Float.MAX_VALUE)
        val pad = ((hi0 - lo0) * 0.12f).coerceAtLeast(0.5f)
        val lo = lo0 - pad
        val hi = hi0 + pad
        fun xOf(i: Int) = inset + i / (values.size - 1f).coerceAtLeast(1f) * (size.width - inset * 2)
        fun yOf(v: Float) = top + (h - top) * (1f - (v - lo) / (hi - lo))
        band?.let { b ->
            val y0 = yOf(b.endInclusive)
            drawRoundRect(color.copy(alpha = NormalBandAlpha), Offset(0f, y0), Size(size.width, yOf(b.start) - y0), CornerRadius(12.dp.toPx()))
            if (bandLabel != null && labelH != null) {
                val w = m.measure(bandLabel, labelStyle).size.width
                val sw = 14.dp.toPx()
                val x = size.width - w
                drawRoundRect(color.copy(alpha = 0.35f), Offset(x - sw - 6.dp.toPx(), labelH / 2 - 4.dp.toPx()), Size(sw, 8.dp.toPx()), CornerRadius(3.dp.toPx()))
                text(m, bandLabel, labelStyle, size.width, 0f, align = 1f)
            }
        }

        val pts = values.mapIndexedNotNull { i, v -> v?.let { i to Offset(xOf(i), yOf(it)) } }
        val stroke = 2.4.dp.toPx() * (0.6f + 0.4f * scale)
        pts.zipWithNext { (i, a), (j, b) ->
            // A missing day between two dots: the line stops there.
            if (j - i == 1) drawLine(color.copy(alpha = 0.85f), a, b, stroke, StrokeCap.Round)
        }
        val dim = lerp(color, Color.Black, 0.5f)
        pts.forEach { (i, p) ->
            when (levels.getOrNull(i)) {
                org.openstrap.app.ui.dashboard.DetailCopy.Level.Above, org.openstrap.app.ui.dashboard.DetailCopy.Level.Below -> {
                    val lit = lerp(color, Color.White, 0.3f)
                    drawCircle(lit.copy(alpha = 0.35f), 14.dp.toPx() * scale, p)
                    drawCircle(lit, 7.dp.toPx() * scale, p)
                }
                else -> {
                    drawCircle(dim, 5.dp.toPx() * scale, p)
                    drawCircle(color, 5.dp.toPx() * scale, p, style = Stroke(2.dp.toPx() * scale.coerceAtLeast(0.6f)))
                }
            }
        }

        if (labels.size == values.size) labels.forEachIndexed { i, l -> text(m, l, axisText.copy(fontSize = 12.sp), xOf(i), size.height - axisH + 6.dp.toPx(), align = 0.5f) }
        else axis(m, labels, size.height - axisH + 6.dp.toPx())

        val tx = touchX
        if (onScrub != null && tx != null) {
            nearestReading(values, tx, size.width, inset)?.let { i ->
                val p = Offset(xOf(i), yOf(values[i]!!))
                guide(p.x, h)
                drawCircle(Color.Black.copy(alpha = 0.6f), 8.dp.toPx(), p)
                drawCircle(Ink, 5.dp.toPx(), p)
            }
        }
    }
}

/* ------------------------------------------------------------------------------------------------ */

/**
 * The zone bar (chart kit): how a workout's time was spent, a thin bar split by the seconds in each heart rate zone,
 * easy to max, in the zone colours. Zones with no time are left out; nothing draws without any.
 */
@Composable
fun ZoneBar(zoneSeconds: List<Int>, colors: List<Color>, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    if (zoneSeconds.sum() <= 0) return
    androidx.compose.foundation.layout.Row(
        modifier.fillMaxWidth().height(height).clip(androidx.compose.foundation.shape.RoundedCornerShape(height / 2)),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp),
    ) {
        zoneSeconds.forEachIndexed { z, secs ->
            if (secs > 0) Box(Modifier.weight(secs.toFloat()).fillMaxHeight().background(colors[z % colors.size]))
        }
    }
}

/* ------------------------------------------------------------------------------------------------ */

/**
 * A night or nap's stages laid flat (the nap hypnogram strip): one line from start to end, each stretch in its
 * stage's colour.
 */
@Composable
fun StageStrip(spans: List<org.openstrap.app.ui.components.StageSpan>, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    Canvas(modifier.fillMaxWidth().height(height)) {
        if (spans.isEmpty()) return@Canvas
        val start = spans.minOf { it.start }
        val end = spans.maxOf { it.end }.coerceAtLeast(start + 1)
        val w = size.height
        spans.sortedBy { it.start }.forEach { s ->
            val x0 = w / 2 + (size.width - w) * (s.start - start) / (end - start)
            val x1 = w / 2 + (size.width - w) * (s.end - start) / (end - start)
            drawLine(stageColor(s.stage), Offset(x0, size.height / 2), Offset(x1.coerceAtLeast(x0 + 0.5f), size.height / 2), w, StrokeCap.Round)
        }
    }
}

/** A sleep stage's colour, as on the strap's own charts. */
internal fun stageColor(stage: Int): Color = when (stage) {
    org.openstrap.app.data.db.SleepStage.DEEP -> MetricColors.sleepDeep
    org.openstrap.app.data.db.SleepStage.REM -> MetricColors.sleepRem
    org.openstrap.app.data.db.SleepStage.AWAKE -> MetricColors.sleepAwake
    else -> MetricColors.sleepLight
}
