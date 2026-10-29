package org.openstrap.app.ui.dashboard

import org.openstrap.app.data.Activity
import org.openstrap.app.data.History
import org.openstrap.app.data.Recovery
import org.openstrap.app.data.SleepRegularity
import org.openstrap.app.l10n.tr

/*
 * What every detail's info sheet says (spec 6.2): a bold one-line meaning, the scale its value is read on, and three
 * folded rows. Every method name, formula and caveat that used to sit on a card or a detail lives here, one tap from
 * the number, so the cards can lead with what it means.
 */

/** An info sheet's words: the [meaning] in bold, the [scale] from low to high, and the three folded rows. */
data class Info(
    val name: String,
    val meaning: String,
    val scale: List<String>,
    val how: String,
    val moves: String,
    val off: String,
)

object InfoCopy {
    /** The scale of every value read against your normal, low to high. */
    val NORMAL_SCALE: List<String> get() = listOf(tr("Below your normal", "Abaixo do teu normal"), tr("Usual", "Habitual"), tr("Above your normal", "Acima do teu normal"))

    /** Recovery's bands, low to high, in the words beside the score ([TodayCopy.recoveryWord]). */
    val RECOVERY_SCALE: List<String> get() = listOf(tr("Take it easy", "Vai com calma"), tr("Moderate", "Moderado"), tr("Ready", "Pronto"), tr("Ready for more", "Pronto para mais"))

    /** Charge's bands, low to high. */
    val CHARGE_SCALE: List<String> get() = listOf(tr("Running low", "A acabar"), tr("Some left", "Ainda alguma"), tr("Good", "Boa"), tr("Full", "Cheia"))

    /** The Sleep score's bands, low to high, in the words beside it ([TabCopy.sleepScoreWord]). */
    val SLEEP_SCALE: List<String> get() = listOf(tr("Poor", "Fraca"), tr("Fair", "Razoável"), tr("Good", "Boa"), tr("Great", "Ótima"))

    /** Sleep regularity's bands, low to high ([SleepRegularity.Band]). */
    val REGULARITY_SCALE: List<String> get() = SleepRegularity.Band.entries.map { it.label }

    /** Which bar of [SLEEP_SCALE] a Sleep score is in. */
    fun sleepBar(score: Int): Int = when {
        score >= 90 -> 3
        score >= 80 -> 2
        score >= 60 -> 1
        else -> 0
    }

    /** Which bar of [REGULARITY_SCALE] a band is. */
    fun regularityBar(band: SleepRegularity.Band): Int = band.ordinal

    /** Which bar of [NORMAL_SCALE] a status against your normal is in; null without a normal. */
    fun normalBar(status: Int?): Int? = status?.let { it.coerceIn(-1, 1) + 1 }

    /** Which bar of [RECOVERY_SCALE] a score is in. */
    fun recoveryBar(score: Int): Int = when {
        score >= 85 -> 3
        score >= 70 -> 2
        score >= Recovery.FAIR_FROM -> 1
        else -> 0
    }

    /** Which bar of [CHARGE_SCALE] a Charge is in, by quarters. */
    fun chargeBar(charge: Int): Int = (charge / 25).coerceIn(0, 3)

    /**
     * The line under the scale: "You are here. Your normal is 44 to 58 ms, from 30 days." Before there's a normal, how
     * far along it is.
     */
    fun normalLine(normal: History.Normal?, days: Int, format: (Float) -> String, unit: String, nightly: Boolean): String {
        val word = if (nightly) tr("nights", "noites") else tr("days", "dias")
        if (normal == null) return tr(
            "Your normal shows after ${History.MIN_NORMAL_DAYS} $word with data. " +
                "${days.coerceAtMost(History.MIN_NORMAL_DAYS)} so far.",
            "O teu normal aparece depois de ${History.MIN_NORMAL_DAYS} $word com dados. " +
                "${days.coerceAtMost(History.MIN_NORMAL_DAYS)} até agora.",
        )
        val u = if (unit.isEmpty()) "" else if (unit.startsWith("%") || unit.startsWith("°")) unit else " $unit"
        return tr(
            "You are here. Your normal is ${format(normal.low)} to ${format(normal.high)}$u, from ${normal.days} " +
                "$word.",
            "Estás aqui. O teu normal é ${format(normal.low)} a ${format(normal.high)}$u, de ${normal.days} " +
                "$word.",
        )
    }

    /** The line under Recovery's and Charge's scales. */
    val HERE: String get() = tr("You are here.", "Estás aqui.")

