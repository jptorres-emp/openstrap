package org.openstrap.app.ui.glow.draw

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.random.Random
import org.openstrap.app.ui.glow.CoreShape
import org.openstrap.app.ui.glow.DotSize
import org.openstrap.app.ui.glow.GlowStyle
import org.openstrap.app.ui.glow.stageColor

/*
 * The shared drawing on an android.graphics.Canvas: the same light, dots and charts the app's cards draw, for places
 * Compose can't draw, the home screen widgets (widgets spec 6.2). GlowCard renders its cached layers with these very
 * functions; the rest paint the same specs the Compose side does. Everything is in px.
 */

/* ------------------------------------------------------------------------------------------------ */
/* Glow light                                                                                        */
/* ------------------------------------------------------------------------------------------------ */

/** The glow card's base on [canvas], in card coordinates: the fill, then the rim light and bevel. */
fun drawGlowBase(canvas: Canvas, paint: GlowPaint) {
    canvas.drawRect(0f, 0f, paint.width, paint.height, Paint().apply { shader = paint.fill.shader() })
    if (paint.rims.isEmpty()) return
    paint.rimClip?.let { canvas.clipRect(it.left, it.top, it.right, it.bottom) }
    paint.rims.forEach { rim ->
        val (path, p) = insetShadow(paint.width, paint.height, paint.radius, rim)
        canvas.drawPath(path, p)
    }
}

/**
 * The glow card's dark core and its second-tone band on [canvas], in card coordinates; the layer reaches
 * [GlowPaint.coreMargin] past the card. Drawn at rest: the heartbeat's motion is the card's own.
 */
fun drawGlowCore(canvas: Canvas, paint: GlowPaint) {
    when (val core = paint.core) {
        is GlowCore.Level -> {
            val m = paint.coreMargin
            canvas.drawRect(-m, -m, paint.width + m, paint.height + m, Paint().apply { shader = core.gradient.shader() })
        }
        is GlowCore.Shapes -> {
            val blur = blurFilter(core.blur)
            val tonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = core.toneColor.toArgb(); maskFilter = blur }
            val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = core.coreColor.toArgb(); maskFilter = blur }
            core.tone?.let { t ->
                val toneRect = RectF(t.left, t.top, t.right, t.bottom)
                when (core.shape) {
                    CoreShape.Rounded -> canvas.drawRoundRect(toneRect, core.toneRadius, core.toneRadius, tonePaint)
                    CoreShape.Radial -> canvas.drawOval(toneRect, tonePaint)
                }
            }
            val box = RectF(core.core.left, core.core.top, core.core.right, core.core.bottom)
            when (core.shape) {
                CoreShape.Rounded -> canvas.drawRoundRect(box, core.coreRadius, core.coreRadius, corePaint)
                CoreShape.Radial -> canvas.drawOval(box, corePaint)
            }
        }
    }
}

/**
 * The glow card's top layer on [canvas]: film grain from [grainSeed]'s spot in the tile, the hairline and its gloss;
 * on hold only the outline. The card's clip is the caller's.
 */
fun drawGlowEdge(canvas: Canvas, paint: GlowPaint, grainSeed: Int = 0) {
    val w = paint.width
    val h = paint.height
    val r = paint.radius
    paint.outline?.let { color ->
        canvas.drawRoundRect(0f, 0f, w, h, r, r, stroke(color, paint.outlineWidth))
        return
    }
    if (paint.grainAlpha > 0) canvas.drawPaint(Paint().apply { shader = grainShader(grainSeed); alpha = paint.grainAlpha })
    canvas.drawRoundRect(0f, 0f, w, h, r, r, stroke(paint.hairline, paint.hairlineWidth))
    val g = paint.gloss
    val gloss = LinearGradient(0f, 0f, if (g.across) w else 0f, if (g.across) 0f else h, g.stops.map { it.second.toArgb() }.toIntArray(), g.stops.map { it.first }.toFloatArray(), Shader.TileMode.CLAMP)
    canvas.drawRoundRect(0f, 0f, w, h, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = paint.hairlineWidth; shader = gloss })
}

/**
 * A whole glow card's background, [style] on a card [width] by [height] px at [density], clipped to its corners:
 * what a GlowCard shows behind its content, the core at rest. The style's mood must be set.
 */
