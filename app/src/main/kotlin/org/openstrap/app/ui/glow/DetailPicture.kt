@file:Suppress("UnstableCollections", "ComposeUnstableCollections", "TooManyFunctions")

package org.openstrap.app.ui.glow

import org.openstrap.app.ui.theme.OnSurface
import org.openstrap.app.ui.theme.IconSize
import org.openstrap.app.ui.theme.Inset
import androidx.compose.runtime.CompositionLocalProvider
import org.openstrap.app.ui.theme.Corner
import org.openstrap.app.ui.theme.Space
import org.openstrap.app.ui.theme.Greys
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import org.openstrap.app.ui.components.Motion
import org.openstrap.app.ui.components.rememberAnimationsOn
import org.openstrap.app.ui.components.rememberScrubHaptics
import org.openstrap.app.ui.components.scrub
import org.openstrap.app.ui.dashboard.NewsTag
import org.openstrap.app.ui.theme.Ink

/*
 * A card's own picture on the page it opens (spec 2.7, "Each metric draws its own picture"): the page draws the same
 * picture its card draws, from the same data in the same ink, so tapping the card leads somewhere that visibly
 * continues it. It sits under the headline, across the page at Wide, and never carries the card's rim: a page has no
 * card edge to light.
 *
 * Below it, the detail's own parts (boards HaloDetailRules, HaloDetailHeart, HaloDetailRecovery, HaloDetailSteps):
 * the plain panel and its rows, the thin gauge, and the charts a detail draws with no card and no dots. They are
 * private copies of what GlowCharts and GlowList draw for cards, so those stay as the tabs use them.
 */

/** The card's [picture] across the page, [height] tall, under the headline and above the rest of the day. */
@Composable
internal fun DetailPicture(modifier: Modifier = Modifier, height: Dp = DetailPictureHeight, picture: @Composable (Modifier) -> Unit) =
    picture(modifier.fillMaxWidth().padding(top = Space.S12, bottom = Space.S4).height(height))

/** How tall a card's picture stands on its page, a little over a Wide's 52 dp on Today. */
internal val DetailPictureHeight = 64.dp

/** Heart points and active minutes share this lime (rules, metric colours), apart from Steps' own. */
internal val ActivityLime = org.openstrap.app.ui.theme.MetricColors.activityLime

/** How tall a detail's chart stands, whatever its data: an empty one keeps it too ([DetailEmpty]), so nothing below moves. */
internal val DetailChartHeight = 200.dp

/**
 * A detail's picture for [range], crossfading to the next range's in [Motion.QUICK] while the header and the figures stay
 * put. With the system's animations off it cuts.
 */
@Composable
internal fun <T> RangeCrossfade(range: T, content: @Composable (T) -> Unit) {
    val animate = rememberAnimationsOn()
    androidx.compose.animation.Crossfade(
        range, animationSpec = if (animate) tween(Motion.QUICK) else androidx.compose.animation.core.snap(), label = "range picture",
    ) { content(it) }
}

/* ------------------------------------------- Ink and type ------------------------------------------- */



internal val DetailAxisInk = Ink.Tertiary


/** Your normal as a band behind a chart (boards: white at 0.07). */
private val DetailBandInk = Ink.Faint

/** A chart's value labels sit in this gutter on the right, which data never enters (rules, picture). */
private val DetailGutter = 30.dp

/** A value label's gap to the chart's right edge, and the room the latest reading's ring takes beside the plot, in dp. */
private const val GUTTER_LABEL_EDGE = 2
private const val GUTTER_RING = 8

/** A chart's axis text (boards: 11 px #6E7480). */
private val DetailAxisText = GlowType.caption.copy(color = DetailAxisInk)

/** A column's value above it (boards: 12 px #9AA0AA). */
private val DetailValueText = GlowType.noteSmall.copy(color = GlowType.noteColor)

/** How far a status word's tint is from the metric colour toward white (boards: 42%). */
private const val STATUS_TINT = 0.42f

/** The half of a column, a gap and an arc: shared by the charts below. */
private const val HALF = 0.5f

