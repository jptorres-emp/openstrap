package org.openstrap.app.ui.dashboard

import org.openstrap.app.data.Reports
import org.openstrap.app.data.Workouts
import org.openstrap.app.l10n.tr
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

/*
 * The Reports screen's words (spec 7), in English and Portuguese side by side: the week and the month against the
 * stretch before them, how the days landed against their Load targets, and what habits do to the next morning. What
 * to do today (the target in your sports, tonight's bedtime) is the Training tab's ([TrainingCopy]). A row's status
 * keeps to a line on a phone where it can, as the tabs' lists do, and the method sits in the info sheet. A list row's
 * value is drawn in dots, so values keep to the dot font's glyphs: digits, ":", "-", "−", "+", ",", ".", "%", "k" and
 * space. Built only from the report's numbers: where one is missing the words say what it waits for.
 */
object ReportCopy {
    val TITLE: String get() = tr("Reports", "Relatórios")

    /** What a row of a comparison list is about, for its colour, icon and the detail it opens. */
    enum class Line { Hrv, RestingHr, Asleep, Restorative, Bedtime, Wake, Load, Usual, Workouts, Steps }

    /**
     * A list row: its [name], its [value] as dot text (null waits, and the [status] says for what), the [status] under
     * the name, and [off] when it's the one to notice: it moved the wrong way by enough to light (spec 2.5).
     */
    data class Row(val line: Line, val name: String, val value: String?, val status: String, val off: Boolean = false)

    /* ------------------------------------------------ Headline --------------------------------------------- */

    /** Beside the headline's average. */
    fun averageLabel(days: Int) = tr("average, last $days days", "média, últimos $days dias")

    /** Beside the headline, under the Load a day over the same days: effort next to Recovery. */
    val LOAD_A_DAY: String get() = tr("Load a day", "Carga por dia")

    /** Why there's no average, as the headline's state and the line under it. */
    fun noAverage(daysWithData: Int): Pair<String, String> =
        if (daysWithData == 0) tr("No data yet", "Ainda sem dados") to tr("Reports fill in as the strap syncs", "Os relatórios enchem-se à medida que a strap sincroniza")
        else tr("Learning", "A aprender") to tr("Recovery is scored after 7 nights", "A Recuperação tem pontuação ao fim de 7 noites")

    val LOADING_WORD: String get() = tr("Loading", "A carregar")
    val FAILED_WORD: String get() = tr("No report", "Sem relatório")
    val FAILED_WHY: String get() = tr("Try again in a moment", "Tenta daqui a pouco")

    /** While the report is worked out, and when it couldn't be. */
    val LOADING: String get() = tr("Working out your report.", "A calcular o teu relatório.")
    val FAILED: String get() = tr(
        "The report couldn't be worked out this time. It tries again after the next sync.",
        "Não deu para calcular o relatório desta vez. Volta a tentar depois da próxima sincronização.",
    )

    /** A chart's band, as the details label theirs ([DetailCopy.bandLabel]): "Your normal, 56 to 78". */
    fun bandLabel(band: ClosedFloatingPointRange<Float>): String =
        tr("Your normal, ${whole(band.start)} to ${whole(band.endInclusive)}", "O teu normal, ${whole(band.start)} a ${whole(band.endInclusive)}")

    /** Under an empty week or month chart. */
    fun noScores(days: Int) = tr("No Recovery in the last $days days yet.", "Ainda sem Recuperação nos últimos $days dias.")

    /** One time-aware sentence under the chart: the week's Recovery against the 3 weeks before, and what moved most. */
    fun weekSentence(now: Reports.Figures, before: Reports.Figures?): String = sentence(now, before, month = false)

    fun monthSentence(now: Reports.Figures, before: Reports.Figures?): String = sentence(now, before, month = true)