    fun of(metric: Metric): Info = when (metric) {
        Metric.HeartRate -> Info(
            tr("Heart rate", "Frequência cardíaca"),
            tr(
                "How fast your heart beats, in beats per minute (bpm), read by the strap through the day and night.",
                "A rapidez com que o coração bate, em batimentos por minuto (bpm), lida pela strap durante o dia e a " +
                    "noite.",
            ),
            NORMAL_SCALE,
            how = tr(
                "The strap reads your pulse with light through the skin, about once a minute, and more often in a " +
                    "workout or while Live is on. The band behind the day's line is your resting range: your usual " +
                    "resting heart rate, give or take a usual spread, over the last 30 days.",
                "A strap lê o pulso com luz através da pele, cerca de uma vez por minuto, e mais vezes num treino ou " +
                    "com o modo ao vivo ligado. A faixa atrás da linha do dia é o teu intervalo em repouso: a tua " +
                    "frequência cardíaca em repouso habitual, mais ou menos a variação habitual, nos últimos 30 dias.",
            ),
            moves = tr(
                "Movement, stress, caffeine and heat raise it. Rest and sleep bring it down.",
                "Movimento, stress, cafeína e calor fazem-na subir. Descanso e sono fazem-na descer.",
            ),
            off = tr(
                "A loose strap, cold hands or busy arm movement can make readings jump. Live readings come more " +
                    "often, so the line looks busier while it's on.",
                "Uma strap solta, mãos frias ou muito movimento do braço podem fazer as leituras saltar. Ao vivo as " +
                    "leituras chegam mais vezes, por isso a linha parece mais agitada.",
            ),
        )
        Metric.RestingHr -> Info(
            tr("Resting heart rate", "FC em repouso"),
            tr(
                "Your heart rate at its calmest. Lower usually means a fitter, well rested heart.",
                "A tua frequência cardíaca no momento mais calmo. Mais baixa costuma querer dizer um coração mais em " +
                    "forma e descansado.",
            ),
            NORMAL_SCALE,
            how = tr(
                "The strap works it out each day from your calmest stretch, usually in sleep: its own value, the one " +
                    "it sends the Zepp app too. Your normal is the middle of your last 30 days, give or take a usual " +
                    "spread, once 7 of them have a value. Recovery reads its own measure, your lowest half hour asleep, " +
                    "usually a few bpm lower.",
                "A strap calcula-a todos os dias a partir do teu período mais calmo, normalmente a dormir: é o valor " +
                    "da própria strap, o mesmo que ela envia à app Zepp. O teu normal é o meio dos teus últimos 30 dias, " +
                    "mais ou menos a variação habitual, quando 7 deles tiverem valor. A Recuperação usa a sua própria " +
                    "medida, a tua meia hora mais calma a dormir, normalmente uns bpm abaixo.",
            ),
            moves = tr(
                "It rises after hard training, late meals, alcohol, poor sleep or when your body is fighting " +
                    "something off, and falls as fitness builds.",
                "Sobe depois de treinos duros, jantares tarde, álcool, mau sono ou quando o corpo está a combater " +
                    "alguma coisa, e desce à medida que ganhas forma.",
            ),
            off = tr(
                "A night with the strap loose or off gives it less to go on. One high day says less than a few in a " +
                    "row.",
                "Uma noite com a strap solta ou sem ela dá-lhe menos com que trabalhar. Um dia alto diz menos do que " +
                    "vários seguidos.",
            ),
        )
        Metric.Hrv -> Info(
            tr("HRV", "VFC"),
            tr(
                "Heart rate variability, the small changes in time between heartbeats. Higher than your usual means " +
                    "your body has recovered.",
                "Variabilidade da frequência cardíaca, as pequenas mudanças no tempo entre batimentos. Acima do teu " +
                    "habitual quer dizer que o corpo recuperou.",
            ),
            NORMAL_SCALE,
            how = tr(
                "The strap measures the time between beats, in milliseconds (ms), while you're still, mostly in " +
                    "sleep, and the day's value is the average of its readings. It's read against your own normal: the " +
                    "middle of your last 30 days, give or take a usual spread.",
                "A strap mede o tempo entre batimentos, em milissegundos (ms), quando estás em repouso, sobretudo a " +
                    "dormir, e o valor do dia é a média das leituras. É lida contra o teu próprio normal: o meio dos " +
                    "teus últimos 30 dias, mais ou menos a variação habitual.",
            ),
            moves = tr(
                "Good sleep and rest raise it. Stress, hard training and alcohol lower it. Compare it with your own " +
                    "normal, not other people's.",
                "Bom sono e descanso fazem-na subir. Stress, treino duro e álcool fazem-na descer. Compara-a com o " +
                    "teu próprio normal, não com o de outras pessoas.",
            ),
            off = tr(
                "Moving during a reading, a loose strap or a short night leave fewer clean readings. HRV swings a " +
                    "lot from day to day, so a few days in a row say more than one.",
                "Mexer durante uma leitura, uma strap solta ou uma noite curta deixam menos leituras limpas. A VFC " +
                    "varia muito de dia para dia, por isso vários dias seguidos dizem mais do que um.",
            ),
        )
        Metric.Spo2 -> Info(
            tr("Blood oxygen", "Oxigénio no sangue"),
            tr(
                "How much of your blood's capacity is carrying oxygen. Most people sit at 95 to 100%.",
                "Quanto da capacidade do teu sangue está a transportar oxigénio. A maioria das pessoas fica entre 95 " +
                    "e 100%.",
            ),
            NORMAL_SCALE,
            how = tr(
                "The strap shines red and infrared light through the skin and compares how much of each comes back. " +
                    "The day's value is the average of its readings.",
                "A strap emite luz vermelha e infravermelha através da pele e compara quanto de cada uma volta. O " +
                    "valor do dia é a média das leituras.",
            ),
            moves = tr(
                "Altitude and pauses in breathing during sleep can lower it.",
                "A altitude e as pausas na respiração durante o sono podem fazê-lo descer.",
            ),
            off = tr(
                "A loose strap, cold skin or moving during a reading can give low values. It's a wellness reading, " +
                    "not a medical one.",
                "Uma strap solta, pele fria ou mexer durante uma leitura podem dar valores baixos. É uma leitura de " +
                    "bem-estar, não médica.",
            ),
        )
        Metric.Stress -> Info(
            tr("Stress", "Stress"),
            tr(
                "The strap's stress level from 0 to 100. Higher means your body is more on alert.",
                "O nível de stress da strap, de 0 a 100. Mais alto quer dizer que o corpo está mais em alerta.",
            ),
            NORMAL_SCALE,
            how = tr(
                "The strap works it out from your heart rate and its variability while you're still. Readings during " +
                    "and just after a workout are left out, since exercise isn't stress.",
                "A strap calcula-o a partir da frequência cardíaca e da sua variabilidade quando estás em repouso. As " +
                    "leituras durante e logo depois de um treino ficam de fora, porque exercício não é stress.",
            ),
            moves = tr(
                "Pressure, caffeine and too little sleep raise it. Calm breathing, rest and time outdoors bring it " +
                    "down.",
                "Pressão, cafeína e pouco sono fazem-no subir. Respiração calma, descanso e tempo ao ar livre " +
                    "fazem-no descer.",
            ),
            off = tr(
                "Excitement, heat and an illness look like stress to the strap. Small movements while sitting can " +
                    "push it up.",
                "Entusiasmo, calor e uma doença parecem stress à strap. Pequenos movimentos sentado podem fazê-lo " +
                    "subir.",
            ),
        )
        Metric.SkinTemperature -> Info(
            tr("Skin temperature", "Temperatura da pele"),
            tr(
                "The temperature of your skin under the strap. What matters is the change from your normal, not the " +
                    "level.",
                "A temperatura da pele por baixo da strap. O que importa é a mudança face ao teu normal, não o nível.",
            ),
            NORMAL_SCALE,
            how = tr(
                "The strap measures it against your skin. The level depends on fit and the room, so OpenStrap shows " +
                    "it as a change from your normal of the last 30 days.",
                "A strap mede-a contra a pele. O nível depende do aperto e do quarto, por isso a OpenStrap mostra-a " +
                    "como mudança face ao teu normal dos últimos 30 dias.",
            ),
            moves = tr(
                "It moves with alcohol, a warm room, hard training, the menstrual cycle, or when your body is " +
                    "fighting something off.",
                "Muda com álcool, um quarto quente, treino duro, o ciclo menstrual, ou quando o corpo está a " +
                    "combater alguma coisa.",
            ),
            off = tr(
                "A warm bed, a heavy duvet or the strap sitting loose all change it without your body changing.",
                "Uma cama quente, um edredão pesado ou a strap solta mudam-na sem que o corpo mude.",
            ),
        )
        Metric.Breathing -> Info(
            tr("Breathing rate", "Frequência respiratória"),
            tr(
                "How many breaths you take a minute while asleep. It stays very steady for most people.",
                "Quantas respirações fazes por minuto a dormir. Fica muito estável para a maioria das pessoas.",
            ),
            NORMAL_SCALE,
            how = tr(
                "The strap measures it during sleep, so there's one value a night, read against your normal of the " +
                    "last 30 days.",
                "A strap mede-a durante o sono, por isso há um valor por noite, lido contra o teu normal dos últimos " +
                    "30 dias.",
            ),
            moves = tr(
                "It can rise above your normal after a hard training day, in a warm, stuffy room, or when your body " +
                    "is fighting something off.",
                "Pode subir acima do teu normal depois de um dia de treino duro, num quarto quente e abafado, ou " +
                    "quando o corpo está a combater alguma coisa.",
            ),
            off = tr(
                "A restless night, or the strap off for part of it, leaves fewer readings to go on.",
                "Uma noite agitada, ou a strap tirada parte da noite, deixa menos leituras.",
            ),
        )
        Metric.Steps -> Info(
            tr("Steps", "Passos"),
            tr("Steps counted by the strap through the day.", "Passos contados pela strap ao longo do dia."),
            NORMAL_SCALE,
            how = tr(
                "The strap counts them from your arm's movement and keeps them minute by minute. A day's steps are " +
                    "the total of its minutes.",
                "A strap conta-os pelo movimento do braço e guarda-os minuto a minuto. Os passos de um dia são o " +
                    "total dos seus minutos.",
            ),
            moves = tr(
                "Short walks spread across the day add up. Most people aim for 7,000 to 10,000.",
                "Caminhadas curtas espalhadas pelo dia somam. A maioria das pessoas aponta para 7 000 a 10 000.",
            ),
            off = tr(
                "Pushing a pram or a trolley, or holding a rail, hides steps. Waving your arms about can add a few.",
                "Empurrar um carrinho, ou segurar num corrimão, esconde passos. Agitar os braços pode juntar alguns.",
            ),
        )
    }

