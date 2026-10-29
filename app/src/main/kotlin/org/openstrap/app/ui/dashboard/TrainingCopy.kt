package org.openstrap.app.ui.dashboard

import org.openstrap.app.data.Reports
import org.openstrap.app.data.Training
import org.openstrap.app.data.Workouts
import org.openstrap.app.data.settings.Units
import org.openstrap.app.l10n.tr
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/*
 * The Training tab's words (spec 7), in English and Portuguese side by side: what the day calls for in your own
 * sports, the last week's Load against your usual, and the calories you burn week by week. Built only from the
 * report's numbers; where one is missing the words say what it waits for. Each card's method sits in its info sheet,
 * which says what the card is for, how it helps and when to look at it.
 */
object TrainingCopy {
    /* ------------------------------------------------ The day ---------------------------------------------- */

    fun dayTitle(isToday: Boolean): String = if (isToday) tr("Today's training", "Treino de hoje") else tr("That day's training", "Treino desse dia")

    /** The kind of day the morning's Recovery made it, beside the title. */
    fun kindWord(t: Reports.Target?): String? = when {
        t == null -> null
        !t.scored -> tr("A usual day", "Dia normal")
        else -> when (t.kind) {
            Reports.DayKind.Recover -> tr("A day to recover", "Dia para recuperar")
            Reports.DayKind.Moderate -> tr("A moderate day", "Dia moderado")
            Reports.DayKind.Usual -> tr("A usual day", "Dia normal")
            Reports.DayKind.Push -> tr("A day to push", "Dia para puxar")
        }
    }

    val LOADING: String get() = tr("Working out the day's training.", "A calcular o treino do dia.")
    val FAILED: String get() = tr("The day's training couldn't be worked out. It tries again after the next sync.", "Não deu para calcular o treino do dia. Volta a tentar depois da próxima sincronização.")
    val NO_TARGET: String get() = tr("Your Load target shows after a week with the strap on.", "O teu alvo de Carga aparece depois de uma semana com a strap.")

    /**
     * The hero's sentence: what the day calls for, in your own sports when you have some ([sports], the most done
     * first), else in words. A day that's over says where it landed instead.
     */
    fun plan(t: Reports.Target, sports: List<Training.SportLoad>, load: Float?, finished: Boolean): String {
        if (finished) {
            val shown = load?.let { Math.round(it) } ?: return tr("No Load that day.", "Sem Carga nesse dia.")
            return when (Reports.versus(load, t.range)) {
                Reports.Versus.Below -> tr("Landed at $shown, under its target.", "Ficou em $shown, abaixo do alvo.")
                Reports.Versus.Within -> tr("Landed at $shown, within its target.", "Ficou em $shown, dentro do alvo.")
                Reports.Versus.Above -> tr("Landed at $shown, over its target.", "Ficou em $shown, acima do alvo.")
            }
        }
        val first = sports.firstOrNull() ?: return when (t.kind.takeIf { t.scored } ?: Reports.DayKind.Usual) {
            Reports.DayKind.Recover -> tr("Keep it light today: a walk or mobility.", "Hoje, mantém leve: uma caminhada ou mobilidade.")
            Reports.DayKind.Moderate -> tr("Train, but lighter than usual.", "Treina, mas mais leve do que o habitual.")
            Reports.DayKind.Usual -> tr("Your usual training fits today.", "Hoje cabe o teu treino habitual.")
            Reports.DayKind.Push -> tr("A day to go harder than usual.", "Dá para ir mais forte do que o habitual.")
        }
        val a = sportInSentence(first.sport)
        val la = Math.round(first.dayLoad)
        return when (Training.fit(t.range, first.dayLoad)) {
            Training.Fit.Within -> {
                val second = sports.drop(1).firstOrNull { Training.fit(t.range, it.dayLoad) == Training.Fit.Within }
                if (second == null) tr("A usual day of $a fits (≈$la).", "Cabe um dia habitual de $a (≈$la).")
                else {
                    val b = sportInSentence(second.sport)
                    val lb = Math.round(second.dayLoad)
                    tr("A usual day of $a fits (≈$la), or one of $b (≈$lb).", "Cabe um dia habitual de $a (≈$la), ou de $b (≈$lb).")
                }
            }
            Training.Fit.Over -> tr("A usual day of $a (≈$la) goes over it: keep it lighter.", "Um dia habitual de $a (≈$la) passa do alvo: faz mais leve.")
            Training.Fit.Under -> tr("A usual day of $a (≈$la) stays under it: room to go harder.", "Um dia habitual de $a (≈$la) fica abaixo: dá para puxar mais.")
        }
    }

