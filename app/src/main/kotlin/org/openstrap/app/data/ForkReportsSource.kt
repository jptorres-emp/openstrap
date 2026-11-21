package org.openstrap.app.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.openstrap.app.data.db.HealthDao
import org.openstrap.app.data.settings.UserProfile
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Reads the fork's reports (jptorres-emp/openstrap) for today: every morning of the last [ForkReports.DAYS] from one
 * read of the engine ([RecoveryEngine.mornings]), each day's steps, workouts and journal, and works out the week, the
 * month, the day's Load target and the habits' effects from them ([ForkReports], [ForkTarget], [ForkHabits]).
 */
class ForkReportsSource(private val dao: HealthDao, private val workouts: WorkoutRepository) {
    suspend fun load(
        profile: UserProfile,
        clock: () -> LocalDate = { LocalDate.now() },
        zone: ZoneId = ZoneId.systemDefault(),
        dispatcher: CoroutineDispatcher = Dispatchers.Default,
    ): ForkReports.Report = withContext(dispatcher) {
        val today = clock()
        val from = today.minusDays(ForkReports.DAYS - 1L)
        val read = RecoveryEngine(dao).mornings(today, profile, zone)
        val mornings = read.mornings.associateBy { it.date }
        val steps = dao.dailyStepsOnce(DayWindow.of(from, zone).from, DayWindow.of(today, zone).to)
            .associate { LocalDate.parse(it.day) to it.value.roundToInt() }
        val tags = dao.dayTagsOnce(from, today).groupBy({ it.day }, { it.tag })
        val sessions = workouts.workouts().first()
            .filter { it.workout.sportType != Workouts.GENERIC_ACTIVITY }
            .groupBy { Instant.ofEpochSecond(it.workout.start).atZone(zone).toLocalDate() }
        fun hour(w: WorkoutAnalysis) = Instant.ofEpochSecond(w.workout.start).atZone(zone).hour
        val days = generateSequence(from) { it.plusDays(1) }.takeWhile { it <= today }.map { date ->
            val m = mornings[date]
            val started = sessions[date].orEmpty()
            ForkReports.Day(
                date, m?.score, m?.strapScore, m?.alert == true, m?.hrv, m?.restingHr, m?.asleepMinutes,
                if (date == today) read.todayLoad else read.loads[date], steps[date],
                started.size, started.any { hour(it) >= LATE_WORKOUT_HOUR }, date == today,
                tags[date]?.let { JournalHabit.ofDay(it) },
            )
        }.toList()
        val training = sessions.filterValues { list -> list.any { it.end - it.workout.start >= TRAINING_SECONDS } }.keys
        ForkReports.Report(
            today, ForkReports.week(days, today), ForkReports.month(days, today),
            ForkTarget.target(days.last(), read.loads, training, read.usualLoad), read.todayLoad, read.usualLoad,
            ForkHabits.effects(days, read.usualLoad), days.firstOrNull { it.date == today.minusDays(1) }?.journal,
        )
    }

    private companion object {
        /** A workout from this hour on is a late one. */
        const val LATE_WORKOUT_HOUR = 20
        /** A workout this long or longer makes a training day for the Load target. */
        const val TRAINING_SECONDS = 10 * 60
    }
}