/** The gauge: its fill time in ms, where its arc starts (lower left) and how far round it runs (open at the foot). */
private const val GAUGE_FILL_MS = 450
private const val GAUGE_START = 135f
private const val GAUGE_SWEEP = 270f

/** The least a chart's axis spans, so a flat line still has a scale. */
private const val MIN_SPAN = 1e-6f

/** How many readings a scrub may skip over to find one: a 48th of the chart. */
private const val SCRUB_REACH = 48

/** A light tint of the metric's colour for its status word (boards: #FF5A5F gives #FF9FA2, 42% of the way to white). */
internal fun statusTint(color: Color): Color = androidx.compose.ui.graphics.lerp(color, Color.White, STATUS_TINT)

/* ----------------------------------------- Panel and rows ------------------------------------------- */

/**
 * A detail's list (board HaloDetailHeart, Related): a heading (15 semibold, or 13 #9AA0AA when [muted] as All metrics
 * groups have it) with a [note] on its right, over one plain panel of rows. The rows are [MetricRow]s.
 */
@Composable
internal fun DetailPanel(
    modifier: Modifier = Modifier,
    title: String? = null,
    note: String? = null,
    onNote: (() -> Unit)? = null,
    muted: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(CardSpec.Gap)) {
        if (title != null || note != null) HaloTitle(
            title.orEmpty(), variant = if (muted) TitleVariant.Group else TitleVariant.OutOfCard, note = note, onNote = onNote,
        )
        HaloCard(fill = CardFill.Rows, content = content)
    }
}

/* ------------------------------------------- The gauge ---------------------------------------------- */

/**
 * The score gauge (board HaloDetailRecovery): a thin arc, 150 by 136, open at the bottom, a track of white at 0.14 and the
 * value in the metric's [color], with [content] (the score in Doto 52) centred in it. A change of [progress] fills from
 * the value shown to the new one, never from zero; a new [key] draws the new value where it lands.
 */
@Composable
internal fun DetailGauge(
    progress: Float,
    color: Color,
    key: Any?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val animate = rememberAnimationsOn()
    val shown = remember(key) { Animatable(progress) }
    LaunchedEffect(shown, progress, animate) {
        if (animate) shown.animateTo(progress, tween(GAUGE_FILL_MS, easing = FastOutSlowInEasing)) else shown.snapTo(progress)
    }
    Box(modifier.size(150.dp, 136.dp)) {
        Canvas(Modifier.matchParentSize()) {
            val w = 6.dp.toPx()
            val box = Size(128.dp.toPx(), 128.dp.toPx())
            val topLeft = Offset(11.dp.toPx(), 4.dp.toPx())
            // Open at the foot: from the lower left round to the lower right, 270 degrees.
            val track = Ink.Edge
            drawArc(track, GAUGE_START, GAUGE_SWEEP, false, topLeft, box, style = Stroke(w, cap = StrokeCap.Round))
            val sweep = GAUGE_SWEEP * shown.value.coerceIn(0f, 1f)
            if (sweep > 0f) drawArc(color, GAUGE_START, sweep, false, topLeft, box, style = Stroke(w, cap = StrokeCap.Round))
        }
        Box(Modifier.fillMaxWidth().padding(top = 46.dp), contentAlignment = Alignment.TopCenter, content = content)
    }
}

/* ------------------------------------------ Chart frame --------------------------------------------- */

/** Where a detail chart's plot sits: [plotRight] is where the value gutter starts, y runs from [top] to [bottom]. */
internal class DetailPlot(val plotRight: Float, val top: Float, val bottom: Float, private val lo: Float, private val hi: Float) {
    fun y(v: Float): Float = bottom - (v - lo) / (hi - lo).coerceAtLeast(MIN_SPAN) * (bottom - top)
}

/**
 * A "nice" set of at most [count] gridline values across [lo]..[hi] (1, 2 or 5 times a power of ten apart), and the range
 * that holds them and the data: the rules allow at most 4 gridlines.
 */