    private fun sentence(now: Reports.Figures, before: Reports.Figures?, month: Boolean): String {
        val rec = now.recovery ?: return tr(
            "Recovery shows here once it's scored, after 7 nights with the strap.",
            "A Recuperação aparece aqui quando tiver pontuação, depois de 7 noites com a strap.",
        )
        val r = whole(rec)
        val days = if (month) Reports.MONTH_DAYS else Reports.WEEK_DAYS
        val then = before?.recovery ?: return if (month) tr(
            "Recovery averaged $r over the last $days days. Comparisons start once there are two weeks before it.",
            "A Recuperação teve média de $r nos últimos $days dias. As comparações começam quando houver duas semanas antes.",
        ) else tr(
            "Recovery averaged $r over the last $days days. Comparisons start once there's a week before it.",
            "A Recuperação teve média de $r nos últimos $days dias. As comparações começam quando houver uma semana antes.",
        )
        val d = Math.round(rec) - Math.round(then)
        val n = abs(d)
        val head = when {
            d == 0 && month -> tr("Recovery averaged $r, level with the month before.", "A Recuperação teve média de $r, igual ao mês anterior.")
            d == 0 -> tr("Recovery averaged $r, level with the 3 weeks before.", "A Recuperação teve média de $r, igual às 3 semanas anteriores.")
            d > 0 && month -> tr("Recovery averaged $r, $n up on the month before.", "A Recuperação teve média de $r, mais $n do que no mês anterior.")
            d > 0 -> tr("Recovery averaged $r, $n up on the 3 weeks before.", "A Recuperação teve média de $r, mais $n do que nas 3 semanas anteriores.")
            month -> tr("Recovery averaged $r, $n down on the month before.", "A Recuperação teve média de $r, menos $n do que no mês anterior.")
            else -> tr("Recovery averaged $r, $n down on the 3 weeks before.", "A Recuperação teve média de $r, menos $n do que nas 3 semanas anteriores.")
        }
        val tails = listOfNotNull(
            delta(now.asleep, before.asleep)?.takeIf { abs(it) >= 15 }?.let {
                val t = TodayCopy.duration(abs(it).toLong() * 60)
                if (it > 0) tr("You slept $t more a night.", "Dormiste mais $t por noite.") else tr("You slept $t less a night.", "Dormiste menos $t por noite.")
            },
            share(now.hrv, before.hrv)?.takeIf { abs(it) >= 5 }?.let {
                if (it > 0) tr("HRV was ${abs(it)}% higher.", "A VFC foi ${abs(it)}% mais alta.") else tr("HRV was ${abs(it)}% lower.", "A VFC foi ${abs(it)}% mais baixa.")
            },
            share(now.load, before.load)?.takeIf { abs(it) >= 20 }?.let {
                if (it > 0) tr("Your Load was ${abs(it)}% higher.", "A tua Carga foi ${abs(it)}% mais alta.") else tr("Your Load was ${abs(it)}% lower.", "A tua Carga foi ${abs(it)}% mais baixa.")
            },
        )
        return (listOf(head) + tails.take(2)).joinToString(" ")
    }

    /** The month's mornings in Recovery's bands, under its chart: Ready or better, Moderate, Take it easy. */
    fun spreadFigures(bands: List<Int>): List<Pair<String, String>> = listOf(
        (bands.getOrElse(2) { 0 } + bands.getOrElse(3) { 0 }).toString() to tr("Ready or better", "Pronto ou mais"),
        bands.getOrElse(1) { 0 }.toString() to tr("Moderate", "Moderado"),
        bands.getOrElse(0) { 0 }.toString() to tr("Take it easy", "Vai com calma"),
    )

    /* --------------------------------------------- Load and target ----------------------------------------- */

    val TARGET_TITLE: String get() = tr("Load against your target", "Carga contra o alvo")

    /** The chart's band: each day's target, as a trend's band is your normal. */
    val TARGET_BAND: String get() = tr("Your target", "O teu alvo")

    /** How the finished days landed, for the figures over the target chart. */
    fun balanceFigures(b: Reports.Balance): List<Pair<String, String>> = listOf(
        whole(b.within) to tr("Within target", "Dentro do alvo"), whole(b.below) to tr("Below", "Abaixo"), whole(b.above) to tr("Above", "Acima"),
    )

