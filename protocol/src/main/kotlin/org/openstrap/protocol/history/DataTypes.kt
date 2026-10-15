package org.openstrap.protocol.history

/**
 * How relevant a GDSP data type is for the Helio Strap (spec 02 §2, "Strap" column).
 */
enum class StrapRelevance {
    /** Strap-relevant and expected. */
    EXPECTED,

    /** Only fetched if the device lists the type in its GDSP `dataTypeMap`. */
    IF_ADVERTISED,

    /** Not applicable (needs a screen, GPS, ECG, scale, ...). */
    NOT_APPLICABLE,
}

/** GDSP one-byte data-type codes (spec 02 §2). */
enum class HistoryDataType(val code: Int, val strap: StrapRelevance) {
    ACTIVITY(1, StrapRelevance.EXPECTED),
    HEART_RATE_MANUAL(2, StrapRelevance.IF_ADVERTISED),
    ECG(3, StrapRelevance.NOT_APPLICABLE),
    TEMPERATURE_LEGACY(4, StrapRelevance.IF_ADVERTISED),
    SPORT_SUMMARY(5, StrapRelevance.EXPECTED),
    SPORT_DETAIL(6, StrapRelevance.EXPECTED),
    LOG(7, StrapRelevance.NOT_APPLICABLE),
    PPG_RR(8, StrapRelevance.NOT_APPLICABLE),
    AF_PPG(11, StrapRelevance.NOT_APPLICABLE),
    PAI(13, StrapRelevance.EXPECTED),
    SPORT_SUMMARY_ALT(14, StrapRelevance.NOT_APPLICABLE),
    AF_RESULT(17, StrapRelevance.NOT_APPLICABLE),
    STRESS_SINGLE(18, StrapRelevance.EXPECTED),
    STRESS_ALL_DAY(19, StrapRelevance.EXPECTED),
    AF_ACC(30, StrapRelevance.NOT_APPLICABLE),
    SPO2_SINGLE(37, StrapRelevance.EXPECTED),
    SPO2_OSA(38, StrapRelevance.EXPECTED),
    SPO2_OSA_EVENT(39, StrapRelevance.IF_ADVERTISED),
    SPO2_OSA_EVENT_ALT(43, StrapRelevance.IF_ADVERTISED),
    DEVICE_STATISTIC(44, StrapRelevance.NOT_APPLICABLE),

    // UNVERIFIED: 02 §2/§3.10 - sync-time keys exist but no GDSP job/parser was found.
    BODY_TEMPERATURE_ALL_DAY(46, StrapRelevance.IF_ADVERTISED),
    BODY_TEMPERATURE_SINGLE(47, StrapRelevance.IF_ADVERTISED),
    BODY_TEMPERATURE_80S(48, StrapRelevance.IF_ADVERTISED),

    BLOOD_PRESSURE_52(52, StrapRelevance.NOT_APPLICABLE),
    BLOOD_PRESSURE_53(53, StrapRelevance.NOT_APPLICABLE),
    BLOOD_PRESSURE_54(54, StrapRelevance.NOT_APPLICABLE),
    BLOOD_PRESSURE_55(55, StrapRelevance.NOT_APPLICABLE),
    SLEEP_BREATH_RATE(56, StrapRelevance.EXPECTED),
    SLEEP_RESPIRATORY_RATE_EVENT(57, StrapRelevance.IF_ADVERTISED),
    RESTING_HEART_RATE(58, StrapRelevance.EXPECTED),
    SPORT_EFFECT(59, StrapRelevance.IF_ADVERTISED),
    DAILY_MAX_EXERCISE_HR(61, StrapRelevance.IF_ADVERTISED),
    BLOOD_PRESSURE_62(62, StrapRelevance.NOT_APPLICABLE),
    SLEEP_PLAN(66, StrapRelevance.IF_ADVERTISED),
    RECOVERY_HEART_RATE(70, StrapRelevance.EXPECTED),
    HLD(71, StrapRelevance.NOT_APPLICABLE),
    SLEEP_RESULT(72, StrapRelevance.EXPECTED),
    HRV_RMSSD(73, StrapRelevance.EXPECTED),
    OSA_RESULT(74, StrapRelevance.IF_ADVERTISED),
    JS_WEIGHT_V1(75, StrapRelevance.NOT_APPLICABLE),
    SEC_HR(76, StrapRelevance.IF_ADVERTISED),
    JS_WEIGHT_V2(77, StrapRelevance.NOT_APPLICABLE),
    HRV_SDNN(78, StrapRelevance.IF_ADVERTISED),
    SPORT_VOICE_RECORD(79, StrapRelevance.NOT_APPLICABLE),
    HLD_COMPOSITION(82, StrapRelevance.NOT_APPLICABLE),
    EDA_RAW_DATA(84, StrapRelevance.IF_ADVERTISED),
    REST_HR(86, StrapRelevance.IF_ADVERTISED),
    SEC_HR_V2(87, StrapRelevance.IF_ADVERTISED),
    TODAY_ACTIVITY_EXTENDED(88, StrapRelevance.IF_ADVERTISED),
    SOLAR_INTENSITY(89, StrapRelevance.NOT_APPLICABLE),
    LIFELOAD_AUDIO(96, StrapRelevance.NOT_APPLICABLE),
    ;

    val usedByStrap: Boolean get() = strap != StrapRelevance.NOT_APPLICABLE

    companion object {
        private val byCode = entries.associateBy { it.code }
        fun fromCode(code: Int): HistoryDataType? = byCode[code]
    }
}
