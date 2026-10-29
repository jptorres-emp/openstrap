package org.openstrap.app.ui.dashboard

import org.openstrap.app.data.Recovery
import org.openstrap.app.data.SleepRegularity
import org.openstrap.app.data.Workouts
import org.openstrap.app.l10n.tr
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * The words on the Recovery, Sleep and Activity tabs (spec 5 and 7). Like [TodayCopy], built only from numbers the
 * app already has: where one is missing the line says what it waits for, and nothing is guessed.
 */
object TabCopy {
    /* ---------------------------------------------- Recovery ---------------------------------------------- */

    /** "+3 vs yesterday", "−3 vs yesterday" or "Same as yesterday"; null without yesterday's score. */
    fun vsYesterday(score: Int, yesterday: Int?): String? = when {
        yesterday == null -> null
        score > yesterday -> tr("+${score - yesterday} vs yesterday", "+${score - yesterday} vs ontem")
        score < yesterday -> tr("−${yesterday - score} vs yesterday", "−${yesterday - score} vs ontem")
        else -> tr("Same as yesterday", "Igual a ontem")
    }

    /**
     * The caveat while Recovery calibrates: nights toward the first score, then toward a settled one. Null once
     * there are enough.
     */
    fun learning(phase: Recovery.Phase, nights: Int): String? = when (phase) {
        Recovery.Phase.Withheld -> tr("Learning, $nights of ${Recovery.SCORED_NIGHTS} nights", "A aprender, $nights de ${Recovery.SCORED_NIGHTS} noites")
        Recovery.Phase.Learning -> tr("Learning, $nights of ${Recovery.SETTLED_NIGHTS} nights", "A aprender, $nights de ${Recovery.SETTLED_NIGHTS} noites")
        Recovery.Phase.Scored, Recovery.Phase.Calibrated -> null
    }

    /** A contributor's name on a row. */
    fun factorName(f: Recovery.Factor): String = when (f) {
        Recovery.Factor.HrvBalance -> tr("HRV", "VFC")
        Recovery.Factor.RestingHr -> tr("Resting heart rate", "FC em repouso")
        Recovery.Factor.Sleep -> tr("Last night's sleep", "Sono da noite passada")
        Recovery.Factor.SleepBalance -> tr("Sleep over recent nights", "Sono das últimas noites")
        Recovery.Factor.Temperature -> tr("Skin temperature", "Temperatura da pele")
        Recovery.Factor.Breathing -> tr("Breathing rate", "Frequência respiratória")
        Recovery.Factor.PreviousDayLoad -> tr("Yesterday's Load", "Carga de ontem")
        Recovery.Factor.BloodOxygen -> tr("Blood oxygen", "Oxigénio no sangue")
        Recovery.Factor.RecoveryIndex -> tr("When your heart settled", "Quando o coração acalmou")
    }

    /** [factorName] inside a sentence: in English after "held back by", in Portuguese with its article ("a VFC"). */
    private fun factorPhrase(f: Recovery.Factor): String = if (org.openstrap.app.l10n.L10n.portuguese) when (f) {
        Recovery.Factor.HrvBalance -> "a VFC"
        Recovery.Factor.RestingHr -> "a frequência cardíaca em repouso"
        Recovery.Factor.Sleep -> "o sono da noite passada"
        Recovery.Factor.SleepBalance -> "o sono das últimas noites"
        Recovery.Factor.Temperature -> "a temperatura da pele"
        Recovery.Factor.Breathing -> "a frequência respiratória"
        Recovery.Factor.PreviousDayLoad -> "a Carga de ontem"
        Recovery.Factor.BloodOxygen -> "o oxigénio no sangue"
        Recovery.Factor.RecoveryIndex -> "a hora tardia a que o coração acalmou"
    } else when (f) {
        Recovery.Factor.HrvBalance -> "HRV"
        Recovery.Factor.RecoveryIndex -> "how late your heart settled"
        else -> factorName(f).replaceFirstChar { it.lowercase() }
    }

    /** Contributors this light or lighter never lead the sentence: too small a part of the score. */
    private const val SENTENCE_WEIGHT = 0.06f

