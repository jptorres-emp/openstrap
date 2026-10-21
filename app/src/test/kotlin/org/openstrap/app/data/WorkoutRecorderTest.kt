package org.openstrap.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openstrap.app.data.db.AppWorkout
import org.openstrap.app.data.db.AppWorkoutHeartRate

class WorkoutRecorderTest {
    /** On the minute, so minute arithmetic reads plainly. */
    private val start = 1_800_000_000L / 60 * 60

    @Test
    fun missingMinutesSkipCoveredCutAndPausedMinutes() {
        // 10 minutes from 30 s past the start minute; live for minutes 1–3, nothing after; paused across minutes 6–7.
        val from = start + 30
        val to = start + 30 + 600
        val live = (60L until 240L).map { start + it }
        val pause = (start + 6 * 60 + 10)..(start + 7 * 60 + 50)
        val missing = WorkoutRecorder.missingMinutes(from, to, listOf(pause), live)
        // Minute 0 is cut by the start, 10 by the end; 1–3 are live; 6 and 7 overlap the pause.
        assertEquals(listOf(4, 5, 8, 9).map { start + it * 60L }, missing)
    }

    @Test
    fun oneLiveSampleCoversItsMinute() {
        assertEquals(emptyList<Long>(), WorkoutRecorder.missingMinutes(start, start + 60, emptyList(), listOf(start + 59)))
    }

    @Test
    fun fillPrefersPerSecondHistoryThenMinuteRecords() {
        val missing = listOf(start, start + 60, start + 120)
        val rows = WorkoutRecorder.fillFromHistory(
            start, missing,
            secondAverages = mapOf(start to 131),
            minuteRecords = mapOf(start to 120, start + 60 to 125, start + 120 to 255),
        )
        // The third minute's 255 is not a heart rate, so it stays missing.
        assertEquals(listOf(AppWorkoutHeartRate(start, start, 131, true), AppWorkoutHeartRate(start, start + 60, 125, true)), rows)
    }

    @Test
    fun historyMinuteCountsAsAWholeMinuteOfZoneTime() {
        val w = AppWorkout(start, start + 120, 1)
        val hr = (0L until 60L).map { AppWorkoutHeartRate(start, start + it, 140) } + AppWorkoutHeartRate(start, start + 60, 140, fromHistory = true)
        val a = WorkoutRecorder.analyse(w, hr, maxHeartRate = 190)
        // 140/190 = 73 %: zone 3. Live samples give 59 s between them, the history minute 55 s of 5-second steps plus the join.
        assertTrue(a.zoneSeconds[2] in 115..120)
        assertEquals(1, a.historyMinutes)
        assertTrue(a.fromPhone)
        assertNull(a.recovery60)
        assertEquals(start + 120, a.end)
    }

    @Test
    fun statsWeighHistoryMinutesByTime() {
        val hr = (0L until 60L).map { AppWorkoutHeartRate(start, start + it, 100) } + AppWorkoutHeartRate(start, start + 60, 160, fromHistory = true)
        val s = WorkoutRecorder.stats(hr)!!
        assertEquals(130, s.average)
        assertEquals(160, s.max)
        assertEquals(100, s.min)
        assertNull(WorkoutRecorder.stats(emptyList()))
    }

    @Test
    fun activeTimeLeavesOutPauses() {
        val w = AppWorkout(start, null, 1).withPause(start + 100, start + 160).copy(pausedAt = start + 400)
        assertEquals(listOf((start + 100)..(start + 160)), w.pauseRanges)
        // 500 s in: 60 s of finished pause and 100 s of the one under way.
        assertEquals(340, WorkoutRecorder.activeSeconds(w, start + 500))
        val ended = w.withPause(start + 400, start + 500).copy(end = start + 600)
        assertEquals(440, WorkoutRecorder.activeSeconds(ended, start + 9_999))
    }

    @Test
    fun validRange() {
        assertTrue(WorkoutRecorder.isValid(30))
        assertTrue(!WorkoutRecorder.isValid(0))
        assertTrue(!WorkoutRecorder.isValid(255))
    }
}