internal fun niceScale(lo: Float, hi: Float, count: Int = 3): Pair<ClosedFloatingPointRange<Float>, List<Float>> {
    val span = (hi - lo).coerceAtLeast(1e-3f)
    val raw = span / count
    val mag = 10f.pow(floor(log10(raw)))
    val step = listOf(1f, 2f, 2.5f, 5f, 10f).map { it * mag }.first { it >= raw }
    val first = ceil(lo / step) * step
    val ticks = generateSequence(first) { it + step }.takeWhile { it <= hi + step * 0.001f }.take(4).toList()
    val pad = span * 0.06f
    return (lo - pad)..(hi + pad) to ticks
}

/**
 * The frame every detail chart shares (boards HaloDetailHeart, HaloDetailSteps, HaloDetailHrv): up to four gridlines in
 * #16171B across the width, their values 11 px #6E7480 in a 30 dp gutter on the right, and [xLabels] under the plot, spread
 * from the left edge to the plot's right or, with [perColumn], one under each column. [draw] paints the data in the
 * plot; [pick] says which of the readings a touch at a share of the plot's width is on, which [onScrub] reports and
 * [draw] gets back to mark it with a white 1 dp line. No card, no dots.
 */
@Composable
internal fun DetailChart(
    values: List<Float?>,
    scale: ClosedFloatingPointRange<Float>,
    ticks: List<Float>,
    tickText: (Float) -> String,
    xLabels: List<String>,
    modifier: Modifier = Modifier,
    height: Dp = DetailChartHeight,
    perColumn: Boolean = false,
    pick: (Float) -> Int? = { f -> if (values.isEmpty()) null else (f * values.size).toInt().coerceIn(0, values.size - 1) },
    onScrub: ((Int?) -> Unit)? = null,
    draw: DrawScope.(DetailPlot, Int?) -> Unit,
) {
    val m = rememberTextMeasurer()
    // The finger's line belongs to the data it was laid on: other data (another day) starts with none.
    var touchX by remember(values) { mutableStateOf<Float?>(null) }
    var width by remember { mutableFloatStateOf(0f) }
    // The gutter holds the widest value label and the latest reading's ring beside it, whatever the font size: no label under a ring.
    val gutter = with(LocalDensity.current) {
        val widest = ticks.maxOfOrNull { m.measure(tickText(it), DetailAxisText).size.width } ?: 0
        maxOf(DetailGutter.toPx(), widest + GUTTER_LABEL_EDGE.dp.toPx() + GUTTER_RING.dp.toPx())
    }
    val tick = rememberScrubHaptics()
    val currentPick by rememberUpdatedState(pick)
    val currentScrub by rememberUpdatedState(onScrub)
    Canvas(
        modifier.fillMaxWidth().height(height)
            .onSizeChanged { width = it.width.toFloat() }
            .let {
                if (onScrub == null) it else it.scrub(values) { x ->
                    touchX = x
                    val i = x?.let { tx -> currentPick((tx / (width - gutter).coerceAtLeast(1f)).coerceIn(0f, 1f)) }
                    tick(i, values, null, false)
                    currentScrub?.invoke(i)
                }.pointerInput(values) {
                    // A tap picks a column, a second tap on it lets go.
                    detectTapGestures { o ->
                        val span = (width - gutter).coerceAtLeast(1f)
                        val i = currentPick((o.x / span).coerceIn(0f, 1f))
                        val same = touchX?.let { currentPick((it / span).coerceIn(0f, 1f)) } == i
                        touchX = if (same) null else o.x
                        currentScrub?.invoke(if (same) null else i)
                    }
                }
            },
    ) {
        val top = 14.dp.toPx()
        val bottom = size.height - 20.dp.toPx()
        val plot = DetailPlot(size.width - gutter, top, bottom, scale.start, scale.endInclusive)
        ticks.forEach { t ->
            val y = plot.y(t)
            drawLine(Greys.Raised, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            val r = m.measure(tickText(t), DetailAxisText)
            drawText(r, topLeft = Offset(size.width - GUTTER_LABEL_EDGE.dp.toPx() - r.size.width, y - 4.dp.toPx() - r.size.height))
        }
        val picked = touchX?.let { tx -> pick((tx / plot.plotRight.coerceAtLeast(1f)).coerceIn(0f, 1f)) }
        draw(plot, picked)
        // Labels under the plot, from the left edge to where the gutter starts.
        if (xLabels.isNotEmpty()) {
            val laid = xLabels.map { m.measure(it, DetailAxisText) }
            laid.forEachIndexed { i, r ->
                val f = if (perColumn) (i + 0.5f) / laid.size else if (laid.size == 1) 0f else i / (laid.size - 1f)
                val x = if (perColumn) plot.plotRight * f - r.size.width / 2f else plot.plotRight * f - r.size.width * f
                drawText(r, topLeft = Offset(x.coerceAtLeast(0f), bottom + 6.dp.toPx()))
            }
        }
        // The finger's line: white, 1 dp, the only thing that moves.
        if (picked != null) {
            val x = if (perColumn || values.size <= 1) plot.plotRight * (picked + 0.5f) / values.size.coerceAtLeast(1)
            else plot.plotRight * picked / (values.size - 1f)
            drawLine(Color.White, Offset(x, top), Offset(x, bottom), 1.dp.toPx())
        }
    }
}

/* ------------------------------------------- Line chart --------------------------------------------- */

/**
 * A day of readings as a line (boards HaloDetailHeart, HaloDetailEnergy): 2 dp in the metric's [color], a ring at the
 * latest reading with a dashed guide up from it, and with [hourBands] the low to high band of each hour shaded at 0.18
 * behind it. [band] is your resting range, [shade] the readings to tint (the hours asleep). [yRange] fixes the axis.
 */
@Composable
internal fun DetailLineChart(
    values: List<Float?>,
    color: Color,
    xLabels: List<String>,
    modifier: Modifier = Modifier,
    height: Dp = DetailChartHeight,
    yRange: ClosedFloatingPointRange<Float>? = null,
    band: ClosedFloatingPointRange<Float>? = null,
    shade: IntRange? = null,
    shadeLabel: String? = null,
    spread: List<ClosedFloatingPointRange<Float>?>? = null,
    hourBands: Boolean = false,
    format: (Float) -> String = { Math.round(it).toString() },
    onScrub: ((Int?) -> Unit)? = null,
) {
    val known = values.filterNotNull()
    if (known.isEmpty()) return
    val lo0 = listOfNotNull(known.min(), band?.start, yRange?.start, spread?.mapNotNull { it?.start }?.minOrNull()).min()
    val hi0 = listOfNotNull(
        known.max(), band?.endInclusive, yRange?.endInclusive, spread?.mapNotNull { it?.endInclusive }?.maxOrNull(),
    ).max()
    val (scale, ticks) = remember(lo0, hi0) { niceScale(lo0, hi0) }
    val m = rememberTextMeasurer()
    val last = values.indexOfLast { it != null }
    DetailChart(
        values, scale, ticks, format, xLabels, modifier, height,
        pick = { f -> nearestKnown(values, f) },
        onScrub = onScrub,
    ) { plot, picked ->
        val n = values.size
        fun xOf(i: Int) = if (n <= 1) 0f else plot.plotRight * i / (n - 1f)
        band?.let { b ->
            drawRect(DetailBandInk, Offset(0f, plot.y(b.endInclusive)), Size(plot.plotRight, plot.y(b.start) - plot.y(b.endInclusive)))
        }
        shade?.let { s ->
            drawRect(DetailBandInk, Offset(xOf(s.first), plot.top), Size(xOf(s.last) - xOf(s.first), plot.bottom - plot.top))
            // Said, so the block is not a mystery.
            if (shadeLabel != null) {
                val labelAt = Offset(xOf(s.first) + 4.dp.toPx(), plot.top + 2.dp.toPx())
                drawText(m.measure(shadeLabel, DetailAxisText), topLeft = labelAt)
            }
        }
        if (hourBands) {
            val pitch = plot.plotRight / HOURS_IN_DAY
            val per = n / HOURS_IN_DAY.toFloat()
            repeat(HOURS_IN_DAY) { h ->
                val from = (h * per).toInt().coerceAtMost(n)
                val upTo = ((h + 1) * per).toInt().coerceIn(0, n)
                // The hour's own readings when it has them (their lowest and highest), else the line's averages.
                val slice = values.subList(from, upTo).filterNotNull() +
                    (
                        spread?.subList(from, upTo.coerceAtMost(spread.size))?.filterNotNull()
                            ?.flatMap { listOf(it.start, it.endInclusive) }.orEmpty()
                        )
                if (slice.isEmpty()) return@repeat
                val y0 = plot.y(slice.max())
                val y1 = plot.y(slice.min())
                val inset = pitch * 0.08f
                drawRoundRect(
                    color.copy(alpha = 0.18f), Offset(h * pitch + inset, y0), Size(pitch - inset * 2, (y1 - y0).coerceAtLeast(3.dp.toPx())),
                    CornerRadius(3.dp.toPx()),
                )
            }
        }
        // The line breaks at a gap rather than bridging it.
        val path = androidx.compose.ui.graphics.Path()
        var open = false
        values.forEachIndexed { i, v ->
            if (v == null) { open = false; return@forEachIndexed }
            if (open) path.lineTo(xOf(i), plot.y(v)) else { path.moveTo(xOf(i), plot.y(v)); open = true }
        }
        drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        val at = picked ?: last
        values.getOrNull(at)?.let { v ->
            val p = Offset(xOf(at), plot.y(v))
            if (picked == null) drawLine(
                Ink.Edge, Offset(p.x, 0f), Offset(p.x, size.height), 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx())),
            )
            drawCircle(Color.Black, 6.dp.toPx(), p)
            drawCircle(Color.White, 6.dp.toPx(), p, style = Stroke(2.5.dp.toPx()))
        }
    }
}

