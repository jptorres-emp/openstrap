package org.openstrap.app.data.settings

/** Your daily targets. The defaults are the app's old fixed goals. */
data class Goals(
    val steps: Int = DEFAULT_STEPS,
    val sleepMinutes: Int = DEFAULT_SLEEP_MINUTES,
    val activeHours: Int = DEFAULT_ACTIVE_HOURS,
) {
    val sleepSeconds: Long get() = sleepMinutes * 60L

    /** "8,000 steps · 8 h sleep · 10 active hours". */
    fun summary(): String {
        val h = sleepMinutes / 60
        val m = sleepMinutes % 60
        val sleep = if (m == 0) "$h h" else "$h h $m min"
        return "%,d steps · %s sleep · %d active hours".format(steps, sleep, activeHours)
    }

    companion object {
        const val DEFAULT_STEPS = 8000
        const val DEFAULT_SLEEP_MINUTES = 8 * 60
        const val DEFAULT_ACTIVE_HOURS = 10
        val STEPS = 1000..50000
        val SLEEP_MINUTES = 4 * 60..12 * 60
        val ACTIVE_HOURS = 1..18
    }
}
