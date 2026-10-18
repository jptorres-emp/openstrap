package org.openstrap.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openstrap.app.data.Recovery.Factor
import org.openstrap.app.data.Recovery.Minute
import org.openstrap.app.data.Recovery.Night
import org.openstrap.app.data.Recovery.State
import org.openstrap.app.data.db.SleepStage
import kotlin.random.Random

class RecoveryTest {
    private val day = 86_400L

    private fun night(i: Int, hrv: Float? = 50f, rhr: Float? = 55f, temp: Float? = 33.5f, breath: Float? = 14f, asleep: Int = 450) =
        Night(
            day = i * day, start = i * day - 7 * 3600, end = i * day + 30 * 60, asleepMinutes = asleep, awakeMinutes = 20,
            deepMinutes = 80, remMinutes = 95, hrv = hrv, restingHr = rhr, nadirTime = i * day - 4 * 3600,
            skinTemperature = temp, breathRate = breath, hrvSamples = 20, hrMinutes = 420,
        )

    /** 30 ordinary nights with realistic night-to-night noise. */
    private fun history(rnd: Random = Random(1)) = (1..30).map {
        night(it, hrv = 50f * (1 + 0.08f * (rnd.nextFloat() - 0.5f) * 2), rhr = 55f + rnd.nextInt(-2, 3), temp = 33.5f + 0.1f * (rnd.nextFloat() - 0.5f))
    }

    @Test
    fun zScoreAnchors() {
        assertEquals(80, Recovery.scoreFromZ(0f))
        assertEquals(93, Recovery.scoreFromZ(1f))
        assertEquals(55, Recovery.scoreFromZ(-1f))
        assertEquals(27, Recovery.scoreFromZ(-2f))
    }

    @Test
    fun ordinaryNightIsReady() {
        val r = Recovery.readiness(night(31), history())
        assertNotNull(r.score)
        assertTrue("score ${r.score}", r.score!! in 75..95)
        assertFalse(r.strainAlert)
        assertFalse(r.capped)
        assertFalse(r.calibrating)
        assertEquals(Recovery.Phase.Calibrated, r.phase)
        val factors = r.contributors.map { it.factor }.toSet()
        assertTrue(factors.containsAll(listOf(Factor.HrvBalance, Factor.RestingHr, Factor.Sleep, Factor.SleepBalance, Factor.Temperature, Factor.RecoveryIndex)))
    }

    @Test
    fun concordantSignalsRaiseAnAlertAndCapTheScore() {
        // Low HRV, resting HR up 9 bpm and skin temperature up together: several signals off baseline at once.
        val sick = night(31, hrv = 30f, rhr = 64f, temp = 34.4f)
        val r = Recovery.readiness(sick, history())
        assertTrue(r.strainAlert)
        assertTrue(r.alertSignals.size >= 2)
        assertTrue("score ${r.score}", r.score!! <= Recovery.ALERT_CAP)
    }

    @Test
    fun oneRedFlagCannotBeAveragedAway() {
        // Everything is great except HRV, which collapsed.
        val r = Recovery.readiness(night(31, hrv = 20f), history())
        val hrv = r.contributors.first { it.factor == Factor.HrvBalance }.score
        assertTrue(hrv < Recovery.RED_FLAG)
        assertTrue(r.score!! <= hrv + Recovery.RED_FLAG_HEADROOM)
        assertTrue(r.capped)
    }

    @Test
    fun withoutHrvOrRestingHrReadinessIsWithheld() {
        // Before 7 valid nights there's no HRV or resting HR contributor, so no score: sleep alone isn't readiness.
        val r = Recovery.readiness(night(7), (1..6).map { night(it) })
        assertTrue(r.calibrating)
        assertEquals(Recovery.Phase.Withheld, r.phase)
        assertTrue(r.contributors.none { it.factor == Factor.HrvBalance || it.factor == Factor.RestingHr })
        assertTrue(r.contributors.any { it.factor == Factor.Sleep })
        assertNull(r.score)
        assertTrue(r.withheld)
        assertTrue(r.showStrapEstimate)
        // At 7 valid nights it's scored, labelled learning.
        val learning = Recovery.readiness(night(8), (1..7).map { night(it) })
        assertEquals(Recovery.Phase.Learning, learning.phase)
        assertNotNull(learning.score)
        assertFalse(learning.withheld)
    }

