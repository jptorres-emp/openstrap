package org.openstrap.app.device

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.openstrap.app.ble.GattClient
import org.openstrap.app.ble.StrapUuids
import org.openstrap.app.data.db.ActivityMinute
import org.openstrap.app.data.db.BreathRate
import org.openstrap.app.data.db.HealthDao
import org.openstrap.app.data.db.HeartRateSample
import org.openstrap.app.data.db.HrvSample
import org.openstrap.app.data.db.PaiSample
import org.openstrap.app.data.db.RestingHeartRate
import org.openstrap.app.data.db.SleepSession
import org.openstrap.app.data.db.SkinTemperature
import org.openstrap.app.data.db.SleepStage
import org.openstrap.app.data.db.Spo2Sample
import org.openstrap.app.data.settings.SettingsStore
import org.openstrap.protocol.ChannelSession
import org.openstrap.protocol.history.ActivityLayout
import org.openstrap.protocol.history.ActivitySample
import org.openstrap.protocol.history.FetchCodec
import org.openstrap.protocol.history.GdspMessage
import org.openstrap.protocol.history.HistoryDataType
import org.openstrap.protocol.history.HistoryRecord
import org.openstrap.protocol.history.HistoryTypeSpec
import org.openstrap.protocol.history.HistoryTypeSpecs
import org.openstrap.protocol.history.HrvRmssd
import org.openstrap.protocol.history.PaiRecord
import org.openstrap.protocol.history.ParseContext
import org.openstrap.protocol.history.ParseResult
import org.openstrap.protocol.history.RestHrRealtime
import org.openstrap.protocol.history.SecondHeartRate
import org.openstrap.protocol.history.SkinTemperatureSample
import org.openstrap.protocol.history.SleepBreathRate
import org.openstrap.protocol.history.SleepResult
import org.openstrap.protocol.history.SleepStageCodes
import org.openstrap.protocol.history.SleepStageType
import org.openstrap.protocol.history.HeartRateSentinel
import org.openstrap.protocol.history.SpO2Reading
import org.openstrap.protocol.history.SpotHeartRate
import org.openstrap.protocol.services.Endpoints
import org.openstrap.protocol.toHex
import java.time.Instant
import java.time.ZoneId
import org.openstrap.app.data.db.StressSample as DbStress
import org.openstrap.protocol.history.RestingHeartRate as StrapRestingHr
import org.openstrap.protocol.history.StressSample as StrapStress

/**
 * Pulls history from the strap into Room using the GDSP fetch protocol (docs/protocol/02).
 * Control runs over channel endpoint 0x004B when listed, otherwise HUAMI(0004); data always
 * arrives on HUAMI(0005). Each type keeps its own cursor so an interrupted sync resumes.
 */
