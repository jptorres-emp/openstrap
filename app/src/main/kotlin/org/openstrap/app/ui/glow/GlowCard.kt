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
import androidx.compose.runtime.CompositionLocalProvider
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
import org.openstrap.app.ui.components.LocalCardNumbers
import org.openstrap.app.ui.components.cardOpening
import org.openstrap.app.ui.components.pressClickable

/**
 * A card in the glow language (see [GlowStyle]). Layers, back to front: rim-coloured fill, rim light,
 * second-tone band, dark core, [content], film grain, hairline edge.
 *
 * The dark core holds still unless the style's [GlowMotion] is a [GlowMotion.Heartbeat] (live heart rate),
 * and always when the system's animations are off. The screen's [LocalGlowMood] is applied on top of the style.
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

/**
 * Whether the page holding these cards is off screen or the tab pager is moving; provided by the pager, apart from
 * [LocalGlowPaused] so a screen's own scroll state doesn't override it. Cores hold still then, so a swipe between
 * tabs gets every frame to itself and the pages kept ready either side don't animate unseen.
 */
val LocalGlowPagePaused = staticCompositionLocalOf<() -> Boolean> { { false } }

@Composable
fun GlowCard(
    style: GlowStyle,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = GlowLayout.CardPadding,
    content: @Composable BoxScope.() -> Unit,
) {
    val lit = style.copy(mood = LocalGlowMood.current)
    val shape = remember(lit.radius) { RoundedCornerShape(lit.radius) }
    val palette = remember(lit) { GlowPalette(lit) }
    val core = remember(lit) { lit.light().core }
    val slab = remember(palette) { lerp(palette.mid, Color.Black, 0.55f) }
    // Each card samples the grain tile from its own spot, so neighbouring cards never share a pattern.
    val grainSeed = remember { Random.nextInt(GRAIN_TILE * GRAIN_TILE) }
    val drift = remember { CoreDrift(Random) }
    val seconds = remember { mutableFloatStateOf(0f) }
    val context = LocalContext.current
    val density = LocalDensity.current
    // When the system's animator scale is 0 the heart doesn't beat: the core sits at its resting state.
    val animate = lit.motion is GlowMotion.Heartbeat && remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    val listPaused = LocalGlowPaused.current
    val pagePaused = LocalGlowPagePaused.current
    val paused = { listPaused() || pagePaused() }
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
    val key = GlowCache.Key(lit, size, density.density)
    val bitmaps by produceState(GlowCache.peek(key), key) {
        // Always the layers for this key: when the card changes size, the old ones no longer fit.
        value = GlowCache.peek(key)
        if (key.size == IntSize.Zero || lit.outline || value != null) return@produceState
        value = withContext(Dispatchers.Default) { GlowCache.get(key) }
    }

    // Tapped, the card grows into what it opens (spec 3.5).
    val opening = cardOpening(onClick, shape)
    Box(
        modifier
            .then(opening?.modifier ?: Modifier)
            .let { if (onClick != null) it.pressClickable(opening?.onClick ?: onClick, shape) else it }
            // The slab's edge: a darker copy of the card showing underneath, for a 3D look.
            .let {
                if (lit.depth > 0.dp) it.drawBehind {
                    val d = lit.depth.toPx()
                    drawRoundRect(slab, topLeft = Offset(0f, d), size = this.size, cornerRadius = CornerRadius(lit.radius.toPx()))
                } else it
            }
            .clip(shape)
            .onSizeChanged { size = it },
        // Content gets the card's full size, e.g. to centre itself in a fixed-height card.
        propagateMinConstraints = true,
    ) {
        val b = bitmaps.takeUnless { lit.outline }
        // Base: fill and rim light. Until its bitmap is ready, the plain mid colour; an outline card is black.
        Spacer(
            Modifier.matchParentSize().drawBehind {
                when {
                    lit.outline -> drawRect(Color.Black)
                    b == null -> drawRect(palette.mid)
                    else -> drawIntoCanvas { it.nativeCanvas.drawBitmap(b.base, null, b.baseDst, FILTER) }
                }
            },
        )
        if (b != null) {
            // The core on its own layer: moved and scaled, never redrawn.
            CoreLayer(b, lit, core) { drift.at(seconds.floatValue, lit.motion) }
        }
        CompositionLocalProvider(LocalCardNumbers provides opening?.numbers) {
            Box(Modifier.padding(contentPadding), content = content)
        }
        // Grain and the hairline edge, on top of everything; static.
        Spacer(Modifier.matchParentSize().drawWithCache { overlay(lit, grainSeed) })
    }
}