    /** Below this a contributor holds the score back ([org.openstrap.app.ui.dashboard.Narrative.ratingFor]'s Good). */
    private const val HELD_BACK_BELOW = 70

    /**
     * The contributor holding Recovery back the most: the lowest scored of those that weigh enough, when it's under
     * [HELD_BACK_BELOW]. The same one the score's old explanation named.
     */
    fun heldBackBy(contributors: List<Recovery.Contributor>): Recovery.Factor? =
        contributors.filter { it.factor.weight >= SENTENCE_WEIGHT }.minByOrNull { it.score }?.takeIf { it.score < HELD_BACK_BELOW }?.factor

    /**
     * The Recovery hero's sentence: what the score means for the day and, short of well recovered, what held it
     * back most. "Recovered enough for your usual training. Mostly held back by HRV."
     */
    fun recovery(score: Int, alert: Boolean, contributors: List<Recovery.Contributor>): String {
        val meaning = TodayCopy.recoveryMeaning(score, alert)
        val by = heldBackBy(contributors)?.takeIf { score < 85 && !alert } ?: return meaning
        return tr("$meaning Mostly held back by ${factorPhrase(by)}.", "$meaning O que mais pesou foi ${factorPhrase(by)}.")
    }

    /** Before a Recovery has been worked out for the day. */
    val RECOVERY_WAITING: String get() = tr("Recovery shows once last night is in.", "A Recuperação aparece quando a noite passada chegar.")

    /** The Recovery hero with no sleep recorded last night: no night to score (spec 4.3). */
    val NO_SLEEP_SCORE: String get() = tr("No sleep recorded last night, so there's no score today.", "Nenhum sono registado na noite passada, por isso hoje não há pontuação.")

    /** A contributor's status word, by the score's own bands. */
    fun contributorWord(score: Int): String = when {
        score >= 85 -> tr("Helping", "A ajudar")
        score >= 70 -> tr("Usual", "Habitual")
        score >= Recovery.FAIR_FROM -> tr("Holding back a little", "A travar um pouco")
        else -> tr("Holding you back", "A travar-te")
    }

    /** Whether a contributor is far enough from usual to take its colour. */
    fun contributorOff(score: Int): Boolean = score < 70 || score >= 85

    /** The [n] contributors that weigh most, heaviest first: the ones "What drove it" shows. */
    fun topDrivers(contributors: List<Recovery.Contributor>, n: Int = 3): List<Recovery.Contributor> =
        contributors.sortedByDescending { it.factor.weight }.take(n)

    /** A Charge amount with its sign: "+32", "−12" with a real minus sign (spec 2.3), or "0" with none. */
    fun chargeAmount(v: Float, sign: Int): String {
        val n = v.toInt()
        return if (n == 0) "0" else "${if (sign > 0) "+" else "−"}$n"
    }

    /**
     * How much Charge was drained, as a plain amount: the "Drained" beside it already says which way, so no minus.
     * The model only ever adds to a drain (each minute's fall), so it's never below zero; the floor is a guard.
     */
    fun drainedAmount(v: Float): String = v.toInt().coerceAtLeast(0).toString()

    /** One row of "Energy today": what moved it, and whether it filled or drained it. */
    data class ChargeRow(val name: String, val value: String, val status: String)

    /** Where today's Charge came from and went, in the order it happened: sleep first, then the day. */
    fun chargeRows(recharged: Float, movement: Float, stress: Float, awake: Float): List<ChargeRow> = listOf(
        ChargeRow(tr("Sleep and calm rest", "Sono e descanso calmo"), chargeAmount(recharged, +1), tr("Recharged", "Recarregou")),
        ChargeRow(tr("Movement", "Movimento"), drainedAmount(movement), tr("Drained", "Gastou")),
        ChargeRow(tr("Stress", "Stress"), drainedAmount(stress), tr("Drained", "Gastou")),
        ChargeRow(tr("Being awake", "Estar acordado"), drainedAmount(awake), tr("Drained", "Gastou")),
    )