    @Test
    fun phasesFollowValidNights() {
        assertEquals(Recovery.Phase.Withheld, Recovery.phase(6))
        assertEquals(Recovery.Phase.Learning, Recovery.phase(7))
        assertEquals(Recovery.Phase.Learning, Recovery.phase(13))
        assertEquals(Recovery.Phase.Scored, Recovery.phase(14))
        assertEquals(Recovery.Phase.Scored, Recovery.phase(27))
        assertEquals(Recovery.Phase.Calibrated, Recovery.phase(28))
    }

    @Test
    fun deadZoneIgnoresChangesUnderHalfAnSd() {
        assertEquals(0f, Recovery.deadZone(0.3f), 1e-6f)
        assertEquals(0f, Recovery.deadZone(-0.5f), 1e-6f)
        assertEquals(0.5f, Recovery.deadZone(1f), 1e-6f)
        assertEquals(-0.7f, Recovery.deadZone(-1.2f), 1e-6f)
        // A night a little off the usual scores as normal.
        val past = (1..30).map { night(it, hrv = 50f + (it % 5 - 2) * 2f) }
        val r = Recovery.readiness(night(31, hrv = 49f), past)
        assertEquals(80, r.contributors.first { it.factor == Factor.HrvBalance }.score)
    }

    @Test
    fun robustBaselineUsesMedianAndMadWithFloors() {
        val b = Recovery.baseline(listOf(10f, 11f, 12f, 13f, 14f, 15f, 16f), floor = 0.1f)!!
        assertEquals(13f, b.centre, 1e-5f)
        assertEquals(1.4826f * 2f, b.scale, 1e-4f) // MAD of 3,2,1,0,1,2,3 is 2
        // One wild night barely moves it.
        val withOutlier = Recovery.baseline(listOf(10f, 11f, 12f, 13f, 14f, 15f, 90f), floor = 0.1f)!!
        assertEquals(13f, withOutlier.centre, 1e-5f)
        assertEquals(1.4826f * 2f, withOutlier.scale, 1e-4f)
        // A perfectly steady baseline uses the floor.
        assertEquals(Recovery.RHR_SD_FLOOR, Recovery.baseline(List(10) { 55f }, Recovery.RHR_SD_FLOOR)!!.scale, 1e-6f)
        assertNull(Recovery.baseline(List(6) { 55f }, Recovery.RHR_SD_FLOOR))
    }

    @Test
    fun hrvBlendsTonightAndTheWeek() {
        // A steady baseline of 50 ms, then six low nights: tonight is back to usual but the 7-night mean still drags.
        val base = (1..24).map { night(it, hrv = 50f * (1 + 0.04f * ((it % 5) - 2))) }
        val low = (25..30).map { night(it, hrv = 38f) }
        val r = Recovery.readiness(night(31, hrv = 50f), base + low)
        val hrv = r.contributors.first { it.factor == Factor.HrvBalance }.score
        assertTrue("hrv $hrv", hrv < 70)
        assertEquals(Recovery.HrvRange.Below, r.hrvRange)
        assertEquals(7, r.hrvWeekNights)
        assertFalse(r.hrvWeekThin)
    }

    @Test
    fun invalidNightsStayOutOfTheBaselineAndTheStrapFillsIn() {
        // Too few RMSSD samples: our HRV and resting HR don't count tonight; the strap's values show as a fallback.
        val thin = night(31, hrv = 20f).copy(hrvSamples = 3, strapHrv = 48f, strapRestingHr = 56f)
        val r = Recovery.readiness(thin, history())
        assertNull(r.contributors.firstOrNull { it.factor == Factor.HrvBalance })
        assertEquals(48f, r.fallbackHrv)
        assertEquals(56f, r.fallbackRestingHr)
        assertTrue(r.withheld)
        // Invalid nights in history are ignored, even with wild values.
        val junk = (1..30).map { night(it, hrv = 500f, rhr = 99f).copy(hrMinutes = 30) }
        val r2 = Recovery.readiness(night(31), junk)
        assertEquals(Recovery.Phase.Withheld, r2.phase)
        assertEquals(0, r2.baselineNights)
    }

