package org.openstrap.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
import org.openstrap.app.data.RecoveryResult
import org.openstrap.app.data.settings.UserProfile
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
import org.openstrap.app.data.db.PaiSample
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

/** Averages over the 7 days before the selected day; null where there's no data. */
data class Baselines(
    val restingHr: Float? = null,
    val hrv: Float? = null,
    val spo2: Float? = null,
    val stress: Float? = null,
    val skinTemperature: Float? = null,
    val breathRate: Float? = null,
)

data class DayUiState(
    val date: LocalDate = LocalDate.now(),
    val steps: Int = 0,
    val hourlySteps: List<Float> = List(24) { 0f },
    val activeHours: Int = 0,
    val heartRate: List<ChartPoint> = emptyList(),
    val latestHeartRate: Int? = null,
    val restingHeartRate: Int? = null,
    /** True when [restingHeartRate] is our own estimate rather than the strap's value. */
    val restingEstimated: Boolean = false,
    val restingTrend: List<TrendPoint> = emptyList(),
    val hrvTrend: List<TrendPoint> = emptyList(),
    /** The main sleep, carrying the strap's own score and stage totals when it reported them. */
    val sleep: SleepSession? = null,
    val naps: List<SleepSession> = emptyList(),
    val sleepStages: List<StageSpan> = emptyList(),
    val sleepBreakdown: Summaries.SleepBreakdown? = null,
    val spo2: List<ChartPoint> = emptyList(),
    val stress: List<ChartPoint> = emptyList(),
    val hrv: List<ChartPoint> = emptyList(),
    val skinTemperature: List<ChartPoint> = emptyList(),
    val breathRate: List<ChartPoint> = emptyList(),
    val pai: PaiSample? = null,
    val baselines: Baselines = Baselines(),
    val dayStart: Long = 0,
    val dayEnd: Long = 0,
) {
    val asleepSeconds: Long? get() = sleepBreakdown?.asleepSeconds ?: sleep?.let { it.end - it.start }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DayViewModel(
    private val repo: HealthRepository,
    private val strapSteps: Flow<Int?> = flowOf(null),
    profile: Flow<UserProfile> = flowOf(UserProfile()),
) : ViewModel() {
    private val selected = MutableStateFlow(LocalDate.now())

    fun previousDay() { selected.value = selected.value.minusDays(1) }
    fun nextDay() { if (selected.value < LocalDate.now()) selected.value = selected.value.plusDays(1) }

    val state: StateFlow<DayUiState> = selected.flatMapLatest { date -> stateFor(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DayUiState())

    /** Readiness and charge, recomputed (off the main thread) when the day, profile or synced data change. */
    @OptIn(FlowPreview::class)
    val recovery: StateFlow<RecoveryResult?> =
        combine(selected, profile, repo.changes().debounce(800)) { d, p, _ -> d to p }
            .mapLatest { (d, p) ->
                withContext(Dispatchers.Default) {
                    runCatching { repo.recovery(d, p) }.onFailure { Log.w("OpenStrap", "recovery failed", it) }.getOrNull()
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun stateFor(date: LocalDate): Flow<DayUiState> {
        val zone = ZoneId.systemDefault()
        val day = DayWindow.of(date)
        val night = DayWindow.nightEndingOn(date)
        val week = DayWindow(DayWindow.of(date.minusDays(6)).from, day.to)

        val activityAndHr = combine(repo.activity(day), repo.heartRate(day)) { act, hr -> act to hr }
        val vitals = combine(repo.spo2(day), repo.stress(day), repo.hrv(day)) { spo2, stress, hrv -> Triple(spo2, stress, hrv) }
        val sleep = repo.sleepSessions(night).flatMapLatest { sessions ->
            // Main sleep = the longest non-nap session that ended in this window.
            val main = sessions.filter { !it.nap }.maxByOrNull { it.end - it.start }
            val naps = sessions.filter { it.nap }
            if (main == null) flowOf(Triple(null, emptyList(), naps))
            else combine(flowOf(main), repo.sleepStages(main.start)) { s, st -> Triple(s, st, naps) }
        }
        val extras = combine(repo.skinTemperature(day), repo.breathRate(night), repo.latestPai(day.to)) { skin, breath, pai ->
            Triple(skin, breath, pai?.takeIf { it.timestamp >= day.from })
        }
        val weekly = combine(repo.restingHr(week.from, week.to), repo.heartRate(week), repo.activity(week), repo.hrv(week)) { resting, hr, act, hrv ->
            val days = (6 downTo 0).map { date.minusDays(it.toLong()) }
            val label = { d: LocalDate -> d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(2) }
            val restingTrend = days.map { d ->
                val w = DayWindow.of(d, zone)
                val stored = resting.firstOrNull { it.day in w.from..w.to }?.bpm
                // Our own estimate (lowest 30-min average) only means something with most of a day's data.
                val series = Summaries.heartRateSeries(hr.filter { it.timestamp in w.from..w.to }, act.filter { it.timestamp in w.from..w.to })
                val estimated = if (series.size >= MIN_MINUTES_FOR_ESTIMATE) Summaries.estimateRestingHr(series) else null
                TrendPoint(label(d), (stored ?: estimated)?.toFloat()) to (stored == null && estimated != null)
            }
            val hrvTrend = days.map { d ->
                val w = DayWindow.of(d, zone)
                val v = hrv.filter { it.timestamp in w.from..w.to }.map { it.milliseconds }
                TrendPoint(label(d), if (v.isEmpty()) null else v.average().toFloat())
            }
            restingTrend to hrvTrend
        }
        val previous = DayWindow(DayWindow.of(date.minusDays(7)).from, DayWindow.of(date.minusDays(1)).to)
        val baselines = combine(
            combine(repo.restingHr(previous.from, previous.to), repo.hrv(previous), repo.spo2(previous)) { r, h, s -> Triple(r, h, s) },
            combine(repo.stress(previous), repo.skinTemperature(previous), repo.breathRate(previous)) { st, sk, br -> Triple(st, sk, br) },
        ) { (resting, hrv, spo2), (stress, skin, breath) ->
            fun List<Number>.mean() = if (isEmpty()) null else map { it.toFloat() }.average().toFloat()
            Baselines(
                restingHr = resting.map { it.bpm }.mean(),
                hrv = hrv.map { it.milliseconds }.mean(),
                spo2 = spo2.map { it.percent }.mean(),
                stress = stress.map { it.value }.mean(),
                skinTemperature = skin.map { it.celsius }.mean(),
                breathRate = breath.map { it.perMinute }.mean(),
            )
        }
        val sleepAndExtras = combine(sleep, extras, baselines) { s, e, b -> Triple(s, e, b) }

        // The strap's own running total covers steps whose minute records were already taken by another app.
        val liveSteps = if (date == LocalDate.now()) strapSteps else flowOf(null)
        val actAndLive = combine(activityAndHr, liveSteps) { a, live -> a to live }

        return combine(actAndLive, vitals, sleepAndExtras, weekly) { (ah, live), (spo2, stress, hrv), (sleepParts, extraParts, base), (restingWithFlag, hrvTrend) ->
            val (session, stages, naps) = sleepParts
            val (skin, breath, pai) = extraParts
            val restingTrend = restingWithFlag.map { it.first }
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
                restingEstimated = restingWithFlag.last().second,
                restingTrend = restingTrend,
                hrvTrend = hrvTrend,
                sleep = session,
                naps = naps,
                sleepStages = stages.map { StageSpan(it.start, it.end, it.stage) },
                sleepBreakdown = if (stages.isEmpty()) null else Summaries.sleepBreakdown(stages),
                spo2 = spo2.map { ChartPoint(it.timestamp, it.percent.toFloat()) },
                stress = stress.map { ChartPoint(it.timestamp, it.value.toFloat()) },
                hrv = hrv.map { ChartPoint(it.timestamp, it.milliseconds.toFloat()) },
                skinTemperature = skin.map { ChartPoint(it.timestamp, it.celsius) },
                breathRate = breath.map { ChartPoint(it.timestamp, it.perMinute.toFloat()) },
                pai = pai,
                baselines = base,
                dayStart = day.from,
                dayEnd = day.to,
            )
        }
    }

    companion object {
        /** Minutes of heart-rate data a day needs before we estimate its resting HR ourselves. */
        private const val MIN_MINUTES_FOR_ESTIMATE = 6 * 60

        val SLEEP_STAGE_ROWS = mapOf(
            SleepStage.AWAKE to 0,
            SleepStage.REM to 1,
            SleepStage.LIGHT to 2,
            SleepStage.DEEP to 3,
        )
    }
}
