package org.openstrap.protocol.history

import org.openstrap.protocol.services.DeviceFeatures

/**
 * How to fetch and decode one GDSP data type.
 *
 * @param syncOrder position in the recommended OpenStrap order (02 §5.1); lower runs first.
 * @param parser null when decoding is not implemented yet (raw payload only).
 * @param loops false for types the engine fetches only once per sync (02 §1.6).
 * @param followedBy a type fetched right after each chunk of this one, from the same cursor, instead of on its own.
 */
data class HistoryTypeSpec(
    val type: HistoryDataType,
    val syncOrder: Int,
    val parser: HistoryParser<HistoryRecord>?,
    val loops: Boolean = true,
    val followedBy: HistoryDataType? = null,
) {
    val code: Int get() = type.code
}

object HistoryTypeSpecs {
    /** Strap-relevant types in the recommended sync order (02 §5.1). */
    val all: List<HistoryTypeSpec> = listOf(
        HistoryTypeSpec(HistoryDataType.ACTIVITY, 1, HistoryParsers.activity),
        HistoryTypeSpec(HistoryDataType.SLEEP_RESULT, 2, HistoryParsers.sleepResult),
        HistoryTypeSpec(HistoryDataType.HRV_RMSSD, 3, HistoryParsers.hrvRmssd),
        HistoryTypeSpec(HistoryDataType.REST_HR, 4, HistoryParsers.restHrRealtime),
        HistoryTypeSpec(HistoryDataType.RESTING_HEART_RATE, 4, HistoryParsers.restingHr),
        HistoryTypeSpec(HistoryDataType.SPO2_SINGLE, 5, HistoryParsers.spo2Single),
        HistoryTypeSpec(HistoryDataType.SPO2_OSA, 5, HistoryParsers.spo2Sleep),
        HistoryTypeSpec(HistoryDataType.STRESS_ALL_DAY, 6, HistoryParsers.stressAllDay),
        // Zepp fetches single stress once per sync (02 §1.6).
        HistoryTypeSpec(HistoryDataType.STRESS_SINGLE, 6, HistoryParsers.stressSingle, loops = false),
        HistoryTypeSpec(HistoryDataType.SLEEP_BREATH_RATE, 7, HistoryParsers.sleepBreathRate),
        HistoryTypeSpec(HistoryDataType.SEC_HR_V2, 8, HistoryParsers.secondHrV2),
        HistoryTypeSpec(HistoryDataType.SEC_HR, 8, HistoryParsers.secondHr),
        HistoryTypeSpec(HistoryDataType.TODAY_ACTIVITY_EXTENDED, 9, HistoryParsers.todayActivityExtended),
        HistoryTypeSpec(HistoryDataType.HRV_SDNN, 10, HistoryParsers.hrvSdnn),
        HistoryTypeSpec(HistoryDataType.PAI, 11, HistoryParsers.pai),
        HistoryTypeSpec(HistoryDataType.BODY_TEMPERATURE_ALL_DAY, 11, HistoryParsers.skinTemperature),
        // One workout per START. The detail is fetched right after its summary with the same cursor (04 §3.16).
        HistoryTypeSpec(HistoryDataType.SPORT_SUMMARY, 12, WorkoutParsers.summary, followedBy = HistoryDataType.SPORT_DETAIL),
        HistoryTypeSpec(HistoryDataType.SPORT_DETAIL, 12, WorkoutParsers.detail),
        // Zepp does one START per sync for sport effect (04 §3).
        HistoryTypeSpec(HistoryDataType.SPORT_EFFECT, 12, WorkoutParsers.sportEffect, loops = false),
        HistoryTypeSpec(HistoryDataType.RECOVERY_HEART_RATE, 13, HistoryParsers.recoveryHr),
        HistoryTypeSpec(HistoryDataType.DAILY_MAX_EXERCISE_HR, 14, HistoryParsers.dailyMaxHr),
        HistoryTypeSpec(HistoryDataType.SPO2_OSA_EVENT, 15, HistoryParsers.spo2OsaEvent),
        HistoryTypeSpec(HistoryDataType.OSA_RESULT, 15, HistoryParsers.osaResult),
        // Not in the recommended list; parsers exist for completeness.
        HistoryTypeSpec(HistoryDataType.HEART_RATE_MANUAL, 16, HistoryParsers.spotHr),
        HistoryTypeSpec(HistoryDataType.SLEEP_RESPIRATORY_RATE_EVENT, 17, HistoryParsers.sleepRespiratoryEvent),
        HistoryTypeSpec(HistoryDataType.SPO2_OSA_EVENT_ALT, 18, HistoryParsers.spo2OsaEventAlt),
        HistoryTypeSpec(HistoryDataType.TEMPERATURE_LEGACY, 19, HistoryParsers.legacyTemperature),
    ).sortedBy { it.syncOrder }

    private val byCode = all.associateBy { it.code }

    fun forCode(code: Int): HistoryTypeSpec? = byCode[code]

    private val followers = all.mapNotNull { it.followedBy }.toSet()

    /**
     * Specs to run, gated on the device's advertised type set (from QUERY_VERSION) when known. Types that are
     * fetched together with another ([HistoryTypeSpec.followedBy]) don't run on their own, and types Zepp never
     * fetches from the strap ([StrapRelevance.NOT_APPLICABLE]) don't run even when advertised.
     *
     * With [features] (0x0028, codemap 01 §3), a type's [HistoryDataType.featureBit] stands in for a missing
     * advertised list, the way Zepp's job gates use it (04 §2.2): set runs the type, clear skips it. An advertised
     * list still wins over a clear bit, because the strap's reply has never been captured and a misread bitmap
     * mustn't stop sleep or HRV sync; [featureDisagreements] lists those cases for the log.
     */
    fun plan(advertised: Set<Int>?, features: DeviceFeatures? = null): List<HistoryTypeSpec> =
        all.filter { spec ->
            val gate = spec.type.featureBit?.let { features?.group2(it) }
            advertised?.contains(spec.code) ?: when (gate) {
                null -> spec.type.strap == StrapRelevance.EXPECTED
                else -> gate
            }
        }.filter { it.type !in followers && it.type.strap != StrapRelevance.NOT_APPLICABLE }

    /** Advertised types whose runtime feature bit is clear: Zepp would skip them, we still fetch them. */
    fun featureDisagreements(advertised: Set<Int>?, features: DeviceFeatures?): List<Int> =
        all.filter { spec -> advertised?.contains(spec.code) == true && spec.type.featureBit?.let { features?.group2(it) } == false }
            .map { it.code }

    /**
     * Loop rule (02 §1.6, §5.2): continue with [next] only if it is later than [current].
     * A null/older/equal cursor ends the loop for this type.
     */
    fun shouldContinue(spec: HistoryTypeSpec, current: Long, next: Long?): Boolean =
        spec.loops && next != null && next > current
}
