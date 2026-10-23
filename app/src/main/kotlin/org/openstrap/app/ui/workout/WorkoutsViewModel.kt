package org.openstrap.app.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.openstrap.app.data.RecoveryEngine
import org.openstrap.app.data.WorkoutAnalysis
import org.openstrap.app.data.WorkoutRecorder
import org.openstrap.app.data.Workouts
import org.openstrap.app.data.db.HealthDao
import org.openstrap.app.data.settings.UserProfile
import kotlin.math.roundToInt

/**
 * What a phone workout's summary shows: [analysis] over its shown range, [recorded] every sample from
 * [recordedStart] to [recordedEnd] (for the trim chart), and [usual] to compare with.
 */
data class PhoneSummary(
    val analysis: WorkoutAnalysis,
    val recorded: List<org.openstrap.app.data.db.WorkoutHeartRate>,
    val recordedStart: Long,
    val recordedEnd: Long,
    val usual: WorkoutRecorder.Usual?,
    val endReason: Int?,
    /** The route, when it was kept; empty otherwise. */
    val route: List<org.openstrap.app.data.Route.Point> = emptyList(),
    /** Every live reading in the shown range, for colouring the route. */
    val history: List<Pair<Long, Int>> = emptyList(),
    val distanceMeters: Float? = null,
    val distanceFromSteps: Boolean = false,
    /** Strength: each set and the rest after it. */
    val sets: List<org.openstrap.app.data.db.AppWorkoutSet> = emptyList(),
    /** The strap's own record of the same workout, when it kept one (a strap session, or its auto-detection). */
    val strapWorkout: WorkoutAnalysis? = null,
    /** Laps, when any were marked. */
    val laps: List<WorkoutRecorder.Lap> = emptyList(),
)

