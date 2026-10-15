package org.openstrap.protocol.history

/** Every parsed record carries a UTC epoch-second timestamp. */
sealed interface HistoryRecord {
    val timestamp: Long
}

/** Heart-rate byte validation. Spec 02 §3.1: 254 is the "no HR" default; treat >= 254 as missing. */
object HeartRateSentinel {
    // UNVERIFIED: 02 §3.1 - whether 255 is also invalid; we treat >= 254 as missing. We also treat
    // 0 as missing since 0 bpm is not a physiological reading.
    fun valid(raw: Int): Int? = if (raw == 0 || raw >= 254) null else raw
}

/** Activity `mode` constants (spec 02 §3.1). */
object ActivityMode {
    const val SILENT = 0
    const val WALKING = 1
    const val RUNNING = 2
    const val NOT_WORN = 3
    const val REM = 4
    const val NREM = 5
    const val CHARGING = 6
    const val ON_BED = 7
    const val DREAM = 8
    const val USER_SLEEP = 9
    const val BLANK = 126
}

/** Type 1, one per minute. [sleepExtra] holds bytes 4..7 when the record size is >= 8. */
data class ActivitySample(
    override val timestamp: Long,
    val mode: Int,
    val intensity: Int,
    val steps: Int,
    val rawHeartRate: Int,
    val sleepExtra: ActivitySleepExtra?,
) : HistoryRecord {
    val heartRate: Int? get() = HeartRateSentinel.valid(rawHeartRate)
}

// UNVERIFIED: 02 §3.1 - bit-level meaning of bytes 4..7 is unknown; exposed raw.
data class ActivitySleepExtra(val retain: Int, val sleepPostureAndProb: Int, val sleepProbDeep: Int, val sleepProbRem: Int)

/** Type 88, one per minute. Duration units UNVERIFIED (02 §3.1.3). */
data class TodayActivityExtended(
    override val timestamp: Long,
    val version: Int,
    val distanceType: Int,
    val floorCount: Int,
    val runDuration: Int,
    val fatBurnDuration: Int,
    val standDuration: Int,
    val walkDuration: Int,
    val sitDuration: Int,
    val steps: Int,
    val calories: Int,
    val distanceCm: Int,
) : HistoryRecord

/** Types 76 and 87: one HR sample per second. */
data class SecondHeartRate(override val timestamp: Long, val rawHeartRate: Int) : HistoryRecord {
    val heartRate: Int? get() = HeartRateSentinel.valid(rawHeartRate)
}

/** Type 2: manual / spot HR. */
data class SpotHeartRate(override val timestamp: Long, val tzQuarters: Int, val rawHeartRate: Int) : HistoryRecord {
    val heartRate: Int? get() = HeartRateSentinel.valid(rawHeartRate)
}

enum class SleepStageType { AWAKE, LIGHT, DEEP, REM, UNKNOWN }

/**
 * One stage of an on-device sleep result. [start]/[stop] are raw i16 values whose unit/origin is
 * UNVERIFIED (02 §3.3); see [SleepResult.resolveTime].
 */
data class SleepStage(val start: Int, val stop: Int, val rawType: Int) {
    val type: SleepStageType get() = SleepStageCodes.map(rawType)
}

object SleepStageCodes {
    // UNVERIFIED: 02 §3.3 - numeric stage codes were not found in the decompiled app. Pure best
    // guess; calibrate against the official app's sleep chart on real hardware.
    fun map(code: Int): SleepStageType = when (code) {
        0 -> SleepStageType.AWAKE
        1 -> SleepStageType.LIGHT
        2 -> SleepStageType.DEEP
        3 -> SleepStageType.REM
        else -> SleepStageType.UNKNOWN
    }
}

data class ShortSleep(val start: Int, val stop: Int, val length: Int)

/** Type 72: on-device sleep result (594 bytes). All i16 time fields are raw. */
data class SleepResult(
    override val timestamp: Long,
    val belongTime: Long,
    val version: Int,
    val hasMainSleep: Boolean,
    val mainSleepStart: Int,
    val mainSleepEnd: Int,
    val onBedTime: Int,
    val offBedTime: Int,
    val isRemSupported: Boolean,
    val isShortSleepSupported: Boolean,
    val hasShortSleep: Boolean,
    val averageHeartRate: Int,
    val sleepScore: Int,
    val shortSleeps: List<ShortSleep>,
    val mainStages: List<SleepStage>,
    val shortStages: List<SleepStage>,
    val remSleepLength: Int,
    val lightSleepLength: Int,
    val deepSleepLength: Int,
    val wakeLength: Int,
) : HistoryRecord {
    /**
     * Convert a raw i16 time field to epoch seconds.
     * UNVERIFIED: 02 §3.3 - assumes minutes relative to [belongTime]. Adjust after hardware tests.
     */
    fun resolveTime(raw: Int): Long = belongTime + raw * 60L
}

