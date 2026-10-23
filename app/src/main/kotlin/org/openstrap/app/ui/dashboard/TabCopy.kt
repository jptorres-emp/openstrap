package org.openstrap.app.ui.dashboard

import org.openstrap.app.data.Recovery
import org.openstrap.app.data.SleepRegularity

/*
 * The words on the Recovery, Sleep and Activity tabs (spec 5 and 7). Like [TodayCopy], built only from numbers the
 * app already has: where one is missing the line says what it waits for, and nothing is guessed.
 */
object TabCopy {
    /* ---------------------------------------------- Recovery ---------------------------------------------- */

    /** "+3 vs yesterday", "−3 vs yesterday" or "Same as yesterday"; null without yesterday's score. */
    fun vsYesterday(score: Int, yesterday: Int?): String? = when {
        yesterday == null -> null
        score > yesterday -> "+${score - yesterday} vs yesterday"
        score < yesterday -> "−${yesterday - score} vs yesterday"
        else -> "Same as yesterday"
    }

    /**
     * The caveat while Recovery calibrates: nights toward the first score, then toward a settled one. Null once
     * there are enough.
     */
    fun learning(phase: Recovery.Phase, nights: Int): String? = when (phase) {
        Recovery.Phase.Withheld -> "Learning, $nights of ${Recovery.SCORED_NIGHTS} nights"
        Recovery.Phase.Learning -> "Learning, $nights of ${Recovery.SETTLED_NIGHTS} nights"
        Recovery.Phase.Scored, Recovery.Phase.Calibrated -> null
    }

    /** A contributor's name on a row. */
    fun factorName(f: Recovery.Factor): String = when (f) {
        Recovery.Factor.HrvBalance -> "HRV"
        Recovery.Factor.RestingHr -> "Resting heart rate"
        Recovery.Factor.Sleep -> "Last night's sleep"
        Recovery.Factor.SleepBalance -> "Sleep over recent nights"
        Recovery.Factor.Temperature -> "Skin temperature"
        Recovery.Factor.Breathing -> "Breathing rate"
        Recovery.Factor.PreviousDayLoad -> "Yesterday's Load"
        Recovery.Factor.BloodOxygen -> "Blood oxygen"
        Recovery.Factor.RecoveryIndex -> "When your heart settled"
    }

    /** [factorName] inside a sentence. */
    private fun factorPhrase(f: Recovery.Factor): String = when (f) {
        Recovery.Factor.HrvBalance -> "HRV"
        Recovery.Factor.RecoveryIndex -> "how late your heart settled"
        else -> factorName(f).replaceFirstChar { it.lowercase() }
    }

    /** Contributors this light or lighter never lead the sentence: too small a part of the score. */
    private const val SENTENCE_WEIGHT = 0.06f

    /** Below this a contributor holds the score back ([org.openstrap.app.ui.dashboard.Narrative.ratingFor]'s Good). */
    private const val HELD_BACK_BELOW = 70

    /**
     * The contributor holding Recovery back the most: the lowest scored of those that weigh enough, when it's under
     * [HELD_BACK_BELOW]. The same one the score's old explanation named.
     */
    fun heldBackBy(contributors: List<Recovery.Contributor>): Recovery.Factor? =
        contributors.filter { it.factor.weight >= SENTENCE_WEIGHT }.minByOrNull { it.score }?.takeIf { it.score < HELD_BACK_BELOW }?.factor

    /**
     * The Recovery hero's sentence: what the score means for the day and, short of well recovered, what held it
     * back most. "Recovered enough for your usual training. Mostly held back by HRV."
     */
    fun recovery(score: Int, alert: Boolean, contributors: List<Recovery.Contributor>): String {
        val meaning = TodayCopy.recoveryMeaning(score, alert)
        val by = heldBackBy(contributors)?.takeIf { score < 85 && !alert } ?: return meaning
        return "$meaning Mostly held back by ${factorPhrase(by)}."
    }

    /** Before a Recovery has been worked out for the day. */
    const val RECOVERY_WAITING = "Recovery shows once last night is in."

    /** A contributor's status word, by the score's own bands. */
    fun contributorWord(score: Int): String = when {
        score >= 85 -> "Helping"
        score >= 70 -> "Usual"
        score >= Recovery.FAIR_FROM -> "Holding back a little"
        else -> "Holding you back"
    }

    /** Whether a contributor is far enough from usual to take its colour. */
    fun contributorOff(score: Int): Boolean = score < 70 || score >= 85

    /** The [n] contributors that weigh most, heaviest first: the ones "What drove it" shows. */
    fun topDrivers(contributors: List<Recovery.Contributor>, n: Int = 3): List<Recovery.Contributor> =
        contributors.sortedByDescending { it.factor.weight }.take(n)

    /** A Charge amount with its sign: "+32", "-12", or "0" with none. Dot-matrix digits have no minus sign, only a dash. */
    fun chargeAmount(v: Float, sign: Int): String {
        val n = v.toInt()
        return if (n == 0) "0" else "${if (sign > 0) "+" else "-"}$n"
    }

    /** One row of "Charge today": what moved it, and whether it filled or drained it. */
    data class ChargeRow(val name: String, val value: String, val status: String)