fun drawGlow(canvas: Canvas, style: GlowStyle, width: Float, height: Float, density: Float, grainSeed: Int = 0) {
    val paint = glowPaint(style, width, height, density)
    canvas.save()
    canvas.clipPath(Path().apply { addRoundRect(0f, 0f, width, height, paint.radius, paint.radius, Path.Direction.CW) })
    if (paint.outline != null) {
        canvas.drawColor(android.graphics.Color.BLACK)
    } else {
        canvas.save()
        drawGlowBase(canvas, paint)
        canvas.restore()
        val layered = paint.coreOpacity < 1f
        if (layered) canvas.saveLayerAlpha(null, (paint.coreOpacity.coerceIn(0f, 1f) * 255).toInt())
        drawGlowCore(canvas, paint)
        if (layered) canvas.restore()
    }
    drawGlowEdge(canvas, paint, grainSeed)
    canvas.restore()
}

/** The everything-outside-the-hole path of an [InsetShadow] on a card [w] by [h] px with corners [radius], and its paint. */
private fun insetShadow(w: Float, h: Float, radius: Float, s: InsetShadow): Pair<Path, Paint> {
    val pad = s.blur * 2 + abs(s.dx) + abs(s.dy) + abs(s.spread) + 4f
    val holeRadius = (radius - s.spread).coerceAtLeast(0f)
    val path = Path().apply {
        fillType = Path.FillType.EVEN_ODD
        addRect(-pad, -pad, w + pad, h + pad, Path.Direction.CW)
        addRoundRect(s.spread + s.dx, s.spread + s.dy, w - s.spread + s.dx, h - s.spread + s.dy, holeRadius, holeRadius, Path.Direction.CW)
    }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = s.color.toArgb()
        maskFilter = blurFilter(s.blur / 2f) // CSS shadow blur is twice the standard deviation
    }
    return path to paint
}

/** A Gaussian blur with standard deviation [sigma] px (Android's radius is ~1.73 × sigma). */
private fun blurFilter(sigma: Float): BlurMaskFilter? =
    if (sigma > 0.5f) BlurMaskFilter((sigma - 0.5f) / 0.57735f, BlurMaskFilter.Blur.NORMAL) else null

/** The Android shader for a gradient spec. */
fun LinearGradientSpec.shader(): Shader =
    if (stops == null && colors.size == 2) LinearGradient(x0, y0, x1, y1, colors[0].toArgb(), colors[1].toArgb(), Shader.TileMode.CLAMP)
    else LinearGradient(x0, y0, x1, y1, colors.map { it.toArgb() }.toIntArray(), stops?.toFloatArray(), Shader.TileMode.CLAMP)

private const val GRAIN_TILE = 512

/** Where a card starts in the grain tile, so neighbouring cards never share a pattern. */
fun randomGrainSeed(random: Random = Random): Int = random.nextInt(GRAIN_TILE * GRAIN_TILE)

/**
 * Film grain: a large tile of sparse specks, one device pixel each, so it reads as texture rather than dots. [seed]
 * shifts where each card starts in the tile, which hides the repeat.
 */
internal fun grainShader(seed: Int): Shader =
    BitmapShader(grainBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
        setLocalMatrix(Matrix().apply { setTranslate((seed % GRAIN_TILE).toFloat(), (seed / GRAIN_TILE).toFloat()) })
    }

private val grainBitmap: Bitmap by lazy {
    val n = GRAIN_TILE
    val random = Random(7)
    val pixels = IntArray(n * n) {
        // Most pixels stay clear; the rest get a random strength, and some darken instead of lighten.
        val v = random.nextFloat()
        if (v < 0.55f) 0 else {
            val alpha = (((v - 0.55f) / 0.45f) * 255).toInt().coerceIn(0, 255)
            (alpha shl 24) or (if (random.nextFloat() < 0.35f) 0x000000 else 0xFFFFFF)
        }
    }
    Bitmap.createBitmap(pixels, n, n, Bitmap.Config.ARGB_8888)
}

/* ------------------------------------------------------------------------------------------------ */
/* Edge light                                                                                        */
/* ------------------------------------------------------------------------------------------------ */

/** An edge card's background on [canvas]: the surface, the light, the hairline. */
fun drawEdge(canvas: Canvas, paint: EdgePaint) {
    val w = paint.width
    val h = paint.height
    val r = paint.radius
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    canvas.drawRoundRect(0f, 0f, w, h, r, r, fill.apply { color = paint.surface.toArgb() })
    canvas.drawRoundRect(0f, 0f, w, h, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = paint.glow.shader() })
    val s = paint.hairlineWidth
    canvas.drawRoundRect(s / 2, s / 2, w - s / 2, h - s / 2, r - s / 2, r - s / 2, stroke(paint.hairline, s))
}

