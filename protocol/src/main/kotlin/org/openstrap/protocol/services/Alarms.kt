package org.openstrap.protocol.services

import org.openstrap.protocol.ByteReader
import org.openstrap.protocol.ByteWriter
import org.openstrap.protocol.bytesOf
import org.openstrap.protocol.u8

/**
 * One alarm slot on the strap (alarm module 0x000F, Zepp `uo0`). [days] is a bitmask with Monday = 0x01
 * through Sunday = 0x40; 0 rings once. [flags] keeps the bits we don't model (bit1 "lazy") as the strap sent them.
 */
data class Alarm(
    val index: Int,
    val hour: Int,
    val minute: Int,
    val days: Int,
    val enabled: Boolean,
    val smart: Boolean = false,
    val flags: Int = 0,
    val source: Int = 1,
    val title: String? = null,
) {
    init {
        require(hour in 0..23 && minute in 0..59) { "bad time $hour:$minute" }
        require(days in 0..0x7F) { "bad days $days" }
    }

    companion object {
        const val MONDAY = 0x01
        const val EVERY_DAY = 0x7F
        const val WEEKDAYS = 0x1F
        const val WEEKEND = 0x60
    }
}

/** `02 version maxCount titleStatus [workday] [smart]` (uo0.java:634-638). */
data class AlarmCapabilities(val version: Int, val maxCount: Int, val titles: Boolean, val smartSupported: Boolean)

/**
 * Alarm commands. Each write carries one alarm and is answered `<cmd+1> 01` on success (uo0.java:207, 359, 556).
 * Records are `[title\0] flags index hour minute days(u32 LE) source smartWakeup` (uo0.java:91-147).
 */
object AlarmCodec {
    const val VERSION = 0x01
    const val ADD = 0x03
    const val DELETE = 0x05
    const val MODIFY = 0x07
    const val LIST = 0x09
    /** Sent by the strap when its alarms change (e.g. one that rang once switched itself off). */
    const val CHANGED = 0x0F

    private const val FLAG_SMART = 0x01
    private const val FLAG_ENABLED = 0x04

    val versionRequest: ByteArray = bytesOf(VERSION)
    val listRequest: ByteArray = bytesOf(LIST)

    fun parseVersion(reply: ByteArray): AlarmCapabilities? {
        if (reply.size < 4 || reply.u8(0) != VERSION + 1) return null
        val v = reply.u8(1)
        val smart = if (v < 3) true else reply.size > 5 && reply.u8(5) == 1
        return AlarmCapabilities(v, reply.u8(2), reply.u8(3) == 1, smart)
    }

    fun parseList(reply: ByteArray, titles: Boolean): List<Alarm>? {
        if (reply.size < 2 || reply.u8(0) != LIST + 1) return null
        val r = ByteReader(reply, 2)
        return List(reply.u8(1)) {
            val title = if (titles) {
                val bytes = generateSequence { r.u8().takeIf { b -> b != 0 } }.map { b -> b.toByte() }.toList().toByteArray()
                String(bytes, Charsets.UTF_8)
            } else null
            val flags = r.u8()
            val index = r.u8(); val hour = r.u8(); val minute = r.u8()
            val days = r.u32().toInt() and 0x7F
            val source = r.u8(); r.u8() // smart-wakeup minutes, unused
            Alarm(index, hour, minute, days, flags and FLAG_ENABLED != 0, flags and FLAG_SMART != 0, flags, source, title)
        }
    }

    fun add(alarm: Alarm, titles: Boolean): ByteArray = ByteWriter().u8(ADD).u8(1).bytes(record(alarm, titles)).toByteArray()

    fun modify(alarm: Alarm, titles: Boolean): ByteArray = ByteWriter().u8(MODIFY).u8(1).bytes(record(alarm, titles)).toByteArray()

    fun delete(index: Int): ByteArray = bytesOf(DELETE, 1, index)

    /** True for the strap's `<command+1> 01`. */
    fun isSuccess(request: ByteArray, reply: ByteArray): Boolean =
        reply.size == 2 && reply.u8(0) == request.u8(0) + 1 && reply.u8(1) == 1

    private fun record(a: Alarm, titles: Boolean): ByteArray {
        val w = ByteWriter()
        if (titles) w.bytes((a.title ?: "").toByteArray(Charsets.UTF_8)).u8(0)
        val flags = (a.flags and (FLAG_SMART or FLAG_ENABLED).inv()) or
            (if (a.smart) FLAG_SMART else 0) or (if (a.enabled) FLAG_ENABLED else 0)
        // Zepp always writes 0 smart-wakeup minutes (uo0.java:117).
        return w.u8(flags).u8(a.index).u8(a.hour).u8(a.minute).u32(a.days.toLong()).u8(a.source).u8(0).toByteArray()
    }
}