    /** Where today's Charge came from and went, in the order it happened: sleep first, then the day. */
    fun chargeRows(recharged: Float, movement: Float, stress: Float, awake: Float): List<ChargeRow> = listOf(
        ChargeRow("Sleep and calm rest", chargeAmount(recharged, +1), "Recharged"),
        ChargeRow("Movement", chargeAmount(movement, -1), "Drained"),
        ChargeRow("Stress", chargeAmount(stress, -1), "Drained"),
        ChargeRow("Being awake", chargeAmount(awake, -1), "Drained"),
    )

    /** The Charge card's line under the number. */
    fun chargeLine(now: Int?, atWake: Int?, anchored: Boolean): String = when {
        !anchored -> "Charge starts after your first night. Sleep is what fills it."
        now == null -> "Charge shows once the strap has a few hours of heart rate."
        atWake == null -> "Now"
        now < atWake -> "Used ${atWake - now} since waking"
        now > atWake -> "Up ${now - atWake} since waking"
        else -> "Level since waking"
    }

    /* ----------------------------------------------- Sleep ----------------------------------------------- */

    /** Fewer other nights than this in the week and "longest this week" means little. */
    const val WEEK_COMPARE_NIGHTS = 3

    /**
     * The night's sentence: how long you slept, and whether it was the longest or shortest of the week ([others]
     * are the week's other nights, asleep seconds). "7 h 40, your longest this week."
     */
    fun night(asleepSeconds: Long, others: List<Long>): String {
        val d = TodayCopy.duration(asleepSeconds)
        return when {
            others.size < WEEK_COMPARE_NIGHTS -> "$d asleep."
            asleepSeconds > others.max() -> "$d, your longest this week."
            asleepSeconds < others.min() -> "$d, your shortest this week."
            else -> "$d asleep."
        }
    }

    /** The Sleep hero with no night to tell. */
    fun noNight(naps: Boolean, isToday: Boolean): String = when {
        naps -> "No night sleep recorded, only naps."
        isToday -> "No sleep recorded last night. Was the strap on?"
        else -> "No sleep recorded that night."
    }

    /** The sleep score's word, in the bands the strap's score is built for. */
    fun sleepScoreWord(score: Int): String = when {
        score >= 90 -> "Great"
        score >= 80 -> "Good"
        score >= 60 -> "Fair"
        else -> "Poor"
    }

    /** "Woke 3 times", "Woke once", "Slept through"; null when the strap didn't say. */
    fun woke(count: Int?): String? = when (count) {
        null -> null
        0 -> "Slept through"
        1 -> "Woke once"
        else -> "Woke $count times"
    }

    /** Sleep regularity's word, or what it still needs. */
    fun regularity(reg: SleepRegularity.Result?): String = when {
        reg == null -> "Needs about 5 days of wear"
        reg.sri != null -> reg.band!!.label
        reg.midpointSdMinutes != null -> if (reg.good == true) "Steady bedtimes this week" else "Bedtimes moved this week"
        else -> "Needs about 5 days of wear"
    }

    /** "Usually asleep by 23:10, up at 06:50." from minutes since midnight. */
    fun usualTimes(bedMinutes: Int, wakeMinutes: Int): String =
        "Usually asleep by ${minutes(bedMinutes)}, up at ${minutes(wakeMinutes)}."

    private fun minutes(m: Int) = Math.floorMod(m, 1440).let { "%02d:%02d".format(it / 60, it % 60) }

    /** Breathing disturbances against your usual, or how many nights it still needs. */
    fun breathing(b: Recovery.BreathingDisturbances): String = when (val usual = b.usual) {
        null -> "Learning your usual, ${b.nights.coerceAtMost(Recovery.SCORED_NIGHTS)} of ${Recovery.SCORED_NIGHTS} nights"
        else -> "Your usual is %.1f an hour".format(usual)
    }

    /* --------------------------------------------- Activity --------------------------------------------- */

    /** Under Load's number: your usual for a whole day, or that it's still being learned. */
    fun loadUsual(usual: Float?): String =
        if (usual == null || usual <= 0f) "Learning your usual" else "Your usual is ${Math.round(usual)}"

    /**
     * The week's balance from fatigue (the last 7 days) against fitness (the last 6 weeks): "Building" when the week
     * is well above your usual, "Easing off" when well below, "Steady" between, with the band Today uses for a usual
     * Load ([TodayCopy.LOAD_BAND]). Null before there's a fitness to compare with.
     */
    fun loadBalance(trend: Recovery.LoadTrend?): String? {
        val t = trend ?: return null
        if (t.ctl <= 0f) return null
        return when {
            t.atl > t.ctl * (1 + TodayCopy.LOAD_BAND) -> "Building"
            t.atl < t.ctl * (1 - TodayCopy.LOAD_BAND) -> "Easing off"
            else -> "Steady"
        }
    }

    /** Active minutes over the week against the 150 to aim for. */
    fun activeMinutes(week: Int, target: Int): String =
        if (week >= target) "This week, goal of $target reached" else "This week, goal $target"

    /** Heart points over the last 7 days against the 100 to aim for. */
    fun heartPoints(total: Int, target: Int): String =
        if (total >= target) "Last 7 days, at $target or more" else "Last 7 days, aim for $target"

    /** Energy under its number. */
    fun energy(partial: Boolean, isToday: Boolean): String = when {
        isToday -> "Estimated kcal so far today"
        partial -> "Estimated kcal, part of the day worn"
        else -> "Estimated kcal"
    }
}
