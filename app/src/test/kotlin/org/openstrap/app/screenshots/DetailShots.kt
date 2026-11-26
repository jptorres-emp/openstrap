package org.openstrap.app.screenshots

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Test
import org.openstrap.app.data.DayStat
import org.openstrap.app.presentation.Metric
import org.openstrap.app.ui.dashboard.DailyHistory
import org.openstrap.app.ui.dashboard.DayUiState
import org.openstrap.app.ui.glow.GlowMetricDetailScreen
import java.time.LocalDate

/**
 * Pins the detail template ([org.openstrap.app.ui.glow.DetailTemplate]) through the real Resting heart rate detail
 * ([GlowMetricDetailScreen]), which needs only a [DayUiState] and a history function, so no ViewModel or database is
 * involved. Four states: with a value (and your normal known), while learning (a value, but only three days of history,
 * so no word against your normal yet), with no data, and loading (the Week range chosen while its history has not
 * arrived). The day is the fixed Friday 13 November 2026, a week before the code's today, so the week strip never
 * changes.
 */
class DetailShots : ScreenshotTest() {
    private val day = LocalDate.of(2026, 11, 13)

    private fun history(days: Int): DailyHistory = { _, _, _ ->
        flowOf((0 until days).map { DayStat(day.minusDays(it.toLong()), 50f + it % 5, 48f, 60f, 1) })
    }

    private val never: DailyHistory = { _, _, _ -> emptyFlow() }

    private var name = ""

    private fun screen(state: DayUiState, history: DailyHistory, prepare: () -> Unit = {}) = shoot("detail", name, prepare = prepare) {
        GlowMetricDetailScreen(
            Metric.RestingHr, night = false, state = state, history = history, liveHeartRate = null, canUseLive = false,
            onOpen = {}, onSwitch = {}, onSelectDay = {}, contentPadding = PaddingValues(), onBack = {},
        )
    }

    @Test fun withValue() {
        name = "detail_value"
        screen(DayUiState(date = day, restingHeartRate = 52), history(30))
    }

    @Test fun learning() {
        name = "detail_learning"
        screen(DayUiState(date = day, restingHeartRate = 52), history(3))
    }

    @Test fun noData() {
        name = "detail_no_data"
        screen(DayUiState(date = day), history(0))
    }

    @Test fun loading() {
        name = "detail_loading"
        screen(DayUiState(date = day), never) {
            // By the semantics action, not a touch: a touch starts a ripple that runs on real time and is never the same twice.
            compose.onNodeWithText("Week").performSemanticsAction(SemanticsActions.OnClick)
        }
    }
}