class HistorySync(
    private val session: ChannelSession,
    private val gatt: GattClient,
    private val dao: HealthDao,
    private val settings: SettingsStore,
    private val state: MutableStateFlow<SyncState>,
    private val log: (String) -> Unit,
) {
    private val zone = ZoneId.systemDefault()
    private val useChannel = session.services.has(Endpoints.HISTORY)
    private val control = Channel<GdspMessage>(Channel.UNLIMITED)
    private val data = Channel<ByteArray>(Channel.UNLIMITED)

    suspend fun run() = coroutineScope {
        state.value = SyncState.Running("Preparing…")
        repairEarlierSyncs()
        val listeners = launch {
            launch { gatt.notifications.filter { it.characteristic == HISTORY_DATA }.collect { data.send(it.value) } }
            if (useChannel) {
                launch { session.messagesFor(Endpoints.HISTORY).collect { log("gdsp < ${it.toHex()}"); control.send(FetchCodec.parse(it)) } }
            } else {
                launch { gatt.notifications.filter { it.characteristic == HISTORY_CONTROL }.collect { control.send(FetchCodec.parse(it.value)) } }
            }
        }
        try {
            gatt.enableNotifications(StrapUuids.MAIN_SERVICE, HISTORY_DATA)
            if (!useChannel) gatt.enableNotifications(StrapUuids.MAIN_SERVICE, HISTORY_CONTROL)
            log("history: control via ${if (useChannel) "channel 0x004B" else "HUAMI(0004)"}")

            val version = runCatching { command(FetchCodec.queryVersion()) }.getOrNull() as? GdspMessage.VersionReply
            val advertised = version?.supportedTypes()
            log("history version ${version?.version}, strap offers types ${advertised?.sorted()}")

            val plan = HistoryTypeSpecs.plan(advertised).filter { it.parser != null }
            plan.forEachIndexed { i, spec ->
                state.value = SyncState.Running("Syncing ${label(spec.type)} (${i + 1}/${plan.size})")
                runCatching { syncType(spec) }.onFailure { log("history ${spec.type}: $it") }
            }
            state.value = SyncState.Done(System.currentTimeMillis() / 1000)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            log("history failed: $e")
            state.value = SyncState.Failed(e.message ?: "Sync failed")
        } finally {
            listeners.cancel()
        }
    }

    /**
     * Debug aid: fetches every type the strap offers since [fromEpoch] and writes each chunk's payload to
     * [dir] as `<code>_<headerTime>.bin`, without storing records or moving sync cursors.
     */
    suspend fun dumpRaw(fromEpoch: Long, dir: java.io.File) = coroutineScope {
        dir.deleteRecursively(); dir.mkdirs()
        val listeners = launch {
            launch { gatt.notifications.filter { it.characteristic == HISTORY_DATA }.collect { data.send(it.value) } }
            if (useChannel) launch { session.messagesFor(Endpoints.HISTORY).collect { control.send(FetchCodec.parse(it)) } }
            else launch { gatt.notifications.filter { it.characteristic == HISTORY_CONTROL }.collect { control.send(FetchCodec.parse(it.value)) } }
        }
        try {
            gatt.enableNotifications(StrapUuids.MAIN_SERVICE, HISTORY_DATA)
            if (!useChannel) gatt.enableNotifications(StrapUuids.MAIN_SERVICE, HISTORY_CONTROL)
            val version = command(FetchCodec.queryVersion()) as? GdspMessage.VersionReply
            val codes = version?.supportedTypes()?.sorted().orEmpty()
            log("dump: types $codes")
            for (code in codes) {
                state.value = SyncState.Running("Dumping type $code")
                runCatching {
                    var cursor = fromEpoch
                    repeat(DUMP_CHUNKS) {
                        val start = command(FetchCodec.startFetch(code, org.openstrap.protocol.history.GdspTime.of(cursor, zone)))
                        if (start !is GdspMessage.StartReply || !start.isSuccess) { log("dump $code: start ${start.status}"); return@runCatching }
                        if (start.size == 0L) { finish(ok = true); log("dump $code: empty"); return@runCatching }
                        val header = start.firstRecordEpoch ?: cursor
                        val (wire, _) = transfer() ?: run { finish(ok = false); return@runCatching }
                        finish(ok = true)
                        val payload = if (wire.size.toLong() != start.size && isZlib(wire)) inflate(wire) else wire
                        java.io.File(dir, "${code}_$header.bin").writeBytes(payload)
                        log("dump $code: ${payload.size} bytes, size field ${start.size}, from ${Instant.ofEpochSecond(header)}")
                        val spec = HistoryTypeSpecs.forCode(code)
                        val parsed = spec?.parser?.parse(payload, ParseContext(header, activityRecordSize = recordSize(code, payload, start.size)))
                        val next = (parsed as? ParseResult.Success)?.nextCursor
                        if (next == null || next <= cursor || spec?.loops == false) return@runCatching
                        cursor = next
                    }
                }.onFailure { log("dump $code: $it") }
            }
            state.value = SyncState.Done(System.currentTimeMillis() / 1000)
            log("dump: done")
        } finally {
            listeners.cancel()
        }
    }

    private suspend fun send(bytes: ByteArray) {
        if (useChannel) session.send(Endpoints.HISTORY, bytes)
        else gatt.write(StrapUuids.MAIN_SERVICE, HISTORY_CONTROL, bytes, withResponse = true)
    }

    /** Sends a command and waits for its `10 <op>` response. */
    private suspend fun command(bytes: ByteArray, timeoutMs: Long = 10_000): GdspMessage.Response {
        val op = bytes[0].toInt() and 0xFF
        send(bytes)
        return withTimeout(timeoutMs) {
            while (true) {
                val m = control.receive()
                if (m is GdspMessage.Response && m.opcode == op) return@withTimeout m
                if (m is GdspMessage.DataAvailable) log("history: device has new data of type ${m.typeCode}")
            }
            @Suppress("UNREACHABLE_CODE") error("unreachable")
        }
    }

    /**
     * Undo damage from earlier builds: 0xFF padding stored as 255-step minutes, stale buffer slots stored
     * with dates weeks in the future, and sync cursors that those pushed past the present.
     */
    private suspend fun repairEarlierSyncs() {
        val now = System.currentTimeMillis() / 1000
        val padding = dao.deletePaddingActivity()
        val outside = dao.deleteActivityOutside(MIN_VALID_EPOCH, now + FUTURE_SLACK)
        if (padding + outside > 0) log("history: removed $padding padding and $outside misdated activity rows")
        // Earlier builds stored the whole mode byte; the mode is its low nibble.
        val modes = dao.fixActivityModeNibble()
        if (modes > 0) log("history: fixed the mode of $modes activity rows")
        for (type in HistoryDataType.entries) {
            val key = "gdsp_${type.code}"
            val cursor = settings.lastSync(key) ?: continue
            val activityRepaired = type == HistoryDataType.ACTIVITY && padding + outside > 0
            if (cursor <= now + FUTURE_SLACK && !activityRepaired) continue
            val reset = if (type == HistoryDataType.ACTIVITY) {
                dao.latestActivity(now)?.plus(60) ?: (now - DEFAULT_LOOKBACK)
            } else {
                now - DEFAULT_LOOKBACK
            }
            log("history: cursor for $type was ${Instant.ofEpochSecond(cursor)}, reset to ${Instant.ofEpochSecond(reset)}")
            settings.setLastSync(key, reset)
        }
    }

    private suspend fun syncType(spec: HistoryTypeSpec) {
        val key = "gdsp_${spec.code}"
        var cursor = settings.lastSync(key) ?: (System.currentTimeMillis() / 1000 - DEFAULT_LOOKBACK)
        repeat(MAX_CHUNKS_PER_TYPE) {
            val start = command(FetchCodec.startFetch(spec.type, cursor, zone))
            if (start !is GdspMessage.StartReply || !start.isSuccess) {
                log("history ${spec.type}: start refused (${start.status})")
                return
            }
            // Every START must be closed with an ACK, even an empty one; otherwise the strap keeps the type
            // open and refuses other types with INVALID_STATE (seen on hardware).
            if (start.size == 0L) {
                finish(ok = true)
                return
            }
            val headerTime = start.firstRecordEpoch ?: cursor
            val transferred = transfer()
            if (transferred == null) {
                finish(ok = false)
                return
            }
            val (wire, deviceCrc) = transferred
            if (deviceCrc != null && deviceCrc != org.openstrap.protocol.crypto.crc32(wire)) {
                log("history ${spec.type}: CRC mismatch, discarding")
                finish(ok = false)
                return
            }
            // Some types (SEC_HR_V2) arrive zlib-compressed; the size field is then the inflated byte count.
            val payload = if (wire.size.toLong() != start.size && isZlib(wire)) {
                runCatching { inflate(wire) }.onFailure { log("history ${spec.type}: inflate failed: $it") }.getOrNull()
                    ?: run { finish(ok = false); return }
            } else wire

            // The size field counts records, not bytes (activity: size 4 = four 8-byte minutes).
            val unit = when {
                payload.size.toLong() == start.size -> 1
                payload.size % start.size == 0L -> (payload.size / start.size).toInt()
                else -> {
                    log("history ${spec.type}: got ${payload.size} bytes for size ${start.size}, discarding")
                    finish(ok = false)
                    return
                }
            }
            log("history ${spec.type}: ${payload.size} bytes (${start.size}×$unit) from ${Instant.ofEpochSecond(headerTime)}: ${payload.copyOf(minOf(32, payload.size)).toHex()}")

            when (val result = spec.parser!!.parse(payload, ParseContext(headerTime, activityRecordSize = recordSize(spec.code, payload, start.size)))) {
                is ParseResult.Failure -> {
                    log("history ${spec.type}: parse failed: ${result.reason}")
                    finish(ok = false)
                    return
                }
                is ParseResult.Success -> {
                    // Drop records with impossible times (stale buffer slots, data from before the clock was set).
                    val now = System.currentTimeMillis() / 1000
                    val kept = result.records.filter { it.timestamp in MIN_VALID_EPOCH..now + FUTURE_SLACK }
                    val dropped = result.records.size - kept.size
                    log("history ${spec.type}: ${kept.size} records" + if (dropped > 0) " ($dropped with impossible times dropped)" else "")
                    store(kept)
                    // Only acknowledge once the data is safely in the database.
                    finish(ok = true)
                    // A chunk of only gap slots still moves the cursor past them (activity), or the type stalls.
                    val next = result.nextCursor
                        ?.let { if (kept.isEmpty()) it.takeIf { result.records.isEmpty() } else it.coerceAtMost(kept.maxOf { r -> r.timestamp } + 60) }
                        ?.coerceAtMost(now)
                    if (next != null && next > cursor) settings.setLastSync(key, next)
                    if (!HistoryTypeSpecs.shouldContinue(spec, cursor, next)) return
                    cursor = next!!
                }
            }
        }
    }

    /**
     * Closes the current fetch. `03 01` after data we stored; `03 00` when we could not use it.
     * The bare `03` was ignored by the strap (it then timed out with status 0x32).
     */
    private suspend fun finish(ok: Boolean) {
        send(FetchCodec.ack(if (ok) 1 else 0))
        val reply = withTimeoutOrNull(5_000) {
            while (true) {
                val m = control.receive()
                if (m is GdspMessage.Response && (m.opcode == 0x03 || m.opcode == 0x02)) return@withTimeoutOrNull m
            }
            @Suppress("UNREACHABLE_CODE") null
        }
        log("history: ack ${if (ok) 1 else 0} -> ${reply?.let { "%02x %s".format(it.opcode, it.status) } ?: "no reply"}")
    }

    /** TRANSFER, then collect indexed packets from 0005 until `10 02`. Returns the payload without index bytes. */
    private suspend fun transfer(): Pair<ByteArray, Long?>? {
        while (data.tryReceive().isSuccess) Unit // drop stale packets
        val out = java.io.ByteArrayOutputStream()
        var lastIndex = -1
        send(FetchCodec.transfer())
        var complete: GdspMessage.TransferComplete? = null
        while (complete == null) {
            val got = withTimeoutOrNull(NO_DATA_TIMEOUT_MS) {
                kotlinx.coroutines.selects.select<Any> {
                    data.onReceive { it }
                    control.onReceive { it }
                }
            } ?: run { log("history: no data for 20 s"); return null }
            when (got) {
                is ByteArray -> if (got.isNotEmpty()) {
                    val index = got[0].toInt() and 0xFF
                    if (lastIndex >= 0 && index != (lastIndex + 1) and 0xFF) log("history: packet gap $lastIndex -> $index")
                    lastIndex = index
                    out.write(got, 1, got.size - 1)
                }
                is GdspMessage.TransferComplete -> complete = got
                is GdspMessage.DataAvailable -> Unit
                else -> log("history: unexpected during transfer: $got")
            }
        }
        // Packets can trail the end marker slightly; drain briefly.
        withTimeoutOrNull(300) {
            while (true) data.receive().let { if (it.isNotEmpty()) out.write(it, 1, it.size - 1) }
        }
        if (!complete!!.isSuccess) {
            log("history: transfer ended with ${complete!!.status}")
            return null
        }
        return out.toByteArray() to complete!!.crc32
    }

    private suspend fun store(records: List<HistoryRecord>) {
        val activity = mutableListOf<ActivityMinute>()
        val hr = mutableListOf<HeartRateSample>()
        val perSecond = mutableMapOf<Long, MutableList<Int>>()
        val spo2 = mutableListOf<Spo2Sample>()
        val stress = mutableListOf<DbStress>()
        val hrv = mutableListOf<HrvSample>()
        val resting = mutableListOf<RestingHeartRate>()
        val restingFromPai = mutableListOf<RestingHeartRate>()
        val skin = mutableListOf<SkinTemperature>()
        val breath = mutableListOf<BreathRate>()
        val pai = mutableListOf<PaiSample>()
        for (r in records) when (r) {
            is ActivitySample -> activity += ActivityMinute(r.timestamp, r.steps, r.intensity, r.mode, r.heartRate)
            // Per-second HR is averaged per minute to keep the database small.
            is SecondHeartRate -> r.heartRate?.let { perSecond.getOrPut(r.timestamp / 60 * 60) { mutableListOf() } += it }
            is SpotHeartRate -> r.heartRate?.let { hr += HeartRateSample(r.timestamp, it) }
            is SpO2Reading -> if (r.isPlausible) spo2 += Spo2Sample(r.timestamp, r.spo2)
            is StrapStress -> if (!r.isLikelyGap) stress += DbStress(r.timestamp, r.stress)
            is HrvRmssd -> if (r.rmssd > 0) hrv += HrvSample(r.timestamp, r.rmssd)
            is StrapRestingHr -> if (r.heartRate in 30..120) resting += RestingHeartRate(dayStart(r.timestamp), r.heartRate)
            is RestHrRealtime -> if (r.realtimeRestHr in 30..120) resting += RestingHeartRate(dayStart(r.timestamp), r.realtimeRestHr)
            is SleepResult -> storeSleep(r)
            is SkinTemperatureSample -> r.skinCelsius?.let { skin += SkinTemperature(r.timestamp, it) }
            is SleepBreathRate -> if (r.breathRate in 4..40) breath += BreathRate(r.timestamp, r.breathRate)
            is PaiRecord -> {
                pai += PaiSample(r.timestamp, r.dailyPai, r.totalPai, r.restHr, r.maxHr)
                // The strap's resting-HR profile; used only for days without a real resting value.
                if (r.restHr in 30..120) restingFromPai += RestingHeartRate(dayStart(r.timestamp), r.restHr)
            }
            else -> Unit
        }
        perSecond.forEach { (minute, values) -> hr += HeartRateSample(minute, values.average().toInt()) }
        if (activity.isNotEmpty()) dao.insertActivity(activity)
        if (hr.isNotEmpty()) dao.insertHeartRate(hr)
        if (spo2.isNotEmpty()) dao.insertSpo2(spo2)
        if (stress.isNotEmpty()) dao.insertStress(stress)
        if (hrv.isNotEmpty()) dao.insertHrv(hrv)
        if (resting.isNotEmpty()) dao.insertRestingHr(resting)
        if (restingFromPai.isNotEmpty()) dao.insertRestingHrIfMissing(restingFromPai)
        if (skin.isNotEmpty()) dao.insertSkinTemperature(skin)
        if (breath.isNotEmpty()) dao.insertBreathRate(breath)
        if (pai.isNotEmpty()) dao.insertPai(pai)
    }

    /**
     * Stores a day's sleep result (02 §3.3): the main sleep with the strap's own score and totals, and each
     * nap. The strap re-sends a day as it updates it, so the day's earlier version is replaced.
     */
    private suspend fun storeSleep(r: SleepResult) {
        val day = r.day(zone)
        val main = r.mainStages.filter { it.rawType in SleepStageCodes.VALID }
        if (main.isNotEmpty()) {
            val start = r.resolveTime(main.first().start, zone)
            val end = r.resolveTime(r.mainSleepEndFixed.takeIf { it > main.first().start } ?: main.last().stop, zone)
            if (end <= start || end - start > 20 * 3600) {
                log("history: implausible sleep window $start..$end, skipped")
            } else {
                dao.deleteSleepStagesForDay(day, nap = false)
                dao.deleteSleepSessionsForDay(day, nap = false)
                dao.insertSleepSessions(listOf(SleepSession(
                    start, end, day, nap = false,
                    score = r.sleepScore.takeIf { it in 1..100 },
                    averageHeartRate = HeartRateSentinel.valid(r.averageHeartRate),
                    deepMinutes = r.deepSleepLength, lightMinutes = r.lightSleepLength,
                    remMinutes = r.remSleepLength.takeIf { r.isRemSupported }, awakeMinutes = r.wakeLength,
                    wakeCount = r.wakeCount,
                )))
                dao.insertSleepStages(main.map { stage(it, start, r) })
            }
        }
        if (r.shortStages.isNotEmpty()) {
            dao.deleteSleepStagesForDay(day, nap = true)
            dao.deleteSleepSessionsForDay(day, nap = true)
            for (nap in r.shortStages) {
                val start = r.resolveTime(nap.start, zone)
                val end = r.resolveTime(nap.stop, zone)
                if (end <= start) continue
                dao.insertSleepSessions(listOf(SleepSession(start, end, day, nap = true)))
                dao.insertSleepStages(listOf(stage(nap, start, r)))
            }
        }
    }

    private fun stage(s: org.openstrap.protocol.history.SleepStage, sessionStart: Long, r: SleepResult) = SleepStage(
        start = r.resolveTime(s.start, zone),
        end = r.resolveTime(s.stop, zone),
        sessionStart = sessionStart,
        stage = when (s.type) {
            SleepStageType.AWAKE -> SleepStage.AWAKE
            SleepStageType.DEEP -> SleepStage.DEEP
            SleepStageType.REM -> SleepStage.REM
            SleepStageType.LIGHT, SleepStageType.UNKNOWN -> SleepStage.LIGHT
        },
    )

    /** Activity record size: the START size field counts records, so bytes ÷ records gives it. */
    private fun recordSize(code: Int, payload: ByteArray, sizeField: Long): Int {
        if (code != HistoryDataType.ACTIVITY.code) return 4
        val unit = if (sizeField > 0 && payload.size % sizeField == 0L) (payload.size / sizeField).toInt() else 0
        return if (unit == 4 || unit == 8) unit else ActivityLayout.detect(payload)
    }

    private fun dayStart(epoch: Long) = Instant.ofEpochSecond(epoch).atZone(zone).toLocalDate().atStartOfDay(zone).toEpochSecond()

    private fun label(t: HistoryDataType) = t.name.lowercase().replace('_', ' ')

    companion object {
        private val HISTORY_CONTROL = java.util.UUID.fromString("00000004-0000-3512-2118-0009af100700")
        private val HISTORY_DATA = java.util.UUID.fromString("00000005-0000-3512-2118-0009af100700")
        private const val DEFAULT_LOOKBACK = 2L * 24 * 3600
        /** 2024-01-01: anything older comes from a strap whose clock was never set. */
        private const val MIN_VALID_EPOCH = 1_704_067_200L
        private const val FUTURE_SLACK = 120L
        private const val NO_DATA_TIMEOUT_MS = 20_000L
        private const val MAX_CHUNKS_PER_TYPE = 50
        private const val DUMP_CHUNKS = 5
    }
}

private fun isZlib(b: ByteArray): Boolean =
    b.size >= 2 && (b[0].toInt() and 0x0F) == 8 && ((b[0].toInt() and 0xFF) * 256 + (b[1].toInt() and 0xFF)) % 31 == 0

private fun inflate(b: ByteArray): ByteArray {
    val inflater = java.util.zip.Inflater()
    try {
        inflater.setInput(b)
        val out = java.io.ByteArrayOutputStream(b.size * 8)
        val buf = ByteArray(4096)
        while (!inflater.finished()) {
            val n = inflater.inflate(buf)
            if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) error("truncated zlib stream")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    } finally {
        inflater.end()
    }
}
