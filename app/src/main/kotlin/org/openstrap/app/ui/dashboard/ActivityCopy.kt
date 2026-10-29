package org.openstrap.app.ui.dashboard

import org.openstrap.app.data.Activity
import org.openstrap.app.data.UsualSteps
import org.openstrap.app.l10n.tr
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/*
 * The words of the Activity tab's details (spec 5.3 and 6.1): steps against your usual by now, Heart points, active
 * minutes, Load, VO2 max, time to recover, energy and distance. Built only from numbers the app already has, like
 * [DetailCopy], so every rule is tested without a screen. The method behind each number is in [InfoCopy].
 */
internal object ActivityCopy {
    /** Heart points' weekly aim: 100 or more over the last seven days. */
    const val HEART_POINTS_TARGET = 100

    /* ----------------------------------------------- Steps ---------------------------------------------- */

    /**
     * Steps against your usual by now (spec 2.7, compare by pace): "About usual by now". A past day is read whole,
     * against your usual day.
     */
    fun pace(p: UsualSteps.Pace, today: Boolean): String = when (p) {
        UsualSteps.Pace.More -> if (today) tr("More than usual by now", "Mais do que o habitual a esta hora") else tr("More than your usual day", "Mais do que num dia habitual")
        UsualSteps.Pace.About -> if (today) tr("About usual by now", "Perto do habitual a esta hora") else tr("About your usual day", "Perto de um dia habitual")
        UsualSteps.Pace.Fewer -> if (today) tr("Fewer than usual by now", "Menos do que o habitual a esta hora") else tr("Fewer than your usual day", "Menos do que num dia habitual")
    }

    /**
     * Today's steps against your [usual] running totals by the hour ([UsualSteps.byHour]): by [hour]:[minute] today,
     * by the end of a past day. Null without a usual, or while your usual by now is too small to compare with.
     */
    fun paceOf(steps: Int, usual: List<Float>?, today: Boolean, hour: Int, minute: Int): UsualSteps.Pace? {
        val u = usual?.takeIf { it.size == 24 } ?: return null
        return UsualSteps.pace(steps, if (today) UsualSteps.at(u, hour, minute) else u[23])
    }

    /** How far toward the goal: "Goal met" or "62% of your goal". */
    fun goalShare(steps: Int, goal: Int): String =
        if (steps >= goal) tr("Goal met", "Objetivo cumprido") else tr("${steps * 100 / goal.coerceAtLeast(1)}% of your goal", "${steps * 100 / goal.coerceAtLeast(1)}% do objetivo")

    /** Today's Activity card under the steps: the goal share, then your usual by now when there is one. */
    fun activityLine(steps: Int, goal: Int, pace: UsualSteps.Pace?, today: Boolean): String {
        val share = goalShare(steps, goal)
        return if (pace == null) share else "$share, ${pace(pace, today).replaceFirstChar { it.lowercase() }}"
    }

    /** An hour's steps in the headline while it's tapped: "steps, 09:00 to 10:00". */
    fun hourLabel(hour: Int): String = tr("steps, %02d:00 to %02d:00", "passos, %02d:00 às %02d:00").format(hour, (hour + 1) % 24)

    /* -------------------------------------------- Heart points ------------------------------------------ */

    /** The status word beside the 7-day total; null under the aim, where the sentence says how far to go. */
    fun heartPointsWord(total: Int): String? = if (total >= HEART_POINTS_TARGET) tr("At your aim", "No objetivo") else null

    /** The week's sentence, under the total: how far to the aim, or that it's met. */
    fun heartPointsWeek(total: Int): String = when {
        total >= HEART_POINTS_TARGET -> tr(
            "Keeping it at $HEART_POINTS_TARGET or more week after week is what counts.",
            "O que conta é manter $HEART_POINTS_TARGET ou mais semana após semana.",
        )
        else -> tr("${HEART_POINTS_TARGET - total} to go for $HEART_POINTS_TARGET.", "Faltam ${HEART_POINTS_TARGET - total} para $HEART_POINTS_TARGET.")
    }

    /** What drops off tomorrow: the oldest day's points. Null when that day earned none. */
    fun dropsOff(oldest: LocalDate, points: Int, locale: Locale = Locale.getDefault()): String? {
        if (points <= 0) return null
        val day = oldest.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
        return tr(
            "Tomorrow $day's $points ${if (points == 1) "point drops" else "points drop"} off.",
            if (points == 1) "Amanhã sai o ponto de $day." else "Amanhã saem os $points pontos de $day.",
        )
    }

    /** A day's points in the headline: "points earned, Tue 20 Oct". */
    fun earnedLabel(dayName: String): String = tr("points earned, $dayName", "pontos ganhos, $dayName")

