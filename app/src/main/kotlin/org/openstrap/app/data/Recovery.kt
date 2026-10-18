package org.openstrap.app.data

import org.openstrap.app.data.db.SleepStage
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * OpenStrap's own recovery model: a morning **Readiness** score and a minute-by-minute **Charge** (body
 * battery). Pure Kotlin, no Android types, so every rule is unit-tested. docs/algorithms/recovery.md
 * explains each rule and the research behind it; the constants below are referenced there by name.
 *
 * Zepp's BioCharge runs in a closed native library and on the strap, so nothing here is derived from it.
 */
object Recovery {

    // ------------------------------------------------------------------------------------------------
    // Inputs
    // ------------------------------------------------------------------------------------------------

    /** One main sleep, reduced to the nightly values readiness needs. Null where the strap had no data. */
    data class Night(
        /** Local midnight of the day the sleep belongs to (the morning it ended). */
        val day: Long,
        val start: Long,
        val end: Long,
        val asleepMinutes: Int,
        val awakeMinutes: Int? = null,
        val deepMinutes: Int? = null,
        val remMinutes: Int? = null,
        /** The strap's own sleep score, when it computed one. */
        val strapScore: Int? = null,
        /** Mean RMSSD during the sleep, ms. */
        val hrv: Float? = null,
        /** Lowest 30-minute average heart rate during the sleep, bpm. */
        val restingHr: Float? = null,
        /** When the lowest 10-minute heart rate happened (epoch seconds). */
        val nadirTime: Long? = null,
        val skinTemperature: Float? = null,
        val breathRate: Float? = null,
        /** Mean blood oxygen during the sleep, %. */
        val spo2: Float? = null,
        /** Oxygen-desaturation events per hour asleep, from the strap's nightly sleep-apnea summary. */
        val odi: Float? = null,
    )

    /** One minute of the day as the charge model sees it. */
    data class Minute(
        val t: Long,
        val hr: Int?,
        val steps: Int,
        /** The strap's stress value (0-100) if one was measured within the last few minutes. */
        val stress: Int?,
        /** A [SleepStage] while asleep (including AWAKE inside a sleep), null outside sleep. */
        val sleepStage: Int?,
    )

    // ------------------------------------------------------------------------------------------------
    // Baselines
    // ------------------------------------------------------------------------------------------------

    /** Mean and standard deviation of a personal baseline, with the number of nights behind it. */
    data class Baseline(val mean: Float, val sd: Float, val n: Int) {
        fun z(x: Float, sdFloor: Float) = (x - mean) / max(sd, sdFloor)
    }

    /** Baseline from [values]; null with fewer than [minN] values. Uses the sample standard deviation. */
    fun baseline(values: List<Float>, minN: Int = MIN_BASELINE_NIGHTS): Baseline? {
        if (values.size < minN) return null
        val mean = values.average()
        val sd = if (values.size < 2) 0.0 else sqrt(values.sumOf { (it - mean).pow(2) } / (values.size - 1))
        return Baseline(mean.toFloat(), sd.toFloat(), values.size)
    }

    /**
     * Where Charge starts counting the strap's stress score as stress. [calmLevel] is null (and [value] is
     * [STRESS_FROM]) until [days] reaches [MIN_STRESS_DAYS].
     */
    data class StressThreshold(val value: Int, val calmLevel: Int?, val days: Int)

    /**
     * A personal stress threshold. [calm] are the strap's stress readings taken awake, still and at resting heart
     * rate over the last [STRESS_BASELINE_DAYS] days, as (epoch seconds, value); [dayOf] maps a time to its day.
     * The calm level is their median, and stress counts from [STRESS_MARGIN] above it, kept within
     * [STRESS_FROM_MIN]..[STRESS_FROM_MAX]. A calm level of 50 gives 60, where Zepp's "medium" band starts.
     * Zepp computes its own threshold on the phone and stores it on the strap; this is our equivalent.
     */
    fun stressThreshold(calm: List<Pair<Long, Int>>, dayOf: (Long) -> Long): StressThreshold {
        val days = calm.map { dayOf(it.first) }.distinct().size
        if (days < MIN_STRESS_DAYS || calm.size < MIN_STRESS_READINGS) return StressThreshold(STRESS_FROM, null, days)
        val sorted = calm.map { it.second }.sorted()
        val median = (sorted[(sorted.size - 1) / 2] + sorted[sorted.size / 2]) / 2f
        val value = Math.round(median + STRESS_MARGIN).coerceIn(STRESS_FROM_MIN, STRESS_FROM_MAX)
        return StressThreshold(value, Math.round(median), days)
    }

