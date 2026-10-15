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
import androidx.compose.ui.text.font.FontWeight
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

private val Base = Typography()

/** Big numerals use tabular figures so values don't jitter as they update (e.g. live HR). */
private val Numerals = TextStyle(fontFeatureSettings = "tnum", fontFamily = FontFamily.SansSerif)

private val AppTypography = Base.copy(
    displayLarge = Base.displayLarge.merge(Numerals).copy(fontWeight = FontWeight.SemiBold, fontSize = 64.sp, letterSpacing = (-1.5).sp),
    displayMedium = Base.displayMedium.merge(Numerals).copy(fontWeight = FontWeight.SemiBold, fontSize = 44.sp, letterSpacing = (-1).sp),
    displaySmall = Base.displaySmall.merge(Numerals).copy(fontWeight = FontWeight.SemiBold, fontSize = 32.sp, letterSpacing = (-0.5).sp),
    headlineSmall = Base.headlineSmall.merge(Numerals).copy(fontWeight = FontWeight.SemiBold),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    // Uppercase tracked labels ("TIME ASLEEP") are styled with labelMedium.
    labelMedium = Base.labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp, fontSize = 11.sp),
)

@Composable
fun OpenStrapTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = AppTypography,
        content = content,
    )
}