private const val HOURS_IN_DAY = 24

/** The reading nearest a share [f] across a line's width, gaps skipped. */
private fun nearestKnown(values: List<Float?>, f: Float): Int? {
    if (values.isEmpty()) return null
    val near = (f * (values.size - 1)).let { Math.round(it) }.coerceIn(0, values.size - 1)
    return (0..values.size).asSequence().flatMap { d -> sequenceOf(near - d, near + d) }
        .firstOrNull { it in values.indices && values[it] != null && abs(it - near) <= values.size / SCRUB_REACH + 1 }
}

/* ------------------------------------------ Column chart -------------------------------------------- */

/**
 * Columns, one per hour, day or week (boards HaloDetailSteps, HaloDetailRecoveryWeek, HaloDetailHrv): the [highlight]ed one
 * white, the rest the metric's [color] at [alpha]. By default they stand on the axis. With a [baseline] (your normal) each
 * runs up or down from it, and [band] is your normal behind them in white 0.07, labelled when there are few enough that
 * the label clears the columns. A [goal] is a dashed line. [valueLabels] print each score above its column.
 */
@Suppress("CyclomaticComplexMethod")
@Composable
internal fun DetailColumns(
    values: List<Float?>,
    color: Color,
    xLabels: List<String>,
    modifier: Modifier = Modifier,
    height: Dp = DetailChartHeight,
    highlight: Int? = values.indexOfLast { it != null }.takeIf { it >= 0 },
    alpha: Float = 0.55f,
    baseline: Float? = null,
    band: ClosedFloatingPointRange<Float>? = null,
    bandLabel: String? = null,
    goal: Float? = null,
    fixed: ClosedFloatingPointRange<Float>? = null,
    valueLabels: Boolean = false,
    format: (Float) -> String = { Math.round(it).toString() },
    onScrub: ((Int?) -> Unit)? = null,
) {
    val known = values.filterNotNull()
    if (known.isEmpty()) return
    val few = values.size <= FEW_COLUMNS
    val lo0 = fixed?.start ?: if (baseline != null) listOfNotNull(known.min(), baseline, band?.start).min() else 0f
    val top = listOfNotNull(known.max(), baseline, band?.endInclusive, goal).max()
    val hi0 = fixed?.endInclusive ?: if (baseline == null) top * HEADROOM else top
    val (scale, ticks) = remember(lo0, hi0) { niceScale(lo0, hi0.coerceAtLeast(lo0 + 1f)) }
    // A column that stands on the axis is drawn from the scale's floor; a normal-based one from the baseline.
    val floor = if (baseline == null) (if (fixed != null) fixed.start else 0f).coerceAtLeast(scale.start) else baseline
    val m = rememberTextMeasurer()
    DetailChart(
        values, scale, ticks, format, xLabels, modifier, height, perColumn = xLabels.size == values.size,
        onScrub = onScrub,
    ) { plot, picked ->
        val n = values.size
        val pitch = plot.plotRight / n
        val barW = (pitch * if (few) 0.55f else 0.78f).coerceIn(2.dp.toPx(), if (few) 30.dp.toPx() else 14.dp.toPx())
        band?.let { b ->
            drawRect(DetailBandInk, Offset(0f, plot.y(b.endInclusive)), Size(plot.plotRight, plot.y(b.start) - plot.y(b.endInclusive)))
            if (few && bandLabel != null) {
                val r = m.measure(bandLabel, DetailAxisText)
                // Where the label touches no column and no axis label: under the band, over it, else in the strip above the plot.
                fun clear(y: Float): Boolean {
                    val x0 = 2.dp.toPx()
                    if (y < 0f || y + r.size.height > plot.bottom) return false
                    return values.indices.none { i ->
                        val v = values[i] ?: return@none false
                        val bx = pitch * i + (pitch - barW) / 2f
                        val (by0, by1) = plot.y(v).let { yv -> minOf(yv, plot.y(floor)) to maxOf(yv, plot.y(floor)) }
                        bx < x0 + r.size.width && bx + barW > x0 && by0 < y + r.size.height && by1 > y
                    }
                }
                val under = plot.y(b.start) + 3.dp.toPx()
                val over = plot.y(b.endInclusive) - 3.dp.toPx() - r.size.height
                val y = listOf(under, over).firstOrNull(::clear) ?: 0f
                drawText(r, topLeft = Offset(2.dp.toPx(), y))
            }
        }
        if (baseline != null) {
            drawLine(Ink.Disabled, Offset(0f, plot.y(baseline)), Offset(plot.plotRight, plot.y(baseline)), 1.dp.toPx())
            // Its value in the gutter, unless a tick's label already stands there (two labels one above the other).
            if (ticks.none { abs(plot.y(it) - plot.y(baseline)) < 12.dp.toPx() }) {
                val r = m.measure(format(baseline), DetailAxisText)
                drawText(r, topLeft = Offset(size.width - 2.dp.toPx() - r.size.width, plot.y(baseline) - r.size.height / 2f))
            }
        }
        goal?.let { g -> dashedGoalLine(plot.y(g), plot.plotRight) }
        val radius = if (few) 9.dp.toPx().coerceAtMost(barW / 2f) else minOf(3.dp.toPx(), barW / 2f)
        val on = picked ?: highlight
        values.forEachIndexed { i, v ->
            if (v == null) return@forEachIndexed
            val x = pitch * i + (pitch - barW) / 2f
            val y0 = plot.y(v)
            val y1 = plot.y(floor)
            val top = minOf(y0, y1)
            val h = abs(y1 - y0).coerceAtLeast(3.dp.toPx())
            val fill = if (i == on) Ink.Primary else color.copy(alpha = alpha)
            drawRoundRect(fill, Offset(x, if (y0 > y1) y1 else top), Size(barW, h), CornerRadius(radius))
            if (valueLabels && few) {
                val r = m.measure(format(v), DetailValueText.copy(color = if (i == on) Color.White else GlowType.noteColor))
                drawText(r, topLeft = Offset(pitch * (i + HALF) - r.size.width / 2f, top - r.size.height - 2.dp.toPx()))
            }
        }
    }
}

