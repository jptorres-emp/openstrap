package org.openstrap.app.ui.workout

import org.openstrap.app.data.Route
import org.openstrap.app.data.Workouts
import org.openstrap.app.device.ConnectionState
import org.openstrap.app.l10n.tr
import org.openstrap.app.ui.glow.workoutClock

/*
 * What the Ready and live workout screens say (spec 6.3), and the small rules behind what they show: the split line's
 * ten seconds, the remembered view, the light that follows the zone. Pure, so it's tested without a workout running.
 */

/** The two views of a GPS workout's live screen, in swipe order. */
enum class LiveView {
    Heart, Map;

    companion object {
        /** The view saved as [code] (its ordinal), Heart when there's none or it's unknown. */
        fun of(code: Int?): LiveView = code?.let { entries.getOrNull(it) } ?: Heart
    }
}

/** A finished kilometre on the live screen: which one ([index] from 0), when it ended, and its line. */
data class SplitLine(val index: Int, val endT: Long, val text: String)

object WorkoutCopy {
    /** How long a split line stays under the stats. */
    const val SPLIT_LINE_S = 10L

    /** At or under this, the Ready line mentions the strap's battery. */
    const val LOW_BATTERY = 20

    private const val KM = 1000.0

    /**
     * Ready's hero, the strap (spec 6.3): its [title], the [line] under it, and whether it's [live], the glow with the
     * heart rate, or an outline card while the strap is away.
     */
    data class ReadyHero(val title: String, val line: String, val live: Boolean)

    /** "Strap connected" and "Live from your strap.", with the battery when low; "Strap not connected" otherwise. */
    fun readyHero(connection: ConnectionState, bpm: Int?): ReadyHero = when (connection) {
        is ConnectionState.Connected -> ReadyHero(
            tr("Strap connected", "Strap ligada"),
            listOfNotNull(
                if (bpm != null) tr("Live from your strap.", "Ao vivo da tua strap.") else tr("Waiting for heart rate.", "À espera da frequência cardíaca."),
                connection.batteryPercent?.takeIf { it <= LOW_BATTERY && !connection.charging }?.let { tr("Battery $it%.", "Bateria $it%.") },
            ).joinToString(" "),
            live = true,
        )
        ConnectionState.Connecting, ConnectionState.Authenticating -> ReadyHero(tr("Connecting to the strap", "A ligar à strap"), ANYWAY, live = false)
        ConnectionState.NotPaired -> ReadyHero(tr("No strap paired", "Nenhuma strap emparelhada"), ANYWAY, live = false)
        ConnectionState.Disconnected, is ConnectionState.Failed -> ReadyHero(tr("Strap not connected", "Strap desligada"), ANYWAY, live = false)
    }

    private val ANYWAY: String get() = tr("You can start anyway.", "Podes começar na mesma.")

    /** Under GPS: what it gives, or what's missing without it. */
    fun gpsNote(on: Boolean, sportType: Int): String = when {
        on -> tr("On. Distance, pace and the map.", "Ligado. Distância, ritmo e mapa.")
        sportType in Workouts.ON_FOOT -> tr("Off. Distance from the strap's steps, roughly.", "Desligado. Distância aproximada pelos passos da strap.")
        else -> tr("Off. No distance without GPS.", "Desligado. Sem GPS não há distância.")
    }

    /** The GPS row while the phone's location is off or not allowed. Start still works, without GPS. */
    val LOCATION_OFF: String get() = tr("Location is off", "A localização está desligada")
    val LOCATION_OFF_NOTE: String get() = tr("Turn it on for distance and the map, or start without it.", "Liga-a para teres distância e mapa, ou começa sem ela.")

    /** Under Keep the route. */
    fun routeNote(on: Boolean): String =
        if (on) tr("On. Saved on this phone for the map.", "Ligado. Guardado neste telemóvel para o mapa.") else tr("Off. Only the totals are kept.", "Desligado. Só ficam os totais.")

    /** Start, naming the sport: "Start run", "Start HIIT", and "Start workout" for Other. */
    fun startLabel(sportType: Int): String = when (val name = Workouts.shortName(sportType)) {
        // Other's name, in whichever language it's in.
        Workouts.shortName(OTHER) -> tr("Start workout", "Começar treino")
        else -> tr("Start ", "Começar ") + if (name.any { it.isLowerCase() } && name.drop(1).none { it.isUpperCase() }) name.lowercase() else name
    }

    /** The sport type the picker calls Other. */
    private const val OTHER = 5

    /** The Ready info sheet's meaning. */
    val READY_MEANING: String
        get() = tr(
            "Heart rate comes from the strap every second. GPS is on for outdoor workouts, and you can turn it off.",
            "A frequência cardíaca vem da strap a cada segundo. O GPS fica ligado nos treinos ao ar livre, e podes desligá-lo.",
        )

