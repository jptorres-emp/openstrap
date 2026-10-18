package org.openstrap.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.openstrap.app.data.settings.UserProfile

class Vo2maxTest {
    private val day = 86_400L
    private val today = 1000 * day

    private val profile = UserProfile(
        sex = UserProfile.Sex.Male, weightKg = 87.7f, heightCm = 172, activityLevel = 1,
        activityAnsweredAt = today - 10 * day, bodyMeasuredAt = today - 10 * day,
    )
    private val rhr = (0 until 7).associate { today - it * day to 72f }

    @Test fun `jurca equation`() {
        // Jurca 2005 NASA model: 2.77·male − 0.10·age − 0.17·BMI − 0.03·RHR + SR-PA + 18.07.
        val bmi = 87.7f / (1.72f * 1.72f)
        val expected = 2.77f - 0.10f * 45 - 0.17f * bmi - 0.03f * 72 + 0.32f + 18.07f
        assertEquals(expected, Vo2max.jurcaMets(true, 45, bmi, 72f, 1), 1e-4f)
        // Women: no sex term. The top activity level adds 3.03.
        assertEquals(expected - 2.77f - 0.32f + 3.03f, Vo2max.jurcaMets(false, 45, bmi, 72f, 4), 1e-4f)
    }

    @Test fun `estimate is a band of plus or minus one SEE, never a decimal`() {
        val (e, why) = Vo2max.estimate(profile, 45, rhr, today)
        assertNull(why)
        val ml = Vo2max.jurcaMets(true, 45, 87.7f / (1.72f * 1.72f), 72f, 1) * 3.5f
        assertEquals(Math.round(ml), e!!.value)
        assertEquals(Math.round(ml - 1.45f * 3.5f), e.low)
        assertEquals(Math.round(ml + 1.45f * 3.5f), e.high)
        assertNull(e.band) // FRIEND tables not transcribed yet
    }

    @Test fun `resting HR is the median of the last 7 nights`() {
        val nights = (0 until 10).associate { today - it * day to (60f + it) } // most recent 60, oldest in window 69
        val (e, _) = Vo2max.estimate(profile, 45, nights, today)
        assertEquals(63f, e!!.restingHr, 1e-5f) // last 7: 60..66
    }

    @Test fun `withholding rules`() {
        fun why(p: UserProfile = profile, age: Int? = 45, n: Map<Long, Float> = rhr) = Vo2max.estimate(p, age, n, today).second
        assertEquals(Vo2max.Withheld.Age, why(age = 19))
        assertEquals(Vo2max.Withheld.Age, why(age = 80))
        assertEquals(Vo2max.Withheld.Sex, why(profile.copy(sex = null)))
        assertEquals(Vo2max.Withheld.Body, why(profile.copy(bodyMeasuredAt = today - 91 * day)))
        assertEquals(Vo2max.Withheld.Body, why(profile.copy(heightCm = null)))
        assertEquals(Vo2max.Withheld.Activity, why(profile.copy(activityAnsweredAt = today - 181 * day)))
        assertEquals(Vo2max.Withheld.Activity, why(profile.copy(activityLevel = null)))
        assertEquals(Vo2max.Withheld.Medication, why(profile.copy(betaBlocker = true)))
        assertEquals(Vo2max.Withheld.Medication, why(profile.copy(pregnant = true)))
        assertEquals(Vo2max.Withheld.RestingHr, why(n = rhr.entries.take(4).associate { it.key to it.value }))
        // Nights older than 14 days don't count.
        assertEquals(Vo2max.Withheld.RestingHr, why(n = (20 until 30).associate { today - it * day to 60f }))
    }
}
