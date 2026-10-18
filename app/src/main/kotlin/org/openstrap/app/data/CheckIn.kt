package org.openstrap.app.data

/** The morning check-in scale: 1 (poor) to 5 (great). */
object CheckIn {
    const val MIN = 1
    const val MAX = 5
    val LABELS = listOf("Poor", "Low", "OK", "Good", "Great")
    fun label(feeling: Int): String = LABELS[(feeling - MIN).coerceIn(0, LABELS.lastIndex)]
}