    @Test
    fun ownAndStrapDisagreeingRaisesAQualityFlag() {
        val r = Recovery.readiness(night(31, hrv = 50f, rhr = 55f).copy(strapHrv = 62f, strapRestingHr = 59f), history())
        assertEquals(2, r.qualityFlags.size)
        val close = Recovery.readiness(night(31, hrv = 50f, rhr = 55f).copy(strapHrv = 54f, strapRestingHr = 57f), history())
        assertTrue(close.qualityFlags.isEmpty())
    }

    @Test
    fun noSleepNoScore() {
        assertNull(Recovery.readiness(null, history()).score)
    }

    @Test
    fun sleepDebtLowersSleepBalance() {
        val short = (1..13).map { night(it, asleep = 330) }
        val rested = (1..13).map { night(it, asleep = 480) }
        val debt = Recovery.readiness(night(14, asleep = 330), short).contributors.first { it.factor == Factor.SleepBalance }.score
        val none = Recovery.readiness(night(14, asleep = 480), rested).contributors.first { it.factor == Factor.SleepBalance }.score
        assertEquals(100, none)
        assertTrue("debt $debt", debt in 30..45) // 2.5 h short a night
    }

    @Test
    fun lateHeartRateNadirLowersRecoveryIndex() {
        val n = night(31)
        val early = Recovery.readiness(n.copy(nadirTime = n.start + 2 * 3600), history()).contributors.first { it.factor == Factor.RecoveryIndex }
        val late = Recovery.readiness(n.copy(nadirTime = n.end - 30 * 60), history()).contributors.first { it.factor == Factor.RecoveryIndex }
        assertEquals(100, early.score)
        assertTrue(late.score < 60)
    }

    @Test
    fun trimpMatchesBanisterWorkedExample() {
        // HRmax 200, resting 40, 30 min at 130 bpm: Banister TRIMP ≈ 32.
        val x = Recovery.hrr(130, 40f, 200f)
        assertEquals(32f, 30 * Recovery.trimpMinute(x), 1.5f)
    }

    @Test
    fun ewmaOfConstantLoadIsBalanced() {
        val (a, c) = Recovery.ewmaLoads(List(28) { 60f })
        assertEquals(60f, a, 0.01f)
        assertEquals(60f, c, 0.01f)
    }

    @Test
    fun loadSpikeIsPenalised() {
        val loads = List(27) { 40f } + 200f
        val r = Recovery.readiness(night(31), history(), loads)
        assertTrue(r.contributors.first { it.factor == Factor.PreviousDayLoad }.score < 100)
        // ACWR is descriptive now, on the Load screen, never a Readiness contributor.
        assertTrue(Recovery.acwr(loads)!! > 1.3f)
        assertNull(Recovery.acwr(loads.takeLast(13)))
    }

    @Test
    fun weightsMatchTheEvidenceReview() {
        val w = Factor.entries.associate { it to it.weight }
        assertEquals(0.52f, w.getValue(Factor.HrvBalance) + w.getValue(Factor.RestingHr), 1e-5f)
        assertEquals(0.28f, w.getValue(Factor.Sleep) + w.getValue(Factor.SleepBalance), 1e-5f)
        assertEquals(0.13f, w.getValue(Factor.Temperature) + w.getValue(Factor.Breathing) + w.getValue(Factor.BloodOxygen), 1e-5f)
        assertEquals(1f, w.values.sum(), 1e-5f)
    }

    // --- Charge ------------------------------------------------------------------------------------

    private val params = Recovery.ChargeParams(restingHr = 58f, maxHr = 188f, nightRestingHr = 52f)

    private fun run(n: Int, f: (Int) -> Minute) = List(n) { f(it) }

