package org.openstrap.app.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** The part of the day Today is shaped for (spec 4.1), highest priority first. */
enum class Moment { During, After, WorkoutWindow, Waking, Night, Evening, Midday }

/**
 * Which [Moment] it is now (spec 4.1). It only reads what the app already has (a recording, workouts, the night's
 * sleep and your usual bedtime) and works out nothing about the body. When moments overlap the first in [Moment]
 * wins.
 */
object MomentResolver {
    /** How long after waking the morning lasts, and Readiness stays the Today hero (`chargeLeads`). */
    const val WAKING_HOURS = 3
    private const val WAKING_SECONDS = WAKING_HOURS * 3600L

    /** How long a finished workout stays the moment. */
    const val AFTER_SECONDS = 2 * 3600L

    /** The workout window runs this long either side of your usual workout time. */
    const val WINDOW_MINUTES = 45

    /** Night starts this long before your usual bedtime, and evening [EVENING_MINUTES] before it. */
    const val NIGHT_LEAD_MINUTES = 45
    const val EVENING_MINUTES = 180

    /** Night ends here, the morning after. */
    val NIGHT_ENDS: LocalTime = LocalTime.of(4, 0)

    /** The morning starts here when no main sleep ended today. */
    val WAKE_FALLBACK: LocalTime = LocalTime.of(5, 0)

    /** Bedtime when none is known yet: three hours after it, evening starts at 19:00 as the spec asks. */
    val BEDTIME_FALLBACK: LocalTime = LocalTime.of(22, 0)

    /** Usual workout time looks back this many days and needs at least [USUAL_WORKOUT_MIN] workouts. */
    const val USUAL_WORKOUT_DAYS = 28
    const val USUAL_WORKOUT_MIN = 3

    /** True once [now] is [WAKING_HOURS] past [wake] (epoch seconds). Shared with `chargeLeads`. */
    fun wakingOver(now: Long, wake: Long): Boolean = now - wake >= WAKING_SECONDS

    /**
     * The moment at [now] for [day]. [lastWorkoutEnd] and [sleepEnd] (today's main sleep) are epoch seconds,
     * [workoutStarts] the start of every workout, phone or strap, for [usualWorkoutTime]. A past [day] is always
     * [Moment.Waking]: it shows how that day began.
     */
    fun resolve(
        now: Instant,
        zone: ZoneId,
        day: LocalDate,
        recording: Boolean,
        lastWorkoutEnd: Long?,
        sleepEnd: Long?,
        usualBedtime: LocalTime?,
        workoutStarts: List<Long>,
    ): Moment {
        val local = now.atZone(zone)
        if (day < local.toLocalDate()) return Moment.Waking
        val t = now.epochSecond
        val clock = local.toLocalTime()
        if (recording) return Moment.During
        if (lastWorkoutEnd != null && t >= lastWorkoutEnd && t - lastWorkoutEnd < AFTER_SECONDS) return Moment.After
        val usualWorkout = usualWorkoutTime(workoutStarts, now, zone)
        if (usualWorkout != null && inWindow(clock, usualWorkout)) return Moment.WorkoutWindow
        val wake = sleepEnd ?: local.toLocalDate().atTime(WAKE_FALLBACK).atZone(zone).toEpochSecond()
        if (t >= wake && !wakingOver(t, wake)) return Moment.Waking
        // Evening and night are read on a clock that starts at noon, so night runs on past midnight to 04:00.
        val bed = afterNoon(usualBedtime ?: BEDTIME_FALLBACK)
        val nightEnds = afterNoon(NIGHT_ENDS)
        val nightStarts = (bed - NIGHT_LEAD_MINUTES).coerceAtLeast(0)
        val m = afterNoon(clock)
        if (m in nightStarts until nightEnds) return Moment.Night
        if (m in (bed - EVENING_MINUTES).coerceAtLeast(0) until minOf(nightStarts, nightEnds)) return Moment.Evening
        return Moment.Midday
    }

    /**
     * Your usual workout time at [now]: the median start, as a clock time, of the workouts in the last
     * [USUAL_WORKOUT_DAYS] days on [now]'s weekday when there are [USUAL_WORKOUT_MIN] or more, else of all of them when
     * there are that many, else null (no workout window yet).
     */
    fun usualWorkoutTime(starts: List<Long>, now: Instant, zone: ZoneId): LocalTime? {
        val from = now.epochSecond - USUAL_WORKOUT_DAYS * 86_400L
        val recent = starts.filter { it in from until now.epochSecond }.map { Instant.ofEpochSecond(it).atZone(zone) }
        val weekday: DayOfWeek = now.atZone(zone).dayOfWeek
        val sameDay = recent.filter { it.dayOfWeek == weekday }
        val pick = when {
            sameDay.size >= USUAL_WORKOUT_MIN -> sameDay
            recent.size >= USUAL_WORKOUT_MIN -> recent
            else -> return null
        }
        val minutes = pick.map { it.toLocalTime().toSecondOfDay() / 60 }.sorted()
        val median = (minutes[(minutes.size - 1) / 2] + minutes[minutes.size / 2]) / 2
        return LocalTime.ofSecondOfDay(median * 60L)
    }

    /** Within [WINDOW_MINUTES] either side of [usual], the end minute left out, across midnight too. */
    private fun inWindow(clock: LocalTime, usual: LocalTime): Boolean {
        val d = Math.floorMod(clock.toSecondOfDay() / 60 - usual.toSecondOfDay() / 60 + 720, 1440) - 720
        return d in -WINDOW_MINUTES until WINDOW_MINUTES
    }

    /** Minutes since noon: 12:00 is 0, 04:00 the next morning is 960. */
    private fun afterNoon(t: LocalTime): Int = Math.floorMod(t.toSecondOfDay() / 60 - 720, 1440)
}
