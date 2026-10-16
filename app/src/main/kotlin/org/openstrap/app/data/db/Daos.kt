package org.openstrap.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHeartRate(samples: List<HeartRateSample>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivity(minutes: List<ActivityMinute>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSleepSessions(sessions: List<SleepSession>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSleepStages(stages: List<SleepStage>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpo2(samples: List<Spo2Sample>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStress(samples: List<StressSample>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHrv(samples: List<HrvSample>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRestingHr(samples: List<RestingHeartRate>)

    /** For estimates (from PAI) that must not overwrite the strap's own daily resting HR. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRestingHrIfMissing(samples: List<RestingHeartRate>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSkinTemperature(samples: List<SkinTemperature>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBreathRate(samples: List<BreathRate>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPai(samples: List<PaiSample>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHrvSdnn(samples: List<HrvSdnnSample>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkouts(workouts: List<Workout>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutHeartRate(samples: List<WorkoutHeartRate>)

    @Query("DELETE FROM workout_hr WHERE workoutStart = :start")
    suspend fun deleteWorkoutHeartRate(start: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSportEffects(rows: List<SportEffectRow>)

    @Query("SELECT COUNT(*) FROM workout")
    suspend fun workoutCount(): Int

    @Query("SELECT * FROM workout ORDER BY start DESC")
    fun workouts(): Flow<List<Workout>>

    @Query("SELECT * FROM workout WHERE start = :start")
    fun workout(start: Long): Flow<Workout?>

    @Query("SELECT * FROM workout_hr WHERE workoutStart = :start ORDER BY timestamp")
    fun workoutHeartRate(start: Long): Flow<List<WorkoutHeartRate>>

    /** Last HR sample per workout: the strap records three minutes past the end, so this places the end. */
    @Query("SELECT workoutStart, MAX(timestamp) AS timestamp, 0 AS bpm FROM workout_hr GROUP BY workoutStart")
    fun lastWorkoutHeartRates(): Flow<List<WorkoutHeartRate>>

    @Query("SELECT * FROM sport_effect ORDER BY `end`")
    fun sportEffects(): Flow<List<SportEffectRow>>

    @Query("SELECT * FROM hrv_sdnn WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp")
    fun hrvSdnn(from: Long, to: Long): Flow<List<HrvSdnnSample>>

    @Query("UPDATE activity_minute SET kind = kind & 15 WHERE kind > 15 AND kind != 126")
    suspend fun fixActivityModeNibble(): Int

    /** The strap re-sends a day's sleep as it updates it; drop the old version first. */
    @Query("DELETE FROM sleep_stage WHERE sessionStart IN (SELECT start FROM sleep_session WHERE day = :day AND nap = :nap)")
    suspend fun deleteSleepStagesForDay(day: Long, nap: Boolean)

    @Query("DELETE FROM sleep_session WHERE day = :day AND nap = :nap")
    suspend fun deleteSleepSessionsForDay(day: Long, nap: Boolean)

    /**
     * Naps that overlap a main sleep. The strap can briefly report a stretch of broken night sleep as a nap,
     * then fold it into the main sleep; earlier builds kept that nap.
     */
    /**
     * Earlier builds ended each stage at its last minute instead of a minute later, leaving a minute's gap before the
     * next stage. Close those gaps; stages that already end where the next starts, or at the session end, are left.
     */
    @Query(
        "UPDATE sleep_stage SET `end` = `end` + 60 " +
            "WHERE NOT EXISTS (SELECT 1 FROM sleep_stage n WHERE n.sessionStart = sleep_stage.sessionStart AND n.start = sleep_stage.`end` AND n.start > sleep_stage.start) " +
            "AND (EXISTS (SELECT 1 FROM sleep_stage n WHERE n.sessionStart = sleep_stage.sessionStart AND n.start = sleep_stage.`end` + 60) " +
            "OR `end` + 60 = (SELECT s.`end` FROM sleep_session s WHERE s.start = sleep_stage.sessionStart))",
    )
    suspend fun closeSleepStageGaps(): Int

    @Query("DELETE FROM sleep_stage WHERE sessionStart IN (SELECT n.start FROM sleep_session n JOIN sleep_session m ON m.nap = 0 AND n.nap = 1 AND n.start < m.`end` AND n.`end` > m.start)")
    suspend fun deleteNapStagesInsideMainSleep(): Int

    @Query("DELETE FROM sleep_session WHERE nap = 1 AND EXISTS (SELECT 1 FROM sleep_session m WHERE m.nap = 0 AND sleep_session.start < m.`end` AND sleep_session.`end` > m.start)")
    suspend fun deleteNapsInsideMainSleep(): Int

    @Query("SELECT * FROM skin_temperature WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp")
    fun skinTemperature(from: Long, to: Long): Flow<List<SkinTemperature>>

    @Query("SELECT * FROM skin_temperature WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp")
    suspend fun skinTemperatureOnce(from: Long, to: Long): List<SkinTemperature>

    @Query("SELECT * FROM breath_rate WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp")
    fun breathRate(from: Long, to: Long): Flow<List<BreathRate>>

    @Query("SELECT * FROM breath_rate WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp")
    suspend fun breathRateOnce(from: Long, to: Long): List<BreathRate>

    @Query("SELECT * FROM pai WHERE timestamp <= :before ORDER BY timestamp DESC LIMIT 1")
    fun latestPai(before: Long): Flow<PaiSample?>

    @Query("SELECT * FROM pai WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp")
    suspend fun paiOnce(from: Long, to: Long): List<PaiSample>

    @Query("SELECT * FROM heart_rate WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp")
    fun heartRate(from: Long, to: Long): Flow<List<HeartRateSample>>

    @Query("SELECT * FROM activity_minute WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp")
    fun activity(from: Long, to: Long): Flow<List<ActivityMinute>>

    @Query("SELECT * FROM sleep_session WHERE `end` BETWEEN :from AND :to ORDER BY start")
    fun sleepSessions(from: Long, to: Long): Flow<List<SleepSession>>

    @Query("SELECT * FROM sleep_stage WHERE sessionStart = :sessionStart ORDER BY start")
    fun sleepStages(sessionStart: Long): Flow<List<SleepStage>>

    @Query("SELECT * FROM spo2 WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp")
    fun spo2(from: Long, to: Long): Flow<List<Spo2Sample>>

    @Query("SELECT * FROM stress WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp")
    fun stress(from: Long, to: Long): Flow<List<StressSample>>

    @Query("SELECT * FROM hrv WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp")
    fun hrv(from: Long, to: Long): Flow<List<HrvSample>>

    @Query("SELECT * FROM resting_hr WHERE day BETWEEN :from AND :to ORDER BY day")
    fun restingHr(from: Long, to: Long): Flow<List<RestingHeartRate>>

    // One-shot reads for export.
    @Query("SELECT * FROM heart_rate WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp")
    suspend fun heartRateOnce(from: Long, to: Long): List<HeartRateSample>

    /** Rows written from 0xFF padding records by builds before the parser skipped them. */
    @Query("SELECT MIN(timestamp) FROM activity_minute WHERE steps = 255 AND intensity = 255 AND kind = 255")
    suspend fun firstPaddingActivity(): Long?

    @Query("DELETE FROM activity_minute WHERE steps = 255 AND intensity = 255 AND kind = 255")
    suspend fun deletePaddingActivity(): Int

    /** Rows dated outside [from, to]: stale buffer slots a strap sent with impossible times. */
    @Query("DELETE FROM activity_minute WHERE timestamp < :from OR timestamp > :to")
    suspend fun deleteActivityOutside(from: Long, to: Long): Int

    @Query("SELECT MAX(timestamp) FROM activity_minute WHERE timestamp <= :before")
    suspend fun latestActivity(before: Long): Long?

    @Query("SELECT * FROM activity_minute WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp")
    suspend fun activityOnce(from: Long, to: Long): List<ActivityMinute>

    @Query("SELECT * FROM sleep_session WHERE `end` BETWEEN :from AND :to ORDER BY start")
    suspend fun sleepSessionsOnce(from: Long, to: Long): List<SleepSession>

    @Query("SELECT * FROM sleep_stage WHERE sessionStart = :sessionStart ORDER BY start")
    suspend fun sleepStagesOnce(sessionStart: Long): List<SleepStage>

    @Query("SELECT * FROM spo2 WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp")
    suspend fun spo2Once(from: Long, to: Long): List<Spo2Sample>

    @Query("SELECT * FROM stress WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp")
    suspend fun stressOnce(from: Long, to: Long): List<StressSample>

    @Query("SELECT * FROM hrv WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp")
    suspend fun hrvOnce(from: Long, to: Long): List<HrvSample>

    @Query("SELECT * FROM resting_hr WHERE day BETWEEN :from AND :to ORDER BY day")
    suspend fun restingHrOnce(from: Long, to: Long): List<RestingHeartRate>

    /**
     * Hours (epoch seconds / 3600) that hold something worth showing: heart rate, steps or the end of a
     * sleep. Hours rather than days so the caller can map them to local days in its own time zone.
     */
    @Query(
        "SELECT DISTINCT timestamp / 3600 FROM heart_rate " +
            "UNION SELECT DISTINCT timestamp / 3600 FROM activity_minute WHERE steps > 0 AND steps != 255 " +
            "UNION SELECT DISTINCT `end` / 3600 FROM sleep_session",
    )
    fun hoursWithData(): Flow<List<Long>>

    @Query("SELECT MAX(timestamp) FROM heart_rate")
    fun latestHeartRateTime(): Flow<Long?>

    @Query("SELECT MAX(timestamp) FROM stress")
    fun latestStressTime(): Flow<Long?>

    @Query("SELECT MAX(`end`) FROM sleep_session")
    fun latestSleepEnd(): Flow<Long?>
}
