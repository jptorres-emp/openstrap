package org.openstrap.app.ui.workout

import org.openstrap.app.R
import org.openstrap.app.data.Route
import org.openstrap.app.data.WorkoutAnalysis
import org.openstrap.app.data.SportTypes
import org.openstrap.app.data.Workouts
import org.openstrap.app.device.ConnectionState
import org.openstrap.app.i18n.Strings
import org.openstrap.app.presentation.DayWords
import org.openstrap.app.presentation.Units
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt
import org.openstrap.app.presentation.WorkoutCopy

/*
 * What Choose and Ready say (workouts spec 2.1, 2.2, 7): the sport, the goal and what the strap will do, never the
 * family. Pure, so it's tested without the screens. The text is in res/values/strings_workout_start.xml.
 */
object StartCopy {
    /** The strap's one line on Choose: "Strap ready, 72 bpm", or what's wrong. */
    fun strapLine(connection: ConnectionState, bpm: Int?, strings: Strings = Strings.app): String = when (connection) {
        is ConnectionState.Connected ->
            bpm?.let { strings.format(R.string.workout_start_strap_ready_bpm, it) } ?: strings.get(R.string.workout_start_strap_ready)
        ConnectionState.Connecting, ConnectionState.Authenticating -> strings.get(R.string.workout_start_strap_connecting)
        ConnectionState.NotPaired -> strings.get(R.string.workout_start_strap_not_paired)
        ConnectionState.Disconnected, ConnectionState.BluetoothOff, is ConnectionState.Failed ->
            strings.get(R.string.workout_start_strap_not_connected)
    }

    /** The goal chip for no goal, named for the sport: "Just run", "Just walk", "Just ride", else "Just go". */
    fun noGoal(type: Int, strings: Strings = Strings.app): String = strings.get(
        when {
            type in SportTypes.RUNS -> R.string.workout_start_just_run
            type in SportTypes.WALKS -> R.string.workout_start_just_walk
            Workouts.tileType(type) == 4 -> R.string.workout_start_just_ride
            else -> R.string.workout_start_just_go
        },
    )

    /* -------------------------------------------- Strength's plan ------------------------------------------- */

    val PLAN: String get() = Strings.app.get(R.string.workout_start_strength_plan)
    val OPEN_SETS_NOTE: String get() = Strings.app.get(R.string.workout_start_open_sets_note)

    /** What the plan opens on when there's none from last time. */
    const val DEFAULT_SETS = 5

    val END_REST_EARLY: String get() = Strings.app.get(R.string.workout_start_end_rest_early)

    /** End rest early's note, with the level it ends at: "Once you're under 110 bpm". */
    fun endRestNote(bpm: Int, strings: Strings = Strings.app): String = strings.format(R.string.workout_start_end_rest_note, bpm)

    /** The level End rest early turns on at when none was chosen before. */
    const val DEFAULT_BELOW = 110

    val LOG_SETS: String get() = Strings.app.get(R.string.workout_start_log_sets)
    val LOG_SETS_NOTE: String get() = Strings.app.get(R.string.workout_start_log_sets_note)

    /** Under two minutes in seconds ("90 s"), then minutes: "2 min", "2 min 30 s". */
    fun restText(seconds: Long, strings: Strings = Strings.app): String = when {
        seconds < 120 -> strings.format(R.string.workout_start_seconds, seconds)
        seconds % 60 == 0L -> strings.format(R.string.workout_start_minutes, seconds / 60)
        else -> strings.format(R.string.workout_start_minutes_seconds, seconds / 60, seconds % 60)
    }

    /** HIIT's presets by their rounds and name, so a structure that is one reads by its name ("Tabata"). */
    private fun presets(strings: Strings): List<Pair<org.openstrap.app.data.Structure, String>> = listOf(
        org.openstrap.app.data.Structure.TABATA to strings.get(R.string.workout_start_preset_tabata),
        org.openstrap.app.data.Structure.FORTY_TWENTY to strings.get(R.string.workout_start_preset_forty_twenty),
        org.openstrap.app.data.Structure.EVERY_MINUTE to strings.get(R.string.workout_start_every_minute),
    )

    /** HIIT's every minute rounds, as their tab names them. */
    val EVERY_MINUTE: String get() = Strings.app.get(R.string.workout_start_every_minute)