    @Test
    fun aGoodNightRechargesFromLowToHigh() {
        val stages = listOf(SleepStage.LIGHT, SleepStage.DEEP, SleepStage.LIGHT, SleepStage.REM)
        val night = run(450) { Minute(it * 60L, 54, 0, null, stages[(it / 25) % 4]) }
        val r = Recovery.charge(night, params, 0L..450 * 60L, start = 30f)
        assertTrue("charge ${r.latest}", r.latest!! in 85f..96f)
    }

    @Test
    fun restlessNightRechargesLess() {
        val good = run(450) { Minute(it * 60L, 52, 0, null, SleepStage.LIGHT) }
        val poor = run(450) { Minute(it * 60L, 68, 0, null, SleepStage.LIGHT) }
        val a = Recovery.charge(good, params, 0L..450 * 60L, start = 30f).latest!!
        val b = Recovery.charge(poor, params.copy(hrvScore = 30), 0L..450 * 60L, start = 30f).latest!!
        assertTrue("good $a poor $b", a - b > 8)
    }

    @Test
    fun quietWakingDrainsSlowly() {
        val day = run(600) { Minute(it * 60L, 64, 0, 30, null) }
        val r = Recovery.charge(day, params, 0L..600 * 60L, start = 80f, requireAnchor = false)
        assertEquals(80f - 600 * Recovery.BASE_DRAIN, r.latest!!, 0.5f)
        assertEquals(0f, r.drainedExercise, 0.01f)
    }

    @Test
    fun anHourOfModerateExerciseDrainsAboutAQuarter() {
        val hr = (58 + 0.6f * (188 - 58)).toInt() // 60% of reserve
        val r = Recovery.charge(run(60) { Minute(it * 60L, hr, 150, 70, null) }, params, 0L..3600L, start = 80f, requireAnchor = false)
        val drop = 80f - r.latest!!
        assertTrue("drop $drop", drop in 20f..32f)
        assertTrue(r.drainedExercise > r.drainedLiving)
        assertEquals(0f, r.drainedStress, 0.01f)
    }

    @Test
    fun elevatedHeartRateAfterExerciseIsNotStress() {
        val workout = run(40) { Minute(it * 60L, 150, 160, 80, null) }
        // Cool-down: HR easing down, strap stress still high from the workout.
        val after = run(20) { Minute((40 + it) * 60L, 95 - it, 0, 75, null) }
        val r = Recovery.charge(workout + after, params, 0L..3600L, start = 90f, requireAnchor = false)
        assertTrue(r.points.filter { it.t >= 40 * 60L }.none { it.state == State.Stress })
        assertEquals(0f, r.drainedStress, 0.01f)
    }

    @Test
    fun sustainedStressDrainsFasterThanCalm() {
        val calm = Recovery.charge(run(240) { Minute(it * 60L, 66, 0, 20, null) }, params, 0L..14_400L, start = 70f, requireAnchor = false).latest!!
        val tense = Recovery.charge(run(240) { Minute(it * 60L, 74, 0, 80, null) }, params, 0L..14_400L, start = 70f, requireAnchor = false)
        assertTrue(calm > tense.latest!! + 5)
        assertTrue(tense.drainedStress > 0)
    }

    private val dayOf = { t: Long -> t / 86_400 }

    @Test
    fun stressThresholdIsTenAboveCalmLevel() {
        // Four days of calm readings around 54 (seen on hardware while seated): stress counts from 64.
        val calm = (0 until 4).flatMap { d -> (0 until 30).map { i -> d * 86_400L + i * 300 to (50 + i % 9) } }
        val t = Recovery.stressThreshold(calm, dayOf)
        assertEquals(54, t.calmLevel)
        assertEquals(64, t.value)
        assertEquals(4, t.days)
    }

    @Test
    fun stressThresholdStaysInZeppsBandsAndNeedsThreeDays() {
        val low = (0 until 3).flatMap { d -> (0 until 30).map { i -> d * 86_400L + i * 300 to 20 } }
        assertEquals(Recovery.STRESS_FROM_MIN, Recovery.stressThreshold(low, dayOf).value)
        val high = low.map { it.first to 75 }
        assertEquals(Recovery.STRESS_FROM_MAX, Recovery.stressThreshold(high, dayOf).value)
        val twoDays = low.filter { it.first < 2 * 86_400L }.map { it.first to 54 }
        val learning = Recovery.stressThreshold(twoDays, dayOf)
        assertEquals(Recovery.STRESS_FROM, learning.value)
        assertNull(learning.calmLevel)
        assertEquals(2, learning.days)
    }

