package org.openstrap.app.ui.glow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.exp
import org.openstrap.app.ui.components.LocalCardNumbers
import org.openstrap.app.ui.components.cardOpening
import org.openstrap.app.ui.components.pressClickable

/*
 * The middle tier of the glow language: a near-black card with a soft glow coming in from one edge in the metric's
 * colour. Which edge, and how strong, carries the meaning (see [edgeLight]). No bright line on the border, only a
 * plain hairline, the same on every card: off your normal shows in the stronger glow and the coloured value and
 * status. Static: the edge only changes when the data does.
 */

/** Which edge of an [EdgeCard] the light comes in from. */
enum class Edge { Top, Bottom, Left, Right }

/** A value against your normal. */
enum class EdgeStatus { Usual, Above, Below }

/** What an edge card is about; with its [EdgeStatus] it decides where the light sits. */
enum class EdgeKind {
    /** A value read against your normal: resting heart rate, HRV, skin temperature. */
    Value,

    /** Progress toward a goal or your usual by now: steps, Load so far. Gets a progress bar. */
    Progress,

    /** Live, now: heart rate today. */
    Live,

    /** About earlier: Recovery after the morning, Habit Age. */
    Earlier,

    /** Sleep: last night, regularity, breathing disturbances. Lit from the top, like a night sky. */
    Sleep,

    /** A level, like Charge: the light fills from the left across to the value, in place of a progress bar. */
    Level,
}

/** Where an edge card's light sits and how it's drawn. */
@Immutable
data class EdgeLight(
    val edge: Edge,
    /** Off your normal: a deeper, stronger glow. */
    val strong: Boolean,
    /** A 4 dp progress bar inside the card. */
    val progressBar: Boolean,
)

/**
 * Where the light sits for a card of [kind] whose value is [status] (spec 2.4). A plain value lights the bottom when
 * usual or below your normal and the top when above. The other kinds keep their own edge (progress bottom, live
 * right, earlier left, sleep top). Off your normal, any of them glows stronger.
 */
fun edgeLight(status: EdgeStatus, kind: EdgeKind = EdgeKind.Value): EdgeLight {
    val off = status != EdgeStatus.Usual
    val edge = when (kind) {
        EdgeKind.Value -> if (status == EdgeStatus.Above) Edge.Top else Edge.Bottom
        EdgeKind.Progress -> Edge.Bottom
        EdgeKind.Live -> Edge.Right
        EdgeKind.Earlier -> Edge.Left
        EdgeKind.Sleep -> Edge.Top
        EdgeKind.Level -> Edge.Left
    }
    return EdgeLight(edge, strong = off, progressBar = kind == EdgeKind.Progress)
}

/** The neutral edge light, hsl 220 10% 55%: a cool soft white for cards that speak for no one metric. */
val EdgeNeutral = Color.hsl(220f, 0.10f, 0.55f)

/**
 * A screen's say over the next edge card's look, so two cards in a row never look the same (spec 2.4): the [edge] the
 * light comes in from and the [color] it's lit in, each null to keep the card's own.
 */
@Immutable
data class EdgeOverride(val edge: Edge? = null, val color: Color? = null)

val LocalEdgeOverride = staticCompositionLocalOf { EdgeOverride() }

private val EdgeShape = GlowLayout.CardShape
private val Surface = Color(0xFF0A0A0B)
private val Hairline = Color.White.copy(alpha = GlowLayout.HairlineAlpha)

/**
 * A card in the edge light tier, lit in [color] from the edge [edgeLight] picks for [status] and [kind], unless
 * [LocalEdgeOverride] says otherwise. For a [EdgeKind.Progress] card, [progress] (0..1) fills the bar under [content].
 * For a [EdgeKind.Level] card it's how far across the light fills.
 * The screen's [LocalGlowMood] softens the light; the hairline always stays.
 */