    val HIIT_FREE_NOTE: String get() = Strings.app.get(R.string.workout_start_hiit_free_note)
    val WARM_UP: String get() = Strings.app.get(R.string.workout_start_warm_up)
    val WARM_UP_NOTE: String get() = Strings.app.get(R.string.workout_start_warm_up_note)

    const val MAX_ROUNDS = 50

    /** A structure's name: a preset's ("Tabata"), else what it is ("6 × 400 m", "8 × 2 min", "30 / 15, 6 rounds"). */
    fun structureName(s: org.openstrap.app.data.Structure, strings: Strings = Strings.app): String {
        presets(strings).firstOrNull { it.first.code == s.warm(false).code }?.let { return it.second }
        return when (s) {
            is org.openstrap.app.data.Structure.Timed ->
                strings.plural(R.plurals.workout_start_structure_timed, s.rounds, s.work, s.rest, s.rounds)
            is org.openstrap.app.data.Structure.EveryMinute ->
                strings.plural(R.plurals.workout_start_structure_every, s.rounds, restText(s.period, strings), s.rounds)
            // As Ready's rep field and note say it ([repParts]): "6 × 1.5 km", "8 × 1 min".
            is org.openstrap.app.data.Structure.Reps ->
                strings.format(R.string.workout_start_structure_reps, s.rounds, repLength(s, strings))
            is org.openstrap.app.data.Structure.Periods -> periodsName(s, strings)
        }
    }

    /** A rep's length with its unit: "400 m", "1.5 km", "4 min", "90 s". */
    fun repText(r: org.openstrap.app.data.Structure.Reps, strings: Strings = Strings.app): String = repLength(r, strings)

    /** A rep's length with its unit, as [repParts] splits it: "400 m", "1.5 km", "4 min", "90 s". */
    private fun repLength(r: org.openstrap.app.data.Structure.Reps, strings: Strings): String = when {
        r.meters != null -> if (r.meters >= 1000) {
            strings.format(R.string.workout_start_km, distanceValue(r.meters))
        } else {
            strings.format(R.string.workout_start_meters, r.meters)
        }
        (r.seconds ?: 0) % 60 == 0L -> strings.format(R.string.workout_start_minutes, (r.seconds ?: 0) / 60)
        else -> strings.format(R.string.workout_start_seconds, r.seconds ?: 0)
    }

    /** "Halves", "Quarters" or "3 periods". */
    fun periodsName(s: org.openstrap.app.data.Structure.Periods, strings: Strings = Strings.app): String = when (s.rounds) {
        2 -> strings.get(R.string.workout_start_halves)
        4 -> strings.get(R.string.workout_start_quarters)
        else -> strings.plural(R.plurals.workout_start_periods, s.rounds, s.rounds)
    }

    /**
     * A period by its place: "1st half", "3rd quarter", "Period 2". Each half and quarter is its own string, since
     * languages put the place in the word differently. A game never has more of them than it was set up with, so past
     * those it reads as a numbered period.
     */
    fun periodName(index: Int, of: Int, strings: Strings = Strings.app): String {
        val n = index + 1
        val id = when (of) {
            2 -> HALVES.getOrNull(index)
            4 -> QUARTERS.getOrNull(index)
            else -> null
        }
        return if (id != null) strings.get(id) else strings.format(R.string.workout_start_period_n, n)
    }

    private val HALVES = listOf(R.string.workout_start_half_1, R.string.workout_start_half_2)
    private val QUARTERS = listOf(
        R.string.workout_start_quarter_1, R.string.workout_start_quarter_2,
        R.string.workout_start_quarter_3, R.string.workout_start_quarter_4,
    )

    /** What the break between periods is called. */
    fun breakName(of: Int, strings: Strings = Strings.app): String =
        strings.get(if (of == 2) R.string.workout_start_half_time else R.string.workout_start_break)

    val MIND_OPEN_NOTE: String get() = Strings.app.get(R.string.workout_start_mind_open_note)

    /** What a game's strap does: "...The strap buzzes at the end of each half and break.", or nothing in free play. */
    fun gamesNote(p: org.openstrap.app.data.Structure.Periods?, strings: Strings = Strings.app): String {
        p ?: return strings.get(R.string.workout_start_games_status)
        val breaks = p.rest > 0 && p.rounds > 1
        return strings.get(
            when (p.rounds) {
                2 -> if (breaks) R.string.workout_start_games_note_half_break else R.string.workout_start_games_note_half
                4 -> if (breaks) R.string.workout_start_games_note_quarter_break else R.string.workout_start_games_note_quarter
                else -> if (breaks) R.string.workout_start_games_note_period_break else R.string.workout_start_games_note_period
            },
        )
    }

