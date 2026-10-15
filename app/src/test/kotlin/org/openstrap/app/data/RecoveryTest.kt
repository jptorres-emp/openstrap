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
            skinTemperature = temp, breathRate = breath,
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
        val factors = r.contributors.map { it.factor }.toSet()
        assertTrue(factors.containsAll(listOf(Factor.HrvBalance, Factor.RestingHr, Factor.Sleep, Factor.SleepBalance, Factor.Temperature, Factor.RecoveryIndex)))
    }

    @Test
    fun concordantSignalsRaiseAnAlertAndCapTheScore() {
        // Low HRV, resting HR up 9 bpm and a fever-like skin temperature together: classic early illness.
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
    fun calibratingWithoutBaselineStillScoresSleep() {
        val r = Recovery.readiness(night(3), listOf(night(1), night(2)))
        assertTrue(r.calibrating)
        assertTrue(r.contributors.none { it.factor == Factor.HrvBalance || it.factor == Factor.RestingHr })
        assertNotNull(r.score)
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
        assertTrue(r.contributors.first { it.factor == Factor.LoadBalance }.score < 100)
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
}
