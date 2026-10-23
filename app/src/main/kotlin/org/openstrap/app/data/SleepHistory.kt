package org.openstrap.app.data

import org.openstrap.app.data.db.SleepSession
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Night-by-night sleep: which sleep belongs to which night, and the week's usual sleep times. How regular the week was
 * is the Sleep Regularity Index ([SleepRegularity]); the bedtime/wake-spread score this file used to compute is gone.
 */
object SleepHistory {
    /** Nights the Sleep screen's week looks back over. */
    const val WEEK_NIGHTS = 7

    /** The night a sleep belongs to: the day it ended on, counting until 18:00 (see DayWindow.nightEndingOn). */
    fun nightOf(end: Long, zone: ZoneId): LocalDate = Instant.ofEpochSecond(end + 6 * 3600).atZone(zone).toLocalDate()

    /** The main sleep of each night: the longest session that isn't a nap. */
    fun mainSleeps(sessions: List<SleepSession>, zone: ZoneId): Map<LocalDate, SleepSession> =
        sessions.filter { !it.nap }
            .groupBy { nightOf(it.end, zone) }
            .mapValues { (_, s) -> s.maxBy { it.end - it.start } }

    /** Time asleep from the strap's stage totals, or the whole session when it didn't send them. */
    fun asleepSeconds(s: SleepSession): Long {
        val staged = listOfNotNull(s.deepMinutes, s.lightMinutes, s.remMinutes)
        return if (staged.isEmpty()) s.end - s.start else staged.sum() * 60L
    }

    /**
     * The average fall-asleep and wake-up time over the nights up to [end]. [bedtime] and [wake] are average minutes
     * past midnight (bedtime can be past 24 h · 60 when it's after midnight; use [clockMinutes] to print it).
     */
    data class UsualTimes(val bedtime: Int, val wake: Int, val nights: Int)

    /**
     * Usual times over the [WEEK_NIGHTS] nights ending on [end], or null when none of them has sleep. Bedtime is
     * averaged from noon and wake time from midnight, so neither wraps around in a normal night.
     */
    fun usualTimes(nights: Map<LocalDate, SleepSession>, end: LocalDate, zone: ZoneId): UsualTimes? {
        val window = (0 until WEEK_NIGHTS).mapNotNull { nights[end.minusDays(it.toLong())] }
        if (window.isEmpty()) return null
        val bed = window.map { minutesFrom(it.start, zone, noon = true) }
        val wake = window.map { minutesFrom(it.end, zone, noon = false) }
        return UsualTimes(bed.average().roundToInt() + 12 * 60, wake.average().roundToInt(), window.size)
    }

    /** [minutes] past midnight as a time of day, wrapped into 0 until 24 h. */
    fun clockMinutes(minutes: Int): Int = Math.floorMod(minutes, 24 * 60)

    private fun minutesFrom(epochSeconds: Long, zone: ZoneId, noon: Boolean): Double {
        val t = Instant.ofEpochSecond(epochSeconds).atZone(zone).toLocalTime()
        val m = t.hour * 60 + t.minute
        return (if (noon) Math.floorMod(m - 12 * 60, 24 * 60) else m).toDouble()
    }
}
