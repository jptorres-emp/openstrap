package org.openstrap.app.ui.dashboard

import org.openstrap.app.data.History
import org.openstrap.app.data.Recovery

/*
 * What every detail's info sheet says (spec 6.2): a bold one-line meaning, the scale its value is read on, and three
 * folded rows. Every method name, formula and caveat that used to sit on a card or a detail lives here, one tap from
 * the number, so the cards can lead with what it means.
 */

/** An info sheet's words: the [meaning] in bold, the [scale] from low to high, and the three folded rows. */
data class Info(
    val name: String,
    val meaning: String,
    val scale: List<String>,
    val how: String,
    val moves: String,
    val off: String,
)

object InfoCopy {
    /** The scale of every value read against your normal, low to high. */
    val NORMAL_SCALE = listOf("Below your normal", "Usual", "Above your normal")

    /** Recovery's bands, low to high, in the words beside the score ([TodayCopy.recoveryWord]). */
    val RECOVERY_SCALE = listOf("Take it easy", "Moderate", "Ready", "Ready for more")

    /** Charge's bands, low to high. */
    val CHARGE_SCALE = listOf("Running low", "Some left", "Good", "Full")

    /** Which bar of [NORMAL_SCALE] a status against your normal is in; null without a normal. */
    fun normalBar(status: Int?): Int? = status?.let { it.coerceIn(-1, 1) + 1 }

    /** Which bar of [RECOVERY_SCALE] a score is in. */
    fun recoveryBar(score: Int): Int = when {
        score >= 85 -> 3
        score >= 70 -> 2
        score >= Recovery.FAIR_FROM -> 1
        else -> 0
    }

    /** Which bar of [CHARGE_SCALE] a Charge is in, by quarters. */
    fun chargeBar(charge: Int): Int = (charge / 25).coerceIn(0, 3)

    /**
     * The line under the scale: "You are here. Your normal is 44 to 58 ms, from 30 days." Before there's a normal, how
     * far along it is.
     */
    fun normalLine(normal: History.Normal?, days: Int, format: (Float) -> String, unit: String, nightly: Boolean): String {
        val word = if (nightly) "nights" else "days"
        if (normal == null) return "Your normal shows after ${History.MIN_NORMAL_DAYS} $word with data. ${days.coerceAtMost(History.MIN_NORMAL_DAYS)} so far."
        val u = if (unit.isEmpty()) "" else if (unit.startsWith("%") || unit.startsWith("°")) unit else " $unit"
        return "You are here. Your normal is ${format(normal.low)} to ${format(normal.high)}$u, from ${normal.days} $word."
    }

    /** The line under Recovery's and Charge's scales. */
    const val HERE = "You are here."

    fun of(metric: Metric): Info = when (metric) {
        Metric.HeartRate -> Info(
            "Heart rate",
            "How fast your heart beats, read by the strap through the day and night.",
            NORMAL_SCALE,
            how = "The strap reads your pulse with light through the skin, about once a minute, and more often in a " +
                "workout or while Live is on. The band behind the day's line is your resting range: your usual resting " +
                "heart rate, give or take a usual spread, over the last 30 days.",
            moves = "Movement, stress, caffeine and heat raise it. Rest and sleep bring it down.",
            off = "A loose strap, cold hands or busy arm movement can make readings jump. Live readings come more often, " +
                "so the line looks busier while it's on.",
        )
        Metric.RestingHr -> Info(
            "Resting heart rate",
            "Your heart rate at its calmest. Lower usually means a fitter, well rested heart.",
            NORMAL_SCALE,
            how = "The strap works it out each day from your calmest stretch, usually in sleep. Your normal is the middle " +
                "of your last 30 days, give or take a usual spread, once 7 of them have a value.",
            moves = "It rises after hard training, late meals, alcohol, poor sleep or when your body is fighting " +
                "something off, and falls as fitness builds.",
            off = "A night with the strap loose or off gives it less to go on. One high day says less than a few in a row.",
        )
        Metric.Hrv -> Info(
            "HRV",
            "Heart rate variability, the small changes in time between heartbeats. Higher than your usual means your body has recovered.",
            NORMAL_SCALE,
            how = "The strap measures the time between beats while you're still, mostly in sleep, and the day's value is " +
                "the average of its readings. It's read against your own normal: the middle of your last 30 days, give " +
                "or take a usual spread.",
            moves = "Good sleep and rest raise it. Stress, hard training and alcohol lower it. Compare it with your own " +
                "normal, not other people's.",
            off = "Moving during a reading, a loose strap or a short night leave fewer clean readings. HRV swings a lot " +
                "from day to day, so a few days in a row say more than one.",
        )
        Metric.Spo2 -> Info(
            "Blood oxygen",
            "How much of your blood's capacity is carrying oxygen. Most people sit at 95 to 100%.",
            NORMAL_SCALE,
            how = "The strap shines red and infrared light through the skin and compares how much of each comes back. The " +
                "day's value is the average of its readings.",
            moves = "Altitude and pauses in breathing during sleep can lower it.",
            off = "A loose strap, cold skin or moving during a reading can give low values. It's a wellness reading, not a " +
                "medical one.",
        )
        Metric.Stress -> Info(
            "Stress",
            "The strap's stress level from 0 to 100. Higher means your body is more on alert.",
            NORMAL_SCALE,
            how = "The strap works it out from your heart rate and its variability while you're still. Readings during " +
                "and just after a workout are left out, since exercise isn't stress.",
            moves = "Pressure, caffeine and too little sleep raise it. Calm breathing, rest and time outdoors bring it down.",
            off = "Excitement, heat and an illness look like stress to the strap. Small movements while sitting can push it up.",
        )
        Metric.SkinTemperature -> Info(
            "Skin temperature",
            "The temperature of your skin under the strap. What matters is the change from your normal, not the level.",
            NORMAL_SCALE,
            how = "The strap measures it against your skin. The level depends on fit and the room, so OpenStrap shows it " +
                "as a change from your normal of the last 30 days.",
            moves = "It moves with alcohol, a warm room, hard training, the menstrual cycle, or when your body is fighting " +
                "something off.",
            off = "A warm bed, a heavy duvet or the strap sitting loose all change it without your body changing.",
        )
        Metric.Breathing -> Info(
            "Breathing rate",
            "How many breaths you take a minute while asleep. It stays very steady for most people.",
            NORMAL_SCALE,
            how = "The strap measures it during sleep, so there's one value a night, read against your normal of the last " +
                "30 days.",
            moves = "It can rise above your normal after a hard training day, in a warm, stuffy room, or when your body is " +
                "fighting something off.",
            off = "A restless night, or the strap off for part of it, leaves fewer readings to go on.",
        )
        Metric.Steps -> Info(
            "Steps",
            "Steps counted by the strap through the day.",
            NORMAL_SCALE,
            how = "The strap counts them from your arm's movement and keeps them minute by minute. A day's steps are the " +
                "total of its minutes.",
            moves = "Short walks spread across the day add up. Most people aim for 7,000 to 10,000.",
            off = "Pushing a pram or a trolley, or holding a rail, hides steps. Waving your arms about can add a few.",
        )
    }

