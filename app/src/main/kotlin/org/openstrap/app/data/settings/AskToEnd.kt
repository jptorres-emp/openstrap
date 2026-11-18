package org.openstrap.app.data.settings

import org.openstrap.app.data.Workouts

/**
 * The groups of sports "Ask to end when I stop" has a time for (Workouts settings page, HaloWorkoutSettings board),
 * each with its time out of the box in minutes, null for Off.
 */
enum class AskToEndGroup(val defaultMinutes: Int?) {
    /** Runs, rides, walks, strength, HIIT and everything else not below. */
    Most(8),

    /** Padel, tennis and the other games with long breaks ([Workouts.Family.Games]). */
    Games(15),

    /** Yoga and Pilates ([Workouts.Family.MindBody]): long still moments are part of it. */
    MindBody(null),
    ;

    companion object {
        /** The group a sport's time comes from, by its family. */
        fun of(sportType: Int): AskToEndGroup = when (Workouts.family(sportType)) {
            Workouts.Family.MindBody -> MindBody
            Workouts.Family.Games -> Games
            else -> Most
        }
    }
}

/**
 * "Ask to end when I stop": whether the app asks at all ([on]), and after how many minutes without moving for each
 * [AskToEndGroup] (null for Off). A workout reads it live, so a change applies to the one under way. On out of the box.
 */
data class AskToEnd(
    val on: Boolean = true,
    val most: Int? = AskToEndGroup.Most.defaultMinutes,
    val games: Int? = AskToEndGroup.Games.defaultMinutes,
    val mindBody: Int? = AskToEndGroup.MindBody.defaultMinutes,
) {
    /** [group]'s own time in minutes, null for Off, whatever [on] says. */
    fun minutes(group: AskToEndGroup): Int? = when (group) {
        AskToEndGroup.Most -> most
        AskToEndGroup.Games -> games
        AskToEndGroup.MindBody -> mindBody
    }

    /** How long [sportType] waits still before the app asks, in seconds, or null when it doesn't ask. */
    fun stillSecondsFor(sportType: Int): Long? = if (!on) null else minutes(AskToEndGroup.of(sportType))?.let { it * SECONDS_A_MINUTE }

    /** This with [group]'s time set to [minutes] (null for Off). */
    fun with(group: AskToEndGroup, minutes: Int?): AskToEnd = when (group) {
        AskToEndGroup.Most -> copy(most = minutes)
        AskToEndGroup.Games -> copy(games = minutes)
        AskToEndGroup.MindBody -> copy(mindBody = minutes)
    }

    companion object {
        /** The minutes the picker offers, Off after them. */
        val CHOICES: List<Int> = listOf(5, 6, 8, 10, 15, 20, 30)

        /** How Off is stored: no group waits zero minutes. */
        const val OFF = 0

        private const val SECONDS_A_MINUTE = 60L

        /** A stored time read back: [OFF] is Off, nothing stored is [default]. */
        fun read(stored: Int?, default: Int?): Int? = when (stored) {
            null -> default
            OFF -> null
            else -> stored
        }
    }
}