/* ------------------------------------------------------------------------------------------------ */
/* Dots                                                                                              */
/* ------------------------------------------------------------------------------------------------ */

/** [layout]'s dots on [canvas] in [color], its top left at ([left], [top]) px. */
fun drawDots(canvas: Canvas, layout: DotLayout, color: Color, left: Float = 0f, top: Float = 0f) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toArgb() }
    layout.centers.forEach { c -> canvas.drawCircle(left + c.x, top + c.y, layout.radius, p) }
}

/** [text] in [dot] dots at [density] as a bitmap just big enough for it. */
fun renderDots(text: String, dot: DotSize, density: Float, color: Color = Color.White): Bitmap {
    val layout = dotLayout(text, dot, density)
    return renderToBitmap(layout.width * density, layout.height * density) { drawDots(it, layout, color) }
}

/* ------------------------------------------------------------------------------------------------ */
/* Charts                                                                                            */
/* ------------------------------------------------------------------------------------------------ */

/** The Android path for [ops]. */
fun androidPath(ops: List<PathOp>): Path = Path().apply {
    ops.forEach { op ->
        when (op) {
            is PathOp.Move -> moveTo(op.x, op.y)
            is PathOp.Line -> lineTo(op.x, op.y)
            is PathOp.Cubic -> cubicTo(op.x1, op.y1, op.x2, op.y2, op.x3, op.y3)
        }
    }
}

/**
 * A day's line (GlowLineChart without its text): grid rows, your normal's band, the line with its halo and soft fill,
 * the dot on the latest reading, in [color]. [showAverage] adds the solid average line.
 */
fun drawLineChart(canvas: Canvas, g: LineChartGeometry, color: Color, density: Float, showAverage: Boolean = false) {
    val hair = ChartInk.HAIRLINE_DP * density
    g.grid.forEach { y -> canvas.drawLine(0f, y, g.width, y, stroke(ChartInk.Grid, hair)) }
    if (g.bandTopLeft != null && g.bandSize != null) {
        val t = g.bandTopLeft
        canvas.drawRect(t.x, t.y, t.x + g.bandSize.width, t.y + g.bandSize.height, fill(color.copy(alpha = ChartInk.NORMAL_BAND_ALPHA)))
    }
    g.dots.forEach { canvas.drawCircle(it.x, it.y, LineChartLook.LONE_DOT_DP * density, fill(color)) }
    g.runs.forEach { pts ->
        val line = androidPath(smoothOps(pts))
        val area = Path().apply { addPath(line); lineTo(pts.last().x, g.plotBottom); lineTo(pts.first().x, g.plotBottom); close() }
        canvas.drawPath(area, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, g.top, 0f, g.plotBottom, color.copy(alpha = LineChartLook.FILL_ALPHA).toArgb(), Color.Transparent.toArgb(), Shader.TileMode.CLAMP)
        })
        canvas.drawPath(line, roundStroke(color.copy(alpha = LineChartLook.HALO_ALPHA), LineChartLook.HALO_DP * density))
        canvas.drawPath(line, roundStroke(color, LineChartLook.LINE_DP * density))
    }
    if (showAverage) canvas.drawLine(0f, g.averageY, g.width, g.averageY, stroke(ChartInk.Target, hair))
    canvas.drawCircle(g.last.x, g.last.y, LineChartLook.END_HALO_DP * density, fill(color.copy(alpha = LineChartLook.END_HALO_ALPHA)))
    canvas.drawCircle(g.last.x, g.last.y, LineChartLook.END_DOT_DP * density, fill(color))
}

/** The ghost line (steps or Energy so far): your usual by now in white at 35%, today in [color] ending in a dot. */
fun drawGhostLine(canvas: Canvas, g: GhostLineGeometry, color: Color, density: Float) {
    g.usual?.let { canvas.drawPath(androidPath(polylineOps(it)), roundStroke(Color.White.copy(alpha = GhostLineLook.USUAL_ALPHA), GhostLineLook.USUAL_DP * density)) }
    if (g.today.isEmpty()) return
    if (g.today.size >= 2) canvas.drawPath(androidPath(polylineOps(g.today)), roundStroke(color, GhostLineLook.LINE_DP * density))
    val end = g.today.last()
    canvas.drawCircle(end.x, end.y, g.endDot, fill(color))
}

