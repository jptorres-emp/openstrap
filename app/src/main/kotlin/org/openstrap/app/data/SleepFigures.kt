package org.openstrap.app.data

import kotlin.math.roundToInt

/**
 * The fork's two sleep figures, as WHOOP has them: performance, the time asleep against the sleep you need, and
 * efficiency, the time asleep against the time in bed. Pure, for the Sleep tab's hero and the sleep detail.
 */
object SleepFigures {
    /** The time asleep against [needMinutes], in percent, at most 100; null without a need. */
    fun performance(asleepSeconds: Long, needMinutes: Int): Int? =
        if (needMinutes <= 0) null else minOf(PERCENT, (asleepSeconds / 60.0 / needMinutes * PERCENT).roundToInt())

    /** The time asleep against the time in bed from [start] to [end], in percent; null for an empty night. */
    fun efficiency(asleepSeconds: Long, start: Long, end: Long): Int? =
        (end - start).takeIf { it > 0 }?.let { bed -> minOf(PERCENT, (asleepSeconds.toDouble() / bed * PERCENT).roundToInt()) }

    private const val PERCENT = 100
}
