package org.openstrap.app.ui.glow

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Shape of the dark core: a rounded rectangle that follows the card, or a soft oval. */
enum class CoreShape { Rounded, Radial }

/** Which edge the rim light is strongest on. [GlowStyle.lightAngle], when set, takes over from it. */
enum class LightFrom { Top, Bottom, All }

/**
 * Where the light sits for [GlowStyle.lightAngle], in degrees counterclockwise from the right edge: 90 above, 270
 * below. The dark core sits on the opposite side.
 */
object LightAngle {
    const val Right = 0f
    const val UpperRight = 45f
    const val Above = 90f
    const val UpperLeft = 135f
    const val Left = 180f
    const val LowerLeft = 225f
    const val Below = 270f
    const val LowerRight = 315f
}

/**
 * How a card's dark core moves. Nothing moves when idle: only live data does, so a card is [Still] unless it shows
 * the live heart rate.
 */
sealed interface GlowMotion {
    /** A lub-dub pulse at [bpm]: the core kicks and tightens on each beat, then relaxes. Live heart rate only. */
    data class Heartbeat(val bpm: Int = 62) : GlowMotion

    /** No motion. */
    data object Still : GlowMotion
}

/**
 * A screen-level light on top of every card's own style (see [LocalGlowMood]). [glow] scales the brightness of a
 * glow card's whole lit area (rim, fill, body and band),
 * [edge] scales edge light, [tone] keeps the second-tone band and [belowOnly] turns every directional light to come
 * from below.
 */
enum class GlowMood(val glow: Float, val edge: Float, val tone: Boolean, val belowOnly: Boolean) {
    /** The default: every card as its style says. */
    Day(glow = 1f, edge = 1f, tone = true, belowOnly = false),

    /** The evening moment: softer light, no tone band. */
    Dim(glow = 0.6f, edge = 0.6f, tone = false, belowOnly = false),

    /**
     * The night moment, and the Sleep tab always: almost unlit, a faint glow along the bottom edge only. Edge light
     * a step calmer than Dim, so the night sky on sleep cards still reads.
     */
    Night(glow = 0.4f, edge = 0.45f, tone = true, belowOnly = true),
}

/** The mood for the cards on this screen; a screen provides it, every [GlowCard] and [EdgeCard] under it follows. */
val LocalGlowMood = staticCompositionLocalOf { GlowMood.Day }

/**
 * The "glow" design language: a card lit from its rim, with a dark core floating inside it and an
 * optional second-tone band between the two. Everything is derived from one [color].
 *
 * Core position and size are fractions of the card (0..1, may go past the edges). A [toneWidth] of 0
 * turns the second tone off; a [glowStrength] of 0 turns the rim light off.
 *
 * The light can carry meaning: [lightAngle] says where it comes from (and places the core opposite), [lightLevel]
 * how high it reaches (Charge), and [second] marks the second glow card on a screen, so the hero clearly leads.
 */
@Immutable
data class GlowStyle(
    val color: Color,
    val radius: Dp = GlowLayout.CardCorner,
    val coreShape: CoreShape = CoreShape.Rounded,
    val coreX: Float = 0.5f,
    val coreY: Float = 0.64f,
    val coreWidth: Float = 0.74f,
    val coreHeight: Float = 0.6f,
    val coreBlur: Dp = 22.dp,
    val coreOpacity: Float = 1f,
    /** HSL lightness of the core, 0..1. 0.09 is the reference's deep plum; ~0.02 reads as black. */
    val coreLightness: Float = 0.09f,
    /** Hue rotation of the second tone, in degrees (-40 turns pink into violet). */
    val toneShift: Float = -40f,
    val toneWidth: Dp = 24.dp,
    val lightFrom: LightFrom = LightFrom.Top,
    val glowSize: Dp = 30.dp,
    val glowStrength: Float = 0.8f,
    /** A thick band of rim light all the way round, like a glossy bevel, 0..1 (0 = none). */
    val bevel: Float = 0f,
    /** How far the card's slab edge shows below it, for a 3D look (0 = flat). */
    val depth: Dp = 0.dp,
    /** Film grain opacity, 0..1. */
    val grain: Float = 0.06f,
    val motion: GlowMotion = GlowMotion.Still,
    /**
     * Where the light comes from, in degrees (see [LightAngle]). Set, it replaces [lightFrom] and the core moves to
     * the opposite side, keeping [coreWidth] and [coreHeight]. Null keeps [lightFrom] and the core where it's set.
     */
    val lightAngle: Float? = null,
    /**
     * How high the light reaches, 0..1, for levels like Charge: the lit part is about this share of the card, the
     * rest dark, with a soft step between (see [levelEdge]). Never under [MIN_LIGHT_LEVEL], so an empty card still
     * glows faintly. Light from below unless [lightAngle] puts it above.
     */
    val lightLevel: Float? = null,
    /** The second glow card on a screen: a weaker rim, a dimmer and shorter fill, a darker body. */
    val second: Boolean = false,
    /**
     * Only the card's outline in [color], on black, with no light at all: the same card on hold, like a paused workout
     * (pattern D1). Nothing is rendered for it but the edge.
     */
    val outline: Boolean = false,
    /** Set by [GlowCard] from [LocalGlowMood]; leave it to the screen. */
    val mood: GlowMood = GlowMood.Day,
) {
    /** The same look in another colour. */
    fun tinted(color: Color) = copy(color = color)

    companion object {
        /** Pink rim, violet band, plum core that follows the card ("Last test"). */
        val Bloom = GlowStyle(color = Color(0xFFD65AA0), bevel = 1f)

        /** Near-black core in the upper left, navy light from below ("Training"). */
        val Midnight = GlowStyle(
            color = Color(0xFF2A44B0),
            coreShape = CoreShape.Radial,
            coreX = 0.30f, coreY = 0.20f, coreWidth = 0.80f, coreHeight = 1.5f,
            coreBlur = 32.dp, coreLightness = 0.03f,
            toneWidth = 0.dp,
            lightFrom = LightFrom.Bottom, glowSize = 26.dp, glowStrength = 0.7f,
        )

        /** Black top melting into orange from below ("Breath Volume"): Charge's glow, in its one ink. */
        val Ember = GlowStyle(
            color = org.openstrap.app.ui.theme.MetricColors.charge,
            coreShape = CoreShape.Radial,
            coreX = 0.5f, coreY = 0.05f, coreWidth = 1.4f, coreHeight = 1.2f,
            coreBlur = 32.dp, coreLightness = 0.02f,
            toneShift = -15f, toneWidth = 26.dp,
            lightFrom = LightFrom.Bottom, glowSize = 34.dp, glowStrength = 0.9f,
        )

        val presets = listOf("Bloom" to Bloom, "Midnight" to Midnight, "Ember" to Ember)
    }
}

