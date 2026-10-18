package org.openstrap.app.ui.glow

import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.DrawResult
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.openstrap.app.ui.components.pressClickable

/**
 * A card in the glow language (see [GlowStyle]). Layers, back to front: rim-coloured fill, rim light,
 * second-tone band, dark core, [content], film grain, hairline edge.
 *
 * The dark core moves with the style's [GlowMotion] (a drift, a heartbeat, a breath, a stride), each
 * card on its own random path. It holds still when the system's animations are off.
 *
 * Built to stay cheap while scrolling: everything blurred is rendered once per style and size, off the
 * main thread, into GPU bitmaps that are cached across cards (see [GlowCache]). The core moves on its
 * own layers by changing their position, scale and opacity only, so a moving card never re-draws its
 * text or charts and never re-blurs anything.
 */
/**
 * Whether glow cards should hold still right now; a scrolling screen provides its scroll state, so
 * the cores pause while the list moves and the scroll gets every frame to itself.
 */
val LocalGlowPaused = staticCompositionLocalOf<() -> Boolean> { { false } }

@Composable
fun GlowCard(
    style: GlowStyle,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = GlowLayout.CardPadding,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = remember(style.radius) { RoundedCornerShape(style.radius) }
    val palette = remember(style) { GlowPalette(style) }
    val slab = remember(palette) { lerp(palette.mid, Color.Black, 0.55f) }
    // Each card samples the grain tile from its own spot, so neighbouring cards never share a pattern.
    val grainSeed = remember { Random.nextInt(GRAIN_TILE * GRAIN_TILE) }
    val drift = remember { CoreDrift(Random) }
    val seconds = remember { mutableFloatStateOf(0f) }
    val context = LocalContext.current
    val density = LocalDensity.current
    val animate = style.motion != GlowMotion.Still && remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    val paused = LocalGlowPaused.current
    if (animate) LaunchedEffect(Unit) {
        // Time only advances while the card isn't paused, so the core picks up where it stopped.
        var last = withFrameNanos { it }
        while (true) {
            if (paused()) {
                snapshotFlow { paused() }.first { !it }
                last = withFrameNanos { it }
            }
            withFrameNanos { now ->
                seconds.floatValue += (now - last) / 1e9f
                last = now
            }
        }
    }

    var size by remember { mutableStateOf(IntSize.Zero) }
    // The motion doesn't change how the card looks at rest, so it isn't part of the cache key.
    val key = GlowCache.Key(style.copy(motion = GlowMotion.Still), size, density.density)
    val bitmaps by produceState(GlowCache.peek(key), key) {
        // Always the layers for this key: when the card changes size, the old ones no longer fit.
        value = GlowCache.peek(key)
        if (key.size == IntSize.Zero || value != null) return@produceState
        value = withContext(Dispatchers.Default) { GlowCache.get(key) }
    }

    Box(
        modifier
            .let { if (onClick != null) it.pressClickable(onClick, shape) else it }
            // The slab's edge: a darker copy of the card showing underneath, for a 3D look.
            .let {
                if (style.depth > 0.dp) it.drawBehind {
                    val d = style.depth.toPx()
                    drawRoundRect(slab, topLeft = Offset(0f, d), size = this.size, cornerRadius = CornerRadius(style.radius.toPx()))
                } else it
            }
            .clip(shape)
            .onSizeChanged { size = it },
        // Content gets the card's full size, e.g. to centre itself in a fixed-height card.
        propagateMinConstraints = true,
    ) {
        val b = bitmaps
        // Base: fill and rim light. Until its bitmap is ready, the plain mid colour.
        Spacer(
            Modifier.matchParentSize().drawBehind {
                if (b == null) drawRect(palette.mid) else drawIntoCanvas { it.nativeCanvas.drawBitmap(b.base, null, b.baseDst, FILTER) }
            },
        )
        if (b != null) {
            // The core on its own layer: moved and scaled, never redrawn.
            CoreLayer(b, style) { drift.at(seconds.floatValue, style.motion) }
        }
        Box(Modifier.padding(contentPadding), content = content)
        // Grain and the hairline edge, on top of everything; static.
        Spacer(Modifier.matchParentSize().drawWithCache { overlay(style, grainSeed) })
    }
}

/** The pre-blurred core, moved by the motion through layer properties only. */
@Composable
private fun BoxScope.CoreLayer(b: GlowBitmaps, style: GlowStyle, motion: () -> Drift) {
    Spacer(
        Modifier
            .matchParentSize()
            .graphicsLayer {
                // Read here, so motion only updates this layer's properties; nothing is redrawn.
                val m = motion()
                translationX = m.dx * size.width
                translationY = m.dy * size.height
                scaleX = m.scale
                scaleY = m.scale
                transformOrigin = TransformOrigin(style.coreX, style.coreY)
                alpha = (style.coreOpacity * m.fade).coerceIn(0f, 1f)
                // Fade by scaling each pixel's alpha instead of rendering the layer offscreen first.
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }
            .drawBehind { drawIntoCanvas { it.nativeCanvas.drawBitmap(b.core, null, b.coreDst, FILTER) } },
    )
}

