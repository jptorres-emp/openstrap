package org.openstrap.app.data

import org.openstrap.app.data.db.AppWorkout
import org.openstrap.app.data.db.AppWorkoutHeartRate
import org.openstrap.app.data.db.Workout
import org.openstrap.app.data.db.WorkoutHeartRate

/**
 * A workout recorded on the phone, worked out in pure Kotlin like [Recovery]: which minutes the phone missed, how the
 * strap's history fills them, and the figures shown afterwards. The phone records live heart rate about once a second;
 * after the next sync the strap's per-minute history fills every whole minute with no live sample, so a disconnect or a
 * killed app leaves a coarser stretch, never a hole.
 */
object WorkoutRecorder {
    /**
     * The zone shown live, 0..4: below 60 % of max is zone 1 (anything slower is easy), then 10 % steps, as
     * [Workouts.zoneOf]. Null for no reading.
     */
    fun liveZone(bpm: Int?, maxHr: Int): Int? = bpm?.let { Workouts.zoneOf(it, maxHr) ?: 0 }

    /** Where [bpm] sits along the five zones, 0..1: 50 % of max at the left, each zone a fifth. */
    fun zonePosition(bpm: Int, maxHr: Int): Float {
        val pct = bpm * 100f / maxHr
        return ((pct - 50f) / 50f).coerceIn(0f, 1f)
    }

    /** Seconds in each live zone over [samples] (time, bpm, ascending); a gap counts up to [MAX_STEP_S]. */
    fun liveZoneSeconds(samples: List<Pair<Long, Int>>, maxHr: Int): IntArray {
        val out = IntArray(Workouts.ZONES)
        samples.zipWithNext().forEach { (a, b) -> out[liveZone(a.second, maxHr)!!] += (b.first - a.first).coerceIn(0, MAX_STEP_S).toInt() }
        return out
    }

    const val MAX_STEP_S = 5L

    /** Live readings outside this range are the sensor settling or off the skin, not a heart rate. */
    fun isValid(bpm: Int) = bpm in 30..240

    /** A history minute is drawn as samples this far apart, so charts and zone time treat it like live data. */
    private const val HISTORY_STEP_S = 5L

    /**
     * Minute starts (epoch seconds, on the minute) wholly inside [start, end) and outside every pause, with no live
     * sample in them: the minutes gap filling looks up. Minutes cut by the start, the end or a pause are left alone.
     */
    fun missingMinutes(start: Long, end: Long, pauses: List<LongRange>, live: Collection<Long>): List<Long> {
        val covered = live.mapTo(HashSet()) { Math.floorDiv(it, 60L) * 60 }
        val out = mutableListOf<Long>()
        var m = Math.floorDiv(start + 59, 60L) * 60
        while (m + 60 <= end) {
            if (m !in covered && pauses.none { it.first < m + 60 && it.last > m }) out += m
            m += 60
        }
        return out
    }

    /**
     * Rows for [missing] minutes from the strap's history: its per-second heart rate averaged for the minute
     * ([secondAverages], the heart_rate table) when it has one, else the minute's own record ([minuteRecords],
     * activity_minute). Both keyed by minute start. Minutes the strap has nothing for stay missing.
     */
    fun fillFromHistory(
        workoutStart: Long,
        missing: List<Long>,
        secondAverages: Map<Long, Int>,
        minuteRecords: Map<Long, Int>,
    ): List<AppWorkoutHeartRate> = missing.mapNotNull { m ->
        val bpm = (secondAverages[m] ?: minuteRecords[m])?.takeIf(::isValid) ?: return@mapNotNull null
        AppWorkoutHeartRate(workoutStart, m, bpm, fromHistory = true)
    }

    /** Each history minute spread over [HISTORY_STEP_S] samples across its minute; live samples as they are. */
    fun expand(hr: List<AppWorkoutHeartRate>): List<WorkoutHeartRate> = hr.flatMap { s ->
        if (!s.fromHistory) listOf(WorkoutHeartRate(s.workoutStart, s.timestamp, s.bpm))
        else (0 until 60 step HISTORY_STEP_S.toInt()).map { WorkoutHeartRate(s.workoutStart, s.timestamp + it, s.bpm) }
    }.sortedBy { it.timestamp }

    data class Stats(val average: Int, val max: Int, val min: Int)

    /** Time-weighted: a live sample stands for a second, a history row for its minute. Null without samples. */
    fun stats(hr: List<AppWorkoutHeartRate>): Stats? {
        if (hr.isEmpty()) return null
        var sum = 0L
        var weight = 0L
        hr.forEach { s ->
            val w = if (s.fromHistory) 60L else 1L
            sum += s.bpm * w
            weight += w
        }
        return Stats(Math.round(sum.toDouble() / weight).toInt(), hr.maxOf { it.bpm }, hr.minOf { it.bpm })
    }

    /** Seconds actually moving: start to end (or [now]) less every pause. */
    fun activeSeconds(w: AppWorkout, now: Long): Long = ((w.end ?: now) - w.start - w.pausedSeconds(w.end ?: now)).coerceAtLeast(0)

    /** The phone workout in the strap workout's shape, so lists and the detail screen show both alike. */
    fun toWorkout(w: AppWorkout, now: Long, stats: Stats? = null) = Workout(
        start = w.start,
        end = w.end ?: now,
        sportType = w.sportType,
        exerciseSeconds = activeSeconds(w, now).toInt(),
        calories = null,
        averageHeartRate = stats?.average ?: w.averageHeartRate,
        maxHeartRate = stats?.max ?: w.maxHeartRate,
        minHeartRate = stats?.min,
        aerobicTrainingEffect = null,
        anaerobicTrainingEffect = null,
        trainingLoad = null,
        vo2max = null,
        steps = null,
    )

    /** A phone workout analysed like a strap one; [hr] empty gives a list row. */
    fun analyse(w: AppWorkout, hr: List<AppWorkoutHeartRate>, maxHeartRate: Int, now: Long = System.currentTimeMillis() / 1000): WorkoutAnalysis =
        Workouts.analyse(toWorkout(w, now, stats(hr)), expand(hr), emptyList(), maxHeartRate)
            .copy(fromPhone = true, historyMinutes = hr.count { it.fromHistory }, historyRanges = historyRanges(hr))

    /** Consecutive history minutes joined into runs, for marking them on a chart. */
    fun historyRanges(hr: List<AppWorkoutHeartRate>): List<LongRange> {
        val out = mutableListOf<LongRange>()
        hr.filter { it.fromHistory }.map { it.timestamp }.sorted().forEach { m ->
            val last = out.lastOrNull()
            if (last != null && last.last + 1 == m) out[out.lastIndex] = last.first..(m + 59) else out += m..(m + 59)
        }
        return out
    }
}
