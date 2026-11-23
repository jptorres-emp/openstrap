package org.openstrap.app.data.settings

import org.openstrap.app.R
import org.openstrap.app.data.settings.UserProfile.Companion.CM_PER_INCH
import org.openstrap.app.data.settings.UserProfile.Companion.KG_PER_LB
import org.openstrap.app.i18n.Strings
import kotlin.math.roundToInt

/**
 * The system distances are written in: kilometres and metres, or miles and feet. It follows the profile's Metric and
 * Imperial choice and only changes what is shown. Stored distances, GPX and the Health Connect export stay metric.
 */
enum class UnitSystem(
    /** Metres in the unit a long distance, a split and a pace are counted in: a kilometre, a mile. */
    val splitMeters: Double,
) {
    Metric(METERS_PER_KM),
    Imperial(METERS_PER_MILE),
}

private const val METERS_PER_KM = 1000.0
private const val METERS_PER_MILE = 1609.344

/**
 * The [UnitSystem] in force, kept here so data code, widgets, notifications and tests can write a distance without a
 * Context, as the clock setting does for 12 or 24 hours. [SettingsStore] sets it from the
 * profile when the app starts and whenever the profile changes. Until then it's metric.
 */
object UnitSetting {
    @Volatile var system: UnitSystem = UnitSystem.Metric
}

/** Body measurement conversions. The profile is stored metric; imperial is only for entry and display. */
object Units {
    fun kgToLb(kg: Float): Float = kg / KG_PER_LB
    fun lbToKg(lb: Float): Float = lb * KG_PER_LB

    /** 178 cm -> 5 ft 10 in (rounded to the nearest inch, never "5 ft 12 in"). */
    fun cmToFeetInches(cm: Int): Pair<Int, Int> {
        val inches = (cm / CM_PER_INCH).roundToInt()
        return inches / 12 to inches % 12
    }

    fun feetInchesToCm(feet: Int, inches: Int): Int = ((feet * 12 + inches) * CM_PER_INCH).roundToInt()

    fun formatWeight(kg: Float, imperial: Boolean, strings: Strings = Strings.app): String =
        if (imperial) {
            strings.format(R.string.core_unit_lb, kgToLb(kg).roundToInt())
        } else {
            strings.format(R.string.core_unit_kg, kg.roundToInt())
        }

    fun formatHeight(cm: Int, imperial: Boolean, strings: Strings = Strings.app): String =
        if (imperial) {
            cmToFeetInches(cm).let { (ft, inch) -> strings.format(R.string.core_unit_feet_inches, ft, inch) }
        } else {
            strings.format(R.string.core_unit_cm, cm)
        }
}