    /** The month's sentence from each day's 7-day total: how often it was at the aim. Null without any. */
    fun heartPointsMonth(totals: List<Int?>): String? {
        val known = totals.filterNotNull()
        if (known.isEmpty()) return null
        val at = known.count { it >= HEART_POINTS_TARGET }
        return when (at) {
            0 -> tr(
                "Under $HEART_POINTS_TARGET every day in the last 30, between ${known.min()} and ${known.max()}.",
                "Abaixo de $HEART_POINTS_TARGET todos os dias dos últimos 30, entre ${known.min()} e ${known.max()}.",
            )
            known.size -> tr("At $HEART_POINTS_TARGET or more every day with a total in the last 30.", "Em $HEART_POINTS_TARGET ou mais todos os dias com total nos últimos 30.")
            else -> tr("At $HEART_POINTS_TARGET or more on $at of ${known.size} days in the last 30.", "Em $HEART_POINTS_TARGET ou mais em $at de ${known.size} dias nos últimos 30.")
        }
    }

    /* ------------------------------------------- Active minutes ----------------------------------------- */

    /** The status word beside the week's active minutes; null under the goal. */
    fun activeWord(week: Int): String? = if (week >= Activity.MVPA_WEEKLY_TARGET) tr("Goal met", "Objetivo cumprido") else null

    /** The week's sentence, under the week's minutes: how far to the 150, or by how much it's past. */
    fun activeWeek(week: Int): String {
        val target = Activity.MVPA_WEEKLY_TARGET
        return when {
            week == target -> tr("Right at the $target to aim for.", "Mesmo nos $target do objetivo.")
            week > target -> tr("${week - target} past the $target to aim for.", "${week - target} acima dos $target do objetivo.")
            else -> tr("${target - week} to go for $target.", "Faltam ${target - week} para $target.")
        }
    }

    /** A day's sentence: its moderate and vigorous minutes. */
    fun activeDay(m: Activity.Mvpa, today: Boolean): String {
        val lead = if (today) tr("So far today", "Hoje até agora") else tr("That day", "Nesse dia")
        return when {
            m.moderateMinutes == 0 && m.vigorousMinutes == 0 ->
                if (today) tr("No active minutes so far today.", "Ainda sem minutos ativos hoje.") else tr("No active minutes that day.", "Sem minutos ativos nesse dia.")
            m.vigorousMinutes == 0 -> tr("$lead, ${minutes(m.moderateMinutes)} of brisk movement.", "$lead, ${minutes(m.moderateMinutes)} de movimento rápido.")
            m.moderateMinutes == 0 -> tr("$lead, ${minutes(m.vigorousMinutes)} of hard effort.", "$lead, ${minutes(m.vigorousMinutes)} de esforço intenso.")
            else -> tr(
                "$lead, ${minutes(m.moderateMinutes)} brisk and ${minutes(m.vigorousMinutes)} hard.",
                "$lead, ${minutes(m.moderateMinutes)} de movimento rápido e ${minutes(m.vigorousMinutes)} de esforço intenso.",
            )
        }
    }

    private fun minutes(n: Int) = if (n == 1) tr("1 minute", "1 minuto") else tr("$n minutes", "$n minutos")

    /* ------------------------------------------------ Load ---------------------------------------------- */

    /** Load's scale in its info sheet, low to high, in the words beside it. */
    val LOAD_SCALE: List<String> get() = listOf(tr("Below your usual", "Abaixo do habitual"), tr("Usual", "Habitual"), tr("Above your usual", "Acima do habitual"))

    /** Which bar of [LOAD_SCALE] a Load is in against your usual for a whole day; null without a usual. */
    fun loadBar(load: Float, usual: Float?): Int? {
        if (usual == null || usual <= 0f) return null
        return when {
            load > usual * (1 + TodayCopy.LOAD_BAND) -> 2
            load < usual * (1 - TodayCopy.LOAD_BAND) -> 0
            else -> 1
        }
    }

    /** Your usual as a band on Load's chart: within [TodayCopy.LOAD_BAND] of it, as Today reads a usual Load. */
    fun usualBand(usual: Float?): ClosedFloatingPointRange<Float>? =
        usual?.takeIf { it > 0f }?.let { it * (1 - TodayCopy.LOAD_BAND)..it * (1 + TodayCopy.LOAD_BAND) }

    /** The band's label: "Your usual, 30 to 50". */
    fun usualBandLabel(band: ClosedFloatingPointRange<Float>): String =
        tr("Your usual, ${Math.round(band.start)} to ${Math.round(band.endInclusive)}", "O teu habitual, ${Math.round(band.start)} a ${Math.round(band.endInclusive)}")