    /** The figures while a day is held on the chart: its Load, its target, and the day with where it landed. */
    fun dayFigures(d: Reports.Day, t: Reports.Target?): List<Pair<String?, String>> = listOf(
        d.load?.let(::whole) to if (d.partial) tr("Load so far", "Carga até agora") else tr("Load", "Carga"),
        t?.let { tr("${it.range.first} to ${it.range.last}", "${it.range.first} a ${it.range.last}") } to tr("Target", "Alvo"),
        day(d.date) to when {
            d.partial -> tr("Under way", "A decorrer")
            t == null || d.load == null -> tr("No target", "Sem alvo")
            else -> when (Reports.versus(d.load, t.range)) {
                Reports.Versus.Below -> tr("Below target", "Abaixo do alvo")
                Reports.Versus.Within -> tr("Within target", "Dentro do alvo")
                Reports.Versus.Above -> tr("Above target", "Acima do alvo")
            }
        },
    )

    /** Under the chart: how the stretch before landed, in whole days. */
    fun balanceBefore(b: Reports.Balance?, month: Boolean): String? = b?.takeIf { it.days >= 0.5f }?.let {
        val within = days(Math.round(it.within))
        val below = Math.round(it.below)
        val above = Math.round(it.above)
        if (month) tr("The month before had $within within, $below below and $above above.", "O mês anterior teve $within dentro, $below abaixo e $above acima.")
        else tr("An average week before had $within within, $below below and $above above.", "Uma semana média antes teve $within dentro, $below abaixo e $above acima.")
    }

    /** Over its targets more than under them: the card lights from the top, as a value above your normal. */
    fun overTarget(b: Reports.Balance): Boolean = b.above >= 2f && b.above > b.below

    /* ---------------------------------------------- Comparisons -------------------------------------------- */

    val WEEK_TITLE: String get() = tr("This week", "Esta semana")
    val WEEK_NOTE: String get() = tr("Against the 3 before", "Contra as 3 anteriores")
    val MONTH_TITLE: String get() = tr("This month", "Este mês")
    val MONTH_NOTE: String get() = tr("Against the one before", "Contra o anterior")

    /** The week's figures, each against an average week of the 3 before it. Recovery is the headline's, so it's not here. */
    fun weekRows(now: Reports.Figures, before: Reports.Figures?): List<Row> = listOfNotNull(
        hrvRow(now, before),
        restingHrRow(now, before),
        asleepRow(now, before),
        shiftRow(Line.Bedtime, tr("Bedtime", "Hora de deitar"), now.bedtime, now.bedtimeShift, before?.bedtimeShift),
        shiftRow(Line.Wake, tr("Wake time", "Hora de acordar"), now.wake, now.wakeShift, before?.wakeShift),
        loadRow(now, before),
        workoutsRow(now, before),
        stepsRow(now, before),
    )

    /** The month's figures against the month before; Recovery and its spread are the headline's and the figures'. */
    fun monthRows(now: Reports.Figures, before: Reports.Figures?): List<Row> = listOfNotNull(
        hrvRow(now, before),
        restingHrRow(now, before),
        asleepRow(now, before),
        now.restorative?.let { r ->
            Row(
                Line.Restorative, tr("Deep and REM", "Profundo e REM"), clock(r),
                before?.restorative?.let { aNight() + ", " + vs(r, it, ::minutes) } ?: aNight(),
                off = before?.restorative?.let { r - it <= -15 } == true,
            )
        },
        shiftRow(Line.Bedtime, tr("Bedtime", "Hora de deitar"), now.bedtime, now.bedtimeShift, before?.bedtimeShift),
        shiftRow(Line.Wake, tr("Wake time", "Hora de acordar"), now.wake, now.wakeShift, before?.wakeShift),
        loadRow(now, before),
        now.usualLoad?.let { u ->
            val over = tr("Over 6 weeks", "Em 6 semanas")
            Row(Line.Usual, tr("Your usual Load", "Carga habitual"), whole(u), before?.usualLoad?.let { "$over, vs ${whole(it)}" } ?: over)
        },
        workoutsRow(now, before),
        stepsRow(now, before),
    )

