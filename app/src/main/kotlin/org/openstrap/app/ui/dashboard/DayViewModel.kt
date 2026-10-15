package org.openstrap.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import org.openstrap.app.data.DayWindow
import org.openstrap.app.data.HealthRepository
import org.openstrap.app.data.Summaries
import org.openstrap.app.data.db.SleepSession
import org.openstrap.app.data.db.SleepStage
import org.openstrap.app.ui.components.ChartPoint
import org.openstrap.app.ui.components.StageSpan
import java.time.LocalDate

data class DayUiState(
    val date: LocalDate = LocalDate.now(),
    val steps: Int = 0,
    val hourlySteps: List<Float> = List(24) { 0f },
    val heartRate: List<ChartPoint> = emptyList(),
    val latestHeartRate: Int? = null,
    val restingHeartRate: Int? = null,
    val sleep: SleepSession? = null,
    val sleepStages: List<StageSpan> = emptyList(),
    val sleepBreakdown: Summaries.SleepBreakdown? = null,
    val spo2: List<ChartPoint> = emptyList(),
    val stress: List<ChartPoint> = emptyList(),
    val hrv: List<ChartPoint> = emptyList(),
    val dayStart: Long = 0,
    val dayEnd: Long = 0,
) {
    val hasAnyData get() = steps > 0 || heartRate.isNotEmpty() || sleep != null
}

@OptIn(ExperimentalCoroutinesApi::class)
class DayViewModel(private val repo: HealthRepository) : ViewModel() {
    private val selected = MutableStateFlow(LocalDate.now())

    fun previousDay() { selected.value = selected.value.minusDays(1) }
    fun nextDay() { if (selected.value < LocalDate.now()) selected.value = selected.value.plusDays(1) }

    val state: StateFlow<DayUiState> = selected.flatMapLatest { date -> stateFor(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DayUiState())

    private fun stateFor(date: LocalDate): Flow<DayUiState> {
        val day = DayWindow.of(date)
        val night = DayWindow.nightEndingOn(date)

        val activityAndHr = combine(repo.activity(day), repo.heartRate(day), repo.restingHr(day.from, day.to)) { act, hr, resting ->
            Triple(act, hr, resting)
        }
        val vitals = combine(repo.spo2(day), repo.stress(day), repo.hrv(day)) { spo2, stress, hrv ->
            Triple(spo2, stress, hrv)
        }
        val sleep = repo.sleepSessions(night).flatMapLatest { sessions ->
            // Main sleep = the longest session that ended in this window.
            val main = sessions.maxByOrNull { it.end - it.start }
            if (main == null) flowOf(null to emptyList()) else repo.sleepStages(main.start).let { stages ->
                combine(flowOf(main), stages) { s, st -> s to st }
            }
        }

        return combine(activityAndHr, vitals, sleep) { (act, hr, resting), (spo2, stress, hrv), (session, stages) ->
            val series = Summaries.heartRateSeries(hr, act)
            DayUiState(
                date = date,
                steps = Summaries.totalSteps(act),
                hourlySteps = Summaries.hourlySteps(act, day.from),
                heartRate = series.map { ChartPoint(it.first, it.second.toFloat()) },
                latestHeartRate = series.lastOrNull()?.second,
                restingHeartRate = resting.lastOrNull()?.bpm ?: Summaries.estimateRestingHr(series),
                sleep = session,
                sleepStages = stages.map { StageSpan(it.start, it.end, it.stage) },
                sleepBreakdown = if (stages.isEmpty()) null else Summaries.sleepBreakdown(stages),
                spo2 = spo2.map { ChartPoint(it.timestamp, it.percent.toFloat()) },
                stress = stress.map { ChartPoint(it.timestamp, it.value.toFloat()) },
                hrv = hrv.map { ChartPoint(it.timestamp, it.milliseconds.toFloat()) },
                dayStart = day.from,
                dayEnd = day.to,
            )
        }
    }

    companion object {
        val SLEEP_STAGE_ROWS = mapOf(
            SleepStage.AWAKE to 0,
            SleepStage.REM to 1,
            SleepStage.LIGHT to 2,
            SleepStage.DEEP to 3,
        )
    }
}
