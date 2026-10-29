package org.openstrap.app.ui.dashboard

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.openstrap.app.data.JournalHabit
import org.openstrap.app.data.Reports
import org.openstrap.app.l10n.tr

/** The journal's words ([JournalHabit]): its sheet, Today's strip and each habit's effect so far. Pure, like [ReportCopy]. */
object JournalCopy {
    /** The sheet's and the strip's title: yesterday's, today's, or the day's. */
    fun title(day: LocalDate, today: LocalDate): String = when (day) {
        today.minusDays(1) -> tr("Yesterday's journal", "Diário de ontem")
        today -> tr("Today's journal", "Diário de hoje")
        else -> tr("Journal, ${day.format(DAY)}", "Diário, ${day.format(DAY)}")
    }

    val NOTE: String
        get() = tr(
            "Tick what you did. Over the weeks the monthly report shows what each does to your Recovery the morning after. " +
                "Save even with nothing ticked: the day then counts as one without them.",
            "Marca o que fizeste. Com as semanas, o relatório mensal mostra o que cada um faz à tua Recuperação na manhã " +
                "seguinte. Guarda mesmo sem nada marcado: assim o dia conta como um dia sem eles.",
        )

    /** Today's strip, on the right: "To fill in", "None", the one habit, or how many. */
    fun summary(habits: Set<JournalHabit>?): String = when {
        habits == null -> tr("To fill in", "Por preencher")
        habits.isEmpty() -> tr("None", "Nenhum")
        habits.size == 1 -> habits.first().label
        else -> tr("${habits.size} habits", "${habits.size} hábitos")
    }

    /**
     * A habit's line in the sheet: what it does to the next morning once that shows ("−12% Recovery the morning after"),
     * how far it has got before that, else what counts ([JournalHabit.hint]).
     */
    fun line(habit: JournalHabit, effect: Reports.Effect?): String {
        val e = effect ?: return habit.hint
        val value = ReportCopy.habitValue(e)
        val recovery = e.recovery != null && (e.recoveryVerdict == Reports.Verdict.Clear || e.recoveryVerdict == Reports.Verdict.Likely)
        return when {
            value != null && recovery -> tr("$value Recovery the morning after", "$value na Recuperação da manhã seguinte")
            value != null -> tr("$value HRV the morning after", "$value na VFC da manhã seguinte")
            e.recoveryVerdict != Reports.Verdict.TooFew -> tr("No clear effect yet, ${e.mornings} days", "Ainda sem efeito claro, ${e.mornings} dias")
            e.mornings < Reports.MIN_HABIT_DAYS -> tr("Learning, ${e.mornings} of ${Reports.MIN_HABIT_DAYS} days", "A aprender, ${e.mornings} de ${Reports.MIN_HABIT_DAYS} dias")
            else -> tr("Learning, needs days without it", "A aprender, faltam dias sem ele")
        }
    }

    /** At the foot of the monthly report's habits: where the journal is filled in. */
    val FILL_IN: String get() = tr("Fill in yesterday's journal", "Preencher o diário de ontem")

    private val DAY: DateTimeFormatter get() = DateTimeFormatter.ofPattern("EEE d MMM")
}
