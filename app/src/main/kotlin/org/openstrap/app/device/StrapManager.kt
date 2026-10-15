package org.openstrap.app.device

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.openstrap.app.ble.GattClient
import org.openstrap.app.data.db.HealthDao
import org.openstrap.app.data.settings.SettingsStore

class StrapManager(
    private val context: Context,
    private val settings: SettingsStore,
    private val dao: HealthDao,
) : StrapController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val connection = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val sync = MutableStateFlow<SyncState>(SyncState.Idle)
    override val liveHeartRate = MutableStateFlow<Int?>(null)

    private var job: Job? = null
    private var gatt: GattClient? = null

    override fun connect() {
        job?.cancel()
        job = scope.launch {
            val device = settings.pairedDevice.first()
            if (device == null) {
                connection.value = ConnectionState.NotPaired
                return@launch
            }
            StrapService.start(context)
            connection.value = ConnectionState.Connecting
            val client = GattClient(context, device.address).also { gatt = it }
            try {
                client.connect()
                connection.value = ConnectionState.Failed("Protocol not implemented yet")
            } catch (e: Exception) {
                connection.value = ConnectionState.Failed(e.message ?: "Connection failed")
                client.close()
            }
        }
    }

    override fun disconnect() {
        job?.cancel()
        gatt?.close()
        gatt = null
        liveHeartRate.value = null
        connection.value = ConnectionState.Disconnected
    }

    override fun syncNow() {}

    override fun setLiveHeartRate(enabled: Boolean) {}
}
