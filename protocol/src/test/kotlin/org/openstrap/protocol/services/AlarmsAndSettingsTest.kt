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
    fun failedQuery() {
        assertNull(SettingsCodec.parse("0403".hexToBytes()))
        assertEquals(3, SettingsCodec.replyCode("0403".hexToBytes()))
    }
}