    private fun aNight() = tr("A night", "Por noite")
    private fun aDay() = tr("A day", "Por dia")

    private fun hrvRow(now: Reports.Figures, before: Reports.Figures?): Row? = now.hrv?.let { h ->
        val then = before?.hrv
        val unit = tr("ms a night", "ms por noite")
        Row(Line.Hrv, tr("HRV", "VFC"), whole(h), if (then == null) unit else "$unit, ${vs(h, then, ::whole)}", off = then != null && (h - then) / then <= -0.05f)
    }

    private fun restingHrRow(now: Reports.Figures, before: Reports.Figures?): Row? = now.restingHr?.let { r ->
        val then = before?.restingHr
        val unit = tr("bpm a night", "bpm por noite")
        Row(Line.RestingHr, tr("Resting heart rate", "FC em repouso"), whole(r), if (then == null) unit else "$unit, ${vs(r, then, ::whole)}", off = then != null && r - then >= 2)
    }

    private fun asleepRow(now: Reports.Figures, before: Reports.Figures?): Row {
        val name = tr("Asleep", "Sono")
        val a = now.asleep ?: return Row(Line.Asleep, name, null, tr("After a night with the strap", "Depois de uma noite com a strap"))
        val then = before?.asleep
        return Row(Line.Asleep, name, clock(a), if (then == null) aNight() else aNight() + ", " + vs(a, then, ::minutes), off = then != null && a - then <= -20)
    }

    /** A sleep time and how much it varies night to night: the less, the steadier. */
    private fun shiftRow(line: Line, name: String, time: LocalTime?, shift: Float?, before: Float?): Row? = time?.let { t ->
        val varies = shift?.let { s -> tr("Varies by ${Math.round(s)} min", "Varia ${Math.round(s)} min") + (before?.let { ", vs ${Math.round(it)}" } ?: "") }
        Row(line, name, t.toString().take(5), varies ?: tr("Your usual time", "A tua hora habitual"), off = shift != null && before != null && shift - before >= 15)
    }

    private fun loadRow(now: Reports.Figures, before: Reports.Figures?): Row {
        val name = tr("Load", "Carga")
        val l = now.load ?: return Row(Line.Load, name, null, tr("After a day with the strap on", "Depois de um dia com a strap"))
        val then = before?.load
        return Row(Line.Load, name, whole(l), if (then == null) aDay() else aDay() + ", " + vs(l, then, ::whole))
    }

    /** How many workouts, their time in all, and how many the stretch before had: the count is the value. */
    private fun workoutsRow(now: Reports.Figures, before: Reports.Figures?): Row {
        val time = if (now.workoutSeconds > 0f) tr("${TodayCopy.duration(now.workoutSeconds.toLong())} in all", "${TodayCopy.duration(now.workoutSeconds.toLong())} no total") else tr("None", "Nenhum")
        return Row(Line.Workouts, tr("Workouts", "Treinos"), whole(now.workouts), before?.let { tr("$time, vs ${count(it.workouts)} workouts", "$time, vs ${count(it.workouts)} treinos") } ?: time)
    }

    private fun stepsRow(now: Reports.Figures, before: Reports.Figures?): Row? = now.steps?.let { s ->
        val then = before?.steps
        Row(Line.Steps, tr("Steps", "Passos"), steps(s), if (then == null) aDay() else aDay() + ", " + vs(s, then, ::steps), off = then != null && then > 0 && (s - then) / then <= -0.2f)
    }

    /* ----------------------------------------------- Weekdays ---------------------------------------------- */

    val WEEKDAYS_TITLE: String get() = tr("By day of the week", "Por dia da semana")
    /** Over all the reports' days. */
    val WEEKDAYS_NOTE: String get() = tr("Last 60 days", "Últimos 60 dias")

    /** A day of the week this far under the average is the one to notice. */
    const val LOW_WEEKDAY_POINTS = 5f

