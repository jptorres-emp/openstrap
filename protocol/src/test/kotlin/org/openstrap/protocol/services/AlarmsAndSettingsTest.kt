package org.openstrap.protocol.services

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openstrap.protocol.hexToBytes

/** Replies captured from a Helio Strap (fw 3.25.0.3) on 2026-10-16. */
class AlarmsAndSettingsTest {

    @Test
    fun alarmVersion() {
        val caps = AlarmCodec.parseVersion("02030a000000".hexToBytes())!!
        assertEquals(AlarmCapabilities(version = 3, maxCount = 10, titles = false, smartSupported = false), caps)
    }

    @Test
    fun alarmList() {
        val alarm = AlarmCodec.parseList("0a010004072d000000000100".hexToBytes(), titles = false)!!.single()
        assertEquals(Alarm(index = 4, hour = 7, minute = 45, days = 0, enabled = false, smart = false, flags = 0, source = 1), alarm)
    }

    @Test
    fun alarmWrites() {
        val a = Alarm(index = 1, hour = 7, minute = 30, days = Alarm.WEEKDAYS, enabled = true)
        assertArrayEquals("03010401071e1f0000000100".hexToBytes(), AlarmCodec.add(a, titles = false))
        assertArrayEquals("07010001071e1f0000000100".hexToBytes(), AlarmCodec.modify(a.copy(enabled = false), titles = false))
        assertArrayEquals("050101".hexToBytes(), AlarmCodec.delete(1))
        val req = AlarmCodec.add(a, titles = false)
        assertTrue(AlarmCodec.isSuccess(req, "0401".hexToBytes()))
        assertFalse(AlarmCodec.isSuccess(req, "0402".hexToBytes()))
    }

    private val health = ("04010803011d0110fe0600fe01050a1e0210000700646e78828c960310000400282d32040b00830b01110b01120b01130b01" +
        "140b01160b00181000060001050a0f1e310b0132105a040050555a410b014230080043301500440b0145300c0046300e00510b0152" +
        "0310270000d00700003075000053012c01c800b80b5403b036000058020000a08c00005501e0010000a00556010c00060010005701" +
        "1e0005005a00711000020001210b00220b00").hexToBytes()

    @Test
    fun healthGroup() {
        val g = SettingsCodec.parse(health)!!
        assertEquals(8, g.group); assertEquals(3, g.version); assertEquals(29, g.settings.size)
        val interval = g[SettingsCodec.KEY_HR_INTERVAL]!!
        assertEquals(0xFEL, interval.number); assertEquals(listOf(0, 0xFE, 1, 5, 10, 30), interval.choices)
        assertEquals(listOf(0, 100, 110, 120, 130, 140, 150), g[SettingsCodec.KEY_HIGH_HR_ALERT]!!.choices)
        assertEquals(0L, g[SettingsCodec.KEY_HIGH_HR_ALERT]!!.number)
        assertEquals(listOf(0, 40, 45, 50), g[SettingsCodec.KEY_LOW_HR_ALERT]!!.choices)
        assertEquals(false, g[SettingsCodec.KEY_ACTIVITY_HR]!!.bool)
        assertEquals(true, g[SettingsCodec.KEY_STRESS]!!.bool)
        assertEquals(90L, g[SettingsCodec.KEY_LOW_SPO2_ALERT]!!.number)
        assertEquals(8 * 60L, g[0x42]!!.number) // stand reminder from 08:00
        assertEquals(10_000L, g[0x52]!!.number) // step goal
    }

    @Test
    fun settingsWrite() {
        val req = SettingsCodec.write(8, 3, listOf(SettingsCodec.enumSetting(SettingsCodec.KEY_HIGH_HR_ALERT, 120)))
        assertArrayEquals("05080300010210 78".replace(" ", "").hexToBytes(), req)
        assertTrue(SettingsCodec.isWriteSuccess("0601".hexToBytes()))
        assertFalse(SettingsCodec.isWriteSuccess("0604".hexToBytes()))
        assertArrayEquals("030108020203".hexToBytes(), SettingsCodec.query(8, listOf(2, 3)))
    }

