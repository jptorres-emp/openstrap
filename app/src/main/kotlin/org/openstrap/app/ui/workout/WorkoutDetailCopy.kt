package org.openstrap.app.ui.workout

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.openstrap.app.data.Workouts
import org.openstrap.app.l10n.L10n
import org.openstrap.app.l10n.tr
import org.openstrap.app.ui.dashboard.Info
import org.openstrap.app.ui.dashboard.TodayCopy
import org.openstrap.app.ui.dashboard.sportNoun
import org.openstrap.app.ui.glow.km
import org.openstrap.app.ui.glow.workoutClock

/*
 * What the page of a past workout says (spec 6.3, "Workout detail"): the sentence first ("Hard run, mostly in zone
 * 4."), the facts, the heart rate drop, and training effect and EPOC explained behind the (i). Pure, so it's tested
 * without a workout.
 */

/**
 * A small fact on the workout's list: [name], [value] in dot-matrix digits, and the [status] under the name. [kind]
 * says which fact it is, for its icon and colour, whatever language the name is in.
 */
data class WorkoutFact(val name: String, val value: String, val status: String?, val kind: Kind? = null) {
    enum class Kind { Distance, Paused, Steps, Calories }
}

/** What a workout's other figures look like, for the strap's own record of a phone workout. */
data class EffectFigures(val aerobic: Float?, val anaerobic: Float?, val load: Int?, val recoveryMinutes: Int?)

/** The (i) on the Load card: the sheet's words, the bar of the 0 to 5 scale this workout is in, and its folded rows. */
data class EffectSheet(val info: Info, val here: Int?, val hereLine: String?, val rows: List<Pair<String, String>>)

object WorkoutDetailCopy {
    /** Shorter than this and a workout is "Short", whatever its Load says. */
    const val SHORT_S = 5 * 60L

    /** Less time in the zones than this says nothing about where the workout sat. */
    const val MIN_ZONE_S = 60

    /** A paused stretch shorter than this isn't worth a row. */
    const val MIN_PAUSED_S = 60L

    /** The effort word, read from [Workouts.effort]: half is your usual Load, full twice it or more. */
    enum class Effort(private val en: String, private val pt: String) {
        Easy("Easy", "leve"), Steady("Steady", "moderado"), Hard("Hard", "duro"), VeryHard("Very hard", "muito duro");

        /** The word: capitalised in English ("Hard run"), lower case in Portuguese ("treino duro"). */
        val word: String get() = tr(en, pt)
    }

    /**
     * How hard a workout was from its [load] against your [usual] workout Load ([Workouts.usualLoad]). The bands match
     * the Load card's: under three quarters of your usual is Easy, within a quarter of it Steady, above that Hard, and
     * from 1.75 times it Very hard. Without a usual the fixed scale behind [Workouts.effort] is used. Null without Load.
     */
    fun effort(load: Int?, usual: Float?): Effort? {
        if (load == null || load <= 0) return null
        val e = Workouts.effort(load, usual)
        return when {
            e < 0.5f * (1 - TodayCopy.LOAD_BAND) -> Effort.Easy
            e <= 0.5f * (1 + TodayCopy.LOAD_BAND) -> Effort.Steady
            e < 0.875f -> Effort.Hard
            else -> Effort.VeryHard
        }
    }

    /** The sport as it reads mid-sentence: "run", "strength training", "HIIT". */
    fun sport(sportType: Int): String = sportNoun(sportType) ?: Workouts.sportName(sportType).let { name ->
        val first = name.substringBefore(' ')
        if (first.length > 1 && first.all { it.isUpperCase() }) name else name.replaceFirstChar { it.lowercase() }
    }

    /** The zone (0 for zone 1) with the most of [zoneSeconds]; null with less than [MIN_ZONE_S] in all of them. */
    fun mostlyZone(zoneSeconds: List<Int>): Int? {
        if (zoneSeconds.sum() < MIN_ZONE_S) return null
        return zoneSeconds.indices.maxByOrNull { zoneSeconds[it] }
    }

    /**
     * The hero's sentence: "Hard run, mostly in zone 4." The effort word from [effort], "Short" under [SHORT_S] of
     * [activeSeconds], and without Load just the sport ("A run", "Strength training"). The zone part goes when there's
     * too little zone time.
     */
    fun sentence(sportType: Int, activeSeconds: Long, load: Int?, usual: Float?, zoneSeconds: List<Int>): String {
        val sport = sport(sportType)
        val word = if (activeSeconds < SHORT_S) tr("Short", "curto") else effort(load, usual)?.word
        if (L10n.portuguese) {
            // "Corrida: treino duro, sobretudo na zona 4." Portuguese leads with the sport's name, so no gender to agree.
            val name = Workouts.sportName(sportType)
            val head = if (word != null) "$name: treino $word" else name
            return head + (mostlyZone(zoneSeconds)?.let { ", sobretudo na zona ${it + 1}" } ?: "") + "."
        }
        val head = when {
            word != null -> "$word $sport"
            sportNoun(sportType) != null -> "A $sport"
            else -> sport.replaceFirstChar { it.uppercase() }
        }
        return head + (mostlyZone(zoneSeconds)?.let { ", mostly in zone ${it + 1}" } ?: "") + "."
    }

