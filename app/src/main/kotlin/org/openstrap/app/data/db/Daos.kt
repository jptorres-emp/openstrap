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
}
