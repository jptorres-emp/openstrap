package org.openstrap.app.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.openstrap.app.R
import org.openstrap.app.data.ForkTarget
import org.openstrap.app.data.ForkTraining
import org.openstrap.app.testing.LocaleRule
import java.time.LocalDate

/** The words of the fork's Training view, in English: what fits today, your sports, the week's Load and the calories. */
class TrainingWordsTest {
    @get:Rule val localeRule = LocaleRule()

    private val target = ForkTarget.Target(ForkTarget.DayKind.Usual, 110..205, personal = true, fromStrap = false)
    private val football = ForkTraining.SportLoad(FOOTBALL, 9, 142.4f)
    private val cross = ForkTraining.SportLoad(CROSS, 4, 120f)
    private val hiit = ForkTraining.SportLoad(HIIT, 3, 230f)

    @Test fun `the sentence says what fits today in your sports, or in words without any`() {
        assertEquals(
            "A usual day of football fits (≈\u00A0142), or one of cross-training (≈\u00A0120).", TrainingWords.plan(target, listOf(football, cross)),
        )
        assertEquals("A usual day of football fits (≈\u00A0142).", TrainingWords.plan(target, listOf(football, hiit)))
        assertEquals("A usual day of HIIT (≈\u00A0230) goes over the target: keep it lighter.", TrainingWords.plan(target, listOf(hiit, football)))
        assertEquals(
            "A usual day of cross-training (≈\u00A0120) stays under the target: room to go harder.",
            TrainingWords.plan(target.copy(range = 130..220), listOf(cross)),
        )
        assertEquals("Keep it light today: a walk or mobility.", TrainingWords.plan(target.copy(kind = ForkTarget.DayKind.Recover), emptyList()))
        assertEquals("A day to go harder than usual.", TrainingWords.plan(target.copy(kind = ForkTarget.DayKind.Push), emptyList()))
    }

    @Test fun `each sport says its usual day against today's target, and the week's Load says its zone`() {
        assertEquals(ReportRow("Football", "142", "Fits today's target, 9 days in 6 weeks"), TrainingWords.sport(football, target))
        assertEquals(ReportRow("HIIT", "230", "Over today's target, 3 days in 6 weeks"), TrainingWords.sport(hiit, target))
        assertEquals("1 day in 6 weeks", TrainingWords.sport(football.copy(days = 1), null).line)
        assertEquals(ReportRow("Building", "127%", "Rising fast: keep the next days steady."), TrainingWords.balance(1.27f))
        assertEquals(ReportRow("Spike", "160%", "A spike, the zone tied most to injuries: ease off."), TrainingWords.balance(1.6f))
        assertEquals("Easing off", TrainingWords.balance(0.6f).title)
    }

    @Test fun `the calories say the last week against the weeks before`() {
        fun weeks(vararg kcal: Float?) = kcal.mapIndexed { i, k ->
            ForkTraining.EnergyWeek(END.minusDays(7L * (kcal.size - 1 - i)), k, if (k == null) 0 else 7)
        }
        assertEquals(
            "2,600 kcal a day in the last 7 days, 200 more than the weeks before.", TrainingWords.calories(weeks(2400f, 2400f, null, 2600f)),
        )
        assertEquals("2,300 kcal a day in the last 7 days, 100 less than the weeks before.", TrainingWords.calories(weeks(2400f, 2300f)))
        assertEquals(
            "2,430 kcal a day in the last 7 days, about the same as the weeks before.", TrainingWords.calories(weeks(2400f, 2430f)),
        )
        assertEquals("2,450 kcal a day in the last 7 days.", TrainingWords.calories(weeks(null, null, null, 2450f)))
        assertNull(TrainingWords.calories(weeks(2400f, null)))
        assertEquals(listOf("30 Oct", "6 Nov", "13 Nov", "20 Nov"), TrainingWords.calorieLabels(weeks(1f, 1f, 1f, 1f)))
        assertTrue(TrainingWords.withPercent(R.string.fork_rep_info_risk).contains("75 to 125% is steady"))
    }

    private companion object {
        const val FOOTBALL = 191
        const val CROSS = 130
        const val HIIT = 49
        val END: LocalDate = LocalDate.of(2026, 11, 20)
    }
}