    fun of(score: Score): Info = when (score) {
        Score.Recovery -> Info(
            tr("Recovery", "Recuperação"),
            tr(
                "How ready your body is for effort today, from 0 to 100, worked out each morning from your night.",
                "Quão pronto está o teu corpo para esforço hoje, de 0 a 100, calculado todas as manhãs a partir da " +
                    "tua noite.",
            ),
            RECOVERY_SCALE,
            how = tr(
                "Each morning OpenStrap compares your night with what's normal for you, not with other people: HRV " +
                    "(heart rate variability), resting heart rate, skin temperature, breathing rate and blood oxygen, " +
                    "last night's sleep and the past few nights, and how hard yesterday was. About half the score comes " +
                    "from HRV and resting heart rate, about a quarter from sleep. One signal far from your normal caps " +
                    "it, so good sleep can't fully make up for it. Everything is worked out on this phone from your " +
                    "strap's data.",
                "Todas as manhãs a OpenStrap compara a tua noite com o que é normal para ti, não com outras pessoas: " +
                    "VFC (variabilidade da frequência cardíaca), frequência cardíaca em repouso, temperatura da pele, " +
                    "frequência respiratória e oxigénio no sangue, o sono da noite passada e das últimas noites, e quão " +
                    "duro foi o dia de ontem. Cerca de metade da pontuação vem da VFC e da frequência cardíaca em " +
                    "repouso, cerca de um quarto do sono. Um sinal muito fora do teu normal põe-lhe um teto, por isso um " +
                    "bom sono não o compensa por completo. Tudo é calculado neste telemóvel a partir dos dados da tua " +
                    "strap.",
            ),
            moves = tr(
                "Good sleep, easy days and nights without alcohol lift it. Hard training, short nights, late meals, " +
                    "stress and illness hold it down.",
                "Bom sono, dias leves e noites sem álcool fazem-na subir. Treino duro, noites curtas, jantares " +
                    "tarde, stress e doença fazem-na descer.",
            ),
            off = tr(
                "It needs ${Recovery.SCORED_NIGHTS} good nights to start and settles over " +
                    "${Recovery.SETTLED_NIGHTS}. A night with the strap loose or off, or too few clean HRV readings, " +
                    "gives it less to go on, and the strap's own readings may stand in.",
                "Precisa de ${Recovery.SCORED_NIGHTS} boas noites para começar e estabiliza ao fim de " +
                    "${Recovery.SETTLED_NIGHTS}. Uma noite com a strap solta ou sem ela, ou com poucas leituras limpas " +
                    "de VFC, dá-lhe menos com que trabalhar, e as leituras da própria strap podem entrar no lugar.",
            ),
        )
        Score.Charge -> Info(
            tr("Energy", "Energia"),
            tr(
                "Your energy right now, from 0 to 100. Sleep fills it and the day uses it.",
                "A tua energia agora, de 0 a 100. O sono enche-a e o dia gasta-a.",
            ),
            CHARGE_SCALE,
            how = tr(
                "Think of it as a battery that runs all day. Sleep fills it, deep sleep most of all, and a calm, " +
                    "steady night fills it faster. Being awake uses a little every hour. Exercise and stress use it " +
                    "faster, while quiet, relaxed moments slow the drain. It's worked out on this phone from your heart " +
                    "rate, minute by minute, as a guide to plan your day, not medical advice.",
                "Pensa nela como uma bateria que funciona o dia todo. O sono enche-a, sobretudo o sono profundo, e " +
                    "uma noite calma e estável enche-a mais depressa. Estar acordado gasta um pouco a cada hora. " +
                    "Exercício e stress gastam-na mais depressa, e momentos calmos e descontraídos abrandam o gasto. É " +
                    "calculada neste telemóvel a partir da frequência cardíaca, minuto a minuto, como guia para " +
                    "planeares o dia, não como conselho médico.",
            ),
            moves = tr(
                "A long, calm night fills it more. Workouts, a stressful day and a late night use it up.",
                "Uma noite longa e calma enche-a mais. Treinos, um dia stressante e deitar tarde gastam-na.",
            ),
            off = tr(
                "Energy starts after your first night. With the strap off overnight there's no sleep to fill it " +
                    "from, so the day starts from your usual Energy at waking, an estimate.",
                "A Energia começa depois da primeira noite. Com a strap tirada durante a noite não há sono para a " +
                    "encher, por isso o dia começa da tua Energia habitual ao acordar, uma estimativa.",
            ),
        )
        Score.Sleep -> Info(
            tr("Sleep score", "Pontuação do sono"),
            tr(
                "How well you slept, from 0 to 100, scored by the strap from the night.",
                "Quão bem dormiste, de 0 a 100, pontuado pela strap a partir da noite.",
            ),
            SLEEP_SCALE,
            how = tr(
                "The score is the strap's own, from the length and the stages of the night, and it mostly follows " +
                    "how long you slept. The stages are light, deep and REM (rapid eye movement), when most dreams " +
                    "happen. What shaped the score rates the night against general sleep guidance: time asleep against " +
                    "your sleep need, and staying asleep, with under 30 minutes awake after falling asleep counting as " +
                    "settled. Too much sleep counts against it too, past 9½ hours. Stages aren't rated, since a strap " +
                    "reads them less surely than sleep from waking. It's a guide, not a medical assessment.",
                "A pontuação é da própria strap, pela duração e pelas fases da noite, e segue sobretudo quanto tempo " +
                    "dormiste. As fases são leve, profundo e REM (movimento rápido dos olhos), quando acontecem a " +
                    "maioria dos sonhos. O que influenciou a pontuação avalia a noite contra as recomendações gerais de " +
                    "sono: tempo a dormir contra o sono de que precisas, e continuidade, com menos de 30 minutos " +
                    "acordado depois de adormecer a contar como estável. Dormir demais também conta contra, a partir de " +
                    "9 horas e meia. As fases não são avaliadas, porque uma strap as lê com menos certeza do que o sono " +
                    "face à vigília. É um guia, não uma avaliação médica.",
            ),
            moves = tr(
                "Regular bedtimes, a dark, cool room and less caffeine and alcohol late in the day help. Late " +
                    "nights, a restless night and waking early pull it down.",
                "Horas de deitar regulares, um quarto escuro e fresco e menos cafeína e álcool ao fim do dia ajudam. " +
                    "Deitar tarde, uma noite agitada e acordar cedo puxam-na para baixo.",
            ),
            off = tr(
                "Lying still awake can count as asleep, and light and deep sleep are hard to tell apart through the " +
                    "skin. A night with the strap off or loose may be missed or cut short.",
                "Estar deitado acordado e quieto pode contar como sono, e o sono leve e o profundo são difíceis de " +
                    "distinguir através da pele. Uma noite com a strap tirada ou solta pode falhar ou ficar cortada.",
            ),
        )
        Score.Regularity -> Info(
            tr("Sleep regularity", "Regularidade do sono"),
            tr(
                "How alike your sleep and wake times are from one day to the next. 100 is the same every day.",
                "Quão parecidas são as tuas horas de dormir e acordar de um dia para o outro. 100 é igual todos os " +
                    "dias.",
            ),
            REGULARITY_SCALE,
            how = tr(
                "OpenStrap works out the Sleep Regularity Index. It marks each minute of the day asleep or awake and " +
                    "counts how often a minute matches the same minute the day before, over the last 7 days. It needs " +
                    "${SleepRegularity.MIN_VALID_PAIRS / 60} h of wear compared day to day, about 5 days worn around the " +
                    "clock, and until then shows how much the middle of your sleep moved. The bands follow how UK " +
                    "Biobank adults scored, where half of them were between 74 and 86. Your usual times are the average " +
                    "of the last 7 nights.",
                "A OpenStrap calcula o Índice de Regularidade do Sono. Marca cada minuto do dia como a dormir ou " +
                    "acordado e conta quantas vezes um minuto coincide com o mesmo minuto do dia anterior, nos últimos 7 " +
                    "dias. Precisa de ${SleepRegularity.MIN_VALID_PAIRS / 60} h de uso comparadas dia a dia, cerca de 5 " +
                    "dias com a strap posta dia e noite, e até lá mostra quanto mudou o meio do teu sono. As faixas " +
                    "seguem as pontuações de adultos do UK Biobank, onde metade ficou entre 74 e 86. As tuas horas " +
                    "habituais são a média das últimas 7 noites.",
            ),
            moves = tr(
                "Going to bed and getting up at the same times, weekends too, raises it. Late nights, lie-ins and " +
                    "long naps lower it.",
                "Deitar e levantar às mesmas horas, também ao fim de semana, faz subir. Deitar tarde, ficar na cama " +
                    "até tarde e sestas longas fazem descer.",
            ),
            off = tr(
                "Minutes with the strap off aren't compared, so a week with little wear rests on fewer days. Lying " +
                    "awake in bed can count as asleep.",
                "Os minutos sem a strap não são comparados, por isso uma semana com pouco uso assenta em menos dias. " +
                    "Estar acordado na cama pode contar como sono.",
            ),
        )
        Score.Naps -> Info(
            tr("Naps", "Sestas"),
            tr(
                "Sleep in the day, which the strap records apart from your night.",
                "Sono durante o dia, que a strap regista à parte da tua noite.",
            ),
            emptyList(),
            how = tr(
                "The strap tells a nap from your night itself, and records its stages when it's long enough. A nap " +
                    "belongs to the day it started on. Its heart rate comes from the day's readings, since the strap " +
                    "sends no average for a nap.",
                "A strap distingue sozinha uma sesta da tua noite, e regista as fases quando é longa o suficiente. " +
                    "Uma sesta pertence ao dia em que começou. A sua frequência cardíaca vem das leituras do dia, porque " +
                    "a strap não envia uma média para a sesta.",
            ),
            moves = tr(
                "A short nap early in the afternoon refreshes without taking much from the night. A long or late one " +
                    "can make it harder to fall asleep.",
                "Uma sesta curta no início da tarde refresca sem tirar muito à noite. Uma longa ou tardia pode " +
                    "tornar mais difícil adormecer.",
            ),
            off = tr(
                "Lying still awake can look like a short nap, and a short nap may have no stages.",
                "Estar quieto acordado pode parecer uma sesta curta, e uma sesta curta pode não ter fases.",
            ),
        )
        // The Activity tab's (spec 5.3). The method notes that sat on the old Activity panels (Paluch, Mifflin-St Jeor,
        // Jurca, FRIEND, acute:chronic, the strap's weekly EPOC range, training effect) live here now.
        Score.HeartPoints -> Info(
            tr("Heart points", "Pontos cardíacos"),
            tr(
                "Points for the minutes your heart rate is raised, harder minutes earning more, over the last 7 " +
                    "days. Aim for ${ActivityCopy.HEART_POINTS_TARGET}.",
                "Pontos pelos minutos com a frequência cardíaca elevada, os mais duros valem mais, nos últimos 7 " +
                    "dias. Aponta para ${ActivityCopy.HEART_POINTS_TARGET}.",
            ),
            emptyList(),
            how = tr(
                "Heart points are the strap's PAI, Personal Activity Intelligence. The strap works them out itself " +
                    "from how far your heart rate rises above resting, against your age and sex, and OpenStrap shows " +
                    "them as they come. Each day's points count for 7 days, then drop off, so the total always covers " +
                    "the last week.",
                "Os pontos cardíacos são o PAI da strap, Personal Activity Intelligence. A própria strap calcula-os " +
                    "a partir de quanto a frequência cardíaca sobe acima do repouso, tendo em conta idade e sexo, e a " +
                    "OpenStrap mostra-os tal como chegam. Os pontos de cada dia contam durante 7 dias e depois saem, por " +
                    "isso o total cobre sempre a última semana.",
            ),
            moves = tr(
                "Anything that gets you out of breath adds the most. Brisk walking adds a little. A few hard " +
                    "sessions a week keep the total at ${ActivityCopy.HEART_POINTS_TARGET} or more.",
                "O que te deixa sem fôlego soma mais. Caminhar depressa soma um pouco. Alguns treinos duros por " +
                    "semana mantêm o total em ${ActivityCopy.HEART_POINTS_TARGET} ou mais.",
            ),
            off = tr(
                "The total updates when the strap syncs, so it can lag behind today's effort. A loose strap in a " +
                    "workout misses raised heart rate.",
                "O total atualiza quando a strap sincroniza, por isso pode ficar atrás do esforço de hoje. Uma strap " +
                    "solta num treino falha frequência cardíaca elevada.",
            ),
        )
        Score.ActiveMinutes -> Info(
            tr("Active minutes", "Minutos ativos"),
            tr(
                "Minutes of brisk movement or harder over the last 7 days, against the " +
                    "${Activity.MVPA_WEEKLY_TARGET} a week health guidelines aim for.",
                "Minutos de movimento rápido ou mais intenso nos últimos 7 dias, contra os " +
                    "${Activity.MVPA_WEEKLY_TARGET} por semana que as recomendações de saúde apontam.",
            ),
            emptyList(),
            how = tr(
                "A minute at ${Activity.MODERATE_CADENCE} steps or more counts as moderate, and " +
                    "${Activity.VIGOROUS_CADENCE} or more as vigorous, inside a run of at least " +
                    "${Activity.CADENCE_RUN_MINUTES} such minutes. Effort without steps, like cycling or rowing, counts " +
                    "from heart rate after ${Activity.HR_RUN_MINUTES} minutes in a row at " +
                    "${(Activity.HR_MODERATE_HRR * 100).toInt()}% of your heart rate reserve or more, vigorous from " +
                    "${(Activity.HR_VIGOROUS_HRR * 100).toInt()}%. A vigorous minute counts double, as in the World " +
                    "Health Organization's 2020 guidelines.",
                "Um minuto com ${Activity.MODERATE_CADENCE} passos ou mais conta como moderado, e com " +
                    "${Activity.VIGOROUS_CADENCE} ou mais como vigoroso, dentro de uma sequência de pelo menos " +
                    "${Activity.CADENCE_RUN_MINUTES} minutos assim. Esforço sem passos, como bicicleta ou remo, conta " +
                    "pela frequência cardíaca depois de ${Activity.HR_RUN_MINUTES} minutos seguidos a " +
                    "${(Activity.HR_MODERATE_HRR * 100).toInt()}% da tua reserva cardíaca ou mais, vigoroso a partir de " +
                    "${(Activity.HR_VIGOROUS_HRR * 100).toInt()}%. Um minuto vigoroso conta a dobrar, como nas " +
                    "recomendações de 2020 da Organização Mundial da Saúde.",
            ),
            moves = tr(
                "Brisk walks of a few minutes or more, cycling, running and sport all count. Strolling and short " +
                    "bursts don't.",
                "Caminhadas rápidas de alguns minutos ou mais, bicicleta, corrida e desporto contam todos. Passear e " +
                    "esforços curtos não.",
            ),
            off = tr(
                "No study has checked a strap's step counts against walking pace, so brisk minutes near the line can " +
                    "be missed or counted. Pushing a trolley or holding a rail hides steps.",
                "Nenhum estudo comparou a contagem de passos de uma strap com o ritmo da caminhada, por isso minutos " +
                    "rápidos perto do limite podem falhar ou contar. Empurrar um carrinho ou segurar num corrimão " +
                    "esconde passos.",
            ),
        )
        Score.Load -> Info(
            tr("Load", "Carga"),
            tr(
                "How hard your heart worked today, from workouts and moving about, against your usual day.",
                "Quanto o teu coração trabalhou hoje, com treinos e a mexer-te, contra o teu dia habitual.",
            ),
            ActivityCopy.LOAD_SCALE,
            how = tr(
                "Each minute counts by how hard your heart works between resting and maximum, Banister's training " +
                    "impulse (TRIMP). Every workout minute counts. Outside workouts only moving minutes do, at 35% of " +
                    "your heart rate reserve or more, or brisk walking from 20%. Your usual is your fitness, a 42-day " +
                    "average of your daily Load. Building, Steady and Easing off compare the last 7 days with it, Steady " +
                    "within a quarter either side. Strain puts the day on a fixed curve from 0 to 21, where an hour at " +
                    "60% of your reserve is about 7.",
                "Cada minuto conta pelo esforço do coração entre o repouso e o máximo, o impulso de treino de " +
                    "Banister (TRIMP). Todos os minutos de treino contam. Fora dos treinos só contam os minutos a mexer, " +
                    "a 35% da tua reserva cardíaca ou mais, ou a caminhar depressa a partir de 20%. O teu habitual é a " +
                    "tua forma, uma média de 42 dias da tua Carga diária. A subir, Estável e A abrandar comparam os " +
                    "últimos 7 dias com ela, Estável dentro de um quarto para cada lado. O esforço (Strain) põe o dia " +
                    "numa curva fixa de 0 a 21, onde uma hora a 60% da tua reserva dá cerca de 7.",
            ),
            moves = tr(
                "Workouts add the most, longer and harder ones more. A day of walking about adds a little. Rest days " +
                    "bring the week's balance down.",
                "Os treinos somam mais, os mais longos e duros mais ainda. Um dia a andar de um lado para o outro " +
                    "soma um pouco. Dias de descanso baixam o equilíbrio da semana.",
            ),
            off = tr(
                "Days the strap was mostly off are left out of your usual. A low maximum heart rate makes every " +
                    "minute count for more. Workouts also carry the strap's EPOC, its measure of what one workout took, " +
                    "on a different scale from Load.",
                "Os dias em que a strap esteve quase sempre tirada ficam fora do teu habitual. Um máximo de " +
                    "frequência cardíaca baixo faz cada minuto contar mais. Os treinos também trazem o EPOC da strap, a " +
                    "sua medida do que um treino custou, numa escala diferente da Carga.",
            ),
        )
        Score.Vo2Max -> Info(
            tr("VO2 max", "VO2 máx"),
            tr(
                "The most oxygen your body can use in hard effort, a measure of fitness. Higher is fitter.",
                "O máximo de oxigénio que o corpo consegue usar em esforço intenso, uma medida de forma física. Mais " +
                    "alto é mais em forma.",
            ),
            emptyList(),
            how = tr(
                "It's measured in millilitres of oxygen per kilogram of body weight a minute (ml/kg/min). After a " +
                    "workout the strap can read it from, the number is the strap's own estimate from your heart rate and " +
                    "pace. Otherwise OpenStrap estimates it from your age, sex, body mass index, resting heart rate " +
                    "while asleep and how active you say you are (Jurca and others, 2005), updated weekly, and shows it " +
                    "as a range. Where you sit for your age and sex comes from treadmill tests of thousands of adults " +
                    "(FRIEND, 2022).",
                "Mede-se em mililitros de oxigénio por quilo de peso por minuto (ml/kg/min). Depois de um treino em " +
                    "que a strap o consiga ler, o número é a estimativa da própria strap pela frequência cardíaca e pelo " +
                    "ritmo. Caso contrário a OpenStrap estima-o pela idade, sexo, índice de massa corporal, frequência " +
                    "cardíaca em repouso a dormir e quão ativo dizes ser (Jurca e outros, 2005), atualizado todas as " +
                    "semanas, e mostra-o como um intervalo. Onde estás para a tua idade e sexo vem de testes em " +
                    "passadeira de milhares de adultos (FRIEND, 2022).",
            ),
            moves = tr(
                "Regular hard effort raises it over weeks. Weight, age and long breaks from training lower it.",
                "Esforço intenso regular fá-lo subir ao longo de semanas. Peso, idade e longas pausas no treino " +
                    "fazem-no descer.",
            ),
            off = tr(
                "The estimate from your profile is typically within about 5 either way. A resting heart rate thrown " +
                    "by a short night or illness moves it.",
                "A estimativa pelo perfil costuma ficar a cerca de 5 para cada lado. Uma frequência cardíaca em " +
                    "repouso alterada por uma noite curta ou doença mexe nela.",
            ),
        )
        Score.Recover -> Info(
            tr("Time to recover", "Tempo de recuperação"),
            tr(
                "How long your body needs after your last workout before it's ready for another hard one.",
                "Quanto tempo o corpo precisa depois do último treino até estar pronto para outro duro.",
            ),
            emptyList(),
            how = tr(
                "It's the strap's estimate, or OpenStrap's own for a workout recorded on the phone, from how hard " +
                    "the workout was: its EPOC, the extra oxygen your body uses afterwards to recover. The same EPOC " +
                    "gives each workout its training effect, from 0 to 5, for how much it builds your fitness.",
                "É a estimativa da strap, ou da própria OpenStrap para um treino gravado no telemóvel, pela dureza " +
                    "do treino: o seu EPOC, o oxigénio extra que o corpo usa depois para recuperar. O mesmo EPOC dá a " +
                    "cada treino o seu efeito de treino, de 0 a 5, por quanto melhora a tua forma.",
            ),
            moves = tr(
                "Longer and harder workouts need more time. Easy sessions need little.",
                "Treinos mais longos e duros precisam de mais tempo. Treinos leves precisam de pouco.",
            ),
            off = tr(
                "It knows only the workout, not your sleep, stress or the rest of the day. Recovery here is time, " +
                    "not the morning's Recovery score.",
                "Só conhece o treino, não o teu sono, stress ou o resto do dia. Recuperar aqui é tempo, não a " +
                    "pontuação de Recuperação da manhã.",
            ),
        )
        Score.Energy -> Info(
            tr("Calories", "Calorias"),
            tr(
                "The energy your body uses in a day, at rest and moving, in kilocalories (kcal). An estimate.",
                "A energia que o corpo gasta num dia, em repouso e a mexer-se, em quilocalorias (kcal). Uma " +
                    "estimativa.",
            ),
            emptyList(),
            how = tr(
                "Your body's use at rest comes from your weight, height, age and sex (Mifflin-St Jeor). On top of it " +
                    "come your steps each minute and, for effort without steps, your heart rate. A recorded workout uses " +
                    "its own kcal. Sleep counts at a little under resting.",
                "O gasto em repouso vem do peso, altura, idade e sexo (Mifflin-St Jeor). Por cima juntam-se os " +
                    "passos de cada minuto e, para esforço sem passos, a frequência cardíaca. Um treino gravado usa as " +
                    "suas próprias kcal. O sono conta um pouco abaixo do repouso.",
            ),
            moves = tr(
                "Moving more raises it, most of all workouts. A heavier body uses more at rest and in motion.",
                "Mexer-te mais faz subir, sobretudo os treinos. Um corpo mais pesado gasta mais em repouso e em " +
                    "movimento.",
            ),
            off = tr(
                "Food digestion isn't included. It only covers the minutes the strap was worn, so a day with it off " +
                    "for hours reads low. A profile out of date throws the resting part.",
                "A digestão não está incluída. Só cobre os minutos com a strap posta, por isso um dia sem ela " +
                    "durante horas conta a menos. Um perfil desatualizado altera a parte em repouso.",
            ),
        )
        Score.Distance -> Info(
            tr("Distance", "Distância"),
            tr(
                "How far you walked and ran today, estimated from your steps.",
                "Quanto andaste e correste hoje, estimado pelos passos.",
            ),
            emptyList(),
            how = tr(
                "Each minute's steps times a step length from your height: " +
                    "${(Activity.WALK_STEP_FRACTION * 100).roundToIntString()}% of it at a walk, rising to " +
                    "${(Activity.RUN_STEP_FRACTION * 100).roundToIntString()}% at 130 steps a minute and faster. Without " +
                    "a height the strap's own figure stands in.",
                "Os passos de cada minuto vezes um comprimento de passo pela tua altura: " +
                    "${(Activity.WALK_STEP_FRACTION * 100).roundToIntString()}% dela a andar, a subir até " +
                    "${(Activity.RUN_STEP_FRACTION * 100).roundToIntString()}% a 130 passos por minuto ou mais. Sem " +
                    "altura entra o valor da própria strap.",
            ),
            moves = tr("More steps, and faster ones, which are longer.", "Mais passos, e mais rápidos, que são mais longos."),
            off = tr(
                "Step length varies from person to person and with hills, so it can be off by a tenth or more. A " +
                    "workout with GPS has its own distance.",
                "O comprimento do passo varia de pessoa para pessoa e com subidas, por isso pode falhar um décimo ou " +
                    "mais. Um treino com GPS tem a sua própria distância.",
            ),
        )
    }

