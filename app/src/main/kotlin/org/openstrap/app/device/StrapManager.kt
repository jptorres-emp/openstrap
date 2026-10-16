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
import org.openstrap.protocol.services.RealtimeSteps
import org.openstrap.protocol.services.StepTotals
import org.openstrap.protocol.crypto.crc32
import org.openstrap.protocol.firmware.DfuCommands
import org.openstrap.protocol.firmware.DfuException
import org.openstrap.protocol.firmware.DfuProgress
import org.openstrap.protocol.firmware.DfuSession
import org.openstrap.app.ble.GattDfuLink
import android.os.PowerManager
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
    override val todaySteps = MutableStateFlow<StepTotals?>(null)
    override val liveHeartRateEnabled = MutableStateFlow(true)
    override val info = MutableStateFlow<StrapInfo?>(null)
    override val firmwareUpdating = MutableStateFlow(false)
    /** Set before a firmware install: the strap's services can change, so re-discover on the next connect. */
    @Volatile private var refreshGattOnConnect = false
    override val log = MutableStateFlow<List<String>>(emptyList())

    private var connectJob: Job? = null
    private var reconnectJob: Job? = null
    /** True while the user wants the strap connected; cleared by [disconnect]. */
    private var wantConnected = false
    private var retries = 0
    private var lastAutoSync = 0L
    private var liveJob: Job? = null
    private var gatt: GattClient? = null
    private var session: ChannelSession? = null
    private var link: GattStrapLink? = null
    private var battery: Int? = null
    private var charging = false
    private var firmware: String? = null

    /** The connected session, for features such as history sync and firmware update. */
    val activeSession: ChannelSession? get() = session?.takeIf { connection.value is ConnectionState.Connected }

    fun log(message: String) {
        Log.i("OpenStrap", message)
        val line = "%tT  %s".format(System.currentTimeMillis(), message)
        log.update { (it + line).takeLast(400) }
    }

    /** Connect if a strap is paired and nothing is connected or in progress. Safe to call often. */
    override fun autoConnect() {
        when (connection.value) {
            is ConnectionState.Connected, ConnectionState.Connecting, ConnectionState.Authenticating -> return
            else -> Unit
        }
        if (reconnectJob?.isActive == true) return
        connect()
    }

    override fun connect() {
        wantConnected = true
        reconnectJob?.cancel()
        startConnect()
    }

    private fun scheduleReconnect() {
        if (!wantConnected) return
        val wait = RETRY_SECONDS[minOf(retries, RETRY_SECONDS.lastIndex)]
        retries++
        log("reconnecting in ${wait}s")
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(wait * 1000L)
            startConnect()
        }
    }

    private fun startConnect() {
        connectJob?.cancel()
        connectJob = scope.launch {
            val device = settings.pairedDevice.first()
            if (device == null) {
                connection.value = ConnectionState.NotPaired
                return@launch
            }
            runCatching { StrapService.start(context) }.onFailure { log("service start failed: $it") }
            connection.value = ConnectionState.Connecting
            val client = GattClient(context, device.address).also { gatt = it }
            try {
                log("connecting to ${device.address}")
                client.connect(refreshCache = refreshGattOnConnect)
                refreshGattOnConnect = false
                log("connected, services discovered, mtu=${client.mtu}")
                watchDisconnect(client)

                val l = GattStrapLink(client).also { link = it }
                l.enableChannelNotifications()
                val s = ChannelSession(l, ::log).also { session = it }
                s.start(this)

                connection.value = ConnectionState.Authenticating
                s.handshake(device.authKeyHex.hexToBytes())

                afterAuth(client, l, s)
                connection.value = ConnectionState.Connected(battery, firmware, charging)
                watchBattery(s)
                watchSteps(s)
                retries = 0
                autoSync(s)
                liveHeartRateEnabled.value = settings.liveHeartRate.first()
                if (liveHeartRateEnabled.value) startLive()
            } catch (e: AuthException) {
                log("auth failed: ${e.message}")
                connection.value = ConnectionState.Failed(e.message ?: "Authentication failed")
                close()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                log("connect failed: $e")
                connection.value = ConnectionState.Failed(e.message ?: "Connection failed")
                close()
                scheduleReconnect()
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
        val hardware = runCatching {
            client.read(StrapUuids.DEVICE_INFO_SERVICE, StrapUuids.HARDWARE_REVISION).toString(Charsets.UTF_8).trimEnd('\u0000')
        }.getOrNull()
        val serial = runCatching {
            client.read(StrapUuids.DEVICE_INFO_SERVICE, StrapUuids.SERIAL_NUMBER).toString(Charsets.UTF_8).trimEnd('\u0000')
        }.getOrNull()
        val dfu = GattDfuLink(client).available
        info.value = StrapInfo(firmware, hardware, serial, dfu)
        log("hardware=$hardware dfu=$dfu")

        runCatching {
            if (s.services.has(Endpoints.BATTERY)) {
                BatteryInfo.parseChannel(s.request(Endpoints.BATTERY, BatteryInfo.GET) { it.u8(0) == 0x04 })
            } else {
                BatteryInfo.parse(client.read(StrapUuids.MAIN_SERVICE, StrapUuids.BATTERY))
            }
        }.onFailure { log("battery read failed: $it") }.getOrNull()?.let {
            battery = it.level
            charging = it.charging == true
        }
        log("battery=$battery charging=$charging")

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
                charging = info.charging == true
                log("battery update ${info.level}% charging=$charging")
                (connection.value as? ConnectionState.Connected)?.let {
                    connection.value = it.copy(batteryPercent = info.level, charging = charging)
                }
            }
        }
    }

    private fun watchSteps(s: ChannelSession) = scope.launch {
        if (!s.services.has(Endpoints.REALTIME_ACTIVITY)) return@launch
        launch {
            s.messagesFor(Endpoints.REALTIME_ACTIVITY).collect { p ->
                val totals = RealtimeSteps.parseMessage(p)
                if (totals == null) log("steps < ${p.toHex()}")
                totals?.let {
                    if (todaySteps.value?.isPlausible != it.isPlausible) {
                        log("steps today=${it.steps} dist=${it.distanceMeters} kcal=${it.calories}" + if (!it.isPlausible) " (total doesn't match distance: counting from synced history)" else "")
                    }
                    todaySteps.value = it
                }
            }
        }
        runCatching { s.send(Endpoints.REALTIME_ACTIVITY, RealtimeSteps.NOTIFY_ON) }.onFailure { log("steps notify failed: $it") }
        // Pushes may only come while walking; poll as a fallback.
        while (isActive && session === s) {
            if (!firmwareUpdating.value) runCatching { s.send(Endpoints.REALTIME_ACTIVITY, RealtimeSteps.QUERY) }.onFailure { log("steps query failed: $it") }
            delay(60_000)
        }
    }

    /** Sync right after connecting (unless one ran recently), then every [AUTO_SYNC_SECONDS] while connected. */
    private fun autoSync(s: ChannelSession) = scope.launch {
        while (isActive && session === s) {
            val now = System.currentTimeMillis() / 1000
            if (now - lastAutoSync >= MIN_SYNC_GAP_SECONDS && sync.value !is SyncState.Running && !firmwareUpdating.value) {
                lastAutoSync = now
                log("auto sync")
                syncNow()
            }
            delay(AUTO_SYNC_SECONDS * 1000L)
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
            if (connection.value is ConnectionState.Connected) {
                if (wantConnected) {
                    // Keep the foreground service up while we try to get the strap back.
                    connection.value = ConnectionState.Connecting
                    scheduleReconnect()
                } else {
                    connection.value = ConnectionState.Disconnected
                }
            }
        }
    }

    override fun disconnect() {
        wantConnected = false
        reconnectJob?.cancel()
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
        if (firmwareUpdating.value) return
        val s = activeSession ?: return
        val client = gatt ?: return
        scope.launch { HistorySync(s, client, dao, settings, sync, ::log).run() }
    }

    /** Debug aid, see [HistorySync.dumpRaw]. */
    fun dumpRaw(days: Int) {
        val s = activeSession ?: return log("dump: not connected")
        val client = gatt ?: return
        scope.launch { HistorySync(s, client, dao, settings, sync, ::log).dumpRaw(System.currentTimeMillis() / 1000 - days * 86_400L, java.io.File(context.filesDir, "raw")) }
    }

    override fun setLiveHeartRate(enabled: Boolean) {
        liveHeartRateEnabled.value = enabled
        scope.launch { settings.setLiveHeartRate(enabled) }
        if (enabled) startLive() else pauseLive()
    }

    /** Stops streaming without changing the user's preference (e.g. during a firmware update). */
    private fun pauseLive() {
        liveJob?.cancel()
        liveHeartRate.value = null
        val s = session
        val client = gatt
        scope.launch { stopLive(s, client) }
    }

    private fun startLive() {
        liveJob?.cancel()
        liveHeartRate.value = null
        val s = activeSession ?: return
        val client = gatt ?: return
        liveJob = scope.launch {
            try {
                // Collect from both the channel push and the standard 2A37 characteristic; de-duplicate by second.
                var pushes = 0
                launch {
                    s.messagesFor(Endpoints.HEART_RATE).collect { p ->
                        if (pushes++ == 0) log("live hr 0048 < ${p.toHex()}")
                        LiveHeartRate.parsePush(p)?.let { onLiveHr(it) }
                    }
                }
                // Each keep-alive is answered with `05 02`; only log the first reply and anything else.
                var opens = 0
                launch { s.messagesFor(Endpoints.OPEN_APP).collect { p -> if (opens++ == 0 || !p.contentEquals(KEEP_ALIVE_REPLY)) log("live hr 001d < ${p.toHex()}") } }
                var measurements = 0
                launch {
                    client.notifications.filter { it.characteristic == StrapUuids.HR_MEASUREMENT }
                        .collect { n ->
                            if (measurements++ == 0) log("live hr 2a37 < ${n.value.toHex()}")
                            LiveHeartRate.parseMeasurement(n.value)?.let { onLiveHr(it) }
                        }
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

    override suspend fun installFirmware(file: ByteArray, type: Int, onProgress: (DfuProgress) -> Unit) {
        val client = gatt
        if (client == null || connection.value !is ConnectionState.Connected) throw DfuException(-1, "The strap isn't connected")
        if (sync.value is SyncState.Running) throw DfuException(-1, "Wait for the sync to finish first")
        if (!firmwareUpdating.compareAndSet(false, true)) throw DfuException(-1, "An update is already running")
        val wake = context.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OpenStrap:firmware").apply { acquire(30 * 60_000L) }
        try {
            if (liveJob?.isActive == true) pauseLive()
            val link = GattDfuLink(client)
            if (!link.available) throw DfuException(-1, "This strap doesn't offer the firmware update service")
            // Bigger ATT packets make the transfer several times faster; fall back to 20-byte writes if refused.
            runCatching { client.requestMtu(247) }.onFailure { log("dfu: mtu request failed: $it") }
            log("dfu: ${file.size} bytes, type ${"%02x".format(type)}, crc ${"%08x".format(crc32(file))}, att mtu ${client.mtu}")
            link.enable()
            refreshGattOnConnect = true
            DfuSession(link, ::log).flash(file, type, DfuCommands.MODE_NORMAL, onProgress)
            log("dfu: installed, strap is restarting")
        } catch (e: Exception) {
            log("dfu failed: ${e.message}")
            throw e
        } finally {
            firmwareUpdating.value = false
            if (wake.isHeld) wake.release()
        }
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

    private companion object {
        val RETRY_SECONDS = longArrayOf(5, 15, 30, 60, 120, 300)
        const val MIN_SYNC_GAP_SECONDS = 15 * 60L
        const val AUTO_SYNC_SECONDS = 60 * 60L
        val KEEP_ALIVE_REPLY = byteArrayOf(0x05, 0x02)
    }
}
