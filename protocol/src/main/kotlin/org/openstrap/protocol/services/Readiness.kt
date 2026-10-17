package org.openstrap.protocol.services

import org.openstrap.protocol.ByteReader
import org.openstrap.protocol.ByteWriter
import org.openstrap.protocol.u8

/**
 * The strap's own morning readiness (module 0x0049, Zepp `pt00`/`xt00`). [day] is local midnight of the day it
 * scores. Sub-scores are 0..100, or null where the strap left them unset (0xFF). The record has sub-scores for
 * HRV, resting HR, skin temperature and breathing disturbances, but none for sleep.
 */
data class StrapReadiness(
    val day: Long,
    val updatedAt: Long,
    val tzQuarters: Int,
    val status: Int,
    val score: Int?,
    val hrvScore: Int?,
    val restingHrScore: Int?,
    val skinTemperatureScore: Int?,
    val breathingScore: Int?,
    val physicalScore: Int?,
    val mentalScore: Int?,
    /** Night HRV (ms) and resting HR (bpm) the strap scored from. */
    val sleepHrv: Int?,
    val sleepRestingHr: Int?,
    val algorithmVersion: String,
    val insightId: Int,
)

/**
 * Readiness commands. Read-only here: `00` version, `08 <since u32>` score; replies are `<cmd+1> status …`
 * with status 0 = OK and 2 = no data. Commands 02, 04 and 0E write data to the strap and are not used.
 */
object ReadinessCodec {
    const val VERSION = 0x00
    const val SCORE = 0x08
    private const val SCORE_RECORD = 44

    val versionRequest: ByteArray = byteArrayOf(VERSION.toByte())

    /** The strap answers with its latest score whatever [since] is (seen on fw 3.25.0.3). */
    fun scoreRequest(since: Long): ByteArray = ByteWriter().u8(SCORE).u32(since).toByteArray()

    /** `09 status n` then n 44-byte records (isk.java:142-157, writer ia20.java:238-271). Null if not a score reply. */
    fun parseScores(reply: ByteArray): List<StrapReadiness>? {
        if (reply.size < 3 || reply.u8(0) != SCORE + 1) return null
        if (reply.u8(1) != 0) return emptyList()
        val r = ByteReader(reply, 3)
        val out = ArrayList<StrapReadiness>()
        repeat(reply.u8(2)) {
            if (r.remaining < SCORE_RECORD) return out
            val ts = r.u32(); val updated = r.u32(); val tz = r.s8()
            val status = r.u8(); val hrv = r.u8(); val rhr = r.u8(); val insight = r.u8()
            val alg = r.u8(); val algSub = r.u8()
            val score = r.u8(); val phy = r.u8(); val ment = r.u8(); val hrvS = r.u8(); val skinS = r.u8(); val rhrS = r.u8(); val ahiS = r.u8()
            r.bytes(SCORE_RECORD - 22) // afib score, baselines and insight codes: unused
            out += StrapReadiness(
                day = ts, updatedAt = updated, tzQuarters = tz, status = status,
                score = pct(score), hrvScore = pct(hrvS), restingHrScore = pct(rhrS), skinTemperatureScore = pct(skinS),
                breathingScore = pct(ahiS), physicalScore = pct(phy), mentalScore = pct(ment),
                sleepHrv = hrv.takeIf { it in 1..254 }, sleepRestingHr = rhr.takeIf { it in 25..220 },
                algorithmVersion = "$alg.$algSub", insightId = insight,
            )
        }
        return out
    }

    private fun pct(v: Int): Int? = v.takeIf { it in 0..100 }
}