/** Workouts list (newest first) and the one open in detail. */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutsViewModel(private val dao: HealthDao, private val profile: Flow<UserProfile>) : ViewModel() {
    /**
     * Zones fall back to the app's one max-HR rule (tested value, Tanaka, strap, raised by observed peaks) when a
     * workout has no sport effect of its own. Shared, so the 90-day read behind it runs once per profile change.
     */
    private val fallbackMaxHr: Flow<Int> = profile
        .mapLatest { p -> RecoveryEngine(dao).maxHeartRate(p).roundToInt() }
        .flowOn(Dispatchers.Default)
        .stateIn<Int?>(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
        .filterNotNull()

    /**
     * The strap's workouts and the phone's, newest first. List rows use the stored end; the detail screen works out
     * the real one from the HR stream. A phone strength workout carries its count of finished sets.
     */
    val workouts: StateFlow<List<WorkoutAnalysis>> =
        combine(
            dao.workouts(), dao.sportEffects(), dao.lastWorkoutHeartRates(),
            combine(dao.appWorkouts(), dao.setCounts()) { phone, sets -> phone to sets.associate { it.workoutStart to it.sets } }, fallbackMaxHr,
        ) { ws, effects, last, (phone, sets), maxHr ->
            val lastByWorkout = last.associateBy { it.workoutStart }
            val strap = ws.map { w -> Workouts.analyse(w, listOfNotNull(lastByWorkout[w.start]), effects, maxHr) }
            (strap + phone.map { WorkoutRecorder.analyse(it, emptyList(), maxHr).copy(sets = sets[it.start]) }).sortedByDescending { it.workout.start }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The strap's sport-effect records, newest last: recovery time, VO2 max and weekly load after each workout. */
    val effects: StateFlow<List<org.openstrap.app.data.db.SportEffectRow>> =
        dao.sportEffects().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The max heart rate zones are shares of, once worked out. */
    val maxHeartRate: StateFlow<Int?> = fallbackMaxHr.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** A day's heart rate a minute at a time, for adding a workout after the fact. */
    suspend fun dayMinuteHeartRate(date: java.time.LocalDate): List<Pair<Long, Int>> = kotlinx.coroutines.withContext(Dispatchers.Default) {
        val w = org.openstrap.app.data.DayWindow.of(date)
        org.openstrap.app.data.Summaries.bucketAverage(org.openstrap.app.data.Summaries.heartRateSeries(dao.heartRateOnce(w.from, w.to), dao.activityOnce(w.from, w.to)), 60)
    }

    /** From the start of yesterday: the stretches the strap's history may hold that nobody started. */
    private val suggestFrom = org.openstrap.app.data.DayWindow.of(java.time.LocalDate.now().minusDays(1)).from

    /**
     * Workouts nobody started, newest first ([org.openstrap.app.data.WorkoutSuggestions]), from the strap's heart rate
     * since yesterday. Sleep, saved workouts and stretches answered "Not a workout" never count.
     */
    val suggestions: StateFlow<List<org.openstrap.app.data.WorkoutSuggestions.Suggestion>> = combine(
        combine(dao.heartRate(suggestFrom, Long.MAX_VALUE), dao.activity(suggestFrom, Long.MAX_VALUE)) { hr, minutes ->
            org.openstrap.app.data.Summaries.bucketAverage(org.openstrap.app.data.Summaries.heartRateSeries(hr, minutes), 60)
        },
        combine(dao.workouts(), dao.appWorkouts(), dao.dismissedWorkouts(suggestFrom), dao.sleepSessions(suggestFrom, Long.MAX_VALUE)) { strap, phone, dismissed, sleep ->
            strap.map { it.start..it.end } + phone.map { it.start..(it.recordedEnd ?: it.end ?: it.start) } +
                dismissed.map { it.start..it.end } + sleep.map { it.start..it.end }
        },
        fallbackMaxHr,
    ) { series, busy, maxHr -> Triple(series, busy, maxHr) }
        .mapLatest { (series, busy, maxHr) ->
            val now = System.currentTimeMillis() / 1000
            val resting = dao.restingHrOnce(now - 14 * 86_400L, now).lastOrNull()?.bpm ?: 60
            org.openstrap.app.data.WorkoutSuggestions.find(series, resting, maxHr, busy)
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val openStart = MutableStateFlow<Long?>(null)

    val open: StateFlow<WorkoutAnalysis?> = openStart.flatMapLatest { start ->
        if (start == null) flowOf(null)
        else dao.appWorkout(start).flatMapLatest { phone ->
            if (phone != null) phoneAnalysis(start)
            else combine(dao.workout(start), dao.workoutHeartRate(start), dao.sportEffects(), fallbackMaxHr) { w, hr, effects, maxHr ->
                w?.let { Workouts.analyse(it, hr, effects, maxHr) }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** A phone workout with every second of it, updating as gap filling adds minutes. */
    fun phoneAnalysis(start: Long): Flow<WorkoutAnalysis?> =
        combine(dao.appWorkout(start), dao.appWorkoutHeartRate(start), fallbackMaxHr) { w, hr, maxHr ->
            w?.let { WorkoutRecorder.analyse(it, hr, maxHr) }
        }

    /**
     * A phone workout's summary: its analysis, everything recorded (for trimming), and "your usual" from the last
     * similar workouts of the same sport, phone or strap ([WorkoutRecorder.similar]).
     */
    fun phoneSummary(start: Long): Flow<PhoneSummary?> =
        combine(dao.appWorkout(start), dao.appWorkoutHeartRate(start), fallbackMaxHr, workouts, combine(dao.route(start), dao.sets(start)) { r, s -> r to s }) { w, hr, maxHr, all, extra ->
            Quad(w, hr, maxHr, all) to extra
        }
            .mapLatest { (q, extra) ->
                val (routeRows, sets) = extra
                val (w, hr, maxHr, all) = q
                w ?: return@mapLatest null
                // A workout from before its effect was worked out gets it now; saving it brings this summary round again.
                if (w.end != null && w.epoc == null && hr.isNotEmpty()) {
                    runCatching { org.openstrap.app.data.WorkoutEffectEngine(dao).backfill(w, profile.first()) }
                }
                val a = WorkoutRecorder.analyse(w, hr, maxHr)
                val similar = WorkoutRecorder.similar(a, all).map { s ->
                    dao.appWorkoutOnce(s.workout.start)?.let { pw -> WorkoutRecorder.analyse(pw, dao.appWorkoutHeartRateOnce(pw.start), maxHr) }
                        ?: Workouts.analyse(s.workout, dao.workoutHeartRate(s.workout.start).first(), dao.sportEffects().first(), maxHr)
                }
                // Merged by time: a strap workout that started within two minutes of this one's shown range.
                val strap = all.firstOrNull { !it.fromPhone && it.workout.start in (w.from - 120)..((w.end ?: a.end) + 120) }
                    ?.let { s -> Workouts.analyse(s.workout, dao.workoutHeartRate(s.workout.start).first(), dao.sportEffects().first(), maxHr) }
                val route = routeRows.map { it.point }.filter { it.t in w.from..(w.end ?: Long.MAX_VALUE) }
                PhoneSummary(
                    a, WorkoutRecorder.expand(hr), w.start, w.recordedEnd ?: w.end ?: a.end, WorkoutRecorder.usual(similar), w.endReason,
                    route, hr.filter { !it.fromHistory }.map { it.timestamp to it.bpm }, w.distanceMeters, w.distanceFromSteps, sets, strap,
                    w.lapMarks.takeIf { it.isNotEmpty() }?.let { marks ->
                        WorkoutRecorder.laps(w.from, w.end ?: a.end, marks, hr.filter { !it.fromHistory }.map { it.timestamp to it.bpm }, route, w.pauseRanges)
                    }.orEmpty(),
                )
            }.flowOn(Dispatchers.Default)

    /**
     * A GPX file of a kept route for sharing: its first and last [org.openstrap.app.data.Route.PRIVACY_M] metres left
     * out, so it doesn't show where you live, with heart rate. Null without a route long enough to share.
     *
     * TODO: write the smoothed track (`Route.track`) instead of the raw fixes, so the file matches the map and the
     * distance shown in the app. Deferred on purpose; the smoothing landed in 33b550f.
     */
    suspend fun gpx(start: Long): Pair<String, String>? = kotlinx.coroutines.withContext(Dispatchers.Default) {
        val w = dao.appWorkoutOnce(start) ?: return@withContext null
        val points = org.openstrap.app.data.Route.privacyTrim(
            dao.routeOnce(start).map { it.point }.filter { it.t in w.from..(w.end ?: Long.MAX_VALUE) },
        )
        if (points.isEmpty()) return@withContext null
        val hr = dao.appWorkoutHeartRateOnce(start).filter { !it.fromHistory }.associate { it.timestamp to it.bpm }
        val name = Workouts.sportName(w.sportType)
        val date = java.time.Instant.ofEpochSecond(w.from).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        "openstrap-${name.lowercase().replace(' ', '-')}-$date.gpx" to org.openstrap.app.data.Route.gpx("$name $date", points, hr)
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    fun open(start: Long?) { openStart.value = start }

    /** Deletes a strap workout and keeps it from coming back with a later sync ([HealthDao.deleteStrapWorkout]). */
    fun deleteStrapWorkout(start: Long) {
        viewModelScope.launch { dao.deleteStrapWorkout(start, System.currentTimeMillis() / 1000) }
    }
}
