package org.openstrap.app.data.settings

import org.openstrap.app.data.settings.UserProfile.Companion.CM_PER_INCH
import org.openstrap.app.data.settings.UserProfile.Companion.KG_PER_LB
import kotlin.math.roundToInt

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

    fun formatWeight(kg: Float, imperial: Boolean): String =
        if (imperial) "${kgToLb(kg).roundToInt()} lb" else "${kg.roundToInt()} kg"

    fun formatHeight(cm: Int, imperial: Boolean): String =
        if (imperial) cmToFeetInches(cm).let { (ft, inch) -> "$ft′$inch″" } else "$cm cm"
}