    @Test
    fun settingsVersion() {
        assertArrayEquals("01".hexToBytes(), SettingsCodec.versionRequest)
        assertEquals(SettingsProtocol(3, emptyList()), SettingsCodec.parseVersion("0203".hexToBytes()))
        // v4+ appends one flag per group after the group ids.
        assertEquals(SettingsProtocol(4, listOf(8, 10)), SettingsCodec.parseVersion("020402080a0100".hexToBytes()))
        assertNull(SettingsCodec.parseVersion("0402".hexToBytes()))
        assertEquals(listOf(0, 0, 0, 1, 2, 3, 3), listOf(null, 2, 3, 4, 5, 6, 9).map(SettingsCodec::flagBytes))
    }

    @Test
    fun flagBytesFromProtocol4() {
        // HR interval enum 10 with choices [0, 10] and flag 0; stress bool on with flag 1 (disabled).
        val v4 = "04010803010201100a02000a00130b0101".hexToBytes()
        val g = SettingsCodec.parse(v4, protocolVersion = 4)!!
        assertEquals(10L, g[SettingsCodec.KEY_HR_INTERVAL]!!.number)
        assertEquals(listOf(0, 10), g[SettingsCodec.KEY_HR_INTERVAL]!!.choices)
        assertEquals(listOf(0), g[SettingsCodec.KEY_HR_INTERVAL]!!.flags)
        assertEquals(true, g[SettingsCodec.KEY_STRESS]!!.bool)
        assertEquals(listOf(1), g[SettingsCodec.KEY_STRESS]!!.flags)
        // Read as v1-3, the flag byte is taken for the next key and the second entry is lost.
        assertNull(SettingsCodec.parse(v4)?.get(SettingsCodec.KEY_STRESS))

        // v6: three flag bytes per value, also without options.
        val v6 = "040108030002010b01000001130b00010000".hexToBytes()
        val g6 = SettingsCodec.parse(v6, protocolVersion = 6)!!
        assertEquals(listOf(0, 0, 1), g6[1]!!.flags)
        assertEquals(false, g6[SettingsCodec.KEY_STRESS]!!.bool)
        assertEquals(listOf(1, 0, 0), g6[SettingsCodec.KEY_STRESS]!!.flags)
        // Writes never carry flags.
        assertArrayEquals("050803000113 0b01".replace(" ", "").hexToBytes(), SettingsCodec.write(8, 3, listOf(g6[SettingsCodec.KEY_STRESS]!!.copy(raw = byteArrayOf(1)))))
    }

    @Test
    fun failedQuery() {
        assertNull(SettingsCodec.parse("0403".hexToBytes()))
        assertEquals(3, SettingsCodec.replyCode("0403".hexToBytes()))
    }
}

class ReadinessCodecTest {
    @Test
    fun scoreFromStrap() {
        val reply = "090001c030d16aa893d16a10043e420104035bffff59645664ffffffffff7fff000080bf0000001fffffffffffffff".hexToBytes()
        val r = ReadinessCodec.parseScores(reply)!!.single()
        assertEquals(1_792_094_400L, r.day)
        assertEquals(91, r.score)
        assertEquals(89, r.hrvScore); assertEquals(86, r.restingHrScore); assertEquals(100, r.skinTemperatureScore); assertEquals(100, r.breathingScore)
        assertNull(r.physicalScore); assertNull(r.mentalScore)
        assertEquals(62, r.sleepHrv); assertEquals(66, r.sleepRestingHr)
        assertEquals("4.3", r.algorithmVersion)
    }

    @Test
    fun recordsAre44BytesApart() {
        val record = "c030d16aa893d16a10043e420104035bffff59645664ffffffffff7fff000080bf0000001fffffffffffffff".hexToBytes()
        assertEquals(44, record.size)
        val second = record.copyOf().also { it[0] = 0x40; it[15] = 77 } // another day, score 77
        val scores = ReadinessCodec.parseScores("090002".hexToBytes() + record + second)!!
        assertEquals(listOf(91, 77), scores.map { it.score })
        assertEquals(listOf(1_792_094_400L, 1_792_094_272L), scores.map { it.day })
    }

    @Test
    fun noData() {
        assertEquals(emptyList<StrapReadiness>(), ReadinessCodec.parseScores("0902".hexToBytes() + byteArrayOf(0)))
        assertNull(ReadinessCodec.parseScores("0d0000".hexToBytes()))
        assertArrayEquals("0846d9aa6a".hexToBytes(), ReadinessCodec.scoreRequest(0x6aaad946))
    }
}
