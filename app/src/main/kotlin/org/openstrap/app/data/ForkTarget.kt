package org.openstrap.app.data

import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The fork's ideal effort for a day (jptorres-emp/openstrap), as WHOOP's Strain target reads it: what the morning's
 * Recovery says the day is for ([DayKind]), and a range of your own Load for it ([target]). Pure, so it's unit tested.
 */
object ForkTarget {
    /** What the morning says the day is for, easiest first. */
    enum class DayKind { Recover, Moderate, Usual, Push }

    /**
     * The day's Load target: a [range] for a [kind] of day, in steps of [STEP]. [personal] when the range comes from your
     * own training days, else from your usual daily Load. [fromStrap] when the strap's score stood in for a Recovery
     * still learning.
     */
    data class Target(val kind: DayKind, val range: IntRange, val personal: Boolean, val fromStrap: Boolean)

    /** Where a day's Load sits against its target, as the Load reads (rounded). */
    enum class Versus { Below, Within, Above }

    /** Training days in the [LOOK_BACK_DAYS] before a morning needed for a target of your own. */
    const val MIN_TRAINING_DAYS = 4
    /** How far back the target looks: fitness's six weeks. */
    const val LOOK_BACK_DAYS = Recovery.CTL_DAYS
    const val STEP = 5
    const val MIN_WIDTH = 20
    /** The share around your usual Load that's still usual, as Today's Load card has it. */
    const val USUAL_BAND = 0.25f
    private const val PUSH_BANDS = 3f
    private const val EASY = 0.25f
    private const val MIDDLE = 0.5f
    private const val HARD = 0.75f
    private const val HARDEST = 0.9f
    private const val HALF = 0.5f

    /** An alert or a low Recovery is for recovering, a fair one for a moderate day, a high one for pushing. */
    fun kindOf(recovery: Int?, alert: Boolean): DayKind = when {
        alert || (recovery != null && recovery < Recovery.FAIR_FROM) -> DayKind.Recover
        recovery != null && recovery < ForkReports.READY_FROM -> DayKind.Moderate
        recovery != null && recovery >= ForkReports.PUSH_FROM -> DayKind.Push
        else -> DayKind.Usual
    }

    /**
     * The target for the morning [day]: its [DayKind] and a range of Load. With at least [MIN_TRAINING_DAYS] [training]
     * days among the worn days ([loads]) of the [LOOK_BACK_DAYS] before, the range comes from their Loads: Recover up
     * to half an easy session (or a usual rest day's Load, if more), Moderate from half an easy session to a middling
     * one, Usual from an easy one to a hard one, Push from a middling one to your hardest tenth. Without them it's built
     * round your [usualLoad], the ±[USUAL_BAND] band Today calls usual. Null before there's either.
     */
    fun target(day: ForkReports.Day, loads: Map<LocalDate, Float>, training: Set<LocalDate>, usualLoad: Float?): Target? {
        val kind = kindOf(day.recovery ?: day.strapRecovery, day.alert)
        val before = (1..LOOK_BACK_DAYS).mapNotNull { back -> day.date.minusDays(back.toLong()).let { d -> loads[d]?.let { d to it } } }
        val sessions = before.filter { it.first in training }.map { it.second }
        val personal = sessions.size >= MIN_TRAINING_DAYS
        val bounds = if (personal) {
            fromSessions(kind, sessions, before.filter { it.first !in training }.map { it.second })
        } else {
            usualLoad?.takeIf { it > 0f }?.let { fromUsual(kind, it) }
        }
        val fromStrap = day.recovery == null && day.strapRecovery != null
        return bounds?.let { (lo, hi) -> Target(kind, rounded(lo, hi), personal, fromStrap) }
    }

    private fun fromSessions(kind: DayKind, sessions: List<Float>, rest: List<Float>): Pair<Float, Float> {
        fun q(p: Float) = Recovery.percentile(sessions, p) ?: 0f
        return when (kind) {
            DayKind.Recover -> 0f to max(Recovery.percentile(rest, MIDDLE) ?: 0f, HALF * q(EASY))
            DayKind.Moderate -> HALF * q(EASY) to q(MIDDLE)
            DayKind.Usual -> q(EASY) to q(HARD)
            DayKind.Push -> q(MIDDLE) to q(HARDEST)
        }
    }

    private fun fromUsual(kind: DayKind, usual: Float): Pair<Float, Float> = when (kind) {
        DayKind.Recover -> 0f to (1 - 2 * USUAL_BAND) * usual
        DayKind.Moderate -> (1 - 2 * USUAL_BAND) * usual to usual
        DayKind.Usual -> (1 - USUAL_BAND) * usual to (1 + USUAL_BAND) * usual
        DayKind.Push -> usual to (1 + PUSH_BANDS * USUAL_BAND) * usual
    }

    /** [lo]..[hi] out to whole [STEP]s, at least [MIN_WIDTH] wide (round its middle when the sessions are all alike). */
    internal fun rounded(lo: Float, hi: Float): IntRange {
        val a = (floor(lo / STEP) * STEP).toInt().coerceAtLeast(0)
        val b = (ceil(hi / STEP) * STEP).toInt()
        if (b - a >= MIN_WIDTH) return a..b
        val start = ((((lo + hi) / 2 - MIN_WIDTH * HALF) / STEP).roundToInt() * STEP).coerceAtLeast(0)
        return start..start + MIN_WIDTH
    }

    /**
     * [days] with each finished day's Load read against that day's own target ([ForkTarget.Versus]), as the morning set
     * it: the loads and the [training] days before it, and your [usual] Load up to the day before ([usualBefore]).
     */
    fun mark(
        days: List<ForkReports.Day>,
        loads: Map<LocalDate, Float>,
        training: Set<LocalDate>,
        usual: Map<LocalDate, Float>,
    ): List<ForkReports.Day> =
        days.map { day ->
            val load = day.load?.takeIf { !day.partial }
            val target = load?.let { target(day, loads, training, usualBefore(usual, day.date)) }
            if (load == null || target == null) day else day.copy(versus = versus(load, target.range))
        }

    /** Your usual Load through the last worn day before [date], from [usual] ([ForkReports.Mornings.usual]). */
    fun usualBefore(usual: Map<LocalDate, Float>, date: LocalDate): Float? =
        usual.entries.filter { it.key < date }.maxByOrNull { it.key }?.value

    /** Where [load] sits against [target]. */
    fun versus(load: Float, target: IntRange): Versus = load.roundToInt().let {
        when {
            it < target.first -> Versus.Below
            it > target.last -> Versus.Above
            else -> Versus.Within
        }
    }
}