    @Test
    fun chargeUsesThePersonalThreshold() {
        val day62 = run(240) { Minute(it * 60L, 70, 0, 62, null) }
        val fixed = Recovery.charge(day62, params, 0L..14_400L, start = 70f, requireAnchor = false)
        val personal = Recovery.charge(day62, params.copy(stressFrom = 64), 0L..14_400L, start = 70f, requireAnchor = false)
        assertTrue(fixed.points.any { it.state == State.Stress })
        assertTrue(personal.points.none { it.state == State.Stress })
    }

    @Test
    fun zeppNormalStressIsNotStress() {
        // Readings of 40-59 are Zepp's "normal" band: a seated day there drains like rest.
        val r = Recovery.charge(run(240) { Minute(it * 60L, 70, 0, 55, null) }, params, 0L..14_400L, start = 70f, requireAnchor = false)
        assertTrue(r.points.none { it.state == State.Stress })
        assertEquals(0f, r.drainedStress, 0.01f)
    }

    @Test
    fun chargeStaysInRange() {
        val r = Recovery.charge(run(2000) { Minute(it * 60L, 175, 170, 90, null) }, params, 0L..120_000L, start = 50f, requireAnchor = false)
        assertTrue(r.points.all { it.value in 0f..100f })
        assertEquals(0f, r.lowest!!, 0.01f)
    }

    @Test
    fun minutesFillsStagesAndHoldsStress() {
        val m = Recovery.minutes(
            from = 0, to = 600,
            hr = listOf(60L to 70), steps = mapOf(120L to 30),
            stress = listOf(0L to 50),
            stages = listOf(Triple(240L, 360L, SleepStage.DEEP)),
            stressHoldSeconds = 180,
        )
        assertEquals(10, m.size)
        assertEquals(70, m[1].hr)
        assertEquals(30, m[2].steps)
        assertEquals(50, m[3].stress)
        assertNull(m[4].stress)
        assertEquals(SleepStage.DEEP, m[4].sleepStage)
        assertEquals(SleepStage.DEEP, m[5].sleepStage)
        assertNull(m[6].sleepStage)
    }

    @Test
    fun lowestAverageFindsTheNadir() {
        val series = (0 until 480).map { it * 60L to (if (it in 100..140) 48 else 60) }
        val (t, v) = Recovery.lowestAverage(series, 30)!!
        assertEquals(48f, v, 0.01f)
        assertTrue(t in 100 * 60L..140 * 60L)
    }

    @Test
    fun maxHeartRateUsesAgeThenStrapThenObserved() {
        assertEquals(187f, Recovery.maxHeartRate(30), 0.01f) // Tanaka: 208 - 0.7 * 30
        assertEquals(192f, Recovery.maxHeartRate(null, strapMax = 192), 0.01f)
        assertEquals(190f, Recovery.maxHeartRate(null), 0.01f)
        assertEquals(195f, Recovery.maxHeartRate(30, observedPeak = 195), 0.01f)
        // A single artefact can't move it more than 15 bpm.
        assertEquals(202f, Recovery.maxHeartRate(30, observedPeak = 240), 0.01f)
    }

    @Test
    fun femaleTrimpCurveUsesItsOwnConstants() {
        // 0.86·e^(1.67x) sits above 0.64·e^(1.92x) everywhere below 118% of reserve.
        assertEquals(0.4f * 0.86f * kotlin.math.exp(1.67f * 0.4f), Recovery.trimpMinute(0.4f, female = true), 1e-4f)
        assertTrue(Recovery.trimpMinute(0.4f, female = true) > Recovery.trimpMinute(0.4f))
        assertTrue(Recovery.trimpMinute(0.95f, female = true) > Recovery.trimpMinute(0.95f))
    }