    /** The day of the week to notice: the lowest, when it's at least [LOW_WEEKDAY_POINTS] under the average of them all. */
    fun lowWeekday(weekdays: List<Reports.Weekday>): Reports.Weekday? {
        val scored = weekdays.filter { it.recovery != null }
        if (scored.size < 3) return null
        val low = scored.minBy { it.recovery!! }
        val mean = scored.map { it.recovery!! }.average().toFloat()
        return low.takeIf { mean - it.recovery!! >= LOW_WEEKDAY_POINTS }
    }

    /** Under the bars: the low day, or that the days are alike. */
    fun weekdayLine(weekdays: List<Reports.Weekday>): String? {
        val scored = weekdays.mapNotNull { it.recovery }
        if (scored.size < 3) return null
        val low = lowWeekday(weekdays) ?: return tr("Your days of the week are much alike.", "Os teus dias da semana são parecidos.")
        val under = Math.round(scored.average().toFloat() - low.recovery!!)
        return tr("${plural(low.day)} are your lowest, $under under your average.", "${pluralPt(low.day)} são o teu dia mais baixo, $under abaixo da tua média.")
    }

    /** "Mondays": sentences are written in their language, the day's name with them. */
    private fun plural(d: DayOfWeek) = d.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + "s"

    private fun pluralPt(d: DayOfWeek) = when (d) {
        DayOfWeek.MONDAY -> "As segundas"
        DayOfWeek.TUESDAY -> "As terças"
        DayOfWeek.WEDNESDAY -> "As quartas"
        DayOfWeek.THURSDAY -> "As quintas"
        DayOfWeek.FRIDAY -> "As sextas"
        DayOfWeek.SATURDAY -> "Os sábados"
        DayOfWeek.SUNDAY -> "Os domingos"
    }

    fun shortDay(d: DayOfWeek, locale: Locale = Locale.getDefault()): String = d.getDisplayName(TextStyle.SHORT, locale)

    /* ------------------------------------------------- Sports ---------------------------------------------- */

    val SPORTS_TITLE: String get() = tr("Workouts by sport", "Treinos por desporto")
    /** Beside the month's sports, whose bars split each one's time by heart rate zone. */
    val ZONES_NOTE: String get() = tr("Zones, easy to max", "Zonas, leve a máximo")

    /** A sport's name, in the language the app speaks now. */
    fun sportName(s: Reports.SportTotals): String = Workouts.sportName(s.sport)

    /** A sport's time in all and how many sessions the month before had: the count is the value. */
    fun sportStatus(s: Reports.SportTotals): String {
        val t = TodayCopy.duration(s.seconds)
        val time = tr("$t in all", "$t no total")
        return s.sessionsBefore?.let {
            val n = count(it)
            if (n == "1") tr("$time, vs 1 session", "$time, vs 1 sessão") else tr("$time, vs $n sessions", "$time, vs $n sessões")
        } ?: time
    }

    /* ------------------------------------------------- Habits ---------------------------------------------- */

    val HABITS_TITLE: String get() = tr("What moves your Recovery", "O que mexe na tua Recuperação")
    val HABITS_NOTE: String get() = tr("Last 60 days", "Últimos 60 dias")
    val HABITS_WAITING: String get() = tr("Habits show once a few weeks of mornings are scored.", "Os hábitos aparecem quando houver algumas semanas de manhãs com pontuação.")

    fun habitName(h: Reports.Habit): String = when (h) {
        Reports.Habit.Workout -> tr("A workout", "Um treino")
        is Reports.Habit.Sport -> Workouts.sportName(h.sport)
        Reports.Habit.LateWorkout -> tr("Late workout", "Treino tardio")
        Reports.Habit.HardDay -> tr("Hard day", "Dia duro")
        Reports.Habit.LateBedtime -> tr("Late bedtime", "Deitar tarde")
        Reports.Habit.ShortNight -> tr("Short night", "Noite curta")
        Reports.Habit.Weekend -> tr("Weekend night", "Fim de semana")
        Reports.Habit.ActiveDay -> tr("Active day", "Dia ativo")
    }

    private fun decisive(v: Reports.Verdict) = v == Reports.Verdict.Clear || v == Reports.Verdict.Likely

