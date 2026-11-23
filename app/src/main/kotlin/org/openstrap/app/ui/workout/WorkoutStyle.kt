package org.openstrap.app.ui.workout

import androidx.compose.ui.graphics.Color
import org.openstrap.app.presentation.WorkoutColors
import org.openstrap.app.presentation.WorkoutWords
import org.openstrap.app.ui.theme.Ink
import org.openstrap.app.ui.theme.SportColors

/** Zone 1 (easy) to 5 (max), cool to hot ([WorkoutColors.ZONES]); during a workout the app takes on the current one. */
internal val ZONE_COLORS: List<Color> = WorkoutColors.ZONES.map(::Color)
/**
 * A workout's top card colour: the zone it spent most time in, so the card agrees with its sentence ("mostly in zone
 * 1"), or [fallback] with no heart rate in any zone.
 */
internal fun mainZoneColor(zones: List<Int>, fallback: Color): Color =
    zones.indices.maxByOrNull { zones[it] }?.takeIf { zones[it] > 0 }?.let { ZONE_COLORS.getOrNull(it) } ?: fallback

/** Read each time, so the names follow the app's language. */
internal val ZONE_NAMES: List<String> get() = WorkoutWords.ZONE_NAMES

private val FamilyDistance = SportColors.distance
private val FamilyIndoorCardio = SportColors.indoorCardio
private val FamilyStrength = SportColors.strength
private val FamilyIntervals = SportColors.intervals
private val FamilyMindBody = SportColors.mindBody
private val FamilyGames = SportColors.games

/**
 * Each kind of workout's colour (workout-rules.md, Tokens; board HaloWorkoutRules): distance lime, indoor cardio blue,
 * strength orange, HIIT red, mind and body violet, games slate blue.
 */
internal fun familyColor(family: org.openstrap.app.data.Workouts.Family): Color = when (family) {
    org.openstrap.app.data.Workouts.Family.Distance -> FamilyDistance
    org.openstrap.app.data.Workouts.Family.IndoorCardio -> FamilyIndoorCardio
    org.openstrap.app.data.Workouts.Family.Strength -> FamilyStrength
    org.openstrap.app.data.Workouts.Family.Intervals -> FamilyIntervals
    org.openstrap.app.data.Workouts.Family.MindBody -> FamilyMindBody
    org.openstrap.app.data.Workouts.Family.Games -> FamilyGames
}

/**
 * The colour of a workout of [type] shown as [family]: one sport, one colour, so the indoor side of a sport with two
 * (a treadmill, indoor cycling) takes its outdoor side's family colour, an indoor run the run's lime. A [family] the
 * workout's structure gave it (rounds make intervals) keeps its own colour.
 */
internal fun sportFamilyColor(
    type: Int,
    family: org.openstrap.app.data.Workouts.Family = org.openstrap.app.data.Workouts.family(type),
): Color = familyColor(colorFamily(type, family))

/** The family whose colour and light [type] takes as [family] ([sportFamilyColor]). */
internal fun colorFamily(
    type: Int,
    family: org.openstrap.app.data.Workouts.Family = org.openstrap.app.data.Workouts.family(type),
): org.openstrap.app.data.Workouts.Family =
    if (family == org.openstrap.app.data.Workouts.family(type)) {
        org.openstrap.app.data.Workouts.family(org.openstrap.app.data.Workouts.tileType(type))
    } else {
        family
    }

/** A paused workout's hero (workout-rules.md, Tokens). */
internal val PausedGrey = Ink.Paused

/**
 * Where the light comes from on a sport's Ready and Live (workout-rules.md, Tokens): strength from the morning side,
 * mind and body low on the left, the rest from the upper right. Degrees counterclockwise from the right edge.
 */
private const val LIGHT_STRENGTH = 225f
private const val LIGHT_MIND = 160f
private const val LIGHT_DEFAULT = 45f

internal fun familyLightAngle(family: org.openstrap.app.data.Workouts.Family): Float = when (family) {
    org.openstrap.app.data.Workouts.Family.Strength -> LIGHT_STRENGTH
    org.openstrap.app.data.Workouts.Family.MindBody -> LIGHT_MIND
    else -> LIGHT_DEFAULT
}