    /** All of a timed structure's time, rounds of work and the rests between, for Ready: "4:00". */
    fun totalTime(s: org.openstrap.app.data.Structure): Long? = when (s) {
        is org.openstrap.app.data.Structure.Timed -> s.rounds * s.work + (s.rounds - 1) * s.rest
        is org.openstrap.app.data.Structure.EveryMinute -> s.rounds * s.period
        is org.openstrap.app.data.Structure.Reps -> null
        is org.openstrap.app.data.Structure.Periods -> s.rounds * s.length + (s.rounds - 1) * s.rest
    }

    /**
     * Your usual's line: what you did last time and when ("Last time 5.2 km, Tuesday"): the distance for a distance
     * sport that has one, the sets for strength, else the minutes.
     */
    fun lastTime(
        a: WorkoutAnalysis, today: LocalDate, zone: java.time.ZoneId, locale: Locale = Locale.getDefault(), strings: Strings = Strings.app,
    ): String {
        val what = when {
            a.distanceMeters != null && a.distanceMeters >= 100 && Workouts.family(a.workout.sportType) == Workouts.Family.Distance ->
                Units.distance(a.distanceMeters, Units.Precision.Workout, locale = locale).orEmpty()
            a.sets != null && a.sets > 0 -> strings.plural(R.plurals.workout_start_sets, a.sets, a.sets)
            else -> strings.format(R.string.workout_start_minutes, (a.activeSeconds / 60).coerceAtLeast(1))
        }
        val day = java.time.Instant.ofEpochSecond(a.workout.start).atZone(zone).toLocalDate()
        return strings.format(R.string.workout_start_last_time, what, DayWords.inSentence(day, today, locale))
    }

    /** A setting's time under Ready's hero: whole minutes in min ("1 min", "20 min"), else as [restText] ("90 s"). */
    fun shortTime(seconds: Long, strings: Strings = Strings.app): String =
        if (seconds >= 60 && seconds % 60 == 0L) {
            strings.format(R.string.workout_start_minutes, seconds / 60)
        } else {
            restText(seconds, strings)
        }

    /**
     * A minus or plus ([up]) on a setting: to the next multiple of [step] that way, kept within [range]. A value off
     * the steps (a saved 95 s) goes to the step next to it.
     */
    fun stepBy(value: Long, step: Long, range: LongRange, up: Boolean): Long {
        val next = if (up) Math.floorDiv(value, step) * step + step else Math.floorDiv(value - 1, step) * step
        return next.coerceIn(range)
    }

    /** A run rep's minus or plus: 100 m steps under a km and half km steps from it, or 30 s steps for a rep by time. */
    fun stepRep(r: org.openstrap.app.data.Structure.Reps, up: Boolean): org.openstrap.app.data.Structure.Reps {
        val m = r.meters ?: return r.copy(seconds = stepBy(r.seconds ?: REP_STEP_S, REP_STEP_S, REP_SECONDS, up))
        val step = if (m > 1000 || (m == 1000 && up)) 500L else 100L
        return r.copy(meters = stepBy(m.toLong(), step, REP_METERS, up).toInt())
    }

    private const val REP_STEP_S = 30L
    private val REP_SECONDS = 30L..3600L
    private val REP_METERS = 100L..50_000L

    /* -------------------------------------------- Ready's Outdoor switch ------------------------------------- */

    /** The Outdoor switch's line when on: what GPS gives, with how close its fix is ("ready to 6 m"). */
    fun outdoorNote(accuracy: Float?, strings: Strings = Strings.app): String = when {
        accuracy == null || accuracy > 1000 -> strings.get(R.string.ready_outdoor_gps_finding)
        accuracy > Route.MAX_ACCURACY_M -> strings.format(R.string.ready_outdoor_gps_close, accuracy.toInt())
        else -> strings.format(R.string.ready_outdoor_gps_ready, accuracy.roundToInt().coerceAtLeast(1))
    }

    /** The Outdoor switch's line when off: indoors, with the distance from steps where [type] is on foot. */
    fun indoorLine(type: Int, strings: Strings = Strings.app): String =
        strings.get(if (type in SportTypes.ON_FOOT) R.string.ready_outdoor_off_steps else R.string.ready_outdoor_off)