    /** A sport's name mid-sentence: "soccer", but "CrossFit" and "HIIT" as they're written. */
    fun sportInSentence(sport: Int): String = inSentence(Workouts.sportName(sport))

    /** A name mid-sentence: lower case first, unless it's written with capitals inside ("CrossFit", "HIIT"). */
    fun inSentence(name: String): String = if (name.drop(1).any { it.isUpperCase() }) name else name.replaceFirstChar { it.lowercase() }

    /** The target as dot text: the dot font has the hyphen, not the en dash. */
    fun targetValue(t: Reports.Target) = "${t.range.first}-${t.range.last}"

    /** Under the target: what it is, and the day's Load so far or where it landed. */
    fun targetLabel(load: Float?, finished: Boolean): String {
        val shown = load?.let { Math.round(it) } ?: return tr("Load target", "Carga alvo")
        return if (finished) tr("Load target, landed at $shown", "Carga alvo, ficou em $shown") else tr("Load target, $shown so far", "Carga alvo, $shown até agora")
    }

    /** The day's Recovery as a figure under the target, the strap's own score while Recovery learns; null without either. */
    fun recoveryFigure(day: Reports.Day?): Pair<String, String>? {
        day?.recovery?.let { return it.toString() to tr("Recovery", "Recuperação") }
        return day?.strapRecovery?.let { it.toString() to tr("Strap's estimate", "Estimativa da strap") }
    }

    /** Tonight's bedtime as a figure beside it, or null without a wake time to count back from. */
    fun bedtimeFigure(p: Reports.SleepPlan): Pair<String, String>? = p.bedtime?.let { it.toString().take(5) to tr("Bed tonight", "Deitar hoje") }

    /** Under the figures: how long tonight's sleep is for and what moved it off your need, if anything did. */
    fun planLine(p: Reports.SleepPlan): String? {
        p.wake ?: return null
        val asleep = TodayCopy.duration(p.asleepMinutes * 60L)
        val more = p.shortfallMinutes + p.loadMinutes
        // The nap's share of the night as the plan took it off: never more than the floor under your need allows.
        val nap = (p.needMinutes + more - p.asleepMinutes).coerceAtLeast(0)
        val why = listOfNotNull(
            when {
                p.shortfallMinutes > 0 && p.loadMinutes > 0 -> tr("$more min more for short nights and a hard day", "mais $more min por noites curtas e um dia duro")
                p.shortfallMinutes > 0 -> tr("$more min more for short nights", "mais $more min por noites curtas")
                p.loadMinutes > 0 -> tr("$more min more after a hard day", "mais $more min depois de um dia duro")
                else -> null
            },
            if (nap > 0) tr("${TodayCopy.duration(nap * 60L)} less for a nap", "menos ${TodayCopy.duration(nap * 60L)} pela sesta") else null,
        )
        return (listOf(tr("For $asleep asleep, up at ${p.wake.toString().take(5)}", "Para dormir $asleep e acordar às ${p.wake.toString().take(5)}")) + why).joinToString(", ")
    }

    /* ------------------------------------------ Load and injury risk --------------------------------------- */

    val RISK_TITLE: String get() = tr("Load and injury risk", "Carga e risco de lesão")

    /** The zone's word, as Activity's Load card says the week's balance. */
    fun zoneWord(z: Training.Zone): String = when (z) {
        Training.Zone.Easing -> tr("Easing off", "A abrandar")
        Training.Zone.Steady -> tr("Steady", "Estável")
        Training.Zone.Building -> tr("Building", "A subir")
        Training.Zone.Spike -> tr("Spike", "Pico")
    }

