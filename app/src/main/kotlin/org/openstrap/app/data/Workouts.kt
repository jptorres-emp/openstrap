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
    /** Recovery time in minutes: the strap's sport effect, or OpenStrap's own for a phone workout ([WorkoutEffect]). */
    val recoveryMinutes: Int? = effect?.recoveryMinutes,
) {
    val durationSeconds: Long get() = end - shownStart

    /** Recovery HR only says something about fitness when you stopped from a real effort. */
    val recoveryIsMeaningful: Boolean get() = endHeartRate != null && endHeartRate * 100 >= maxHeartRate * MIN_EFFORT_PCT

    private companion object {
        const val MIN_EFFORT_PCT = 65
    }
    val sportName: String get() = Workouts.sportName(workout.sportType)
    /** The strap recognised this workout by itself rather than being started on it (codemap 05 §8). */
    val autoDetected: Boolean get() = !fromPhone && workout.subtype == Workouts.SUBTYPE_AUTO_DETECTED
    /** You started it, on the phone or the strap: not one the strap noticed or filed as "Activity" by itself. */
    val startedByYou: Boolean get() = !autoDetected && workout.sportType != Workouts.GENERIC_ACTIVITY
}

object Workouts {
    /** The strap keeps recording HR this long after a workout, for recovery heart rate. */
    const val RECOVERY_TAIL_S = 180L
    /** A sport effect belongs to a workout when it ends within this long of the workout's start. */
    const val EFFECT_WINDOW_S = 12 * 3600L
    const val ZONES = 5
    /** Summary `Type.subtype` for a workout the strap recognised on its own; 6 is a phone workout it mirrored (codemap 05 §8). */
    const val SUBTYPE_AUTO_DETECTED = 1
    /** Older auto-detected workouts arrive on a first sync of the strap's backlog and get no notification. */
    const val AUTO_DETECT_NOTIFY_WINDOW_S = 24 * 3600L

    /**
     * Whether a strap workout just stored gets Zepp's "detected" notification (codemap 05 §8): only one the strap
     * auto-detected, not stored before (the strap re-sends a workout whose detail failed), and ended within a day.
     */
    fun notifyAutoDetected(w: Workout, storedBefore: Boolean, now: Long): Boolean =
        w.subtype == SUBTYPE_AUTO_DETECTED && !storedBefore && now - w.end <= AUTO_DETECT_NOTIFY_WINDOW_S

    /**
     * Ties a workout to its HR stream and sport effect. [maxHeartRate] comes from the strap's effect when it
     * has one, then [fallbackMaxHr] (the app's usual max HR, [RecoveryEngine.maxHeartRate]).
     */
    fun analyse(w: Workout, hr: List<WorkoutHeartRate>, effects: List<SportEffectRow>, fallbackMaxHr: Int): WorkoutAnalysis {
        val effect = effectOf(w, effects)
        val lastSample = hr.maxOfOrNull { it.timestamp }
        val end = endOf(w, lastSample, effect)
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

    /** The sport effect shown with [w]: the first that ends after its start, within [EFFECT_WINDOW_S]. */
    fun effectOf(w: Workout, effects: List<SportEffectRow>): SportEffectRow? =
        effects.filter { it.end > w.start && it.end <= w.start + EFFECT_WINDOW_S }.minByOrNull { it.end }

    /** [w]'s real end: its sport effect's, else three minutes before its last HR sample ([lastSample]), else its stored end. */
    fun endOf(w: Workout, lastSample: Long?, effect: SportEffectRow?): Long = effect?.end
        ?: lastSample?.let { it - RECOVERY_TAIL_S }?.takeIf { it > w.start }?.let { maxOf(it, w.end) }
        ?: w.end

    /**
     * The sport effect deleted along with [w]: the one shown with it, unless another workout starts ([otherStarts])
     * before that effect ends. A strap workout with no effect of its own would otherwise take the next one's.
     */
    fun ownEffect(w: Workout, effects: List<SportEffectRow>, otherStarts: List<Long>): SportEffectRow? {
        val e = effectOf(w, effects) ?: return null
        return e.takeIf { otherStarts.none { s -> s > w.start && s < e.end } }
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
    val sports: List<Pair<Int, String>> by lazy {
        SPORT_NAMES.entries.filter { it.key !in HIDDEN }.distinctBy { it.value }.map { it.key to it.value }.sortedBy { it.second }
    }

    /**
     * Sports kept for naming workouts already recorded (by the strap, Zepp or an import) but not offered for new ones:
     * each is another one under a second name, or the strap's own generic "Activity".
     */
    val HIDDEN = setOf(143, 40, 224, 55, 10, 57, 58, 51, 109, 24, 226, 16, 223)

    /** The picker's sections, each sport once, most-done kinds first. */
    val SECTIONS: List<Pair<String, List<Int>>> = listOf(
        "Running & walking" to listOf(1, 2, 11, 3, 15, 215, 225, 108),
        "Gym" to listOf(52, 50, 49, 130, 222, 9, 21, 5),
        "Cycling" to listOf(4, 8),
        "Water" to listOf(6, 7, 23),
        "Mind & body" to listOf(60, 61, 53, 76),
        "Racket & ball" to listOf(17, 205, 204, 92, 89, 18, 85, 88, 83, 97),
        "Outdoors" to listOf(12, 70),
    )

    /** Sports done outdoors, where the ready screen offers GPS. */
    val OUTDOOR = setOf(1, 3, 4, 11, 12, 15, 215, 224, 225)

    /**
     * Sports done on your feet, where steps times a stride make a distance (≈) without GPS: runs, walks, hikes. On a
     * bike, in water or on a mat, steps say nothing about distance, so those show none.
     */
    val ON_FOOT = setOf(1, 2, 3, 11, 15, 40, 215, 224, 225)

    /** Offered before any workout has been recorded: outdoor run, walk, strength training. */
    val DEFAULT_SPORTS = listOf(1, 3, 52)

    /** "Activity": what the strap files movement it logged by itself under; a record, not a sport anyone starts. */
    const val GENERIC_ACTIVITY = 223

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
