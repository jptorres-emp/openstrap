package org.openstrap.app.data

import org.openstrap.app.l10n.tr

/** The morning check-in scale: 1 (poor) to 5 (great). */
object CheckIn {
    const val MIN = 1
    const val MAX = 5
    val LABELS: List<String> get() = listOf(tr("Poor", "Mal"), tr("Low", "Em baixo"), "OK", tr("Good", "Bem"), tr("Great", "Muito bem"))
    fun label(feeling: Int): String = LABELS[(feeling - MIN).coerceIn(0, LABELS.lastIndex)]
}
