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
    val steps = Color(0xFF3DDC97)
    val sleepDeep = Color(0xFF4C6FFF)
    val sleepLight = Color(0xFF8FA8FF)
    val sleepRem = Color(0xFFB37CFF)
    val sleepAwake = Color(0xFFFFB547)
    val spo2 = Color(0xFF38BDF8)
    val stress = Color(0xFFFF8A3D)
    val hrv = Color(0xFF2DD4BF)
    val good = Color(0xFF3DDC97)
    val warn = Color(0xFFFFB547)
    val bad = Color(0xFFFF5A5F)

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
    background = Color(0xFF07080B),
    onBackground = Color(0xFFF2F3F5),
    surface = Color(0xFF07080B),
    onSurface = Color(0xFFF2F3F5),
    surfaceContainerLowest = Color(0xFF07080B),
    surfaceContainerLow = Color(0xFF0F1116),
    surfaceContainer = Color(0xFF14171D),
    surfaceContainerHigh = Color(0xFF1B1F27),
    surfaceContainerHighest = Color(0xFF232833),
    onSurfaceVariant = Color(0xFF9097A3),
    outline = Color(0xFF2A2F3A),
    outlineVariant = Color(0xFF1F232B),
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
