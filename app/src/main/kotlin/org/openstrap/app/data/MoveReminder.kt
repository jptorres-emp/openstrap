package org.openstrap.app.data

import java.time.DayOfWeek
import java.time.LocalDateTime

/**
 * A nudge to get up after sitting too long, for a desk day: the strap buzzes when you've taken almost no steps for
 * [MoveSettings.everyMinutes] in your working hours. The strap has no reminder of its own for this, so the app keeps it,
 * from the strap's live step count while connected. Pure, so the rule is tested without a strap; the buzz and the
 * notification are StrapManager's.
 */
object MoveReminder {
    /** Fewer steps than this over the whole stretch counts as sitting: a trip to the printer doesn't reset it. */
    const val MIN_STEPS = 100

    /** The stretches you can choose, in minutes. */
    val EVERY = listOf(45, 60, 90)

    /** The working hours you can choose, as (start, end) minutes of the day. */
    val HOURS = listOf(9 * 60 to 18 * 60, 8 * 60 to 17 * 60, 10 * 60 to 19 * 60)

    /**
     * Whether to nudge at [now]: the switch is on, it's a chosen day within the hours, the strap isn't known to be off
     * the wrist ([worn] false) and no workout is recording, the step counts cover the whole stretch ([steps]: the steps
     * since the stretch began, null when the app hasn't watched that long) with under [MIN_STEPS] in it, and the last
     * nudge ([lastAt], epoch seconds) is a stretch ago or more.
     */
    fun due(
        settings: MoveSettings, now: LocalDateTime, nowEpoch: Long, steps: Int?, lastAt: Long?, worn: Boolean?, recording: Boolean,
    ): Boolean {
        if (!settings.on || recording || worn == false) return false
        if (settings.weekdaysOnly && now.dayOfWeek in WEEKEND) return false
        val minute = now.hour * 60 + now.minute
        // The first nudge can come a stretch after the day starts, and none after it ends.
        if (minute < settings.startMinute + settings.everyMinutes || minute >= settings.endMinute) return false
        if (steps == null || steps >= MIN_STEPS) return false
        return lastAt == null || nowEpoch - lastAt >= settings.everyMinutes * 60L
    }

    /**
     * The steps taken over the [minutes] before [now] from the strap's running totals ([samples]: epoch seconds to the
     * day's total, oldest first); null when the samples don't go back that far. The day's total starts again at
     * midnight, so a drop counts as a new day and only the steps since it are kept.
     */
    fun stepsOver(samples: List<Pair<Long, Int>>, now: Long, minutes: Int): Int? {
        val from = now - minutes * 60L
        val start = samples.lastOrNull { it.first <= from } ?: return null
        var steps = 0
        var previous = start.second
        for ((t, total) in samples) {
            if (t <= start.first || t > now) continue
            steps += if (total >= previous) total - previous else total
            previous = total
        }
        return steps
    }

    private val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

    /** The nudge's buzz: two short pulses, unlike a call's or a notification's. */
    val PATTERN = listOf(300, 200, 300)
}

/** The move reminder's choices: on or off, the working hours, how long a stretch, and weekdays only or every day. */
data class MoveSettings(
    val on: Boolean = false,
    val startMinute: Int = 9 * 60,
    val endMinute: Int = 18 * 60,
    val everyMinutes: Int = 60,
    val weekdaysOnly: Boolean = true,
)
