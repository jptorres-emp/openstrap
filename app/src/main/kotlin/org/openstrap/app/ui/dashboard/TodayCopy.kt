package org.openstrap.app.ui.dashboard

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.abs
import org.openstrap.app.data.Recovery
import org.openstrap.app.l10n.tr

/*
 * The words on Today's hero and its small facts (spec 4.2 and 7). Built only from numbers the app already has: where
 * a number is missing the sentence says what it waits for, and nothing is guessed.
 */
object TodayCopy {
    /** A length of time the way Today says it: "40 min", "7 h 40", "8 h". */
    fun duration(seconds: Long): String {
        val minutes = seconds.coerceAtLeast(0) / 60
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0L -> "$m min"
            m == 0L -> "$h h"
            else -> "$h h %02d".format(m)
        }
    }

    /** A value against your normal, from [org.openstrap.app.data.History.status]: null is no normal yet. */
    fun normalWord(direction: Int?): String = when {
        direction == null -> tr("Learning your normal", "A aprender o teu normal")
        direction > 0 -> tr("Above your normal", "Acima do teu normal")
        direction < 0 -> tr("Below your normal", "Abaixo do teu normal")
        else -> tr("Usual", "Habitual")
    }

    /** What a Recovery score means for the day, in a sentence. */
    fun recoveryMeaning(score: Int, alert: Boolean): String = when {
        alert -> tr("Some overnight signals are off your normal. An easy day suits.", "Alguns sinais da noite estão fora do teu normal. Convém um dia leve.")
        score >= 85 -> tr("Well recovered. A good day for a hard session.", "Bem recuperado. Um bom dia para um treino duro.")
        score >= 70 -> tr("Recovered enough for your usual training.", "Recuperado o suficiente para o teu treino habitual.")
        score >= Recovery.FAIR_FROM -> tr("Not fully recovered. Keep the effort moderate.", "Ainda não totalmente recuperado. Mantém o esforço moderado.")
        else -> tr("Still catching up. An easy day helps.", "Ainda a recuperar. Um dia leve ajuda.")
    }

    /** The status word beside the score. */
    fun recoveryWord(score: Int, alert: Boolean): String = when {
        alert -> tr("Take it easy", "Vai com calma")
        score >= 85 -> tr("Ready for more", "Pronto para mais")
        score >= 70 -> tr("Ready", "Pronto")
        score >= Recovery.FAIR_FROM -> tr("Moderate", "Moderado")
        else -> tr("Take it easy", "Vai com calma")
    }

    /**
     * Why there's no Recovery score yet. [Learning] counts the nights OpenStrap has toward its first score ([nights] of
     * [of]); [NoHeartData] is a night without the HRV or resting heart rate a score needs.
     */
    sealed interface NoScore {
        data class Learning(val nights: Int, val of: Int) : NoScore
        data object NoHeartData : NoScore
    }

    /** "OpenStrap is still learning your nights, 5 of 7." and the like. */
    fun noScoreLine(why: NoScore): String = when (why) {
        is NoScore.Learning -> tr(
            "OpenStrap is still learning your nights, ${why.nights} of ${why.of}.",
            "A OpenStrap ainda está a aprender as tuas noites, ${why.nights} de ${why.of}.",
        )
        NoScore.NoHeartData -> tr(
            "Not enough heart data last night for a Recovery score.",
            "Faltaram dados do coração na noite passada para a pontuação de Recuperação.",
        )
    }

    /**
     * The waking hero (spec 4.2, 4.3): how long you slept, then what Recovery says, or why there's no score yet and the
     * strap's own [estimate] when there is one. [firstNights] is before the strap has seen any sleep.
     */
    fun waking(asleepSeconds: Long?, score: Int?, alert: Boolean, noScore: NoScore?, firstNights: Boolean, isToday: Boolean): String {
        if (firstNights) return RECOVERY_FIRST_NIGHT
        if (asleepSeconds == null) return if (isToday) NO_SLEEP else tr("No sleep recorded that night.", "Nenhum sono registado nessa noite.")
        val slept = tr("Slept ${duration(asleepSeconds)}.", "Dormiste ${duration(asleepSeconds)}.")
        return when {
            score != null -> "$slept ${recoveryMeaning(score, alert)}"
            // The strap's estimate stands under the sentence with its label, so the sentence doesn't repeat it.
            noScore != null -> "$slept ${noScoreLine(noScore)}"
            else -> slept
        }
    }

    /** The Recovery heroes before the strap has seen a night (spec 4.3). */
    val RECOVERY_FIRST_NIGHT: String
        get() = tr("Recovery starts after your first night with the strap.", "A Recuperação começa depois da tua primeira noite com a strap.")

    /** Today's waking hero with no sleep recorded last night, and the Sleep tab's night (spec 4.3). */
    val NO_SLEEP: String get() = tr("No sleep recorded last night. Was the strap on?", "Nenhum sono registado na noite passada. Tinhas a strap posta?")

    /** What a Recovery card shows: the number (null for a dash), the word beside it and the line under it. */
    data class RecoveryShown(val value: Int?, val word: String?, val line: String, val estimate: Boolean = false)

    /**
     * Recovery as Today shows it everywhere (spec 4.3): OpenStrap's score, else the strap's [estimate] labelled as its
     * own, else why there's none, else [waiting].
     */
    fun recoveryShown(score: Int?, alert: Boolean, noScore: NoScore?, estimate: Int?, waiting: String): RecoveryShown = when {
        score != null -> RecoveryShown(score, recoveryWord(score, alert), recoveryMeaning(score, alert))
        estimate != null -> RecoveryShown(
            estimate, tr("Strap's estimate", "Estimativa da strap"),
            noScore?.let(::noScoreLine) ?: tr("OpenStrap's own score isn't ready yet.", "A pontuação da própria OpenStrap ainda não está pronta."),
            estimate = true,
        )
        noScore != null -> RecoveryShown(null, null, noScoreLine(noScore))
        else -> RecoveryShown(null, null, waiting)
    }

    /** Why OpenStrap has no score: still learning comes first, since a learning night has no baseline to score against. */
    fun noScore(r: Recovery.Readiness?): NoScore? = when {
        r == null || r.score != null -> null
        r.phase == Recovery.Phase.Withheld -> NoScore.Learning(r.baselineNights, Recovery.SCORED_NIGHTS)
        r.withheld -> NoScore.NoHeartData
        else -> null
    }

    /**
     * [recoveryShown] from the day's [r], so Today, the Recovery tab and All metrics show the same value: the strap's
     * [strapScore] stands in, as its estimate, while OpenStrap's own score is withheld.
     */
    fun recoveryShown(r: Recovery.Readiness?, strapScore: Int?, waiting: String): RecoveryShown {
        val estimate = r?.takeIf { it.score == null && it.showStrapEstimate }?.let { strapScore }
        return recoveryShown(r?.score, r?.strainAlert == true, noScore(r), estimate, waiting)
    }

    /**
     * The Charge hero at midday: what the day has [drained] since waking (all of it, as Drained says on the card and
     * the Recovery tab, not just the drop since waking) and what's left for the evening. [hour] is the clock's hour,
     * so the morning is called the morning.
     */
    fun charge(now: Int?, drained: Int?, anchored: Boolean, hour: Int): String {
        if (now == null || !anchored) return tr("Energy starts after your first night. Sleep is what fills it.", "A Energia começa depois da primeira noite. É o sono que a enche.")
        val first = when {
            drained == null -> null
            drained >= 1 && hour < 12 -> tr("The morning drained $drained.", "A manhã gastou $drained.")
            drained >= 1 -> tr("The day so far drained $drained.", "O dia gastou $drained até agora.")
            else -> tr("Nothing drained since waking.", "Nada gasto desde que acordaste.")
        }
        val left = when {
            now >= 50 -> tr("Plenty left for this evening.", "Sobra bastante para o fim do dia.")
            now >= 25 -> tr("Enough left for an easy evening.", "Chega para um fim do dia tranquilo.")
            else -> tr("Running low. An early night helps.", "Está a acabar. Deitar cedo ajuda.")
        }
        return listOfNotNull(first, left).joinToString(" ")
    }

    /** The workout window hero's title: what Recovery says about effort now. */
    fun windowTitle(score: Int?): String = when {
        score == null -> tr("Your usual workout time", "A tua hora habitual de treino")
        score >= 70 -> tr("Ready for effort", "Pronto para esforço")
        score >= Recovery.FAIR_FROM -> tr("Fine for a steady session", "Bom para um treino moderado")
        else -> tr("An easy session suits today", "Hoje convém um treino leve")
    }

    /**
     * "You usually run around now. Energy is 58, plenty left." The sport you usually do now ([sportType]) reads as a
     * verb where it can ([sportNoun] in English, [sportVerb] in Portuguese), else as working out.
     */
    fun window(sportType: Int?, charge: Int?): String {
        val usual = tr("You usually ${sportType?.let(::sportNoun) ?: "work out"} around now.", "Costumas ${sportVerb(sportType)} por esta hora.")
        val left = charge?.let {
            when {
                it >= 50 -> tr(" Energy is $it, plenty left.", " A Energia está em $it, sobra bastante.")
                it >= 25 -> tr(" Energy is $it, enough for something easy.", " A Energia está em $it, chega para algo leve.")
                else -> tr(" Energy is $it, running low.", " A Energia está em $it, a acabar.")
            }
        }
        return usual + left.orEmpty()
    }

    /** The Start button for the sport you usually do now: "Start run", "Start CrossFit", "Começar a correr". */
    fun startSport(sportType: Int): String {
        val name = org.openstrap.app.data.Workouts.sportName(sportType)
        return if (sportNoun(sportType) != null) tr("Start ${sportNoun(sportType)}", "Começar a ${sportVerb(sportType)}")
        else tr("Start ${TrainingCopy.inSentence(name)}", "Começar ${TrainingCopy.inSentence(name)}")
    }

    /** After a workout: how the heart is coming down, from the latest reading against resting. */
    fun after(nowHr: Int?, resting: Int?): String = when {
        nowHr == null -> tr("Heart rate since the end shows once the strap sends it.", "A frequência cardíaca desde o fim aparece quando a strap a enviar.")
        resting == null -> tr("Heart rate $nowHr now.", "Frequência cardíaca agora em $nowHr.")
        nowHr <= resting + SETTLED_BPM -> tr("Heart rate back near resting.", "Frequência cardíaca de volta perto do repouso.")
        else -> tr("Heart rate settling.", "A frequência cardíaca está a baixar.")
    }

    /** Within this many bpm of resting, the heart has settled. */
    const val SETTLED_BPM = 10

    /** How a Load compares with your usual. */
    enum class LoadStatus(private val en: String, private val pt: String) {
        Above("Above your usual", "Acima do habitual"), Usual("Usual", "Habitual"), Below("Below your usual", "Abaixo do habitual"),
        SoFar("So far today", "Hoje até agora");

        val text: String get() = tr(en, pt)
    }

    /**
     * [load] against your [usual] daily Load. Before the evening a low Load is only "so far today": compared with a
     * whole usual day it would always look low (spec 2.7, compare by pace). Null before there's a usual.
     */
    fun loadStatus(load: Float, usual: Float?, endOfDay: Boolean): LoadStatus? {
        if (usual == null || usual <= 0f) return null
        return when {
            load > usual * (1 + LOAD_BAND) -> LoadStatus.Above
            !endOfDay -> LoadStatus.SoFar
            load < usual * (1 - LOAD_BAND) -> LoadStatus.Below
            else -> LoadStatus.Usual
        }
    }

    /** A Load within this share of your usual counts as usual. */
    const val LOAD_BAND = 0.25f

    /** The evening hero: what kind of day it was, then what made it. [workouts] are the day's workouts' sport types. */
    fun evening(workouts: List<Int>, steps: Int, stepGoal: Int, load: LoadStatus?, locale: Locale = Locale.getDefault()): String {
        val head = when {
            workouts.isNotEmpty() && (steps >= stepGoal || load == LoadStatus.Above) -> tr("A full day.", "Um dia cheio.")
            workouts.isEmpty() && steps < stepGoal / 2 -> tr("A quiet day.", "Um dia calmo.")
            else -> tr("A steady day.", "Um dia equilibrado.")
        }
        val parts = listOfNotNull(
            when (workouts.size) {
                0 -> null
                1 -> tr("a ${sportNoun(workouts[0]) ?: "workout"}", sportWithA(workouts[0]))
                else -> tr("${workouts.size} workouts", "${workouts.size} treinos")
            },
            tr("%,d steps", "%,d passos").format(locale, steps),
            when (load) {
                LoadStatus.Above -> tr("a Load above your usual", "uma Carga acima do habitual")
                LoadStatus.Below -> tr("a Load below your usual", "uma Carga abaixo do habitual")
                LoadStatus.Usual -> tr("a usual Load", "uma Carga habitual")
                LoadStatus.SoFar, null -> null
            },
        )
        val list = if (parts.size == 1) parts[0] else parts.dropLast(1).joinToString(", ") + tr(" and ", " e ") + parts.last()
        return "$head ${list.replaceFirstChar { it.uppercase() }}."
    }

    /** "Bedtime in 1 h 10" until your usual [bedtime], read on a clock that starts at noon; null once it's passed. */
    fun bedtimeIn(now: LocalTime, bedtime: LocalTime?): String? {
        bedtime ?: return null
        val minutes = afterNoon(bedtime) - afterNoon(now)
        return if (minutes <= 0) null else tr("Bedtime in ${duration(minutes * 60L)}", "Deitar daqui a ${duration(minutes * 60L)}")
    }

    private fun afterNoon(t: LocalTime): Int = Math.floorMod(t.toSecondOfDay() / 60 - 720, 1440)

    /** Seconds still to recover from a workout that ended at [end] and needs [minutes], at [now]. */
    fun recoverLeft(end: Long, minutes: Int, now: Long): Long = (end + minutes * 60L - now).coerceAtLeast(0)

    /**
     * Time to recover as a value and a status: "14" and "Hours left, ready tomorrow", "2" and "Hours left, ready by
     * 20:30". Recovered has no number, only the word (null value).
     */
    fun recoverText(left: Long, now: Long, zone: ZoneId): Pair<String?, String> {
        if (left <= 0) return null to tr("Recovered", "Recuperado")
        val hours = (left + 3599) / 3600
        val ready = Instant.ofEpochSecond(now + left).atZone(zone)
        val today = Instant.ofEpochSecond(now).atZone(zone).toLocalDate()
        val unit = if (hours == 1L) tr("Hour left", "Hora restante") else tr("Hours left", "Horas restantes")
        val day = ready.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())
        val status = when (ready.toLocalDate()) {
            today -> tr("ready by ${ready.format(DateTimeFormatter.ofPattern("HH:mm"))}", "pronto às ${ready.format(DateTimeFormatter.ofPattern("HH:mm"))}")
            today.plusDays(1) -> tr("ready tomorrow", "pronto amanhã")
            // Saturday and Sunday are "no", the weekdays "na" in Portuguese.
            else -> tr("ready $day", if (ready.dayOfWeek.value >= 6) "pronto no $day" else "pronto na $day")
        }
        return "$hours" to "$unit, $status"
    }

    /** The line under Load today's number, which says it's a Load and how it compares. */
    fun loadLine(status: LoadStatus?): String = when (status) {
        null -> tr("Load, learning your usual", "Carga, a aprender o habitual")
        LoadStatus.Above -> tr("Load above your usual", "Carga acima do habitual")
        LoadStatus.Usual -> tr("Your usual Load", "A tua Carga habitual")
        LoadStatus.Below -> tr("Load below your usual", "Carga abaixo do habitual")
        LoadStatus.SoFar -> tr("Load so far today", "Carga de hoje até agora")
    }

    /** Under Your week before there's a week before it to compare with. */
    val WEEK_FIRST: String get() = tr("Week on week comparisons start next Monday.", "As comparações semana a semana começam na próxima segunda-feira.")

    /** The week Your week tells (spec 4.2): Monday to Sunday, the last full week before [date]. */
    fun lastWeek(date: LocalDate): ClosedRange<LocalDate> {
        val start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1)
        return start..start.plusDays(6)
    }

    /** "Steps up 12%" and the like, comparing two weeks; null without both. */
    fun stepsChange(now: Double?, before: Double?): String? {
        if (now == null || before == null || before <= 0) return null
        val pct = Math.round((now - before) / before * 100).toInt()
        return when {
            pct == 0 -> tr("Steps held steady", "Passos estáveis")
            pct > 0 -> tr("Steps up ${abs(pct)}%", "Mais ${abs(pct)}% de passos")
            else -> tr("Steps down ${abs(pct)}%", "Menos ${abs(pct)}% de passos")
        }
    }

    /** The Last night strip: "7 h 40, score 83". */
    fun lastNight(asleepSeconds: Long?, score: Int?): String? = asleepSeconds?.let {
        duration(it) + (score?.let { s -> tr(", score $s", ", pontuação $s") } ?: "")
    }

    /** A Small's word for Load against your usual: the [LoadStatus]'s, or that the usual is still being learned. */
    fun loadWord(status: LoadStatus?): String = status?.text ?: tr("Learning your usual", "A aprender o habitual")

    /**
     * Time to recover as a Small says it: the hours as the value and when you're ready as the word. "14" and "Ready
     * tomorrow", "2" and "Ready by 20:30". Recovered has no number, only the word.
     */
    fun recoverShort(left: Long, now: Long, zone: ZoneId): Pair<String?, String> {
        val (value, status) = recoverText(left, now, zone)
        if (value == null) return null to status
        return value to status.substringAfter(", ").replaceFirstChar { it.uppercase() }
    }

    /** How to read the heart rate line, with [band] when your resting range is drawn behind it. */
    fun heartLineNote(band: Boolean): String =
        if (band) tr("Hold to scrub, the number follows. The band is your resting range.", "Mantém o dedo para percorrer, o número acompanha. A faixa é o teu intervalo em repouso.")
        else tr("Hold to scrub, the number follows.", "Mantém o dedo para percorrer, o número acompanha.")

    /** The heart rate card's line under its number: live, or when the reading shown was taken. */
    fun heartAt(live: Boolean, at: String?): String = when {
        live -> tr("Live now", "Ao vivo")
        at != null -> tr("At $at", "Às $at")
        else -> tr("Waiting for the strap", "À espera da strap")
    }
}