    const val STRESS_BASELINE_DAYS = 14
    const val MIN_STRESS_DAYS = 3
    const val MIN_STRESS_READINGS = 60
    const val STRESS_MARGIN = 10
    const val STRESS_FROM_MIN = 50
    const val STRESS_FROM_MAX = 70

    /** Nights needed before a baseline is used at all, and before it's considered settled. */
    const val MIN_BASELINE_NIGHTS = 4
    const val CALIBRATED_NIGHTS = 14
    /** Nights of history a baseline looks back over (Oura uses about two months for its long average). */
    const val BASELINE_NIGHTS = 60

    // ------------------------------------------------------------------------------------------------
    // Readiness
    // ------------------------------------------------------------------------------------------------

    enum class Factor(val label: String, val weight: Float, val physiological: Boolean = false) {
        HrvBalance("HRV balance", 0.25f, physiological = true),
        RestingHr("Resting heart rate", 0.15f, physiological = true),
        Sleep("Last night's sleep", 0.18f),
        SleepBalance("Sleep balance", 0.10f),
        RecoveryIndex("Recovery index", 0.07f),
        Temperature("Body temperature", 0.10f, physiological = true),
        Breathing("Breathing rate", 0.03f, physiological = true),
        BloodOxygen("Blood oxygen", 0.05f, physiological = true),
        BreathingDisturbances("Breathing disturbances", 0.04f),
        PreviousDayLoad("Previous day's load", 0.07f),
        LoadBalance("Training load balance", 0.05f),
    }

    data class Contributor(val factor: Factor, val score: Int, val detail: String)

    data class Readiness(
        /** 0-100, or null when there's no sleep to score. */
        val score: Int?,
        val contributors: List<Contributor>,
        /** Nights in the HRV baseline; readiness is provisional below [CALIBRATED_NIGHTS]. */
        val baselineNights: Int,
        /** Several body signals moved away from normal together; worth taking it easy. */
        val strainAlert: Boolean,
        /** The signals that moved, in plain words, for the alert. */
        val alertSignals: List<String>,
        /** True when the weighted score was capped by one very low physiological contributor. */
        val capped: Boolean,
    ) {
        val calibrating get() = baselineNights < CALIBRATED_NIGHTS
    }

    /**
     * Maps a z-score (deviation from baseline in standard deviations, positive = better) to 0-100.
     * A normal night (z = 0) scores 80, one SD better ~93, one SD worse ~55, two SD worse ~27.
     */
    fun scoreFromZ(z: Float): Int = Math.round(100.0 / (1.0 + exp(-(Z_SLOPE * z + Z_OFFSET)))).toInt().coerceIn(0, 100)
    private const val Z_SLOPE = 1.19
    private val Z_OFFSET = ln(4.0)

    /** Smallest worthwhile change, as a fraction of the baseline SD (Hopkins; Plews uses 0.5 for HRV). */
    const val SWC_SD = 0.5f
    /** Floors so a very steady baseline doesn't turn tiny changes into huge z-scores. */
    const val HRV_LN_SD_FLOOR = 0.05f
    const val RHR_SD_FLOOR = 2f
    const val SLEEP_NEED_MINUTES = 8 * 60

