package org.openstrap.app.ui.dashboard

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.saveable.Saver
import org.openstrap.app.data.settings.Goals
import org.openstrap.app.data.DailyMetric

/** A metric with its own detail screen: its title, unit, decimals shown, and the per-day series behind it. */
enum class Metric(val title: String, val unit: String, val decimals: Int, val daily: DailyMetric) {
    HeartRate("Heart rate", "bpm", 0, DailyMetric.HeartRate),
    RestingHr("Resting heart rate", "bpm", 0, DailyMetric.RestingHr),
    Hrv("HRV", "ms", 0, DailyMetric.Hrv),
    Spo2("Blood oxygen", "%", 0, DailyMetric.Spo2),
    Stress("Stress", "", 0, DailyMetric.Stress),
    SkinTemperature("Skin temperature", "°C", 1, DailyMetric.SkinTemperature),
    Breathing("Breathing rate", "br/min", 0, DailyMetric.Breathing),
    Steps("Steps", "steps", 0, DailyMetric.Steps),
    ;

    fun format(v: Float): String = if (this == Steps) "%,d".format(Math.round(v)) else "%.${decimals}f".format(v)
}

/**
 * A score OpenStrap works out itself, with a detail of its own like a [Metric]'s but no per-day series behind it:
 * Recovery keeps only its last 7 days, Charge only the day shown. Sleep score, Load and the rest join here (step 6).
 */
enum class Score(val title: String) { Recovery("Recovery"), Charge("Charge") }

/** Your daily goals, provided once at the top of the app so every screen measures against the same ones. */
val LocalGoals = compositionLocalOf { Goals() }

/**
 * A screen pushed on top of a tab. Each tab keeps its own, so opening one never moves you to another tab: a card on
 * Today that's about Recovery opens the Recovery screen over Today rather than switching to its tab (spec 3.3).
 */
sealed interface Detail {
    data class OfMetric(val metric: Metric) : Detail
    /** A [Score]'s detail, in the same template as a metric's. */
    data class OfScore(val score: Score) : Detail
    data class Workout(val start: Long) : Detail
    /** Every workout, newest first. */
    data object AllWorkouts : Detail
    /** The Healthspan estimate and what moves it, opened from its card on Today. */
    data object Healthspan : Detail
    /** The Recovery tab's screen (Recovery, Charge and the body signals), opened from a card on another tab. */
    data object Recovery : Detail
    /** The Sleep tab's screen, opened from a card on another tab. */
    data object Sleep : Detail

    companion object {
        /** Saves a tab's stack of details, deepest last, as one string ("m:Hrv|w:1792227300") so it survives process death. */
        val StackSaver: Saver<List<Detail>, String> = Saver(
            save = { stack -> stack.joinToString("|", transform = ::key) },
            restore = { s -> s.split('|').mapNotNull(::restore) },
        )

        private fun key(d: Detail): String = when (d) {
            is OfMetric -> "m:${d.metric.name}"
            is OfScore -> "c:${d.score.name}"
            is Workout -> "w:${d.start}"
            AllWorkouts -> "a:"
            Healthspan -> "h:"
            Recovery -> "r:"
            Sleep -> "s:"
        }

        private fun restore(s: String): Detail? {
            val value = s.substringAfter(':')
            return when (s.substringBefore(':')) {
                "m" -> Metric.entries.firstOrNull { it.name == value }?.let(::OfMetric)
                "c" -> Score.entries.firstOrNull { it.name == value }?.let(::OfScore)
                "w" -> value.toLongOrNull()?.let(::Workout)
                "a" -> AllWorkouts
                "h" -> Healthspan
                "r" -> Recovery
                "s" -> Sleep
                else -> null
            }
        }

        /**
         * The screen an `open_metric` link names: a [Metric] by name ("Hrv"), or "Recovery" or "Sleep" for those
         * screens. Null for anything else.
         */
        fun fromRoute(route: String): Detail? = when (route) {
            "Recovery" -> Recovery
            "Sleep" -> Sleep
            else -> Metric.entries.firstOrNull { it.name == route }?.let(::OfMetric)
        }
    }
}
