package org.openstrap.protocol.history

import org.openstrap.protocol.ByteWriter
import org.openstrap.protocol.hexToBytes
import org.openstrap.protocol.toHex
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.zip.CRC32

class FetchCodecTest {

    @Test
    fun simpleCommands() {
        assertEquals("00", FetchCodec.queryVersion().toHex())
        assertEquals("02", FetchCodec.transfer().toHex())
        assertEquals("03", FetchCodec.ack().toHex())
        assertEquals("0301", FetchCodec.ack(1).toHex())
        assertEquals("05", FetchCodec.queryDataStatus().toHex())
    }

    @Test
    fun startFetchWithDstOffset() {
        // Berlin summer time: UTC+2 -> 8 quarter-hours.
        val t = ZonedDateTime.of(2026, 7, 15, 13, 45, 30, 0, ZoneId.of("Europe/Berlin"))
        val cmd = FetchCodec.startFetch(HistoryDataType.ACTIVITY, t)
        assertEquals("01" + "01" + "ea07" + "07" + "0f" + "0d" + "2d" + "1e" + "08" + "01", cmd.toHex())
    }

    @Test
    fun startFetchWinterAndNegativeAndHalfHourOffsets() {
        val winter = FetchCodec.startFetch(HistoryDataType.SLEEP_RESULT, ZonedDateTime.of(2026, 1, 2, 0, 0, 0, 0, ZoneId.of("Europe/Berlin")), flag = null)
        assertEquals(10, winter.size)
        assertEquals(4, winter[9].toInt())
        assertEquals(72, winter[1].toInt())

        val ny = FetchCodec.startFetch(HistoryDataType.PAI, ZonedDateTime.of(2026, 1, 2, 0, 0, 0, 0, ZoneId.of("America/New_York")))
        assertEquals(-20, ny[9].toInt()) // UTC-5
        val india = FetchCodec.startFetch(HistoryDataType.PAI, ZonedDateTime.of(2026, 1, 2, 0, 0, 0, 0, ZoneId.of("Asia/Kolkata")))
        assertEquals(22, india[9].toInt()) // UTC+5:30
    }

    @Test
    fun startFetchFromEpochMatchesZoned() {
        val zone = ZoneId.of("Europe/Berlin")
        val zdt = ZonedDateTime.of(2026, 3, 29, 3, 30, 0, 0, zone) // right after DST switch
        assertArrayEquals(
            FetchCodec.startFetch(HistoryDataType.ACTIVITY, zdt),
            FetchCodec.startFetch(HistoryDataType.ACTIVITY, zdt.toEpochSecond(), zone),
        )
    }

    @Test
    fun gdspTimeRoundTrip() {
        val zdt = ZonedDateTime.of(2026, 10, 16, 23, 59, 1, 0, ZoneId.of("America/Los_Angeles"))
        val t = GdspTime.of(zdt)
        assertEquals(-28, t.tzQuarters)
        assertEquals(zdt.toEpochSecond(), t.toEpochSecond())
    }

    @Test
    fun parseStartReply() {
        val bytes = ByteWriter().u8(0x10).u8(0x01).u8(0x01).u32(1234)
            .u16(2026).u8(10).u8(16).u8(8).u8(0).u8(0).u8(8).u8(0x01).toByteArray()
        val msg = FetchCodec.parse(bytes) as GdspMessage.StartReply
        assertTrue(msg.isSuccess)
        assertEquals(1234L, msg.size)
        assertEquals(ZonedDateTime.of(2026, 10, 16, 8, 0, 0, 0, ZoneId.of("Europe/Berlin")).toEpochSecond(), msg.firstRecordEpoch)
        assertEquals(1, msg.expansion.size)
        assertEquals(1, msg.completionHint)
    }

    @Test
    fun parseStartReplyEmptyAndErrors() {
        val empty = "1001 01 00000000 ea07 0a 10 08 00 00 08".hexToBytes()
        val e = FetchCodec.parse(empty) as GdspMessage.StartReply
        assertEquals(0L, e.size)
        assertEquals(1, e.completionHint) // no expansion

        val busy = FetchCodec.parse("100107".hexToBytes()) as GdspMessage.StartReply
        assertFalse(busy.isSuccess)
        assertEquals(GdspStatus.BUSY, busy.status)
        assertNull(busy.firstRecordTime)

        assertTrue(FetchCodec.parse("10010100".hexToBytes()) is GdspMessage.Malformed)
        assertTrue(FetchCodec.parse("10".hexToBytes()) is GdspMessage.Malformed)
        assertTrue(FetchCodec.parse(ByteArray(0)) is GdspMessage.Malformed)
        assertTrue(FetchCodec.parse("99".hexToBytes()) is GdspMessage.Malformed)
    }

