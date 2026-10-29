package org.openstrap.app.ui.dashboard

import org.openstrap.app.data.DailyMetric
import org.openstrap.app.data.DayStat
import org.openstrap.app.data.History
import org.openstrap.app.data.SleepRegularity
import org.openstrap.app.ui.components.ChartPoint
import kotlin.math.abs
import kotlin.math.roundToInt
import org.openstrap.app.l10n.tr

/*
 * The numbers and words of the Sleep tab's details (spec 5.2 and 6.1): the night-scoped vitals, the Sleep score, Sleep
 * regularity and the day's naps. Built only from what the day already loads, like [DetailCopy], so every rule is
 * tested without a screen. A night-scoped value always comes from the sleep itself, and its words say "Last night" or
 * "That night".
 */
internal object SleepCopy {
    /* ------------------------------------------- Night vitals ------------------------------------------- */

    /** The night shown, with the vitals averaged over its sleep; null without a main sleep that night. */
    fun tonight(state: DayUiState): SleepNight? = state.nights.lastOrNull()?.takeIf { it.date == state.date && state.sleep != null }

    /**
     * A night's value of [metric] from its sleep: the vitals averaged over the sleep, and for heart rate the strap's
     * average for the night. Null for a metric that isn't a night vital.
     */
    fun pick(metric: Metric): ((SleepNight) -> Float?)? = when (metric) {
        Metric.Hrv -> { n -> n.hrv }
        Metric.Breathing -> { n -> n.breathing }
        Metric.Spo2 -> { n -> n.spo2 }
        Metric.SkinTemperature -> { n -> n.skinTemperature }
        Metric.HeartRate -> { n -> n.session?.averageHeartRate?.toFloat() }
        else -> null
    }

    /** The night's heart rate readings while asleep (5-minute averages). */
    fun sleepHeart(state: DayUiState): List<ChartPoint> =
        state.sleep?.let { s -> state.sleepHeartRate.filter { it.x in s.start..s.end } }.orEmpty()

    /** The lowest heart rate while asleep, and when. */
    fun lowestHeart(state: DayUiState): ChartPoint? = sleepHeart(state).minByOrNull { it.y }

    /** The average heart rate while asleep: the strap's for the night, else the average of the readings. */
    fun averageHeart(state: DayUiState): Int? =
        state.sleep?.averageHeartRate ?: sleepHeart(state).takeIf { it.isNotEmpty() }?.map { it.y }?.average()?.roundToInt()

    /** The night's value of [metric], the one its night vital row shows. */
    fun nightValue(state: DayUiState, metric: Metric): Float? =
        if (metric == Metric.HeartRate) averageHeart(state)?.toFloat() else tonight(state)?.let { n -> pick(metric)?.invoke(n) }

    /** The nights before the one shown, oldest first. */
    private fun previous(state: DayUiState): List<SleepNight> = state.nights.filter { it.date < state.date }

    /**
     * Your normal of [metric] at night, from the nights before the one shown (up to [SLEEP_NIGHTS] - 1 of them), as the
     * night vitals read it. Heart rate has none: its normal is a resting one, not an average while asleep.
     */
    fun nightNormal(state: DayUiState, metric: Metric): History.Normal? {
        if (metric == Metric.HeartRate) return null
        val p = pick(metric) ?: return null
        return History.normal(previous(state).mapNotNull { n -> p(n)?.let { DayStat(n.date, it, it, it, 1) } }, state.date.minusDays(1), SLEEP_NIGHTS - 1)
    }

    /** How many of the nights before the one shown have a value of [metric], for "Learning your normal, 4 of 7 nights". */
    fun nightNormalNights(state: DayUiState, metric: Metric): Int = pick(metric)?.let { p -> previous(state).count { p(it) != null } } ?: 0

    /** The last 7 nights' values of [metric], oldest first, the night shown last with the same value as its Day. */
    fun nightWeek(state: DayUiState, metric: Metric): List<Pair<java.time.LocalDate, Float?>> {
        val p = pick(metric) ?: return emptyList()
        return state.nights.takeLast(org.openstrap.app.data.SleepHistory.WEEK_NIGHTS).map { n ->
            n.date to if (n.date == state.date) nightValue(state, metric) else n.session?.let { p(n) }
        }
    }