    /** The ratio as dot text, in percent of your usual: "112%". */
    fun ratioValue(ratio: Float): String = "${Math.round(ratio * 100)}%"

    val RATIO_UNIT: String get() = tr("of your usual", "do habitual")

    /** Under the ratio: what the zone means for the days ahead. */
    fun riskStatus(z: Training.Zone): String = when (z) {
        Training.Zone.Easing -> tr("Under your usual: fine for a lighter week, but fitness fades if it lasts.", "Abaixo do habitual: bom numa semana leve, mas perdes forma se durar.")
        Training.Zone.Steady -> tr("The safe zone: build from here little by little.", "A zona segura: daqui, aumenta aos poucos.")
        Training.Zone.Building -> tr("Rising fast: keep the next days steady.", "A subir depressa: mantém os próximos dias estáveis.")
        Training.Zone.Spike -> tr("A spike, the zone tied most to injuries: ease off.", "Um pico, a zona mais ligada a lesões: abranda.")
    }

    val RISK_WAITING: String get() = tr("After a week with the strap on", "Depois de uma semana com a strap")

    /** The chart's band: the steady zone. */
    val RISK_BAND: String get() = tr("Steady, 75 to 125%", "Estável, 75 a 125%")

    /** What a screen reader says for the chart. */
    fun describeRatios(days: List<Reports.Day>): String = tr("Last week's Load as a share of your usual, by day. ", "Carga da última semana em percentagem da habitual, por dia. ") +
        days.joinToString(", ") { d -> "${d.date.dayOfMonth} ${Training.ratio(d)?.let(::ratioValue) ?: tr("no reading", "sem leitura")}" } + "."

    /* ------------------------------------------------ Calories --------------------------------------------- */

    /** Calories, as Activity's row and detail call them: Energy is Charge's name. */
    val CALORIES_TITLE: String get() = tr("Calories burned", "Calorias gastas")
    val CALORIES_NOTE: String get() = tr("For fat loss", "Para perder gordura")
    val CALORIES_UNIT: String get() = tr("kcal a day", "kcal por dia")

    /** The week's kcal a day as dot text, with the thousands mark the dot font has. */
    fun kcal(v: Float): String = "%,d".format(Math.round(v))

    /** Under the figure: the last 7 days' average against the 3 weeks before them, as the bars show them. */
    fun caloriesStatus(now: Float, before: Float?): String {
        val b = before ?: return tr("Last 7 days", "Últimos 7 dias")
        val d = Math.round(now) - Math.round(b)
        return when {
            d == 0 -> tr("Last 7 days, same as before", "Últimos 7 dias, igual a antes")
            d > 0 -> tr("Last 7 days, +$d vs ${kcal(b)} before", "Últimos 7 dias, +$d vs ${kcal(b)} antes")
            else -> tr("Last 7 days, −${-d} vs ${kcal(b)} before", "Últimos 7 dias, −${-d} vs ${kcal(b)} antes")
        }
    }

    /** A week's bar, by the day it ends: the last ends on the day shown. */
    fun weekLabel(end: LocalDate): String = end.format(DateTimeFormatter.ofPattern("d MMM"))

    val CALORIES_LINE: String get() = tr(
        "Food isn't in this count. To lose fat, burn more than you eat: training and steps add to this number.",
        "A comida não entra nesta conta. Para perder gordura, gasta mais do que comes: treino e passos somam a este número.",
    )

    /** Without age, weight and height there's no estimate; with them, it waits for a whole day with the strap on. */
    val CALORIES_NO_BODY: String get() = tr("Needs your age, weight and height", "Precisa da tua idade, peso e altura")
    val CALORIES_WAITING: String get() = tr("After a whole day with the strap on", "Depois de um dia inteiro com a strap")
    val ADD_PROFILE: String get() = tr("Add age, weight and height", "Adicionar idade, peso e altura")

    /** What a screen reader says for the weeks. */
    fun describeCalories(weeks: List<Training.EnergyWeek>): String = tr("Calories burned a day, week by week. ", "Calorias gastas por dia, semana a semana. ") +
        weeks.joinToString(", ") { w -> "${w.end.dayOfMonth}: ${w.kcalADay?.let(::kcal) ?: tr("no estimate", "sem estimativa")}" } + "."

