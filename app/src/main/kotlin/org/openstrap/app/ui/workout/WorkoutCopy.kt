package org.openstrap.app.ui.workout

import org.openstrap.app.data.Route
import org.openstrap.app.data.Workouts
import org.openstrap.app.device.ConnectionState
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

    /** The one line on Ready that confirms the strap: "Strap connected. 72 bpm." */
    fun readyLine(connection: ConnectionState, bpm: Int?): String = when (connection) {
        is ConnectionState.Connected -> listOfNotNull(
            "Strap connected.",
            bpm?.let { "$it bpm." } ?: "Waiting for heart rate.",
            connection.batteryPercent?.takeIf { it <= LOW_BATTERY && !connection.charging }?.let { "Battery $it%." },
        ).joinToString(" ")
        ConnectionState.Connecting, ConnectionState.Authenticating -> "Connecting to the strap."
        ConnectionState.NotPaired -> "No strap paired. You can start anyway."
        ConnectionState.Disconnected, is ConnectionState.Failed -> "Strap not connected. You can start anyway."
    }

    /** Under Use GPS: what it gives, or what's missing without it. */
    fun gpsNote(on: Boolean, sportType: Int): String = when {
        on -> "For distance, pace and the map, during this workout only."
        sportType in Workouts.ON_FOOT -> "Off. Distance from the strap's steps, roughly."
        else -> "Off. No distance without GPS."
    }

    /** Under Keep route on this phone. */
    fun routeNote(on: Boolean): String = if (on) "Saved on this phone for the map and export." else "Off. Only the totals are kept."

    /** The Ready info sheet's meaning. */
    const val READY_MEANING = "Heart rate comes from the strap every second. GPS only when you turn it on."

    /** The Ready info sheet's rows: the strap always, GPS and the route for an [outdoor] sport. */
    fun readyRows(outdoor: Boolean): List<Pair<String, String>> = listOfNotNull(
        "Heart rate" to "The strap sends heart rate every second, with the phone's screen off too. If it drops out or " +
            "isn't connected at the start, recording carries on, and the missing minutes are filled from the strap's own " +
            "history after the next sync.",
        ("GPS" to "Use GPS turns on the phone's location for this workout only, for distance, pace and the map. Map " +
            "tiles come from OpenFreeMap, which sees roughly where the map is. Route only on the map loads no tiles. " +
            "Without GPS, walks and runs get a distance from the strap's steps, marked ≈.").takeIf { outdoor },
        ("Keep route" to "With Keep route on, the route is saved on this phone for the map and export. Off, only the " +
            "totals are kept and the route is discarded when the workout ends.").takeIf { outdoor },
    )

    /** "Zone 3, aerobic" as its two parts, drawn as two text elements. */
    fun zoneTitle(zone: Int): Pair<String, String> = "Zone ${zone + 1}," to ZONE_NAMES[zone].lowercase()

    /** "9 min in this zone". */
    fun zoneTime(seconds: Int): String = if (seconds < 60) "Under a minute in this zone" else "${seconds / 60} min in this zone"

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
        return SplitLine(i, sp.endT, "Km ${i + 1} in ${workoutClock(sp.seconds)}" + (avg?.let { ", avg $it" } ?: ""))
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
