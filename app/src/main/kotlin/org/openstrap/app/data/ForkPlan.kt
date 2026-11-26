package org.openstrap.app.data

import kotlin.math.roundToInt

/**
 * Today on the fork's Reports page (jptorres-emp/openstrap), as WHOOP's day and its sleep planner read it: the
 * morning's Recovery, the day's Load target and the Load so far, and tonight's bedtime ([plan]): the sleep you need
 * plus some for the last week's short nights ([extra]), before your usual waking, at your usual time asleep against
 * time in bed ([efficiency]). Pure, so it's unit tested.
 */
object ForkPlan {
    /** The nights your usual waking and your time asleep against time in bed are read from. */
    const val NIGHTS = 14
    /** The nights whose shortfall tonight makes up. */
    const val DEBT_NIGHTS = 7
    /** The most tonight adds for short nights, in minutes. */
    const val MAX_EXTRA = 60
    const val STEP = 5
    /** Time asleep against time in bed is kept between these, so an odd night doesn't move the bedtime far. */
    const val MIN_EFFICIENCY = 0.8f
    const val MAX_EFFICIENCY = 0.98f
    const val USUAL_EFFICIENCY = 0.9f
    private const val MIDDLE = 0.5f
    private const val DAY_MINUTES = 24 * 60

    /**
     * Today: the morning's [recovery] (the strap's when [fromStrap]), whether body signals capped it ([alert]), the Load
     * [target] and the [load] so far, and tonight's [bedtime] (minutes after midnight) for [asleep] minutes asleep, the
     * [extra] for short nights among them, before your usual [wake]. No bedtime before a night has its waking.
     */
    data class Plan(
        val recovery: Int?,
        val fromStrap: Boolean,
        val alert: Boolean,
        val target: ForkTarget.Target?,
        val load: Float,
        val bedtime: Int?,
        val asleep: Int,
        val wake: Int?,
        val extra: Int,
    )

    /** Today's plan from the [days] up to today (today last), the sleep you [need] in minutes, the [target] and [load]. */
    fun plan(days: List<ForkReports.Day>, need: Int, target: ForkTarget.Target?, load: Float): Plan {
        val today = days.lastOrNull()
        val recent = days.filter { it.wakeTime != null }.takeLast(NIGHTS)
        val wake = Recovery.percentile(recent.mapNotNull { it.wakeTime?.toFloat() }, MIDDLE)?.roundToInt()
        val extra = extra(days, need)
        val asleep = need + extra
        val bedtime = wake?.let { Math.floorMod(it - (asleep / efficiency(recent)).roundToInt(), DAY_MINUTES) }
        val fromStrap = today?.recovery == null && today?.strapRecovery != null
        return Plan(today?.recovery ?: today?.strapRecovery, fromStrap, today?.alert == true, target, load, bedtime, asleep, wake, extra)
    }

    /**
     * Minutes to add tonight for the last [DEBT_NIGHTS] nights' shortfall against [need]: a night's shortfall on
     * average, in [STEP]s, at most [MAX_EXTRA].
     */
    fun extra(days: List<ForkReports.Day>, need: Int): Int {
        val nights = days.mapNotNull { it.asleepMinutes }.takeLast(DEBT_NIGHTS)
        if (nights.isEmpty()) return 0
        val short = nights.sumOf { (need - it).coerceAtLeast(0) }.toFloat() / nights.size
        return ((short / STEP).roundToInt() * STEP).coerceIn(0, MAX_EXTRA)
    }

    /** Your usual time asleep against time in bed over [days], between [MIN_EFFICIENCY] and [MAX_EFFICIENCY]. */
    fun efficiency(days: List<ForkReports.Day>): Float {
        val shares = days.mapNotNull { d -> d.asleepMinutes?.let { a -> d.inBedMinutes?.takeIf { it > 0 }?.let { a.toFloat() / it } } }
        return (Recovery.percentile(shares, MIDDLE) ?: USUAL_EFFICIENCY).coerceIn(MIN_EFFICIENCY, MAX_EFFICIENCY)
    }
}
