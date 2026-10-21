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

    // Resting 60, max 190: calm is up to 60 + 0.25 × 130 = 92.5 bpm.
    private fun hrRun(from: Long, to: Long, bpm: Int) = (from until to).map { it to bpm }

    @Test
    fun stoppedAfterEightCalmMinutesEndsAtTheLastActiveMoment() {
        val hr = hrRun(start, start + 600, 140) + hrRun(start + 600, start + 1200, 80)
        val now = start + 600 + WorkoutRecorder.STOPPED_S
        assertEquals(start + 599, WorkoutRecorder.stoppedSince(hr, emptyList(), 60, 190, since = start, now = now))
        // A minute earlier it is still too soon.
        assertNull(WorkoutRecorder.stoppedSince(hr, emptyList(), 60, 190, since = start, now = now - 60))
    }

    @Test
    fun stepsKeepAWorkoutGoingAtACalmHeartRate() {
        val hr = hrRun(start, start + 1200, 80)
        // A first active spell at the start, then walking slowly: steps every minute.
        val active = hrRun(start, start + 60, 120)
        val steps = (0..20).map { start + it * 60L to 1000 + it * 50 }
        assertNull(WorkoutRecorder.stoppedSince(active + hr, steps, 60, 190, since = start, now = start + 1200))
    }

    @Test
    fun neverAsksWithoutAnActiveMomentOrAfterKeepGoing() {
        val calm = hrRun(start, start + 1200, 70)
        assertNull(WorkoutRecorder.stoppedSince(calm, emptyList(), 60, 190, since = start, now = start + 1200))
        val hr = hrRun(start, start + 60, 130) + hrRun(start + 60, start + 1200, 70)
        // "Keep going" at start + 700: nothing active since, so no second prompt.
        assertNull(WorkoutRecorder.stoppedSince(hr, emptyList(), 60, 190, since = start + 700, now = start + 1200))
    }

    @Test
    fun aDroppedStrapNeverEndsAWorkout() {
        // Active, then the strap went quiet: no heart rate at all after.
        val hr = hrRun(start, start + 60, 130)
        assertNull(WorkoutRecorder.stoppedSince(hr, emptyList(), 60, 190, since = start, now = start + 60 + 600))
    }

    @Test
    fun anEarlyEndLeavesTheRestRecordedButOutOfTheFigures() {
        val hr = (0L until 600L).map { AppWorkoutHeartRate(start, start + it, if (it < 300) 150 else 80) }
        val w = AppWorkout(start, start + 300, 1, recordedEnd = start + 600)
        val a = WorkoutRecorder.analyse(w, hr, 190)
        assertEquals(300, a.durationSeconds)
        assertEquals(150, a.workout.averageHeartRate)
        assertEquals(start + 600, a.recordedEnd)
        // Trimming the start too.
        val t = WorkoutRecorder.analyse(w.copy(shownStart = start + 100), hr, 190)
        assertEquals(200, t.durationSeconds)
        assertEquals(200L, t.workout.exerciseSeconds!!.toLong())
        assertEquals(start + 100, t.shownStart)
    }

    @Test
    fun usualAveragesTheLastFiveSimilarWorkouts() {
        fun workout(at: Long, sport: Int, length: Long, bpm: Int) =
            WorkoutRecorder.analyse(AppWorkout(at, at + length, sport), (0L until length).map { AppWorkoutHeartRate(at, at + it, bpm) }, 190)
        val now = workout(start + 100_000, 1, 1800, 140)
        val others = listOf(
            workout(start, 1, 1800, 130),
            workout(start + 10_000, 1, 1500, 150),
            workout(start + 20_000, 3, 1800, 100), // another sport
            workout(start + 30_000, 1, 300, 170), // far shorter
            workout(start + 200_000, 1, 1800, 160), // after it
        )
        val similar = WorkoutRecorder.similar(now, others)
        assertEquals(listOf(start + 10_000, start), similar.map { it.workout.start })
        val u = WorkoutRecorder.usual(similar)!!
        assertEquals(2, u.count)
        assertEquals(140, u.averageHeartRate)
        assertEquals(1650L, u.activeSeconds)
        // 130/190 = 68 %: zone 2; 150/190 = 79 %: zone 3; half each.
        assertEquals(0.5f, u.zoneShares[1], 0.01f)
        assertEquals(0.5f, u.zoneShares[2], 0.01f)
        assertNull(WorkoutRecorder.usual(emptyList()))
    }
}
