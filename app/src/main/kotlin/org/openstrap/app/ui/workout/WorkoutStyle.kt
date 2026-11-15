package org.openstrap.app.ui.workout

import androidx.compose.ui.graphics.Color
import org.openstrap.app.presentation.WorkoutColors
import org.openstrap.app.presentation.WorkoutWords

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

private val FamilyDistance = Color(0xFFC6F55A)
private val FamilyIndoorCardio = Color(0xFF52ACFC)
private val FamilyStrength = Color(0xFFFF8410)
private val FamilyIntervals = Color(0xFFFF5A5F)
private val FamilyMindBody = Color(0xFFB67AE8)
private val FamilyGames = Color(0xFF6E8FB8)

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

/** Rest and recovery, in any family (workout-rules.md, Colour): strength rest, HIIT rest. */
internal val RestTeal = Color(0xFF7CF2C9)

/** A paused workout's hero (workout-rules.md, Tokens). */
internal val PausedGrey = Color(0xFFAEB6C2)

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
