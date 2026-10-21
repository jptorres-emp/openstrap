package org.openstrap.app.device

import android.content.Context
import android.os.PowerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.openstrap.app.data.RecoveryEngine
import org.openstrap.app.data.WorkoutRecorder
import org.openstrap.app.data.db.AppWorkout
import org.openstrap.app.data.db.AppWorkoutHeartRate
import org.openstrap.app.data.db.HealthDao
import org.openstrap.app.data.settings.SettingsStore
import kotlin.math.roundToInt

sealed interface WorkoutState {
    data object Idle : WorkoutState
    /** [workout] as stored: its pauses, and [AppWorkout.pausedAt] while paused. Zones are shares of [maxHeartRate]. */
    data class Recording(val workout: AppWorkout, val maxHeartRate: Int) : WorkoutState {
        val paused: Boolean get() = workout.pausedAt != null
    }
    /** Saved; its summary shows until it is dismissed. */
    data class Ended(val start: Long) : WorkoutState
}

/**
 * The one workout state in the app: it drives the recorder, the notification and the screens. Live readings are
 * written to Room every [FLUSH_MS], so a killed process loses at most that much, and [restore] carries on with a
 * workout that was still recording when the process died. Minutes the phone missed are filled after the next sync
 * ([WorkoutGapFill]).
 */