/** The pre-blurred core, moved by the motion through layer properties only. */
@Composable
private fun BoxScope.CoreLayer(b: GlowBitmaps, style: GlowStyle, core: CoreBox, motion: () -> Drift) {
    Spacer(
        Modifier
            .matchParentSize()
            .graphicsLayer {
                // Read here, so motion only updates this layer's properties; nothing is redrawn.
                val m = motion()
                translationX = m.dx * size.width
                translationY = m.dy * size.height
                // A squash widens the core a touch and flattens it as much: a squeeze, not a resize.
                scaleX = m.scale * (1f + m.squash)
                scaleY = m.scale * (1f - m.squash)
                transformOrigin = TransformOrigin(core.x, core.y)
                alpha = (style.coreOpacity * m.fade).coerceIn(0f, 1f)
                // Fade by scaling each pixel's alpha instead of rendering the layer offscreen first.
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }
            .drawBehind { drawIntoCanvas { it.nativeCanvas.drawBitmap(b.core, null, b.coreDst, FILTER) } },
    )
}

private val FILTER = Paint(Paint.FILTER_BITMAP_FLAG)

/** An outline card's edge. */
private val OUTLINE_WIDTH = 1.5.dp
private const val OUTLINE_ALPHA = 0.7f

/** Grain and the hairline edge with its gloss. */
private fun CacheDrawScope.overlay(style: GlowStyle, grainSeed: Int): DrawResult {
    val grainPaint = Paint().apply {
        shader = grainShader(grainSeed)
        alpha = (style.grain.coerceIn(0f, 1f) * 255).toInt()
    }
    val hairline = Stroke(1.dp.toPx())
    val outline = RoundedCornerShape(style.radius).createOutline(size, layoutDirection, this)
    // A glossy edge: a lighter hairline along the top fading out down the sides. On a level card it follows the
    // light instead: glossy along the lit edge, plain hairline on the unlit side.
    val light = style.light()
    val gloss = if (light.level != null && !light.fromAbove) {
        Brush.verticalGradient(0f to Color.White.copy(alpha = 0.04f), 0.75f to Color.White.copy(alpha = 0.08f), 1f to Color.White.copy(alpha = 0.35f))
    } else {
        Brush.verticalGradient(0f to Color.White.copy(alpha = 0.35f), 0.25f to Color.White.copy(alpha = 0.08f), 1f to Color.White.copy(alpha = 0.04f))
    }
    // On hold: only the edge, in the card's colour.
    if (style.outline) return onDrawBehind { drawOutline(outline, style.color.copy(alpha = OUTLINE_ALPHA), style = Stroke(OUTLINE_WIDTH.toPx())) }
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
    val light = style.light()
    val r = style.radius.toPx()
    val area = Size(w, h)

    val base = halfResBitmap(w, h, 0f) { canvas ->
        canvas.drawRect(0f, 0f, w, h, Paint().apply { shader = fillShader(light, p, w, h) })
        if (light.rim > 0f) {
            // On a level card the rim lights only the lit part: clipped where the core is fully dark, so the unlit
            // side keeps just its hairline.
            light.level?.let { level ->
                val dark = (levelEdge(level) - LEVEL_SOFTNESS) * h
                if (light.fromAbove) canvas.clipRect(0f, 0f, w, h - dark) else canvas.clipRect(0f, dark, w, h)
            }
            val g = style.glowSize.toPx()
            val k = light.rim
            // Rim light: CSS-style inset shadows (one leaning toward the lit edge, one all the way round).
            if (light.angle != null) {
                // The hole shifts away from the light, so the lit side shows the most.
                val (vx, vy) = lightVector(light.angle)
                insetShadow(area, r, -vx * g * 0.6f, -vy * g * 0.6f, blur = g * 1.2f, spread = -g * 0.5f, p.light.copy(alpha = k.coerceAtMost(1f))).let { (path, paint) -> canvas.drawPath(path, paint) }
            }
            insetShadow(area, r, 0f, 0f, blur = g, spread = g * 0.1f, p.light.copy(alpha = (0.6f * k).coerceAtMost(1f))).let { (path, paint) -> canvas.drawPath(path, paint) }
            // Bevel: a thick band of light all the way round, like the edge of a glossy slab.
            if (style.bevel > 0f) {
                val b = minOf(w, h) * 0.075f * style.bevel
                insetShadow(area, r, 0f, 0f, blur = b * 1.6f, spread = b * 0.45f, p.light.copy(alpha = (0.85f * k).coerceAtMost(1f))).let { (path, paint) -> canvas.drawPath(path, paint) }
            }
        }
    }

    // Dark core and the second-tone band around it, pre-blurred.
    val cw = w * light.core.width
    val ch = h * light.core.height
    val core = RectF(w * light.core.x - cw / 2, h * light.core.y - ch / 2, 0f, 0f).apply { right = left + cw; bottom = top + ch }
    val coreRadius = (r - 8.dp.toPx()).coerceAtLeast(0f)
    val band = if (light.tone) style.toneWidth.toPx() else 0f
    val coreBlur = style.coreBlur.toPx()
    // Just enough room round the card for the core to drift and swell into view.
    val margin = maxOf(w, h) * 0.08f
    val coreLayer = halfResBitmap(w, h, margin) { canvas ->
        light.level?.let { level ->
            // A level card: the unlit part in the core colour, fading smoothly into the light around the level
            // (a smoothstep over 2 x LEVEL_SOFTNESS of the card), like light rising in a glass. Only the core's alpha
            // changes, so nothing is ever darker than the unlit part and there's no line.
            val edge = levelEdge(level) * h
            val soft = LEVEL_SOFTNESS * h
            val (y0, y1) = if (light.fromAbove) h + margin to -margin else -margin to h + margin
            val span = abs(y1 - y0)
            fun at(fromUnlit: Float) = ((fromUnlit + margin) / span).coerceIn(0f, 1f)
            val steps = 10
            val colors = IntArray(steps + 3)
            val stops = FloatArray(steps + 3)
            colors[0] = p.core.toArgb(); stops[0] = 0f
            for (i in 0..steps) {
                val t = i / steps.toFloat()
                val fade = t * t * (3f - 2f * t)
                colors[i + 1] = p.core.copy(alpha = 1f - fade).toArgb()
                stops[i + 1] = at(edge - soft + 2f * soft * t)
            }
            colors[steps + 2] = Color.Transparent.toArgb(); stops[steps + 2] = 1f
            val paint = Paint().apply { shader = android.graphics.LinearGradient(0f, y0, 0f, y1, colors, stops, Shader.TileMode.CLAMP) }
            canvas.drawRect(-margin, -margin, w + margin, h + margin, paint)
            return@halfResBitmap
        }
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
 * The card's base fill: rim colour at the lit edge melting into the mid colour, along the light's angle. The melt
 * is eased (fast near the edge, then slowly settling, with no corner where it ends), so the light reads as a soft
 * glow but has mostly faded by the title row.
 */
private fun fillShader(light: GlowLight, p: GlowPalette, w: Float, h: Float): Shader {
    val angle = light.angle ?: return android.graphics.LinearGradient(0f, 0f, 0f, h, p.mid.toArgb(), p.mid.toArgb(), Shader.TileMode.CLAMP)
    val start = lerp(p.mid, p.light, light.fill.coerceIn(0f, 1f))
    val steps = 12
    // Ease-out: 1 - (1 - x)^3 runs from light to mid and arrives with zero slope, so there's no seam.
    val fade = List(steps + 1) { i -> i / steps.toFloat() }
    val colors = fade.map { x -> lerp(start, p.mid, 1f - (1f - x).let { it * it * it }).toArgb() } + p.mid.toArgb()
    val stops = fade.map { it * light.reach } + 1f
    // Like a CSS linear-gradient: the line runs through the centre, just long enough to reach both far corners.
    val (vx, vy) = lightVector(angle)
    val half = (abs(w * vx) + abs(h * vy)) / 2f
    return android.graphics.LinearGradient(
        w / 2 + vx * half, h / 2 + vy * half, w / 2 - vx * half, h / 2 - vy * half,
        colors.toIntArray(), stops.toFloatArray(), Shader.TileMode.CLAMP,
    )
}

/**
 * The equivalent of CSS `box-shadow: inset [dx] [dy] [blur] [spread] [color]`: everything outside the
 * card's shape (shifted by [dx], [dy], shrunk by [spread]), blurred and clipped to the card.
 */
private fun insetShadow(size: Size, radius: Float, dx: Float, dy: Float, blur: Float, spread: Float, color: Color): Pair<Path, Paint> {
    val pad = blur * 2 + abs(dx) + abs(dy) + abs(spread) + 4f
    val holeRadius = (radius - spread).coerceAtLeast(0f)
    val path = Path().apply {
        fillType = Path.FillType.EVEN_ODD
        addRect(-pad, -pad, size.width + pad, size.height + pad, Path.Direction.CW)
        addRoundRect(spread + dx, spread + dy, size.width - spread + dx, size.height - spread + dy, holeRadius, holeRadius, Path.Direction.CW)
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

/** How the dark core sits: offset (fractions of the card), scale, and [fade] on top of the style's core opacity. */
private class Drift(val dx: Float, val dy: Float, val scale: Float, val fade: Float = 1f, val squash: Float = 0f)

/**
 * The core's motion over time. Under the heartbeat is a small never-repeating wander: each axis is two
 * sine waves with unrelated periods (10–24 s), and every card gets its own periods and phases.
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
            GlowMotion.Still -> Drift(0f, 0f, 1f)
            is GlowMotion.Heartbeat -> {
                // The dark core is the heart: it squeezes on each beat (lub, then a smaller dub) and relaxes,
                // always as dark, so nothing flashes. Each beat also pushes it a little off centre, a new way each
                // time, and squashes it, so the colour around it surges past a different side, like blood.
                val base = wander(t, amount = 0.4f)
                val beats = t * motion.bpm / 60f
                val c = contraction(beats)
                val angle = beatAngle(floor(beats).toInt())
                Drift(
                    dx = base.dx + 0.022f * c * cos(angle),
                    dy = base.dy + 0.018f * c * sin(angle),
                    scale = base.scale * (1f - 0.075f * c),
                    squash = 0.025f * c * cos(2f * angle),
                )
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
 * How hard the heart is squeezing over one beat (phase in beats), 0..1: a quick lub, a smaller dub just after,
 * each tightening fast and letting go more slowly, then rest until the next beat.
 */
private fun contraction(phase: Float): Float {
    val f = phase - floor(phase)
    // Fast in, slow out: a skewed bump that rises over [rise] and decays over [fall].
    fun squeeze(at: Float, rise: Float, fall: Float): Float {
        val d = f - at
        val w = if (d < 0f) rise else fall
        return exp(-(d / w).let { it * it })
    }
    return (squeeze(0.08f, 0.035f, 0.09f) + 0.55f * squeeze(0.30f, 0.03f, 0.08f)).coerceAtMost(1f)
}

/** Which way beat [n] pushes the core: a fixed scramble of the beat number, so each beat goes its own way. */
private fun beatAngle(n: Int): Float {
    var h = n * 0x9E3779B1.toInt()
    h = h xor (h ushr 15)
    h *= 0x85EBCA6B.toInt()
    h = h xor (h ushr 13)
    return (h ushr 8) / (1 shl 24).toFloat() * 2f * PI.toFloat()
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