    fun of(score: Score): Info = when (score) {
        Score.Recovery -> Info(
            "Recovery",
            "How ready your body is for effort today, from 0 to 100, worked out each morning from your night.",
            RECOVERY_SCALE,
            how = "Each morning OpenStrap compares your night with what's normal for you, not with other people: HRV, " +
                "resting heart rate, skin temperature, breathing rate and blood oxygen, last night's sleep and the past " +
                "few nights, and how hard yesterday was. About half the score comes from HRV and resting heart rate, " +
                "about a quarter from sleep. One signal far from your normal caps it, so good sleep can't fully make up " +
                "for it. Everything is worked out on this phone from your strap's data.",
            moves = "Good sleep, easy days and nights without alcohol lift it. Hard training, short nights, late meals, " +
                "stress and illness hold it down.",
            off = "It needs ${Recovery.SCORED_NIGHTS} good nights to start and settles over ${Recovery.SETTLED_NIGHTS}. A " +
                "night with the strap loose or off, or too few clean HRV readings, gives it less to go on, and the " +
                "strap's own readings may stand in.",
        )
        Score.Charge -> Info(
            "Charge",
            "Your energy right now, from 0 to 100. Sleep fills it and the day uses it.",
            CHARGE_SCALE,
            how = "Think of it as a battery that runs all day. Sleep fills it, deep sleep most of all, and a calm, steady " +
                "night fills it faster. Being awake uses a little every hour. Exercise and stress use it faster, while " +
                "quiet, relaxed moments slow the drain. It's worked out on this phone from your heart rate, minute by " +
                "minute, as a guide to plan your day, not medical advice.",
            moves = "A long, calm night fills it more. Workouts, a stressful day and a late night use it up.",
            off = "Charge starts after your first night. With the strap off overnight there's no sleep to fill it from, " +
                "so the day starts from your usual Charge at waking, an estimate.",
        )
    }

    /**
     * What's particular about today's Recovery, for its info sheet: the strap's own score beside ours, and anything that
     * made tonight's reading less sure. Each is a sentence; none when there's nothing to say.
     */
    fun recoveryNotes(ready: Recovery.Readiness?, strapScore: Int?): List<String> = buildList {
        if (strapScore != null) add(
            "The strap's own score today is $strapScore. It doesn't count sleep length, so after a short night it reads " +
                "higher than OpenStrap's. It's kept for comparison and never enters OpenStrap's score.",
        )
        ready ?: return@buildList
        if (ready.capped) add("Today one body signal is far from your normal, so the score is capped.")
        ready.fallbackHrv?.let { add("Last night's own HRV wasn't clean enough, so the strap's ${it.toInt()} ms stands in.") }
        ready.fallbackRestingHr?.let { add("Last night's own resting heart rate wasn't clean enough, so the strap's ${it.toInt()} bpm stands in.") }
        if (ready.hrvWeekThin) add("The week's HRV rests on ${ready.hrvWeekNights} of 7 nights, so it's less sure than usual.")
    }

    /** What's particular about today's Charge: an estimated start after a night with the strap off. */
    fun chargeNotes(c: Recovery.ChargeDay?): List<String> = listOfNotNull(
        "The strap was off overnight, so today started from an estimate.".takeIf { c?.strapOffOvernight == true },
    )
}