    /** Start on Ready, naming the sport as its tile does: "Start run" indoors too, the Outdoor switch says which. */
    fun startLabel(type: Int, strings: Strings = Strings.app): String =
        WorkoutCopy.startLabel(Workouts.sidesOf(type)?.outdoor ?: type, strings)

    /* -------------------------------------------- Ready's goal ---------------------------------------------- */

    /** The goal kinds' names after the sport's [noGoal], on Ready's goal type tabs or menu. */
    val DISTANCE: String get() = Strings.app.get(R.string.workout_start_kind_distance)
    val TIME: String get() = Strings.app.get(R.string.workout_start_kind_time)
    val ZONE: String get() = Strings.app.get(R.string.workout_start_kind_zone)
    val INTERVALS: String get() = Strings.app.get(R.string.workout_start_kind_intervals)

    /** No goal's note: what's recorded, the distance only where there is one. */
    fun noGoalNote(hasDistance: Boolean, strings: Strings = Strings.app): String =
        strings.get(if (hasDistance) R.string.workout_start_no_goal_note_distance else R.string.workout_start_no_goal_note)

    /** A distance goal's value in km, as typed: "5", "7.5", "12.45", and a half marathon "21.1". */
    fun distanceValue(meters: Int): String =
        if (meters % 1000 == 0) "${meters / 1000}" else String.format(Locale.ROOT, "%.2f", meters / 1000.0).trimEnd('0').trimEnd('.')

    /** What a distance goal does: "The strap buzzes at halfway and at 7.5 km." */
    fun distanceNote(meters: Int, strings: Strings = Strings.app): String =
        strings.format(R.string.workout_start_distance_note, distanceValue(meters))

    const val MAX_KM = 300

    /** A time goal in one line, as every duration reads ([Units.duration]): "1 h 15", "1 h", "45 min". */
    fun timeValue(seconds: Long): String = Units.duration(seconds)

    const val MAX_HOURS = 10

    /** How the zone buttons work, and what the strap does. */
    val ZONE_NOTE: String get() = Strings.app.get(R.string.workout_start_zone_note)

    /** What run intervals open on before you've chosen any: 6 × 400 m with 90 s rest. */
    val DEFAULT_INTERVALS: org.openstrap.app.data.Structure.Reps = org.openstrap.app.data.Structure.Reps(400, null, 90, 6)

    /** A rep's length as a number and its unit: "400" "m", "1.5" "km", "4" "min", "90" "s". */
    fun repParts(r: org.openstrap.app.data.Structure.Reps, strings: Strings = Strings.app): Pair<String, String> = when {
        r.meters != null -> if (r.meters >= 1000) {
            distanceValue(r.meters) to strings.get(R.string.workout_start_unit_km)
        } else {
            "${r.meters}" to strings.get(R.string.workout_start_unit_m)
        }
        (r.seconds ?: 0) % 60 == 0L -> "${(r.seconds ?: 0) / 60}" to strings.get(R.string.workout_start_unit_min)
        else -> "${r.seconds}" to strings.get(R.string.workout_start_unit_s)
    }

    /**
     * What run intervals do: "The strap buzzes at the end of each 400 m and when rest is up. 2.4 km in all.", or for
     * reps of a time "at the end of each 4 min rep", with "16 min of reps in all".
     */
    fun intervalsNote(r: org.openstrap.app.data.Structure.Reps, strings: Strings = Strings.app): String {
        val each = repLength(r, strings)
        val meters = r.meters ?: return strings.format(
            R.string.workout_start_intervals_note_time, each, timeValue((r.seconds ?: 0) * r.rounds),
        )
        val all = meters * r.rounds
        val total = if (all >= 1000) {
            strings.format(R.string.workout_start_km, distanceValue(all))
        } else {
            strings.format(R.string.workout_start_meters, all)
        }
        return strings.format(R.string.workout_start_intervals_note_distance, each, total)
    }

    const val MAX_REPS = 30

    /** Ask to end when I stop, a workout setting (no longer on Ready). */
    val ASK_TO_END: String get() = Strings.app.get(R.string.workout_start_ask_to_end)
    val ASK_TO_END_NOTE: String get() = Strings.app.get(R.string.workout_start_ask_to_end_note)

    /** Ready's button when an outdoor sport's location is off. */
    val TURN_ON_LOCATION: String get() = Strings.app.get(R.string.workout_start_turn_on_location)
}