    /**
     * The day's sentence: how it sits among your recent days ([percentile], the share of the last 90 days' loads at or
     * below it), or before there are enough days, that your usual is being learned.
     */
    fun loadDay(percentile: Int?, today: Boolean): String {
        val lead = if (today) tr("So far today", "Hoje até agora") else tr("That day", "Nesse dia")
        return when {
            percentile == null -> tr("Your usual shows after a week of wear.", "O teu habitual aparece depois de uma semana de uso.")
            percentile >= 50 -> tr(
                "$lead, as hard as or harder than $percentile% of your days in the last 3 months.",
                "$lead, tão duro ou mais do que $percentile% dos teus dias nos últimos 3 meses.",
            )
            else -> tr("$lead, easier than ${100 - percentile}% of your days in the last 3 months.", "$lead, mais leve do que ${100 - percentile}% dos teus dias nos últimos 3 meses.")
        }
    }

    /** The week's balance in a sentence, from its word ([TabCopy.loadBalance]). */
    fun balance(word: String?): String? = when (word) {
        tr("Building", "A subir") -> tr("Your last 7 days are well above your usual, so you're building.", "Os teus últimos 7 dias estão bem acima do habitual: estás a subir.")
        tr("Easing off", "A abrandar") -> tr("Your last 7 days are well below your usual, so you're easing off.", "Os teus últimos 7 dias estão bem abaixo do habitual: estás a abrandar.")
        tr("Steady", "Estável") -> tr("Your last 7 days are close to your usual, a steady stretch.", "Os teus últimos 7 dias estão perto do habitual, uma fase estável.")
        else -> null
    }

    /** A week's or month's sentence from daily Loads against your usual band: how many days sat above it. */
    fun loadSpan(levels: List<DetailCopy.Level?>, range: Range): String? {
        val known = levels.filterNotNull()
        if (known.isEmpty()) return null
        val above = known.count { it == DetailCopy.Level.Above }
        val over = if (range == Range.Month) tr("in the last 30 days", "nos últimos 30 dias") else tr("in the last 7 days", "nos últimos 7 dias")
        return when (above) {
            0 -> tr("No day above your usual $over.", "Nenhum dia acima do habitual $over.")
            1 -> tr("One day above your usual $over.", "Um dia acima do habitual $over.")
            else -> tr("$above days above your usual $over.", "$above dias acima do habitual $over.")
        }
    }

    /** The strap's own weekly load and the range it calls productive, as a row's status. */
    fun strapRange(low: Int, high: Int): String = tr("The strap's productive range is $low to $high", "O intervalo produtivo da strap é de $low a $high")

    /* --------------------------------------------- VO2 max ---------------------------------------------- */

    /** The sentence: where the number comes from. */
    fun vo2(fromWorkout: Boolean): String =
        if (fromWorkout) tr("From your latest workout the strap could read it from.", "Do teu último treino em que a strap o conseguiu ler.")
        else tr("Estimated from your profile and resting heart rate, updated weekly.", "Estimado pelo teu perfil e frequência cardíaca em repouso, atualizado todas as semanas.")

    /* ------------------------------------------ Time to recover ----------------------------------------- */

    /** When the workout time to recover counts from ended: "today at 18:30", "yesterday at 07:10", "Tue 20 Oct at 18:30". */
    fun ended(date: LocalDate, clock: String, today: LocalDate, locale: Locale = Locale.getDefault()): String = when (date) {
        today -> tr("today at $clock", "hoje às $clock")
        today.minusDays(1) -> tr("yesterday at $clock", "ontem às $clock")
        else -> date.format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM", locale)).let { tr("$it at $clock", "$it às $clock") }
    }

    /** The sentence under the hours to go: which workout they count from. */
    fun recover(sport: String, ended: String): String =
        tr("Counted from your ${TrainingCopy.inSentence(sport)}, which ended $ended.", "Contado a partir do treino de ${TrainingCopy.inSentence(sport)}, que acabou $ended.")

    /* ----------------------------------------------- Energy --------------------------------------------- */

    /** The sentence under the day's kcal: what your body uses at rest, before any movement. */
    fun energy(atRest: Int): String =
        tr("Your body uses about ${"%,d".format(atRest)} kcal a day at rest, before any movement.", "O teu corpo gasta cerca de ${"%,d".format(atRest)} kcal por dia em repouso, antes de qualquer movimento.")

    /* ---------------------------------------------- Distance -------------------------------------------- */

    /** The distance row's line and the detail's label: where the number comes from. */
    fun distanceSource(fromSteps: Boolean): String = if (fromSteps) tr("km, from your steps", "km, pelos teus passos") else tr("km, from the strap", "km, da strap")

    /** The sentence under the kilometres: the steps they come from. */
    fun distance(steps: Int, today: Boolean): String =
        if (today) tr("Over ${"%,d".format(steps)} steps so far today.", "Em ${"%,d".format(steps)} passos hoje até agora.")
        else tr("Over ${"%,d".format(steps)} steps that day.", "Em ${"%,d".format(steps)} passos nesse dia.")
}