    /**
     * Readiness for [tonight] against [history] (earlier nights, any order, tonight excluded) and
     * [dailyLoad] (TRIMP per day, oldest first, ending with yesterday).
     */
    fun readiness(tonight: Night?, history: List<Night>, dailyLoad: List<Float> = emptyList()): Readiness {
        if (tonight == null) return Readiness(null, emptyList(), 0, false, emptyList(), false)
        val past = history.filter { it.day < tonight.day }.sortedBy { it.day }.takeLast(BASELINE_NIGHTS)
        val out = mutableListOf<Contributor>()
        val alerts = mutableListOf<String>()

        // HRV balance: tonight and the 7-night rolling mean of ln(RMSSD) against the long baseline (Plews).
        val lnHistory = past.mapNotNull { it.hrv?.takeIf { v -> v > 0 }?.let { v -> ln(v) } }
        val hrvBase = baseline(lnHistory)
        tonight.hrv?.takeIf { it > 0 }?.let { hrv ->
            val ln = ln(hrv)
            if (hrvBase != null) {
                val recent = (lnHistory.takeLast(6) + ln)
                val zNight = hrvBase.z(ln, HRV_LN_SD_FLOOR)
                val zWeek = hrvBase.z(recent.average().toFloat(), HRV_LN_SD_FLOOR)
                val score = scoreFromZ(0.6f * zNight + 0.4f * zWeek)
                val pct = ((exp(ln - hrvBase.mean.toDouble()) - 1) * 100).toInt()
                out += Contributor(Factor.HrvBalance, score, "${hrv.toInt()} ms · ${signed(pct)}% vs usual")
                if (zNight <= -1.5f) alerts += "HRV well below your normal"
            }
        }

        // Resting heart rate: the night's lowest 30-minute average against baseline; lower is better.
        val rhrBase = baseline(past.mapNotNull { it.restingHr })
        tonight.restingHr?.let { rhr ->
            if (rhrBase != null) {
                val z = -rhrBase.z(rhr, RHR_SD_FLOOR)
                val delta = rhr - rhrBase.mean
                out += Contributor(Factor.RestingHr, scoreFromZ(z), "${rhr.toInt()} bpm · ${signed(delta, 0)} vs usual")
                if (delta >= max(5f, 2 * max(rhrBase.sd, RHR_SD_FLOOR))) alerts += "resting heart rate up ${delta.toInt()} bpm"
            }
        }

        // Last night's sleep: the strap's own score when there is one, else ours.
        out += Contributor(Factor.Sleep, tonight.strapScore ?: sleepScore(tonight), formatMinutes(tonight.asleepMinutes) + " asleep")

        // Sleep balance: recent nights' shortfall against need, recent nights weighted more.
        val nights = (past.takeLast(13) + tonight)
        if (nights.size >= 3) {
            var wSum = 0.0
            var shortSum = 0.0
            nights.reversed().forEachIndexed { i, n ->
                val w = 0.5.pow(i / SLEEP_DEBT_HALF_LIFE_NIGHTS)
                // A long night repays at most an hour of earlier debt.
                shortSum += w * (SLEEP_NEED_MINUTES - n.asleepMinutes).coerceAtLeast(-60)
                wSum += w
            }
            val avgShortHours = (shortSum / wSum / 60).toFloat()
            val score = (100 - SLEEP_DEBT_POINTS_PER_HOUR * avgShortHours.coerceAtLeast(0f)).toInt().coerceIn(0, 100)
            val detail = if (avgShortHours <= 0.05f) "No sleep debt" else "${formatMinutes((avgShortHours * 60).toInt())} short a night"
            out += Contributor(Factor.SleepBalance, score, detail)
        }

        // Recovery index: the night's lowest heart rate should come in its first half (Oura).
        if (tonight.nadirTime != null && tonight.end > tonight.start) {
            val p = (tonight.nadirTime - tonight.start).toFloat() / (tonight.end - tonight.start)
            val score = if (p <= 0.5f) 100 else (100 - 120 * (p - 0.5f)).toInt().coerceIn(40, 100)
            val after = ((tonight.end - tonight.nadirTime) / 60).toInt()
            out += Contributor(Factor.RecoveryIndex, score, "Lowest HR ${formatMinutes(after)} before waking")
        }

        // Body temperature: distance from the usual nightly skin temperature, either direction.
        val tempBase = baseline(past.mapNotNull { it.skinTemperature })
        tonight.skinTemperature?.let { t ->
            if (tempBase != null) {
                val dev = t - tempBase.mean
                val score = (100 - 70 * (abs(dev) - 0.2f).coerceAtLeast(0f)).toInt().coerceIn(0, 100)
                out += Contributor(Factor.Temperature, score, "${signed(dev, 1)} °C vs usual")
                if (dev >= 0.5f) alerts += "skin temperature up ${"%.1f".format(dev)} °C"
            }
        }

        // Breathing rate: very stable night to night, so a change of 1-2 breaths/min is notable.
        val breathBase = baseline(past.mapNotNull { it.breathRate })
        tonight.breathRate?.let { b ->
            if (breathBase != null) {
                val dev = b - breathBase.mean
                val score = (100 - 30 * (abs(dev) - 1f).coerceAtLeast(0f)).toInt().coerceIn(0, 100)
                out += Contributor(Factor.Breathing, score, "${signed(dev, 1)} /min vs usual")
                if (dev >= 2f) alerts += "breathing rate up ${"%.1f".format(dev)}/min"
            }
        }

        // Blood oxygen: only a fall below the usual nightly level counts; a higher reading is no better.
        val spo2Base = baseline(past.mapNotNull { it.spo2 })
        tonight.spo2?.let { o ->
            if (spo2Base != null) {
                val dev = o - spo2Base.mean
                val score = (100 - SPO2_POINTS_PER_PERCENT * (-dev - SPO2_DEAD_BAND).coerceAtLeast(0f)).toInt().coerceIn(0, 100)
                out += Contributor(Factor.BloodOxygen, score, "${"%.1f".format(o)} % · ${signed(dev, 1)} vs usual")
                if (dev <= -SPO2_ALERT_DROP) alerts += "overnight blood oxygen down ${"%.1f".format(-dev)} %"
            }
        }

        // Breathing disturbances: oxygen-desaturation events per hour, scored on the 5/15/30 bands Zepp uses.
        tonight.odi?.let { odi ->
            out += Contributor(Factor.BreathingDisturbances, odiScore(odi), "${"%.1f".format(odi)} events/h · ${odiBand(odi)}")
        }

        // Training load: yesterday against the chronic load, and the acute:chronic balance (EWMA).
        if (dailyLoad.size >= 7) {
            val (acute, chronic) = ewmaLoads(dailyLoad)
            val yesterday = dailyLoad.last()
            val ratio = yesterday / max(chronic, MIN_CHRONIC_LOAD)
            val score = if (ratio <= 1.5f) 100 else (100 - 25 * (ratio - 1.5f)).toInt().coerceIn(30, 100)
            out += Contributor(Factor.PreviousDayLoad, score, "Load ${yesterday.toInt()} · usual ${chronic.toInt()}")
            if (dailyLoad.size >= 14) {
                val acwr = acute / max(chronic, MIN_CHRONIC_LOAD)
                val s = when {
                    acwr > 1.3f -> (100 - 80 * (acwr - 1.3f)).toInt()
                    acwr < 0.8f -> (100 - 40 * (0.8f - acwr)).toInt()
                    else -> 100
                }.coerceIn(20, 100)
                out += Contributor(Factor.LoadBalance, s, "Acute:chronic ${"%.2f".format(acwr)}")
            }
        }

        val totalWeight = out.sumOf { it.factor.weight.toDouble() }
        var score = (out.sumOf { it.factor.weight.toDouble() * it.score } / totalWeight).toInt()
        // One very poor body signal shouldn't be averaged away by good sleep and a quiet day.
        val worst = out.filter { it.factor.physiological }.minOfOrNull { it.score }
        var capped = false
        if (worst != null && worst < RED_FLAG && score > worst + RED_FLAG_HEADROOM) {
            score = worst + RED_FLAG_HEADROOM
            capped = true
        }
        val alert = alerts.size >= 2
        if (alert && score > ALERT_CAP) { score = ALERT_CAP; capped = true }
        return Readiness(score.coerceIn(0, 100), out, hrvBase?.n ?: lnHistory.size, alert, alerts, capped)
    }

