package org.openstrap.app.ui.dashboard

import androidx.compose.runtime.saveable.Saver
import org.openstrap.app.data.DailyMetric

/** A metric with its own detail screen: its title, unit, decimals shown, and the per-day series behind it. */
enum class Metric(val title: String, val unit: String, val decimals: Int, val daily: DailyMetric) {
    HeartRate("Heart rate", "bpm", 0, DailyMetric.HeartRate),
    RestingHr("Resting heart rate", "bpm", 0, DailyMetric.RestingHr),
    Hrv("Heart-rate variability", "ms", 0, DailyMetric.Hrv),
    Spo2("Blood oxygen", "%", 0, DailyMetric.Spo2),
    Stress("Stress", "", 0, DailyMetric.Stress),
    SkinTemperature("Skin temperature", "°C", 1, DailyMetric.SkinTemperature),
    Breathing("Breathing rate", "br/min", 0, DailyMetric.Breathing),
    Steps("Steps", "steps", 0, DailyMetric.Steps),
    ;

    fun format(v: Float): String = if (this == Steps) "%,d".format(Math.round(v)) else "%.${decimals}f".format(v)
}

/** A screen pushed on top of a tab. Each tab keeps its own, so opening one never moves you to another tab. */
sealed interface Detail {
    data class OfMetric(val metric: Metric) : Detail
    data class Workout(val start: Long) : Detail

    companion object {
        /** Saves a tab's stack of details, deepest last, as one string ("m:Hrv|w:1792227300") so it survives process death. */
        val StackSaver: Saver<List<Detail>, String> = Saver(
            save = { stack -> stack.joinToString("|", transform = ::key) },
            restore = { s -> s.split('|').mapNotNull(::restore) },
        )

        private fun key(d: Detail): String = when (d) {
            is OfMetric -> "m:${d.metric.name}"
            is Workout -> "w:${d.start}"
        }

        private fun restore(s: String): Detail? {
            val value = s.substringAfter(':')
            return when (s.substringBefore(':')) {
                "m" -> Metric.entries.firstOrNull { it.name == value }?.let(::OfMetric)
                "w" -> value.toLongOrNull()?.let(::Workout)
                else -> null
            }
        }
    }
}
