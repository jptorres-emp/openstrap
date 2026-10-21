package org.openstrap.app.device

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import org.openstrap.app.ble.GattClient
import org.openstrap.app.ble.GattException
import org.openstrap.app.ble.GattStrapLink
import org.openstrap.app.ble.StrapUuids
import org.openstrap.app.data.db.HealthAlertRow
import org.openstrap.app.data.db.HealthDao
import org.openstrap.app.data.settings.SettingsStore
import org.openstrap.app.data.settings.UserProfile
import org.openstrap.protocol.services.Gender
import org.openstrap.protocol.services.HealthAlert
import org.openstrap.protocol.services.UserProfile as StrapUserProfile
import org.openstrap.protocol.ChannelSession
import org.openstrap.protocol.auth.AuthException
import org.openstrap.protocol.hexToBytes
import org.openstrap.protocol.services.BatteryInfo
import org.openstrap.protocol.services.Alarm
import org.openstrap.protocol.services.AlarmCapabilities
import org.openstrap.protocol.services.AlarmCodec
import org.openstrap.protocol.services.Endpoints
import org.openstrap.protocol.services.FindDevice
import org.openstrap.protocol.services.MotorVibrate
import org.openstrap.protocol.services.Setting
import org.openstrap.protocol.services.SettingsCodec
import org.openstrap.protocol.services.SettingsGroup
import org.openstrap.protocol.services.SettingsProtocol
import org.openstrap.protocol.services.LiveHeartRate
import org.openstrap.protocol.services.RealtimeSteps
import org.openstrap.protocol.services.StepTotals
import org.openstrap.protocol.crypto.crc32
import org.openstrap.protocol.history.FetchCodec
import org.openstrap.protocol.history.GdspMessage
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
    private val healthAlerts = HealthAlertNotifier(context)

    override val connection = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val sync = MutableStateFlow<SyncState>(SyncState.Idle)
    override val liveHeartRate = MutableStateFlow<Int?>(null)
    override val todaySteps = MutableStateFlow<StepTotals?>(null)
    override val liveHeartRateEnabled = MutableStateFlow(true)
    override val info = MutableStateFlow<StrapInfo?>(null)
    override val firmwareUpdating = MutableStateFlow(false)
    override val alarmsChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
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
    // Synchronized, and checking connectJob: callers race at process start (app start-up, the companion presence
    // service, phone alerts), before the connect coroutine has moved the state off Disconnected.
    @Synchronized
    override fun autoConnect() {
        when (connection.value) {
            is ConnectionState.Connected, ConnectionState.Connecting, ConnectionState.Authenticating -> return
            else -> Unit
        }
        if (reconnectJob?.isActive == true || connectJob?.isActive == true) return
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
            // A client left over from a lost link still holds one of Android's few GATT client slots.
            gatt?.close()
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
                // afterAuth swallows step failures, so a link that dropped during it would otherwise show as Connected.
                if (!client.connected.value) throw GattException("Link lost during setup")
                connection.value = ConnectionState.Connected(battery, firmware, charging)
                watchBattery(s)
                watchSteps(s)
                watchDataAvailable(s)
                watchProfile(s)
                watchHealthAlerts(s)
                watchAlarms(s)
                watchFindDevice(s)
                retries = 0
                autoSync(s)
                liveHeartRateEnabled.value = settings.liveHeartRate.first()
                if (liveHeartRateEnabled.value && appVisible) startLive()
            } catch (e: AuthException) {
                log("auth failed: ${e.message}")
                connection.value = PairingErrors.authFailure(e)
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

        // The settings protocol version decides how query replies are framed (03 §3.6), so learn it first.
        settingsProtocol = null
        if (s.services.has(Endpoints.SETTINGS)) {
            runCatching {
                val r = s.request(Endpoints.SETTINGS, SettingsCodec.versionRequest, 5_000) { it.isNotEmpty() && it.u8(0) == SettingsCodec.VERSION + 1 }
                settingsProtocol = SettingsCodec.parseVersion(r)
                log("settings protocol ${settingsProtocol?.version} groups ${settingsProtocol?.groups} (${r.toHex()})")
            }.onFailure { log("settings version query failed: $it") }
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

        timeProtocol = null
        if (s.services.has(Endpoints.TIME)) {
            timeProtocol = runCatching {
                TimeSync.parseVersion(s.request(Endpoints.TIME, TimeSync.versionRequest, 5_000) { it.isNotEmpty() && it.u8(0) == TimeSync.VERSION + 1 })
            }.onFailure { log("time version query failed: $it") }.getOrNull()
            log("time protocol $timeProtocol")
        }
        syncTime(s, client)
    }

    /** The time module's version; the next-DST hint needs 2 or later (03 §3.8). */
    @Volatile private var timeProtocol: Int? = null

    /** Sets the strap's clock and time zone, and tells it when DST next changes, as Zepp does on connect and on every change. */
    private suspend fun syncTime(s: ChannelSession, client: GattClient) {
        val now = ZonedDateTime.now()
        runCatching {
            if (s.services.has(Endpoints.TIME)) {
                val r = s.request(Endpoints.TIME, TimeSync.setRequest(now)) { it.u8(0) == 0x06 }
                log("time set: ${r.toHex()}")
            } else {
                client.write(StrapUuids.MAIN_SERVICE, StrapUuids.CURRENT_TIME, TimeSync.struct(now), withResponse = true)
                log("time set via 2A2B")
            }
        }.onFailure { log("time sync failed: $it") }
        if ((timeProtocol ?: 0) >= 2) runCatching {
            val request = TimeSync.nextDstRequest(now)
            val r = s.request(Endpoints.TIME, request) { it.isNotEmpty() && it.u8(0) == TimeSync.NEXT_DST + 1 }
            log("next dst ${request.toHex()}: ${r.toHex()}")
        }.onFailure { log("dst hint failed: $it") }
    }

    /** The phone's clock or time zone changed (travel, DST, a manual change): pass it on while connected. */
    private val timeChanged = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: Context, intent: android.content.Intent) {
            val s = activeSession ?: return
            val client = gatt ?: return
            log("phone time changed (${intent.action?.substringAfterLast('.')}), updating the strap")
            scope.launch { syncTime(s, client) }
        }
    }

    init {
        val filter = android.content.IntentFilter().apply {
            addAction(android.content.Intent.ACTION_TIMEZONE_CHANGED)
            addAction(android.content.Intent.ACTION_TIME_CHANGED)
        }
        androidx.core.content.ContextCompat.registerReceiver(context, timeChanged, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    /**
     * Sends the user's profile to the strap now and whenever it changes, as Zepp does (03 §3.5); the strap uses it
     * for calories and heart-rate zones. Runs in the connect job, so it ends with this connection.
     */
    private fun CoroutineScope.watchProfile(s: ChannelSession) = launch {
        if (!s.services.has(Endpoints.USER_INFO)) return@launch
        settings.profile.map { it.toStrapProfile() }.distinctUntilChanged().collect { p ->
            val payload = p.channelPayload() ?: return@collect
            runCatching {
                val r = s.request(Endpoints.USER_INFO, payload, 5_000) { it.isNotEmpty() && it.u8(0) == StrapUserProfile.SET + 1 }
                log("profile sent: ${r.toHex()}")
            }.onFailure { log("profile send failed: $it") }
        }
    }

    /** The strap's own alerts (high/low HR, stress, low SpO2, irregular rhythm) arrive on 0x001D at any time. */
    private fun CoroutineScope.watchHealthAlerts(s: ChannelSession) = launch {
        s.messagesFor(Endpoints.OPEN_APP).collect { p ->
            if (p.isEmpty() || p.u8(0) != HealthAlert.PUSH) return@collect
            val alert = HealthAlert.parse(p)
            log("health alert < ${p.toHex()}: $alert")
            alert ?: return@collect
            healthAlerts.show(alert)
            // Kept so Today can list the day's alerts; a failed write mustn't stop the watcher.
            runCatching { dao.insertHealthAlerts(listOf(HealthAlertRow(alert.timestamp, alert.type.code, alert.value))) }
                .onFailure { log("health alert not stored: $it") }
        }
    }

    /** Zepp re-reads the alarms on the strap's `0F` push (03 §3.14). */
    private fun CoroutineScope.watchAlarms(s: ChannelSession) = launch {
        s.messagesFor(Endpoints.ALARMS).collect { p ->
            if (p.isEmpty() || p.u8(0) != AlarmCodec.CHANGED) return@collect
            log("alarms changed on the strap: ${p.toHex()}")
            alarmsChanged.tryEmit(Unit)
        }
    }

    /** Logged only: the strap's `07` once buzzing has stopped. */
    private fun CoroutineScope.watchFindDevice(s: ChannelSession) = launch {
        if (!s.services.has(Endpoints.FIND_DEVICE)) return@launch
        s.messagesFor(Endpoints.FIND_DEVICE).collect { p ->
            if (p.isNotEmpty() && p.u8(0) == FindDevice.FOUND) log("buzzing stopped on the strap: ${p.toHex()}")
        }
    }

    private fun UserProfile.toStrapProfile() = StrapUserProfile(
        birthYear = birthYear,
        gender = when (sex) { UserProfile.Sex.Female -> Gender.FEMALE; UserProfile.Sex.Male -> Gender.MALE; null -> null },
        heightCm = heightCm,
        weightKg = weightKg,
    )

    // The watch* listeners run in the connect job, so they end with the connection instead of piling up.
    private fun CoroutineScope.watchBattery(s: ChannelSession) = launch {
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

    private fun CoroutineScope.watchSteps(s: ChannelSession) = launch {
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

    /**
     * The strap pushes `11 <type>` when it has new data; like Zepp, sync the related types at once
     * (a finished workout, recovery HR; 04 §1.7).
     */
    private fun CoroutineScope.watchDataAvailable(s: ChannelSession) = launch {
        if (!s.services.has(Endpoints.HISTORY)) return@launch
        s.messagesFor(Endpoints.HISTORY).collect { p ->
            val m = FetchCodec.parse(p) as? GdspMessage.DataAvailable ?: return@collect
            val types = m.typesToSync.toSet()
            if (types.isEmpty()) return@collect
            log("strap has new data of type ${m.typeCode}, syncing $types")
            synchronized(pendingTypes) { pendingTypes += types }
            startSync(full = false)
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
            // Ends this connection's listeners, and a setup still in progress, so it can't go on to report Connected.
            connectJob?.cancel()
            liveJob?.cancel()
            liveHeartRate.value = null
            session?.stop()
            session = null
            gatt = null
            client.close()
            if (wantConnected) {
                // Keep the foreground service up while we try to get the strap back.
                connection.value = ConnectionState.Connecting
                scheduleReconnect()
            } else {
                connection.value = ConnectionState.Disconnected
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
        // Clear the field first, so watchDisconnect sees the drop as ours and doesn't reconnect.
        val g = gatt
        gatt = null
        g?.close()
    }

    override fun syncNow() = startSync(full = true)

    /** One sync at a time; a full sync or types asked for meanwhile run when it ends. */
    private val syncLock = Mutex()
    private val pendingTypes = mutableSetOf<Int>()
    private val pendingFull = java.util.concurrent.atomic.AtomicBoolean(false)

    private fun takePending(): Set<Int> = synchronized(pendingTypes) { pendingTypes.toSet().also { pendingTypes.clear() } }
    private fun hasPending(): Boolean = pendingFull.get() || synchronized(pendingTypes) { pendingTypes.isNotEmpty() }

    /** What to sync next: null for everything (a full sync covers anything pushed so far), or the pushed types. */
    private fun takeNext(): Set<Int>? = if (pendingFull.getAndSet(false)) { takePending(); null } else takePending()

    private fun startSync(full: Boolean) {
        if (firmwareUpdating.value) return
        val s = activeSession ?: return
        val client = gatt ?: return
        if (full) pendingFull.set(true)
        scope.launch {
            // A sync is running: it picks up what was just queued when it finishes.
            if (!syncLock.tryLock()) return@launch
            try {
                while (activeSession === s && !firmwareUpdating.value) {
                    val only = takeNext()
                    if (only != null && only.isEmpty()) break
                    HistorySync(s, client, dao, settings, sync, ::log).run(only)
                }
                runCatching { notifyBodySignals() }.onFailure { log("body signals: $it") }
            } finally {
                syncLock.unlock()
            }
            // Something queued between the last check and the unlock would otherwise wait for the next trigger.
            if (hasPending()) startSync(full = false)
        }
    }

    /**
     * After a sync, pushes the body-signal alert for this morning once, when the state machine is in Alert
     * ([org.openstrap.app.data.BodySignals]). Watch stays in the app.
     */
    private suspend fun notifyBodySignals() {
        val today = java.time.LocalDate.now()
        val day = org.openstrap.app.data.DayWindow.of(today).from
        if ((settings.lastSync(BODY_SIGNALS_PUSHED) ?: 0L) >= day) return
        val r = org.openstrap.app.data.RecoveryEngine(dao).compute(today, settings.profile.first())
        val body = r.readiness.bodySignals ?: return
        if (body.day != day || body.state != org.openstrap.app.data.BodySignals.State.Alert) return
        healthAlerts.showBodySignals(body.families, body.nightsOff)
        settings.setLastSync(BODY_SIGNALS_PUSHED, day)
    }

    /** Debug aid, see [HistorySync.dumpRaw]. */
    fun dumpRaw(days: Int, types: Set<Int>? = null) {
        val s = activeSession ?: return log("dump: not connected")
        val client = gatt ?: return
        scope.launch { HistorySync(s, client, dao, settings, sync, ::log).dumpRaw(System.currentTimeMillis() / 1000 - days * 86_400L, java.io.File(context.filesDir, "raw"), types) }
    }

    /** Debug aid: sends [payload] to [endpoint] and logs every reply on it for 5 s. */
    fun probe(endpoint: Int, payload: ByteArray) {
        val s = activeSession ?: return log("probe: not connected")
        scope.launch {
            val listener = launch { s.messagesFor(endpoint).collect { log("probe %04x < %s".format(endpoint, it.toHex())) } }
            // Device-side chunk ACKs on 0016 carry transport errors (e.g. status 5 = unknown endpoint).
            val acks = link?.let { l -> launch { l.controlNotifications.collect { log("probe %04x ack < %s".format(endpoint, it.toHex())) } } }
            log("probe %04x > %s".format(endpoint, payload.toHex()))
            runCatching { s.send(endpoint, payload) }.onFailure { log("probe failed: $it") }
            delay(5_000)
            listener.cancel()
            acks?.cancel()
        }
    }

    override fun setLiveHeartRate(enabled: Boolean) {
        liveHeartRateEnabled.value = enabled
        scope.launch { settings.setLiveHeartRate(enabled) }
        if (enabled && appVisible) startLive() else pauseLive()
    }

    /** Live HR streams only while the app is on screen; in the background nothing would show it. */
    @Volatile private var appVisible = false

    override fun setAppVisible(visible: Boolean) {
        if (visible == appVisible) return
        appVisible = visible
        if (!visible) {
            if (liveJob?.isActive == true) pauseLive()
        } else if (liveHeartRateEnabled.value && !firmwareUpdating.value && connection.value is ConnectionState.Connected) {
            startLive()
        }
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

    /** Sends a settings-style command and returns the reply whose first byte is the command + 1. */
    private suspend fun command(endpoint: Int, request: ByteArray): ByteArray {
        val s = activeSession ?: throw StrapCommandException("The strap isn't connected")
        if (firmwareUpdating.value) throw StrapCommandException("Wait for the firmware update to finish")
        val reply = runCatching {
            s.request(endpoint, request, 8_000) { it.isNotEmpty() && it.u8(0) == request.u8(0) + 1 }
        }.getOrElse { throw StrapCommandException("The strap didn't answer") }
        log("cmd %04x > %s < %s".format(endpoint, request.toHex(), reply.toHex()))
        return reply
    }

    private var alarmCaps: AlarmCapabilities? = null

    private suspend fun alarmCaps(): AlarmCapabilities = alarmCaps ?: (
        AlarmCodec.parseVersion(command(Endpoints.ALARMS, AlarmCodec.versionRequest))
            ?: throw StrapCommandException("The strap sent an unexpected alarm reply")
        ).also { alarmCaps = it }

    override suspend fun readAlarms(): Pair<AlarmCapabilities, List<Alarm>> {
        val caps = alarmCaps()
        val list = AlarmCodec.parseList(command(Endpoints.ALARMS, AlarmCodec.listRequest), caps.titles)
            ?: throw StrapCommandException("The strap sent an unexpected alarm list")
        return caps to list
    }

    override suspend fun saveAlarm(alarm: Alarm, isNew: Boolean) {
        val caps = alarmCaps()
        val request = if (isNew) AlarmCodec.add(alarm, caps.titles, caps.workdaysSupported) else AlarmCodec.modify(alarm, caps.titles, caps.workdaysSupported)
        if (!AlarmCodec.isSuccess(request, command(Endpoints.ALARMS, request))) throw StrapCommandException("The strap didn't save the alarm")
    }

    override suspend fun deleteAlarm(index: Int) {
        val request = AlarmCodec.delete(index)
        if (!AlarmCodec.isSuccess(request, command(Endpoints.ALARMS, request))) throw StrapCommandException("The strap didn't delete the alarm")
    }

    override suspend fun clearAlarms() {
        val request = AlarmCodec.clearAllRequest
        if (!AlarmCodec.isSuccess(request, command(Endpoints.ALARMS, request))) throw StrapCommandException("The strap didn't delete the alarms")
    }

    /** Learned on connect; null until then or when the strap didn't answer (then replies are read as v1–3). */
    @Volatile private var settingsProtocol: SettingsProtocol? = null

    private suspend fun settingsProtocol(): SettingsProtocol? = settingsProtocol
        ?: runCatching { SettingsCodec.parseVersion(command(Endpoints.SETTINGS, SettingsCodec.versionRequest)) }.getOrNull()
            ?.also { settingsProtocol = it }

    override suspend fun readHealthSettings(): SettingsGroup {
        val protocol = settingsProtocol()
        val reply = command(Endpoints.SETTINGS, SettingsCodec.query(SettingsCodec.GROUP_HEALTH))
        return SettingsCodec.parse(reply, protocol?.version) ?: throw StrapCommandException("The strap couldn't read its settings (code ${SettingsCodec.replyCode(reply)})")
    }

    override suspend fun writeHealthSettings(version: Int, settings: List<Setting>) {
        val reply = command(Endpoints.SETTINGS, SettingsCodec.write(SettingsCodec.GROUP_HEALTH, version, settings))
        if (!SettingsCodec.isWriteSuccess(reply)) throw StrapCommandException("The strap didn't accept the setting (code ${reply.getOrNull(1)})")
    }

    override suspend fun buzz(pattern: List<Int>) {
        if (activeSession?.services?.has(Endpoints.MOTOR_VIBRATE) == true) {
            if (!MotorVibrate.isOk(command(Endpoints.MOTOR_VIBRATE, MotorVibrate.play(pattern)))) throw StrapCommandException("The strap didn't buzz")
            return
        }
        // No motor module: find-device's `03` keeps buzzing until stopped (fw 3.25, 2026-10-20), so time it by hand.
        if (!FindDevice.isBuzzOk(command(Endpoints.FIND_DEVICE, FindDevice.BUZZ))) throw StrapCommandException("The strap didn't buzz")
        delay(FindDevice.SHORT_BUZZ_MS)
        stopBuzzing()
    }

    /**
     * `03`, not the `05 01` Zepp uses for its continuous find: on fw 3.25 `03` buzzes until `06`, while `05 01` doesn't
     * vibrate at all (2026-10-21).
     */
    override suspend fun startBuzzing() {
        if (!FindDevice.isBuzzOk(command(Endpoints.FIND_DEVICE, FindDevice.BUZZ))) throw StrapCommandException("The strap didn't buzz")
    }

    override suspend fun stopBuzzing() = sendFindDevice(FindDevice.STOP)

    /** STOP gets no reply (03 §3.16), so it is only sent. */
    private suspend fun sendFindDevice(request: ByteArray) {
        val s = activeSession ?: throw StrapCommandException("The strap isn't connected")
        if (firmwareUpdating.value) throw StrapCommandException("Wait for the firmware update to finish")
        log("cmd %04x > %s".format(Endpoints.FIND_DEVICE, request.toHex()))
        runCatching { s.send(Endpoints.FIND_DEVICE, request) }.getOrElse { throw StrapCommandException("The strap didn't answer") }
    }

    /** GENERAL key 47; null when the strap doesn't report it. Zepp refuses OTA while it is on (07 §1.6). */
    private suspend fun fastRechargeOn(): Boolean? = runCatching {
        val reply = command(Endpoints.SETTINGS, SettingsCodec.query(SettingsCodec.GROUP_GENERAL, listOf(SettingsCodec.KEY_FAST_RECHARGE), withOptions = false))
        SettingsCodec.parse(reply, settingsProtocol()?.version)?.get(SettingsCodec.KEY_FAST_RECHARGE)?.bool
    }.onFailure { log("fast-charge query failed: $it") }.getOrNull()

    override suspend fun installFirmware(file: ByteArray, type: Int, onProgress: (DfuProgress) -> Unit) {
        val client = gatt
        if (client == null || connection.value !is ConnectionState.Connected) throw DfuException(-1, "The strap isn't connected")
        if (sync.value is SyncState.Running) throw DfuException(-1, "Wait for the sync to finish first")
        if (firmwareUpdating.value) throw DfuException(-1, "An update is already running")
        if (session?.services?.has(Endpoints.SETTINGS) == true && fastRechargeOn() == true) throw DfuException(-1, "Turn off fast-charge mode on the strap first")
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

    /** Display only, like Zepp: the strap records per-second HR itself and history sync stores it. */
    private fun onLiveHr(bpm: Int) {
        liveHeartRate.value = bpm
    }

    private companion object {
        val RETRY_SECONDS = longArrayOf(5, 15, 30, 60, 120, 300)
        const val MIN_SYNC_GAP_SECONDS = 15 * 60L
        /** Settings key: the local midnight of the last morning a body-signal alert was pushed. */
        const val BODY_SIGNALS_PUSHED = "body_signals_push"
        const val AUTO_SYNC_SECONDS = 60 * 60L
        val KEEP_ALIVE_REPLY = byteArrayOf(0x05, 0x02)
    }
}