    private fun Float.roundToIntString(): String = if (this % 1f == 0f) toInt().toString() else "%.1f".format(this)

    /** What's particular about today's active minutes: how many came from heart rate. */
    fun activeNotes(fromHeartRate: Int, today: Boolean): List<String> = listOfNotNull(
        tr(
            "$fromHeartRate of ${if (today) "today's" else "that day's"} minutes came from heart rate.",
            "$fromHeartRate dos minutos ${if (today) "de hoje" else "desse dia"} vieram da frequência cardíaca.",
        ).takeIf { fromHeartRate > 0 },
    )

    /** What's particular about today's Load: the heart rates it's read between, and the contested ratio. */
    fun loadNotes(restingHr: Float, maxHr: Float, maxTested: Boolean, acwr: Float?): List<String> = listOfNotNull(
        tr(
            "Today it's read between ${restingHr.toInt()} bpm resting and ${maxHr.toInt()} bpm maximum",
            "Hoje é lida entre ${restingHr.toInt()} bpm em repouso e ${maxHr.toInt()} bpm de máximo",
        ) +
            if (maxTested) tr(", from your test.", ", do teu teste.") else ".",
        acwr?.let {
            tr(
                "Your last 7 days against the last 4 weeks come to %.2f, the acute to chronic ratio. It's shown for ",
                "Os teus últimos 7 dias contra as últimas 4 semanas dão %.2f, o rácio agudo/crónico. É mostrado só ",
            ).format(it) +
                tr(
                    "interest only, since the evidence for a safe ratio is disputed, and never enters Recovery.",
                    "por curiosidade, já que a evidência sobre um rácio seguro é discutida, e nunca entra na Recuperação.",
                )
        },
    )