    @Test
    fun noSleepMeansNoCharge() {
        // A new user's first evening: plenty of heart rate, no night yet. A number here would be a guess.
        val r = Recovery.charge(run(300) { Minute(it * 60L, 70, 0, 40, null) }, params, 0L..18_000L)
        assertFalse(r.anchored)
        assertNull(r.latest)
        assertTrue(r.points.isEmpty())
    }

    @Test
    fun chargeHoldsWhileTheStrapIsOff() {
        val night = run(60) { Minute(it * 60L, 52, 0, null, SleepStage.DEEP) }
        val off = run(240) { Minute((60 + it) * 60L, null, 0, null, null) }
        val r = Recovery.charge(night + off, params, 0L..18_000L, start = 60f, sampleSeconds = 60)
        val atWake = r.points.last { it.t < 60 * 60L }.value
        assertTrue(r.anchored)
        assertEquals(atWake, r.points.last().value, 0.5f)
    }

    @Test
    fun overnightOxygenDropLowersReadinessAndAlerts() {
        val past = history().map { it.copy(spo2 = 96f) }
        val normal = Recovery.readiness(night(31).copy(spo2 = 95.8f), past)
        assertEquals(100, normal.contributors.first { it.factor == Factor.BloodOxygen }.score)

        val low = Recovery.readiness(night(31).copy(spo2 = 93f), past)
        val c = low.contributors.first { it.factor == Factor.BloodOxygen }
        assertEquals(50, c.score) // 3 % below usual: 2 % past the dead band, 25 points each
        assertTrue(low.alertSignals.any { "blood oxygen" in it })
        assertTrue(low.score!! < normal.score!!)
    }

    @Test
    fun higherOxygenThanUsualIsNotABonus() {
        val past = history().map { it.copy(spo2 = 95f) }
        val r = Recovery.readiness(night(31).copy(spo2 = 98f), past)
        assertEquals(100, r.contributors.first { it.factor == Factor.BloodOxygen }.score)
        assertFalse(r.alertSignals.any { "blood oxygen" in it })
    }

    @Test
    fun oxygenNeedsABaseline() {
        val r = Recovery.readiness(night(31).copy(spo2 = 90f), history())
        assertNull(r.contributors.firstOrNull { it.factor == Factor.BloodOxygen })
    }

    @Test
    fun breathingDisturbancesAreTheirOwnCardAgainstYourHistory() {
        val past = history().map { it.copy(odi = 4f) }
        val b = Recovery.breathingDisturbances(night(31).copy(odi = 40f), past)!!
        assertEquals(40f, b.perHour, 1e-5f)
        assertEquals(4f, b.usual!!, 1e-5f)
        assertNull(Recovery.breathingDisturbances(night(31).copy(odi = 40f), past.take(6))!!.usual)
        assertNull(Recovery.breathingDisturbances(night(31), past))
    }

    @Test
    fun bodySignalsPushFollowsTheWellnessCopy() {
        assertEquals(
            "Several of your overnight signals (resting heart rate, skin temperature) are outside your usual range for the second night. " +
                "This can happen with heavy training, alcohol, travel, poor sleep, or when your body is fighting something off. " +
                "Consider an easier day. If you feel unwell, checking in with a healthcare professional may be helpful. " +
                "This is a wellness estimate, not a medical assessment.",
            BodySignalsCopy.pushText(listOf("resting heart rate", "skin temperature"), nights = 2),
        )
        val banned = listOf("illness", "infection", "fever", "covid", "abnormal", "diagnos")
        val texts = listOf(BodySignalsCopy.TITLE, BodySignalsCopy.SHORT, BodySignalsCopy.pushText(listOf("skin temperature"), 1))
        texts.forEach { t -> banned.forEach { assertFalse("$it in $t", it in t.lowercase()) } }
    }

    @Test
    fun breathingDisturbancesDontMoveReadiness() {
        // A stable trait, so inside Readiness it would only act as a fixed offset: it's left out.
        val with = Recovery.readiness(night(31).copy(odi = 40f), history())
        val without = Recovery.readiness(night(31), history())
        assertEquals(without.score, with.score)
        assertFalse(with.capped)
    }
}
