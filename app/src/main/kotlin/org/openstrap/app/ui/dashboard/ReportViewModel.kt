package org.openstrap.app.ui.dashboard

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import org.openstrap.app.data.HealthRepository
import org.openstrap.app.data.Reports
import org.openstrap.app.data.WorkoutAnalysis
import org.openstrap.app.data.settings.UserProfile
import java.time.LocalDate

/** The report for a day: worked out, or why not. */
sealed interface ReportOutcome {
    val date: LocalDate

    data class Ready(val report: Reports.Report) : ReportOutcome {
        override val date: LocalDate get() = report.date
    }

    data class Failed(override val date: LocalDate) : ReportOutcome
}

/**
 * The report for the day shown ([Reports]), worked out once and read by everything that needs it: the Training tab,
 * the Reports screen, and tonight's bedtime on Today, so the app never shows two answers to one question. Worked out
 * off the main thread when the day, the profile, the workouts or the synced data change; it's heavy (two months of
 * days, five of heart rate), so after the first a burst of changes settles for a few seconds and a newer one cancels
 * the one under way. Coming back to the app with nothing new keeps the report it has. Null while the first is worked
 * out.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class ReportViewModel(
    private val repo: HealthRepository,
    date: Flow<LocalDate>,
    profile: Flow<UserProfile>,
    workouts: Flow<List<WorkoutAnalysis>>,
) : ViewModel() {
    /** What a report was worked out from: the same again needs no new one. */
    private data class Inputs(val date: LocalDate, val profile: UserProfile, val workouts: List<WorkoutAnalysis>, val data: List<Long?>)

    /** The last report worked out and what from, kept when nobody watches so a return with nothing new reuses it. */
    @Volatile private var last: Pair<Inputs, ReportOutcome.Ready>? = null

    val outcome: StateFlow<ReportOutcome?> =
        combine(date.distinctUntilChanged(), profile.distinctUntilChanged(), workouts.settled(), repo.dataStamp().settled()) { d, p, w, s -> Inputs(d, p, w, s) }
            .distinctUntilChanged()
            .mapLatest { i ->
                last?.takeIf { it.first == i }?.second ?: withContext(Dispatchers.Default) {
                    // mapLatest cancels this when newer data arrives; that's not a failure, so let it through.
                    try {
                        ReportOutcome.Ready(repo.reports(i.date, i.profile, i.workouts)).also { last = i to it }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w("OpenStrap", "report for ${i.date} failed", e)
                        ReportOutcome.Failed(i.date)
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private companion object {
        /** How long a burst of changes (a sync's batches, workout zones filling in) settles before the report is redone. */
        const val SETTLE_MS = 3_000L

        /** The first value at once, so the first report doesn't wait; later ones once they've settled for [SETTLE_MS]. */
        @OptIn(FlowPreview::class)
        fun <T> Flow<T>.settled(): Flow<T> = flow {
            var first = true
            emitAll(debounce { if (first) 0L.also { first = false } else SETTLE_MS })
        }
    }
}