/** A glow style tuned for charts: the dark core sits low and wide, behind the plot. */
fun GlowStyle.Companion.chart(color: Color) = Bloom.copy(
    color = color,
    coreY = 0.64f, coreWidth = 0.88f, coreHeight = 0.66f,
    coreBlur = 24.dp, toneWidth = 16.dp,
    // Sleep's quiet recipe: a near-same-hue band, a softer rim and a lighter bevel.
    toneShift = -10f, glowStrength = 0.6f, bevel = 0.6f,
)

/** The lowest [GlowStyle.lightLevel] drawn: an empty card still glows faintly. */
const val MIN_LIGHT_LEVEL = 0.12f

/** The light level to draw for [level]: within [MIN_LIGHT_LEVEL]..1. Null (no level) stays null. */
fun glowLevel(level: Float?): Float? = when {
    level == null -> null
    level.isNaN() -> MIN_LIGHT_LEVEL
    else -> level.coerceIn(MIN_LIGHT_LEVEL, 1f)
}

/** A unit vector from the card's centre toward the light at [angle] degrees, in screen coordinates (y down). */
internal fun lightVector(angle: Float): Pair<Float, Float> {
    val r = angle / 180f * PI.toFloat()
    // Snapped, so 90 and 270 give exactly the vertical light the old Top and Bottom did.
    fun clean(v: Float) = if (abs(v) < 1e-6f) 0f else v
    return clean(cos(r)) to clean(-sin(r))
}

/** Where the dark core sits and how big it is, as fractions of the card. */
internal data class CoreBox(val x: Float, val y: Float, val width: Float, val height: Float)

/** The core for light from [angle]: pushed toward the opposite side by a little over a third of the card. */
internal fun angleCore(angle: Float, width: Float, height: Float): CoreBox {
    val (vx, vy) = lightVector(angle)
    return CoreBox(0.5f - CORE_PUSH * vx, 0.5f - CORE_PUSH * vy, width, height)
}

/**
 * Where the lit part of a card with light [level] (already through [glowLevel]) ends, as a fraction of the card
 * measured from the unlit side: about the level's height, so 0.3 lights a third of the card. Near full it moves
 * past the edge by half the soft fade, so a full card is lit all the way.
 */
fun levelEdge(level: Float): Float = 1f - level - LEVEL_SOFTNESS * level * level * level * level

/** Half the height of the soft fade between dark and light on a level card, as a fraction of the card. */
const val LEVEL_SOFTNESS = 0.11f

/** The core box for a light [level]: the dark part of the card, from the unlit side to [levelEdge]. */
internal fun levelCore(level: Float, fromAbove: Boolean): CoreBox {
    val edge = levelEdge(level).coerceAtLeast(0f)
    return CoreBox(0.5f, if (fromAbove) 1f - edge / 2f else edge / 2f, 1f, edge)
}

private const val CORE_PUSH = 0.38f

