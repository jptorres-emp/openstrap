package org.openstrap.app.data.db

import androidx.room.ColumnInfo
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

/** A main sleep or a nap. The strap's own totals and score are kept as it reported them. */
@Entity(tableName = "sleep_session", indices = [Index("day")])
data class SleepSession(
    @PrimaryKey val start: Long,
    val end: Long,
    /** Local midnight of the day the strap assigned this sleep to; 0 for rows from before v2. */
    @ColumnInfo(defaultValue = "0") val day: Long = 0,
    @ColumnInfo(defaultValue = "0") val nap: Boolean = false,
    val score: Int? = null,
    val averageHeartRate: Int? = null,
    val deepMinutes: Int? = null,
    val lightMinutes: Int? = null,
    val remMinutes: Int? = null,
    val awakeMinutes: Int? = null,
    val wakeCount: Int? = null,
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

/** Skin temperature from the strap's touch sensor, one per minute (type 46). */
@Entity(tableName = "skin_temperature")
data class SkinTemperature(
    @PrimaryKey val timestamp: Long,
    val celsius: Float,
)

/** Respiratory rate during sleep, breaths per minute (type 56). */
@Entity(tableName = "breath_rate")
data class BreathRate(
    @PrimaryKey val timestamp: Long,
    val perMinute: Int,
)

/** The strap's PAI state at [timestamp] (type 13); the latest row of a day is that day's value. */
@Entity(tableName = "pai")
data class PaiSample(
    @PrimaryKey val timestamp: Long,
    val daily: Float,
    val total: Float,
    val restingHeartRate: Int,
    val maxHeartRate: Int,
)
