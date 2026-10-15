package org.openstrap.protocol.history

/**
 * How to fetch and decode one GDSP data type.
 *
 * @param syncOrder position in the recommended OpenStrap order (02 §5.1); lower runs first.
 * @param parser null when decoding is not implemented yet (raw payload only).
 * @param loops false for types the engine fetches only once per sync (02 §1.6).
 */
data class HistoryTypeSpec(
    val type: HistoryDataType,
    val syncOrder: Int,
    val parser: HistoryParser<HistoryRecord>?,
    val loops: Boolean = true,
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
        // TODO: workout summary (protobuf, 02 §3.12) and detail (TLV, 02 §3.13) are not decoded
        // yet. Summary 5 and detail 6 must be fetched back to back with the same cursor.
        HistoryTypeSpec(HistoryDataType.SPORT_SUMMARY, 12, null),
        HistoryTypeSpec(HistoryDataType.SPORT_DETAIL, 12, null, loops = false),
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

    /** Specs to run, gated on the device's advertised type set (from QUERY_VERSION) when known. */
    fun plan(advertised: Set<Int>?): List<HistoryTypeSpec> =
        if (advertised == null) all.filter { it.type.strap == StrapRelevance.EXPECTED }
        else all.filter { it.code in advertised }

    /**
     * Loop rule (02 §1.6, §5.2): continue with [next] only if it is later than [current].
     * A null/older/equal cursor ends the loop for this type.
     */
    fun shouldContinue(spec: HistoryTypeSpec, current: Long, next: Long?): Boolean =
        spec.loops && next != null && next > current
}