    /** The night's readings of [metric] while asleep, for its line. */
    fun nightSeries(state: DayUiState, metric: Metric): List<ChartPoint> {
        val s = state.sleep ?: return emptyList()
        val series = when (metric) {
            Metric.HeartRate -> state.sleepHeartRate
            Metric.Hrv -> state.sleepHrv
            Metric.Breathing -> state.breathRate
            Metric.Spo2 -> state.sleepSpo2
            Metric.SkinTemperature -> state.sleepSkinTemperature
            else -> emptyList()
        }
        return series.filter { it.x in s.start..s.end }
    }

    /**
     * The per-day series a night-scoped detail's Month and Year draw: blood oxygen's own night table, and for the rest
     * the metric's usual per-day one.
     */
    fun nightDaily(metric: Metric): DailyMetric = if (metric == Metric.Spo2) DailyMetric.SleepSpo2 else metric.daily

    /** Whether [nightDaily] comes only from sleep (breathing rate, night blood oxygen), so Month and Year are nights too. */
    fun dailyIsNightly(metric: Metric): Boolean = metric == Metric.Spo2 || metric == Metric.Breathing

    /**
     * The label under a night-scoped value: "ms, average asleep", "ms, average of the last 7 nights". Month and Year say
     * whether their days are nights or whole days, since only the night table and breathing rate are from sleep alone.
     */
    fun nightLabel(metric: Metric, range: Range): String {
        val u = metric.unit
        val nightly = dailyIsNightly(metric)
        return when (range) {
            Range.Day -> tr("$u, average asleep", "$u, média a dormir")
            Range.Week -> tr("$u, average of the last 7 nights", "$u, média das últimas 7 noites")
            Range.Month -> if (nightly) tr("$u, last 30 nights", "$u, últimas 30 noites") else tr("$u, last 30 days, day and night", "$u, últimos 30 dias, dia e noite")
            Range.Year -> if (nightly) tr("$u, nights of the last 12 months", "$u, noites dos últimos 12 meses") else tr("$u, last 12 months, day and night", "$u, últimos 12 meses, dia e noite")
        }
    }

    /** Under the state word when the night has no value of a vital. */
    fun nightNoValue(hasSleep: Boolean, today: Boolean): Pair<String, String> =
        if (!hasSleep) tr("No sleep", "Sem sono") to (if (today) tr("None recorded last night", "Nada registado na noite passada") else tr("None recorded that night", "Nada registado nessa noite"))
        else tr("No reading", "Sem leitura") to (if (today) tr("Not measured last night", "Não medido na noite passada") else tr("Not measured that night", "Não medido nessa noite"))

    /** The night's heart rate sentence: "Lowest 48 bpm at 03:10 last night, 55 on average while asleep." */
    fun nightHeart(lowest: Int?, at: String?, average: Int, today: Boolean): String {
        val night = if (today) tr("last night", "na noite passada") else tr("that night", "nessa noite")
        return if (lowest != null && at != null) tr(
            "Lowest $lowest bpm at $at $night, $average on average while asleep.",
            "Mínimo de $lowest bpm às $at $night, $average em média a dormir.",
        )
        else tr("$average bpm on average while asleep $night.", "$average bpm em média a dormir $night.")
    }

    /**
     * The night heart rate's week, without a normal to read it against: "Averaged 55 bpm asleep over the last 7
     * nights, from 51 to 60." Null without any.
     */
    fun heartWeek(values: List<Int?>): String? {
        val known = values.filterNotNull()
        return when (known.size) {
            0 -> null
            1 -> tr(
                "One night with an average heart rate in the last 7 so far.",
                "Para já, uma noite com frequência cardíaca média nas últimas 7.",
            )
            else -> tr(
                "Averaged ${Math.round(known.average())} bpm asleep over the last 7 nights, from ${known.min()} to " +
                    "${known.max()}.",
                "Média de ${Math.round(known.average())} bpm a dormir nas últimas 7 noites, de ${known.min()} a " +
                    "${known.max()}.",
            )
        }
    }

    /* -------------------------------------------- Sleep score ------------------------------------------- */

    /** The label under the score. */
    fun scoreLabel(today: Boolean): String = if (today) tr("last night", "na noite passada") else tr("that night", "nessa noite")

