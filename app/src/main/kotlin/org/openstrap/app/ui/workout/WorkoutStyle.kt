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

/**
 * Each kind of workout's colour (workout-rules.md, Tokens; board HaloWorkoutRules): distance lime, indoor cardio blue,
 * strength orange, HIIT red, mind and body violet, games slate blue.
 */
internal fun familyColor(family: org.openstrap.app.data.Workouts.Family): Color = when (family) {
    org.openstrap.app.data.Workouts.Family.Distance -> Color(0xFFC6F55A)
    org.openstrap.app.data.Workouts.Family.IndoorCardio -> Color(0xFF52ACFC)
    org.openstrap.app.data.Workouts.Family.Strength -> Color(0xFFFF8410)
    org.openstrap.app.data.Workouts.Family.Intervals -> Color(0xFFFF5A5F)
    org.openstrap.app.data.Workouts.Family.MindBody -> Color(0xFFB67AE8)
    org.openstrap.app.data.Workouts.Family.Games -> Color(0xFF6E8FB8)
}

/** Rest and recovery, in any family (workout-rules.md, Colour): strength rest, HIIT rest. */
internal val RestTeal = Color(0xFF7CF2C9)

/** A paused workout's hero (workout-rules.md, Tokens). */
internal val PausedGrey = Color(0xFFAEB6C2)

/**
 * Where the light comes from on a sport's Ready and Live (workout-rules.md, Tokens): strength from the morning side,
 * mind and body low on the left, the rest from the upper right. Degrees counterclockwise from the right edge.
 */
internal fun familyLightAngle(family: org.openstrap.app.data.Workouts.Family): Float = when (family) {
    org.openstrap.app.data.Workouts.Family.Strength -> 225f
    org.openstrap.app.data.Workouts.Family.MindBody -> 160f
    else -> 45f
}