    /** The hero's day: "Today", "Yesterday", "Tue 21 Oct", with the year when it isn't [today]'s. */
    fun day(start: Long, today: LocalDate, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String {
        val d = Instant.ofEpochSecond(start).atZone(zone).toLocalDate()
        return when {
            d == today -> tr("Today", "Hoje")
            d == today.minusDays(1) -> tr("Yesterday", "Ontem")
            d.year == today.year -> d.format(DateTimeFormatter.ofPattern("EEE d MMM", locale))
            else -> d.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", locale))
        }
    }

    /**
     * The workout's small facts, each left out when there's nothing to say: distance (from GPS, or estimated from
     * steps), time paused past [MIN_PAUSED_S], steps and energy.
     */
    fun facts(meters: Float?, fromSteps: Boolean, pausedSeconds: Long, steps: Int?, calories: Int?, locale: Locale = Locale.getDefault()): List<WorkoutFact> =
        listOfNotNull(
            meters?.takeIf { it >= 10f }?.let { m ->
                val unit = if (m >= 1000) tr("Kilometres", "Quilómetros") else tr("Metres", "Metros")
                WorkoutFact(
                    tr("Distance", "Distância"), if (m >= 1000) km(m) else m.toInt().toString(),
                    if (fromSteps) tr("$unit, estimated from your steps", "$unit, estimados pelos passos") else unit, WorkoutFact.Kind.Distance,
                )
            },
            pausedSeconds.takeIf { it >= MIN_PAUSED_S }?.let {
                WorkoutFact(tr("Paused", "Em pausa"), workoutClock(it), tr("Not counted in the time", "Não conta no tempo"), WorkoutFact.Kind.Paused)
            },
            steps?.takeIf { it > 0 }?.let { WorkoutFact(tr("Steps", "Passos"), "%,d".format(locale, it), null, WorkoutFact.Kind.Steps) },
            calories?.takeIf { it > 0 }?.let { WorkoutFact(tr("Calories", "Calorias"), it.toString(), "kcal", WorkoutFact.Kind.Calories) },
        )

    /**
     * The Load card's heart rate drop line: "Heart rate drop 34 bpm in the first minute, quick for you." Only after a
     * real effort ([meaningful]); against your [usual] drop when there is one ([SummaryCopy.usualDrop]). Null otherwise.
     */
    fun dropLine(drop: Int?, meaningful: Boolean, usual: Int?): String? {
        if (drop == null || drop <= 0 || !meaningful) return null
        val head = tr("Heart rate drop $drop bpm in the first minute", "Descida da frequência cardíaca de $drop bpm no primeiro minuto")
        return when {
            usual == null -> "$head."
            drop >= usual + SummaryCopy.DROP_BAND -> tr("$head, quick for you.", "$head, rápida para ti.")
            drop <= usual - SummaryCopy.DROP_BAND -> tr("$head, slow for you.", "$head, lenta para ti.")
            else -> tr("$head, about usual for you.", "$head, perto do teu habitual.")
        }
    }

    /** The 0 to 5 training effect, low to high, in plain words. */
    val EFFECT_SCALE: List<String>
        get() = listOf(tr("Light", "Leve"), tr("Keeps", "Mantém"), tr("Builds", "Melhora"), tr("Builds a lot", "Melhora muito"), tr("Overreach", "Excessivo"))

    /** Which bar of [EFFECT_SCALE] a training effect is in. */
    fun effectBand(te: Float): Int = when {
        te < 2f -> 0
        te < 3f -> 1
        te < 4f -> 2
        te < 5f -> 3
        else -> 4
    }

    private fun one(x: Float) = String.format(Locale.ROOT, "%.1f", x)

    /**
     * The (i) on the Load card: Load (EPOC), training effect on its 0 to 5 scales, time to recover, the heart rate drop
     * and VO2 max, each explained plainly with this workout's value and left out when it has none. [fromPhone] says who
     * worked them out; [strap] is the strap's own record of a phone workout, when it kept one.
     */
    fun effectSheet(
        aerobic: Float?, anaerobic: Float?, load: Int?, usualLoad: Float?, recoveryMinutes: Int?,
        drop60: Int?, drop120: Int?, endBpm: Int?, meaningfulDrop: Boolean, vo2max: Float?,
        fromPhone: Boolean, strap: EffectFigures?,
    ): EffectSheet {
        val rows = listOfNotNull(
            load?.let { l ->
                val vs = usualLoad?.let { tr(" Your usual is ${Math.round(it)}.", " O teu habitual é ${Math.round(it)}.") } ?: ""
                tr("Load", "Carga") to tr(
                    "Load on a workout is EPOC: the extra oxygen your body uses afterwards to recover, in ml per kg of " +
                        "body weight. The harder and longer the workout, the higher it goes. It isn't the day's Load, which " +
                        "counts every minute on another scale. This workout: $l.$vs",
                    "A Carga de um treino é o EPOC: o oxigénio extra que o corpo usa depois para recuperar, em ml por kg de " +
                        "peso. Quanto mais duro e longo o treino, mais alto. Não é a Carga do dia, que conta cada minuto " +
                        "noutra escala. Este treino: $l.$vs",
                )
            },
            if (aerobic != null || anaerobic != null) {
                tr("Training effect", "Efeito do treino") to tr(
                    "Two scales from 0 to 5. Aerobic is what the workout did for your endurance, anaerobic " +
                        "for short, hard efforts. Under 2 trains little, 2 to 3 keeps what you have, 3 to 4 builds on it, 4 to 5 " +
                        "builds a lot, and 5 is more than your body can use right away. This workout: ",
                    "Duas escalas de 0 a 5. Aeróbico é o que o treino fez pela tua resistência, anaeróbico pelos " +
                        "esforços curtos e duros. Abaixo de 2 treina pouco, de 2 a 3 mantém o que tens, de 3 a 4 melhora, de 4 a 5 " +
                        "melhora muito, e 5 é mais do que o corpo consegue aproveitar logo. Este treino: ",
                ) + listOfNotNull(
                    aerobic?.let { tr("aerobic ${one(it)}", "aeróbico ${one(it)}") }, anaerobic?.let { tr("anaerobic ${one(it)}", "anaeróbico ${one(it)}") },
                ).joinToString(", ") + "."
            } else null,
            SummaryCopy.recoverSpan(recoveryMinutes)?.let {
                tr("Time to recover", "Tempo de recuperação") to tr(
                    "How long until your body is ready for another hard workout, from the training effect " +
                        "and how tired you've been lately. This workout: $it.",
                    "Quanto falta até o corpo estar pronto para outro treino duro, pelo efeito do treino " +
                        "e pelo cansaço dos últimos tempos. Este treino: $it.",
                )
            },
            drop60?.let { d ->
                val value = if (meaningfulDrop) {
                    tr("This workout: down $d in the first minute", "Este treino: desceu $d no primeiro minuto") +
                        (drop120?.let { tr(", $it in two", ", $it em dois") } ?: "") + "."
                } else {
                    tr(
                        "You finished at ${endBpm ?: d} bpm, close to resting, so there was little to come down from and it isn't rated.",
                        "Acabaste a ${endBpm ?: d} bpm, perto do repouso, por isso havia pouco para descer e não é avaliado.",
                    )
                }
                tr("Heart rate drop", "Descida da frequência cardíaca") to tr(
                    "How far your heart rate falls in the first minute after you stop. A bigger drop " +
                        "usually means a fitter heart, and under 12 bpm is on the low side. It only says something after a " +
                        "hard effort. $value",
                    "Quanto desce a frequência cardíaca no primeiro minuto depois de parares. Uma descida maior " +
                        "costuma querer dizer um coração mais em forma, e abaixo de 12 bpm é baixa. Só diz alguma coisa " +
                        "depois de um esforço duro. $value",
                )
            },
            vo2max?.let {
                tr("VO2 max", "VO2 máx") to tr(
                    "The most oxygen your body can use, in ml per kg per minute, estimated from this workout: ${Math.round(it)}.",
                    "O máximo de oxigénio que o corpo consegue usar, em ml por kg por minuto, estimado por este treino: ${Math.round(it)}.",
                )
            },
            tr("Where these come from", "De onde vêm estes valores") to (
                if (fromPhone) tr("Worked out by OpenStrap from your heart rate, on the strap's own scales.", "Calculados pela OpenStrap a partir da frequência cardíaca, nas escalas da própria strap.")
                else tr("From the strap, worked out during the workout.", "Da strap, calculados durante o treino.")
                ) + (strap?.let { s ->
                val figures = listOfNotNull(
                    s.aerobic?.let { tr("aerobic ${one(it)}", "aeróbico ${one(it)}") }, s.anaerobic?.let { tr("anaerobic ${one(it)}", "anaeróbico ${one(it)}") },
                    s.load?.let { tr("Load $it", "Carga $it") },
                    SummaryCopy.recoverSpan(s.recoveryMinutes)?.let { tr("time to recover $it", "tempo de recuperação $it") },
                )
                if (figures.isEmpty()) "" else tr(
                    " The strap recorded this workout too. Its own figures: ${figures.joinToString(", ")}.",
                    " A strap também gravou este treino. Os valores dela: ${figures.joinToString(", ")}.",
                )
            } ?: ""),
        )
        val info = Info(
            name = tr("Load and training effect", "Carga e efeito do treino"),
            meaning = tr("What this workout asked of your body, and what it trained.", "O que este treino pediu ao corpo, e o que treinou."),
            scale = if (aerobic != null) EFFECT_SCALE else emptyList(),
            how = "", moves = "", off = "",
        )
        return EffectSheet(
            info, aerobic?.let(::effectBand),
            aerobic?.let { tr("You are here. Aerobic ${one(it)} on the 0 to 5 scale.", "Estás aqui. Aeróbico ${one(it)} na escala de 0 a 5.") },
            rows,
        )
    }
}
