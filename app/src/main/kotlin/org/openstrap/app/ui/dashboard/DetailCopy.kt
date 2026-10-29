package org.openstrap.app.ui.dashboard

import org.openstrap.app.data.History
import org.openstrap.app.l10n.L10n
import org.openstrap.app.l10n.tr
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/*
 * The words and the plain decisions of a detail screen (spec 6.1): the status word beside the value, the label under
 * it for each range, the seven-day strip, where each chart point sits against your normal, and the one time-aware
 * sentence. Built only from numbers the app already has, like [TabCopy], so every rule is tested without a screen.
 */
internal object DetailCopy {
    /** The status word beside a value against your normal (spec 7.2); null without a normal to compare with. */
    fun statusWord(status: Int?): String? = when (status) {
        null -> null
        1 -> tr("Above your normal", "Acima do teu normal")
        -1 -> tr("Below your normal", "Abaixo do teu normal")
        else -> tr("Usual", "Habitual")
    }

    /** Where a chart's point sits against your normal: inside it stays dim, above or below it gets the full colour. */
    enum class Level { Inside, Above, Below }

    /** Each value's [Level] against [normal]; null for a missing value. Without a normal every point is [Level.Inside]. */
    fun levels(values: List<Float?>, normal: History.Normal?): List<Level?> = values.map { v ->
        when {
            v == null -> null
            normal == null -> Level.Inside
            v > normal.high -> Level.Above
            v < normal.low -> Level.Below
            else -> Level.Inside
        }
    }

    /** The band's label on the chart: "Your normal, 44 to 58". */
    fun bandLabel(normal: History.Normal, format: (Float) -> String): String =
        tr("Your normal, ${format(normal.low)} to ${format(normal.high)}", "O teu normal, ${format(normal.low)} a ${format(normal.high)}")

    /** Before there's a normal: "Learning your normal, 4 of 7 days" (spec 2.7). */
    fun learningNormal(days: Int, nightly: Boolean): String {
        val n = days.coerceAtMost(History.MIN_NORMAL_DAYS)
        return tr(
            "Learning your normal, $n of ${History.MIN_NORMAL_DAYS} ${if (nightly) "nights" else "days"}",
            "A aprender o teu normal, $n de ${History.MIN_NORMAL_DAYS} ${if (nightly) "noites" else "dias"}",
        )
    }

    /**
     * The latest run of values off your normal on one side, counting back from the last value and skipping days with
     * none: its side and its length. Null when the last value is inside your normal, or there is none.
     */
    fun latestRun(levels: List<Level?>): Pair<Level, Int>? {
        val known = levels.filterNotNull()
        val side = known.lastOrNull()?.takeIf { it != Level.Inside } ?: return null
        return side to known.takeLastWhile { it == side }.size
    }