/**
 * A longer range of a Signal (rules 2): a capsule per day from its low to its high at 0.35 in the metric's [color], the
 * average a white tick across it, the one shown white. [days] hold each day's low, average and high (null for a gap);
 * scrubbing reports the day's index.
 */
@Composable
internal fun DetailRanges(
    days: List<org.openstrap.app.data.DayStat?>,
    color: Color,
    xLabels: List<String>,
    modifier: Modifier = Modifier,
    format: (Float) -> String = { Math.round(it).toString() },
    onScrub: ((Int?) -> Unit)? = null,
) {
    val known = days.filterNotNull()
    if (known.isEmpty()) {
        DetailEmpty(androidx.compose.ui.res.stringResource(org.openstrap.app.R.string.glow_metrics_none_in_range))
        return
    }
    val (scale, ticks) = remember(days) { niceScale(known.minOf { it.min }, known.maxOf { it.max }) }
    val highlight = days.indexOfLast { it != null }
    DetailChart(
        days.map { it?.value }, scale, ticks, format, xLabels, modifier, perColumn = xLabels.size == days.size, onScrub = onScrub,
    ) { plot, picked ->
        val pitch = plot.plotRight / days.size
        val few = days.size <= FEW_COLUMNS
        val barW = (pitch * if (few) 0.55f else 0.78f).coerceIn(2.dp.toPx(), if (few) 30.dp.toPx() else 14.dp.toPx())
        val on = picked ?: highlight
        days.forEachIndexed { i, d ->
            if (d == null) return@forEachIndexed
            val x = pitch * i + (pitch - barW) / 2f
            val top = plot.y(d.max)
            val h = (plot.y(d.min) - top).coerceAtLeast(barW)
            drawRoundRect(
                if (i == on) Ink.Primary.copy(alpha = RANGE_ALPHA + 0.2f) else color.copy(alpha = RANGE_ALPHA),
                Offset(x, top), Size(barW, h), CornerRadius(barW / 2f),
            )
            drawRoundRect(Color.White, Offset(x, plot.y(d.value) - 1.5.dp.toPx()), Size(barW, 3.dp.toPx()), CornerRadius(1.5.dp.toPx()))
        }
    }
}

