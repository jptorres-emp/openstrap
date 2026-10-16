package org.openstrap.app.ui.workout

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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.openstrap.app.data.WorkoutAnalysis
import org.openstrap.app.data.Workouts
import org.openstrap.app.data.db.HealthDao
import org.openstrap.app.data.settings.UserProfile
import java.time.Year

/** Workouts list (newest first) and the one open in detail. */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutsViewModel(private val dao: HealthDao, profile: Flow<UserProfile>) : ViewModel() {
    private val fallbackMaxHr: Flow<Int> = profile.map { p -> p.birthYear?.let { 220 - (Year.now().value - it) } ?: DEFAULT_MAX_HR }

    /** List rows use the stored end; the detail screen works out the real one from the HR stream. */
    val workouts: StateFlow<List<WorkoutAnalysis>> =
        combine(dao.workouts(), dao.sportEffects(), dao.lastWorkoutHeartRates(), fallbackMaxHr) { ws, effects, last, maxHr ->
            val lastByWorkout = last.associateBy { it.workoutStart }
            ws.map { w -> Workouts.analyse(w, listOfNotNull(lastByWorkout[w.start]), effects, maxHr) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val openStart = MutableStateFlow<Long?>(null)

    val open: StateFlow<WorkoutAnalysis?> = openStart.flatMapLatest { start ->
        if (start == null) flowOf(null)
        else combine(dao.workout(start), dao.workoutHeartRate(start), dao.sportEffects(), fallbackMaxHr) { w, hr, effects, maxHr ->
            w?.let { Workouts.analyse(it, hr, effects, maxHr) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun open(start: Long?) { openStart.value = start }

    private companion object {
        const val DEFAULT_MAX_HR = 190
    }
}
