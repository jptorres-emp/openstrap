package org.openstrap.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** All timestamps are epoch seconds (UTC). */

@Entity(tableName = "heart_rate")
data class HeartRateSample(
    @PrimaryKey val timestamp: Long,
    val bpm: Int,
    val source: Int = SOURCE_HISTORY,
) {
    companion object {
        const val SOURCE_HISTORY = 0
        const val SOURCE_LIVE = 1
    }
}

@Entity(tableName = "activity_minute")
data class ActivityMinute(
    @PrimaryKey val timestamp: Long,
    val steps: Int,
    val intensity: Int,
    val kind: Int,
    val heartRate: Int?,
)

@Entity(tableName = "sleep_session")
data class SleepSession(
    @PrimaryKey val start: Long,
    val end: Long,
)

@Entity(tableName = "sleep_stage", indices = [Index("sessionStart")])
data class SleepStage(
    @PrimaryKey val start: Long,
    val end: Long,
    val sessionStart: Long,
    val stage: Int,
) {
    companion object {
        const val AWAKE = 0
        const val LIGHT = 1
        const val DEEP = 2
        const val REM = 3
    }
}

@Entity(tableName = "spo2")
data class Spo2Sample(
    @PrimaryKey val timestamp: Long,
    val percent: Int,
)

@Entity(tableName = "stress")
data class StressSample(
    @PrimaryKey val timestamp: Long,
    val value: Int,
)

@Entity(tableName = "hrv")
data class HrvSample(
    @PrimaryKey val timestamp: Long,
    val milliseconds: Int,
)

@Entity(tableName = "resting_hr")
data class RestingHeartRate(
    /** Start of the local day, epoch seconds. */
    @PrimaryKey val day: Long,
    val bpm: Int,
)