/** A range capsule's fill (rules: the low to high bar at 0.35). */
private const val RANGE_ALPHA = 0.35f

/** A column chart standing on the axis leaves this much room above its tallest column. */
private const val HEADROOM = 1.05f

/** A chart with this many columns or fewer is a few-column chart: fat columns, their labels under each. */
private const val FEW_COLUMNS = 7

/* -------------------------------------------- Drivers ----------------------------------------------- */

/** One thing that moved a score: its [name], the [reading] and [note] beside it, and how far it pulled, -1 to 1 (up is better). */
internal class DetailDriver(val name: String, val reading: String?, val note: String?, val pull: Float)

/**
 * What drove a score (boards HaloDetailRecovery, HaloDetailEnergy): each driver as its name with the reading beside it,
 * over a bar from your normal, better to the right in the metric's colour and worse to the left in white 0.35, with a
 * mark at the normal. "Worse, Your normal, Better" under them. Plain, no panel.
 */
@Composable
internal fun DetailDrivers(
    drivers: List<DetailDriver>,
    color: Color,
    labels: Triple<String, String, String>,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = Space.S4)) {
        drivers.forEachIndexed { i, d ->
            val longNote = d.note != null && d.note.length + (d.reading?.length ?: 0) > 16
            Column(
                Modifier.fillMaxWidth().topHairline(i > 0, OnSurface.line).padding(vertical = Space.S12),
                verticalArrangement = Arrangement.spacedBy(Space.S8),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Text(d.name, Modifier.weight(1f), color = Color.White, style = GlowType.body, maxLines = 1)
                    Spacer(Modifier.width(Space.S12))
                    if (d.reading != null) Text(d.reading, color = Color.White, style = GlowType.body, maxLines = 1)
                    // A long note would be measured first and squeeze the name, so it goes on its own line under it.
                    if (d.note != null && !longNote) Text(
                        (if (d.reading != null) " " else "") + d.note, color = GlowType.noteColor, style = GlowType.secondary, maxLines = 1,
                    )
                }
                if (d.note != null && longNote) Text(d.note, color = GlowType.noteColor, style = GlowType.secondary)
                Canvas(Modifier.fillMaxWidth().padding(end = DetailGutter).height(10.dp)) {
                    drawRoundRect(Greys.Raised, Offset(0f, 3.dp.toPx()), Size(size.width, 4.dp.toPx()), CornerRadius(2.dp.toPx()))
                    val mid = size.width / 2f
                    val end = mid + d.pull.coerceIn(-1f, 1f) * mid
                    if (abs(end - mid) > HALF) drawRoundRect(
                        if (d.pull >= 0f) color else Ink.Disabled,
                        Offset(minOf(mid, end), 1.dp.toPx()), Size(abs(end - mid), 8.dp.toPx()), CornerRadius(4.dp.toPx()),
                    )
                    drawRoundRect(
                        Ink.Tertiary, Offset(mid - 1.dp.toPx(), 0f), Size(2.dp.toPx(), 10.dp.toPx()),
                        CornerRadius(1.dp.toPx()),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(end = DetailGutter)) {
            val style = GlowType.caption.copy(color = DetailAxisInk)
            Text(labels.first, Modifier.weight(1f), style = style, color = DetailAxisInk)
            Text(labels.second, style = style, color = DetailAxisInk)
            Text(
                labels.third, Modifier.weight(1f), style = style, color = DetailAxisInk,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
        }
    }
}

/* ------------------------------------------- Goal bar ----------------------------------------------- */

/**
 * A count's progress (board HaloDetailSteps): a solid 6 dp bar in the metric's [color] on white 0.12, filled to [share]
 * of the goal, with a white 3 by 14 mark at the goal, then "of 8,000" ([goalText]) under it in 13 white 0.6.
 */
@Composable
internal fun DetailGoalBar(share: Float, color: Color, goalText: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(top = Space.S8), verticalArrangement = Arrangement.spacedBy(Space.S8)) {
        Canvas(Modifier.fillMaxWidth().height(14.dp)) {
            val goalAt = size.width * GOAL_MARK
            val y = (size.height - 6.dp.toPx()) / 2f
            drawRoundRect(Ink.Edge, Offset(0f, y), Size(size.width, 6.dp.toPx()), CornerRadius(3.dp.toPx()))
            // The bar is scaled so the goal sits at the mark, and a bar past the goal runs on past it.
            val w = (size.width * (share.coerceIn(0f, 1.25f) * GOAL_MARK)).coerceAtLeast(0f)
            if (w > 0f) drawRoundRect(color, Offset(0f, y), Size(w, 6.dp.toPx()), CornerRadius(3.dp.toPx()))
            drawRoundRect(Color.White, Offset(goalAt - 1.5.dp.toPx(), 0f), Size(3.dp.toPx(), 14.dp.toPx()), CornerRadius(2.dp.toPx()))
        }
        Text(goalText, color = Ink.Secondary, style = GlowType.secondary)
    }
}

/** Where the goal's mark stands along the bar (board HaloDetailSteps: 80%). */
private const val GOAL_MARK = 0.8f

/** The three marks under a score's drivers: Worse, Your normal, Better. */
@Composable
@androidx.compose.runtime.ReadOnlyComposable
internal fun scaleWords(): Triple<String, String, String> = Triple(
    androidx.compose.ui.res.stringResource(org.openstrap.app.R.string.glow_detail_worse),
    androidx.compose.ui.res.stringResource(org.openstrap.app.R.string.glow_detail_your_normal),
    androidx.compose.ui.res.stringResource(org.openstrap.app.R.string.glow_detail_better),
)

/**
 * A goal across a chart: a dashed line in white 0.35 (boards HaloDetailSteps, HaloCharts: the goal is the one dashed mark on
 * a detail). The only dash in the chart code, so the solid-lines rule has one place to look.
 */
internal fun DrawScope.dashedGoalLine(y: Float, right: Float) = drawLine(
    Ink.Disabled, Offset(0f, y), Offset(right, y), 1.dp.toPx(),
    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
)