    @Test
    fun parseTransferCompleteAckAndVersion() {
        val done = FetchCodec.parse("100201".hexToBytes()) as GdspMessage.TransferComplete
        assertTrue(done.isSuccess)
        assertNull(done.crc32)
        val withCrc = FetchCodec.parse("100201 78563412".hexToBytes()) as GdspMessage.TransferComplete
        assertEquals(0x12345678L, withCrc.crc32)

        val ack = FetchCodec.parse("100301".hexToBytes()) as GdspMessage.AckReply
        assertTrue(ack.isSuccess)

        val ver = FetchCodec.parse("100001 03 01 00 01 48 49".hexToBytes()) as GdspMessage.VersionReply
        assertEquals(3, ver.version)
        assertEquals(true, ver.guessSupportsDataUpload())
        assertEquals(false, ver.guessSupportsQueryDataStatus())
        assertEquals(setOf(1, 72, 73), ver.guessSupportedTypes())

        val other = FetchCodec.parse("10050e".hexToBytes()) as GdspMessage.OtherReply
        assertEquals(GdspStatus.DATA_SIZE_MISMATCH, other.status)
        assertEquals(GdspStatus.UNKNOWN, GdspStatus.fromCode(200))
    }

    @Test
    fun parseDataAvailable() {
        val n = FetchCodec.parse("1105".hexToBytes()) as GdspMessage.DataAvailable
        assertEquals(HistoryDataType.SPORT_SUMMARY, n.type)
        assertEquals(listOf(5, 6, 79), n.typesToSync)
        assertEquals(listOf(70), (FetchCodec.parse("1146".hexToBytes()) as GdspMessage.DataAvailable).typesToSync)
        assertTrue(FetchCodec.parse("11".hexToBytes()) is GdspMessage.Malformed)
    }

    // ---- assembler ----

    @Test
    fun assemblerConcatenatesAndWraps() {
        val a = FetchAssembler(6)
        assertEquals(FetchAssembler.State.Collecting, a.accept("ff0102".hexToBytes()))
        assertEquals(FetchAssembler.State.Collecting, a.accept("000304".hexToBytes())) // 255 -> 0 wrap
        assertEquals(FetchAssembler.State.Complete, a.accept("010506".hexToBytes()))
        val r = a.finish() as FetchAssembler.Result.Complete
        assertEquals("010203040506", r.payload.toHex())
        val crc = CRC32().apply { update(r.payload) }.value
        assertEquals(true, a.crcMatches(crc))
        assertEquals(false, a.crcMatches(crc xor 1))
        assertNull(a.crcMatches(null))
    }

    @Test
    fun assemblerDetectsGap() {
        val a = FetchAssembler(10)
        a.accept("0001".hexToBytes())
        val s = a.accept("0202".hexToBytes())
        assertEquals(FetchAssembler.State.Failed(FetchAssembler.Error.IndexGap(1, 2)), s)
        assertTrue(a.finish() is FetchAssembler.Result.Failed)
    }

    @Test
    fun assemblerDetectsShortAndOverflow() {
        val short = FetchAssembler(4)
        short.accept("000102".hexToBytes())
        assertEquals(
            FetchAssembler.Result.Failed(FetchAssembler.Error.SizeMismatch(2, 4)),
            short.finish(),
        )
        val over = FetchAssembler(2)
        assertTrue(over.accept("00010203".hexToBytes()) is FetchAssembler.State.Failed)
        val late = FetchAssembler(1)
        late.accept("0001".hexToBytes())
        assertTrue(late.accept("0102".hexToBytes()) is FetchAssembler.State.Failed)
        assertTrue(FetchAssembler(5).accept(ByteArray(0)) is FetchAssembler.State.Failed)
        assertTrue(FetchAssembler(0).finish() is FetchAssembler.Result.Complete)
    }

    // ---- type table / specs ----

    @Test
    fun dataTypesAndSpecs() {
        assertEquals(HistoryDataType.SLEEP_RESULT, HistoryDataType.fromCode(72))
        assertNull(HistoryDataType.fromCode(250))
        assertTrue(HistoryDataType.ACTIVITY.usedByStrap)
        assertFalse(HistoryDataType.ECG.usedByStrap)
        assertEquals(HistoryDataType.entries.size, HistoryDataType.entries.map { it.code }.toSet().size)

        val all = HistoryTypeSpecs.all
        assertEquals(HistoryDataType.ACTIVITY, all.first().type)
        assertEquals(all.sortedBy { it.syncOrder }, all)
        assertNull(HistoryTypeSpecs.forCode(5)!!.parser)
        assertEquals(listOf(72, 1).toSet(), HistoryTypeSpecs.plan(setOf(1, 72, 3)).map { it.code }.toSet())
        assertTrue(HistoryTypeSpecs.plan(null).all { it.type.strap == StrapRelevance.EXPECTED })

        val act = HistoryTypeSpecs.forCode(1)!!
        assertTrue(HistoryTypeSpecs.shouldContinue(act, 100, 101))
        assertFalse(HistoryTypeSpecs.shouldContinue(act, 100, 100))
        assertFalse(HistoryTypeSpecs.shouldContinue(act, 100, null))
        assertFalse(HistoryTypeSpecs.shouldContinue(HistoryTypeSpecs.forCode(6)!!, 100, 200))
    }
}
