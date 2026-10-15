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

/** What the UI can see and ask of the strap. Implemented on top of the BLE stack. */
interface StrapController {
    val connection: StateFlow<ConnectionState>
    val sync: StateFlow<SyncState>

    /** Latest live heart-rate reading in bpm, or null when live HR is off. */
    val liveHeartRate: StateFlow<Int?>

    /** Today's step total as counted by the strap (since local midnight), or null when unknown. */
    val todaySteps: StateFlow<Int?>

    /** Recent protocol log lines, shown on the Device screen for troubleshooting. */
    val log: StateFlow<List<String>>

    fun connect()

    /** Connect to the paired strap if not already connected; no-op when nothing is paired. */
    fun autoConnect()
    fun disconnect()
    fun syncNow()
    fun setLiveHeartRate(enabled: Boolean)
}
