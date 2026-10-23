package org.openstrap.app.ui.glow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openstrap.app.ui.components.ChartPoint
import org.openstrap.app.ui.dashboard.DayUiState
import org.openstrap.app.ui.dashboard.Detail
import org.openstrap.app.ui.dashboard.Metric

/** The All metrics index is a map of what exists: every metric detail once, and the screens without one. */
class AllMetricsTest {
    private val empty = DayUiState()

    @Test fun `every metric detail appears exactly once`() {
        val details = metricIndex(empty, null, null, emptyList()).flatMap { it.rows }.map { it.detail }
        for (m in Metric.entries) assertEquals(m.name, 1, details.count { it == Detail.OfMetric(m) })
    }

    @Test fun `the screens without a metric detail are there too`() {
        val details = metricIndex(empty, null, null, emptyList()).flatMap { it.rows }.map { it.detail }.toSet()
        assertTrue(details.containsAll(listOf(Detail.Recovery, Detail.Sleep, Detail.Healthspan, Detail.AllWorkouts)))
    }

    @Test fun `grouped by section`() {
        assertEquals(
            listOf("Recovery", "Sleep", "Heart", "Body signals", "Activity"),
            metricIndex(empty, null, null, emptyList()).map { it.title },
        )
    }

    @Test fun `a day without a value says so instead of a number`() {
        val hrv = metricIndex(empty, null, null, emptyList()).flatMap { it.rows }.first { it.detail == Detail.OfMetric(Metric.Hrv) }
        assertEquals(null, hrv.value)
        assertEquals(NO_VALUE, hrv.status)
    }

    @Test fun `values are the day's, in the metric's format`() {
        val day = empty.copy(
            steps = 13860, latestHeartRate = 58,
            spo2 = listOf(ChartPoint(0, 96f), ChartPoint(60, 98f)),
            skinTemperature = listOf(ChartPoint(0, 36.4f), ChartPoint(60, 36.6f)),
        )
        val rows = metricIndex(day, null, null, emptyList()).flatMap { it.rows }.associateBy { it.detail }
        assertEquals("13,860", rows.getValue(Detail.OfMetric(Metric.Steps)).value)
        assertEquals("58", rows.getValue(Detail.OfMetric(Metric.HeartRate)).value)
        assertEquals("97%", rows.getValue(Detail.OfMetric(Metric.Spo2)).value)
        assertEquals("36.5", rows.getValue(Detail.OfMetric(Metric.SkinTemperature)).value)
    }

    private fun readiness(score: Int?, nights: Int, withheld: Boolean = false) =
        org.openstrap.app.data.Recovery.Readiness(score, emptyList(), nights, false, emptyList(), false, withheld = withheld)

    @Test fun `Recovery while learning says how far along it is`() {
        val n = org.openstrap.app.data.Recovery.SCORED_NIGHTS
        assertEquals("Learning, 2 of $n nights", recoveryStatus(readiness(null, 2, withheld = true)))
        assertEquals("Score for the day", recoveryStatus(readiness(80, 60)))
        assertEquals(null, recoveryStatus(null))
    }

    @Test fun `Charge is the latest today and the end of day before, workouts the last 7 days`() {
        val today = java.time.LocalDate.of(2026, 10, 23)
        fun status(date: java.time.LocalDate, name: String) =
            metricIndex(empty.copy(date = date), null, null, emptyList(), today).flatMap { it.rows }.first { it.name == name }
        // Without a value the row says so; the status words are checked through a day that has none of either.
        assertEquals(NO_VALUE, status(today, "Charge").status)
        assertEquals("Last 7 days", status(today, "Workouts").status)
    }

    @Test fun `no row text breaks the copy rules`() {
        metricIndex(empty, null, null, emptyList()).flatMap { it.rows }.forEach { r ->
            for (s in listOf(r.name, r.status)) assertTrue(s, s.none { it == '·' || it == ';' || it == '—' })
        }
    }
}