    /** What's particular about the VO2 max estimate: the resting heart rate used, fitness age, or why it's withheld. */
    fun vo2Notes(estimate: org.openstrap.app.data.Vo2max.Estimate?, withheld: org.openstrap.app.data.Vo2max.Withheld?): List<String> = buildList {
        estimate?.let { e ->
            add(tr(
                "This week's estimate uses a resting heart rate of ${e.restingHr.toInt()} bpm, while asleep.",
                "A estimativa desta semana usa uma frequência cardíaca em repouso de ${e.restingHr.toInt()} bpm, a " +
                    "dormir.",
            ))
            e.fitnessAge?.let { a ->
                e.sex?.let { add(tr(
                    "That's typical of a " +
                        "${if (it == org.openstrap.app.data.settings.UserProfile.Sex.Male) "man" else "woman"} aged about " +
                        "${a.first} to ${a.last}.",
                    "É típico de " +
                        "${if (it == org.openstrap.app.data.settings.UserProfile.Sex.Male) "um homem" else "uma mulher"} com " +
                        "cerca de ${a.first} a ${a.last} anos.",
                )) }
            }
        }
        withheld?.let { add(tr("No estimate from your profile yet. ${it.reason}", "Ainda sem estimativa pelo teu perfil. ${it.reason}")) }
    }

