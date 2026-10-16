package org.openstrap.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import org.openstrap.app.R
import androidx.compose.ui.unit.sp

/** Metric accent colors, shared by cards and charts. Tuned to read well on near-black. */
object MetricColors {
    val heart = Color(0xFFFF5A5F)
    val steps = Color(0xFFC3F53C)
    val sleepDeep = Color(0xFF4C6FFF)
    val sleepLight = Color(0xFF8FA8FF)
    val sleepRem = Color(0xFFB37CFF)
    val sleepAwake = Color(0xFFFFB547)
    val spo2 = Color(0xFF38BDF8)
    val stress = Color(0xFFF062C0)
    val hrv = Color(0xFF2DD4BF)
    val temperature = Color(0xFFFF8A3D)
    val readiness = Color(0xFF7CF2C9)
    val charge = Color(0xFFFFD166)
    val good = Color(0xFF3DDC97)
    val warn = Color(0xFFFFB547)
    val bad = Color(0xFFFF5A5F)

    /** Charge from empty to full: red, orange, gold, then green. */
    val chargeScale = listOf(0f to Color(0xFFFF5A5F), 0.3f to Color(0xFFFF9F43), 0.55f to Color(0xFFFFD166), 0.8f to Color(0xFF3DDC97))

    /** The colour for a Charge level (0–100) on [chargeScale]; plain gold when unknown. */
    fun chargeAt(level: Float?): Color {
        val f = (level ?: return charge) / 100f
        val i = chargeScale.indexOfLast { it.first <= f }.coerceAtLeast(0)
        val (a, ca) = chargeScale[i]
        val (b, cb) = chargeScale.getOrNull(i + 1) ?: return ca
        return androidx.compose.ui.graphics.lerp(ca, cb, ((f - a) / (b - a)).coerceIn(0f, 1f))
    }

    /** Battery colour: red at 15% or below, amber up to 40%, green above. */
    fun battery(level: Int?): Color = when {
        level == null -> good
        level <= 15 -> bad
        level <= 40 -> warn
        else -> good
    }
}

private val Dark = darkColorScheme(
    primary = Color(0xFF8FA8FF),
    onPrimary = Color(0xFF0A0F24),
    secondary = Color(0xFF2DD4BF),
    // OLED black page with neutral grey cards, so the metric accents carry all the colour.
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainer = Color(0xFF141414),
    surfaceContainerHigh = Color(0xFF1C1C1E),
    surfaceContainerHighest = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFF8E8E93),
    outline = Color(0xFF2C2C2E),
    outlineVariant = Color(0xFF232323),
    error = Color(0xFFFF6B6B),
)

private val Light = lightColorScheme(
    primary = Color(0xFF3550D9),
    secondary = Color(0xFF0F9F8F),
    background = Color(0xFFF4F5F7),
    surface = Color(0xFFF4F5F7),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEDEEF2),
    onSurfaceVariant = Color(0xFF6B7280),
    outline = Color(0xFFE2E4E9),
    outlineVariant = Color(0xFFEDEEF2),
)

private fun variable(res: Int, weight: Int) = Font(
    res,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Funnel Display for numbers and headings, Funnel Sans for text. Both SIL OFL 1.1 (see licenses/). */
val FunnelDisplay = FontFamily(
    variable(R.font.funnel_display, 400),
    variable(R.font.funnel_display, 500),
    variable(R.font.funnel_display, 600),
    variable(R.font.funnel_display, 700),
)
val FunnelSans = FontFamily(
    variable(R.font.funnel_sans, 400),
    variable(R.font.funnel_sans, 500),
    variable(R.font.funnel_sans, 600),
    variable(R.font.funnel_sans, 700),
)

/** MuseoModerno for the OpenStrap wordmark only. SIL OFL 1.1 (see licenses/). */
val MuseoModerno = FontFamily(variable(R.font.museo_moderno, 600))

private val Base = Typography()

/** Big numerals use tabular figures so values don't jitter as they update (e.g. live HR). */
private fun display(style: TextStyle, weight: FontWeight, size: Int, tracking: Float) =
    style.copy(fontFamily = FunnelDisplay, fontWeight = weight, fontSize = size.sp, letterSpacing = tracking.sp, fontFeatureSettings = "tnum")

private fun text(style: TextStyle) = style.copy(fontFamily = FunnelSans)

private val AppTypography = Typography(
    displayLarge = display(Base.displayLarge, FontWeight.SemiBold, 64, -1.5f),
    displayMedium = display(Base.displayMedium, FontWeight.SemiBold, 48, -1f),
    displaySmall = display(Base.displaySmall, FontWeight.SemiBold, 34, -0.5f),
    headlineLarge = display(Base.headlineLarge, FontWeight.SemiBold, 30, -0.4f),
    headlineMedium = display(Base.headlineMedium, FontWeight.SemiBold, 26, -0.3f),
    headlineSmall = display(Base.headlineSmall, FontWeight.SemiBold, 22, -0.2f),
    titleLarge = display(Base.titleLarge, FontWeight.SemiBold, 20, 0f),
    titleMedium = text(Base.titleMedium).copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
    titleSmall = text(Base.titleSmall).copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = text(Base.bodyLarge),
    bodyMedium = text(Base.bodyMedium),
    bodySmall = text(Base.bodySmall),
    labelLarge = text(Base.labelLarge).copy(fontWeight = FontWeight.SemiBold),
    // Uppercase tracked labels ("TIME ASLEEP") are styled with labelMedium.
    labelMedium = text(Base.labelMedium).copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp, fontSize = 11.sp),
    labelSmall = text(Base.labelSmall),
)

@Composable
fun OpenStrapTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = AppTypography,
        content = content,
    )
}
