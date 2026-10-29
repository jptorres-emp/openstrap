package org.openstrap.app.data

import java.time.LocalDate
import org.openstrap.app.l10n.tr

/** A day's journal: the habits ticked, none, or null when it wasn't answered. */
data class JournalDay(val date: LocalDate, val habits: Set<JournalHabit>?)

/**
 * The journal, as WHOOP has it: what you did on a day that the strap can't see, ticked the morning after, so the
 * monthly report can tell what each does to the next morning's Recovery ([Reports.habits]). Kept as day tags
 * (`day_tag`, the day the habit happened). A day counts once it has been answered, even with nothing ticked
 * ([ANSWERED]): a day nobody answered is left out rather than read as a day without them.
 */
enum class JournalHabit(val key: String) {
    Alcohol("alcohol"),
    LateCaffeine("caffeine_late"),
    LateMeal("late_meal"),
    ScreensInBed("screens_bed"),
    ReadingInBed("reading_bed"),
    Stress("stress"),
    Travel("travel"),
    Sick("sick"),
    Meditation("meditation"),
    Stretching("stretching"),
    ;

    /** Its name, as a row and in the reports: short, so it fits a report's row. */
    val label: String
        get() = when (this) {
            Alcohol -> tr("Alcohol", "Álcool")
            LateCaffeine -> tr("Late caffeine", "Café tarde")
            LateMeal -> tr("Late dinner", "Jantar tarde")
            ScreensInBed -> tr("Screens in bed", "Ecrãs na cama")
            ReadingInBed -> tr("Reading in bed", "Ler na cama")
            Stress -> tr("Stressful day", "Dia stressante")
            Travel -> tr("Travel", "Viagem")
            Sick -> tr("Feeling ill", "Doente")
            Meditation -> tr("Meditation", "Meditação")
            Stretching -> tr("Stretching", "Alongamentos")
        }

    /** What counts, under the name. */
    val hint: String
        get() = when (this) {
            Alcohol -> tr("Any drink", "Qualquer bebida")
            LateCaffeine -> tr("After 2 pm: coffee, tea or energy drinks", "Depois das 14h: café, chá ou bebidas energéticas")
            LateMeal -> tr("Eating within 2 h of bed", "Comer nas 2 h antes de deitar")
            ScreensInBed -> tr("Phone, tablet or TV", "Telemóvel, tablet ou TV")
            ReadingInBed -> tr("A book, not a screen", "Um livro, não um ecrã")
            Stress -> tr("At work or at home", "No trabalho ou em casa")
            Travel -> tr("A long trip or another time zone", "Uma viagem longa ou outro fuso horário")
            Sick -> tr("A cold, a fever or run down", "Constipação, febre ou em baixo")
            Meditation -> tr("Or breathing, 10 min or more", "Ou respiração, 10 min ou mais")
            Stretching -> tr("Or mobility, 10 min or more", "Ou mobilidade, 10 min ou mais")
        }

    companion object {
        /** The tag that says a day was answered, whatever was ticked. */
        const val ANSWERED = "answered"

        fun of(key: String): JournalHabit? = entries.firstOrNull { it.key == key }

        /**
         * The habits ticked for a day, from its tags; null when the day wasn't answered. A day with a habit's tag and no
         * [ANSWERED] (tagged before the journal) counts as answered.
         */
        fun ofDay(tags: Collection<String>): Set<JournalHabit>? {
            val habits = tags.mapNotNull(::of).toSet()
            return if (ANSWERED in tags || habits.isNotEmpty()) habits else null
        }

        /** The tags that keep [habits] as a day's answer. */
        fun tags(habits: Set<JournalHabit>): List<String> = listOf(ANSWERED) + habits.map { it.key }
    }
}
