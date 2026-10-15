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
import org.openstrap.app.data.db.HealthDao
import org.openstrap.app.data.db.HeartRateSample
import org.openstrap.app.data.db.HrvSample
import org.openstrap.app.data.db.RestingHeartRate
import org.openstrap.app.data.db.SleepSession
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
import org.openstrap.protocol.history.ParseContext
import org.openstrap.protocol.history.ParseResult
import org.openstrap.protocol.history.RestHrRealtime
import org.openstrap.protocol.history.SecondHeartRate
import org.openstrap.protocol.history.SleepResult
import org.openstrap.protocol.history.SleepStageType
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

    private suspend fun syncType(spec: HistoryTypeSpec) {
        val key = "gdsp_${spec.code}"
        var cursor = settings.lastSync(key) ?: (System.currentTimeMillis() / 1000 - DEFAULT_LOOKBACK)
        repeat(MAX_CHUNKS_PER_TYPE) {
            val start = command(FetchCodec.startFetch(spec.type, cursor, zone))
            if (start !is GdspMessage.StartReply || !start.isSuccess) {
                log("history ${spec.type}: start refused (${start.status})")
                return
            }
            if (start.size == 0L) return
            val headerTime = start.firstRecordEpoch ?: cursor

            // Always close the fetch with an ACK, even when we reject the data; otherwise the strap stays
            // mid-transfer and refuses every following START with INVALID_STATE (seen on hardware).
            val payload = try {
                transfer()
            } finally {
                runCatching { command(FetchCodec.ack()) }.onFailure { log("history: ack failed: $it") }
            } ?: return

            // The size field counts bytes on some types and records on others (activity: 1 = one 8-byte minute).
            val unit = when {
                payload.size.toLong() == start.size -> 1
                payload.size % start.size == 0L -> (payload.size / start.size).toInt()
                else -> { log("history ${spec.type}: got ${payload.size} bytes for size ${start.size}, skipping"); return }
            }
            log("history ${spec.type}: ${payload.size} bytes (size ${start.size}×$unit) from ${Instant.ofEpochSecond(headerTime)}: ${payload.copyOf(minOf(32, payload.size)).toHex()}")

            val recordSize = when {
                spec.type != HistoryDataType.ACTIVITY -> 4
                unit == 4 || unit == 8 -> unit
                else -> ActivityLayout.detect(payload)
            }
            val result = spec.parser!!.parse(payload, ParseContext(headerTime, activityRecordSize = recordSize))
            when (result) {
                is ParseResult.Failure -> {
                    log("history ${spec.type}: parse failed: ${result.reason}")
                    return
                }
                is ParseResult.Success -> {
                    log("history ${spec.type}: ${result.records.size} records")
                    store(result.records)
                    val next = result.nextCursor
                    if (next != null && next > cursor) settings.setLastSync(key, next)
                    if (!HistoryTypeSpecs.shouldContinue(spec, cursor, next)) return
                    cursor = next!!
                }
            }
        }
    }

    /** TRANSFER, then collect indexed packets from 0005 until `10 02`. Returns the payload without index bytes. */
    private suspend fun transfer(): ByteArray? {
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
        return out.toByteArray()
    }

    private suspend fun store(records: List<HistoryRecord>) {
        val activity = mutableListOf<ActivityMinute>()
        val hr = mutableListOf<HeartRateSample>()
        val perSecond = mutableMapOf<Long, MutableList<Int>>()
        val spo2 = mutableListOf<Spo2Sample>()
        val stress = mutableListOf<DbStress>()
        val hrv = mutableListOf<HrvSample>()
        val resting = mutableListOf<RestingHeartRate>()
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
            else -> Unit
        }
        perSecond.forEach { (minute, values) -> hr += HeartRateSample(minute, values.average().toInt()) }
        if (activity.isNotEmpty()) dao.insertActivity(activity)
        if (hr.isNotEmpty()) dao.insertHeartRate(hr)
        if (spo2.isNotEmpty()) dao.insertSpo2(spo2)
        if (stress.isNotEmpty()) dao.insertStress(stress)
        if (hrv.isNotEmpty()) dao.insertHrv(hrv)
        if (resting.isNotEmpty()) dao.insertRestingHr(resting)
    }

    private suspend fun storeSleep(r: SleepResult) {
        if (!r.hasMainSleep) return
        val start = r.resolveTime(r.mainSleepStart)
        val end = r.resolveTime(r.mainSleepEnd)
        if (end <= start || end - start > 20 * 3600) {
            log("history: implausible sleep window $start..$end, skipped")
            return
        }
        dao.insertSleepSessions(listOf(SleepSession(start, end)))
        dao.insertSleepStages(r.mainStages.map {
            SleepStage(
                start = r.resolveTime(it.start),
                end = r.resolveTime(it.stop),
                sessionStart = start,
                stage = when (it.type) {
                    SleepStageType.AWAKE -> SleepStage.AWAKE
                    SleepStageType.LIGHT -> SleepStage.LIGHT
                    SleepStageType.DEEP -> SleepStage.DEEP
                    SleepStageType.REM -> SleepStage.REM
                    SleepStageType.UNKNOWN -> SleepStage.LIGHT
                },
            )
        })
    }

    private fun dayStart(epoch: Long) = Instant.ofEpochSecond(epoch).atZone(zone).toLocalDate().atStartOfDay(zone).toEpochSecond()

    private fun label(t: HistoryDataType) = t.name.lowercase().replace('_', ' ')

    companion object {
        private val HISTORY_CONTROL = java.util.UUID.fromString("00000004-0000-3512-2118-0009af100700")
        private val HISTORY_DATA = java.util.UUID.fromString("00000005-0000-3512-2118-0009af100700")
        private const val DEFAULT_LOOKBACK = 2L * 24 * 3600
        private const val NO_DATA_TIMEOUT_MS = 20_000L
        private const val MAX_CHUNKS_PER_TYPE = 50
    }
}
