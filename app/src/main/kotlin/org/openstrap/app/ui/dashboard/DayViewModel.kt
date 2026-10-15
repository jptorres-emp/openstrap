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
import org.openstrap.app.ui.components.TrendPoint
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

const val STEP_GOAL = 8000
const val SLEEP_GOAL_SECONDS = 8 * 3600L
const val ACTIVE_HOURS_GOAL = 10

data class DayUiState(
    val date: LocalDate = LocalDate.now(),
    val steps: Int = 0,
    val hourlySteps: List<Float> = List(24) { 0f },
    val activeHours: Int = 0,
    val heartRate: List<ChartPoint> = emptyList(),
    val latestHeartRate: Int? = null,
    val restingHeartRate: Int? = null,
    val restingTrend: List<TrendPoint> = emptyList(),
    val hrvTrend: List<TrendPoint> = emptyList(),
    val sleep: SleepSession? = null,
    val sleepStages: List<StageSpan> = emptyList(),
    val sleepBreakdown: Summaries.SleepBreakdown? = null,
    val spo2: List<ChartPoint> = emptyList(),
    val stress: List<ChartPoint> = emptyList(),
    val hrv: List<ChartPoint> = emptyList(),
    val dayStart: Long = 0,
    val dayEnd: Long = 0,
) {
    val asleepSeconds: Long? get() = sleepBreakdown?.asleepSeconds ?: sleep?.let { it.end - it.start }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DayViewModel(private val repo: HealthRepository, private val strapSteps: Flow<Int?> = flowOf(null)) : ViewModel() {
    private val selected = MutableStateFlow(LocalDate.now())

    fun previousDay() { selected.value = selected.value.minusDays(1) }
    fun nextDay() { if (selected.value < LocalDate.now()) selected.value = selected.value.plusDays(1) }

    val state: StateFlow<DayUiState> = selected.flatMapLatest { date -> stateFor(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DayUiState())

    private fun stateFor(date: LocalDate): Flow<DayUiState> {
        val zone = ZoneId.systemDefault()
        val day = DayWindow.of(date)
        val night = DayWindow.nightEndingOn(date)
        val week = DayWindow(DayWindow.of(date.minusDays(6)).from, day.to)

        val activityAndHr = combine(repo.activity(day), repo.heartRate(day)) { act, hr -> act to hr }
        val vitals = combine(repo.spo2(day), repo.stress(day), repo.hrv(day)) { spo2, stress, hrv -> Triple(spo2, stress, hrv) }
        val sleep = repo.sleepSessions(night).flatMapLatest { sessions ->
            // Main sleep = the longest session that ended in this window.
            val main = sessions.maxByOrNull { it.end - it.start }
            if (main == null) flowOf(null to emptyList()) else combine(flowOf(main), repo.sleepStages(main.start)) { s, st -> s to st }
        }
        val weekly = combine(repo.restingHr(week.from, week.to), repo.heartRate(week), repo.activity(week), repo.hrv(week)) { resting, hr, act, hrv ->
            val days = (6 downTo 0).map { date.minusDays(it.toLong()) }
            val label = { d: LocalDate -> d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(2) }
            val restingTrend = days.map { d ->
                val w = DayWindow.of(d, zone)
                val stored = resting.firstOrNull { it.day in w.from..w.to }?.bpm
                val estimated = stored ?: Summaries.estimateRestingHr(
                    Summaries.heartRateSeries(hr.filter { it.timestamp in w.from..w.to }, act.filter { it.timestamp in w.from..w.to }),
                )
                TrendPoint(label(d), estimated?.toFloat())
            }
            val hrvTrend = days.map { d ->
                val w = DayWindow.of(d, zone)
                val v = hrv.filter { it.timestamp in w.from..w.to }.map { it.milliseconds }
                TrendPoint(label(d), if (v.isEmpty()) null else v.average().toFloat())
            }
            restingTrend to hrvTrend
        }

        // The strap's own running total covers steps whose minute records were already taken by another app.
        val liveSteps = if (date == LocalDate.now()) strapSteps else flowOf(null)
        val actAndLive = combine(activityAndHr, liveSteps) { a, live -> a to live }

        return combine(actAndLive, vitals, sleep, weekly) { (ah, live), (spo2, stress, hrv), (session, stages), (restingTrend, hrvTrend) ->
            val (act, hr) = ah
            val series = Summaries.heartRateSeries(hr, act)
            val hourly = Summaries.hourlySteps(act, day.from)
            DayUiState(
                date = date,
                steps = maxOf(Summaries.totalSteps(act), live ?: 0),
                hourlySteps = hourly,
                activeHours = Summaries.activeHours(hourly),
                heartRate = Summaries.bucketAverage(series).map { ChartPoint(it.first, it.second.toFloat()) },
                latestHeartRate = series.lastOrNull()?.second,
                restingHeartRate = restingTrend.last().value?.toInt(),
                restingTrend = restingTrend,
                hrvTrend = hrvTrend,
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