    /** The Sleep score's state with no score: no sleep at all, or a night the strap didn't score. */
    fun noScore(hasSleep: Boolean, today: Boolean): Pair<String, String> = when {
        !hasSleep -> tr("No sleep", "Sem sono") to (if (today) tr("None recorded last night", "Nada registado na noite passada") else tr("None recorded that night", "Nada registado nessa noite"))
        else -> tr("No score", "Sem pontuação") to (if (today) tr("The strap didn't score last night", "A strap não pontuou a noite passada") else tr("The strap didn't score that night", "A strap não pontuou essa noite"))
    }

    /** The night's sentence: what pulled it down or lifted it, then how long ("Short night. 5 h 50 asleep."). */
    fun night(headline: String?, asleepSeconds: Long, others: List<Long>): String =
        listOfNotNull(headline?.let { "$it." }, TabCopy.night(asleepSeconds, others)).joinToString(" ")

    /** One row of What shaped the score: its name, the reading behind it, its word, how full its bar is, and whether it's lit. */
    data class Factor(val name: String, val detail: String, val word: String, val fraction: Float, val off: Boolean)

    /**
     * What shaped the score, from [Narrative.sleepContributors]: time asleep against your need and minutes awake after
     * falling asleep, each against general sleep guidance. The words say what each did to the night, never the
     * strap's own bands ("Optimal", "Pay attention").
     */
    fun factors(contributors: List<Narrative.Contributor>, asleepSeconds: Long, needMinutes: Int, wasoMinutes: Int?): List<Factor> =
        contributors.map { c ->
            val detail = when (c.label) {
                "Total sleep" -> tr(
                    "${TodayCopy.duration(asleepSeconds)} of ${TodayCopy.duration(needMinutes * 60L)}",
                    "${TodayCopy.duration(asleepSeconds)} de ${TodayCopy.duration(needMinutes * 60L)}",
                )
                else -> tr(
                    "${TodayCopy.duration((wasoMinutes ?: 0) * 60L)} awake after falling asleep",
                    "${TodayCopy.duration((wasoMinutes ?: 0) * 60L)} acordado depois de adormecer",
                )
            }
            val name = if (c.label == "Total sleep") tr("Time asleep", "Tempo a dormir") else tr("Staying asleep", "Continuidade do sono")
            Factor(name, detail, factorWord(c.rating), c.fraction, c.rating != Narrative.Rating.GOOD)
        }

    /** A factor's word, by its rating against sleep guidance. */
    fun factorWord(rating: Narrative.Rating): String = when (rating) {
        Narrative.Rating.OPTIMAL -> tr("Helping", "A ajudar")
        Narrative.Rating.GOOD -> tr("Good", "Bom")
        Narrative.Rating.FAIR -> tr("Holding it back a little", "A travar um pouco")
        Narrative.Rating.LOW -> tr("Holding it back", "A travar")
    }

    /** The bedtime and wake figures' labels. */
    val FELL_ASLEEP: String get() = tr("Fell asleep", "Adormeceste")
    val WOKE: String get() = tr("Woke", "Acordaste")
    val ASLEEP: String get() = tr("Asleep", "A dormir")

    /* --------------------------------------------- Regularity ------------------------------------------- */

    /** The label under the regularity number. */
    val REGULARITY_LABEL: String get() = tr("last 7 nights", "últimas 7 noites")

    /** Regularity's state with no number: still learning, with what it needs or how the bedtimes moved. */
    fun regularityState(reg: SleepRegularity.Result?): Pair<String, String> = tr("Learning", "A aprender") to TabCopy.regularity(reg)

    /** The week's sentence, by its band, or by how much the middle of your sleep moved while the full measure waits. */
    fun regularity(reg: SleepRegularity.Result?): String {
        val band = reg?.band
        val sd = reg?.midpointSdMinutes
        return when {
            band == SleepRegularity.Band.VeryRegular -> tr(
                "Very regular this week. Your days kept nearly the same sleep times.",
                "Muito regular esta semana. Os teus dias mantiveram quase as mesmas horas de sono.",
            )
            band == SleepRegularity.Band.Regular -> tr(
                "Regular this week. Most days kept similar sleep times.",
                "Regular esta semana. A maioria dos dias manteve horas de sono parecidas.",
            )
            band == SleepRegularity.Band.Fair -> tr(
                "Fair this week. Your sleep times moved on some days.",
                "Razoável esta semana. As tuas horas de sono mudaram em alguns dias.",
            )
            band == SleepRegularity.Band.Irregular -> tr(
                "Irregular this week. Your sleep times moved a lot from day to day.",
                "Irregular esta semana. As tuas horas de sono mudaram muito de dia para dia.",
            )
            sd != null -> tr(
                "The middle of your sleep moved about ${TodayCopy.duration(sd.roundToInt() * 60L)} this week. The " +
                    "full measure needs more wear.",
                "O meio do teu sono mudou cerca de ${TodayCopy.duration(sd.roundToInt() * 60L)} esta semana. A " +
                    "medida completa precisa de mais uso.",
            )
            else -> tr(
                "Regularity needs about 5 days of wear around the clock.",
                "A regularidade precisa de cerca de 5 dias de uso, dia e noite.",
            )
        }
    }