    /* ------------------------------------------------- Weight ---------------------------------------------- */

    val WEIGHT_TITLE: String get() = tr("Weight", "Peso")
    val WEIGHT_NOTE: String get() = tr("Week by week", "Semana a semana")
    val WEIGHT_LOG: String get() = tr("Log your weight", "Registar o peso")
    val WEIGHT_FIRST: String get() = tr("Log it once or twice a week", "Regista-o 1 a 2 vezes por semana")

    /** A weight as dot text, to one decimal: "78.4", or "78,4" in Portuguese. */
    fun weightValue(kg: Float, imperial: Boolean): String = "%.1f".format(if (imperial) Units.kgToLb(kg) else kg)

    fun weightUnit(imperial: Boolean): String = if (imperial) "lb" else "kg"

    /** Under the weight: how the weeks have moved it, or what the trend waits for. */
    fun weightStatus(trend: Training.WeightTrend?, imperial: Boolean): String {
        val t = trend ?: return tr("The trend shows after two weeks", "A tendência aparece ao fim de duas semanas")
        val since = t.since.format(DateTimeFormatter.ofPattern("d MMM"))
        val kg = if (imperial) Units.kgToLb(t.kg) else t.kg
        val shown = "%.1f".format(kotlin.math.abs(kg))
        val unit = weightUnit(imperial)
        return when {
            kotlin.math.abs(kg) < 0.05f -> tr("Steady since $since", "Estável desde $since")
            kg < 0 -> tr("−$shown $unit since $since", "−$shown $unit desde $since")
            else -> tr("+$shown $unit since $since", "+$shown $unit desde $since")
        }
    }

    val WEIGHT_LINE: String get() = tr(
        "Weigh yourself in the morning, after the toilet and before eating, so the weeks compare.",
        "Pesa-te de manhã, depois da casa de banho e antes de comer, para as semanas serem comparáveis.",
    )

    /** What a screen reader says for the weeks. */
    fun describeWeight(weeks: List<Training.WeightWeek>, imperial: Boolean): String =
        tr("Average weight a week, ", "Peso médio por semana, ") +
            weeks.joinToString(", ") { w -> "${w.end.dayOfMonth}: ${w.kg?.let { weightValue(it, imperial) } ?: tr("none", "nenhum")}" } + "."

    fun weightInfo(): Info = Info(
        WEIGHT_TITLE,
        tr(
            "Your weight week by week, from what you log. For fat loss it's the result to check the calories against.",
            "O teu peso semana a semana, a partir do que registas. Para perder gordura, é o resultado a comparar com as calorias.",
        ),
        emptyList(), how = "", moves = "", off = "",
    )

    val WEIGHT_ROWS: List<Pair<String, String>>
        get() = listOf(
            HOW_IT_HELPS to tr(
                "Calories burned are only half of fat loss, food is the other. The weight trend shows whether the two add up: about half a kilo a week down is a steady pace that keeps your strength.",
                "As calorias gastas são só metade da perda de gordura, a comida é a outra. A tendência do peso mostra se as duas batem certo: cerca de meio quilo por semana a descer é um ritmo estável que mantém a força.",
            ),
            WHEN_TO_LOOK to tr(
                "Once a week, after logging. Look at the line over a few weeks, not one morning.",
                "Uma vez por semana, depois de registares. Olha para a linha ao longo de algumas semanas, não para uma manhã.",
            ),
            HOW_WORKED_OUT to tr(
                "Each week is the average of the weights you logged in it. The trend compares the last 7 days with the first week you logged in the 4 weeks before them. Logging also updates your profile's weight, which the calorie estimate uses.",
                "Cada semana é a média dos pesos que registaste nela. A tendência compara os últimos 7 dias com a primeira semana registada nas 4 semanas anteriores. Registar também atualiza o peso do teu perfil, que a estimativa de calorias usa.",
            ),
            WHY_OFF to tr(
                "Water, salt, a late meal or a hard session can move a morning by a kilo. Strength training adds muscle while you lose fat, so the scale can move slower than your waist.",
                "Água, sal, um jantar tarde ou um treino duro podem mexer uma manhã em um quilo. O treino de força junta músculo enquanto perdes gordura, por isso a balança pode descer mais devagar do que a cintura.",
            ),
        )

