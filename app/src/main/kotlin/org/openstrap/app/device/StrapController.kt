package org.openstrap.app.device

import kotlinx.coroutines.flow.StateFlow
import org.openstrap.protocol.services.Alarm
import org.openstrap.protocol.services.AlarmCapabilities
import org.openstrap.protocol.services.Setting
import org.openstrap.protocol.services.SettingsGroup
import org.openstrap.protocol.services.StepTotals

sealed interface ConnectionState {
    data object NotPaired : ConnectionState
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    data object Authenticating : ConnectionState
    data class Connected(val batteryPercent: Int?, val firmware: String?, val charging: Boolean = false) : ConnectionState
    data class Failed(val reason: String) : ConnectionState
}

sealed interface SyncState {
    data object Idle : SyncState
    /** [progress] is the share of data types done (0–1), when known. */
    data class Running(val step: String, val progress: Float? = null) : SyncState
    data class Done(val atEpochSeconds: Long) : SyncState
    data class Failed(val reason: String) : SyncState
}

/** Details read from the strap at connect time. */
data class StrapInfo(val firmware: String?, val hardware: String?, val serial: String?, val dfuAvailable: Boolean)

/** What the UI can see and ask of the strap. Implemented on top of the BLE stack. */
interface StrapController {
    val connection: StateFlow<ConnectionState>
    val sync: StateFlow<SyncState>

    /** Latest live heart-rate reading in bpm, or null when live HR is off. */
    val liveHeartRate: StateFlow<Int?>

    /** The user's live-HR switch; when on, streaming runs while connected and the app is on screen. */
    val liveHeartRateEnabled: StateFlow<Boolean>

    /**
     * The strap's live step counter for today, or null when unknown. Taken as sent: on firmware 3.25 the
     * total carries a large constant offset, so check [StepTotals.isPlausible] before showing it directly.
     */
    val todaySteps: StateFlow<StepTotals?>

    /** Versions and capabilities of the connected strap, or null before the first connect. */
    val info: StateFlow<StrapInfo?>

    /** True while a firmware image is being sent; other traffic to the strap is paused. */
    val firmwareUpdating: StateFlow<Boolean>

    /** Recent protocol log lines, shown on the Device screen for troubleshooting. */
    val log: StateFlow<List<String>>

    fun connect()

    /** Connect to the paired strap if not already connected; no-op when nothing is paired. */
    fun autoConnect()
    fun disconnect()
    fun syncNow()
    fun setLiveHeartRate(enabled: Boolean)

    /** Called as the app's screen comes to the front or leaves it; live HR only runs while it's visible. */
    fun setAppVisible(visible: Boolean)

    /**
     * Sends [file] to the strap and applies it (docs/protocol/03 §3.4). The strap reboots afterwards and the
     * app reconnects on its own. Throws [org.openstrap.protocol.firmware.DfuException] on any refusal.
     */
    suspend fun installFirmware(file: ByteArray, type: Int, onProgress: (org.openstrap.protocol.firmware.DfuProgress) -> Unit)

    /** The strap's alarms and its alarm limits. Throws [StrapCommandException] when not connected or refused. */
    suspend fun readAlarms(): Pair<AlarmCapabilities, List<Alarm>>

    /** Adds [alarm] when its slot is free on the strap, otherwise replaces that slot. */
    suspend fun saveAlarm(alarm: Alarm, isNew: Boolean)
    suspend fun deleteAlarm(index: Int)

    /** The health settings group (HR measuring, HR and SpO2 alerts, …) with the choices the strap allows. */
    suspend fun readHealthSettings(): SettingsGroup

    /** Writes [settings] into the health group; [version] comes from the last [readHealthSettings]. */
    suspend fun writeHealthSettings(version: Int, settings: List<Setting>)
}

/** The strap couldn't be asked, or said no. [message] is fit to show. */
class StrapCommandException(message: String) : Exception(message)