    /** The habit's effect as dot text: Recovery points when that's decisive, else HRV in percent when that is, else none. */
    fun habitValue(e: Reports.Effect): String? = when {
        decisive(e.recoveryVerdict) && e.recovery != null -> signed(Math.round(e.recovery))
        decisive(e.hrvVerdict) && e.hrv != null -> signed(Math.round(e.hrv)) + "%"
        else -> null
    }

    /** How sure, over how many mornings, and what it keeps company with: the value says how much. */
    fun habitStatus(e: Reports.Effect): String {
        val m = e.mornings
        if (e.recoveryVerdict == Reports.Verdict.TooFew) return when {
            m < Reports.MIN_HABIT_DAYS -> tr("Too few mornings yet, $m of ${Reports.MIN_HABIT_DAYS}", "Ainda poucas manhãs, $m de ${Reports.MIN_HABIT_DAYS}")
            else -> tr("Almost every morning, nothing to compare", "Quase todas as manhãs, nada a comparar")
        }
        val hrv = tr("HRV", "VFC")
        val head = when {
            decisive(e.recoveryVerdict) -> {
                val base = if (e.recoveryVerdict == Reports.Verdict.Clear) tr("Clear over $m mornings", "Claro em $m manhãs") else tr("Likely over $m mornings", "Provável em $m manhãs")
                base + (if (decisive(e.hrvVerdict) && e.hrv != null) ", $hrv ${signed(Math.round(e.hrv))}%" else "")
            }
            decisive(e.hrvVerdict) -> if (e.hrvVerdict == Reports.Verdict.Clear) tr("HRV, clear over $m mornings", "VFC, claro em $m manhãs") else tr("HRV, likely over $m mornings", "VFC, provável em $m manhãs")
            else -> tr("No clear effect, $m mornings", "Sem efeito claro, $m manhãs")
        }
        val with = e.with.firstOrNull()?.let { tr(". Often with ", ". Muitas vezes com ") + TrainingCopy.inSentence(habitName(it)) } ?: ""
        return head + with
    }

    /** Lit when the effect is clear and costs Recovery or HRV. */
    fun habitOff(e: Reports.Effect): Boolean =
        (e.recoveryVerdict == Reports.Verdict.Clear && (e.recovery ?: 0f) < 0f) || (e.hrvVerdict == Reports.Verdict.Clear && (e.hrv ?: 0f) < 0f)

    /** The habits listed one a row: those whose effect shows, clear or likely, the clearest first. */
    fun shownHabits(habits: List<Reports.Effect>): List<Reports.Effect> = habits.filter { habitValue(it) != null }

    /**
     * The rest, a row each for what they have in common, so the ones that matter aren't lost among them: no clear
     * effect, too few mornings after them yet, or after almost every morning. Each with the habits' names.
     */
    fun quietHabits(habits: List<Reports.Effect>): List<Pair<String, String>> {
        val rest = habits.filter { habitValue(it) == null }
        val few = rest.filter { it.recoveryVerdict == Reports.Verdict.TooFew && it.mornings < Reports.MIN_HABIT_DAYS }
        val always = rest.filter { it.recoveryVerdict == Reports.Verdict.TooFew && it.mornings >= Reports.MIN_HABIT_DAYS }
        val unclear = rest - few.toSet() - always.toSet()
        return listOf(
            tr("No clear effect", "Sem efeito claro") to unclear,
            tr("Too few mornings yet", "Ainda poucas manhãs") to few,
            tr("After almost every morning", "Em quase todas as manhãs") to always,
        ).filter { it.second.isNotEmpty() }.map { (title, list) -> title to names(list.map { habitName(it.habit) }) }
    }

    /** "Weekend night, short night and hard day". */
    private fun names(names: List<String>): String {
        val all = names.mapIndexed { i, n -> if (i == 0) n else TrainingCopy.inSentence(n) }
        return if (all.size == 1) all[0] else all.dropLast(1).joinToString(", ") + tr(" and ", " e ") + all.last()
    }

    /* ---------------------------------------------- Accessibility ------------------------------------------ */