@Composable
fun EdgeCard(
    color: Color,
    modifier: Modifier = Modifier,
    status: EdgeStatus = EdgeStatus.Usual,
    kind: EdgeKind = EdgeKind.Value,
    progress: Float? = null,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = GlowLayout.CardPadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    val override = LocalEdgeOverride.current
    // A level always fills from the left, so only its colour can be overridden.
    val light = edgeLight(status, kind).let { l -> override.edge?.takeIf { kind != EdgeKind.Level }?.let { l.copy(edge = it) } ?: l }
    val level = progress?.takeIf { kind == EdgeKind.Level }?.coerceIn(MinLevel, 1f)
    val tint = override.color ?: color
    val mood = LocalGlowMood.current
    // Tapped, the card grows into what it opens (spec 3.5).
    val opening = cardOpening(onClick, EdgeShape)
    Column(
        modifier
            .then(opening?.modifier ?: Modifier)
            .let { if (onClick != null) it.pressClickable(opening?.onClick ?: onClick, EdgeShape) else it }
            .drawWithCache {
                val glow = if (light.strong) StrongGlow else SoftGlow
                val depth = glow.depth.toPx()
                // From the lit edge inward, the colour falling off like the blurred edge of a CSS inset shadow.
                val (from, to) = when (light.edge) {
                    Edge.Top -> Offset(0f, 0f) to Offset(0f, depth)
                    Edge.Bottom -> Offset(0f, size.height) to Offset(0f, size.height - depth)
                    Edge.Left -> Offset(0f, 0f) to Offset(depth, 0f)
                    Edge.Right -> Offset(size.width, 0f) to Offset(size.width - depth, 0f)
                }
                val stops = glow.stops(mood.edge).map { (at, alpha) -> at to tint.copy(alpha = alpha) }.toTypedArray()
                val brush = if (level == null) Brush.linearGradient(*stops, start = from, end = to) else {
                    // The light fills across to the level, 46% of the ink at the left and 30% at the value, then fades
                    // out over LevelFade past it on a smoothstep, so it never stops in a line (spec 2.1, Levels).
                    val w = size.width
                    val end = w * level
                    val fade = LevelFade.toPx()
                    val strength = mood.edge * (if (light.strong) 1.2f else 1f)
                    val atValue = 0.30f * strength
                    val past = List(LEVEL_FADE_STEPS) { i ->
                        val t = (i + 1) / LEVEL_FADE_STEPS.toFloat()
                        ((end + fade * t) / w).coerceIn(0f, 1f) to tint.copy(alpha = (atValue * (1f - t * t * (3f - 2f * t))).coerceIn(0f, 1f))
                    }
                    Brush.horizontalGradient(
                        0f to tint.copy(alpha = (0.46f * strength).coerceIn(0f, 1f)),
                        (end / w).coerceIn(0f, 1f) to tint.copy(alpha = atValue.coerceIn(0f, 1f)),
                        *past.toTypedArray(),
                    )
                }
                val radius = CornerRadius(GlowLayout.CardCorner.toPx())
                val stroke = 1.dp.toPx()
                onDrawBehind {
                    drawRoundRect(Surface, cornerRadius = radius)
                    drawRoundRect(brush, cornerRadius = radius)
                    drawRoundRect(
                        Hairline,
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(size.width - stroke, size.height - stroke),
                        cornerRadius = CornerRadius(radius.x - stroke / 2),
                        style = Stroke(stroke),
                    )
                }
            }
            .padding(contentPadding),
    ) {
        CompositionLocalProvider(LocalCardNumbers provides opening?.numbers) { content() }
        if (light.progressBar && progress != null) {
            Spacer(Modifier.weight(1f, fill = false).padding(top = 12.dp))
            ProgressBar(progress, tint)
        }
    }
}

/** A level card's lowest light, so an empty one still shows where it fills from. */
private const val MinLevel = 0.04f

/** How far past its level a level card's light fades out. */
internal val LevelFade = 40.dp
private const val LEVEL_FADE_STEPS = 6

/** The 4 dp bar inside a progress edge card. */
@Composable
private fun ProgressBar(progress: Float, color: Color) {
    val shape = RoundedCornerShape(2.dp)
    Box(Modifier.fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = 0.08f), shape)) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(4.dp).background(color, shape))
    }
}

/**
 * An edge glow, after the CSS `box-shadow: inset` it was designed with: [alpha] of the colour, its hard edge [shift]
 * in from the card's edge, blurred with a standard deviation of [sigma].
 */
internal class EdgeGlow(val alpha: Float, val shift: Dp, val sigma: Dp) {
    /** How far in the glow reaches before it's gone. */
    val depth: Dp get() = shift + sigma * 2.5f

    /** Colour stops along [depth] (position 0..1 to alpha), scaled by the mood's [strength]. */
    fun stops(strength: Float): List<Pair<Float, Float>> = List(STEPS + 1) { i ->
        val at = i / STEPS.toFloat()
        val x = depth.value * at
        // The Gaussian's cumulative curve, closely enough: a logistic with the matching slope. Scaled so the lit edge
        // is [alpha] of the colour exactly.
        at to (alpha * strength * cover(x) / cover(0f) * (1f - at)).coerceIn(0f, 1f)
    }

    private fun cover(x: Float) = 1f / (1f + exp(-1.702f * (shift.value - x) / sigma.value))

    private companion object {
        const val STEPS = 8
    }
}

/** L1, a usual value and every strip (spec 2.1): the ink at 70% at the edge, reaching 48 dp in. */
internal val SoftGlow = EdgeGlow(alpha = 0.70f, shift = 10.5.dp, sigma = 15.dp)

/** L2, off your normal: the ink at 90% at the edge, reaching 72 dp in. */
internal val StrongGlow = EdgeGlow(alpha = 0.90f, shift = 19.5.dp, sigma = 21.dp)