    private const val SLEEP_DEBT_HALF_LIFE_NIGHTS = 4.0
    /** Readiness points lost per hour of average nightly shortfall (1 h short ≈ 75, 2 h ≈ 50). */
    const val SLEEP_DEBT_POINTS_PER_HOUR = 25
    const val RED_FLAG = 40
    const val RED_FLAG_HEADROOM = 30
    const val ALERT_CAP = 55
    private const val MIN_CHRONIC_LOAD = 20f
    /** Readiness points lost per % of nightly SpO2 below baseline, beyond a 1 % dead band (−2 % ≈ 75, −3 % ≈ 50). */
    const val SPO2_POINTS_PER_PERCENT = 25
    const val SPO2_DEAD_BAND = 1f
    const val SPO2_ALERT_DROP = 2f

    /**
     * Score for an oxygen-desaturation index (events per hour), on the bands Zepp uses (5, 15, 30 per hour).
     * Within each band the score falls linearly.
     */
    fun odiScore(odi: Float): Int = when {
        odi < 5f -> 100
        odi < 15f -> 100 - (odi - 5f) * 2.5f
        odi < 30f -> 75 - (odi - 15f) * 2f
        else -> 45 - (odi - 30f)
    }.toInt().coerceIn(20, 100)

    /** Plain count wording, not clinical labels: this is a wellness estimate, not a sleep-apnea screen. */
    fun odiBand(odi: Float) = when {
        odi < 5f -> "few"
        odi < 15f -> "some"
        odi < 30f -> "frequent"
        else -> "very frequent"
    }

