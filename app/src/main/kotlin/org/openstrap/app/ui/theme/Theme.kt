package org.openstrap.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import org.openstrap.app.R
import androidx.compose.ui.unit.sp

/**
 * Each metric's one ink (spec 2.2): its values and status when off your normal, its row icon, its chart marks and its
 * edge light. A metric never takes a second colour, and a level shows as how far the light reaches, never as a hue.
 * Tuned to read well on near-black.
 */
object MetricColors {
    val heart = Color(0xFFFF5A5F)
    /** Heart's lighter step: the text of a small destructive pill (delete, discard) on its dark fill. */
    val heartLight = Color(0xFFFF7A7E)
    val steps = Color(0xFFC3F53C)
    /** Heart points and Active minutes share this lime, a step apart from Steps' own. */
    val activityLime = Color(0xFFC6F55A)
    val sleepDeep = Color(0xFF4C6FFF)
    /** Sleep's navy: the near-black "Midnight" glow's light from below (the Training card). */
    val sleepNavy = Color(0xFF2A44B0)
    val sleepLight = Color(0xFF8FA8FF)
    val sleepRem = Color(0xFFB37CFF)
    val sleepAwake = Color(0xFFFFB547)
    val spo2 = Color(0xFF38BDF8)
    val stress = Color(0xFFF062C0)
    val hrv = Color(0xFF2DD4BF)
    /**
     * Skin temperature at your normal: grey-white. Its colour comes from the reading ([temperatureFor]), cooler toward
     * ice cyan and warmer toward dusty rose, so it never shares Energy's orange (calmer pass, 14 Nov 2026).
     */
    val temperature = Color(0xFFD8D8D8)
    val temperatureCool = Color(0xFF8FD8F5)
    val temperatureWarm = Color(0xFFCA6789)

    /** Skin temperature's colour for a reading [deviation] °C from your normal: full cyan or rose at 1 °C off. */
    fun temperatureFor(deviation: Float?): Color {
        if (deviation == null) return temperature
        val k = (kotlin.math.abs(deviation) / TEMP_FULL_AT).coerceIn(0f, 1f)
        return androidx.compose.ui.graphics.lerp(temperature, if (deviation < 0f) temperatureCool else temperatureWarm, k)
    }

    private const val TEMP_FULL_AT = 1f
    /** Recovery: one teal on every surface. */
    val readiness = Color(0xFF7CF2C9)
    /** Recovery's pale step: the text a lit teal card takes on (the rest-or-recovery drop on a workout summary). */
    val readinessPale = Color(0xFFD9FBEF)
    /** Recovery's softer step: the strap picture's own light when it is connected. */
    val readinessSoft = Color(0xFF7DE0C0)
    /** Charge: Ember orange on every surface, whatever the level. */
    val charge = Color(0xFFF66C28)
    /** Charge's deeper step: the Drained bar beside Recharged's [charge] (board HaloRecovery). */
    val chargeDrained = Color(0xFFC55620)
    /**
     * Calories: Gold (hue 48), between Charge's orange and Steps' lime and lighter than the warning amber, so Calories
     * and Steps read as two metrics side by side on Activity.
     */
    val calories = Color(0xFFFFD23F)
    /** Calories' darker shade, for walking beside workouts in the hourly bars: a part of the same metric, not a second one. */
    val caloriesShade = Color(0xFF8C7314)
    /** Load: violet, apart from Sleep's blues and REM's purple. */
    val load = Color(0xFFA78BFA)
    /** Habit Age: orchid. */
    val habitAge = Color(0xFFD86BEA)
    /** The evening hero's light: a moment, not a metric. Violet, well clear of the night's Sleep blue. */
    val evening = Color(0xFFA274F2)
    /** Green and amber are for the strap and the sync only, never a health value. */
    val good = Color(0xFF3DDC97)
    val warn = Color(0xFFFFB547)

    /** Battery colour: amber up to 40%, green above. */
    fun battery(level: Int?): Color = when {
        level == null -> good
        level <= 40 -> warn
        else -> good
    }
}

/**
 * The workout families' colours (workout-rules.md, Colour): they belong to no metric, so they sit here and not in
 * [MetricColors]. Distance takes Steps' lime and Intervals takes Heart's red, as the family is the same data.
 */
object SportColors {
    val distance = MetricColors.steps
    val intervals = MetricColors.heart
    /** Indoor cardio: a bright blue. */
    val indoorCardio = Color(0xFF52ACFC)
    /** Strength: orange, a step brighter and redder than Charge's. */
    val strength = Color(0xFFFF8410)
    /** Mind and body: a soft purple. */
    val mindBody = Color(0xFFB67AE8)
    /** Games: a dusty slate blue. */
    val games = Color(0xFF6E8FB8)
}

/** Glow tints that belong to a card, not a metric. */
object GlowTints {
    /** The "Last test" card's pink rim ("Bloom"). */
    val bloom = Color(0xFFD65AA0)
}

private val Dark = darkColorScheme(
    // No accent: controls (switches, checkboxes, buttons, progress) are white with black on top, like the
    // marks on the glow cards, so colour in the app always means data. The containers Material would
    // otherwise fill with its own purple-blue are neutral greys.
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF000000),
    primaryContainer = Greys.Control,
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFFFFFFFF),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Greys.Control,
    onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = Color(0xFFFFFFFF),
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Greys.Control,
    onTertiaryContainer = Color(0xFFFFFFFF),
    // Elevation would tint raised surfaces with the primary; white would lift them to grey.
    surfaceTint = Color.Transparent,
    // OLED black page with neutral grey cards, so the metric accents carry all the colour.
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Greys.Card,
    surfaceContainer = Greys.Raised,
    surfaceContainerHigh = Greys.Control,
    surfaceContainerHighest = Greys.Picked,
    onSurfaceVariant = Ink.Tertiary,
    outline = Color(0xFF2C2C2C),
    outlineVariant = Color(0xFF232323),
    error = Color(0xFFFF6B6B),
)

private fun variable(res: Int, weight: Int) = Font(
    res,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Funnel Display for numbers and headings, Funnel Sans for text. Both SIL OFL 1.1 (see licenses/). */
val FunnelDisplay = FontFamily(
    variable(R.font.funnel_display, 300),
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

/** Always dark: the glow design is built for OLED black and doesn't follow the system light mode. */
@Composable
fun OpenStrapTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Dark,
        typography = AppTypography,
        content = content,
    )
}
