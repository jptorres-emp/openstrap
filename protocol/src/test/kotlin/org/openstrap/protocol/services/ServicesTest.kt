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

class RealtimeStepsTest {
    @org.junit.Test
    fun stepCountThatContradictsDistanceIsImplausible() {
        // Real reply after the 3.25.0.3 update: 8969 steps but 42 m and 5 kcal.
        val after = RealtimeSteps.parseMessage("04010c092300002a00000005000000".hexToBytes())!!
        org.junit.Assert.assertEquals(8969, after.steps)
        org.junit.Assert.assertFalse(after.isPlausible)
        org.junit.Assert.assertTrue(StepTotals(57, 42, 5).isPlausible)
        org.junit.Assert.assertTrue(StepTotals(8969, 6200, 300).isPlausible)
        org.junit.Assert.assertTrue(StepTotals(8969, null, null).isPlausible)
    }

    @org.junit.Test
    fun parsesQueryReplyAndPush() {
        // flags 0x0C: steps 1234, distance 900 m, 45 kcal.
        val reply = "04 01 0c d2040000 84030000 2d000000".replace(" ", "").hexToBytes()
        org.junit.Assert.assertEquals(StepTotals(1234, 900, 45), RealtimeSteps.parseMessage(reply))
        // flags 0x01 (running u32 present) without distance.
        val push = "03 01 64000000 05000000".replace(" ", "").hexToBytes()
        org.junit.Assert.assertEquals(StepTotals(100, null, null), RealtimeSteps.parseMessage(push))
        org.junit.Assert.assertNull(RealtimeSteps.parseMessage("0601".hexToBytes()))
        org.junit.Assert.assertNull(RealtimeSteps.parseMessage("0405".hexToBytes()))
    }
}