    /** What a screen reader says for a chart of Recovery by day. */
    fun describeRecovery(days: List<Reports.Day>, locale: Locale = Locale.getDefault()): String =
        tr("Recovery by day. ", "Recuperação por dia. ") +
            days.joinToString(", ") { d -> "${shortDay(d.date.dayOfWeek, locale)} ${d.recovery ?: tr("no score", "sem pontuação")}" } + "."

    /** What a screen reader says for the days' Load against their targets. */
    fun describeTargets(days: List<Reports.Day>, targets: List<Reports.Target?>, locale: Locale = Locale.getDefault()): String =
        tr("Load against your target by day. ", "Carga contra o alvo por dia. ") + days.mapIndexed { i, d ->
            val t = targets.getOrNull(i)?.range
            val load = d.load?.let { Math.round(it).toString() } ?: tr("no reading", "sem leitura")
            "${shortDay(d.date.dayOfWeek, locale)} $load" + (t?.let { tr(", target ${it.first} to ${it.last}", ", alvo ${it.first} a ${it.last}") } ?: "")
        }.joinToString(". ") + "."

    /** What a screen reader says for the days of the week. */
    fun describeWeekdays(weekdays: List<Reports.Weekday>, locale: Locale = Locale.getDefault()): String =
        tr("Average Recovery by day of the week. ", "Recuperação média por dia da semana. ") +
            weekdays.joinToString(", ") { w -> "${shortDay(w.day, locale)} ${w.recovery?.let(::whole) ?: tr("no score", "sem pontuação")}" } + "."

    /* -------------------------------------------------- Info ----------------------------------------------- */

    /** The (i) sheet's meaning line and its folds, in place of how, moves and off. */
    val INFO_MEANING: String
        get() = tr(
            "Your last 7 or 30 days against the stretch before them: how you recovered, how your Load landed against its targets, and what your habits do to the next morning.",
            "Os teus últimos 7 ou 30 dias contra o período antes deles: como recuperaste, onde a Carga ficou face aos alvos e o que os teus hábitos fazem à manhã seguinte.",
        )

    val INFO_ROWS: List<Pair<String, String>>
        get() = listOf(
            tr("The week and the month", "A semana e o mês") to tr(
                "Recovery, HRV, resting heart rate, sleep and Load are the app's own, each morning worked out as it was that day. " +
                    "The last 7 days are compared with an average week of the 3 before them, the last 30 with the 30 before. Your normal " +
                    "on the chart is the last 30 days. Bedtime and wake time say how much they vary from one night to the next: the " +
                    "less, the steadier your sleep.",
                "A Recuperação, a VFC, a frequência cardíaca em repouso, o sono e a Carga são os da própria app, cada manhã calculada " +
                    "como foi nesse dia. Os últimos 7 dias comparam-se com uma semana média das 3 anteriores, os últimos 30 com os 30 " +
                    "anteriores. O teu normal no gráfico são os últimos 30 dias. A hora de deitar e a de acordar dizem quanto variam de " +
                    "uma noite para a outra: quanto menos, mais regular o teu sono.",
            ),
            tr("Load against your target", "Carga contra o alvo") to tr(
                "Each morning's Recovery sets a Load target for the day from your own training days over the last 6 weeks, as the " +
                    "Training tab shows it. On the chart each day's band is that target and the bar is the Load you did: in full " +
                    "within it, lit when over it, faded when under, often just a rest day.",
                "A Recuperação de cada manhã define um alvo de Carga para o dia a partir dos teus dias de treino das últimas 6 " +
                    "semanas, como o separador Treino mostra. No gráfico, a faixa de cada dia é esse alvo e a barra é a Carga que " +
                    "fizeste: cheia dentro dele, mais clara quando passou, esbatida quando ficou abaixo, muitas vezes só um dia de descanso.",
            ),
            tr("Habits", "Hábitos") to tr(
                "The app keeps no journal, so habits are found in your data, each on the day before the morning: a workout and " +
                    "the sports you do most, a late workout (from 20:00), a hard day (a Load 1.5 times your usual), an active day " +
                    "(your top quarter of steps). And on the night itself: a late bedtime (45 min after your usual), a short night " +
                    "(under 7 h asleep), a weekend night. They're read together, so a habit that only keeps company with another " +
                    "doesn't take its effect. The number is Recovery points, or HRV in percent when only HRV moved.",
                "A app não tem diário, por isso os hábitos são encontrados nos teus dados, cada um no dia antes da manhã: um treino " +
                    "e os desportos que mais fazes, um treino tardio (a partir das 20:00), um dia duro (Carga 1,5 vezes a habitual), " +
                    "um dia ativo (o teu quarto de dias com mais passos). E na própria noite: deitar tarde (45 min depois do " +
                    "habitual), uma noite curta (menos de 7 h a dormir), uma noite de fim de semana. São lidos em conjunto, por isso " +
                    "um hábito que só anda com outro não fica com o efeito dele. O número são pontos de Recuperação, ou a VFC em " +
                    "percentagem quando só a VFC mexeu.",
            ),
            tr("Why it might be off", "Porque pode falhar") to tr(
                "Clear means a difference of at least 3 points (or 3%) that chance would give less than 1 time in 20, likely less " +
                    "than 1 in 10. With several habits tested one may still look clear by chance, and a pattern isn't proof of cause. " +
                    "Sleep counts toward Recovery, so short nights and late bedtimes move it partly by definition: HRV is the cleaner " +
                    "sign there. A day with the strap off reads low on Load and steps.",
                "Claro quer dizer uma diferença de pelo menos 3 pontos (ou 3%) que o acaso daria menos de 1 vez em 20, provável " +
                    "menos de 1 em 10. Com vários hábitos testados, um pode parecer claro por acaso, e um padrão não prova a causa. O " +
                    "sono conta para a Recuperação, por isso noites curtas e deitar tarde mexem nela em parte por definição: aí, a " +
                    "VFC é o sinal mais limpo. Um dia sem a strap conta a menos na Carga e nos passos.",
            ),
        )

