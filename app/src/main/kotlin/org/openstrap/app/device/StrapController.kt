package org.openstrap.app.device

import kotlinx.coroutines.flow.StateFlow

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
    data class Running(val step: String) : SyncState
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

    /** The user's live-HR switch; when on, streaming starts on every connect. */
    val liveHeartRateEnabled: StateFlow<Boolean>

    /** Today's step total as counted by the strap (since local midnight), or null when unknown. */
    val todaySteps: StateFlow<Int?>

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

    /**
     * Sends [file] to the strap and applies it (docs/protocol/03 §3.4). The strap reboots afterwards and the
     * app reconnects on its own. Throws [org.openstrap.protocol.firmware.DfuException] on any refusal.
     */
    suspend fun installFirmware(file: ByteArray, type: Int, onProgress: (org.openstrap.protocol.firmware.DfuProgress) -> Unit)
}
