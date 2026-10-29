package org.openstrap.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JournalTest {
    @Test fun `a day counts once it's answered, even with nothing ticked`() {
        assertNull(JournalHabit.ofDay(emptyList()))
        assertEquals(emptySet<JournalHabit>(), JournalHabit.ofDay(listOf(JournalHabit.ANSWERED)))
        assertEquals(setOf(JournalHabit.Alcohol, JournalHabit.Stress), JournalHabit.ofDay(listOf(JournalHabit.ANSWERED, "alcohol", "stress")))
        // A tag from before the journal answers the day; one it doesn't know doesn't.
        assertEquals(setOf(JournalHabit.Travel), JournalHabit.ofDay(listOf("travel", "vaccination")))
        assertNull(JournalHabit.ofDay(listOf("vaccination")))
    }

    @Test fun `saving keeps the answer and the ticked habits`() {
        val tags = JournalHabit.tags(setOf(JournalHabit.LateMeal))
        assertEquals(listOf(JournalHabit.ANSWERED, "late_meal"), tags)
        assertEquals(setOf(JournalHabit.LateMeal), JournalHabit.ofDay(tags))
        // Keys are what's stored: each one different, and every habit found again from its own.
        assertEquals(JournalHabit.entries.size, JournalHabit.entries.map { it.key }.toSet().size)
        JournalHabit.entries.forEach { assertEquals(it, JournalHabit.of(it.key)) }
    }

    @Test fun `names fit a report's row and hints keep to the copy rules`() {
        for (l in listOf(false, true)) {
            org.openstrap.app.l10n.L10n.speak(l)
            JournalHabit.entries.forEach { h ->
                assertTrue(h.label, h.label.length <= 14)
                listOf("·", ";", "—").forEach { assertTrue("${h.hint} has $it", it !in h.hint && it !in h.label) }
            }
        }
        org.openstrap.app.l10n.L10n.speak(false)
    }
}