    /**
     * Our sleep score when the strap didn't give one: duration against need (50%), efficiency (25%)
     * and the share of deep + REM (25%).
     */
    fun sleepScore(n: Night): Int {
        val duration = (n.asleepMinutes.toFloat() / SLEEP_NEED_MINUTES).coerceAtMost(1f)
        val efficiency = n.awakeMinutes?.let { n.asleepMinutes.toFloat() / (n.asleepMinutes + it).coerceAtLeast(1) }
        val restorative = if (n.deepMinutes != null && n.remMinutes != null && n.asleepMinutes > 0)
            ((n.deepMinutes + n.remMinutes).toFloat() / n.asleepMinutes / 0.45f).coerceAtMost(1f) else null
        val parts = listOfNotNull(0.5f to duration, efficiency?.let { 0.25f to ((it - 0.6f) / 0.35f).coerceIn(0f, 1f) }, restorative?.let { 0.25f to it })
        return (parts.sumOf { (w, v) -> (w * v).toDouble() } / parts.sumOf { it.first.toDouble() } * 100).toInt().coerceIn(0, 100)
    }

    // ------------------------------------------------------------------------------------------------
    // Training load
    // ------------------------------------------------------------------------------------------------

    /** Heart-rate reserve fraction: 0 at resting, 1 at maximum. */
    fun hrr(hr: Int, rest: Float, maxHr: Float): Float = ((hr - rest) / (maxHr - rest)).coerceIn(0f, 1.2f)

    /**
     * Banister TRIMP for one minute at [hrr]: 0.64·e^(1.92·x) for men, 0.86·e^(1.67·x) for women (the
     * lactate-curve weightings). Unknown sex uses the men's curve, the common default.
     */
    fun trimpMinute(hrr: Float, female: Boolean = false): Float = when {
        hrr <= 0f -> 0f
        female -> hrr * 0.86f * exp(1.67f * hrr)
        else -> hrr * 0.64f * exp(1.92f * hrr)
    }

    /** Daily load: TRIMP over awake minutes with HR at or above [LOAD_MIN_HRR] of reserve. */
    fun dailyLoad(hr: List<Pair<Long, Int>>, rest: Float, maxHr: Float, female: Boolean = false, asleep: (Long) -> Boolean = { false }): Float =
        hr.filter { !asleep(it.first) }.sumOf { (_, bpm) ->
            val x = hrr(bpm, rest, maxHr)
            if (x >= LOAD_MIN_HRR) trimpMinute(x, female).toDouble() else 0.0
        }.toFloat()

