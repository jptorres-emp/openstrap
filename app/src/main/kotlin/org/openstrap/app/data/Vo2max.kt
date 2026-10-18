package org.openstrap.app.data

import org.openstrap.app.data.settings.UserProfile

/**
 * A non-exercise VO2max estimate (docs/algorithms/evidence-review.md, "VO2max and fitness age"): Jurca et al. 2005
 * needs only sex, age, BMI, resting HR and one activity question. Its SEE is 1.45-1.97 METs, with no bias in an
 * independent older-adult sample (Mailey et al. 2010). Shown as a band, never a decimal. No biological age.
 */
object Vo2max {

    /*
     * Jurca et al. 2005, Am J Prev Med 29(3):185-193, NASA model (Table 5) and the worksheet built from it (Figure 1),
     * checked against the full text: METs = 2.77·sex (female 0, male 1) − 0.10·age − 0.17·BMI − 0.03·RHR + SR-PA score
     * + 18.07, SEE 1.45 METs. The SR-PA scores are the NASA model's dummy weights, not 0-4.
     */
    const val SEX_COEF = 2.77f
    const val AGE_COEF = 0.10f
    const val BMI_COEF = 0.17f
    const val RHR_COEF = 0.03f
    const val INTERCEPT = 18.07f
    /** Score for activity levels 1-5 (index 0-4), Jurca 2005 Figure 1. */
    val SRPA_SCORES = floatArrayOf(0f, 0.32f, 1.06f, 1.76f, 3.03f)
    /** The NASA model's standard error of estimate, METs (≈ 5 ml/kg/min). */
    const val SEE_METS = 1.45f
    const val ML_PER_MET = 3.5f

    /** The worksheet's question, Jurca 2005 Figure 1 (shortened): the user's usual pattern of physical activity. */
    val ACTIVITY_LEVELS = listOf(
        "Inactive, or little activity beyond usual daily activities",
        "Light activity, at least 10 minutes at a time, 5 or more days a week",
        "Aerobic exercise 20–60 minutes a week",
        "Aerobic exercise 1–3 hours a week",
        "Aerobic exercise over 3 hours a week",
    )

    /** Withholding rules. */
    const val MIN_RHR_NIGHTS = 5
    const val RHR_LOOKBACK_DAYS = 14
    const val RHR_NIGHTS_USED = 7
    const val BODY_MAX_AGE_DAYS = 90
    const val ACTIVITY_MAX_AGE_DAYS = 180
    const val MIN_AGE = 20
    const val MAX_AGE = 79

    /** Jurca's estimate in METs. */
    fun jurcaMets(male: Boolean, age: Int, bmi: Float, restingHr: Float, activityLevel: Int): Float =
        SEX_COEF * (if (male) 1 else 0) - AGE_COEF * age - BMI_COEF * bmi - RHR_COEF * restingHr +
            SRPA_SCORES[activityLevel.coerceIn(0, SRPA_SCORES.size - 1)] + INTERCEPT

    data class Estimate(
        /** ml/kg/min, rounded: never shown as a decimal. */
        val value: Int,
        /** ±1 SEE. */
        val low: Int,
        val high: Int,
        /** The resting HR used: median of the last 7 nightly values. */
        val restingHr: Float,
        /** FRIEND 2022 percentile band; null until the tables are transcribed (see [Friend]). */
        val band: Friend.Band? = null,
    )

    /** Why there is no estimate, in the order checked. */
    enum class Withheld(val reason: String) {
        Age("Needs an age between 20 and 79."),
        Sex("Needs your sex: the equation differs for women and men."),
        Body("Needs a weight and height entered in the last 90 days."),
        Activity("Answer the activity question (asked every 6 months)."),
        Medication("Not shown while taking beta-blockers or during pregnancy: resting heart rate doesn't reflect fitness then."),
        RestingHr("Needs at least 5 nights of resting heart rate in the last 2 weeks."),
    }