private val FILTER = Paint(Paint.FILTER_BITMAP_FLAG)

/** Grain and the hairline edge with its gloss. */
private fun CacheDrawScope.overlay(style: GlowStyle, grainSeed: Int): DrawResult {
    val grainPaint = Paint().apply {
        shader = grainShader(grainSeed)
        alpha = (style.grain.coerceIn(0f, 1f) * 255).toInt()
    }
    val hairline = Stroke(1.dp.toPx())
    val outline = RoundedCornerShape(style.radius).createOutline(size, layoutDirection, this)
    // A glossy edge: a lighter hairline along the top fading out down the sides.
    val gloss = Brush.verticalGradient(0f to Color.White.copy(alpha = 0.35f), 0.25f to Color.White.copy(alpha = 0.08f), 1f to Color.White.copy(alpha = 0.04f))
    return onDrawBehind {
        if (style.grain > 0f) drawIntoCanvas { it.nativeCanvas.drawPaint(grainPaint) }
        drawOutline(outline, Color.White.copy(alpha = 0.07f), style = hairline)
        drawOutline(outline, gloss, style = hairline)
    }
}

/** A card's pre-rendered layers, and where to draw them (card coordinates). */
private class GlowBitmaps(
    val base: android.graphics.Bitmap,
    val core: android.graphics.Bitmap,
    val baseDst: RectF,
    val coreDst: RectF,
)

/**
 * Rendered glow layers, shared by every card with the same look and size. Rendering blurs is the
 * expensive part of a glow card; with the cache, a card scrolling back into view costs nothing.
 */
private object GlowCache {
    data class Key(val style: GlowStyle, val size: IntSize, val density: Float)

    private val entries = object : LinkedHashMap<Key, GlowBitmaps>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, GlowBitmaps>?) = size > MAX_ENTRIES
    }

    fun peek(key: Key): GlowBitmaps? = synchronized(entries) { entries[key] }

    fun get(key: Key): GlowBitmaps {
        peek(key)?.let { return it }
        val built = render(key.style, key.size.width.toFloat(), key.size.height.toFloat(), Density(key.density))
        return synchronized(entries) { entries.getOrPut(key) { built } }
    }

    /** Enough for every card on a screen plus the ones just scrolled past. */
    private const val MAX_ENTRIES = 10
}

/** Renders the base and the core layer at half resolution (blur hides it), as GPU bitmaps. */
private fun render(style: GlowStyle, w: Float, h: Float, density: Density): GlowBitmaps = with(density) {
    val p = GlowPalette(style)
    val r = style.radius.toPx()
    val area = Size(w, h)

    val base = halfResBitmap(w, h, 0f) { canvas ->
        canvas.drawRect(0f, 0f, w, h, Paint().apply { shader = fillShader(style, p, h) })
        if (style.glowStrength > 0f) {
            val g = style.glowSize.toPx()
            val k = style.glowStrength
            // Rim light: CSS-style inset shadows (one leaning toward the lit edge, one all the way round).
            if (style.lightFrom != LightFrom.All) {
                val dy = if (style.lightFrom == LightFrom.Top) g * 0.6f else -g * 0.6f
                insetShadow(area, r, dy, blur = g * 1.2f, spread = -g * 0.5f, p.light.copy(alpha = k)).let { (path, paint) -> canvas.drawPath(path, paint) }
            }
            insetShadow(area, r, 0f, blur = g, spread = g * 0.1f, p.light.copy(alpha = 0.6f * k)).let { (path, paint) -> canvas.drawPath(path, paint) }
            // Bevel: a thick band of light all the way round, like the edge of a glossy slab.
            if (style.bevel > 0f) {
                val b = minOf(w, h) * 0.075f * style.bevel
                insetShadow(area, r, 0f, blur = b * 1.6f, spread = b * 0.45f, p.light.copy(alpha = 0.85f * k)).let { (path, paint) -> canvas.drawPath(path, paint) }
            }
        }
    }

    // Dark core and the second-tone band around it, pre-blurred.
    val cw = w * style.coreWidth
    val ch = h * style.coreHeight
    val core = RectF(w * style.coreX - cw / 2, h * style.coreY - ch / 2, 0f, 0f).apply { right = left + cw; bottom = top + ch }
    val coreRadius = (r - 8.dp.toPx()).coerceAtLeast(0f)
    val band = style.toneWidth.toPx()
    val coreBlur = style.coreBlur.toPx()
    // Just enough room round the card for the core to drift and swell into view.
    val margin = maxOf(w, h) * 0.08f
    val coreLayer = halfResBitmap(w, h, margin) { canvas ->
        val blur = blurFilter(coreBlur)
        val tonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.tone.toArgb(); maskFilter = blur }
        val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.core.toArgb(); maskFilter = blur }
        val toneRect = RectF(core.left - band, core.top - band, core.right + band, core.bottom + band)
        if (band > 0f) when (style.coreShape) {
            CoreShape.Rounded -> canvas.drawRoundRect(toneRect, coreRadius + band, coreRadius + band, tonePaint)
            CoreShape.Radial -> canvas.drawOval(toneRect, tonePaint)
        }
        when (style.coreShape) {
            CoreShape.Rounded -> canvas.drawRoundRect(core, coreRadius, coreRadius, corePaint)
            CoreShape.Radial -> canvas.drawOval(core, corePaint)
        }
    }
    GlowBitmaps(
        base = base,
        core = coreLayer,
        baseDst = RectF(0f, 0f, w, h),
        coreDst = RectF(-margin, -margin, w + margin, h + margin),
    )
}