class WorkoutSession(
    private val context: Context,
    private val dao: HealthDao,
    private val settings: SettingsStore,
    private val strap: StrapController,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()

    private val _state = MutableStateFlow<WorkoutState>(WorkoutState.Idle)
    val state: StateFlow<WorkoutState> = _state

    /** Live readings of the last [RECENT_S] seconds, oldest first, for the live chart: (epoch seconds, bpm). */
    private val _recent = MutableStateFlow<List<Pair<Long, Int>>>(emptyList())
    val recent: StateFlow<List<Pair<Long, Int>>> = _recent

    /** False until [restore] has looked for a workout left recording, so the service doesn't stop before it knows. */
    private val _restored = MutableStateFlow(false)
    val restored: StateFlow<Boolean> = _restored

    /** Average and peak over the whole workout so far, live readings and filled minutes alike. */
    private val _stats = MutableStateFlow<WorkoutRecorder.Stats?>(null)
    val stats: StateFlow<WorkoutRecorder.Stats?> = _stats
    private var sum = 0L
    private var count = 0L

    /** Seconds in each live zone so far ([WorkoutRecorder.liveZone]), for "6:40 in zone". */
    private val _zoneSeconds = MutableStateFlow(IntArray(org.openstrap.app.data.Workouts.ZONES))
    val zoneSeconds: StateFlow<IntArray> = _zoneSeconds
    private var lastReading: Pair<Long, Int>? = null

    /** The live screen is tucked away to use the rest of the app; the notification brings it back. */
    val minimized = MutableStateFlow(false)

    private var recordJob: Job? = null
    private val buffer = mutableListOf<AppWorkoutHeartRate>()
    private var lastSecond = -1L
    private var wake: PowerManager.WakeLock? = null

    /** Picks up a workout left recording when the process died. Called once at app start. */
    fun restore() {
        scope.launch {
            try {
                lock.withLock {
                    if (_state.value is WorkoutState.Recording) return@withLock
                    val w = dao.activeAppWorkout() ?: return@withLock
                    val now = now()
                    _recent.value = dao.appWorkoutHeartRateOnce(w.start)
                        .filter { !it.fromHistory && it.timestamp >= now - RECENT_S }.map { it.timestamp to it.bpm }
                    val stored = dao.appWorkoutHeartRateOnce(w.start)
                    sum = stored.sumOf { it.bpm.toLong() }
                    count = stored.size.toLong()
                    _stats.value = WorkoutRecorder.stats(stored)
                    val live = stored.filter { !it.fromHistory }.map { it.timestamp to it.bpm }
                    val maxHr = maxHeartRate()
                    _zoneSeconds.value = WorkoutRecorder.liveZoneSeconds(live, maxHr)
                    lastReading = live.lastOrNull()
                    begin(w, maxHr)
                }
            } finally {
                _restored.value = true
            }
        }
    }

    fun start(sportType: Int) {
        scope.launch {
            lock.withLock {
                if (_state.value is WorkoutState.Recording) return@withLock
                val w = AppWorkout(start = now(), end = null, sportType = sportType)
                dao.upsertAppWorkout(w)
                _recent.value = emptyList()
                _stats.value = null
                sum = 0
                count = 0
                _zoneSeconds.value = IntArray(org.openstrap.app.data.Workouts.ZONES)
                lastReading = null
                minimized.value = false
                begin(w, maxHeartRate())
            }
        }
    }

    fun pause() = update { w -> if (w.pausedAt != null) w else { flush(); w.copy(pausedAt = now()) } }

    /** A pause doesn't count as time in zone. */
    fun resume() = update { w -> lastReading = null; w.pausedAt?.let { w.withPause(it, now()) } ?: w }

    /** Saves the workout; ended while paused, it ends where the pause began. */
    fun end() {
        scope.launch {
            lock.withLock {
                val r = _state.value as? WorkoutState.Recording ?: return@withLock
                flush()
                val w = r.workout
                val stats = WorkoutRecorder.stats(dao.appWorkoutHeartRateOnce(w.start))
                dao.upsertAppWorkout(
                    w.copy(
                        end = w.pausedAt ?: now(), pausedAt = null, endReason = AppWorkout.END_USER,
                        averageHeartRate = stats?.average, maxHeartRate = stats?.max,
                    ),
                )
                stop()
                _state.value = WorkoutState.Ended(w.start)
            }
            // The strap's history fills any minutes the phone missed; it batches, so ask now.
            strap.syncNow()
        }
    }

    /** Deletes a phone workout, and closes its summary if it is the one showing. */
    fun discard(start: Long) {
        scope.launch {
            lock.withLock {
                dao.deleteAppWorkout(start)
                if ((_state.value as? WorkoutState.Ended)?.start == start) _state.value = WorkoutState.Idle
            }
        }
    }

    fun dismissSummary() {
        if (_state.value is WorkoutState.Ended) _state.value = WorkoutState.Idle
    }

    private fun update(change: suspend (AppWorkout) -> AppWorkout) {
        scope.launch {
            lock.withLock {
                val r = _state.value as? WorkoutState.Recording ?: return@withLock
                val w = change(r.workout)
                if (w == r.workout) return@withLock
                dao.upsertAppWorkout(w)
                _state.value = r.copy(workout = w)
            }
        }
    }

    private suspend fun maxHeartRate() =
        runCatching { RecoveryEngine(dao).maxHeartRate(settings.profile.first()).roundToInt() }.getOrDefault(DEFAULT_MAX_HR)

    private suspend fun begin(w: AppWorkout, maxHr: Int) {
        _state.value = WorkoutState.Recording(w, maxHr)
        strap.setWorkoutActive(true)
        runCatching { StrapService.start(context) }
        strap.autoConnect()
        // The CPU must keep up with the strap's readings and the flushes while the screen is off.
        wake = context.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OpenStrap:workout").apply { acquire(MAX_WAKE_MS) }
        recordJob?.cancel()
        recordJob = scope.launch {
            launch { strap.heartRateReadings.collect(::onReading) }
            while (isActive) {
                delay(FLUSH_MS)
                flush()
            }
        }
    }

    private fun stop() {
        recordJob?.cancel()
        recordJob = null
        strap.setWorkoutActive(false)
        wake?.takeIf { it.isHeld }?.release()
        wake = null
        _recent.value = emptyList()
        minimized.value = false
    }

    /** One reading a second: the strap sends each on two paths. Nothing is kept while paused. */
    private fun onReading(bpm: Int) {
        val r = _state.value as? WorkoutState.Recording ?: return
        if (r.paused || !WorkoutRecorder.isValid(bpm)) return
        val t = now()
        synchronized(buffer) {
            if (t == lastSecond) return
            lastSecond = t
            buffer += AppWorkoutHeartRate(r.workout.start, t, bpm)
        }
        _recent.value = (_recent.value + (t to bpm)).dropWhile { it.first < t - RECENT_S }
        sum += bpm
        count++
        lastReading?.let { (pt, pbpm) ->
            val z = WorkoutRecorder.liveZone(pbpm, r.maxHeartRate)!!
            _zoneSeconds.value = _zoneSeconds.value.copyOf().also { it[z] += (t - pt).coerceIn(0, WorkoutRecorder.MAX_STEP_S).toInt() }
        }
        lastReading = t to bpm
        _stats.value = WorkoutRecorder.Stats(Math.round(sum.toDouble() / count).toInt(), maxOf(_stats.value?.max ?: 0, bpm), minOf(_stats.value?.min ?: bpm, bpm))
    }

    private suspend fun flush() {
        val batch = synchronized(buffer) { buffer.toList().also { buffer.clear() } }
        if (batch.isEmpty()) return
        runCatching { dao.insertAppWorkoutHeartRate(batch) }.onFailure {
            // Kept for the next flush rather than lost.
            synchronized(buffer) { buffer.addAll(0, batch) }
        }
    }

    private fun now() = System.currentTimeMillis() / 1000

    private companion object {
        const val FLUSH_MS = 10_000L
        const val RECENT_S = 600L
        /** Only if the max-HR model can't run; 190 is Tanaka's estimate at 25. */
        const val DEFAULT_MAX_HR = 190
        /** A safety net should end() never come; the strap's battery is long gone by then. */
        const val MAX_WAKE_MS = 12 * 3600_000L
    }
}
