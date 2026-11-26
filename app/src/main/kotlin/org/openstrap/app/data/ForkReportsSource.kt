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
 * month, today's plan, the days of the week and the habits' effects from them ([ForkReports], [ForkTarget],
 * [ForkPlan], [ForkTally], [ForkHabits]).
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
        val all = workouts.workouts().first().filter { it.workout.sportType != Workouts.GENERIC_ACTIVITY }
        val sessions = all.groupBy({ Instant.ofEpochSecond(it.workout.start).atZone(zone).toLocalDate() }) { w ->
            val late = Instant.ofEpochSecond(w.workout.start).atZone(zone).hour >= LATE_WORKOUT_HOUR
            ForkReports.Session(w.workout.sportType, (w.end - w.workout.start).coerceAtLeast(0), late)
        }
        val days = generateSequence(from) { it.plusDays(1) }.takeWhile { it <= today }.map { date ->
            day(date, mornings[date], zone).copy(
                load = if (date == today) read.todayLoad else read.loads[date], steps = steps[date],
                sessions = sessions[date].orEmpty(), partial = date == today, journal = tags[date]?.let { JournalHabit.ofDay(it) },
            )
        }.toList()
        val training = sessions.filterValues { list -> list.any { it.seconds >= TRAINING_SECONDS } }.keys
        val marked = ForkTarget.mark(days, read.loads, training, read.usual)
        val target = ForkTarget.target(marked.last(), read.loads, training, read.usualLoad)
        ForkReports.Report(
            today, ForkReports.week(marked, today, read.usual), ForkReports.month(marked, today, read.usual),
            ForkReports.normal(marked), ForkTally.weekdays(marked), ForkPlan.plan(marked, read.sleepNeed, target, read.todayLoad),
            ForkHabits.effects(marked, read.usualLoad), marked.firstOrNull { it.date == today.minusDays(1) }?.journal,
        )
    }

    /** [date]'s morning as a report's day: its scores and its night, the bedtime read from the noon before. */
    private fun day(date: LocalDate, m: ForkReports.Morning?, zone: ZoneId): ForkReports.Day {
        fun minuteOf(t: Long) = Instant.ofEpochSecond(t).atZone(zone).let { it.hour * MINUTES_PER_HOUR + it.minute }
        val inBed = m?.bed?.let { bed -> m.wake?.let { ((it - bed) / SECONDS_PER_MINUTE).toInt() } }
        return ForkReports.Day(
            date, m?.score, m?.strapScore, m?.alert == true, m?.hrv, m?.restingHr, m?.asleepMinutes,
            bedtime = m?.bed?.let { Math.floorMod(minuteOf(it) - NOON, DAY_MINUTES) }, wakeTime = m?.wake?.let(::minuteOf),
            inBedMinutes = inBed, deepRem = m?.deepRem,
        )
    }

    private companion object {
        /** A workout from this hour on is a late one. */
        const val LATE_WORKOUT_HOUR = 20
        /** A workout this long or longer makes a training day for the Load target. */
        const val TRAINING_SECONDS = 10 * 60
        const val MINUTES_PER_HOUR = 60
        const val SECONDS_PER_MINUTE = 60
        const val NOON = 12 * 60
        const val DAY_MINUTES = 24 * 60
    }
}
