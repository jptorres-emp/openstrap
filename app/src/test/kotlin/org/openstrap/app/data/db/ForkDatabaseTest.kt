package org.openstrap.app.data.db

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openstrap.app.testing.MigrationHarness
import org.openstrap.app.testing.inMemoryDb
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * The fork's own version 20 (the original's 19 plus `weight`) opens at the current version with everything it had
 * ([ForkDatabase]): the phone that ran the fork upgrades without losing a day. Its schema file is the fork's 20.json,
 * kept under the test resources.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ForkDatabaseTest {
    @get:Rule val harness = MigrationHarness()

    private val lisbon = ZoneId.of("Europe/Lisbon")
    private val forkSchema = File(requireNotNull(javaClass.getResource("/fork-schemas/20.json")).toURI())

    /** A fork day key: the local midnight in Lisbon. */
    private fun legacy(date: LocalDate): Long = date.atStartOfDay(lisbon).toEpochSecond()

    @Test fun `the fork's version 20 opens at the current one with its weights, workouts and days`() = runTest {
        val file = harness.create(forkSchema, ForkDatabase.FORK_VERSION) {
            // 29 October is winter time, so its local midnight is already the date's key; 20 October is summer time.
            execSQL("INSERT INTO weight (day, kg, updatedAt) VALUES (?, 93.0, 100)", arrayOf<Any>(legacy(LocalDate.of(2026, 10, 29))))
            execSQL("INSERT INTO weight (day, kg, updatedAt) VALUES (?, 93.5, 50)", arrayOf<Any>(legacy(LocalDate.of(2026, 10, 20))))
            execSQL("INSERT INTO app_workout (start, `end`, sportType, source, pauses) VALUES (1794249080, 1794252781, 130, 0, '')")
            execSQL("INSERT INTO heart_rate (timestamp, bpm, source) VALUES (1794249120, 147, 0)")
            execSQL("INSERT INTO check_in (day, feeling, updatedAt) VALUES (?, 4, 10)", arrayOf<Any>(legacy(LocalDate.of(2026, 11, 9))))
        }
        assertTrue(ForkDatabase.repair(file, lisbon))
        val db = harness.open(file, lisbon)
        val dao = db.healthDao()
        assertEquals(
            listOf(WeightEntry(LocalDate.of(2026, 10, 20), 93.5f, 50), WeightEntry(LocalDate.of(2026, 10, 29), 93.0f, 100)),
            dao.weightsAll(),
        )
        assertEquals(listOf(1794249080L), dao.appWorkoutsStartingOnce(0, 2_000_000_000).map { it.start })
        assertEquals(listOf(HeartRateSample(1794249120, 147, 0)), dao.heartRateOnce(1794249000, 1794250000))
        assertEquals(4, dao.checkInsOnce(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 9)).single().feeling)
        assertEquals(33, db.openHelper.readableDatabase.version)
    }

    @Test fun `a repaired file isn't repaired twice, and the original's own files are left alone`() {
        val fork = harness.create(forkSchema, ForkDatabase.FORK_VERSION)
        assertTrue(ForkDatabase.repair(fork, lisbon))
        assertFalse(ForkDatabase.repair(fork, lisbon))
        assertFalse(ForkDatabase.repair(harness.create(20), lisbon))
        assertFalse(ForkDatabase.repair(harness.create(32), lisbon))
        assertFalse(ForkDatabase.repair(File("/nonexistent/openstrap.db"), lisbon))
    }

    @Test fun `a new database has the weight log`() = runTest {
        val db = inMemoryDb()
        db.healthDao().insertWeights(listOf(WeightEntry(LocalDate.of(2026, 11, 21), 92.4f, 1)))
        assertEquals(92.4f, db.healthDao().weightsAll().single().kg)
        db.close()
    }
}
