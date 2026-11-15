package org.openstrap.app.data

/**
 * The sections sports are listed in on Choose a sport, Change sport and Search (boards HaloChooseSport,
 * HaloSwitchSport, HaloSearchSports), in the boards' order. They follow [Workouts.family], with two differences the
 * boards draw: HIIT (Intervals) sits at the end of Strength, and Other (free training) has a section of its own
 * instead of belonging to Indoor cardio.
 */
enum class SportGroup { Distance, IndoorCardio, StrengthIntervals, MindBody, Games, Other }

/** A section: its [group] and the sports in it, in the order they were given. */
data class SportSection(val group: SportGroup, val types: List<Int>)

object SportGroups {
    /** Free training, the sport that stands for anything not listed (the strap's Activity is the same). */
    private val OTHER = setOf(5, 16, Workouts.GENERIC_ACTIVITY)

    /** The section [type] is in. */
    fun of(type: Int): SportGroup = if (type in OTHER) SportGroup.Other else when (Workouts.family(type)) {
        Workouts.Family.Distance -> SportGroup.Distance
        Workouts.Family.IndoorCardio -> SportGroup.IndoorCardio
        Workouts.Family.Strength, Workouts.Family.Intervals -> SportGroup.StrengthIntervals
        Workouts.Family.MindBody -> SportGroup.MindBody
        Workouts.Family.Games -> SportGroup.Games
    }

    /**
     * [types] in sections, in the boards' order, a group with no sport left out. Within a section the sports keep
     * the order given, except that HIIT goes last in Strength and intervals.
     */
    fun sections(types: List<Int>): List<SportSection> = types.distinct().groupBy(::of).let { byGroup ->
        SportGroup.entries.mapNotNull { group ->
            val list = byGroup[group] ?: return@mapNotNull null
            val hiitLast = { type: Int -> Workouts.family(type) == Workouts.Family.Intervals }
            SportSection(group, if (group == SportGroup.StrengthIntervals) list.sortedBy(hiitLast) else list)
        }
    }

    /** Every sport a tile is offered for: the everyday ones first, then the rest A to Z. */
    val OFFERED: List<Int> get() = (Workouts.EVERYDAY + Workouts.MORE).distinct()

    /** Choose's and Change sport's sections: every offered sport. */
    fun chooseSections(): List<SportSection> = sections(OFFERED)
}
