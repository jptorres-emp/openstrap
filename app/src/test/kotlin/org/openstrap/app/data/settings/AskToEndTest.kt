package org.openstrap.app.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.openstrap.app.data.Workouts

/** "Ask to end when I stop": which group each sport takes its time from, the times out of the box, and how they're kept. */
class AskToEndTest {
    @get:Rule val folder = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After fun tearDown() = scope.cancel()

    @Test fun `each family maps to its group`() {
        for (type in Workouts.SPORT_IDS) {
            val expected = when (Workouts.family(type)) {
                Workouts.Family.MindBody -> AskToEndGroup.MindBody
                Workouts.Family.Games -> AskToEndGroup.Games
                else -> AskToEndGroup.Most
            }
            assertEquals("sport $type", expected, AskToEndGroup.of(type))
        }
    }

    @Test fun `runs, strength and HIIT are most workouts, yoga is yoga and pilates`() {
        assertEquals(AskToEndGroup.Most, AskToEndGroup.of(1))
        assertEquals(AskToEndGroup.Most, AskToEndGroup.of(49))
        assertEquals(AskToEndGroup.MindBody, AskToEndGroup.of(53))
        val game = Workouts.SPORT_IDS.first { Workouts.family(it) == Workouts.Family.Games }
        assertEquals(AskToEndGroup.Games, AskToEndGroup.of(game))
    }

    @Test fun `out of the box it is on, 8 and 15 minutes, and off for yoga`() {
        val a = AskToEnd()
        assertEquals(true, a.on)
        assertEquals(8, a.minutes(AskToEndGroup.Most))
        assertEquals(15, a.minutes(AskToEndGroup.Games))
        assertNull(a.minutes(AskToEndGroup.MindBody))
        assertEquals(8 * 60L, a.stillSecondsFor(1))
        assertNull(a.stillSecondsFor(53))
    }

    @Test fun `the switch off never asks`() {
        assertNull(AskToEnd(on = false).stillSecondsFor(1))
    }

    @Test fun `the store gives the defaults until set, and keeps Off apart from unset`() = runBlocking {
        val settings = SettingsStore.of(
            PreferenceDataStoreFactory.create(scope = scope) { folder.newFile("s.preferences_pb").also { it.delete() } },
        )
        assertEquals(AskToEnd(), settings.askToEnd.first())
        val set = AskToEnd(on = false, most = null, games = 30, mindBody = 5)
        settings.setAskToEnd(set)
        assertEquals(set, settings.askToEnd.first())
    }
}