/** How a [GlowStyle] is lit once its angle, level, [GlowStyle.second] and mood are applied. */
internal class GlowLight(
    /** Where the light comes from, or null for light all the way round. */
    val angle: Float?,
    /** Multiplier on the rim light. */
    val rim: Float,
    /** How much of the rim colour the fill starts with at the lit edge, 0..1. */
    val fill: Float,
    /** How far into the card the fill reaches before it's all body colour, 0..1. */
    val reach: Float,
    val core: CoreBox,
    val tone: Boolean,
    /** Set for a level card: the light level the dark part is drawn down to (or up to, [fromAbove]). */
    val level: Float?,
    val fromAbove: Boolean,
    /** How bright the whole lit area is (rim, fill, body and band), 0..1: the mood and [GlowStyle.second]. */
    val brightness: Float,
)

internal fun GlowStyle.light(): GlowLight {
    val own = lightAngle ?: if (lightLevel != null) LightAngle.Below else when (lightFrom) {
        LightFrom.Top -> LightAngle.Above
        LightFrom.Bottom -> LightAngle.Below
        LightFrom.All -> null
    }
    val angle = if (mood.belowOnly && own != null) LightAngle.Below else own
    // At night a card without a level of its own is lit only along the bottom edge.
    val level = glowLevel(lightLevel) ?: if (mood.belowOnly && angle != null) NIGHT_LEVEL else null
    val fromAbove = angle != null && lightVector(angle).second < 0f
    val core = when {
        level != null -> levelCore(level, fromAbove)
        // An angle of the card's own, or the mood moved the light: the core goes opposite it.
        angle != null && (lightAngle != null || angle != own) -> angleCore(angle, coreWidth, coreHeight)
        else -> CoreBox(coreX, coreY, coreWidth, coreHeight)
    }
    return GlowLight(
        angle = angle,
        rim = glowStrength * if (second) SECOND_RIM else 1f,
        fill = if (second) SECOND_FILL else 1f,
        reach = if (second) 0.24f else 0.4f,
        core = core,
        tone = mood.tone,
        level = level,
        fromAbove = fromAbove,
        brightness = mood.glow * if (second) SECOND_BRIGHTNESS else 1f,
    )
}

/** The second glow card against the hero: a weaker rim, a fill that starts dimmer, and a darker card overall. */
private const val SECOND_RIM = 0.5f
private const val SECOND_FILL = 0.5f
private const val SECOND_BRIGHTNESS = 0.7f

/** How far up the light reaches on a card at night. */
private const val NIGHT_LEVEL = 0.18f

/** The handful of colours a [GlowStyle] is drawn with, all derived from its main colour. */
@Immutable
internal class GlowPalette(style: GlowStyle) {
    var light: Color
        private set
    var mid: Color
        private set
    var tone: Color
        private set
    val core: Color

    init {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(style.color.toArgb(), hsl)
        val (h, s0, l) = hsl
        // Near-grey colours stay neutral (a dark grey glow); anything coloured gets enough saturation to glow.
        val s = if (s0 < 0.12f) s0 else s0.coerceIn(0.35f, 0.9f)
        // A rim not far above the body in brightness, so the light melts in rather than sitting on top.
        light = hsl(h, s * 0.9f, (l + 0.03f).coerceIn(0.45f, 0.6f))
        // Same lightness looks far brighter in yellow-green than in red or blue, so the body and band
        // are darkened until their actual brightness keeps white text readable, whatever the hue.
        // Only a small hue step from the rim, so the card reads as one colour in different light.
        mid = capped(h - 4f, s * 0.85f, 0.32f, MID_MAX_LUMINANCE)
        tone = capped(h + style.toneShift, s * 0.8f, 0.28f, TONE_MAX_LUMINANCE)
        core = hsl(h, 0.7f, style.coreLightness)
        // A dimmer mood or a second card darkens the whole lit area, like turning the light down.
        val k = style.light().brightness.coerceIn(0f, 1f)
        if (k < 1f) {
            light = lerp(Color.Black, light, k)
            mid = lerp(Color.Black, mid, k)
            tone = lerp(Color.Black, tone, k)
        }
    }

    private companion object {
        /** Relative luminance; white on it has a contrast of at least 1.05 / (0.09 + 0.05) = 7.5:1. */
        const val MID_MAX_LUMINANCE = 0.09f
        /** The band round the core stays a step darker than the body. */
        const val TONE_MAX_LUMINANCE = 0.07f
    }

    /** The colour at lightness [l], or darker until its actual brightness is at most [max]. */
    private fun capped(h: Float, s: Float, l: Float, max: Float) =
        generateSequence(l) { it - 0.01f }.takeWhile { it > 0.08f }
            .map { hsl(h, s, it) }
            .firstOrNull { it.luminance() <= max } ?: hsl(h, s, 0.08f)

    private fun hsl(h: Float, s: Float, l: Float) =
        Color.hsl(((h % 360f) + 360f) % 360f, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))
}
