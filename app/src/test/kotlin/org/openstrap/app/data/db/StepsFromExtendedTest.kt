package org.openstrap.app.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openstrap.app.data.Summaries
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Minutes whose activity record (type 1, kept about 43 hours) was never synced take their steps from the extended record
 * (type 88, kept 60 days and more), and give way when an activity record for the minute turns up.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StepsFromExtendedTest {
    private lateinit var db: OpenStrapDatabase
    private lateinit var dao: HealthDao

    private val t0 = 1_761_000_000L / 60 * 60

    private fun extended(t: Long, steps: Int) = ActivityExtendedMinute(t, 0, 0, 0, 0, 0, 0, steps, 0, 0)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), OpenStrapDatabase::class.java).allowMainThreadQueries().build()
        dao = db.healthDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun missingMinutesTakeTheirStepsFromTheExtendedRecord() = runTest {
        // Minute 0 has its activity record; minutes 1 and 2 only the extended one; minute 3 has no steps at all.
        dao.insertActivity(listOf(ActivityMinute(t0, 40, 10, 0, 90)))
        dao.insertActivityExtended(listOf(extended(t0, 41), extended(t0 + 60, 55), extended(t0 + 120, 60), extended(t0 + 180, 0)))
        dao.fillStepsFromExtended(t0, t0 + 180)
        val minutes = dao.activityOnce(t0, t0 + 300)
        assertEquals(listOf(t0, t0 + 60, t0 + 120), minutes.map { it.timestamp })
        // The activity record keeps its own count.
        assertEquals(40 + 55 + 60, Summaries.totalSteps(minutes))
        assertEquals(listOf(0, ActivityMinute.KIND_FROM_EXTENDED, ActivityMinute.KIND_FROM_EXTENDED), minutes.map { it.kind })
        // Filling again adds nothing.
        dao.fillStepsFromExtended(t0, t0 + 180)
        assertEquals(3, dao.activityOnce(t0, t0 + 300).size)
    }

    @Test
    fun anActivityRecordReplacesTheFilledMinute() = runTest {
        dao.insertActivityExtended(listOf(extended(t0, 50), extended(t0 + 60, 70)))
        dao.fillStepsFromExtended(t0, t0 + 60)
        // The same minute, on the minute: it replaces the filled row.
        dao.insertActivity(listOf(ActivityMinute(t0, 48, 10, 0, 95)))
        // The same minute, seconds in: the filled row goes too.
        dao.insertActivity(listOf(ActivityMinute(t0 + 60 + 5, 66, 10, 0, 97)))
        assertEquals(1, dao.dropFilledStepsCovered(t0 - 60, t0 + 180))
        val minutes = dao.activityOnce(t0, t0 + 300)
        assertEquals(listOf(48, 66), minutes.map { it.steps })
        assertEquals(listOf(0, 0), minutes.map { it.kind })
    }

    @Test
    fun theRepairsAfterEachSyncLeaveFilledMinutesAsTheyAre() = runTest {
        dao.insertActivityExtended(listOf(extended(t0, 50)))
        dao.fillStepsFromExtended(t0, t0)
        // A whole mode byte from an earlier build: only that one is cut to its nibble.
        dao.insertActivity(listOf(ActivityMinute(t0 + 60, 30, 10, 0x21, 90)))
        assertEquals(1, dao.fixActivityModeNibble())
        assertEquals(listOf(ActivityMinute.KIND_FROM_EXTENDED, 1), dao.activityOnce(t0, t0 + 120).map { it.kind })
        // The last activity record is the strap's own, not a filled-in minute after it.
        dao.insertActivityExtended(listOf(extended(t0 + 300, 20)))
        dao.fillStepsFromExtended(t0 + 300, t0 + 300)
        assertEquals(t0 + 60, dao.latestActivity(t0 + 600))
    }
}
