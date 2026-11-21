package org.openstrap.app.data

import org.openstrap.app.data.db.WeightEntry
import java.time.LocalDate

/**
 * The fork's weight log ([WeightEntry]): one weight a day, read as 7-day averages, so a day's water and meals don't
 * read as a change. Pure, for the You row and the weight sheet ([org.openstrap.app.ui.device.WeightSheet]).
 */
object WeightLog {
    /** The weeks the chart shows, newest last. */
    const val WEEKS = 12

    /** How far back the log is read: the chart's weeks and a little more. */
    const val DAYS = 7L * WEEKS + 7

    /** The 7 days that end on [end], and their average, null with nothing logged in them. */
    data class Week(val end: LocalDate, val kg: Float?)

    /** The last weight logged up to today, the last 7 days' average and the 7 days' before it. */
    data class Summary(val latest: WeightEntry, val week: Float?, val weekBefore: Float?) {
        /** The 7-day average against the 7 days before, when both have a weight. */
        val change: Float? get() = if (week != null && weekBefore != null) week - weekBefore else null
    }

    /** The [count] 7-day stretches ending [today], oldest first: today's 7 days last, then a week back each. */
    fun weeks(entries: List<WeightEntry>, today: LocalDate, count: Int = WEEKS): List<Week> = (count - 1 downTo 0).map { i ->
        val end = today.minusDays(7L * i)
        val start = end.minusDays(6)
        Week(end, entries.filter { it.day in start..end }.map { it.kg }.takeIf { it.isNotEmpty() }?.average()?.toFloat())
    }

    /** What the You row and the sheet say, or null before the first weight. */
    fun summary(entries: List<WeightEntry>, today: LocalDate): Summary? {
        val latest = entries.filter { it.day <= today }.maxByOrNull { it.day } ?: return null
        val (before, week) = weeks(entries, today, 2)
        return Summary(latest, week.kg, before.kg)
    }

    /** Today's weight, if one was logged. */
    fun today(entries: List<WeightEntry>, today: LocalDate): WeightEntry? = entries.firstOrNull { it.day == today }

    /** The kilograms typed in [text] ([imperial] for pounds), or null when it isn't a body weight. */
    fun parse(text: String, imperial: Boolean): Float? {
        val n = text.trim().replace(',', '.').toFloatOrNull() ?: return null
        val kg = if (imperial) org.openstrap.app.data.settings.Units.lbToKg(n) else n
        return kg.takeIf { it in MIN_KG..MAX_KG }
    }

    /** The range a weight is taken in, as the profile takes it. */
    private const val MIN_KG = 25f
    private const val MAX_KG = 300f
}