    /** Small counts in words, as an English sentence says them: "three nights", "12 days". */
    fun count(n: Int, unit: String): String {
        val word = listOf("no", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine").getOrNull(n) ?: n.toString()
        return "$word $unit${if (n == 1) "" else "s"}"
    }

    /** "days" or "nights", singular. */
    private fun dayWord(nightly: Boolean) = if (nightly) "night" else "day"

    /**
     * The label under the value: what the number is for the range. "bpm now", "ms, day average", "steps, average of
     * the last 7 days". [live] is heart rate read live this minute.
     */
    fun valueLabel(metric: Metric, range: Range, live: Boolean = false, today: Boolean = true): String {
        val unit = metric.unit
        fun with(text: String) = if (unit.isEmpty()) text.replaceFirstChar { it.uppercase() } else "$unit, $text"
        return when (range) {
            Range.Day -> when (metric) {
                Metric.HeartRate -> if (live) tr("bpm now", "bpm agora") else tr("bpm, latest", "bpm, última leitura")
                Metric.RestingHr -> tr("bpm resting", "bpm em repouso")
                Metric.Steps -> if (today) tr("steps so far today", "passos hoje até agora") else tr("steps", "passos")
                Metric.Breathing -> tr("br/min during sleep", "resp/min a dormir")
                else -> with(tr("day average", "média do dia"))
            }
            else -> {
                val span = when (range) {
                    Range.Week -> tr("last 7 days", "últimos 7 dias")
                    Range.Month -> tr("last 30 days", "últimos 30 dias")
                    else -> tr("last 12 months", "últimos 12 meses")
                }
                if (metric == Metric.Steps) tr("steps a day, $span", "passos por dia, $span") else with(tr("average of the $span", "média dos $span"))
            }
        }
    }

    /**
     * The day's sentence for a value against your normal, in the words of where the number comes from ([source]):
     * "Last night, within your normal." for a value from the night, "Today's average so far, ..." for the average of
     * the day's readings, "So far today, ..." for a running total, and "That night", "That day's average" or "That
     * day" for a past one. While your normal is still being learned it says how far along it is ([days] with data).
     */
    fun day(status: Int?, source: DaySource, today: Boolean, days: Int): String {
        val word = when (status) {
            null -> return learningNormal(days, source == DaySource.Night) + "."
            1 -> tr("above your normal", "acima do teu normal")
            -1 -> tr("below your normal", "abaixo do teu normal")
            else -> tr("within your normal", "dentro do teu normal")
        }
        val prefix = when (source) {
            DaySource.Night -> if (today) tr("Last night", "Na noite passada") else tr("That night", "Nessa noite")
            DaySource.Average -> if (today) tr("Today's average so far", "A média de hoje até agora") else tr("That day's average", "A média desse dia")
            DaySource.SoFar -> if (today) tr("So far today", "Hoje até agora") else tr("That day", "Nesse dia")
        }
        return "$prefix, $word."
    }

    /** Under the state word when there's no value: why, for the range. */
    fun noValue(metric: Metric, range: Range, today: Boolean): String = when (range) {
        Range.Day -> when {
            metric == Metric.Breathing -> tr("Measured during sleep", "Medido durante o sono")
            daySource(metric) == DaySource.Night -> if (today) tr("For last night", "Da noite passada") else tr("For that night", "Dessa noite")
            else -> if (today) tr("So far today", "Hoje até agora") else tr("For that day", "Desse dia")
        }
        Range.Week -> tr("In the last 7 days", "Nos últimos 7 dias")
        Range.Month -> tr("In the last 30 days", "Nos últimos 30 dias")
        Range.Year -> tr("In the last 12 months", "Nos últimos 12 meses")
    }

    /** Heart rate's day sentence: its spread, since your normal is a daily average and not a reading. */
    fun heartDay(low: Int, high: Int, today: Boolean): String =
        if (today) tr("Between $low and $high bpm so far today.", "Entre $low e $high bpm hoje até agora.")
        else tr("Between $low and $high bpm that day.", "Entre $low e $high bpm nesse dia.")

    /**
     * A week's or a month's sentence from the daily [levels] against your normal: a run off it first ("Below your
     * normal for three nights."), otherwise how many days sat inside it. Null with no values at all.
     */
    fun span(range: Range, levels: List<Level?>, nightly: Boolean, normalKnown: Boolean, days: Int): String? {
        val known = levels.filterNotNull()
        if (known.isEmpty()) return null
        if (!normalKnown) return learningNormal(days, nightly) + "."
        val unit = dayWord(nightly)
        if (L10n.portuguese) {
            val noun = { n: Int -> if (nightly) (if (n == 1) "1 noite" else "$n noites") else (if (n == 1) "1 dia" else "$n dias") }
            latestRun(levels)?.takeIf { it.second >= 2 }?.let { (side, n) ->
                return "${if (side == Level.Above) "Acima" else "Abaixo"} do teu normal há ${noun(n)}."
            }
            val inside = known.count { it == Level.Inside }
            val over = if (range == Range.Month) " nos últimos 30 dias" else ""
            val every = if (nightly) "todas as noites" else "todos os dias"
            return when (inside) {
                known.size -> "Dentro do teu normal em $every${if (known.size < range.days) " com dados" else ""}$over."
                else -> "Dentro do teu normal em $inside de ${noun(known.size)}$over."
            }
        }
        latestRun(levels)?.takeIf { it.second >= 2 }?.let { (side, n) ->
            return "${if (side == Level.Above) "Above" else "Below"} your normal for ${count(n, unit)}."
        }
        val inside = known.count { it == Level.Inside }
        val over = if (range == Range.Month) " in the last 30 days" else ""
        return when (inside) {
            known.size -> "Within your normal on every ${unit}${if (known.size < range.days) " with data" else ""}$over."
            else -> "Within your normal on $inside of ${known.size} ${unit}s$over."
        }
    }

    /** The year's sentence, from its weekly averages: "Over the last 12 months, weeks averaged from 48 to 61 ms." */
    fun year(lowest: String, highest: String, unit: String): String =
        tr(
            "Over the last 12 months, weeks averaged from $lowest to $highest${if (unit.isEmpty()) "" else " $unit"}.",
            "Nos últimos 12 meses, as semanas tiveram médias de $lowest a $highest${if (unit.isEmpty()) "" else " $unit"}.",
        )

    /** A score's last 7 days: "Averaged 74 over the last 7 days, from 61 to 84." Null without any. */
    fun scoreWeek(values: List<Int?>): String? {
        val known = values.filterNotNull()
        return when (known.size) {
            0 -> null
            1 -> tr("One score in the last 7 days so far.", "Para já, uma pontuação nos últimos 7 dias.")
            else -> tr(
                "Averaged ${Math.round(known.average())} over the last 7 days, from ${known.min()} to ${known.max()}.",
                "Média de ${Math.round(known.average())} nos últimos 7 dias, de ${known.min()} a ${known.max()}.",
            )
        }
    }

    /**
     * Charge's sentence for the day shown: today what the day took and what's left ([TodayCopy.charge]), a past day
     * where it started and ended.
     */
    fun charge(now: Int?, atWake: Int?, drained: Int?, anchored: Boolean, today: Boolean, hour: Int): String = when {
        today || now == null || !anchored -> TodayCopy.charge(now, drained, anchored, hour)
        atWake != null -> tr("Started the day at $atWake and ended it at $now.", "Começou o dia em $atWake e acabou em $now.")
        else -> tr("Ended the day at $now.", "Acabou o dia em $now.")
    }

    /**
     * Recovery's state when there's no score, as the hero's word and the line under it: still learning ("Learning",
     * "6 of 7 nights"), a night without the heart data a score needs, or waiting for the night.
     */
    fun recoveryState(ready: org.openstrap.app.data.Recovery.Readiness?, today: Boolean): Pair<String, String> = when {
        ready == null -> tr("Waiting", "À espera") to
            if (today) tr("For last night's sleep", "Pelo sono da noite passada") else tr("No sleep recorded that night", "Nenhum sono registado nessa noite")
        ready.phase == org.openstrap.app.data.Recovery.Phase.Withheld ->
            tr("Learning", "A aprender") to
                tr("${ready.baselineNights} of ${org.openstrap.app.data.Recovery.SCORED_NIGHTS} nights", "${ready.baselineNights} de ${org.openstrap.app.data.Recovery.SCORED_NIGHTS} noites")
        ready.withheld -> tr("No score", "Sem pontuação") to tr("Not enough heart data that night", "Faltaram dados do coração nessa noite")
        else -> tr("No score", "Sem pontuação") to tr("Scored each morning from the night", "Pontuada todas as manhãs a partir da noite")
    }

    /** A day of the seven-day strip under the value: its short name, whether it has data, and whether it's shown. */
    data class StripDay(val date: LocalDate, val label: String, val hasData: Boolean, val selected: Boolean)

    /**
     * The seven days under a detail's value. It ends today while the day shown is in the last week, so tapping back
     * through the week keeps the strip still; an older day ends its own strip.
     */
    fun strip(selected: LocalDate, today: LocalDate, withData: Set<LocalDate>, locale: Locale = Locale.getDefault()): List<StripDay> {
        val end = if (!selected.isAfter(today) && selected >= today.minusDays(6)) today else selected
        return (6 downTo 0).map { end.minusDays(it.toLong()) }.map { d ->
            StripDay(d, d.dayOfWeek.getDisplayName(TextStyle.SHORT, locale), d in withData, d == selected)
        }
    }
}