    /* ------------------------------------------------- Formats --------------------------------------------- */

    /** A whole number. */
    fun whole(v: Float): String = Math.round(v).toString()

    /** Minutes as a clock-style duration for dots: "7:43". */
    fun clock(minutes: Float): String = Math.round(minutes).let { "%d:%02d".format(it / 60, it % 60) }

    /** Steps as the Steps rows show them. */
    fun steps(v: Float): String = "%,d".format(Math.round(v))

    fun days(n: Int) = if (n == 1) tr("1 day", "1 dia") else tr("$n days", "$n dias")

    private fun minutes(v: Float): String = TodayCopy.duration(Math.round(v) * 60L)

    /** A count that may be an average: whole when it is one, else to one decimal. */
    private fun count(v: Float): String = if (abs(v - Math.round(v)) < 0.05f) whole(v) else "%.1f".format(v)

    /** A signed whole number with a true minus (spec 2.3): "+4", "−3", "0". */
    fun signed(n: Int): String = when {
        n > 0 -> "+$n"
        n < 0 -> "−${-n}"
        else -> "0"
    }

    /**
     * [now] against [before] in words: "+4 vs 70", "−12 min vs 7 h 30", or "same as before" when the change wouldn't
     * show (spec 2.3, never "+0"). Both are rounded as shown first, so the change adds up with the values on screen.
     * [shown] formats a whole value and the size of the change alike.
     */
    private fun vs(now: Float, before: Float, shown: (Float) -> String): String {
        val a = Math.round(now)
        val b = Math.round(before)
        if (a == b) return tr("same as before", "igual a antes")
        return "${if (a < b) "−" else "+"}${shown(abs(a - b).toFloat())} vs ${shown(b.toFloat())}"
    }

    private fun delta(now: Float?, before: Float?): Int? = if (now != null && before != null) Math.round(now - before) else null

    /** [now] against [before] in whole percent. */
    private fun share(now: Float?, before: Float?): Int? =
        if (now != null && before != null && before > 0f) Math.round((now - before) / before * 100) else null

    /** A date as the details write it under a scrubbed value. */
    fun day(d: LocalDate): String = d.format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM"))
}