    /** What's particular about the day's energy: part of the day worn, an unusual total, minutes from heart rate. */
    fun energyNotes(e: org.openstrap.app.data.EnergyExpenditure.Day?): List<String> = buildList {
        e ?: return@buildList
        if (e.partial) add(tr(
            "It covers ${TodayCopy.duration(e.minutesWithData * 60L)} of wear, so it's part of the day.",
            "Cobre ${TodayCopy.duration(e.minutesWithData * 60L)} de uso, por isso é só parte do dia.",
        ))
        if (e.palImplausible) add(tr(
            "That's an unusual total for a full day. Check your profile and how the strap was worn.",
            "É um total invulgar para um dia inteiro. Confirma o teu perfil e como a strap foi usada.",
        ))
        if (e.hrAssistedMinutes > 0) add(tr(
            "${e.hrAssistedMinutes} minutes came from heart rate rather than steps.",
            "${e.hrAssistedMinutes} minutos vieram da frequência cardíaca em vez dos passos.",
        ))
    }

    /** Spec 6.2's sheet for a night vital opened from the Sleep tab: the metric's, saying where the night's numbers come from. */
    fun ofNight(metric: Metric): Info = of(metric).let { it.copy(how = it.how + " " + nightHow(metric)) }

    private fun nightHow(metric: Metric): String = when (metric) {
        Metric.HeartRate -> tr(
            "Opened from the night, it shows only your sleep: the line from falling asleep to waking, the lowest " +
                "while asleep and the strap's average for the night. Month and Year are whole days.",
            "Aberto a partir da noite, mostra só o teu sono: a linha de adormecer a acordar, o mínimo a dormir e " +
                "a média da strap para a noite. Mês e Ano são dias inteiros.",
        )
        Metric.Spo2 -> tr(
            "Opened from the night, it shows the strap's readings in sleep, kept apart from the spot readings in " +
                "the day, against your earlier nights.",
            "Aberto a partir da noite, mostra as leituras da strap a dormir, separadas das leituras pontuais do " +
                "dia, contra as tuas noites anteriores.",
        )
        Metric.Breathing -> tr(
            "Opened from the night, it's that night's readings, against your earlier nights.",
            "Aberto a partir da noite, são as leituras dessa noite, contra as tuas noites anteriores.",
        )
        else -> tr(
            "Opened from the night, it's the average of the readings taken while you slept, against your earlier " +
                "nights. Month and Year are whole days, day and night.",
            "Aberto a partir da noite, é a média das leituras feitas enquanto dormias, contra as tuas noites " +
                "anteriores. Mês e Ano são dias inteiros, dia e noite.",
        )
    }

