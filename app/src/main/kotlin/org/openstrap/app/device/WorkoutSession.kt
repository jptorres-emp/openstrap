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
    /**
     * [workout] as stored: its pauses, and [AppWorkout.pausedAt] while paused. Zones are shares of [maxHeartRate].
     * [endPrompt] is the end proposed by "Looks like you've stopped", while that question is open.
     */
    data class Recording(val workout: AppWorkout, val maxHeartRate: Int, val endPrompt: Long? = null, val inCar: Boolean = false) : WorkoutState {
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
    /** A coaching buzz never plays while a call rings: a motor pattern cuts off the call's buzz (hardware test). */
    private val callRinging: () -> Boolean = { false },
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

    /** A sport a launcher shortcut or the Quick Settings tile asked the ready screen to open with; the screen clears it. */
    val readyRequest = MutableStateFlow<Int?>(null)

    /** The live screen is tucked away to use the rest of the app; the notification brings it back. */
    val minimized = MutableStateFlow(false)

    /** For "Looks like you've stopped": live readings and the strap's step totals of the last [STOP_WINDOW_S]. */
    private var window = listOf<Pair<Long, Int>>()
    private var steps = listOf<Pair<Long, Int>>()
    /** Nothing before this counts as activity for the end prompt: the start, or the last "Keep going". */
    private var askSince = 0L
    private var restingHr = DEFAULT_RESTING_HR

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
                    window = live.filter { it.first >= now - STOP_WINDOW_S }
                    // A restart mustn't ask at once about a stop it can't see the start of.
                    askSince = now
                    begin(w, maxHr)
                }
            } finally {
                _restored.value = true
            }
        }
    }

    fun start(sportType: Int, askToEnd: Boolean = true, target: org.openstrap.app.data.WorkoutTarget? = null) {
        scope.launch {
            lock.withLock {
                if (_state.value is WorkoutState.Recording) return@withLock
                val w = AppWorkout(start = now(), end = null, sportType = sportType, askToEnd = askToEnd, target = target?.code)
                dao.upsertAppWorkout(w)
                _recent.value = emptyList()
                _stats.value = null
                sum = 0
                count = 0
                _zoneSeconds.value = IntArray(org.openstrap.app.data.Workouts.ZONES)
                lastReading = null
                window = emptyList()
                askSince = w.start
                minimized.value = false
                begin(w, maxHeartRate())
            }
        }
    }

    fun pause() = update { w -> if (w.pausedAt != null) w else { flush(); w.copy(pausedAt = now()) } }

    /** A pause doesn't count as time in zone. */
    fun resume() = update { w -> lastReading = null; w.pausedAt?.let { w.withPause(it, now()) } ?: w }

    /** Saves the workout; ended while paused, it ends where the pause began. */
    fun end() = finish(null, AppWorkout.END_USER)

    /** "End at 18:50": saves the workout ending where the wearer stopped; what came after stays recorded, for undo. */
    fun endAtPrompt() {
        val r = _state.value as? WorkoutState.Recording ?: return
        finish(r.endPrompt ?: return, if (r.inCar) AppWorkout.END_CAR else AppWorkout.END_AUTO)
    }

    /** "Keep going": closes the question; it comes back only after a new active spell. */
    fun keepGoing() {
        askSince = now()
        val r = _state.value as? WorkoutState.Recording ?: return
        if (r.endPrompt != null) _state.value = r.copy(endPrompt = null, inCar = false)
    }

    private fun finish(at: Long?, reason: Int) {
        scope.launch {
            lock.withLock {
                val r = _state.value as? WorkoutState.Recording ?: return@withLock
                flush()
                val w = r.workout
                val recorded = w.pausedAt ?: now()
                val end = at?.coerceIn(w.start, recorded) ?: recorded
                val ended = w.copy(end = end, pausedAt = null, endReason = reason, recordedEnd = recorded.takeIf { it > end })
                dao.upsertAppWorkout(WorkoutRecorder.withStats(ended, dao.appWorkoutHeartRateOnce(w.start)))
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

    /**
     * Saves a stretch nobody started as a workout ("Save as workout", or added after the fact), built from the
     * strap's history. A stretch over a workout already saved is refused.
     */
    fun saveFromHistory(from: Long, to: Long, sportType: Int) {
        scope.launch {
            lock.withLock {
                if (to - from < 60 || dao.appWorkoutsOnce(from, to).isNotEmpty()) return@withLock
                val w = AppWorkout(start = from, end = to, sportType = sportType, source = AppWorkout.SOURCE_SUGGESTED, endReason = AppWorkout.END_USER)
                dao.upsertAppWorkout(w)
                WorkoutGapFill(dao).fill(w)
            }
        }
    }

    /** "Not a workout": remembered for that time range, so the same stretch isn't offered again. */
    fun dismissSuggestion(from: Long, to: Long) {
        scope.launch { dao.insertDismissedWorkouts(listOf(org.openstrap.app.data.db.DismissedWorkout(from, to))) }
    }

    /** Puts back what an early end or a trim left out: the workout as recorded. */
    fun undoTrim(start: Long) = editSaved(start) { it.copy(end = it.recordedEnd ?: it.end, recordedEnd = null, shownStart = null) }

    /** Shows a saved workout from [from] to [to], both inside what was recorded; nothing is deleted. */
    fun trim(start: Long, from: Long, to: Long) = editSaved(start) { w ->
        val recorded = w.recordedEnd ?: w.end ?: return@editSaved w
        val end = to.coerceIn(w.start + 60, recorded)
        // A trim by hand replaces whatever the app proposed, so the summary says "Trimmed", not "when you'd stopped".
        w.copy(
            shownStart = from.coerceIn(w.start, end - 60).takeIf { it > w.start }, end = end, recordedEnd = recorded.takeIf { it > end },
            endReason = AppWorkout.END_USER,
        )
    }

    private fun editSaved(start: Long, change: (AppWorkout) -> AppWorkout) {
        scope.launch {
            lock.withLock {
                val w = dao.appWorkoutOnce(start)?.takeIf { it.end != null } ?: return@withLock
                val changed = change(w)
                if (changed != w) dao.upsertAppWorkout(WorkoutRecorder.withStats(changed, dao.appWorkoutHeartRateOnce(start)))
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

    private var coach = org.openstrap.app.data.Coach(null)

    private suspend fun begin(w: AppWorkout, maxHr: Int) {
        coach = org.openstrap.app.data.Coach(org.openstrap.app.data.WorkoutTarget.parse(w.target))
        _state.value = WorkoutState.Recording(w, maxHr)
        strap.setWorkoutActive(true)
        runCatching { StrapService.start(context) }
        strap.autoConnect()
        // The CPU must keep up with the strap's readings and the flushes while the screen is off.
        wake = context.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OpenStrap:workout").apply { acquire(MAX_WAKE_MS) }
        watchForCar()
        restingHr = runCatching { dao.restingHrOnce(now() - RESTING_LOOKBACK_S, now()).lastOrNull()?.bpm }.getOrNull() ?: DEFAULT_RESTING_HR
        recordJob?.cancel()
        recordJob = scope.launch {
            launch { strap.heartRateReadings.collect(::onReading) }
            // The strap's running total; only its changes matter, so its firmware offset doesn't.
            launch {
                strap.todaySteps.collect { s ->
                    s ?: return@collect
                    val t = now()
                    steps = (steps + (t to s.steps)).dropWhile { it.first < t - STOP_WINDOW_S }
                }
            }
            var tick = 0
            while (isActive) {
                delay(FLUSH_MS)
                flush()
                if (++tick % STOP_CHECK_TICKS == 0) checkStopped()
            }
        }
    }

    /** Asks once the wearer has been calm and still for a while ([WorkoutRecorder.stoppedSince]); the strap buzzes. */
    private fun checkStopped() {
        val r = _state.value as? WorkoutState.Recording ?: return
        if (r.paused || !r.workout.askToEnd || r.endPrompt != null) return
        val at = WorkoutRecorder.stoppedSince(window, steps, restingHr, r.maxHeartRate, maxOf(askSince, r.workout.start), now()) ?: return
        _state.value = r.copy(endPrompt = at)
        if (!callRinging()) scope.launch { runCatching { strap.buzz(STOP_BUZZ) } }
    }

    /**
     * Car detection without location or Google services: a car stereo or hands-free kit connecting over Bluetooth
     * means a car. The question comes at once, ending at the last active moment.
     */
    private val carReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: Context, intent: android.content.Intent) {
            val device = androidx.core.content.IntentCompat.getParcelableExtra(intent, android.bluetooth.BluetoothDevice.EXTRA_DEVICE, android.bluetooth.BluetoothDevice::class.java)
            val cls = runCatching { device?.bluetoothClass?.deviceClass }.getOrNull() ?: return
            if (cls != android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO && cls != android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE) return
            val r = _state.value as? WorkoutState.Recording ?: return
            if (r.paused || !r.workout.askToEnd || r.endPrompt != null) return
            val at = WorkoutRecorder.lastActive(window, steps, restingHr, r.maxHeartRate)?.takeIf { it > r.workout.start } ?: now()
            _state.value = r.copy(endPrompt = at, inCar = true)
            if (!callRinging()) scope.launch { runCatching { strap.buzz(STOP_BUZZ) } }
        }
    }
    private var carRegistered = false

    private fun watchForCar() {
        if (carRegistered) return
        carRegistered = true
        androidx.core.content.ContextCompat.registerReceiver(
            context, carReceiver, android.content.IntentFilter(android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED),
            androidx.core.content.ContextCompat.RECEIVER_EXPORTED,
        )
    }

    /**
     * Plays a coaching cue on the strap, never while a call rings or Do Not Disturb silences everything. A cue that
     * arrives late (out of range, ~5 s) is never retried: the notification still shows where you are.
     */
    private fun cue(c: org.openstrap.app.data.Cue) {
        if (callRinging()) return
        val filter = context.getSystemService(android.app.NotificationManager::class.java).currentInterruptionFilter
        if (filter == android.app.NotificationManager.INTERRUPTION_FILTER_NONE || filter == android.app.NotificationManager.INTERRUPTION_FILTER_ALARMS) return
        scope.launch { runCatching { strap.buzz(c.pattern) } }
        _lastCue.value = c
    }

    /** The last coaching cue, for the live screen to show what the buzz meant. */
    private val _lastCue = MutableStateFlow<org.openstrap.app.data.Cue?>(null)
    val lastCue: StateFlow<org.openstrap.app.data.Cue?> = _lastCue

    private fun stop() {
        _lastCue.value = null
        if (carRegistered) runCatching { context.unregisterReceiver(carReceiver) }
        carRegistered = false
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
        window = (window + (t to bpm)).dropWhile { it.first < t - STOP_WINDOW_S }
        sum += bpm
        count++
        lastReading?.let { (pt, pbpm) ->
            val z = WorkoutRecorder.liveZone(pbpm, r.maxHeartRate)!!
            _zoneSeconds.value = _zoneSeconds.value.copyOf().also { it[z] += (t - pt).coerceIn(0, WorkoutRecorder.MAX_STEP_S).toInt() }
        }
        lastReading = t to bpm
        coach.feed(t, WorkoutRecorder.liveZone(bpm, r.maxHeartRate), WorkoutRecorder.activeSeconds(r.workout, t))?.let(::cue)
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
        /** Long enough to hold the 8 calm minutes and the active moment before them. */
        const val STOP_WINDOW_S = 20 * 60L
        /** Checked every third flush, 30 s. */
        const val STOP_CHECK_TICKS = 3
        const val RESTING_LOOKBACK_S = 14 * 86_400L
        const val DEFAULT_RESTING_HR = 60
        /** Two long pulses: unlike a notification's short ones. */
        val STOP_BUZZ = listOf(500, 250, 500)
        /** Only if the max-HR model can't run; 190 is Tanaka's estimate at 25. */
        const val DEFAULT_MAX_HR = 190
        /** A safety net should end() never come; the strap's battery is long gone by then. */
        const val MAX_WAKE_MS = 12 * 3600_000L
    }
}