/**
 * The hypnogram without its names and times: a hairline per row and the stages as round bands, each in its row's
 * colour from [rowColors] (Awake at row 0), or white brighter the deeper, joined by thin lines, when it's null.
 */
fun drawHypnogram(canvas: Canvas, g: HypnogramGeometry, density: Float, rowColors: List<Color>? = null) {
    val hair = ChartInk.HAIRLINE_DP * density
    g.rowY.forEach { y -> canvas.drawLine(g.gridX0, y, g.width, y, stroke(ChartInk.Grid, hair)) }
    if (rowColors == null) g.joins.forEach { j -> canvas.drawLine(j.from.x, j.from.y, j.to.x, j.to.y, stroke(Color.White.copy(alpha = HypnogramLook.JOIN_ALPHA), hair)) }
    g.bands.forEach { b ->
        val color = rowColors?.getOrNull(b.stage.row) ?: Color.White.copy(alpha = b.whiteAlpha)
        canvas.drawRoundRect(b.topLeft.x, b.topLeft.y, b.topLeft.x + b.size.width, b.topLeft.y + b.size.height, b.corner, b.corner, fill(color))
    }
}

/**
 * The stage bar (chart kit) across [width] px from ([left], [top]): [totals] (from [stageTotals]) as segments in the
 * stage colours, [heightDp] high with 2 dp gaps and 3 dp corners.
 */
fun drawStageBar(canvas: Canvas, totals: List<Pair<Int, Long>>, left: Float, top: Float, width: Float, density: Float, heightDp: Float = StageBarLook.HEIGHT_DP) {
    val h = Math.round(heightDp * density).toFloat()
    val corner = StageBarLook.CORNER_DP * density
    stageBarSegments(totals, width.toInt(), Math.round(StageBarLook.GAP_DP * density)).forEach { s ->
        canvas.drawRoundRect(left + s.left, top, left + s.left + s.width, top + h, corner, corner, fill(stageColor(s.stage)))
    }
}

/** Pill bars: the baseline hairline, then each bar, lit in white or white at 45%. */
fun drawHourPills(canvas: Canvas, g: HourPillsGeometry, width: Float, density: Float) {
    canvas.drawLine(0f, g.baselineY, width, g.baselineY, stroke(ChartInk.Grid, ChartInk.HAIRLINE_DP * density))
    g.bars.forEach { (bar, lit) -> drawPillBar(canvas, bar, if (lit) Color.White else Color.White.copy(alpha = HourPillsLook.DIM_ALPHA)) }
}

/** One pill bar in [color]. */
fun drawPillBar(canvas: Canvas, bar: PillBar, color: Color) {
    canvas.drawRoundRect(bar.topLeft.x, bar.topLeft.y, bar.topLeft.x + bar.size.width, bar.topLeft.y + bar.size.height, bar.corner, bar.corner, fill(color))
}

/* ------------------------------------------------------------------------------------------------ */
/* Bitmaps                                                                                           */
/* ------------------------------------------------------------------------------------------------ */

/**
 * A software ARGB bitmap [width] by [height] px (rounded up, at least 1), drawn by [draw]: what a widget hands to
 * RemoteViews. Transparent where [draw] leaves it.
 */
fun renderToBitmap(width: Float, height: Float, draw: (Canvas) -> Unit): Bitmap {
    val bmp = Bitmap.createBitmap(ceil(width).toInt().coerceAtLeast(1), ceil(height).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    draw(Canvas(bmp))
    return bmp
}

/** A whole glow card's background as a bitmap, [width] by [height] px. */
fun renderGlow(style: GlowStyle, width: Int, height: Int, density: Float, grainSeed: Int = 0): Bitmap =
    renderToBitmap(width.toFloat(), height.toFloat()) { drawGlow(it, style, width.toFloat(), height.toFloat(), density, grainSeed) }

/** A whole edge card's background as a bitmap. */
fun renderEdge(paint: EdgePaint): Bitmap = renderToBitmap(paint.width, paint.height) { drawEdge(it, paint) }

private fun fill(color: Color) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toArgb() }

private fun stroke(color: Color, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color.toArgb()
    style = Paint.Style.STROKE
    strokeWidth = width
}

private fun roundStroke(color: Color, width: Float) = stroke(color, width).apply {
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
}