    /** The breathing disturbances sheet: an early estimate, with no scale, since its only words would be apnea bands. */
    val BREATHING_DISTURBANCES: Info get() = Info(
        tr("Breathing disturbances", "Perturbações respiratórias"),
        tr(
            "Dips in blood oxygen while you sleep, counted by the strap, as how many an hour. An early estimate.",
            "Descidas do oxigénio no sangue durante o sono, contadas pela strap, em número por hora. Uma " +
                "estimativa inicial.",
        ),
        emptyList(),
        how = tr(
            "The strap counts each short drop in blood oxygen during sleep, and that's divided by the hours " +
                "asleep. Your usual is the middle of your earlier nights, once there are ${Recovery.SCORED_NIGHTS} " +
                "of them. They are fairly steady for each person, so they're shown with your sleep rather than in " +
                "Recovery.",
            "A strap conta cada descida curta do oxigénio no sangue durante o sono, e o total é dividido pelas " +
                "horas a dormir. O teu habitual é o meio das tuas noites anteriores, quando houver " +
                "${Recovery.SCORED_NIGHTS}. São bastante estáveis em cada pessoa, por isso aparecem com o teu sono e " +
                "não na Recuperação.",
        ),
        moves = tr(
            "Alcohol, a blocked nose, sleeping on your back and altitude can raise it.",
            "Álcool, nariz entupido, dormir de costas e altitude podem fazê-las subir.",
        ),
        off = tr(
            "The strap's units aren't confirmed on a real strap yet, so treat the number as an early estimate. A " +
                "loose strap can read as a dip. ${org.openstrap.app.data.BodySignalsCopy.DISCLAIMER} It isn't a test " +
                "for sleep apnea.",
            "As unidades da strap ainda não foram confirmadas numa strap real, por isso trata o número como uma " +
                "estimativa inicial. Uma strap solta pode parecer uma descida. " +
                "${org.openstrap.app.data.BodySignalsCopy.DISCLAIMER} Não é um teste de apneia do sono.",
        ),
    )

    /** Habit Age's bands, against your age: [habitAgeBar] says which one you're in. */
    val HABIT_AGE_SCALE: List<String> get() = listOf(tr("Younger than you", "Mais novo do que tu"), tr("Your age", "A tua idade"), tr("Older than you", "Mais velho do que tu"))

    /** Which bar of [HABIT_AGE_SCALE] an estimate [years] younger than your age is in. */
    fun habitAgeBar(years: Int): Int = when {
        years > 0 -> 0
        years < 0 -> 2
        else -> 1
    }

    /** Habit Age's sheet, behind the (i) on its card (spec 5.4): the method, the pace of aging and what moves it. */
    val HABIT_AGE: Info get() = Info(
        tr("Habit Age", "Idade dos hábitos"),
        tr(
            "How old your habits make your body look, as a range of years, worked out each Sunday.",
            "Que idade os teus hábitos dão ao teu corpo, como um intervalo de anos, calculada todos os domingos.",
        ),
        HABIT_AGE_SCALE,
        how = tr(
            "Each habit is scored against the typical person your age and sex: resting heart rate, steps, sleep " +
                "regularity, time asleep, strength training and fitness (VO2 max, the most oxygen your body can use " +
                "in hard effort). Better than typical takes years off, worse adds them. The total is shrunk, because " +
                "the habits overlap, and capped at 5 years either way. It looks back 6 months, 3 for fitness.",
            "Cada hábito é comparado com a pessoa típica da tua idade e sexo: frequência cardíaca em repouso, " +
                "passos, regularidade do sono, tempo a dormir, treino de força e forma física (VO2 máx, o máximo de " +
                "oxigénio que o corpo consegue usar em esforço intenso). Melhor do que o típico tira anos, pior " +
                "junta. O total é reduzido, porque os hábitos se sobrepõem, e limitado a 5 anos para cada lado. Olha " +
                "para os últimos 6 meses, 3 para a forma física.",
        ),
        moves = tr(
            "Your habits over months, not a single day. The pace of aging compares your last 30 days with those " +
                "6 months: faster than 1x means the estimate is heading up, slower means it's coming down. The Habit " +
                "Age page shows what each habit takes off or adds.",
            "Os teus hábitos ao longo de meses, não de um só dia. O ritmo de envelhecimento compara os teus " +
                "últimos 30 dias com esses 6 meses: mais rápido do que 1x quer dizer que a estimativa vai subir, " +
                "mais lento que vai descer. A página Idade dos hábitos mostra o que cada hábito tira ou junta.",
        ),
        off = tr(
            "It updates once a week and is withheld until there's enough data. An estimate from under 6 months " +
                "of data is early, so its range is wider. It's an estimate for personal insight, not a medical " +
                "measurement, shown as a range because each habit's effect is uncertain.",
            "Atualiza uma vez por semana e fica retida até haver dados suficientes. Uma estimativa com menos de " +
                "6 meses de dados é inicial, por isso o intervalo é mais largo. É uma estimativa para conhecimento " +
                "pessoal, não uma medição médica, mostrada como intervalo porque o efeito de cada hábito é incerto.",
        ),
    )

