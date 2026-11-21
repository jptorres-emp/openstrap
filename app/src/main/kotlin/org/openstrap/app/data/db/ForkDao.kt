package org.openstrap.app.data.db

import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * The fork's own reads and writes, kept apart from the original's [HealthDao], which takes them by extending this:
 * the weight log ([WeightEntry]) and the steps filled in from the extended minutes
 * ([ActivityMinute.KIND_FROM_EXTENDED], which the mode repair in [HealthDao.fixActivityModeNibble] leaves alone).
 */
interface ForkDao {
    // Weights the user logged, one a day.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeights(rows: List<WeightEntry>)

    @Query("DELETE FROM weight WHERE day = :day")
    suspend fun deleteWeight(day: LocalDate)

    @Query("SELECT * FROM weight WHERE day BETWEEN :from AND :to ORDER BY day")
    fun weights(from: LocalDate, to: LocalDate): Flow<List<WeightEntry>>

    @Query("SELECT * FROM weight ORDER BY day")
    suspend fun weightsAll(): List<WeightEntry>

    /**
     * Steps for minutes with no activity record (type 1) from the extended records (type 88) between [from] and [to]:
     * the strap keeps type 1 about 43 hours and type 88 for months, so a phone away longer still gets the day's steps.
     * Filled rows are marked [ActivityMinute.KIND_FROM_EXTENDED]; a minute that has a record of its own is left alone.
     */
    @Query(
        "INSERT INTO activity_minute (timestamp, steps, intensity, kind, heartRate) " +
            "SELECT e.timestamp / 60 * 60, e.steps, 0, ${ActivityMinute.KIND_FROM_EXTENDED}, NULL FROM activity_extended e " +
            "WHERE e.timestamp BETWEEN :from AND :to AND e.steps BETWEEN 1 AND 250 AND NOT EXISTS (" +
            "SELECT 1 FROM activity_minute a WHERE a.timestamp BETWEEN e.timestamp / 60 * 60 AND e.timestamp / 60 * 60 + 59)",
    )
    suspend fun fillStepsFromExtended(from: Long, to: Long)

    /** Drops the filled-in minutes ([fillStepsFromExtended]) that an activity record for the same minute now covers. */
    @Query(
        "DELETE FROM activity_minute WHERE kind = ${ActivityMinute.KIND_FROM_EXTENDED} AND timestamp BETWEEN :from AND :to " +
            "AND EXISTS (SELECT 1 FROM activity_minute a WHERE a.kind != ${ActivityMinute.KIND_FROM_EXTENDED} " +
            "AND a.timestamp BETWEEN activity_minute.timestamp / 60 * 60 AND activity_minute.timestamp / 60 * 60 + 59)",
    )
    suspend fun dropFilledStepsCovered(from: Long, to: Long): Int
}
