package org.openstrap.app.data

import org.openstrap.app.data.db.ActivityMinute
import org.openstrap.app.data.db.HeartRateSample
import org.openstrap.app.data.db.SleepStage

/** Pure aggregation helpers for the dashboard; kept free of Android types so they're unit-testable. */
object Summaries {
    fun totalSteps(minutes: List<ActivityMinute>): Int = minutes.sumOf { it.steps }

    /** 24 buckets of steps for the day starting at [dayStart]. */
    fun hourlySteps(minutes: List<ActivityMinute>, dayStart: Long): List<Float> {
        val buckets = FloatArray(24)
        for (m in minutes) {
            val hour = ((m.timestamp - dayStart) / 3600).toInt()
            if (hour in 0..23) buckets[hour] += m.steps.toFloat()
        }
        return buckets.toList()
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