    /**
     * The night shown against your usual times, from minutes past midnight: "Last night you fell asleep at 23:40 and
     * woke at 07:05, 30 min later than usual." Within 20 minutes of your usual bedtime it says so.
     */
    fun regularityNight(bedMinutes: Int, wakeMinutes: Int, usualBed: Int?, today: Boolean): String {
        val base = if (today) tr("Last night you fell asleep at ${clock(bedMinutes)} and woke at ${clock(wakeMinutes)}", "Na noite passada adormeceste às ${clock(bedMinutes)} e acordaste às ${clock(wakeMinutes)}")
        else tr("That night you fell asleep at ${clock(bedMinutes)} and woke at ${clock(wakeMinutes)}", "Nessa noite adormeceste às ${clock(bedMinutes)} e acordaste às ${clock(wakeMinutes)}")
        usualBed ?: return "$base."
        val diff = Math.floorMod(bedMinutes - usualBed + 12 * 60, 24 * 60) - 12 * 60
        return when {
            abs(diff) < USUAL_MINUTES -> tr("$base, close to your usual.", "$base, perto do habitual.")
            diff > 0 -> tr(
                "$base, ${TodayCopy.duration(diff * 60L)} later than usual.",
                "$base, ${TodayCopy.duration(diff * 60L)} mais tarde do que o habitual.",
            )
            else -> tr(
                "$base, ${TodayCopy.duration(-diff * 60L)} earlier than usual.",
                "$base, ${TodayCopy.duration(-diff * 60L)} mais cedo do que o habitual.",
            )
        }
    }

    /** A bedtime this close to your usual one is "close to your usual". */
    const val USUAL_MINUTES = 20

    /** The night shown, without a sleep. */
    fun noNight(today: Boolean): String = if (today) tr("No sleep recorded last night.", "Nenhum sono registado na noite passada.") else tr("No sleep recorded that night.", "Nenhum sono registado nessa noite.")

    private fun clock(m: Int) = Math.floorMod(m, 1440).let { "%02d:%02d".format(it / 60, it % 60) }

    /* ----------------------------------------------- Naps ----------------------------------------------- */

    /** The label under the time napped. */
    fun napsLabel(today: Boolean): String = if (today) tr("napped today", "de sesta hoje") else tr("napped that day", "de sesta nesse dia")

    /** The naps detail's state with none. */
    fun noNaps(today: Boolean): Pair<String, String> = tr("No naps", "Sem sestas") to (if (today) tr("None recorded today", "Nenhuma registada hoje") else tr("None recorded that day", "Nenhuma registada nesse dia"))

    /** The day's naps in a sentence: "One nap, 45 min." or "Two naps, 1 h 10 in all." */
    fun naps(count: Int, totalSeconds: Long): String =
        if (count == 1) tr("One nap, ${TodayCopy.duration(totalSeconds)}.", "Uma sesta, ${TodayCopy.duration(totalSeconds)}.")
        else tr("${DetailCopy.count(count, "nap").replaceFirstChar { it.uppercase() }}, ${TodayCopy.duration(totalSeconds)} in all.", "$count sestas, ${TodayCopy.duration(totalSeconds)} no total.")

    /** Under a nap's heart rate line. */
    fun napHeart(lowest: Int, average: Int): String = tr("Lowest $lowest bpm, $average on average.", "Mínimo de $lowest bpm, $average em média.")

    /** A nap the strap recorded without stages. */
    val NAP_NO_STAGES: String get() = tr("The strap didn't record stages for this nap.", "A strap não registou fases nesta sesta.")
}
