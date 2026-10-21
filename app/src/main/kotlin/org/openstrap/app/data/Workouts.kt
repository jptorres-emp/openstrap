package org.openstrap.app.data

import org.openstrap.app.data.db.SportEffectRow
import org.openstrap.app.data.db.Workout
import org.openstrap.app.data.db.WorkoutHeartRate

/** A workout with what the strap recorded around it, worked out for display. */
data class WorkoutAnalysis(
    val workout: Workout,
    /** Real end: the strap's sport effect, else three minutes before the last HR sample, else start + exercise time. */
    val end: Long,
    val heartRate: List<WorkoutHeartRate>,
    val effect: SportEffectRow?,
    /** HR at the end minus HR one and two minutes later (bpm); null without the recovery recording. */
    val recovery60: Int?,
    val recovery120: Int?,
    /** HR when the workout ended, the starting point of the recovery figures. */
    val endHeartRate: Int?,
    /** Seconds in each of the five zones (50–60 … 90–100 % of [maxHeartRate]) during the workout. */
    val zoneSeconds: List<Int>,
    val maxHeartRate: Int,
    /** Recorded by OpenStrap from live heart rate rather than by the strap ([WorkoutRecorder]). */
    val fromPhone: Boolean = false,
    /** Minutes the phone missed that the strap's history filled in. */
    val historyMinutes: Int = 0,
    /** Those minutes as runs of epoch seconds, each from its first minute's start to its last minute's end − 1. */
    val historyRanges: List<LongRange> = emptyList(),
    /** Where the workout is shown from: a phone workout's trimmed start, else its start. */
    val shownStart: Long = workout.start,
    /** A phone workout's end before the app or a trim moved it earlier; null when it ends where it was recorded. */
    val recordedEnd: Long? = null,
) {
    val durationSeconds: Long get() = end - shownStart

    /** Recovery HR only says something about fitness when you stopped from a real effort. */
    val recoveryIsMeaningful: Boolean get() = endHeartRate != null && endHeartRate * 100 >= maxHeartRate * MIN_EFFORT_PCT

    private companion object {
        const val MIN_EFFORT_PCT = 65
    }
    val sportName: String get() = Workouts.sportName(workout.sportType)
}

object Workouts {
    /** The strap keeps recording HR this long after a workout, for recovery heart rate. */
    const val RECOVERY_TAIL_S = 180L
    /** A sport effect belongs to a workout when it ends within this long of the workout's start. */
    private const val EFFECT_WINDOW_S = 12 * 3600L
    const val ZONES = 5

    /**
     * Ties a workout to its HR stream and sport effect. [maxHeartRate] comes from the strap's effect when it
     * has one, then [fallbackMaxHr] (the app's usual max HR, [RecoveryEngine.maxHeartRate]).
     */
    fun analyse(w: Workout, hr: List<WorkoutHeartRate>, effects: List<SportEffectRow>, fallbackMaxHr: Int): WorkoutAnalysis {
        val effect = effects.filter { it.end > w.start && it.end <= w.start + EFFECT_WINDOW_S }.minByOrNull { it.end }
        val lastSample = hr.maxOfOrNull { it.timestamp }
        val end = effect?.end
            ?: lastSample?.let { it - RECOVERY_TAIL_S }?.takeIf { it > w.start }?.let { maxOf(it, w.end) }
            ?: w.end
        val maxHr = effect?.maximalHeartRate ?: fallbackMaxHr
        val during = hr.filter { it.timestamp in w.start..end }
        val zones = IntArray(ZONES)
        during.zipWithNext().forEach { (a, b) ->
            val z = zoneOf(a.bpm, maxHr) ?: return@forEach
            zones[z] += (b.timestamp - a.timestamp).coerceAtMost(5).toInt()
        }
        val hasTail = lastSample != null && lastSample >= end + 60
        return WorkoutAnalysis(
            w, end, hr, effect,
            recovery60 = if (hasTail) recovery(hr, end, 60) else null,
            recovery120 = if (lastSample != null && lastSample >= end + 120) recovery(hr, end, 120) else null,
            endHeartRate = medianAt(hr, end),
            zoneSeconds = zones.toList(),
            maxHeartRate = maxHr,
        )
    }

    /** Zone 0..4 for 50–60, 60–70, 70–80, 80–90 and 90+ % of max; null below 50 %. */
    fun zoneOf(bpm: Int, maxHr: Int): Int? {
        val pct = bpm * 100 / maxHr
        return if (pct < 50) null else ((pct - 50) / 10).coerceAtMost(ZONES - 1)
    }

    /** HR drop from the end to [after] seconds later, each taken as a 5-second median to ride out noise. */
    private fun recovery(hr: List<WorkoutHeartRate>, end: Long, after: Long): Int? {
        val a = medianAt(hr, end) ?: return null
        val b = medianAt(hr, end + after) ?: return null
        return a - b
    }

    private fun medianAt(hr: List<WorkoutHeartRate>, t: Long): Int? =
        hr.filter { it.timestamp in t - 2..t + 2 }.map { it.bpm }.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }

    /** Zepp's sport type ids (Type.type in the summary) for the sports a strap is likely to record. */
    fun sportName(type: Int): String = SPORT_NAMES[type] ?: "Workout"

    /** Every sport a workout can be started as, by name, one id per name (Zepp has two "Free training"). */
    val sports: List<Pair<Int, String>> by lazy { SPORT_NAMES.entries.distinctBy { it.value }.map { it.key to it.value }.sortedBy { it.second } }

    /** Sports done outdoors, where the ready screen offers GPS. */
    val OUTDOOR = setOf(1, 3, 4, 11, 12, 15, 215, 224, 225)

    /** Offered before any workout has been recorded: outdoor run, walk, strength training. */
    val DEFAULT_SPORTS = listOf(1, 3, 52)

    private val SPORT_NAMES = mapOf(
        1 to "Outdoor run", 2 to "Treadmill", 3 to "Walk", 4 to "Outdoor cycling", 5 to "Free training",
        6 to "Pool swim", 7 to "Open water swim", 8 to "Indoor cycling", 9 to "Elliptical", 10 to "Climbing",
        11 to "Trail run", 12 to "Skiing", 15 to "Hiking", 16 to "Free training", 17 to "Tennis", 18 to "Soccer",
        21 to "Jump rope", 23 to "Rowing machine", 24 to "Indoor fitness", 40 to "Indoor walk", 49 to "HIIT",
        50 to "Core training", 51 to "Mixed aerobic", 52 to "Strength training", 53 to "Stretching",
        55 to "Flexibility", 57 to "Step training", 58 to "Stair stepper", 60 to "Yoga", 61 to "Pilates",
        70 to "Rock climbing", 76 to "Dance", 83 to "Golf", 85 to "Basketball", 88 to "Volleyball",
        89 to "Table tennis", 92 to "Badminton", 97 to "Boxing", 108 to "Stair climbing", 109 to "Aerobics",
        130 to "Cross-training", 143 to "Spinning", 204 to "Pickleball", 205 to "Padel", 215 to "Dog walking",
        222 to "HYROX training", 223 to "Activity", 224 to "Mountain hike", 225 to "Rucking", 226 to "Hybrid training",
    )
}
