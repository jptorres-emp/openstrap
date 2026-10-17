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

/** Nightly HRV as SDNN (type 78). */
@Entity(tableName = "hrv_sdnn")
data class HrvSdnnSample(
    @PrimaryKey val timestamp: Long,
    val milliseconds: Float,
)

/**
 * A workout recorded on the strap (type 5). [start] is the strap's trackId. [end] is the start plus the
 * exercise time; the real end (pauses included) comes from the sport effect or the HR stream when present.
 */
@Entity(tableName = "workout")
data class Workout(
    @PrimaryKey val start: Long,
    val end: Long,
    val sportType: Int,
    val exerciseSeconds: Int?,
    val calories: Int?,
    val averageHeartRate: Int?,
    val maxHeartRate: Int?,
    val minHeartRate: Int?,
    val aerobicTrainingEffect: Float?,
    val anaerobicTrainingEffect: Float?,
    val trainingLoad: Int?,
    val vo2max: Int?,
    val steps: Int?,
)

/** Per-second HR during a workout and the strap's 3-minute recovery recording after it (type 6). */
@Entity(tableName = "workout_hr", primaryKeys = ["workoutStart", "timestamp"])
data class WorkoutHeartRate(
    val workoutStart: Long,
    val timestamp: Long,
    val bpm: Int,
)

/** The strap's post-workout result (type 59), keyed by the workout's end. */
@Entity(tableName = "sport_effect")
data class SportEffectRow(
    @PrimaryKey val end: Long,
    val recoveryMinutes: Int?,
    val vo2max: Float?,
    val epoc: Float?,
    val weeklyLoad: Float?,
    val monthlyLoad: Float?,
    val optimalLoadMin: Int?,
    val optimalLoadMax: Int?,
    val maximalHeartRate: Int?,
)

/**
 * The strap's own readiness for a day (module 0x0049), kept next to OpenStrap's so the two can be compared
 * and ours fitted later. The strap keeps only its latest score, so each sync saves it.
 */
@Entity(tableName = "strap_readiness")
data class StrapReadinessRow(
    /** Local midnight of the scored day. */
    @PrimaryKey val day: Long,
    val updatedAt: Long,
    val score: Int?,
    val hrvScore: Int?,
    val restingHrScore: Int?,
    val skinTemperatureScore: Int?,
    val breathingScore: Int?,
    val sleepHrv: Int?,
    val sleepRestingHr: Int?,
    val algorithmVersion: String,
)

/** Blood oxygen measured during sleep (type 38). Separate from daytime spot readings ([Spo2Sample]). */
@Entity(tableName = "sleep_spo2")
data class SleepSpo2Sample(
    @PrimaryKey val timestamp: Long,
    val percent: Int,
)

/**
 * One oxygen-desaturation event during sleep (type 39): the size of the drop, and the 240 SpO2 and 240 heart-rate
 * samples the strap recorded around it, one unsigned byte each. The samples are null for the short record form.
 */
@Entity(tableName = "osa_event")
class OsaEventRow(
    @PrimaryKey val timestamp: Long,
    val drop: Int,
    val spo2: ByteArray?,
    val heartRate: ByteArray?,
)

/**
 * The strap's nightly sleep-apnea summary (type 74), keyed by the night's belongTime (the same day rule as the
 * sleep result). Units of [validTime], [odiRaw] and [odiScore] are unverified, so they're kept as sent; readiness
 * uses only [eventCount].
 */
@Entity(tableName = "osa_result")
data class OsaResultRow(
    @PrimaryKey val belongTime: Long,
    val validTime: Long,
    val odiRaw: Long,
    val odiScore: Int,
    val dispCode: Int,
    val eventCount: Int,
    val version: Int,
)

/** The day's highest exercise heart rate (type 61). */
@Entity(tableName = "daily_max_hr")
data class DailyMaxHr(
    /** Start of the local day, epoch seconds. */
    @PrimaryKey val day: Long,
    val bpm: Int,
)

/** Heart rate after a workout ended (type 70): about one bpm per second, one unsigned byte each. */
@Entity(tableName = "recovery_hr")
class RecoveryHeartRateRow(
    @PrimaryKey val workoutStart: Long,
    val samples: ByteArray,
)

/** Extra per-minute activity (type 88). The duration units are unverified, so they're kept as sent. */
@Entity(tableName = "activity_extended")
data class ActivityExtendedMinute(
    @PrimaryKey val timestamp: Long,
    val floors: Int,
    val run: Int,
    val fatBurn: Int,
    val stand: Int,
    val walk: Int,
    val sit: Int,
    val steps: Int,
    val calories: Int,
    val distanceCm: Int,
)

/** A record whose format isn't decoded yet (type 57), kept whole so it can be parsed later. */
@Entity(tableName = "raw_record", primaryKeys = ["type", "timestamp"])
class RawRecord(
    val type: Int,
    val timestamp: Long,
    val payload: ByteArray,
)