    /**
     * The estimate for [day] (local midnight, epoch seconds), from [nightlyRhr] (morning → the night's resting HR) and
     * the [profile]. Sleeping RHR runs a few bpm below a seated one; at −0.03 METs per bpm that is small next to the SEE,
     * so it's documented, not corrected.
     */
    fun estimate(profile: UserProfile, age: Int?, nightlyRhr: Map<Long, Float>, day: Long): Pair<Estimate?, Withheld?> {
        val dayS = 86_400L
        if (age == null || age !in MIN_AGE..MAX_AGE) return null to Withheld.Age
        val sex = profile.sex ?: return null to Withheld.Sex
        val w = profile.weightKg
        val h = profile.heightCm
        val measured = profile.bodyMeasuredAt
        if (w == null || h == null || measured == null || day - measured > BODY_MAX_AGE_DAYS * dayS) return null to Withheld.Body
        val level = profile.activityLevel
        val answered = profile.activityAnsweredAt
        if (level == null || answered == null || day - answered > ACTIVITY_MAX_AGE_DAYS * dayS) return null to Withheld.Activity
        if (profile.betaBlocker || profile.pregnant) return null to Withheld.Medication
        val recent = nightlyRhr.filterKeys { it <= day && day - it < RHR_LOOKBACK_DAYS * dayS }.toSortedMap().values.toList()
        if (recent.size < MIN_RHR_NIGHTS) return null to Withheld.RestingHr
        val rhr = Recovery.percentile(recent.takeLast(RHR_NIGHTS_USED), 0.5f)!!
        val bmi = w / ((h / 100f) * (h / 100f))
        val mets = jurcaMets(sex == UserProfile.Sex.Male, age, bmi, rhr, level)
        val ml = mets * ML_PER_MET
        val see = SEE_METS * ML_PER_MET
        return Estimate(Math.round(ml), Math.round(ml - see), Math.round(ml + see), rhr, Friend.band(ml, sex, age)) to null
    }

    /**
     * FRIEND 2022 reference standards (Kaminsky et al., Mayo Clin Proc 2022;97(2):285-293, treadmill, apparently healthy
     * adults) for the percentile band < 25 / 25-50 / 50-75 / 75-97.7 / ≥ 97.7 and for fitness age (the age at which the
     * sex-specific 50th percentile equals the estimate).
     *
     * TODO: transcribe the treadmill percentile tables from the primary paper. They could not be verified here: the
     * evidence review's link (PMC8751972) is the FRIEND report by cardiovascular-disease category (Peterman et al., JAHA
     * 2022), not the healthy-adult standards, and no value is entered from memory. Until then [band] and [fitnessAge]
     * return null and nothing is shown.
     */
    object Friend {
        enum class Band(val label: String) { Below25("Below the 25th percentile"), P25to50("25th–50th"), P50to75("50th–75th"), P75to97("75th–97.7th"), Top("Top 2.3 %") }

        /** Age-decade rows (20-29 … 70-79): percentiles 25, 50, 75, 97.7 in ml/kg/min. Empty until transcribed. */
        val MEN: Map<IntRange, FloatArray> = emptyMap()
        val WOMEN: Map<IntRange, FloatArray> = emptyMap()

        fun band(vo2: Float, sex: UserProfile.Sex, age: Int): Band? {
            val row = (if (sex == UserProfile.Sex.Male) MEN else WOMEN).entries.firstOrNull { age in it.key }?.value ?: return null
            return when {
                vo2 < row[0] -> Band.Below25
                vo2 < row[1] -> Band.P25to50
                vo2 < row[2] -> Band.P50to75
                vo2 < row[3] -> Band.P75to97
                else -> Band.Top
            }
        }

        /** Fitness age, clamped 20-80 and shown as a range of about ±10 years; null until the tables exist. */
        fun fitnessAge(vo2: Float, sex: UserProfile.Sex): IntRange? {
            val rows = (if (sex == UserProfile.Sex.Male) MEN else WOMEN).entries.sortedBy { it.key.first }
            if (rows.isEmpty()) return null
            // The decade whose median is closest to the estimate, by its midpoint age.
            val mid = rows.minBy { kotlin.math.abs(it.value[1] - vo2) }.key.let { (it.first + it.last) / 2 }
            return (mid - FITNESS_AGE_SPREAD).coerceAtLeast(20)..(mid + FITNESS_AGE_SPREAD).coerceAtMost(80)
        }

        const val FITNESS_AGE_SPREAD = 10
    }
}
