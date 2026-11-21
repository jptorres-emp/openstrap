package org.openstrap.app.data

import androidx.annotation.StringRes
import org.openstrap.app.R
import org.openstrap.app.data.db.DayTagRow
import org.openstrap.app.data.db.HealthDao
import java.time.LocalDate

/**
 * The fork's journal (jptorres-emp/openstrap), as WHOOP has it: what you did on a day that the strap can't see, ticked
 * the morning after, so the reports can tell what each does to the next morning's Recovery ([ForkHabits]). Kept as day
 * tags (`day_tag`, the day the habit happened), as the fork kept them before. A day counts once it has been answered,
 * even with nothing ticked ([ANSWERED]): a day nobody answered is left out rather than read as a day without them.
 */
enum class JournalHabit(val key: String, @StringRes val label: Int, @StringRes val hint: Int) {
    Alcohol("alcohol", R.string.fork_habit_alcohol, R.string.fork_habit_alcohol_hint),
    LateCaffeine("caffeine_late", R.string.fork_habit_caffeine, R.string.fork_habit_caffeine_hint),
    LateMeal("late_meal", R.string.fork_habit_late_meal, R.string.fork_habit_late_meal_hint),
    ScreensInBed("screens_bed", R.string.fork_habit_screens, R.string.fork_habit_screens_hint),
    ReadingInBed("reading_bed", R.string.fork_habit_reading, R.string.fork_habit_reading_hint),
    Stress("stress", R.string.fork_habit_stress, R.string.fork_habit_stress_hint),
    Travel("travel", R.string.fork_habit_travel, R.string.fork_habit_travel_hint),
    Sick("sick", R.string.fork_habit_sick, R.string.fork_habit_sick_hint),
    Meditation("meditation", R.string.fork_habit_meditation, R.string.fork_habit_meditation_hint),
    Stretching("stretching", R.string.fork_habit_stretching, R.string.fork_habit_stretching_hint),
    ;

    companion object {
        /** The tag that says a day was answered, whatever was ticked. */
        const val ANSWERED = "answered"

        fun of(key: String): JournalHabit? = entries.firstOrNull { it.key == key }

        /**
         * The habits ticked for a day, from its tags; null when the day wasn't answered. A day with a habit's tag and no
         * [ANSWERED] (tagged before the journal) counts as answered.
         */
        fun ofDay(tags: Collection<String>): Set<JournalHabit>? {
            val habits = tags.mapNotNull(::of).toSet()
            return habits.takeIf { ANSWERED in tags || it.isNotEmpty() }
        }

        /** The tags that keep [habits] as a day's answer. */
        fun tags(habits: Set<JournalHabit>): List<String> = listOf(ANSWERED) + habits.map { it.key }
    }
}

/** Reads and writes the journal's days in the day tags, leaving any other tag a day has alone. */
object Journal {
    /** A day of the journal: its [date] and what was ticked, null when it wasn't answered. */
    data class Entry(val date: LocalDate, val habits: Set<JournalHabit>?)

    /** Yesterday's entry, the one the journal asks for. */
    suspend fun yesterday(dao: HealthDao, clock: () -> LocalDate = { LocalDate.now() }): Entry =
        clock().minusDays(1).let { day -> Entry(day, JournalHabit.ofDay(dao.dayTagsOnce(day, day).map { it.tag })) }

    /** [habits] as [date]'s answer, in place of what the journal had for it. */
    suspend fun save(dao: HealthDao, date: LocalDate, habits: Set<JournalHabit>) {
        (JournalHabit.entries.map { it.key } + JournalHabit.ANSWERED).forEach { dao.deleteDayTag(date, it) }
        dao.insertDayTags(JournalHabit.tags(habits).map { DayTagRow(date, it) })
    }
}
