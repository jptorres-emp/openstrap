package org.openstrap.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openstrap.app.data.EnergyExpenditure.Minute
import org.openstrap.app.data.settings.UserProfile

class EnergyExpenditureTest {
    private val man = UserProfile(sex = UserProfile.Sex.Male, weightKg = 70f, heightCm = 175)
    private val age = 30
    /** 10·70 + 6.25·175 − 5·30 + 5 = 1648.75 kcal/day. */
    private val bmr = 1648.75f
    private val b = bmr / 1440f

    @Test fun `mifflin st jeor`() {
        assertEquals(bmr, EnergyExpenditure.bmr(70f, 175, 30, UserProfile.Sex.Male), 1e-3f)
        assertEquals(bmr - 166f, EnergyExpenditure.bmr(70f, 175, 30, UserProfile.Sex.Female), 1e-3f)
        assertEquals(bmr - 83f, EnergyExpenditure.bmr(70f, 175, 30, null), 1e-3f)
        assertEquals(2f * 3.5f * 70f / 200f, EnergyExpenditure.net(3f, 70f), 1e-5f)
        assertEquals(0f, EnergyExpenditure.net(0.9f, 70f), 1e-6f)
    }

    @Test fun `cadence mets`() {
        assertEquals(2f, EnergyExpenditure.cadenceMet(60, 30), 1e-5f)
        assertEquals(3f, EnergyExpenditure.cadenceMet(100, 30), 1e-5f)
        assertEquals(4.5f, EnergyExpenditure.cadenceMet(115, 30), 1e-5f)
        assertEquals(6f, EnergyExpenditure.cadenceMet(130, 30), 1e-5f)
        assertEquals(6f, EnergyExpenditure.cadenceMet(140, 30), 1e-5f)
        assertEquals(4f, EnergyExpenditure.cadenceMet(110, 70), 1e-5f)
        assertEquals(5f, EnergyExpenditure.cadenceMet(120, 70), 1e-5f)
        // Running: 160 spm × 0.45·1.75 m = 126 m/min → VO2 28.7 → 8.2 METs.
        assertEquals((3.5f + 0.2f * 160 * 0.45f * 1.75f) / 3.5f, EnergyExpenditure.runningMet(160, 175), 1e-3f)
    }

    @Test fun `each kind of minute`() {
        fun one(m: Minute, profile: UserProfile = man) = EnergyExpenditure.day(listOf(m), profile, age)!!.totalKcal
        assertEquals(0.95f * b, one(Minute(0, null, null, asleep = true)), 1e-4f)
        assertEquals(1.15f * b, one(Minute(0, 0, 0.1f, asleep = false)), 1e-4f)
        assertEquals(1.3f * b, one(Minute(0, 20, null, asleep = false)), 1e-4f)
        assertEquals(b + EnergyExpenditure.net(3f, 70f), one(Minute(0, 100, null, asleep = false)), 1e-4f)
        assertEquals(9f, one(Minute(0, 0, null, asleep = false, workoutKcal = 9f)), 1e-4f)
        // A still minute next to a stepping one is light activity.
        val pair = EnergyExpenditure.day(listOf(Minute(0, 0, null, false), Minute(60, 30, null, false)), man, age)!!
        assertEquals(2 * 1.3f * b, pair.totalKcal, 1e-4f)
        assertNull(EnergyExpenditure.day(listOf(Minute(0, 0, null, false)), man.copy(weightKg = null), age))
    }

    @Test fun `hr branch only for ten still minutes at 40 percent and up`() {
        val cycling = (0 until 12).map { Minute(it * 60L, 0, 0.5f, false) }
        val d = EnergyExpenditure.day(cycling, man, age, vo2max = 42f)!!
        val met = 1f + 0.5f * (42f / 3.5f - 1f) // 6.5, capped at 6
        assertEquals(12, d.hrAssistedMinutes)
        assertEquals(12 * (b + EnergyExpenditure.net(minOf(6f, met), 70f)), d.totalKcal, 1e-3f)
        val short = EnergyExpenditure.day(cycling.take(9), man, age, vo2max = 42f)!!
        assertEquals(0, short.hrAssistedMinutes)
        assertEquals(9 * 1.15f * b, short.totalKcal, 1e-3f)
    }

    @Test fun `partial days and pal`() {
        val half = EnergyExpenditure.day((0 until 600).map { Minute(it * 60L, 0, null, false) }, man, age)!!
        assertTrue(half.partial)
        assertNull(half.pal)
        // A full sedentary day with 8 h asleep: PAL ≈ (480·0.95 + 960·1.15)/1440 ≈ 1.08, below 1.2: flagged.
        val full = EnergyExpenditure.day((0 until 1440).map { Minute(it * 60L, 0, null, it < 480) }, man, age)!!
        assertFalse(full.partial)
        assertEquals((480 * 0.95f + 960 * 1.15f) / 1440, full.pal!!, 1e-3f)
        assertTrue(full.palImplausible)
    }
}