    /**
     * Maximum heart rate: Tanaka's 208 − 0.7·age when the age is known, else the strap's own value (from
     * the Zepp profile), else 190. Raised to [observedPeak] when the user has actually gone higher, as
     * Firstbeat does, but by at most 15 bpm so one artefact can't move it far.
     */
    fun maxHeartRate(age: Int?, strapMax: Int? = null, observedPeak: Int? = null): Float {
        val estimate = when {
            age != null && age in 10..100 -> 208f - 0.7f * age
            strapMax != null && strapMax in 140..225 -> strapMax.toFloat()
            else -> 190f
        }
        return if (observedPeak != null && observedPeak > estimate) min(observedPeak.toFloat(), estimate + 15) else estimate
    }

    /** Below this reserve fraction, heart rate is everyday living rather than training. */
    const val LOAD_MIN_HRR = 0.2f

    /** Acute (7-day) and chronic (28-day) exponentially weighted load, λ = 2/(N+1) (Williams et al. 2017). */
    fun ewmaLoads(daily: List<Float>): Pair<Float, Float> {
        val la = 2f / (7 + 1)
        val lc = 2f / (28 + 1)
        var a = daily.first()
        var c = daily.first()
        for (x in daily.drop(1)) {
            a = la * x + (1 - la) * a
            c = lc * x + (1 - lc) * c
        }
        return a to c
    }

    // ------------------------------------------------------------------------------------------------
    // Charge
    // ------------------------------------------------------------------------------------------------

    /** What the body was doing in a minute, Firstbeat-style. */
    enum class State { Sleep, Relax, Rest, Stress, Activity, Exercise, ExerciseRecovery, NoData }

    data class ChargeParams(
        /** Daytime resting heart rate (the baseline), bpm. */
        val restingHr: Float,
        val maxHr: Float,
        /** Lowest 30-minute average of the night being simulated, for the sleep relaxation term. */
        val nightRestingHr: Float? = null,
        /** Tonight's HRV contributor (0-100); scales how well sleep recharges. */
        val hrvScore: Int? = null,
        val female: Boolean = false,
        /** Stress score from which a still minute counts as stress (see [stressThreshold]). */
        val stressFrom: Int = STRESS_FROM,
    )

    data class ChargePoint(val t: Long, val value: Float, val state: State)

    data class ChargeDay(
        /** Every [sampleSeconds] inside the requested window. */
        val points: List<ChargePoint>,
        val latest: Float?,
        /** Charge when the main sleep ended. */
        val atWake: Float?,
        val recharged: Float,
        /** Drained by movement: exercise and everyday activity above resting. */
        val drainedExercise: Float,
        val drainedStress: Float,
        val drainedLiving: Float,
        val highest: Float?,
        val lowest: Float?,
        /**
         * True once the simulation has seen a sleep: charge is relative to how full a night left you, so
         * without one the number would only reflect the arbitrary starting guess.
         */
        val anchored: Boolean,
    )

    /** Sleep recharge rate per minute, as a fraction of the remaining headroom (≈30 → 92 over 7.5 h). */
    const val SLEEP_K = 0.0060f
    /** Resting drain while awake, per minute (≈2.4 per hour). */
    const val BASE_DRAIN = 0.04f
    /** Charge drained per TRIMP unit of exercise (≈25 for an hour at 60% of reserve). */
    const val EXERCISE_DRAIN_PER_TRIMP = 0.32f
    /** Extra drain per stress point above the threshold, per minute (80 over 60 drains 0.14/min). */
    const val STRESS_DRAIN = 0.007f
    /**
     * Zepp's bands for the strap's score: relaxed 1–39, normal 40–59, medium 60–79, high 80–100. Only medium
     * and up count as stress; from 40, most of a seated day (Zepp's "normal") drained as stress. The default
     * until [stressThreshold] has enough of the user's own readings.
     */
    const val STRESS_FROM = 60
    const val RELAX_BELOW = 25
    /** Firstbeat's intensity bands, as heart-rate reserve (≈ %VO2 reserve, Swain & Leutholtz 1997). */
    const val ACTIVITY_HRR = 0.20f
    const val EXERCISE_HRR = 0.30f