/** A bitmap at half the card's resolution covering the card plus [margin] on every side; [draw] gets card coordinates. */
private fun halfResBitmap(w: Float, h: Float, margin: Float, draw: (android.graphics.Canvas) -> Unit): android.graphics.Bitmap {
    val scale = 0.5f
    val bmp = android.graphics.Bitmap.createBitmap(
        ((w + margin * 2) * scale).roundToInt().coerceAtLeast(1),
        ((h + margin * 2) * scale).roundToInt().coerceAtLeast(1),
        android.graphics.Bitmap.Config.ARGB_8888,
    )
    val canvas = android.graphics.Canvas(bmp)
    canvas.scale(scale, scale)
    canvas.translate(margin, margin)
    draw(canvas)
    // Upload to the GPU now, on this background thread, rather than on the first frame that draws it.
    return bmp.copy(android.graphics.Bitmap.Config.HARDWARE, false)?.also { bmp.recycle() } ?: bmp
}

/**
 * The card's base fill: rim colour at the lit edge melting into the mid colour. The melt is eased
 * (fast near the edge, then slowly settling, with no corner where it ends), so the light reads as a
 * soft glow but has mostly faded by the title row.
 */
private fun fillShader(style: GlowStyle, p: GlowPalette, h: Float): Shader {
    if (style.lightFrom == LightFrom.All) {
        return android.graphics.LinearGradient(0f, 0f, 0f, h, p.mid.toArgb(), p.mid.toArgb(), Shader.TileMode.CLAMP)
    }
    val reach = 0.4f
    val steps = 12
    // Ease-out: 1 - (1 - x)^3 runs from light to mid and arrives with zero slope, so there's no seam.
    val fade = List(steps + 1) { i -> i / steps.toFloat() }
    val colors = fade.map { x -> lerp(p.light, p.mid, 1f - (1f - x).let { it * it * it }).toArgb() } + p.mid.toArgb()
    val stops = fade.map { it * reach } + 1f
    val (c, st) = if (style.lightFrom == LightFrom.Top) colors to stops else colors.reversed() to stops.map { 1f - it }.reversed()
    return android.graphics.LinearGradient(0f, 0f, 0f, h, c.toIntArray(), st.toFloatArray(), Shader.TileMode.CLAMP)
}

/**
 * The equivalent of CSS `box-shadow: inset 0 [dy] [blur] [spread] [color]`: everything outside the
 * card's shape (shifted by [dy], shrunk by [spread]), blurred and clipped to the card.
 */
private fun insetShadow(size: Size, radius: Float, dy: Float, blur: Float, spread: Float, color: Color): Pair<Path, Paint> {
    val pad = blur * 2 + abs(dy) + abs(spread) + 4f
    val holeRadius = (radius - spread).coerceAtLeast(0f)
    val path = Path().apply {
        fillType = Path.FillType.EVEN_ODD
        addRect(-pad, -pad, size.width + pad, size.height + pad, Path.Direction.CW)
        addRoundRect(spread, spread + dy, size.width - spread, size.height - spread + dy, holeRadius, holeRadius, Path.Direction.CW)
    }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        maskFilter = blurFilter(blur / 2f) // CSS shadow blur is twice the standard deviation
    }
    return path to paint
}

/** A Gaussian blur with standard deviation [sigma] px (Android's radius is ~1.73 × sigma). */
private fun blurFilter(sigma: Float): BlurMaskFilter? =
    if (sigma > 0.5f) BlurMaskFilter((sigma - 0.5f) / 0.57735f, BlurMaskFilter.Blur.NORMAL) else null

