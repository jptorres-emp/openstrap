package org.openstrap.app.ui.dashboard

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openstrap.app.data.Reports
import org.openstrap.app.data.Training
import org.openstrap.app.l10n.L10n
import org.openstrap.app.ui.glow.draw.DotFont
import java.time.LocalDate
import java.time.LocalTime

class TrainingCopyTest {
    private val end = LocalDate.of(2026, 10, 28)
    private val target = Reports.Target(Reports.DayKind.Usual, 110..205, personal = true, fromStrap = false, scored = true)
    private val crossfit = Training.SportLoad(130, days = 18, dayLoad = 112f)
    private val futsal = Training.SportLoad(18, days = 6, dayLoad = 150f)

    @After fun english() = L10n.speak(false)

    private fun assertDots(value: String) =
        value.forEach { c -> assertTrue("'$c' in \"$value\" has no dot glyph", DotFont.has(c) || c == ' ' || c == ' ') }

    private fun assertCopy(text: String) = assertTrue("\"$text\"", text.none { it in setOf('·', ';', '—', '!') })

    @Test fun `the day says what to do in your own sports`() {
        assertEquals("A usual day of CrossFit fits (≈112), or one of soccer (≈150).", TrainingCopy.plan(target, listOf(crossfit, futsal), null, finished = false))
        assertEquals("A usual day of CrossFit fits (≈112).", TrainingCopy.plan(target, listOf(crossfit), 30f, finished = false))
        val recover = target.copy(kind = Reports.DayKind.Recover, range = 0..55)
        assertEquals("A usual day of CrossFit (≈112) goes over it: keep it lighter.", TrainingCopy.plan(recover, listOf(crossfit), null, false))
        val push = target.copy(kind = Reports.DayKind.Push, range = 150..250)
        assertEquals("A usual day of CrossFit (≈112) stays under it: room to go harder.", TrainingCopy.plan(push, listOf(crossfit), null, false))
        // Without sports of your own, the kind of day in words; a day without a score is a usual one.
        assertEquals("Keep it light today: a walk or mobility.", TrainingCopy.plan(recover, emptyList(), null, false))
        assertEquals("Your usual training fits today.", TrainingCopy.plan(recover.copy(scored = false), emptyList(), null, false))
        // A day that's over says where it landed.
        assertEquals("Landed at 94, under its target.", TrainingCopy.plan(target, listOf(crossfit), 94.4f, finished = true))
        assertEquals("Landed at 150, within its target.", TrainingCopy.plan(target, listOf(crossfit), 150f, finished = true))
        assertEquals("No Load that day.", TrainingCopy.plan(target, listOf(crossfit), null, finished = true))
        // Past the target already today: the plan is over, whatever the sports.
        assertEquals("You're past today's target: keep the rest of the day light.", TrainingCopy.plan(target, listOf(crossfit), 400f, finished = false))
        assertEquals("soccer", TrainingCopy.sportInSentence(18))
        assertEquals("HIIT", TrainingCopy.sportInSentence(49))
    }

    @Test fun `the target reads in dots, with the Load so far under it`() {
        assertEquals("110-205", TrainingCopy.targetValue(target))
        assertDots(TrainingCopy.targetValue(target))
        assertEquals("Load target", TrainingCopy.targetLabel(null, finished = false))
        assertEquals("Load target, 35 so far", TrainingCopy.targetLabel(35.2f, finished = false))
        assertEquals("Load target, landed at 150", TrainingCopy.targetLabel(150f, finished = true))
        assertEquals("A day to recover", TrainingCopy.kindWord(target.copy(kind = Reports.DayKind.Recover)))
        assertEquals("A usual day", TrainingCopy.kindWord(target.copy(kind = Reports.DayKind.Push, scored = false)))
        assertNull(TrainingCopy.kindWord(null))
    }

    @Test fun `the figures under the target are the Recovery behind it and tonight's bedtime`() {
        assertEquals("82" to "Recovery", TrainingCopy.recoveryFigure(Reports.Day(end, recovery = 82, strapRecovery = 70)))
        assertEquals("70" to "Strap's estimate", TrainingCopy.recoveryFigure(Reports.Day(end, strapRecovery = 70)))
        assertNull(TrainingCopy.recoveryFigure(Reports.Day(end)))
        val plan = Reports.SleepPlan(505, 480, 25, 0, 0, 0.95f, LocalTime.of(7, 5), LocalTime.of(22, 12))
        assertEquals("22:12" to "Bed tonight", TrainingCopy.bedtimeFigure(plan))
        assertEquals("For 8 h 25 asleep, up at 07:05, 25 min more for short nights", TrainingCopy.planLine(plan))
        assertEquals("For 8 h asleep, up at 07:05", TrainingCopy.planLine(plan.copy(asleepMinutes = 480, shortfallMinutes = 0)))
        // Need 480, plus 25 and 15, less a 40 min nap.
        assertEquals(
            "For 8 h asleep, up at 07:05, 40 min more for short nights and a hard day, 40 min less for a nap",
            TrainingCopy.planLine(plan.copy(asleepMinutes = 480, loadMinutes = 15, napMinutes = 40)),
        )
        assertNull(TrainingCopy.bedtimeFigure(plan.copy(bedtime = null, wake = null)))
        assertNull(TrainingCopy.planLine(plan.copy(bedtime = null, wake = null)))
    }

