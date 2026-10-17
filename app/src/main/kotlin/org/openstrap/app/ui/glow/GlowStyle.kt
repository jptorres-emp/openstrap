package org.openstrap.app.ui.glow

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils

/** Shape of the dark core: a rounded rectangle that follows the card, or a soft oval. */
enum class CoreShape { Rounded, Radial }

/** Which edge the rim light is strongest on. */
enum class LightFrom { Top, Bottom, All }

/**
 * How a card's dark core moves: its personality. Pick the one that matches what the card measures, so
 * motion carries meaning (a heart card beats, a breathing card breathes).
 */
sealed interface GlowMotion {
    /** A slow, calm wander. The default for scores and anything without a rhythm of its own. */
    data object Drift : GlowMotion

    /** A lub-dub pulse at [bpm]: the core kicks and tightens on each beat, then relaxes. */
    data class Heartbeat(val bpm: Int = 62) : GlowMotion

    /** A slow inhale (swell, soften, rise) and longer exhale, [perMinute] breaths a minute. */
    data class Breath(val perMinute: Float = 6f) : GlowMotion

    /**
     * Lively: a quick wander with a bounce at walking [cadence] (steps a minute). [energy] 0..1 scales
     * it, e.g. progress toward the step goal, so a lazy day moves less.
     */
    data class Stride(val cadence: Int = 110, val energy: Float = 1f) : GlowMotion

    /** No motion. */
    data object Still : GlowMotion
}

/**
 * The "glow" design language: a card lit from its rim, with a dark core floating inside it and an
 * optional second-tone band between the two. Everything is derived from one [color].
 *
 * Core position and size are fractions of the card (0..1, may go past the edges). A [toneWidth] of 0
 * turns the second tone off; a [glowStrength] of 0 turns the rim light off.
 */
@Immutable
data class GlowStyle(
    val color: Color,
    val radius: Dp = 48.dp,
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
    val motion: GlowMotion = GlowMotion.Drift,
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

        /** Black top melting into orange from below ("Breath Volume"). */
        val Ember = GlowStyle(
            color = Color(0xFFF2582A),
            coreShape = CoreShape.Radial,
            coreX = 0.5f, coreY = 0.05f, coreWidth = 1.4f, coreHeight = 1.2f,
            coreBlur = 32.dp, coreLightness = 0.02f,
            toneShift = -15f, toneWidth = 26.dp,
            lightFrom = LightFrom.Bottom, glowSize = 34.dp, glowStrength = 0.9f,
        )

        val presets = listOf("Bloom" to Bloom, "Midnight" to Midnight, "Ember" to Ember)
    }
}

/** The handful of colours a [GlowStyle] is drawn with, all derived from its main colour. */
@Immutable
internal class GlowPalette(style: GlowStyle) {
    val light: Color
    val mid: Color
    val tone: Color
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