/** Type 37: spot/auto SpO2. [history] holds the 60 preceding samples (v2 only). */
data class SpO2Reading(
    override val timestamp: Long,
    val spo2: Int,
    val isAuto: Boolean,
    val history: List<Int>?,
) : HistoryRecord {
    /** The app keeps only 80..100 (02 §3.5). */
    val isPlausible: Boolean get() = spo2 in 80..100
}

/** Type 38: sleep SpO2 samples. [v2] extras are present only for version 2. */
data class SleepSpO2Sample(
    override val timestamp: Long,
    val spo2: Int,
    // UNVERIFIED: 02 §3.5 - meaning of `time` (probably measurement seconds).
    val time: Int,
    val v2: SleepSpO2Extra?,
) : HistoryRecord

data class SleepSpO2Extra(val high: ByteArray, val low: ByteArray, val signalQuality: ByteArray, val extend: ByteArray) {
    override fun equals(other: Any?) = other is SleepSpO2Extra && high.contentEquals(other.high) &&
        low.contentEquals(other.low) && signalQuality.contentEquals(other.signalQuality) && extend.contentEquals(other.extend)
    override fun hashCode() = high.contentHashCode() * 31 + low.contentHashCode()
}

/** Types 39/43: OSA desaturation event. [spo2]/[heartRate] (240 each) only in the long form. */
data class SpO2OsaEvent(
    override val timestamp: Long,
    val spo2Decrease: Int,
    val spo2: List<Int>?,
    val heartRate: List<Int>?,
) : HistoryRecord

/** Type 74: OSA result (ODI). */
data class OsaResult(
    override val timestamp: Long, // belongTime
    val odiValidTime: Long,
    val odiRefined: Long,
    val odiScore: Int,
    val odiDispCode: Int,
    val version: Int,
    val odNumRefined: Int,
    val events: List<OsaDesaturation>,
) : HistoryRecord

data class OsaDesaturation(val odTime: Long, val odValue: Int, val odDuration: Int)

/**
 * Types 18 (single) and 19 (all-day, per minute). Raw value kept.
 */
data class StressSample(override val timestamp: Long, val stress: Int, val allDay: Boolean) : HistoryRecord {
    // UNVERIFIED: 02 §3.6 - sentinel not confirmed; 0 and >= 254 are probably gaps.
    val isLikelyGap: Boolean get() = stress == 0 || stress >= 254
}

/** Type 73. RMSSD unit probably ms (UNVERIFIED). */
data class HrvRmssd(override val timestamp: Long, val tzQuarters: Int, val rmssd: Int) : HistoryRecord

/** Type 78. SDNN unit probably ms (UNVERIFIED). */
data class HrvSdnn(override val timestamp: Long, val tzQuarters: Int, val sdnn: Long) : HistoryRecord

/** Type 86: real-time resting HR + baseline. `level` semantics UNVERIFIED. */
data class RestHrRealtime(
    override val timestamp: Long,
    val tzQuarters: Int,
    val realtimeRestHr: Int,
    val restHrBaseline: Int,
    val level: Int,
) : HistoryRecord

/** Type 58: daily resting HR. */
data class RestingHeartRate(override val timestamp: Long, val tzQuarters: Int, val heartRate: Int) : HistoryRecord

/** Type 61: daily max exercise HR. */
data class DailyMaxHeartRate(override val timestamp: Long, val tzQuarters: Int, val maxHeartRate: Int) : HistoryRecord

/** Type 56: sleep respiratory rate. */
data class SleepBreathRate(
    override val timestamp: Long,
    val tzQuarters: Int,
    val breathRate: Int,
    val snr: Int,
    val channel: Int,
) : HistoryRecord

/** Type 57: opaque 569-byte event kept raw for later processing. */
class SleepRespiratoryEvent(override val timestamp: Long, val tzQuarters: Int, val raw: ByteArray) : HistoryRecord

/** Type 70: recovery HR after a workout. [timestamp] is the trackId (= workout start epoch). */
data class RecoveryHeartRate(override val timestamp: Long, val samples: List<Int>) : HistoryRecord {
    val trackId: Long get() = timestamp
}

/** Type 4: legacy temperature. Unit/offset UNVERIFIED (02 §3.10). */
data class LegacyTemperature(override val timestamp: Long, val rawTemperature: Int) : HistoryRecord

/** Type 13: PAI. Optional fields depend on [version]. */
data class PaiRecord(
    override val timestamp: Long,
    val version: Int,
    val timezoneRaw: Long,
    val activityScores: List<Float>,
    val lowZonePai: Float,
    val mediumZonePai: Float,
    val highZonePai: Float,
    val lowZoneMinutes: Int,
    val mediumZoneMinutes: Int,
    val highZoneMinutes: Int,
    val dailyPai: Float,
    val totalPai: Float,
    val restHr: Int,
    val maxHr: Int,
    val index: Int,
    val type: Int,
    val nextActivityScores: List<Float>?,
    val gender: Int?,
    val age: Int?,
    val lowZoneLimit: Int?,
    val mediumZoneLimit: Int?,
    val highZoneLimit: Int?,
) : HistoryRecord