    /* ------------------------------------------------- Reports --------------------------------------------- */

    val REPORTS_TITLE: String get() = tr("Reports", "Relatórios")
    val WEEK_REPORT: String get() = tr("Weekly report", "Relatório semanal")
    val WEEK_REPORT_NOTE: String get() = tr("The last 7 days against the 3 weeks before", "Os últimos 7 dias contra as 3 semanas antes")
    val MONTH_REPORT: String get() = tr("Monthly report", "Relatório mensal")
    val MONTH_REPORT_NOTE: String get() = tr("30 days, your days of the week and your habits", "30 dias, os dias da semana e os teus hábitos")

    /* -------------------------------------------------- Info ----------------------------------------------- */

    /** The info sheets' folds: what it's for and when to look first, then the method and its limits. */
    val HOW_IT_HELPS: String get() = tr("How it helps", "Como te ajuda")
    val WHEN_TO_LOOK: String get() = tr("When to look", "Quando olhar")
    val HOW_WORKED_OUT: String get() = tr("How it's worked out", "Como é calculado")
    val WHY_OFF: String get() = tr("Why it might be off", "Porque pode falhar")

    /** The day kinds, low to high, for the hero's scale. */
    val DAY_SCALE: List<String> get() = listOf(tr("Recover", "Recuperar"), tr("Moderate", "Moderado"), tr("Usual", "Normal"), tr("Push", "Puxar"))

    fun dayInfo(): Info = Info(
        dayTitle(true),
        tr(
            "How much training the day calls for, from your Recovery this morning, in the Load your own sessions usually give.",
            "Quanto treino o dia pede, pela tua Recuperação desta manhã, na Carga que os teus treinos costumam dar.",
        ),
        DAY_SCALE, how = "", moves = "", off = "",
    )

    val DAY_ROWS: List<Pair<String, String>>
        get() = listOf(
            HOW_IT_HELPS to tr(
                "It keeps you from going hard on a day your body isn't ready for, and tells you when you can push. It says it in your own sports, from the Load a usual day of each gives you.",
                "Evita que treines forte num dia em que o corpo não está pronto, e diz-te quando podes puxar. Fala nos teus desportos, pela Carga que um dia habitual de cada um te dá.",
            ),
            WHEN_TO_LOOK to tr(
                "In the morning, before you plan the day's training, and after it to see where the day landed. Tonight's bedtime is there too: sleep is when the training pays off.",
                "De manhã, antes de planeares o treino, e depois dele para veres onde o dia ficou. A hora de deitar também está lá: é a dormir que o treino rende.",
            ),
            HOW_WORKED_OUT to tr(
                "Your Recovery sets the kind of day. Under 55, or with an alert, it's a day to recover. Under 70, or with HRV below its range, a moderate day. 85 or more, a day to push. Otherwise a usual day. The range is your own Load on training days over the last 6 weeks. A sport's usual day is the median Load of the days you did that sport and no other. Bedtime is your sleep need, plus up to 45 min for short nights and 15 to 30 min after a hard day, counted back from your usual wake time.",
                "A Recuperação define o tipo de dia. Abaixo de 55, ou com alerta, é dia para recuperar. Abaixo de 70, ou com a VFC abaixo do intervalo, dia moderado. 85 ou mais, dia para puxar. Nos outros casos, dia normal. O intervalo é a tua própria Carga nos dias de treino das últimas 6 semanas. O dia habitual de um desporto é a mediana da Carga dos dias em que fizeste só esse desporto. A hora de deitar é o sono de que precisas, mais até 45 min por noites curtas e 15 a 30 min depois de um dia duro, contada a partir da hora a que costumas acordar.",
            ),
            WHY_OFF to tr(
                "Load comes from heart rate, so heavy lifting with long rests reads lower than it feels. A sport needs 2 days on its own before its usual day shows. Until Recovery is scored, the strap's own score stands in.",
                "A Carga vem da frequência cardíaca, por isso força pesada com descansos longos conta menos do que parece. Um desporto precisa de 2 dias sozinho para o dia habitual aparecer. Até haver Recuperação, conta a pontuação da strap.",
            ),
        )

