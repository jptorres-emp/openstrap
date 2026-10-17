package org.openstrap.app.data

import org.openstrap.app.data.db.SleepSession
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Night-by-night sleep: which sleep belongs to which night, and how regular the timing is. */
object SleepHistory {
    /** Nights the regularity score looks back over. */
    const val REGULARITY_NIGHTS = 7

    /** Nights with sleep needed in that window before the score means anything (Zepp also waits for five). */
    const val MIN_REGULARITY_NIGHTS = 5

    /** Minutes of spread (average of bedtime and wake-time SD) that take the score from 100 to 0. */
    private const val ZERO_SCORE_SPREAD_MINUTES = 120.0

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

    enum class Band(val label: String) {
        VeryRegular("Very regular"), Regular("Regular"), FairlyRegular("Fairly regular"), Irregular("Irregular");

        companion object {
            /** Zepp's bands: 90–100, 80–89, 60–79, 0–59. */
            fun of(score: Int) = when {
                score >= 90 -> VeryRegular
                score >= 80 -> Regular
                score >= 60 -> FairlyRegular
                else -> Irregular
            }
        }
    }

    /**
     * How regular the timing was over the nights up to [end]. [bedtime] and [wake] are average minutes past
     * midnight (bedtime can be past 24 h · 60 when it's after midnight; use [clockMinutes] to print it).
     */
    data class Regularity(val score: Int, val bedtime: Int, val wake: Int, val nights: Int) {
        val band get() = Band.of(score)
    }

    /**
     * Regularity over the [REGULARITY_NIGHTS] nights ending on [end], or null with fewer than
     * [MIN_REGULARITY_NIGHTS]. The score falls linearly with the spread (standard deviation) of bedtime and
     * wake time: 0 min spread is 100, 15 min about 88, 30 min 75, an hour 50.
     */
    fun regularity(nights: Map<LocalDate, SleepSession>, end: LocalDate, zone: ZoneId): Regularity? {
        val window = (0 until REGULARITY_NIGHTS).mapNotNull { nights[end.minusDays(it.toLong())] }
        if (window.size < MIN_REGULARITY_NIGHTS) return null
        // Bedtime counted from noon and wake time from midnight, so neither wraps around in a normal night.
        val bed = window.map { minutesFrom(it.start, zone, noon = true) }
        val wake = window.map { minutesFrom(it.end, zone, noon = false) }
        val spread = (sd(bed) + sd(wake)) / 2
        val score = (100 * (1 - spread / ZERO_SCORE_SPREAD_MINUTES)).roundToInt().coerceIn(0, 100)
        return Regularity(score, bed.average().roundToInt() + 12 * 60, wake.average().roundToInt(), window.size)
    }

    /** [minutes] past midnight as a time of day, wrapped into 0 until 24 h. */
    fun clockMinutes(minutes: Int): Int = Math.floorMod(minutes, 24 * 60)

    private fun minutesFrom(epochSeconds: Long, zone: ZoneId, noon: Boolean): Double {
        val t = Instant.ofEpochSecond(epochSeconds).atZone(zone).toLocalTime()
        val m = t.hour * 60 + t.minute
        return (if (noon) Math.floorMod(m - 12 * 60, 24 * 60) else m).toDouble()
    }

    private fun sd(values: List<Double>): Double {
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
    }
}
