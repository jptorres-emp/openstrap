package org.openstrap.protocol.services

import org.openstrap.protocol.ByteReader
import org.openstrap.protocol.ByteWriter
import org.openstrap.protocol.u8

/**
 * One setting from the strap's settings module (0x000A, Zepp `gm40`/`emj`). [raw] is the value as sent
 * (without the type byte) so any type can be written back unchanged; [number] is set for numeric types.
 * [choices] are the allowed values the strap offers for enum settings.
 */
data class Setting(val key: Int, val type: Int, val raw: ByteArray, val number: Long?, val choices: List<Int>? = null) {
    val bool: Boolean? get() = if (type == SettingsCodec.TYPE_BOOL) number != 0L else null

    override fun equals(other: Any?) = other is Setting && key == other.key && type == other.type &&
        raw.contentEquals(other.raw) && choices == other.choices
    override fun hashCode() = key * 31 + raw.contentHashCode()
}

/** A group as read: its version (needed for writes) and settings by key. */
data class SettingsGroup(val group: Int, val version: Int, val settings: Map<Int, Setting>) {
    operator fun get(key: Int): Setting? = settings[key]
}

/**
 * Settings module commands (version 2+). Query `03 withOptions group n keys…` (n = 0 reads the whole group),
 * reply `04 code group grpVer withOptions n {key type value [options]}`; write `05 group grpVer 00 n {key type
 * value}` with keys ascending, answered `06 01` (zj40.k, yk40.java:776). Integers are little-endian.
 */
object SettingsCodec {
    const val QUERY = 0x03
    const val WRITE = 0x05

    const val TYPE_I16 = 0x01
    const val TYPE_I16_LIST = 0x02
    const val TYPE_I32 = 0x03
    const val TYPE_I32_LIST = 0x04
    const val TYPE_BOOL = 0x0B
    const val TYPE_ENUM = 0x10
    const val TYPE_MULTI = 0x11
    const val TYPE_STRING = 0x20
    const val TYPE_STRING_ENUM = 0x21
    const val TYPE_TIME = 0x30
    const val TYPE_I64 = 0x40
    const val TYPE_I32_B = 0x50

    /** Health group and its keys (kel.java:62-110). */
    const val GROUP_HEALTH = 0x08
    const val KEY_HR_INTERVAL = 0x01
    const val KEY_HIGH_HR_ALERT = 0x02
    const val KEY_LOW_HR_ALERT = 0x03
    const val KEY_ACTIVITY_HR = 0x04
    const val KEY_STRESS = 0x13
    const val KEY_SMART_SPO2 = 0x31
    const val KEY_LOW_SPO2_ALERT = 0x32

    /** HR interval value for continuous measuring; 0 is off, other values are minutes. */
    const val HR_CONTINUOUS = 0xFE

    fun query(group: Int, keys: List<Int> = emptyList(), withOptions: Boolean = true): ByteArray =
        ByteWriter().u8(QUERY).u8(if (withOptions) 1 else 0).u8(group).u8(keys.size).apply { keys.forEach { u8(it) } }.toByteArray()

    /** Result code of a query reply: 1 is success (a630.y). */
    fun replyCode(reply: ByteArray): Int? = if (reply.size >= 2 && reply.u8(0) == QUERY + 1) reply.u8(1) else null

    fun parse(reply: ByteArray): SettingsGroup? {
        if (replyCode(reply) != 1 || reply.size < 6) return null
        val r = ByteReader(reply, 2)
        val group = r.u8(); val version = r.u8(); val options = r.u8() != 0; val n = r.u8()
        val out = LinkedHashMap<Int, Setting>()
        repeat(n) {
            val key = r.u8(); val type = r.u8()
            val start = r.pos
            val number: Long? = when (type) {
                TYPE_I16 -> r.u16().toShort().toLong()
                TYPE_I32, TYPE_I32_B -> r.u32().toInt().toLong()
                TYPE_BOOL, TYPE_ENUM -> r.u8().toLong()
                TYPE_I64 -> r.u32() or (r.u32() shl 32)
                TYPE_TIME -> { val h = r.u8(); val m = r.u8(); h * 60L + m }
                TYPE_I16_LIST -> { r.bytes(r.u8() * 2); null }
                TYPE_I32_LIST -> { r.bytes(r.u8() * 4); null }
                TYPE_MULTI -> { r.bytes(r.u8()); null }
                TYPE_STRING, TYPE_STRING_ENUM -> { cString(r); null }
                else -> return null // an unknown type: the rest can't be framed
            }
            val raw = reply.copyOfRange(start, r.pos)
            var choices: List<Int>? = null
            if (options) when (type) {
                TYPE_I16 -> r.bytes(4)
                TYPE_I32 -> r.bytes(8)
                TYPE_ENUM -> choices = List(r.u8()) { r.u8() }
                TYPE_MULTI -> r.bytes(r.u8())
                TYPE_I16_LIST -> r.bytes(6)
                TYPE_I32_LIST -> r.bytes(10)
                TYPE_STRING -> r.bytes(1)
                TYPE_STRING_ENUM -> { r.bytes(1); repeat(r.u8()) { cString(r) } }
                else -> Unit
            }
            out[key] = Setting(key, type, raw, number, choices)
        }
        return SettingsGroup(group, version, out)
    }

    /** Writes [settings] (key → type + raw value) to [group]; [version] is the group version from the last read. */
    fun write(group: Int, version: Int, settings: List<Setting>): ByteArray {
        val w = ByteWriter().u8(WRITE).u8(group).u8(version).u8(0).u8(settings.size)
        settings.sortedBy { it.key }.forEach { w.u8(it.key).u8(it.type).bytes(it.raw) }
        return w.toByteArray()
    }

    fun enumSetting(key: Int, value: Int) = Setting(key, TYPE_ENUM, byteArrayOf(value.toByte()), value.toLong())
    fun boolSetting(key: Int, on: Boolean) = Setting(key, TYPE_BOOL, byteArrayOf(if (on) 1 else 0), if (on) 1 else 0)

    fun isWriteSuccess(reply: ByteArray): Boolean = reply.size == 2 && reply.u8(0) == WRITE + 1 && reply.u8(1) == 1

    private fun cString(r: ByteReader) { while (r.u8() != 0) Unit }
}
