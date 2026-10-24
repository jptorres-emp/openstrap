package org.openstrap.app.data

/**
 * Words for the body-signal alert, written to the FDA's General Wellness guidance as revised on 6 January 2026
 * (docs/algorithms/evidence-review.md, "Wording"). Never name a disease, never call a value abnormal, never show a
 * diagnostic threshold, and never prompt clinical action beyond saying professional evaluation "may be helpful".
 */
object BodySignalsCopy {
    const val TITLE = "Some body signals are off your normal"
    const val SHORT = "Some signals are off your normal"

    /** The tail every alert text ends with: likely everyday causes, a gentle suggestion, and the wellness disclaimer. */
    const val CAUSES = "This can happen with heavy training, alcohol, travel, poor sleep, or when your body is fighting something off."
    const val ADVICE = "An easier day may help. If you feel unwell, checking in with a healthcare professional may be helpful."
    const val DISCLAIMER = "This is a wellness estimate, not a medical assessment."

    /**
     * The push text, as the evidence review gives it: "Several of your overnight signals (resting heart rate, skin
     * temperature) are outside your usual range for the second night. …". The bracket names the signals that are off;
     * "for the second night" is said only when they have been off for two nights or more, else "last night".
     */
    fun pushText(signals: List<String>, nights: Int): String {
        val names = signals.ifEmpty { listOf("resting heart rate") }.joinToString(", ")
        val lead = if (signals.size > 1) "Several of your overnight signals ($names) are" else "Your overnight $names is"
        val time = if (nights >= 2) "for the second night" else "last night"
        return "$lead outside your usual range $time. $CAUSES $ADVICE $DISCLAIMER"
    }
}
