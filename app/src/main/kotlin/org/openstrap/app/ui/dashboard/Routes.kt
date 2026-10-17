package org.openstrap.app.ui.dashboard

import androidx.compose.runtime.saveable.Saver

/** A metric with its own detail screen. */
enum class Metric(val title: String) {
    HeartRate("Heart rate"),
    RestingHr("Resting heart rate"),
    Hrv("Heart-rate variability"),
    Spo2("Blood oxygen"),
    Stress("Stress"),
    SkinTemperature("Skin temperature"),
    Breathing("Breathing rate"),
}

/** A screen pushed on top of a tab. Each tab keeps its own, so opening one never moves you to another tab. */
sealed interface Detail {
    data class OfMetric(val metric: Metric) : Detail
    data class Workout(val start: Long) : Detail

    companion object {
        /** Saves a detail as a short string ("m:Hrv", "w:1792227300") so it survives process death. */
        val Saver: Saver<Detail?, String> = Saver(
            save = { d ->
                when (d) {
                    is OfMetric -> "m:${d.metric.name}"
                    is Workout -> "w:${d.start}"
                    null -> ""
                }
            },
            restore = { s ->
                val value = s.substringAfter(':')
                when (s.substringBefore(':')) {
                    "m" -> Metric.entries.firstOrNull { it.name == value }?.let(::OfMetric)
                    "w" -> value.toLongOrNull()?.let(::Workout)
                    else -> null
                }
            },
        )
    }
}
