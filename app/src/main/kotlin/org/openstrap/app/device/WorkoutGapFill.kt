package org.openstrap.app.device

import org.openstrap.app.data.WorkoutRecorder
import org.openstrap.app.data.db.HealthDao
import org.openstrap.app.data.db.HeartRateSample

/**
 * After a history sync, fills the minutes each recent phone workout missed (a disconnect, a killed app) from what the
 * strap recorded meanwhile ([WorkoutRecorder.missingMinutes], [WorkoutRecorder.fillFromHistory]), then refreshes the
 * workout's averages. Running it again changes nothing once a minute is filled.
 */
class WorkoutGapFill(private val dao: HealthDao) {

    /** A line for the log when anything was filled, else null. */
    suspend fun run(now: Long = System.currentTimeMillis() / 1000): String? {
        var filled = 0
        for (w in dao.appWorkoutsOnce(now - LOOKBACK_S, now)) {
            val end = w.end ?: w.pausedAt ?: now
            val hr = dao.appWorkoutHeartRateOnce(w.start)
            val missing = WorkoutRecorder.missingMinutes(w.start, end, w.pauseRanges, hr.map { it.timestamp })
            if (missing.isEmpty()) continue
            val from = missing.first()
            val to = missing.last() + 59
            val perSecond = dao.heartRateOnce(from, to)
                .filter { it.source == HeartRateSample.SOURCE_HISTORY && it.timestamp % 60 == 0L }
                .associate { it.timestamp to it.bpm }
            val minutes = dao.activityOnce(from, to).mapNotNull { m -> m.heartRate?.let { m.timestamp to it } }.toMap()
            val rows = WorkoutRecorder.fillFromHistory(w.start, missing, perSecond, minutes)
            if (rows.isEmpty()) continue
            dao.insertAppWorkoutHeartRateIfMissing(rows)
            filled += rows.size
            // The recording workout gets its figures when it ends.
            if (w.end != null) WorkoutRecorder.stats(dao.appWorkoutHeartRateOnce(w.start))?.let { s ->
                dao.upsertAppWorkout(w.copy(averageHeartRate = s.average, maxHeartRate = s.max))
            }
        }
        return if (filled > 0) "workout gap fill: $filled minutes from the strap's history" else null
    }

    private companion object {
        /** The strap's history reaches back about this far; older workouts can't gain anything. */
        const val LOOKBACK_S = 7 * 86_400L
    }
}