    fun riskInfo(): Info = Info(
        RISK_TITLE,
        tr(
            "Your last week's Load against your usual over 6 weeks. A sudden jump is when injuries are most likely.",
            "A tua Carga da última semana contra a habitual de 6 semanas. Um salto repentino é quando as lesões são mais prováveis.",
        ),
        Training.Zone.entries.map(::zoneWord), how = "", moves = "", off = "",
    )

    val RISK_ROWS: List<Pair<String, String>>
        get() = listOf(
            HOW_IT_HELPS to tr(
                "It lets you build fitness without jumps your body isn't used to: raise Load little by little, and keep most weeks steady.",
                "Deixa-te ganhar forma sem saltos a que o corpo não está habituado: aumenta a Carga aos poucos e mantém a maioria das semanas estáveis.",
            ),
            WHEN_TO_LOOK to tr(
                "Once a week, and before you add an extra session or a game to a busy week.",
                "Uma vez por semana, e antes de juntares um treino ou um jogo extra a uma semana cheia.",
            ),
            HOW_WORKED_OUT to tr(
                "Fatigue (the last 7 days' Load, the latest counting most) over fitness (the last 42 days'), as the day began. 75 to 125% is steady, the band Activity's Load card uses. Over 150% is a spike.",
                "Fadiga (a Carga dos últimos 7 dias, a mais recente a contar mais) a dividir pela forma (a dos últimos 42 dias), no início do dia. 75 a 125% é estável, o mesmo intervalo do cartão Carga da Atividade. Acima de 150% é um pico.",
            ),
            WHY_OFF to tr(
                "The ratio is a guide, not a diagnosis: studies disagree on how well it predicts injuries. Days without the strap count as no Load, so a week off it reads as easing off.",
                "O rácio é uma orientação, não um diagnóstico: os estudos não concordam sobre quanto prevê lesões. Dias sem a strap contam como sem Carga, por isso uma semana sem ela parece um abrandamento.",
            ),
        )

    fun caloriesInfo(): Info = Info(
        CALORIES_TITLE,
        tr(
            "What your body burns a day, at rest and moving, week by week. For fat loss, it's the side training and steps can move.",
            "O que o teu corpo gasta por dia, em repouso e a mexer-se, semana a semana. Para perder gordura, é o lado que o treino e os passos mexem.",
        ),
        emptyList(), how = "", moves = "", off = "",
    )

    val CALORIES_ROWS: List<Pair<String, String>>
        get() = listOf(
            HOW_IT_HELPS to tr(
                "Fat loss comes from burning more than you eat. Weeks side by side tell you whether your training and steps are adding up.",
                "A perda de gordura vem de gastar mais do que comes. As semanas lado a lado dizem-te se o treino e os passos estão a somar.",
            ),
            WHEN_TO_LOOK to tr(
                "At the end of the week, next to your weight or your waist.",
                "No fim da semana, ao lado do teu peso ou da medida da cintura.",
            ),
            HOW_WORKED_OUT to tr(
                "The app's own estimate, the same as Calories on the Activity tab: your burn at rest from age, weight and height (Mifflin-St Jeor), plus steps, heart rate and the strap's workouts, averaged over each week's days.",
                "A estimativa da própria app, a mesma das Calorias no separador Atividade: o gasto em repouso pela idade, peso e altura (Mifflin-St Jeor), mais passos, frequência cardíaca e os treinos da strap, em média pelos dias de cada semana.",
            ),
            WHY_OFF to tr(
                "Estimates like this are often 10 to 20% off. Food isn't counted. Days with the strap off read low, and today counts once it's over.",
                "Estimativas assim falham muitas vezes 10 a 20%. A comida não entra. Dias sem a strap contam a menos, e o dia de hoje só conta quando acabar.",
            ),
        )
}