    /** The Ready info sheet's rows: the strap always, GPS and the route for an [outdoor] sport. */
    fun readyRows(outdoor: Boolean): List<Pair<String, String>> = listOfNotNull(
        tr("Heart rate", "Frequência cardíaca") to tr(
            "The strap sends heart rate every second, with the phone's screen off too. If it drops out or " +
                "isn't connected at the start, recording carries on, and the missing minutes are filled from the strap's own " +
                "history after the next sync.",
            "A strap envia a frequência cardíaca a cada segundo, também com o ecrã do telemóvel desligado. Se falhar ou não " +
                "estiver ligada no início, a gravação continua, e os minutos em falta são preenchidos com o histórico da " +
                "própria strap depois da próxima sincronização.",
        ),
        ("GPS" to tr(
            "Outdoors, GPS uses the phone's location during this workout only, for distance, pace and the map. " +
                "Its switch turns it off for this workout. Map tiles come from OpenFreeMap, which sees roughly where the map " +
                "is. Route only on the map loads no tiles. Without GPS, walks and runs get a distance from the strap's " +
                "steps, marked ≈.",
            "Ao ar livre, o GPS usa a localização do telemóvel só durante este treino, para distância, ritmo e mapa. " +
                "O interruptor desliga-o para este treino. Os mosaicos do mapa vêm do OpenFreeMap, que fica a saber mais ou " +
                "menos onde está o mapa. Só percurso no mapa não carrega mosaicos. Sem GPS, caminhadas e corridas têm uma " +
                "distância pelos passos da strap, marcada com ≈.",
        )).takeIf { outdoor },
        (tr("Keep the route", "Guardar o percurso") to tr(
            "With Keep the route on, the route is saved on this phone for the map. Off, only the " +
                "totals are kept and the route is discarded when the workout ends.",
            "Com Guardar o percurso ligado, o percurso fica guardado neste telemóvel para o mapa. Desligado, só ficam " +
                "os totais e o percurso é apagado quando o treino acaba.",
        )).takeIf { outdoor },
    )

    /** "Zone 3, aerobic" as its two parts, drawn as two text elements. */
    fun zoneTitle(zone: Int): Pair<String, String> = tr("Zone ${zone + 1},", "Zona ${zone + 1},") to ZONE_NAMES[zone].lowercase()

    /** "9 min in this zone". */
    fun zoneTime(seconds: Int): String =
        if (seconds < 60) tr("Under a minute in this zone", "Menos de um minuto nesta zona") else tr("${seconds / 60} min in this zone", "${seconds / 60} min nesta zona")

    /**
     * The line for the latest whole kilometre in [splits], for [SPLIT_LINE_S] after it ended, else null: "Km 3 in 5:42,
     * avg 152". The average is the readings in [history] (time, bpm) over that kilometre, which starts where the one
     * before ended, or at [firstT] for the first; without readings the line leaves it out.
     */
    fun splitLine(splits: List<Route.Split>, history: List<Pair<Long, Int>>, firstT: Long?, now: Long): SplitLine? {
        val i = splits.indexOfLast { it.meters >= KM }
        if (i < 0) return null
        val sp = splits[i]
        // A fix stamped a moment ahead of the phone's clock still shows; one long ago doesn't.
        if (now - sp.endT >= SPLIT_LINE_S || sp.endT - now > SPLIT_LINE_S) return null
        val from = splits.getOrNull(i - 1)?.endT ?: firstT ?: sp.endT
        val avg = averageOver(history, from, sp.endT)
        return SplitLine(i, sp.endT, tr("Km ${i + 1} in ${workoutClock(sp.seconds)}", "Km ${i + 1} em ${workoutClock(sp.seconds)}") + (avg?.let { tr(", avg $it", ", média $it") } ?: ""))
    }

    /** The average of the [readings] (time, bpm) from [from] to [to], whole bpm; null without any. */
    fun averageOver(readings: List<Pair<Long, Int>>, from: Long, to: Long): Int? =
        readings.filter { it.first in from..to }.takeIf { it.isNotEmpty() }?.map { it.second }?.average()?.toInt()

    /**
     * Every reading so far for [splitLine]: the session's [history], refreshed every few seconds, then the [recent]
     * live readings newer than it, so a kilometre's last seconds count.
     */
    fun readings(history: List<Pair<Long, Int>>, recent: List<Pair<Long, Int>>): List<Pair<Long, Int>> {
        val last = history.lastOrNull()?.first ?: return recent
        return history + recent.filter { it.first > last }
    }

    /** The index of the latest whole kilometre, or -1: the splits already done when the live screen opens. */
    fun wholeKms(splits: List<Route.Split>): Int = splits.indexOfLast { it.meters >= KM }

    /** Whether [line] is a kilometre not ticked for yet ([lastTicked] its index): one tick per split. */
    fun splitTick(line: SplitLine?, lastTicked: Int): Boolean = line != null && line.index > lastTicked

    /** Whether going from zone [previous] to [current] ticks: a change between two readings, not the first one. */
    fun zoneTick(previous: Int?, current: Int?): Boolean = previous != null && current != null && previous != current

    /**
     * How high the live heart card's light reaches for [zone] (spec 2.5): about a third of the card in zone 1, all of it
     * in zone 5. Null without a reading, for the card's usual light.
     */
    fun zoneLightLevel(zone: Int?): Float? = zone?.let { 0.35f + 0.65f * it.coerceIn(0, Workouts.ZONES - 1) / (Workouts.ZONES - 1) }
}