    /** The Charge card's line under the number. */
    fun chargeLine(now: Int?, atWake: Int?, anchored: Boolean): String = when {
        !anchored -> tr("Energy starts after your first night. Sleep is what fills it.", "A Energia começa depois da primeira noite. É o sono que a enche.")
        now == null -> tr("Energy shows once the strap has a few hours of heart rate.", "A Energia aparece quando a strap tiver algumas horas de frequência cardíaca.")
        atWake == null -> tr("Now", "Agora")
        now < atWake -> tr("Used ${atWake - now} since waking", "Gastou ${atWake - now} desde que acordaste")
        now > atWake -> tr("Up ${now - atWake} since waking", "Mais ${now - atWake} desde que acordaste")
        else -> tr("Level since waking", "Igual desde que acordaste")
    }

    /* ----------------------------------------------- Sleep ----------------------------------------------- */

    /** Fewer other nights than this in the week and "longest this week" means little. */
    const val WEEK_COMPARE_NIGHTS = 3

    /**
     * The night's sentence: how long you slept, and whether it was the longest or shortest of the week ([others]
     * are the week's other nights, asleep seconds). "7 h 40, your longest this week."
     */
    fun night(asleepSeconds: Long, others: List<Long>): String {
        val d = TodayCopy.duration(asleepSeconds)
        return when {
            others.size < WEEK_COMPARE_NIGHTS -> tr("$d asleep.", "$d a dormir.")
            asleepSeconds > others.max() -> tr("$d, your longest this week.", "$d, a tua noite mais longa esta semana.")
            asleepSeconds < others.min() -> tr("$d, your shortest this week.", "$d, a tua noite mais curta esta semana.")
            else -> tr("$d asleep.", "$d a dormir.")
        }
    }

    /** The Sleep hero with no night to tell. */
    /** The Sleep tab's night before the strap has seen one (spec 4.3). */
    val FIRST_NIGHT_SLEEP: String get() = tr("Your first night with the strap shows here in the morning.", "A tua primeira noite com a strap aparece aqui de manhã.")

    fun noNight(naps: Boolean, isToday: Boolean): String = when {
        naps -> tr("No night sleep recorded, only naps.", "Nenhum sono noturno registado, só sestas.")
        isToday -> TodayCopy.NO_SLEEP
        else -> tr("No sleep recorded that night.", "Nenhum sono registado nessa noite.")
    }

    /** The sleep score's word, in the bands the strap's score is built for. */
    fun sleepScoreWord(score: Int): String = when {
        // Feminine in Portuguese, after "pontuação".
        score >= 90 -> tr("Great", "Ótima")
        score >= 80 -> tr("Good", "Boa")
        score >= 60 -> tr("Fair", "Razoável")
        else -> tr("Poor", "Fraca")
    }

    /** The score's line beside the night's dotted score: "Sleep score, good", or why there's no score. */
    fun sleepScoreLine(score: Int?): String =
        score?.let { tr("Sleep score, ${sleepScoreWord(it).lowercase()}", "Pontuação do sono, ${sleepScoreWord(it).lowercase()}") } ?: NO_STRAP_SCORE

    /** A night the strap gave no score. */
    val NO_STRAP_SCORE: String get() = tr("No score from the strap", "Sem pontuação da strap")

    /** A stage's total on the stage bar's legend: "Deep 1 h 32", "Awake 18 min". */
    fun stageTotal(stage: Int, seconds: Long): String = "${SLEEP_NAMES[stage].orEmpty()} ${TodayCopy.duration(seconds)}"

    /** "7 h 40 asleep". */
    fun asleep(seconds: Long): String = tr("${TodayCopy.duration(seconds)} asleep", "${TodayCopy.duration(seconds)} a dormir")