    private val STAGE_WEIGHT = mapOf(
        SleepStage.DEEP to 1.0f,
        SleepStage.LIGHT to 0.8f,
        SleepStage.REM to 0.65f,
        SleepStage.AWAKE to 0.1f,
    )

    /**
     * Runs the charge model over [minutes] (one per minute, ascending; gaps are filled as no-data),
     * starting from [start], and reports the part inside [window].
     */
    fun charge(
        minutes: List<Minute>,
        params: ChargeParams,
        window: LongRange,
        start: Float = 50f,
        sampleSeconds: Long = 300,
        mainSleepEnd: Long? = null,
        /** Report nothing until a sleep has been seen (see [ChargeDay.anchored]); tests switch this off. */
        requireAnchor: Boolean = true,
    ): ChargeDay {
        var c = start
        var epoc = 0f
        var lastHr: Int? = null
        val points = mutableListOf<ChargePoint>()
        var recharged = 0f
        var exercise = 0f
        var stress = 0f
        var living = 0f
        var atWake: Float? = null
        var latest: Float? = null
        val hrvFactor = params.hrvScore?.let { (0.75f + 0.25f * it / 80f).coerceIn(0.6f, 1.1f) } ?: 1f
        val nightRest = params.nightRestingHr ?: params.restingHr
        var anchored = false

        for (m in minutes) {
            val hr = m.hr
            val x = hr?.let { hrr(it, params.restingHr, params.maxHr) }
            val state: State
            var delta: Float
            when {
                m.sleepStage != null -> {
                    anchored = true
                    state = State.Sleep
                    val relax = hr?.let { (1.2f - 0.04f * (it - nightRest)).coerceIn(0.5f, 1.2f) } ?: 1f
                    delta = SLEEP_K * (STAGE_WEIGHT[m.sleepStage] ?: 0.8f) * relax * hrvFactor * (100 - c)
                }
                // Off the wrist we can't tell rest from effort, so the value holds until data returns.
                hr == null || x == null -> {
                    state = State.NoData
                    delta = 0f
                }
                x >= EXERCISE_HRR -> {
                    state = State.Exercise
                    val t = trimpMinute(x, params.female)
                    epoc += t
                    delta = -BASE_DRAIN - EXERCISE_DRAIN_PER_TRIMP * t
                }
                // After exercise the heart rate stays up while the body recovers; that isn't stress.
                epoc > EPOC_THRESHOLD && (lastHr == null || hr <= lastHr + 2) -> {
                    state = State.ExerciseRecovery
                    delta = -BASE_DRAIN
                }
                x >= ACTIVITY_HRR || m.steps >= ACTIVE_STEPS -> {
                    state = State.Activity
                    delta = -BASE_DRAIN - EXERCISE_DRAIN_PER_TRIMP * trimpMinute(x, params.female)
                }
                m.stress != null && m.stress >= params.stressFrom -> {
                    state = State.Stress
                    delta = -BASE_DRAIN - STRESS_DRAIN * (m.stress - params.stressFrom)
                }
                m.stress != null && m.stress < RELAX_BELOW && m.steps < 10 -> {
                    state = State.Relax
                    // Calm, still minutes recover at about a quarter of light sleep's rate.
                    delta = -BASE_DRAIN + 0.25f * SLEEP_K * 0.8f * (100 - c)
                }
                else -> {
                    state = State.Rest
                    delta = -BASE_DRAIN
                }
            }
            // EPOC-like debt decays with a half-life of about 20 minutes once exercise stops.
            if (state != State.Exercise) epoc *= EPOC_DECAY
            if (hr != null) lastHr = hr

            val before = c
            c = (c + delta).coerceIn(0f, 100f)
            val applied = c - before
            if (m.t in window) {
                if (applied > 0) recharged += applied
                else when (state) {
                    State.Exercise, State.Activity -> { exercise += -applied * (1 - BASE_DRAIN / -delta); living += -applied * BASE_DRAIN / -delta }
                    State.ExerciseRecovery, State.Rest, State.NoData, State.Relax, State.Sleep -> living += -applied
                    State.Stress -> { stress += -applied * (1 - BASE_DRAIN / -delta); living += -applied * BASE_DRAIN / -delta }
                }
                if (m.t % sampleSeconds == 0L || points.isEmpty()) points += ChargePoint(m.t, c, state)
                if (state != State.NoData) latest = c
            }
            if (mainSleepEnd != null && atWake == null && m.t >= mainSleepEnd) atWake = c
        }
        val values = points.map { it.value }
        if (!anchored && requireAnchor) return ChargeDay(emptyList(), null, null, 0f, 0f, 0f, 0f, null, null, anchored = false)
        return ChargeDay(points, latest, atWake, recharged, exercise, stress, living, values.maxOrNull(), values.minOrNull(), anchored)
    }