/** Where the core is at a moment: an offset (fractions of the card), and size and blur multipliers. */
/** How the dark core sits: offset (fractions of the card), scale, and [fade] on top of the style's core opacity. */
private class Drift(val dx: Float, val dy: Float, val scale: Float, val fade: Float = 1f)

/**
 * The core's motion over time. Underneath every [GlowMotion] is a never-repeating wander: each axis is
 * two sine waves with unrelated periods (10–24 s), and every card gets its own periods and phases.
 */
private class CoreDrift(private val random: Random) {
    private fun wave() = Wave(period = 10f + random.nextFloat() * 14f, phase = random.nextFloat() * 2f * PI.toFloat())
    private val x = wave() to wave()
    private val y = wave() to wave()
    private val breath = wave()
    /** Where in its cycle each card starts, so two heart cards don't beat in lockstep. */
    private val offset = random.nextFloat() * 10f

    fun at(time: Float, motion: GlowMotion): Drift {
        val t = time + offset
        return when (motion) {
            GlowMotion.Drift -> wander(t, amount = 1f)
            GlowMotion.Still -> Drift(0f, 0f, 1f)
            is GlowMotion.Heartbeat -> {
                // An ECG beat: on each spike the dark core pulls in and thins, so the colour flares inward.
                // The small dips either side of the spike push it out a touch, like the trace dipping below the line.
                val base = wander(t, amount = 0.4f)
                val e = ecg(t * motion.bpm / 60f)
                Drift(base.dx, base.dy, scale = 1f - 0.04f * e, fade = 1f - 0.2f * e.coerceAtLeast(0f))
            }
            is GlowMotion.Breath -> {
                // In: swell and rise like a chest. Out: slower, settling back.
                val base = wander(t, amount = 0.5f)
                val b = inhale(t * motion.perMinute / 60f)
                Drift(base.dx, base.dy - 0.01f * b, scale = 1f + 0.045f * b)
            }
            is GlowMotion.Stride -> {
                // The same wander twice as fast, plus a light footfall bounce at the cadence.
                val e = motion.energy.coerceIn(0f, 1f)
                val base = wander(t * 2f, amount = 0.4f + 0.4f * e)
                val step = abs(sin(PI.toFloat() * t * motion.cadence / 60f))
                Drift(base.dx, base.dy - 0.01f * e * step, scale = base.scale)
            }
        }
    }

    private fun wander(t: Float, amount: Float) = Drift(
        dx = 0.035f * amount * (0.65f * x.first(t) + 0.35f * x.second(t)),
        dy = 0.03f * amount * (0.65f * y.first(t) + 0.35f * y.second(t)),
        // Breathing in: the core swells a little; breathing out, it settles.
        scale = 1f + 0.025f * amount * breath(t),
    )

    private class Wave(val period: Float, val phase: Float) {
        operator fun invoke(t: Float) = sin(2f * PI.toFloat() * t / period + phase)
    }
}

/**
 * One heartbeat as an ECG trace (phase in beats), about -0.25..1: a small P wave, the QRS complex (a dip,
 * the sharp R spike, a deeper dip) and a broad, gentle T wave, then flat until the next beat.
 */
private fun ecg(phase: Float): Float {
    val f = phase - floor(phase)
    fun bump(at: Float, width: Float) = exp(-((f - at) / width).let { it * it })
    return 0.12f * bump(0.10f, 0.035f) -
        0.10f * bump(0.215f, 0.012f) + bump(0.24f, 0.018f) - 0.25f * bump(0.265f, 0.014f) +
        0.30f * bump(0.52f, 0.07f)
}

/** One breath over a cycle (phase in breaths): eased inhale over 40%, longer eased exhale, 0..1. */
private fun inhale(phase: Float): Float {
    val f = phase - floor(phase)
    fun ease(x: Float) = (1f - cos(PI.toFloat() * x)) / 2f
    return if (f < 0.4f) ease(f / 0.4f) else 1f - ease((f - 0.4f) / 0.6f)
}

private const val GRAIN_TILE = 512

/**
 * Film grain: a large tile of sparse specks, one device pixel each, so it reads as texture rather
 * than dots. [seed] shifts where each card starts in the tile, which hides the repeat.
 */
private fun grainShader(seed: Int): Shader =
    BitmapShader(grainBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
        setLocalMatrix(android.graphics.Matrix().apply {
            setTranslate((seed % GRAIN_TILE).toFloat(), (seed / GRAIN_TILE).toFloat())
        })
    }

private val grainBitmap: android.graphics.Bitmap by lazy {
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
    android.graphics.Bitmap.createBitmap(pixels, n, n, android.graphics.Bitmap.Config.ARGB_8888)
}
