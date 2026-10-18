package org.openstrap.app.data

import org.openstrap.app.data.settings.UserProfile

/**
 * Daily energy expenditure, minute by minute, anchored on the user's own BMR (docs/algorithms/evidence-review.md,
 * "Energy expenditure"). Mifflin-St Jeor came within 10 % of measured resting metabolic rate in more people than other
 * equations (Frankenfield et al. 2005). Against doubly labelled water, HR-only estimates were less precise than
 * motion (Brage et al. 2015), and we have per-minute steps rather than acceleration, so the model is motion first
 * with a narrow, labelled heart-rate branch. Pure Kotlin, like [Recovery].
 *
 * Established for BMR; contested for everything above it. The 0.95, 1.15 and 1.3 multipliers are conventions, not 2024
 * Compendium values (sleeping and sitting quietly are both 1.0 MET there).
 */
object EnergyExpenditure {

    /** Mifflin-St Jeor sex terms, kcal/day: male +5, female −161, unset −78 (their midpoint). */
    const val BMR_MALE = 5f
    const val BMR_FEMALE = -161f
    const val BMR_UNSET = -78f

    /** Multipliers on the per-minute BMR (b) for minutes without a cadence MET. */
    const val SLEEP_FACTOR = 0.95f
    const val SEDENTARY_FACTOR = 1.15f
    const val LIGHT_FACTOR = 1.3f
    /** Minutes at or above this many steps use the cadence MET. */
    const val CADENCE_MET_STEPS = 60
    /** Running cadence: sustained (this and a neighbouring minute) at or above it uses the ACSM running equation. */
    const val RUNNING_CADENCE = 145

    /** HR branch ("HR-assisted"): awake, outside a workout, < 20 steps, ≥ 10 consecutive minutes at ≥ 40 % of reserve. */
    const val HR_BRANCH_MAX_STEPS = 20
    const val HR_BRANCH_HRR = 0.40f
    const val HR_BRANCH_RUN_MINUTES = 10
    const val HR_BRANCH_MAX_MET = 6f
    /** VO2max for the HR branch until there's an estimate of the user's own, ml/kg/min (UNVERIFIED default). */
    const val DEFAULT_VO2MAX = 35f

    /** A day below this much wear is "partial"; PAL outside [PAL_LOW]..[PAL_HIGH] is flagged as implausible. */
    const val FULL_DAY_MINUTES = 20 * 60
    const val PAL_LOW = 1.2f
    const val PAL_HIGH = 2.5f

    /** Mifflin-St Jeor BMR, kcal/day: 10·W + 6.25·H − 5·A + sex term. */
    fun bmr(weightKg: Float, heightCm: Int, age: Int, sex: UserProfile.Sex?): Float =
        10f * weightKg + 6.25f * heightCm - 5f * age + when (sex) {
            UserProfile.Sex.Male -> BMR_MALE
            UserProfile.Sex.Female -> BMR_FEMALE
            null -> BMR_UNSET
        }

    /** kcal/min above rest for an activity of [met]: (MET − 1)·3.5·W/200. Anchoring on BMR avoids counting rest twice. */
    fun net(met: Float, weightKg: Float): Float = (met - 1f).coerceAtLeast(0f) * 3.5f * weightKg / 200f

    /**
     * METs for a walking cadence (CADENCE-Adults, Tudor-Locke et al. 2019): up to 60 years, 60 → 2.0, 100 → 3, 130 → 6,
     * linear between, held at 6 above 130. From 61: 100 → 3, 110 → 4, 120 → 5, then on to 6 at 130 (the last segment
     * is our extrapolation). A fixed-stride ACSM walking equation gives about 3.7 at 130 spm, far below the measured 6.
     */
    fun cadenceMet(cadence: Int, age: Int?): Float {
        val c = cadence.toFloat()
        val older = age != null && age >= 61
        val points = if (older) listOf(60f to 2f, 100f to 3f, 110f to 4f, 120f to 5f, 130f to 6f) else listOf(60f to 2f, 100f to 3f, 130f to 6f)
        if (c <= points.first().first) return points.first().second
        if (c >= points.last().first) return points.last().second
        val (a, b) = points.zipWithNext().first { (_, hi) -> c <= hi.first }
        return a.second + (b.second - a.second) * (c - a.first) / (b.first - a.first)
    }