    private const val EPOC_THRESHOLD = 5f
    private const val EPOC_DECAY = 0.966f
    private const val ACTIVE_STEPS = 60

    /**
     * Builds one [Minute] per minute over [from, to): heart rate at that minute, the latest stress
     * value within the last [stressHoldSeconds], and the sleep stage covering the minute.
     */
    fun minutes(
        from: Long,
        to: Long,
        hr: List<Pair<Long, Int>>,
        steps: Map<Long, Int>,
        stress: List<Pair<Long, Int>>,
        stages: List<Triple<Long, Long, Int>>,
        stressHoldSeconds: Long = 10 * 60,
    ): List<Minute> {
        val hrByMinute = hr.associate { (it.first / 60 * 60) to it.second }
        val stressSorted = stress.sortedBy { it.first }
        val stageSorted = stages.sortedBy { it.first }
        var si = 0
        var lastStress: Pair<Long, Int>? = null
        var gi = 0
        val out = ArrayList<Minute>(((to - from) / 60).toInt().coerceAtLeast(0))
        var t = from / 60 * 60
        while (t < to) {
            while (si < stressSorted.size && stressSorted[si].first <= t) { lastStress = stressSorted[si]; si++ }
            while (gi < stageSorted.size && stageSorted[gi].second <= t) gi++
            val stage = stageSorted.getOrNull(gi)?.takeIf { t >= it.first && t < it.second }?.third
            val s = lastStress?.takeIf { t - it.first <= stressHoldSeconds }?.second
            out += Minute(t, hrByMinute[t], steps[t] ?: 0, s, stage)
            t += 60
        }
        return out
    }

    /** Lowest rolling-[windowMinutes] average in [series], with the time at its centre. */
    fun lowestAverage(series: List<Pair<Long, Int>>, windowMinutes: Int): Pair<Long, Float>? {
        if (series.size < windowMinutes / 2) return null
        var best: Pair<Long, Float>? = null
        var lo = 0
        var sum = 0L
        for (hi in series.indices) {
            sum += series[hi].second
            while (series[hi].first - series[lo].first >= windowMinutes * 60L) { sum -= series[lo].second; lo++ }
            val n = hi - lo + 1
            if (n >= windowMinutes / 2) {
                val avg = sum.toFloat() / n
                if (best == null || avg < best.second) best = ((series[lo].first + series[hi].first) / 2) to avg
            }
        }
        return best
    }

    private fun signed(v: Int) = if (v > 0) "+$v" else if (v < 0) "−${-v}" else "±0"
    /** "+0.4", "−3", or "±0" when it rounds to zero (never "-0"). */
    private fun signed(v: Float, decimals: Int): String {
        val text = "%.${decimals}f".format(abs(v))
        return when {
            text.trim('0', '.').isEmpty() -> "±$text"
            v > 0 -> "+$text"
            else -> "−$text"
        }
    }
    fun formatMinutes(m: Int) = if (m >= 60) "${m / 60}h ${"%02d".format(m % 60)}m" else "${m}m"
}
