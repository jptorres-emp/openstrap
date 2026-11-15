package org.openstrap.app.data

/**
 * The usual range of a workout's Load as two edges, "Load, usual 80 to 120" (boards HaloSummaryRun and HaloPastWorkout).
 * The middle is [Workouts.usualLoad]; the edges sit [BAND] under and over it, the same share the day Load band
 * and the Above / Below usual tag use, so a Load inside the edges is never tagged off usual.
 */
object WorkoutLoadBand {
    /** The share under and over the usual, the same as `LoadBands.BAND` (a test keeps them equal; data cannot import presentation). */
    const val BAND = 0.25f

    /** The edges around [usual], each rounded to a whole number, or null without a usual. */
    fun edges(usual: Float?): Pair<Int, Int>? =
        usual?.takeIf { it > 0f }?.let { Math.round(it * (1 - BAND)) to Math.round(it * (1 + BAND)) }

    /** The edges of the usual of [loads] (newest first), or null with fewer than [Workouts.USUAL_LOAD_MIN] workouts with a Load. */
    fun of(loads: List<Int?>): Pair<Int, Int>? = edges(Workouts.usualLoad(loads))
}