    /** The week story's sheet: what each day's numbers are and how far back the comparisons reach. */
    val WEEK: Info get() = Info(
        tr("Your week", "A tua semana"),
        tr(
            "Last Monday to Sunday, day by day: how much you moved, trained and slept.",
            "De segunda a domingo passados, dia a dia: quanto te mexeste, treinaste e dormiste.",
        ),
        emptyList(),
        how = tr(
            "Steps are each day's total from the strap's minutes, and steps a day the average of the days with " +
                "any. Active minutes are worked out as in their own sheet. A night belongs to the evening it " +
                "started, so Sunday's is the night into Monday.",
            "Os passos são o total de cada dia a partir dos minutos da strap, e os passos por dia a média dos " +
                "dias com algum. Os minutos ativos são calculados como na sua própria folha. Uma noite pertence à " +
                "tarde em que começou, por isso a de domingo é a noite para segunda.",
        ),
        moves = tr(
            "Every walk, workout and early night shows here a week later.",
            "Cada caminhada, treino e noite em que te deitaste cedo aparece aqui uma semana depois.",
        ),
        off = tr(
            "Steps and workouts are compared with the week before. Active minutes and sleep only go back two " +
                "weeks, so they're shown without a comparison. A day with the strap off reads low.",
            "Passos e treinos são comparados com a semana anterior. Os minutos ativos e o sono só vão duas " +
                "semanas para trás, por isso aparecem sem comparação. Um dia sem a strap conta a menos.",
        ),
    )

    /** Under the breathing disturbances sheet's title: the night against your usual, or how far the usual has got. */
    fun breathingLine(b: Recovery.BreathingDisturbances, today: Boolean): String {
        val night = tr(
            "${if (today) "Last night" else "That night"} %.1f an hour.",
            "${if (today) "Na noite passada" else "Nessa noite"}, %.1f por hora.",
        ).format(b.perHour)
        return when (val usual = b.usual) {
            null -> tr(
                "$night Learning your usual, ${b.nights.coerceAtMost(Recovery.SCORED_NIGHTS)} of " +
                    "${Recovery.SCORED_NIGHTS} nights.",
                "$night A aprender o habitual, ${b.nights.coerceAtMost(Recovery.SCORED_NIGHTS)} de " +
                    "${Recovery.SCORED_NIGHTS} noites.",
            )
            else -> tr("$night Your usual is %.1f.", "$night O teu habitual é %.1f.").format(usual)
        }
    }

    /** What's particular about this week's regularity: how much wear it compares, or what it still needs. */
    fun regularityNotes(reg: SleepRegularity.Result?): List<String> = buildList {
        reg ?: return@buildList
        val hours = reg.validPairs / 60
        if (reg.sri != null) add(tr("This week compares $hours h of wear.", "Esta semana compara $hours h de uso."))
        else {
            add(tr(
                "Not enough wear for the full measure yet: $hours h of the ${SleepRegularity.MIN_VALID_PAIRS / 60} h " +
                    "it needs.",
                "Ainda sem uso suficiente para a medida completa: $hours h das " +
                    "${SleepRegularity.MIN_VALID_PAIRS / 60} h de que precisa.",
            ))
            reg.midpointSdMinutes?.let { add(tr(
                "Meanwhile the middle of your sleep moved about ${TodayCopy.duration(Math.round(it) * 60L)} this " +
                    "week.",
                "Entretanto o meio do teu sono mudou cerca de ${TodayCopy.duration(Math.round(it) * 60L)} esta " +
                    "semana.",
            )) }
        }
    }

    /** What's particular about the night's Sleep score: the sleep need the guidance measures against. */
    fun sleepNotes(needMinutes: Int): List<String> = listOf(tr(
        "Your sleep need here is ${TodayCopy.duration(needMinutes * 60L)}, from your age or your own setting.",
        "O sono de que precisas aqui é ${TodayCopy.duration(needMinutes * 60L)}, pela tua idade ou pela tua " +
            "definição.",
    ))

    /**
     * What's particular about today's Recovery, for its info sheet: the strap's own score beside ours, and anything that
     * made tonight's reading less sure. Each is a sentence; none when there's nothing to say.
     */
    fun recoveryNotes(ready: Recovery.Readiness?, strapScore: Int?): List<String> = buildList {
        if (strapScore != null) add(
            tr(
                "The strap's own score today is $strapScore. It doesn't count sleep length, so after a short night " +
                    "it reads higher than OpenStrap's. It's kept for comparison and never enters OpenStrap's score.",
                "A pontuação da própria strap hoje é $strapScore. Não conta a duração do sono, por isso depois de " +
                    "uma noite curta fica mais alta do que a da OpenStrap. Fica para comparação e nunca entra na " +
                    "pontuação da OpenStrap.",
            ),
        )
        ready ?: return@buildList
        if (ready.capped) add(tr(
            "Today one body signal is far from your normal, so the score is capped.",
            "Hoje um sinal do corpo está muito fora do teu normal, por isso a pontuação tem um teto.",
        ))
        ready.fallbackHrv?.let { add(tr(
            "Last night's own HRV wasn't clean enough, so the strap's ${it.toInt()} ms stands in.",
            "A VFC da própria noite não foi limpa o suficiente, por isso entram os ${it.toInt()} ms da strap.",
        )) }
        ready.fallbackRestingHr?.let { add(tr(
            "Last night's own resting heart rate wasn't clean enough, so the strap's ${it.toInt()} bpm stands in.",
            "A frequência cardíaca em repouso da própria noite não foi limpa o suficiente, por isso entram os " +
                "${it.toInt()} bpm da strap.",
        )) }
        if (ready.hrvWeekThin) add(tr(
            "The week's HRV rests on ${ready.hrvWeekNights} of 7 nights, so it's less sure than usual.",
            "A VFC da semana assenta em ${ready.hrvWeekNights} de 7 noites, por isso é menos certa do que o " +
                "habitual.",
        ))
    }

    /** What's particular about today's Charge: an estimated start after a night with the strap off. */
    fun chargeNotes(c: Recovery.ChargeDay?): List<String> = listOfNotNull(
        tr(
            "The strap was off overnight, so today started from an estimate.",
            "A strap não esteve posta durante a noite, por isso o dia começou com uma estimativa.",
        ).takeIf { c?.strapOffOvernight == true },
    )
}
