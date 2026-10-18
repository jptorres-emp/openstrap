package org.openstrap.app.data

/**
 * Things the user can say happened on a day, stored in `day_tag` by [key]. They annotate a body-signal alert as a
 * likely cause (never suppress it) and let the calibration review check how each signal responds to known events
 * (docs/algorithms/evidence-review.md). The labels avoid medical words on purpose.
 */
enum class DayTag(val key: String, val label: String) {
    Alcohol("alcohol", "Alcohol"),
    Travel("travel", "Travel"),
    Vaccination("vaccination", "Vaccination"),
    HardSession("hard_session", "Hard session"),
    /** Stored as "sick", the key the evidence review names; shown in wellness words. */
    Unwell("sick", "Feeling unwell");

    companion object {
        fun of(key: String): DayTag? = entries.firstOrNull { it.key == key }
    }
}

/** The morning check-in scale: 1 (poor) to 5 (great). */
object CheckIn {
    const val MIN = 1
    const val MAX = 5
    val LABELS = listOf("Poor", "Low", "OK", "Good", "Great")
    fun label(feeling: Int): String = LABELS[(feeling - MIN).coerceIn(0, LABELS.lastIndex)]
}
