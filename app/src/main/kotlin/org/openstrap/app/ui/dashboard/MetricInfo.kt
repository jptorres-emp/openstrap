package org.openstrap.app.ui.dashboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.Flow
import org.openstrap.app.data.DailyMetric
import org.openstrap.app.data.DayStat
import org.openstrap.app.ui.theme.MetricColors
import java.time.LocalDate

/** How far back a metric detail looks: the selected day, or one value per day (per week, for a year). */
internal enum class Range(val label: String, val days: Int) { Day("Day", 1), Week("Week", 7), Month("Month", 30), Year("Year", 365) }

/** Loads one value per day of a metric between two dates; the app root passes the repository's. */
typealias DailyHistory = (DailyMetric, LocalDate, LocalDate) -> Flow<List<DayStat>>

internal fun colorOf(metric: Metric): Color = when (metric) {
    Metric.HeartRate, Metric.RestingHr -> MetricColors.heart
    Metric.Hrv, Metric.Breathing -> MetricColors.hrv
    Metric.Spo2 -> MetricColors.spo2
    Metric.Stress -> MetricColors.stress
    Metric.SkinTemperature -> MetricColors.temperature
    Metric.Steps -> MetricColors.steps
}

internal val RELATED = mapOf(
    Metric.HeartRate to listOf(Metric.RestingHr, Metric.Hrv, Metric.Stress),
    Metric.RestingHr to listOf(Metric.Hrv, Metric.HeartRate, Metric.SkinTemperature),
    Metric.Hrv to listOf(Metric.RestingHr, Metric.Stress, Metric.Breathing),
    Metric.Spo2 to listOf(Metric.Breathing, Metric.RestingHr),
    Metric.Stress to listOf(Metric.Hrv, Metric.HeartRate),
    Metric.SkinTemperature to listOf(Metric.RestingHr, Metric.Breathing),
    Metric.Breathing to listOf(Metric.Spo2, Metric.SkinTemperature, Metric.Hrv),
    Metric.Steps to listOf(Metric.HeartRate),
)

internal val ABOUT = mapOf(
    Metric.HeartRate to (
        "How fast your heart beats, measured all day by the strap." to
            "Movement, stress, caffeine and heat raise it; rest and sleep bring it down."
        ),
    Metric.RestingHr to (
        "Your heart rate at its calmest, which the strap works out each day. Lower usually means a fitter, well-rested heart." to
            "It rises after hard training, late meals, alcohol, poor sleep or when your body is fighting something off, and falls as fitness builds."
        ),
    Metric.Hrv to (
        "Heart-rate variability: how much the time between beats varies. Higher than your usual means your body has recovered." to
            "Good sleep and rest raise it. Stress, hard training and alcohol lower it. Compare it with your own normal, not other people's."
        ),
    Metric.Spo2 to (
        "Blood oxygen: how much of your blood's capacity is carrying oxygen. Most people sit at 95–100%." to
            "Altitude and breathing pauses in sleep can lower it. A loose strap can also give low readings."
        ),
    Metric.Stress to (
        "The strap's 0–100 stress level, worked out from your heart rate and its variability while you're still." to
            "Pressure, caffeine and too little sleep raise it; calm breathing, rest and time outdoors bring it down."
        ),
    Metric.SkinTemperature to (
        "The temperature of your skin under the strap. The level depends on fit and room; what matters is the change from your normal." to
            "It moves with alcohol, a warm room, hard training, the menstrual cycle, or when your body is fighting something off."
        ),
    Metric.Breathing to (
        "How many breaths you take a minute while asleep. It stays very steady for most people." to
            "It can rise above your normal after a hard training day, in a warm, stuffy room, or when your body is fighting something off."
        ),
    Metric.Steps to (
        "Steps counted by the strap through the day." to
            "Short walks spread across the day add up; most people aim for 7,000–10,000."
        ),
)