    @Test fun `injury risk reads as a share of your usual, with what the zone means`() {
        assertEquals("112%", TrainingCopy.ratioValue(1.12f))
        assertDots(TrainingCopy.ratioValue(1.12f))
        Training.Zone.entries.forEach { z -> assertCopy(TrainingCopy.riskStatus(z)); assertCopy(TrainingCopy.zoneWord(z)) }
        // Steady and Building are Activity's words for the week's balance too.
        assertEquals(TabCopy.loadBalance(org.openstrap.app.data.Recovery.LoadTrend(100f, 110f, -10f)), TrainingCopy.zoneWord(Training.Zone.Steady))
        assertEquals(TabCopy.loadBalance(org.openstrap.app.data.Recovery.LoadTrend(100f, 130f, -30f)), TrainingCopy.zoneWord(Training.Zone.Building))
        assertTrue(TrainingCopy.describeRatios(listOf(Reports.Day(end, usualLoad = 100f, fatigue = 112f))).contains("112%"))
    }

    @Test fun `calories are the last 7 days against the 3 weeks before`() {
        assertEquals("2,650", TrainingCopy.kcal(2650.4f))
        assertDots(TrainingCopy.kcal(2650.4f))
        assertEquals("Last 7 days", TrainingCopy.caloriesStatus(2650f, null))
        assertEquals("Last 7 days, +120 vs 2,530 before", TrainingCopy.caloriesStatus(2650f, 2530f))
        assertEquals("Last 7 days, −80 vs 2,730 before", TrainingCopy.caloriesStatus(2650f, 2730f))
        assertEquals("Last 7 days, same as before", TrainingCopy.caloriesStatus(2650.2f, 2649.8f))
        // A week's bar goes by the day it ends, in the phone's own way of writing dates.
        assertTrue(TrainingCopy.weekLabel(end).startsWith("28"))
    }

    @Test fun `every info sheet says what it's for, when to look, how and its limits`() {
        listOf(TrainingCopy.DAY_ROWS, TrainingCopy.RISK_ROWS, TrainingCopy.CALORIES_ROWS).forEach { rows ->
            assertEquals(listOf(TrainingCopy.HOW_IT_HELPS, TrainingCopy.WHEN_TO_LOOK, TrainingCopy.HOW_WORKED_OUT, TrainingCopy.WHY_OFF), rows.map { it.first })
            rows.forEach { (title, text) -> assertCopy(title); assertCopy(text) }
        }
        listOf(TrainingCopy.dayInfo(), TrainingCopy.riskInfo(), TrainingCopy.caloriesInfo()).forEach { assertCopy(it.meaning) }
        assertEquals(Reports.DayKind.entries.size, TrainingCopy.DAY_SCALE.size)
        assertEquals(Training.Zone.entries.size, TrainingCopy.riskInfo().scale.size)
    }

    @Test fun `in Portuguese the tab reads in Portuguese`() {
        L10n.speak(true)
        assertEquals("Cabe um dia habitual de CrossFit (≈112), ou de futebol (≈150).", TrainingCopy.plan(target, listOf(crossfit, futsal), null, finished = false))
        assertEquals("Ficou em 94, abaixo do alvo.", TrainingCopy.plan(target, listOf(crossfit), 94.4f, finished = true))
        assertEquals("Carga alvo, 35 até agora", TrainingCopy.targetLabel(35.2f, finished = false))
        assertEquals("Dia para puxar", TrainingCopy.kindWord(target.copy(kind = Reports.DayKind.Push)))
        val plan = Reports.SleepPlan(505, 480, 25, 0, 0, 0.95f, LocalTime.of(7, 5), LocalTime.of(22, 12))
        assertEquals("Para dormir 8 h 25 e acordar às 07:05, mais 25 min por noites curtas", TrainingCopy.planLine(plan))
        assertEquals("Carga e risco de lesão", TrainingCopy.RISK_TITLE)
        assertEquals("Calorias gastas", TrainingCopy.CALORIES_TITLE)
        assertEquals("Últimos 7 dias, +120 vs ${TrainingCopy.kcal(2530f)} antes", TrainingCopy.caloriesStatus(2650f, 2530f))
        listOf(TrainingCopy.DAY_ROWS, TrainingCopy.RISK_ROWS, TrainingCopy.CALORIES_ROWS).flatten().forEach { (title, text) -> assertCopy(title); assertCopy(text) }
        Training.Zone.entries.forEach { z -> assertCopy(TrainingCopy.riskStatus(z)) }
    }
}
