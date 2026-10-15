package org.openstrap.app.device

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.openstrap.app.ble.GattClient
import org.openstrap.app.ble.GattStrapLink
import org.openstrap.app.ble.StrapUuids
import org.openstrap.app.data.db.HealthDao
import org.openstrap.app.data.db.HeartRateSample
import org.openstrap.app.data.settings.SettingsStore
import org.openstrap.protocol.ChannelSession
import org.openstrap.protocol.auth.AuthException
import org.openstrap.protocol.hexToBytes
import org.openstrap.protocol.services.BatteryInfo
import org.openstrap.protocol.services.Endpoints
import org.openstrap.protocol.services.LiveHeartRate
import org.openstrap.protocol.services.TimeSync
import org.openstrap.protocol.toHex
import org.openstrap.protocol.u8
import java.time.ZonedDateTime

/**
 * Owns the connection to the strap: GATT, the channel session and everything built on it.
 * Every step is logged to [log] so behaviour on real hardware can be checked against the spec.
 */
class StrapManager(
    private val context: Context,
    private val settings: SettingsStore,
    private val dao: HealthDao,
) : StrapController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val connection = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val sync = MutableStateFlow<SyncState>(SyncState.Idle)
    override val liveHeartRate = MutableStateFlow<Int?>(null)
    override val log = MutableStateFlow<List<String>>(emptyList())

    private var connectJob: Job? = null
    private var liveJob: Job? = null
    private var gatt: GattClient? = null
    private var session: ChannelSession? = null
    private var link: GattStrapLink? = null
    private var battery: Int? = null
    private var firmware: String? = null

    /** The connected session, for features such as history sync and firmware update. */
    val activeSession: ChannelSession? get() = session?.takeIf { connection.value is ConnectionState.Connected }

    fun log(message: String) {
        Log.i("OpenStrap", message)
        val line = "%tT  %s".format(System.currentTimeMillis(), message)
        log.update { (it + line).takeLast(400) }
    }

    override fun connect() {
        connectJob?.cancel()
        connectJob = scope.launch {
            val device = settings.pairedDevice.first()
            if (device == null) {
                connection.value = ConnectionState.NotPaired
                return@launch
            }
            StrapService.start(context)
            connection.value = ConnectionState.Connecting
            val client = GattClient(context, device.address).also { gatt = it }
            try {
                log("connecting to ${device.address}")
                client.connect()
                log("connected, services discovered, mtu=${client.mtu}")
                watchDisconnect(client)

                val l = GattStrapLink(client).also { link = it }
                l.enableChannelNotifications()
                val s = ChannelSession(l, ::log).also { session = it }
                s.start(this)

                connection.value = ConnectionState.Authenticating
                s.handshake(device.authKeyHex.hexToBytes())

                afterAuth(client, l, s)
                connection.value = ConnectionState.Connected(battery, firmware)
                watchBattery(s)
            } catch (e: AuthException) {
                log("auth failed: ${e.message}")
                connection.value = ConnectionState.Failed(e.message ?: "Authentication failed")
                close()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                log("connect failed: $e")
                connection.value = ConnectionState.Failed(e.message ?: "Connection failed")
                close()
            }
        }
    }

    private suspend fun afterAuth(client: GattClient, l: GattStrapLink, s: ChannelSession) {
        // Payload MTU reported by the strap (§1.3).
        if (s.services.has(Endpoints.CONNECT)) {
            runCatching {
                val r = s.request(Endpoints.CONNECT, byteArrayOf(1), 5_000) { it.u8(0) == 0x02 }
                val n = r.u8(1) or (r.u8(2) shl 8)
                if (n + 3 in 23..247) l.payloadMtu = n
                log("strap payload mtu=$n")
            }.onFailure { log("mtu query failed: $it") }
        }

        firmware = runCatching {
            client.read(StrapUuids.DEVICE_INFO_SERVICE, StrapUuids.SOFTWARE_REVISION).toString(Charsets.UTF_8).trimEnd('\u0000')
        }.getOrNull()
        log("firmware=$firmware")

        battery = runCatching {
            if (s.services.has(Endpoints.BATTERY)) {
                BatteryInfo.parseChannel(s.request(Endpoints.BATTERY, BatteryInfo.GET) { it.u8(0) == 0x04 })?.level
            } else {
                BatteryInfo.parse(client.read(StrapUuids.MAIN_SERVICE, StrapUuids.BATTERY)).level
            }
        }.onFailure { log("battery read failed: $it") }.getOrNull()
        log("battery=$battery")

        runCatching {
            val now = ZonedDateTime.now()
            if (s.services.has(Endpoints.TIME)) {
                val r = s.request(Endpoints.TIME, TimeSync.setRequest(now)) { it.u8(0) == 0x06 }
                log("time set: ${r.toHex()}")
            } else {
                client.write(StrapUuids.MAIN_SERVICE, StrapUuids.CURRENT_TIME, TimeSync.struct(now), withResponse = true)
                log("time set via 2A2B")
            }
        }.onFailure { log("time sync failed: $it") }
    }

    private fun watchBattery(s: ChannelSession) = scope.launch {
        s.messagesFor(Endpoints.BATTERY).collect { payload ->
            BatteryInfo.parseChannel(payload)?.let { info ->
                battery = info.level
                (connection.value as? ConnectionState.Connected)?.let { connection.value = it.copy(batteryPercent = info.level) }
            }
        }
    }

    private fun watchDisconnect(client: GattClient) = scope.launch {
        client.connected.filter { !it }.first()
        if (gatt === client) {
            log("link lost")
            liveJob?.cancel()
            liveHeartRate.value = null
            session?.stop()
            session = null
            if (connection.value is ConnectionState.Connected) connection.value = ConnectionState.Disconnected
        }
    }

    override fun disconnect() {
        connectJob?.cancel()
        liveJob?.cancel()
        close()
        liveHeartRate.value = null
        connection.value = ConnectionState.Disconnected
    }

    private fun close() {
        session?.stop()
        session = null
        gatt?.close()
        gatt = null
    }

    override fun syncNow() {
        val s = activeSession ?: return
        val client = gatt ?: return
        scope.launch { HistorySync(s, client, dao, settings, sync, ::log).run() }
    }

    override fun setLiveHeartRate(enabled: Boolean) {
        liveJob?.cancel()
        liveHeartRate.value = null
        if (!enabled) {
            val s = session
            val client = gatt
            scope.launch { stopLive(s, client) }
            return
        }
        val s = activeSession ?: return
        val client = gatt ?: return
        liveJob = scope.launch {
            try {
                // Collect from both the channel push and the standard 2A37 characteristic; de-duplicate by second.
                launch { s.messagesFor(Endpoints.HEART_RATE).collect { p -> LiveHeartRate.parsePush(p)?.let { onLiveHr(it) } } }
                launch {
                    client.notifications.filter { it.characteristic == StrapUuids.HR_MEASUREMENT }
                        .collect { n -> LiveHeartRate.parseMeasurement(n.value)?.let { onLiveHr(it) } }
                }
                runCatching { client.enableNotifications(StrapUuids.HEART_RATE_SERVICE, StrapUuids.HR_MEASUREMENT) }
                    .onFailure { log("2A37 subscribe failed: $it") }

                val channel = s.services.has(Endpoints.HEART_RATE)
                if (channel) {
                    log("live hr open: ${s.request(Endpoints.OPEN_APP, LiveHeartRate.OPEN, 5_000) { it.u8(0) == 0x05 }.toHex()}")
                    log("live hr subscribe: ${s.request(Endpoints.HEART_RATE, LiveHeartRate.SUBSCRIBE, 5_000) { it.u8(0) == 0x04 }.toHex()}")
                } else {
                    client.write(StrapUuids.HEART_RATE_SERVICE, StrapUuids.HR_CONTROL, LiveHeartRate.LEGACY_START, withResponse = true)
                    log("live hr started via 2A39")
                }
                while (isActive) {
                    delay(LiveHeartRate.KEEP_ALIVE_MS)
                    if (channel) s.send(Endpoints.OPEN_APP, LiveHeartRate.KEEP_ALIVE)
                    else client.write(StrapUuids.HEART_RATE_SERVICE, StrapUuids.HR_CONTROL, LiveHeartRate.LEGACY_KEEP_ALIVE, withResponse = true)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                log("live hr failed: $e")
                liveHeartRate.value = null
            }
        }
    }

    private suspend fun stopLive(s: ChannelSession?, client: GattClient?) {
        runCatching {
            if (s != null && s.services.has(Endpoints.HEART_RATE)) {
                s.send(Endpoints.OPEN_APP, LiveHeartRate.CLOSE)
                s.send(Endpoints.HEART_RATE, LiveHeartRate.UNSUBSCRIBE)
            } else {
                client?.write(StrapUuids.HEART_RATE_SERVICE, StrapUuids.HR_CONTROL, LiveHeartRate.LEGACY_STOP, withResponse = true)
            }
        }.onFailure { log("live hr stop failed: $it") }
    }

    private var lastLiveSecond = 0L
    private suspend fun onLiveHr(bpm: Int) {
        liveHeartRate.value = bpm
        val now = System.currentTimeMillis() / 1000
        if (now != lastLiveSecond) {
            lastLiveSecond = now
            dao.insertHeartRate(listOf(HeartRateSample(now, bpm, HeartRateSample.SOURCE_LIVE)))
        }
    }
}