    /** ACSM running on the level: VO2 = 3.5 + 0.2·v (v in m/min), as METs. */
    fun runningMet(cadence: Int, heightCm: Int): Float = (3.5f + 0.2f * cadence * Activity.stepLength(cadence, heightCm)) / 3.5f

    data class Minute(
        val t: Long,
        /** Null when there was no step record that minute (but the minute still had data, e.g. heart rate or sleep). */
        val steps: Int?,
        val hrr: Float?,
        val asleep: Boolean,
        /** The device's kcal for this minute when it falls inside a recorded workout (pro-rated). */
        val workoutKcal: Float? = null,
    )

    data class Day(
        val totalKcal: Float,
        /** BMR for the whole day, for reference. */
        val bmrKcal: Float,
        val minutesWithData: Int,
        /** Under [FULL_DAY_MINUTES] of data: the total covers only part of the day. */
        val partial: Boolean,
        /** TEE / BMR, on a full day. */
        val pal: Float?,
        /** PAL outside 1.2-2.5: likely a wear or profile problem. */
        val palImplausible: Boolean,
        val hrAssistedMinutes: Int,
    )

    /**
     * Energy for one day of [minutes] (ascending; only minutes with data — no row is missing, never zero). Null without
     * weight, height and age. No thermic effect of food is added.
     */
    fun day(minutes: List<Minute>, profile: UserProfile, age: Int?, vo2max: Float? = null): Day? {
        val w = profile.weightKg ?: return null
        val h = profile.heightCm ?: return null
        val a = age ?: return null
        val bmrDay = bmr(w, h, a, profile.sex)
        val b = bmrDay / 1440f
        val vo2Mets = (vo2max ?: DEFAULT_VO2MAX) / 3.5f
        val n = minutes.size
        fun neighbourStepped(i: Int): Boolean = listOf(i - 1, i + 1).any { j ->
            val m = minutes.getOrNull(j) ?: return@any false
            kotlin.math.abs(m.t - minutes[i].t) <= 60 && (m.steps ?: 0) > 0
        }
        fun running(i: Int): Boolean = (minutes[i].steps ?: 0) >= RUNNING_CADENCE && neighbourAtLeast(minutes, i, RUNNING_CADENCE)

        // The HR branch's qualifying runs.
        val hrRun = BooleanArray(n)
        var i = 0
        while (i < n) {
            val ok = { k: Int ->
                val m = minutes[k]
                !m.asleep && m.workoutKcal == null && (m.steps ?: 0) < HR_BRANCH_MAX_STEPS && (m.hrr ?: 0f) >= HR_BRANCH_HRR
            }
            if (!ok(i)) { i++; continue }
            var j = i
            while (j + 1 < n && ok(j + 1) && minutes[j + 1].t - minutes[j].t == 60L) j++
            if (j - i + 1 >= HR_BRANCH_RUN_MINUTES) for (k in i..j) hrRun[k] = true
            i = j + 1
        }

        var total = 0.0
        var hrAssisted = 0
        for ((k, m) in minutes.withIndex()) {
            val steps = m.steps ?: 0
            var ee = when {
                m.workoutKcal != null -> maxOf(m.workoutKcal, b)
                m.asleep -> SLEEP_FACTOR * b
                steps >= CADENCE_MET_STEPS -> b + net(if (running(k)) runningMet(steps, h) else cadenceMet(steps, a), w)
                steps > 0 || neighbourStepped(k) -> LIGHT_FACTOR * b
                else -> SEDENTARY_FACTOR * b
            }
            if (hrRun[k]) {
                val met = minOf(HR_BRANCH_MAX_MET, 1f + m.hrr!! * (vo2Mets - 1f))
                val hrEe = b + net(met, w)
                if (hrEe > ee) { ee = hrEe; hrAssisted++ }
            }
            total += ee
        }
        val partial = n < FULL_DAY_MINUTES
        val pal = if (partial) null else (total / bmrDay).toFloat()
        return Day(total.toFloat(), bmrDay, n, partial, pal, pal != null && (pal < PAL_LOW || pal > PAL_HIGH), hrAssisted)
    }

    private fun neighbourAtLeast(minutes: List<Minute>, i: Int, cadence: Int) = listOf(i - 1, i + 1).any { j ->
        val m = minutes.getOrNull(j) ?: return@any false
        kotlin.math.abs(m.t - minutes[i].t) <= 60 && (m.steps ?: 0) >= cadence
    }
}
