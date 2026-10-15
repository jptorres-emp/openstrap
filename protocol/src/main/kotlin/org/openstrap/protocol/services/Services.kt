package org.openstrap.protocol.services

import org.openstrap.protocol.ByteReader
import org.openstrap.protocol.ByteWriter
import org.openstrap.protocol.bytesOf
import org.openstrap.protocol.u8
import java.time.ZonedDateTime

/** Channel endpoint ids (docs/protocol/01 §4). */
object Endpoints {
    const val SERVICES = 0x0000
    const val CONNECT = 0x0015
    const val USER_INFO = 0x0017
    const val OPEN_APP = 0x001D
    const val BATTERY = 0x0029
    const val DEVICE_INFO = 0x0043
    const val TIME = 0x0047
    const val HEART_RATE = 0x0048
    const val HISTORY = 0x004B
    const val FIRMWARE = 0x000C
    const val AUTH = 0x0082
}

/** Supported endpoints and whether each requires encryption (§4). */
data class ServiceList(val endpoints: Map<Int, Boolean>) {
    fun has(endpoint: Int) = endpoint in endpoints
    fun requiresEncryption(endpoint: Int) = endpoints[endpoint] == true

    companion object {
        val REQUEST: ByteArray = bytesOf(0x03)

        /** Parses the v2 reply `04 count(u16) {endpoint u16, requireEncryption bool}*`. */
        fun parse(reply: ByteArray): ServiceList {
            require(reply.isNotEmpty() && reply.u8(0) == 0x04) { "Not a v2 services reply" }
            val r = ByteReader(reply, 1)
            val count = r.u16()
            val map = LinkedHashMap<Int, Boolean>()
            repeat(count) { map[r.u16()] = r.bool() }
            require(r.remaining == 0) { "Trailing bytes in services list" }
            return ServiceList(map)
        }
    }
}

/** Channel version reply on 0016 (§2.1): `02 version [encryptEnable] [supportBT] [featureFlag u16]`. */
data class ChannelVersion(val version: Int, val encryptEnable: Boolean?) {
    companion object {
        val REQUEST: ByteArray = bytesOf(0x01)

        fun parse(reply: ByteArray): ChannelVersion? {
            if (reply.size < 2 || reply.u8(0) != 0x02) return null
            return ChannelVersion(reply.u8(1), if (reply.size >= 3) reply.u8(2) != 0 else null)
        }
    }
}

/** BatteryInfo (§5.1). Only the fields the UI needs. */
data class BatteryInfo(val level: Int, val charging: Boolean?) {
    companion object {
        val GET: ByteArray = bytesOf(0x03)

        /** Parses a raw BatteryInfo (legacy 0006 value, or channel payload after the `04`/`05` byte). */
        fun parse(info: ByteArray): BatteryInfo {
            val flags = info.u8(0)
            val level = info.u8(1)
            val charging = if (flags and 0x01 != 0 && info.size > 2) info.u8(2) in 1..2 else null
            return BatteryInfo(level, charging)
        }

        /** Channel reply or push: `04`/`05` + BatteryInfo. */
        fun parseChannel(payload: ByteArray): BatteryInfo? =
            if (payload.size >= 3 && (payload.u8(0) == 0x04 || payload.u8(0) == 0x05)) parse(payload.copyOfRange(1, payload.size)) else null
    }
}

/** The 11-byte time struct (§5.3). */
object TimeSync {
    fun struct(now: ZonedDateTime): ByteArray {
        val offset = now.offset.totalSeconds
        val dst = now.zone.rules.isDaylightSavings(now.toInstant())
        return ByteWriter()
            .u16(now.year).u8(now.monthValue).u8(now.dayOfMonth)
            .u8(now.hour).u8(now.minute).u8(now.second)
            .u8(now.dayOfWeek.value % 7) // 0 = Sunday
            .u8(now.nano / 1_000_000 / 4)
            .u8(if (dst) 0x08 else 0x00)
            .u8(offset / (15 * 60))
            .toByteArray()
    }

    /** Channel endpoint 0x0047 set request; reply `06 01` on success. */
    fun setRequest(now: ZonedDateTime): ByteArray = bytesOf(0x05) + struct(now)
}

enum class Gender(val code: Int) { MALE(0), FEMALE(1), UNSPECIFIED(2) }

data class UserProfile(
    val birthYear: Int,
    val birthMonth: Int,
    val birthDay: Int,
    val gender: Gender,
    val heightCm: Int,
    val weightKg: Int,
    val userId: Long = 1,
) {
    /** Legacy FEE0/0008 write (§5.5). */
    fun legacyPayload(): ByteArray = ByteWriter()
        .u8(0x4F).u8(0x00).u8(0x00)
        .u16(birthYear).u8(birthMonth).u8(birthDay)
        .u8(gender.code)
        .u16(heightCm).u16(weightKg * 200)
        .u32(userId)
        .toByteArray()

    /** Channel 0x0017 set request with birthday, gender, height and weight (mask bits 0-3). */
    fun channelPayload(): ByteArray = ByteWriter()
        .u8(0x01).u32(0x0F)
        .u16(birthYear).u8(birthMonth).u8(birthDay)
        .u8(gender.code)
        .u16(heightCm)
        .u16(weightKg * 200)
        .toByteArray()
}

/** Real-time heart rate (§5.6). */
object LiveHeartRate {
    // Endpoint 0x001D. UNVERIFIED encoding (§5.6): open 04 02, close 04 01, keep-alive 04 03; reply 05 00.
    val OPEN: ByteArray = bytesOf(0x04, 0x02)
    val CLOSE: ByteArray = bytesOf(0x04, 0x01)
    val KEEP_ALIVE: ByteArray = bytesOf(0x04, 0x03)

    // Endpoint 0x0048.
    val SUBSCRIBE: ByteArray = bytesOf(0x03, 0x01)
    val UNSUBSCRIBE: ByteArray = bytesOf(0x03, 0x00)

    // Legacy 180D/2A39.
    val LEGACY_START: ByteArray = bytesOf(0x15, 0x01, 0x01)
    val LEGACY_STOP: ByteArray = bytesOf(0x15, 0x01, 0x00)
    val LEGACY_KEEP_ALIVE: ByteArray = bytesOf(0x16)

    const val KEEP_ALIVE_MS = 10_000L

    /** Channel push on 0x0048: `05 hr(u16)`. */
    fun parsePush(payload: ByteArray): Int? =
        if (payload.size >= 3 && payload.u8(0) == 0x05) (payload.u8(1) or (payload.u8(2) shl 8)).takeIf { it in 1..250 } else null

    /** Standard BLE HR measurement (2A37). */
    fun parseMeasurement(value: ByteArray): Int? {
        if (value.size < 2) return null
        val wide = value.u8(0) and 0x01 != 0
        val bpm = if (wide && value.size >= 3) value.u8(1) or (value.u8(2) shl 8) else value.u8(1)
        return bpm.takeIf { it in 1..250 }
    }
}
