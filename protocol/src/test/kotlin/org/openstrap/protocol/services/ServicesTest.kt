package org.openstrap.protocol.services

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openstrap.protocol.hexToBytes
import java.time.ZoneId
import java.time.ZonedDateTime

class ServicesTest {
    @Test
    fun parsesServiceList() {
        val list = ServiceList.parse("04 0300 2900 00 4700 01 8200 00".hexToBytes())
        assertTrue(list.has(Endpoints.BATTERY))
        assertTrue(list.requiresEncryption(Endpoints.TIME))
        assertFalse(list.requiresEncryption(Endpoints.AUTH))
    }

    @Test
    fun parsesChannelVersion() {
        assertEquals(ChannelVersion(2, true), ChannelVersion.parse("020201".hexToBytes()))
        assertEquals(ChannelVersion(2, null), ChannelVersion.parse("0202".hexToBytes()))
    }

    @Test
    fun parsesBattery() {
        assertEquals(BatteryInfo(83, true), BatteryInfo.parseChannel("04 01 53 01".hexToBytes()))
        assertEquals(BatteryInfo(40, null), BatteryInfo.parse("00 28".hexToBytes()))
    }

    @Test
    fun encodesTimeStruct() {
        // 2026-10-16 09:30:15 in Berlin (UTC+2, DST): offset 8 quarter-hours, DST flag 0x08, Friday = 5.
        val t = ZonedDateTime.of(2026, 10, 16, 9, 30, 15, 0, ZoneId.of("Europe/Berlin"))
        assertArrayEquals("ea07 0a 10 09 1e 0f 05 00 08 08".hexToBytes(), TimeSync.struct(t))
    }

    @Test
    fun encodesUserProfile() {
        val p = UserProfile(1990, 4, 2, Gender.MALE, 180, 75)
        val legacy = p.legacyPayload()
        assertEquals(16, legacy.size)
        assertEquals(0x4F, legacy[0].toInt())
        assertEquals(75 * 200, (legacy[10].toInt() and 0xFF) or ((legacy[11].toInt() and 0xFF) shl 8))
    }

    @Test
    fun parsesHeartRate() {
        assertEquals(72, LiveHeartRate.parsePush("05 4800".hexToBytes()))
        assertEquals(64, LiveHeartRate.parseMeasurement("00 40".hexToBytes()))
        assertEquals(300 - 250, (LiveHeartRate.parseMeasurement("01 2c01".hexToBytes()) ?: 50))
    }
}