    /** When the night ran, bedtime to waking: "22:58–06:56" (an en dash, a time range). */
    fun nightTimes(start: Long, end: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String {
        fun clock(epoch: Long) = java.time.Instant.ofEpochSecond(epoch).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))
        return "${clock(start)}–${clock(end)}"
    }

    /** "Woke 3 times", "Woke once", "Slept through"; null when the strap didn't say. */
    fun woke(count: Int?): String? = when (count) {
        null -> null
        0 -> tr("Slept through", "Dormiste de seguida")
        1 -> tr("Woke once", "Acordaste uma vez")
        else -> tr("Woke $count times", "Acordaste $count vezes")
    }

    /** Sleep regularity's word, or what it still needs. */
    fun regularity(reg: SleepRegularity.Result?): String = when {
        reg == null -> tr("Needs about 5 days of wear", "Precisa de cerca de 5 dias de uso")
        reg.sri != null -> reg.band!!.label
        reg.midpointSdMinutes != null -> if (reg.good == true) tr("Steady bedtimes this week", "Horas de deitar regulares esta semana") else tr("Bedtimes moved this week", "Horas de deitar variaram esta semana")
        else -> tr("Needs about 5 days of wear", "Precisa de cerca de 5 dias de uso")
    }

    /** "Usually asleep by 23:10, up at 06:50." from minutes since midnight. */
    fun usualTimes(bedMinutes: Int, wakeMinutes: Int): String =
        tr("Usually asleep by ${minutes(bedMinutes)}, up at ${minutes(wakeMinutes)}.", "Costumas adormecer às ${minutes(bedMinutes)} e acordar às ${minutes(wakeMinutes)}.")

    private fun minutes(m: Int) = Math.floorMod(m, 1440).let { "%02d:%02d".format(it / 60, it % 60) }

    /** Breathing disturbances against your usual, or how many nights it still needs. */
    fun breathing(b: Recovery.BreathingDisturbances): String = when (val usual = b.usual) {
        null -> tr(
            "Learning your usual, ${b.nights.coerceAtMost(Recovery.SCORED_NIGHTS)} of ${Recovery.SCORED_NIGHTS} nights",
            "A aprender o habitual, ${b.nights.coerceAtMost(Recovery.SCORED_NIGHTS)} de ${Recovery.SCORED_NIGHTS} noites",
        )
        else -> tr("Your usual is %.1f an hour", "O teu habitual é %.1f por hora").format(usual)
    }

    /* --------------------------------------------- Activity --------------------------------------------- */

    /** Under Load's number: your usual for a whole day, or that it's still being learned. */
    fun loadUsual(usual: Float?): String =
        if (usual == null || usual <= 0f) tr("Learning your usual", "A aprender o habitual") else tr("Your usual is ${Math.round(usual)}", "O teu habitual é ${Math.round(usual)}")

    /**
     * The week's balance from fatigue (the last 7 days) against fitness (the last 6 weeks): "Building" when the week
     * is well above your usual, "Easing off" when well below, "Steady" between, with the band Today uses for a usual
     * Load ([TodayCopy.LOAD_BAND]). Null before there's a fitness to compare with.
     */
    fun loadBalance(trend: Recovery.LoadTrend?): String? {
        val t = trend ?: return null
        if (t.ctl <= 0f) return null
        return when {
            t.atl > t.ctl * (1 + TodayCopy.LOAD_BAND) -> tr("Building", "A subir")
            t.atl < t.ctl * (1 - TodayCopy.LOAD_BAND) -> tr("Easing off", "A abrandar")
            else -> tr("Steady", "Estável")
        }
    }

    /** Active minutes over the week against the 150 to aim for. */
    fun activeMinutes(week: Int, target: Int): String =
        if (week >= target) tr("This week, goal of $target reached", "Esta semana, objetivo de $target cumprido") else tr("This week, goal $target", "Esta semana, objetivo $target")

    /** Heart points over the last 7 days against the 100 to aim for. */
    fun heartPoints(total: Int, target: Int): String =
        if (total >= target) tr("Last 7 days, at $target or more", "Últimos 7 dias, $target ou mais") else tr("Last 7 days, aim for $target", "Últimos 7 dias, objetivo $target")

    /** Energy under its number. */
    fun energy(partial: Boolean, isToday: Boolean): String = when {
        isToday -> tr("Estimated kcal so far today", "kcal estimadas até agora")
        partial -> tr("Estimated kcal, part of the day worn", "kcal estimadas, strap só parte do dia")
        else -> tr("Estimated kcal", "kcal estimadas")
    }

    /* ------------------------------------------- Small cards ------------------------------------------- */

    /** Under Drained on the Recovery tab: what took the most, or that nothing has yet. */
    fun drainedMostly(movement: Float, stress: Float, awake: Float): String {
        val most = listOf(
            tr("Mostly movement", "Sobretudo movimento") to movement, tr("Mostly stress", "Sobretudo stress") to stress,
            tr("Mostly being awake", "Sobretudo estar acordado") to awake,
        ).maxBy { it.second }
        return if (most.second.toInt() <= 0) tr("Nothing yet", "Nada ainda") else most.first
    }

    /** Under Recharged on the Recovery tab. */
    val RECHARGED: String get() = tr("Sleep and calm rest", "Sono e descanso calmo")

    /** Breathing disturbances as a Small: your usual, or how many nights it still needs, and that it's early. */
    fun breathingShort(b: Recovery.BreathingDisturbances): String = when (val usual = b.usual) {
        null -> tr("Learning, ${b.nights.coerceAtMost(Recovery.SCORED_NIGHTS)} of ${Recovery.SCORED_NIGHTS} nights", "A aprender, ${b.nights.coerceAtMost(Recovery.SCORED_NIGHTS)} de ${Recovery.SCORED_NIGHTS} noites")
        else -> tr("Usual %.1f, early estimate", "Habitual %.1f, estimativa inicial").format(usual)
    }

    /** The naps Small's title: "Nap" for one, "Naps" for more. */
    fun napsTitle(count: Int): String = if (count == 1) tr("Nap", "Sesta") else tr("Naps", "Sestas")

    /** Under the naps Small: when the nap was, or how many and when the first. */
    fun naps(count: Int, firstAt: String): String = if (count == 1) tr("At $firstAt", "Às $firstAt") else tr("$count naps, first at $firstAt", "$count sestas, a primeira às $firstAt")

    /** Under the Recovery hero's trend dots. */
    val LAST_7_DAYS: String get() = tr("Last 7 days", "Últimos 7 dias")

    /* ---------------------------------------------- Workouts --------------------------------------------- */

    /** The quiet tag beside a workout the strap found by itself, or filed under its generic "Activity". */
    val FOUND_BY_STRAP: String get() = tr("Found by the strap", "Detetado pela strap")

    /** Which fact leads a workout's line, by sport. */
    enum class SportFact { Run, Walk, Strength, Other }

    fun sportFact(sportType: Int): SportFact = when (sportType) {
        in Workouts.RUNS -> SportFact.Run
        in Workouts.WALKS -> SportFact.Walk
        in org.openstrap.app.data.Strength.SPORTS -> SportFact.Strength
        else -> SportFact.Other
    }

    /** Shorter than this and a pace says more about GPS noise than the run. */
    private const val PACE_MIN_M = 500f

    /**
     * A workout's one fact, by [sport]: distance and pace for a run ("5.2 km, 5:24 a km"), distance for a walk, sets
     * for strength ("14 sets"), average heart rate otherwise ("Average 128 bpm"). Each falls back to the next the
     * workout has: a run without a GPS pace to its distance, a run or walk without one to heart rate, strength without
     * sets to heart rate, anything else without heart rate to distance. Distance from steps is approximate ("≈ 3.4
     * km") and gets no pace. Only phone workouts carry distance and sets; the strap's fall back to heart rate.
     */
    fun workoutFact(sport: SportFact, distanceMeters: Float?, fromSteps: Boolean, activeSeconds: Long?, sets: Int?, averageHeartRate: Int?): String? {
        val distance = distanceMeters?.takeIf { it >= 10f }?.let { distanceText(it, fromSteps) }
        val pace = distanceMeters?.takeIf { !fromSteps && it >= PACE_MIN_M }?.let { m ->
            activeSeconds?.takeIf { it > 0 }?.let { paceText(it / (m / 1000f)) }
        }
        val heart = averageHeartRate?.takeIf { it > 0 }?.let { tr("Average $it bpm", "Média de $it bpm") }
        val counted = sets?.takeIf { it > 0 }?.let { if (it == 1) tr("1 set", "1 série") else tr("$it sets", "$it séries") }
        return when (sport) {
            SportFact.Run -> pace?.let { "$distance, $it" } ?: distance ?: heart
            SportFact.Walk -> distance ?: heart
            SportFact.Strength -> counted ?: heart
            SportFact.Other -> heart ?: distance
        }
    }

    /** "850 m" under a kilometre, "5.2 km" from one; "≈ " in front when estimated from steps. */
    fun distanceText(meters: Float, fromSteps: Boolean): String =
        (if (fromSteps) "≈ " else "") + if (meters < 1000f) "${meters.toInt()} m" else "%.1f km".format(meters / 1000f)

    /** "5:24 a km"; null for a pace slower than an hour a kilometre, which is standing still. */
    fun paceText(secondsPerKm: Float): String? {
        if (secondsPerKm <= 0f || secondsPerKm > 3600f) return null
        val s = Math.round(secondsPerKm)
        return tr("%d:%02d a km", "%d:%02d por km").format(s / 60, s % 60)
    }

    /**
     * A workout row's second line: its fact, then when it started ("3.4 km, 17:35"), or the time alone.
     * TODO: a personal best ("Fastest 5 km since March") goes after the fact in the mock's warm colour (0xFFFFD08A)
     * once the app detects them; it doesn't yet.
     */
    fun workoutLine(fact: String?, startClock: String): String = listOfNotNull(fact, startClock).joinToString(", ")

    /** The header over a day's workouts: "Today", "Yesterday", then "Wed 21 Oct", with the year when it isn't this one. */
    fun workoutDay(date: LocalDate, today: LocalDate, locale: Locale = Locale.getDefault()): String = when (date) {
        today -> tr("Today", "Hoje")
        today.minusDays(1) -> tr("Yesterday", "Ontem")
        else -> date.format(DateTimeFormatter.ofPattern(if (date.year == today.year) "EEE d MMM" else "EEE d MMM yyyy", locale))
    }

    /* --------------------------------------------- Habit Age --------------------------------------------- */

    /** "Updated Sun 18 Oct", for the Sunday the estimate is for. */
    fun habitAgeUpdated(sunday: LocalDate, locale: Locale = Locale.getDefault()): String =
        tr("Updated ", "Atualizado ") + sunday.format(DateTimeFormatter.ofPattern("EEE d MMM", locale))

    /** "First estimate Sunday 22 Nov", before there's a number. */
    fun firstEstimate(sunday: LocalDate, locale: Locale = Locale.getDefault()): String =
        tr("First estimate ", "Primeira estimativa ") + sunday.format(DateTimeFormatter.ofPattern("EEEE d MMM", locale))

    /** Habit Age's one status line, and whether the number moved, which alone takes the orchid. */
    data class HabitAgeLine(val text: String, val moved: Boolean)

    /**
     * Habit Age against last week's estimate, in whole years: "1 year younger than last week", "Same as last week".
     * Until there's a [lastWeek] it says the range instead: "Likely 27 to 31".
     */
    fun habitAgeLine(estimate: Float, lastWeek: Float?, low: Int, high: Int): HabitAgeLine {
        lastWeek ?: return HabitAgeLine(tr("Likely $low to $high", "Provavelmente $low a $high"), moved = false)
        val d = Math.round(estimate) - Math.round(lastWeek)
        fun years(n: Int) = if (n == 1) tr("1 year", "1 ano") else tr("$n years", "$n anos")
        return when {
            d < 0 -> HabitAgeLine(tr("${years(-d)} younger than last week", "${years(-d)} mais novo do que na semana passada"), moved = true)
            d > 0 -> HabitAgeLine(tr("${years(d)} older than last week", "${years(d)} mais velho do que na semana passada"), moved = true)
            else -> HabitAgeLine(tr("Same as last week", "Igual à semana passada"), moved = false)
        }
    }

    /** The age scale's labels: the range under its band and your age at its hairline. */
    fun ageRange(low: Int, high: Int): String = tr("$low to $high", "$low a $high")
    fun yourAge(age: Int): String = tr("You, $age", "Tu, $age")
}
