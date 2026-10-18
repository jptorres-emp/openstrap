package org.openstrap.app.data

import org.openstrap.app.data.db.SleepStage
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign

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
        /** Lowest 30-minute average heart rate during the sleep, bpm (`rhr_n`). */
        val restingHr: Float? = null,
        /** Mean of the per-minute heart rate during the sleep, bpm (`rhr_mean_sleep`). */
        val rhrMeanSleep: Float? = null,
        /** 10th percentile of the per-minute heart rate during the sleep, bpm (`rhr_p10`). */
        val rhrP10: Float? = null,
        /** RMSSD samples behind [hrv], after gating to [HRV_MIN_MS]..[HRV_MAX_MS]. */
        val hrvSamples: Int = 0,
        /** Minutes of heart rate in [HR_MIN_BPM]..[HR_MAX_BPM] during the sleep. */
        val hrMinutes: Int = 0,
        /** When the lowest 10-minute heart rate happened (epoch seconds). */
        val nadirTime: Long? = null,
        val skinTemperature: Float? = null,
        val breathRate: Float? = null,
        /** Wake after sleep onset: awake minutes between the first and the last sleep stage; null without stages. */
        val wasoMinutes: Int? = null,
        /** Minutes asleep in naps on this day, counted toward total sleep for the debt. */
        val napMinutes: Int = 0,
        /** Mean blood oxygen during the sleep, %. */
        val spo2: Float? = null,
        /** Oxygen-desaturation events per hour asleep, from the strap's nightly sleep-apnea summary. */
        val odi: Float? = null,
        /** The strap's own nightly HRV (ms) and resting HR (bpm) for this night, when it reported them. */
        val strapHrv: Float? = null,
        val strapRestingHr: Float? = null,
    ) {
        /**
         * The validity rule for OpenStrap's own HRV and resting HR: a main sleep of at least 3 h, ≥ [MIN_HRV_SAMPLES]
         * gated RMSSD samples and ≥ [MIN_HR_MINUTES] minutes of gated heart rate. Invalid nights never enter a baseline.
         */
        val valid: Boolean
            get() = end - start >= MIN_VALID_SLEEP_MINUTES * 60L && hrvSamples >= MIN_HRV_SAMPLES && hrMinutes >= MIN_HR_MINUTES

        /** `hrv_n` = ln(mean gated RMSSD) on a valid night, else null. */
        val hrvLn: Float? get() = hrv?.takeIf { valid && it > 0 }?.let { ln(it) }
        val validRestingHr: Float? get() = restingHr?.takeIf { valid }
    }

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

    /**
     * A robust personal baseline: [centre] is the median and [scale] is 1.4826·MAD, floored, so one bad stretch
     * (illness, alcohol, an artefact night) can't drag "normal" with it the way a mean and SD do.
     */
    data class Baseline(val centre: Float, val scale: Float, val n: Int) {
        fun z(x: Float) = (x - centre) / scale
    }

    /** Baseline from [values] with its scale floored at [floor]; null with fewer than [minN] values. */
    fun baseline(values: List<Float>, floor: Float, minN: Int = SCORED_NIGHTS): Baseline? {
        if (values.size < minN || values.isEmpty()) return null
        val centre = percentile(values, 0.5f)!!
        val mad = percentile(values.map { abs(it - centre) }, 0.5f)!!
        return Baseline(centre, max(MAD_SCALE * mad, floor), values.size)
    }

    /** 1.4826·MAD estimates the SD for normally distributed data. */
    const val MAD_SCALE = 1.4826f

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

    /**
     * Phases by valid nights in the baseline (they replace MIN_BASELINE_NIGHTS = 4 and CALIBRATED_NIGHTS = 14):
     * below [SCORED_NIGHTS] HRV and resting HR are withheld and the strap's estimate is shown instead; up to
     * [SETTLED_NIGHTS] the score is labelled "learning"; from [CALIBRATED_NIGHTS] it's labelled "calibrated".
     */
    const val SCORED_NIGHTS = 7
    const val SETTLED_NIGHTS = 14
    const val CALIBRATED_NIGHTS = 28
    /** Valid nights of history a baseline looks back over (published windows run 28-60). */
    const val BASELINE_NIGHTS = 60

    enum class Phase { Withheld, Learning, Scored, Calibrated }

    fun phase(validNights: Int): Phase = when {
        validNights < SCORED_NIGHTS -> Phase.Withheld
        validNights < SETTLED_NIGHTS -> Phase.Learning
        validNights < CALIBRATED_NIGHTS -> Phase.Scored
        else -> Phase.Calibrated
    }

    /** The validity rule's thresholds (see [Night.valid]). */
    const val MIN_VALID_SLEEP_MINUTES = 3 * 60
    const val MIN_HRV_SAMPLES = 6
    const val MIN_HR_MINUTES = 120
    /** The lowest 30-minute window needs this many minutes of heart rate in it. */
    const val RHR_WINDOW_MIN_MINUTES = 20

    // ------------------------------------------------------------------------------------------------
    // Readiness
    // ------------------------------------------------------------------------------------------------

    /**
     * Readiness contributors and their weights (a design choice, evidence-review.md): autonomic 52 %, sleep 28 %, other
     * body signals 13 %, load 4 %, renormalised over the contributors present. Breathing disturbances (a stable trait,
     * so only a fixed offset inside Readiness) moved to the Breathing card ([breathingDisturbances]); training load
     * balance (ACWR) moved to the Load screen ([acwr]).
     */
    enum class Factor(val label: String, val weight: Float, val physiological: Boolean = false) {
        HrvBalance("HRV balance", 0.30f, physiological = true),
        RestingHr("Resting heart rate", 0.22f, physiological = true),
        Sleep("Last night's sleep", 0.16f),
        SleepBalance("Sleep balance", 0.12f),
        Temperature("Body temperature", 0.06f, physiological = true),
        Breathing("Breathing rate", 0.04f, physiological = true),
        PreviousDayLoad("Previous day's load", 0.04f),
        BloodOxygen("Blood oxygen", 0.03f, physiological = true),
        RecoveryIndex("Recovery index", 0.03f),
    }

    data class Contributor(val factor: Factor, val score: Int, val detail: String)

    /** Where the 7-night HRV sits against the normal range, centre ± [HRV_RANGE_SCALE]·scale (the trials' band). */
    enum class HrvRange { Below, Within, Above }

    data class Readiness(
        /** 0-100, or null when there's no sleep, or no HRV or resting HR to score ([withheld]). */
        val score: Int?,
        val contributors: List<Contributor>,
        /** Valid nights in the HRV and resting HR baselines (the larger of the two); sets [phase]. */
        val baselineNights: Int,
        /** Body signals off baseline in the Alert state ([BodySignals]); worth taking it easy. */
        val strainAlert: Boolean,
        /** The signals that moved, in plain words, for the alert. */
        val alertSignals: List<String>,
        /** True when the weighted score was capped by one very low physiological contributor. */
        val capped: Boolean,
        val phase: Phase = phase(baselineNights),
        /**
         * There was a night but neither an HRV nor a resting HR contributor, so no score: sleep alone isn't readiness
         * (on 2026-10-16 it gave 52 against the strap's 91). The sleep contributor and the strap's estimate show instead.
         */
        val withheld: Boolean = false,
        val hrvRange: HrvRange? = null,
        /** Valid nights behind the 7-night HRV; "thin" below [HRV_WEEK_FULL_NIGHTS]. */
        val hrvWeekNights: Int = 0,
        /** The strap's values, shown (and used by alerts) because tonight's own ones failed the validity rule. */
        val fallbackHrv: Float? = null,
        val fallbackRestingHr: Float? = null,
        /** Data-quality notes: own and strap values disagree by more than [HRV_QA_FRACTION] or [RHR_QA_BPM]. */
        val qualityFlags: List<String> = emptyList(),
        /** Tonight in the body-signal state machine: Normal, Watch (in-app) or Alert (push, capped). */
        val bodySignals: BodySignals.Day? = null,
    ) {
        val calibrating get() = phase == Phase.Withheld || phase == Phase.Learning
        val hrvWeekThin get() = hrvWeekNights in HRV_WEEK_MIN_NIGHTS until HRV_WEEK_FULL_NIGHTS
        /** Show the strap's own score and parts as "Strap's estimate": before our baselines exist, or when withheld. */
        val showStrapEstimate get() = phase == Phase.Withheld || withheld
    }

    /**
     * Maps a z-score (deviation from baseline in standard deviations, positive = better) to 0-100.
     * A normal night (z = 0) scores 80, one SD better ~93, one SD worse ~55, two SD worse ~27.
     */
    fun scoreFromZ(z: Float): Int = Math.round(100.0 / (1.0 + exp(-(Z_SLOPE * z + Z_OFFSET)))).toInt().coerceIn(0, 100)
    private const val Z_SLOPE = 1.19
    private val Z_OFFSET = ln(4.0)

    /** Smallest worthwhile change, as a fraction of the baseline scale (Buchheit; Plews uses 0.5 for HRV). */
    const val SWC_SD = 0.5f

    /** The dead zone at the smallest worthwhile change: z_eff = sign(z)·max(0, |z| − [SWC_SD]). Smaller changes are noise. */
    fun deadZone(z: Float): Float = sign(z) * max(0f, abs(z) - SWC_SD)

    /** Floors so a very steady baseline doesn't turn tiny changes into huge z-scores (RHR's sits above device error). */
    const val HRV_LN_SD_FLOOR = 0.05f
    const val RHR_SD_FLOOR = 2f
    const val BREATH_SD_FLOOR = 0.5f
    const val TEMP_SD_FLOOR = 0.15f
    const val SPO2_SD_FLOOR = 0.5f

    /** Tonight's and the 7-night HRV in the score input; nightly wearable HRV error is larger than the SWC. */
    const val HRV_NIGHT_WEIGHT = 0.4f
    const val HRV_WEEK_WEIGHT = 0.6f
    /** The 7-night mean needs this many valid nights, and is "thin" below the second. */
    const val HRV_WEEK_MIN_NIGHTS = 3
    const val HRV_WEEK_FULL_NIGHTS = 5
    const val HRV_RANGE_SCALE = 0.5f
    /** Own and strap values further apart than this raise a data-quality flag. */
    const val HRV_QA_FRACTION = 0.15f
    const val RHR_QA_BPM = 3f
    /** Default sleep need: 480 min for ages 18-64, [SLEEP_NEED_65_MINUTES] from 65 (NSF 7-9 h and 7-8 h; AASM ≥ 7 h). */
    const val SLEEP_NEED_MINUTES = 8 * 60
    const val SLEEP_NEED_65_MINUTES = 450
    /** A user's own need can be set within this range (individual optimal sleep ran 7.3-9.3 h, Kitamura 2016). */
    const val SLEEP_NEED_MIN = 420
    const val SLEEP_NEED_MAX = 540

    /** Sleep need in minutes: the user's [override] when it's in range, else by [age]. */
    fun sleepNeed(age: Int?, override: Int? = null): Int = when {
        override != null && override in SLEEP_NEED_MIN..SLEEP_NEED_MAX -> override
        age != null && age >= 65 -> SLEEP_NEED_65_MINUTES
        else -> SLEEP_NEED_MINUTES
    }

    /**
     * Readiness for [tonight] against [history] (earlier nights, any order, tonight excluded) and
     * [dailyLoad] (TRIMP per day, oldest first, ending with yesterday). [causes] gives the likely causes noted for a
     * morning, to annotate a body-signal alert.
     */
    fun readiness(
        tonight: Night?,
        history: List<Night>,
        dailyLoad: List<Float> = emptyList(),
        causes: (Long) -> List<String> = { emptyList() },
        sleepNeed: Int = SLEEP_NEED_MINUTES,
    ): Readiness {
        if (tonight == null) {
            val valid = history.filter { it.valid }.takeLast(BASELINE_NIGHTS)
            return Readiness(null, emptyList(), max(valid.count { it.hrvLn != null }, valid.count { it.restingHr != null }), false, emptyList(), false)
        }
        val earlier = history.filter { it.day < tonight.day }.sortedBy { it.day }
        // Body signals first: nights in Watch or Alert stay out of every baseline, so a bad stretch can't become normal.
        val states = BodySignals.evaluate(earlier + tonight, causes)
        val flagged = states.filter { it.state != BodySignals.State.Normal }.mapTo(HashSet()) { it.day }
        val body = states.last()
        val usable = earlier.filter { it.day !in flagged }
        // Baselines use the prior BASELINE_NIGHTS valid nights; invalid nights never enter them.
        val validPast = usable.filter { it.valid }.takeLast(BASELINE_NIGHTS)
        val past = usable.takeLast(BASELINE_NIGHTS)
        val out = mutableListOf<Contributor>()

        val lnHistory = validPast.mapNotNull { it.hrvLn }
        val rhrHistory = validPast.mapNotNull { it.restingHr }
        val nights = max(lnHistory.size, rhrHistory.size)
        val phase = phase(nights)
        val scored = phase != Phase.Withheld

        // HRV balance: 0.4·z(tonight) + 0.6·z(7-night mean of hrv_n) against the robust baseline, through the dead zone.
        val hrvBase = baseline(lnHistory, HRV_LN_SD_FLOOR)
        val week = (earlier + tonight).filter { tonight.day - it.day <= 6 * DAY + DST_SLACK }.mapNotNull { it.hrvLn }
        val hrv7 = week.takeIf { it.size >= HRV_WEEK_MIN_NIGHTS }?.average()?.toFloat()
        val hrvRange = if (hrvBase != null && hrv7 != null) when {
            hrv7 < hrvBase.centre - HRV_RANGE_SCALE * hrvBase.scale -> HrvRange.Below
            hrv7 > hrvBase.centre + HRV_RANGE_SCALE * hrvBase.scale -> HrvRange.Above
            else -> HrvRange.Within
        } else null
        val ln = tonight.hrvLn
        if (scored && hrvBase != null && ln != null) {
            val zNight = hrvBase.z(ln)
            val z = if (hrv7 != null) HRV_NIGHT_WEIGHT * zNight + HRV_WEEK_WEIGHT * hrvBase.z(hrv7) else zNight
            val pct = ((exp(ln - hrvBase.centre.toDouble()) - 1) * 100).toInt()
            out += Contributor(Factor.HrvBalance, scoreFromZ(deadZone(z)), "${tonight.hrv!!.toInt()} ms · ${signed(pct)}% vs usual")
        }

        // Resting heart rate: the night's lowest 30-minute average against baseline; lower is better.
        val rhrBase = baseline(rhrHistory, RHR_SD_FLOOR)
        val rhr = tonight.validRestingHr
        if (scored && rhrBase != null && rhr != null) {
            val delta = rhr - rhrBase.centre
            out += Contributor(Factor.RestingHr, scoreFromZ(deadZone(-rhrBase.z(rhr))), "${rhr.toInt()} bpm · ${signed(delta, 0)} vs usual")
        }

        // The strap's own values: a fallback for display when ours failed the validity rule, and a quality check.
        val fallbackHrv = tonight.strapHrv?.takeIf { tonight.hrvLn == null }
        val fallbackRhr = tonight.strapRestingHr?.takeIf { tonight.validRestingHr == null }
        val quality = buildList {
            val own = tonight.hrv?.takeIf { tonight.valid }
            val strap = tonight.strapHrv
            if (own != null && strap != null && strap > 0 && abs(own - strap) / strap > HRV_QA_FRACTION) {
                add("HRV: ours ${own.toInt()} ms, the strap's ${strap.toInt()} ms")
            }
            val strapRhr = tonight.strapRestingHr
            if (rhr != null && strapRhr != null && abs(rhr - strapRhr) > RHR_QA_BPM) add("Resting HR: ours ${rhr.toInt()} bpm, the strap's ${strapRhr.toInt()} bpm")
        }

        // Last night's sleep: the strap's own score when there is one, else ours.
        out += Contributor(Factor.Sleep, tonight.strapScore ?: sleepScore(tonight, sleepNeed), formatMinutes(tonight.asleepMinutes) + " asleep")

        // Sleep balance: debt built over the last 14 nights, against need (naps count).
        sleepDebtMinutes(earlier + tonight, tonight.day, sleepNeed)?.let { debt ->
            val score = (100 - SLEEP_DEBT_POINTS_PER_HOUR * debt / 60f).toInt().coerceIn(0, 100)
            val detail = if (debt < 3f) "No sleep debt" else "${formatMinutes(debt.toInt())} of sleep debt"
            out += Contributor(Factor.SleepBalance, score, detail)
        }

        // Recovery index: the night's lowest heart rate should come in its first half (Oura).
        if (tonight.nadirTime != null && tonight.end > tonight.start) {
            val p = (tonight.nadirTime - tonight.start).toFloat() / (tonight.end - tonight.start)
            val score = if (p <= 0.5f) 100 else (100 - 120 * (p - 0.5f)).toInt().coerceIn(40, 100)
            val after = ((tonight.end - tonight.nadirTime) / 60).toInt()
            out += Contributor(Factor.RecoveryIndex, score, "Lowest HR ${formatMinutes(after)} before waking")
        }

        // Body temperature: distance from the usual nightly skin temperature, either direction, through the dead zone.
        val tempBase = baseline(past.mapNotNull { it.skinTemperature }, TEMP_SD_FLOOR)
        tonight.skinTemperature?.let { t ->
            if (tempBase != null) {
                val dev = t - tempBase.centre
                out += Contributor(Factor.Temperature, scoreFromZ(-abs(deadZone(tempBase.z(t)))), "${signed(dev, 1)} °C vs usual")
            }
        }

        // Breathing rate: very stable night to night; a change either way beyond the dead zone counts.
        val breathBase = baseline(past.mapNotNull { it.breathRate }, BREATH_SD_FLOOR)
        tonight.breathRate?.let { b ->
            if (breathBase != null) {
                val dev = b - breathBase.centre
                out += Contributor(Factor.Breathing, scoreFromZ(-abs(deadZone(breathBase.z(b)))), "${signed(dev, 1)} /min vs usual")
            }
        }

        // Blood oxygen: only a fall below the usual nightly level counts; a higher reading is no better.
        val spo2Base = baseline(past.mapNotNull { it.spo2 }, SPO2_SD_FLOOR)
        tonight.spo2?.let { o ->
            if (spo2Base != null) {
                val dev = o - spo2Base.centre
                val score = (100 - SPO2_POINTS_PER_PERCENT * (-dev - SPO2_DEAD_BAND).coerceAtLeast(0f)).toInt().coerceIn(0, 100)
                out += Contributor(Factor.BloodOxygen, score, "${"%.1f".format(o)} % · ${signed(dev, 1)} vs usual")
            }
        }

        // Training load: yesterday against fitness (CTL, 42-day) up to the day before.
        if (dailyLoad.size >= 7) {
            val chronic = fitnessFatigue(dailyLoad.dropLast(1)).last().ctl
            val yesterday = dailyLoad.last()
            val ratio = yesterday / max(chronic, MIN_CHRONIC_LOAD)
            val score = if (ratio <= 1.5f) 100 else (100 - 25 * (ratio - 1.5f)).toInt().coerceIn(30, 100)
            out += Contributor(Factor.PreviousDayLoad, score, "Load ${yesterday.toInt()} · usual ${chronic.toInt()}")
        }

        val alert = body.state == BodySignals.State.Alert
        val extras = { score: Int?, capped: Boolean, withheld: Boolean ->
            Readiness(
                score, out, nights, alert, if (body.state == BodySignals.State.Normal) emptyList() else body.signals, capped, phase,
                withheld, hrvRange, week.size, fallbackHrv, fallbackRhr, quality, body,
            )
        }
        // Withhold without an autonomic contributor: sleep alone isn't readiness.
        if (out.none { it.factor == Factor.HrvBalance || it.factor == Factor.RestingHr }) return extras(null, false, true)
        val totalWeight = out.sumOf { it.factor.weight.toDouble() }
        var score = (out.sumOf { it.factor.weight.toDouble() * it.score } / totalWeight).toInt()
        // One very poor body signal shouldn't be averaged away by good sleep and a quiet day.
        val worst = out.filter { it.factor.physiological }.minOfOrNull { it.score }
        var capped = false
        if (worst != null && worst < RED_FLAG && score > worst + RED_FLAG_HEADROOM) {
            score = worst + RED_FLAG_HEADROOM
            capped = true
        }
        if (alert && score > ALERT_CAP) { score = ALERT_CAP; capped = true }
        return extras(score.coerceIn(0, 100), capped, false)
    }

    /** Sleep debt halves over 7 nights: λ = 0.5^(1/7) ≈ 0.906, so a 14-night build-up still counts. */
    const val SLEEP_DEBT_HALF_LIFE_NIGHTS = 7.0
    val SLEEP_DEBT_DECAY = 0.5.pow(1 / SLEEP_DEBT_HALF_LIFE_NIGHTS).toFloat()
    /** A long night repays half its surplus, at most an hour. */
    const val SLEEP_DEBT_CREDIT_FRACTION = 0.5f
    const val SLEEP_DEBT_MAX_CREDIT_MINUTES = 60f
    const val SLEEP_DEBT_NIGHTS = 14
    const val SLEEP_DEBT_MIN_NIGHTS = 7

    /**
     * Sleep debt in minutes at the morning [day], from the last [SLEEP_DEBT_NIGHTS] nights of [nights] (oldest to
     * newest; missing nights skipped; naps count toward total sleep). Each night d = need − TST: a short night adds d
     * on top of the decayed debt, a long one repays min(½·surplus, 60 min). Null with fewer than [SLEEP_DEBT_MIN_NIGHTS].
     * Deficits build near-linearly over 14 days without people noticing (Van Dongen 2003); about 1 h of debt took 4
     * nights to clear (Kitamura 2016).
     */
    fun sleepDebtMinutes(nights: List<Night>, day: Long, need: Int = SLEEP_NEED_MINUTES): Float? {
        val window = nights.filter { it.day <= day && day - it.day < SLEEP_DEBT_NIGHTS * DAY - DST_SLACK }.sortedBy { it.day }
        if (window.size < SLEEP_DEBT_MIN_NIGHTS) return null
        var debt = 0f
        for (n in window) {
            val d = need - (n.asleepMinutes + n.napMinutes).toFloat()
            debt = if (d >= 0) debt * SLEEP_DEBT_DECAY + d
            else max(0f, debt * SLEEP_DEBT_DECAY - min(SLEEP_DEBT_CREDIT_FRACTION * -d, SLEEP_DEBT_MAX_CREDIT_MINUTES))
        }
        return debt
    }
    private const val DAY = 86_400L
    /** Local midnights a DST change apart are an hour off a whole number of days. */
    private const val DST_SLACK = 2 * 3600L
    /** Readiness points lost per hour of average nightly shortfall (1 h short ≈ 75, 2 h ≈ 50). */
    const val SLEEP_DEBT_POINTS_PER_HOUR = 25
    const val RED_FLAG = 40
    const val RED_FLAG_HEADROOM = 30
    const val ALERT_CAP = 55
    private const val MIN_CHRONIC_LOAD = 20f
    /** Readiness points lost per % of nightly SpO2 below baseline, beyond a 1 % dead band (−2 % ≈ 75, −3 % ≈ 50). */
    const val SPO2_POINTS_PER_PERCENT = 25
    const val SPO2_DEAD_BAND = 1f

    /** The Breathing card: tonight's oxygen-desaturation events per hour against the user's own nights. */
    data class BreathingDisturbances(val perHour: Float, val usual: Float?, val nights: Int)

    /**
     * Events per hour for [tonight], with the median of earlier nights once there are [SCORED_NIGHTS] of them. No
     * normal / mild / moderate / severe words: those are apnea severity bands (evidence-review.md, "Wording").
     */
    fun breathingDisturbances(tonight: Night?, history: List<Night>): BreathingDisturbances? {
        val odi = tonight?.odi ?: return null
        val past = history.filter { it.day < tonight.day }.sortedBy { it.day }.takeLast(BASELINE_NIGHTS).mapNotNull { it.odi }
        return BreathingDisturbances(odi, past.takeIf { it.size >= SCORED_NIGHTS }?.let { percentile(it, 0.5f) }, past.size)
    }

    /**
     * Acute:chronic workload ratio for the Load screen only, labelled contested: it is out of Readiness because the
     * evidence is against scoring with it (evidence-review.md, "Load screen"). Null before 14 days of load.
     */
    fun acwr(daily: List<Float>): Float? {
        // Uncoupled: the chronic EWMA stops where the acute week begins, so this week's load isn't in both.
        if (daily.size < ACWR_ACUTE_DAYS + ACWR_MIN_CHRONIC_DAYS) return null
        val acute = ewma(daily, ACWR_ACUTE_DAYS)
        val chronic = ewma(daily.dropLast(ACWR_ACUTE_DAYS), ACWR_CHRONIC_DAYS)
        return acute / max(chronic, MIN_CHRONIC_LOAD)
    }

    const val ACWR_ACUTE_DAYS = 7
    const val ACWR_CHRONIC_DAYS = 28
    const val ACWR_MIN_CHRONIC_DAYS = 14

    /** Exponentially weighted mean of [daily] with λ = 2/([days] + 1). */
    fun ewma(daily: List<Float>, days: Int): Float {
        val l = 2f / (days + 1)
        var v = daily.first()
        for (x in daily.drop(1)) v = l * x + (1 - l) * v
        return v
    }

    /**
     * Our sleep score when the strap didn't give one: 0.7·duration + 0.3·continuity. No stage target: four-stage
     * agreement across six devices was kappa 0.21-0.53 (Schyvens et al. 2025), so stages are shown, not scored. And
     * low wake specificity inflates wearable efficiency, so continuity uses wake after sleep onset, not time in bed.
     * Without stages (no WASO), duration alone.
     */
    fun sleepScore(n: Night, need: Int = SLEEP_NEED_MINUTES): Int {
        val duration = durationScore(n.asleepMinutes, need)
        val continuity = n.wasoMinutes?.let { continuityScore(it) } ?: return Math.round(duration)
        return Math.round(SLEEP_DURATION_WEIGHT * duration + SLEEP_CONTINUITY_WEIGHT * continuity).coerceIn(0, 100)
    }

    const val SLEEP_DURATION_WEIGHT = 0.7f
    const val SLEEP_CONTINUITY_WEIGHT = 0.3f
    /** WASO up to this is free; continuity then falls to 0 over the next [WASO_SPAN_MINUTES]. */
    const val WASO_FREE_MINUTES = 30f
    const val WASO_SPAN_MINUTES = 90f

    /** 100·min(1, TST/need)²: 7 of 8 h ≈ 77, 6 h ≈ 56. */
    fun durationScore(asleepMinutes: Int, need: Int = SLEEP_NEED_MINUTES): Float =
        100f * (asleepMinutes.toFloat() / need).coerceIn(0f, 1f).pow(2)

    /** clamp(100 − (WASO − 30)·(100/90), 0, 100). */
    fun continuityScore(wasoMinutes: Int): Float = (100f - (wasoMinutes - WASO_FREE_MINUTES) * (100f / WASO_SPAN_MINUTES)).coerceIn(0f, 100f)

    /**
     * Wake after sleep onset from [stages] (start, end, stage): awake minutes between the first sleep stage's start
     * and the last one's end. Null without any sleep stage.
     */
    fun wasoMinutes(stages: List<Triple<Long, Long, Int>>): Int? {
        val sleep = stages.filter { it.third != SleepStage.AWAKE }
        if (sleep.isEmpty()) return null
        val onset = sleep.minOf { it.first }
        val wake = sleep.maxOf { it.second }
        val seconds = stages.filter { it.third == SleepStage.AWAKE }.sumOf { (max(it.first, onset) until min(it.second, wake)).let { r -> max(0L, r.last - r.first + 1) } }
        return (seconds / 60).toInt()
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

    /**
     * Daily load: TRIMP over awake minutes with HR at or above [LOAD_MIN_HRR] of reserve, counting a minute only when at
     * least [LOAD_RUN_MINUTES] of the last [LOAD_RUN_WINDOW] minutes (itself included) are there too, so isolated PPG
     * spikes don't count. No HR-load method is validated on free-living minutes; Banister is the least-bad choice.
     */
    fun dailyLoad(hr: List<Pair<Long, Int>>, rest: Float, maxHr: Float, female: Boolean = false, asleep: (Long) -> Boolean = { false }): Float {
        val minutes = hr.filter { !asleep(it.first) }.sortedBy { it.first }.map { (t, bpm) -> t to hrr(bpm, rest, maxHr) }
        val above = minutes.filter { it.second >= LOAD_MIN_HRR }
        var lo = 0
        var total = 0.0
        for ((k, m) in above.withIndex()) {
            while (above[lo].first <= m.first - LOAD_RUN_WINDOW * 60L) lo++
            if (k - lo + 1 >= LOAD_RUN_MINUTES) total += trimpMinute(m.second, female)
        }
        return total.toFloat()
    }

    const val LOAD_RUN_MINUTES = 3
    const val LOAD_RUN_WINDOW = 5

    /**
     * Maximum heart rate: a [tested] value the user entered wins. Otherwise Tanaka's 208 − 0.7·age (18,712 subjects)
     * when the age is known, else the strap's own value (from the Zepp profile), else 190. Raised to [observedPeak]
     * when the user has actually gone higher (an equation's individual SD is 7-11 bpm, so raising is sound and
     * lowering is not), by at most [MAX_HR_RAISE] bpm.
     */
    fun maxHeartRate(age: Int?, strapMax: Int? = null, observedPeak: Int? = null, tested: Int? = null): Float {
        if (tested != null && tested in 120..230) return tested.toFloat()
        val estimate = when {
            age != null && age in 10..100 -> 208f - 0.7f * age
            strapMax != null && strapMax in 140..225 -> strapMax.toFloat()
            else -> 190f
        }
        return if (observedPeak != null && observedPeak > estimate) min(observedPeak.toFloat(), estimate + MAX_HR_RAISE) else estimate
    }

    const val MAX_HR_RAISE = 15f
    /** Two separate days must reach within this of each other before a peak counts. */
    const val MAX_HR_AGREE_BPM = 3

    /**
     * The highest heart rate the user has really reached, from each day's artefact-filtered maximum [dailyMaxima]: the
     * second of the first two days (from the top) within [MAX_HR_AGREE_BPM] of each other. One day alone, however
     * high, never sets it. Null when no two days agree.
     */
    fun observedPeak(dailyMaxima: List<Int>): Int? {
        val sorted = dailyMaxima.sortedDescending()
        for (i in 0 until sorted.size - 1) if (sorted[i] - sorted[i + 1] <= MAX_HR_AGREE_BPM) return sorted[i + 1]
        return null
    }

    /**
     * A day's artefact-filtered maximum 1-minute HR: the highest minute in [HR_MIN_BPM]..[HR_MAX_BPM] with a
     * neighbouring minute within [ARTEFACT_JUMP_BPM] of it (a lone spike isn't a heartbeat). [minutes] ascending.
     */
    fun filteredMax(minutes: List<Pair<Long, Int>>): Int? = minutes.indices.filter { i ->
        val (t, bpm) = minutes[i]
        bpm in HR_MIN_BPM..HR_MAX_BPM && listOfNotNull(minutes.getOrNull(i - 1), minutes.getOrNull(i + 1))
            .any { (u, v) -> abs(u - t) <= 60 && abs(v - bpm) <= ARTEFACT_JUMP_BPM }
    }.maxOfOrNull { minutes[it].second }

    const val ARTEFACT_JUMP_BPM = 10

    /**
     * Daytime resting HR for heart-rate reserve: Karvonen and Swain define resting HR as awake rest, so a nightly
     * minimum would inflate HRR for every waking minute. The median of the strap's daily resting HR over the last
     * [RHR_DAYS] days ([daily], day → bpm); else the median nightly lowest ([nightly]) of those days plus an offset:
     * the median of (daily − nightly lowest) over the latest [RHR_DAYS] days that have both, else [RHR_OFFSET_BPM].
     */
    fun daytimeRestingHr(daily: Map<Long, Float>, nightly: Map<Long, Float>, day: Long): Float? {
        val recent = { m: Map<Long, Float> -> m.filterKeys { it <= day && day - it < RHR_DAYS * DAY - DST_SLACK }.values.toList() }
        percentile(recent(daily), 0.5f)?.let { return it }
        val lowest = percentile(recent(nightly), 0.5f) ?: return null
        val pairs = daily.keys.filter { it in nightly }.sortedDescending().take(RHR_DAYS).map { daily.getValue(it) - nightly.getValue(it) }
        return lowest + (percentile(pairs, 0.5f) ?: RHR_OFFSET_BPM)
    }

    const val RHR_DAYS = 14
    const val RHR_OFFSET_BPM = 3f

    /** Below this reserve fraction, heart rate is everyday living rather than training. */
    const val LOAD_MIN_HRR = 0.2f

    /** A day whose TRIMP is more than [HARD_DAY_RATIO]× the chronic load: annotated as a likely cause of an alert. */
    fun hardDay(load: Float, chronic: Float): Boolean = load > HARD_DAY_RATIO * max(chronic, MIN_CHRONIC_LOAD)
    const val HARD_DAY_RATIO = 1.5f

    /** Fitness (CTL), fatigue (ATL) and form for one day of [fitnessFatigue]. */
    data class LoadTrend(val ctl: Float, val atl: Float, val form: Float)

    /** The standard 42/7-day convention; personal time constants are not fitted (poorly identifiable, Imbach 2025). */
    const val CTL_DAYS = 42
    const val ATL_DAYS = 7

    /**
     * CTL_t = CTL_{t−1} + (TRIMP_t − CTL_{t−1})·(1 − e^(−1/42)), ATL likewise with 7, Form_t = CTL_{t−1} − ATL_{t−1}, over
     * [daily] (oldest first; only worn days, so an unworn day holds rather than decays). Seeded with the mean of the
     * first week, so a short history doesn't start at zero.
     */
    fun fitnessFatigue(daily: List<Float>): List<LoadTrend> {
        if (daily.isEmpty()) return listOf(LoadTrend(0f, 0f, 0f))
        val kc = 1 - exp(-1f / CTL_DAYS)
        val ka = 1 - exp(-1f / ATL_DAYS)
        var ctl = daily.take(7).average().toFloat()
        var atl = ctl
        return daily.map { x ->
            val form = ctl - atl
            ctl += (x - ctl) * kc
            atl += (x - atl) * ka
            LoadTrend(ctl, atl, form)
        }
    }

    /** Strain 0-21 from a day's TRIMP: 21·(1 − e^(−TRIMP/180)). A fixed curve, so today's 12 compares with last year's. */
    fun strain(trimp: Float): Float = STRAIN_MAX * (1 - exp(-trimp.coerceAtLeast(0f) / STRAIN_SCALE))
    const val STRAIN_MAX = 21f
    const val STRAIN_SCALE = 180f

    /** "vs your usual": the share (0-100) of the last 90 days' loads ([history]) at or below [today]; null under 7 days. */
    fun loadPercentile(today: Float, history: List<Float>): Int? =
        if (history.size < 7) null else Math.round(100f * history.count { it <= today } / history.size)

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
    /** Stress drains only after this many consecutive minutes at or above the threshold; a blip is rest. */
    const val STRESS_GATE_MINUTES = 3
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
        var stressRun = 0

        for (m in minutes) {
            // Consecutive awake minutes at or above the threshold: stress drains only from the third.
            stressRun = if (m.sleepStage == null && m.stress != null && m.stress >= params.stressFrom) stressRun + 1 else 0
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
                m.stress != null && m.stress >= params.stressFrom && stressRun >= STRESS_GATE_MINUTES -> {
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

    /** Gates for a nightly sample: RMSSD outside 5-200 ms or heart rate outside 30-220 bpm is treated as artefact. */
    const val HRV_MIN_MS = 5
    const val HRV_MAX_MS = 200
    const val HR_MIN_BPM = 30
    const val HR_MAX_BPM = 220

    /** The [p]-th percentile (0-1) of [values] by linear interpolation; null when empty. */
    fun percentile(values: List<Float>, p: Float): Float? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val pos = p.coerceIn(0f, 1f) * (sorted.size - 1)
        val lo = pos.toInt()
        val hi = min(lo + 1, sorted.size - 1)
        return sorted[lo] + (sorted[hi] - sorted[lo]) * (pos - lo)
    }

    /** Lowest rolling-[windowMinutes] average in [series], with the time at its centre. */
    fun lowestAverage(series: List<Pair<Long, Int>>, windowMinutes: Int, minCount: Int = windowMinutes / 2): Pair<Long, Float>? {
        if (series.size < minCount) return null
        var best: Pair<Long, Float>? = null
        var lo = 0
        var sum = 0L
        for (hi in series.indices) {
            sum += series[hi].second
            while (series[hi].first - series[lo].first >= windowMinutes * 60L) { sum -= series[lo].second; lo++ }
            val n = hi - lo + 1
            if (n >= minCount) {
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
