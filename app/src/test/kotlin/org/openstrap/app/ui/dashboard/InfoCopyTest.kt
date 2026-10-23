package org.openstrap.app.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openstrap.app.data.History
import org.openstrap.app.data.Recovery
import org.openstrap.app.data.SleepRegularity

/** The info sheets (spec 6.2): one for every detail, in the copy rules of spec 7. */
class InfoCopyTest {
    private fun ready(capped: Boolean = false, fallbackHrv: Float? = null, weekNights: Int = 7) = Recovery.Readiness(
        score = 70, contributors = emptyList(), baselineNights = 30, strainAlert = false, alertSignals = emptyList(),
        capped = capped, fallbackHrv = fallbackHrv, hrvWeekNights = weekNights,
    )

    private val every: List<Info> =
        Metric.entries.map(InfoCopy::of) + Score.entries.map(InfoCopy::of) + NIGHT_VITALS.map(InfoCopy::ofNight) + InfoCopy.BREATHING_DISTURBANCES

    private val dynamic: List<String> =
        InfoCopy.recoveryNotes(ready(capped = true, fallbackHrv = 41f, weekNights = 4).copy(fallbackRestingHr = 55f), strapScore = 91) +
            InfoCopy.chargeNotes(null) +
            listOf(
                InfoCopy.normalLine(History.Normal(51f, 44f, 58f, 30), 30, { Math.round(it).toString() }, "ms", nightly = false),
                InfoCopy.normalLine(null, 3, { it.toString() }, "%", nightly = true),
                InfoCopy.HERE,
            ) +
            InfoCopy.regularityNotes(SleepRegularity.Result(82f, 9000, null)) + InfoCopy.regularityNotes(SleepRegularity.Result(null, 3360, 40f)) +
            InfoCopy.sleepNotes(480)

    @Test fun `no info sheet uses an interpunct, semicolon or em dash`() {
        val strings = every.flatMap { listOf(it.name, it.meaning, it.how, it.moves, it.off) + it.scale } + dynamic
        strings.forEach { s -> assertFalse("\"$s\"", s.any { it == '·' || it == ';' || it == '—' }) }
    }

    @Test fun `no exclamation marks, and the meaning is one bold line`() {
        every.forEach { info ->
            (listOf(info.meaning, info.how, info.moves, info.off)).forEach { assertFalse(it, '!' in it) }
            assertTrue(info.name, info.meaning.length <= 140)
            assertTrue(info.name, info.how.isNotBlank() && info.moves.isNotBlank() && info.off.isNotBlank())
        }
    }

    @Test fun `HRV is spelled out once, in its own sheet`() {
        assertTrue(InfoCopy.of(Metric.Hrv).meaning.startsWith("Heart rate variability"))
    }

    @Test fun `you are here, on each scale`() {
        assertNull(InfoCopy.normalBar(null))
        assertEquals(listOf(0, 1, 2), listOf(-1, 0, 1).map(InfoCopy::normalBar))
        assertEquals("Below your normal", InfoCopy.NORMAL_SCALE[InfoCopy.normalBar(-1)!!])
        assertEquals(listOf(0, 1, 2, 3), listOf(40, Recovery.FAIR_FROM, 70, 85).map(InfoCopy::recoveryBar))
        assertEquals(listOf(0, 1, 2, 3, 3), listOf(10, 25, 60, 75, 100).map(InfoCopy::chargeBar))
        assertEquals(
            "You are here. Your normal is 44 to 58 ms, from 30 days.",
            InfoCopy.normalLine(History.Normal(51f, 44f, 58f, 30), 30, { Math.round(it).toString() }, "ms", nightly = false),
        )
        assertEquals("Your normal shows after 7 nights with data. 3 so far.", InfoCopy.normalLine(null, 3, { it.toString() }, "%", nightly = true))
    }

    @Test fun `the sleep sheets carry the method moved off the cards`() {
        // Regularity's method, named once and spelled out, with what this week compares.
        val reg = InfoCopy.of(Score.Regularity)
        assertTrue(reg.how.contains("Sleep Regularity Index"))
        assertFalse(reg.how.contains("SRI"))
        assertEquals(listOf("Irregular", "Fair", "Regular", "Very regular"), reg.scale)
        assertEquals(3, InfoCopy.regularityBar(SleepRegularity.Band.VeryRegular))
        assertEquals(listOf("This week compares 150 h of wear."), InfoCopy.regularityNotes(SleepRegularity.Result(82f, 9000, null)))
        assertTrue(InfoCopy.regularityNotes(SleepRegularity.Result(null, 3360, 40f)).any { "56 h of the 120 h" in it })
        // The sleep score's guidance note, from the old What shaped the score panel.
        assertTrue(InfoCopy.of(Score.Sleep).how.contains("under 30 minutes awake"))
        assertEquals(listOf(0, 1, 2, 3), listOf(50, 60, 80, 90).map(InfoCopy::sleepBar))
        // Breathing disturbances stay an early estimate with no severity scale.
        assertTrue(InfoCopy.BREATHING_DISTURBANCES.meaning.contains("early estimate"))
        assertTrue(InfoCopy.BREATHING_DISTURBANCES.scale.isEmpty())
        assertEquals("Last night 2.4 an hour. Your usual is 2.1.", InfoCopy.breathingLine(Recovery.BreathingDisturbances(2.4f, 2.1f, 9), true))
        // A night vital's sheet says where the night's numbers come from.
        NIGHT_VITALS.forEach { assertTrue(it.name, InfoCopy.ofNight(it).how.contains("Opened from the night")) }
    }

    @Test fun `Recovery's sheet carries the strap's own score and what made today less sure`() {
        val notes = InfoCopy.recoveryNotes(ready(capped = true, fallbackHrv = 41f, weekNights = 4), strapScore = 91)
        assertTrue(notes.first().startsWith("The strap's own score today is 91."))
        assertTrue(notes.any { "capped" in it })
        assertTrue(notes.any { "41 ms" in it })
        assertTrue(notes.any { "4 of 7 nights" in it })
        assertEquals(emptyList<String>(), InfoCopy.recoveryNotes(ready(), strapScore = null))
    }
}
