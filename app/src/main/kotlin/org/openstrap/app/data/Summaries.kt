package org.openstrap.app.data

import org.openstrap.app.data.db.ActivityMinute
import org.openstrap.app.data.db.HeartRateSample
import org.openstrap.app.data.db.SleepStage

/** Pure aggregation helpers for the dashboard; kept free of Android types so they're unit-testable. */
object Summaries {
    /**
     * The day's steps from its minutes. A minute over [Recovery.MAX_MINUTE_STEPS] is a sentinel or an error (the
     * strap's byte tops out at 255), so it counts as none here, as in every other sum of steps: the week and month
     * charts, the reports and the energy estimate.
     */
    fun totalSteps(minutes: List<ActivityMinute>): Int = minutes.sumOf { stepsOf(it) }

    /** A minute's steps, a sentinel or an error counted as none ([totalSteps]). */
    fun stepsOf(m: ActivityMinute): Int = m.steps.takeIf { it in 0..Recovery.MAX_MINUTE_STEPS } ?: 0

    /** 24 buckets of steps for the day starting at [dayStart]. */
    fun hourlySteps(minutes: List<ActivityMinute>, dayStart: Long): List<Float> {
        val buckets = FloatArray(24)
        for (m in minutes) {
            val hour = ((m.timestamp - dayStart) / 3600).toInt()
            if (hour in 0..23) buckets[hour] += stepsOf(m).toFloat()
        }
        return buckets.toList()
    }

    /**
     * The day's running total at the end of each hour from its [hourly] steps, the Steps card's line: null for the
     * hours after [upTo] (the hour now, on today), every hour on a past day ([upTo] null).
     */
    fun runningSteps(hourly: List<Float>, upTo: Int?): List<Float?> {
        var sum = 0f
        return hourly.mapIndexed { i, v -> sum += v; if (upTo != null && i > upTo) null else sum }
    }

    /** Hours of the day with at least [threshold] steps — a simple "stay active" measure. */
    fun activeHours(hourly: List<Float>, threshold: Int = 250): Int = hourly.count { it >= threshold }

    /** Merges heart rate from dedicated HR samples and per-minute activity, preferring HR samples. */
    fun heartRateSeries(hr: List<HeartRateSample>, minutes: List<ActivityMinute>): List<Pair<Long, Int>> {
        val byTime = sortedMapOf<Long, Int>()
        for (m in minutes) if (m.heartRate != null && m.heartRate in 30..230) byTime[m.timestamp] = m.heartRate
        for (s in hr) if (s.bpm in 30..230) byTime[s.timestamp] = s.bpm
        return byTime.map { it.key to it.value }
    }

    /**
     * The lowest and highest minute of the day's [series] (ascending), as a minute-by-minute strap screen reads the day:
     * the charts' 5-minute averages flatten a workout's peak and a night's low. A minute with no neighbour within
     * [Recovery.ARTEFACT_JUMP_BPM] of it is an artefact and is skipped. The strap's own daily maximum [strapMax], read
     * second by second, raises the high when it's above the highest minute by up to [STRAP_MAX_LEAD_BPM]: a sprint's
     * seconds peak above their minute's average, while a spike far above every minute is noise. Null without readings.
     */
    fun heartRange(series: List<Pair<Long, Int>>, strapMax: Int? = null): IntRange? {
        val low = steadyExtreme(series, lowest = true) ?: return null
        val high = steadyExtreme(series, lowest = false) ?: return null
        val raised = strapMax?.takeIf { it in Recovery.HR_MIN_BPM..Recovery.HR_MAX_BPM && it in high.second + 1..high.second + STRAP_MAX_LEAD_BPM }
        return low.second..(raised ?: high.second)
    }

    /** The lowest minute of [series] (ascending) and when, artefacts skipped as [heartRange] skips them. */
    fun lowestMinute(series: List<Pair<Long, Int>>): Pair<Long, Int>? = steadyExtreme(series, lowest = true)

    /** How far the strap's per-second daily maximum may sit above the highest minute and still count. */
    const val STRAP_MAX_LEAD_BPM = 25

    /** The lowest or highest minute with a neighbouring minute close to it; any minute when none has one. */
    private fun steadyExtreme(series: List<Pair<Long, Int>>, lowest: Boolean): Pair<Long, Int>? {
        val valid = series.filter { it.second in Recovery.HR_MIN_BPM..Recovery.HR_MAX_BPM }
        if (valid.isEmpty()) return null
        val steady = valid.indices.filter { i ->
            val (t, bpm) = valid[i]
            listOfNotNull(valid.getOrNull(i - 1), valid.getOrNull(i + 1))
                .any { (u, v) -> kotlin.math.abs(u - t) <= 60 && kotlin.math.abs(v - bpm) <= Recovery.ARTEFACT_JUMP_BPM }
        }.map { valid[it] }.ifEmpty { valid }
        return if (lowest) steady.minByOrNull { it.second } else steady.maxByOrNull { it.second }
    }

    /**
     * [days] with [today]'s total raised to [total] when that's higher (or added when today has no row yet): today's
     * steps as Today counts them, the strap's live count included, rather than only the minutes synced so far.
     */
    fun withToday(days: List<DayStat>, today: java.time.LocalDate, total: Float): List<DayStat> {
        val row = days.firstOrNull { it.date == today }
        return when {
            row == null -> (days + DayStat(today, total, 0f, 0f, 1)).sortedBy { it.date }
            row.value >= total -> days
            else -> days.map { if (it.date == today) it.copy(value = total) else it }
        }
    }

    /** Averages a series into fixed buckets (e.g. 5 minutes) for a calmer chart line. */
    fun bucketAverage(series: List<Pair<Long, Int>>, bucketSeconds: Long = 300): List<Pair<Long, Int>> =
        series.groupBy { it.first / bucketSeconds * bucketSeconds }
            .map { (t, v) -> t to Math.round(v.map { it.second }.average()).toInt() }
            .sortedBy { it.first }

    /**
     * Lowest 30-minute average heart rate — a reasonable stand-in for resting HR when the strap's own
     * value isn't available.
     */
    fun estimateRestingHr(series: List<Pair<Long, Int>>, windowSeconds: Long = 30 * 60): Int? {
        if (series.isEmpty()) return null
        var best: Double? = null
        var startIdx = 0
        var sum = 0L
        for (endIdx in series.indices) {
            sum += series[endIdx].second
            while (series[endIdx].first - series[startIdx].first > windowSeconds) {
                sum -= series[startIdx].second
                startIdx++
            }
            val count = endIdx - startIdx + 1
            val coversWindow = series[endIdx].first - series[startIdx].first >= windowSeconds * 0.8
            if (count >= 5 && coversWindow) {
                val avg = sum.toDouble() / count
                if (best == null || avg < best) best = avg
            }
        }
        return best?.let { Math.round(it).toInt() }
    }

    data class SleepBreakdown(
        val asleepSeconds: Long,
        val awakeSeconds: Long,
        val byStage: Map<Int, Long>,
    )

    fun sleepBreakdown(stages: List<SleepStage>): SleepBreakdown {
        val byStage = stages.groupBy { it.stage }.mapValues { (_, v) -> v.sumOf { it.end - it.start } }
        val awake = byStage[SleepStage.AWAKE] ?: 0L
        return SleepBreakdown(byStage.values.sum() - awake, awake, byStage)
    }
}
