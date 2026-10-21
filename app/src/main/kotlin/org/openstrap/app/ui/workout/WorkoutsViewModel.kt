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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import org.openstrap.app.data.RecoveryEngine
import org.openstrap.app.data.WorkoutAnalysis
import org.openstrap.app.data.WorkoutRecorder
import org.openstrap.app.data.Workouts
import org.openstrap.app.data.db.HealthDao
import org.openstrap.app.data.settings.UserProfile
import kotlin.math.roundToInt

/** Workouts list (newest first) and the one open in detail. */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutsViewModel(private val dao: HealthDao, profile: Flow<UserProfile>) : ViewModel() {
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
     * the real one from the HR stream.
     */
    val workouts: StateFlow<List<WorkoutAnalysis>> =
        combine(dao.workouts(), dao.sportEffects(), dao.lastWorkoutHeartRates(), dao.appWorkouts(), fallbackMaxHr) { ws, effects, last, phone, maxHr ->
            val lastByWorkout = last.associateBy { it.workoutStart }
            val strap = ws.map { w -> Workouts.analyse(w, listOfNotNull(lastByWorkout[w.start]), effects, maxHr) }
            (strap + phone.map { WorkoutRecorder.analyse(it, emptyList(), maxHr) }).sortedByDescending { it.workout.start }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The strap's sport-effect records, newest last: recovery time, VO2 max and weekly load after each workout. */
    val effects: StateFlow<List<org.openstrap.app.data.db.SportEffectRow>> =
        dao.sportEffects().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    fun open(start: Long?) { openStart.value = start }
}
