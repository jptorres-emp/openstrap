package org.openstrap.app.ui.device

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openstrap.app.AppContainer
import org.openstrap.app.ble.FoundDevice
import org.openstrap.app.data.settings.PairedDevice
import org.openstrap.app.data.settings.ZeppAccount
import org.openstrap.app.device.StrapController
import org.openstrap.app.zepp.BoundDevice
import org.openstrap.app.zepp.DeviceListing

data class ScanUiState(
    val scanning: Boolean = false,
    val found: List<FoundDevice> = emptyList(),
    val error: String? = null,
)

class DeviceViewModel(private val container: AppContainer) : ViewModel() {
    val strap: StrapController get() = container.strap
    val paired: StateFlow<PairedDevice?> =
        container.settings.pairedDevice.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val profile: StateFlow<org.openstrap.app.data.settings.UserProfile> =
        container.settings.profile.stateIn(viewModelScope, SharingStarted.Eagerly, org.openstrap.app.data.settings.UserProfile())

    fun saveProfile(p: org.openstrap.app.data.settings.UserProfile) = viewModelScope.launch { container.settings.saveProfile(p) }

    private val _scan = MutableStateFlow(ScanUiState())
    val scan = _scan.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    fun clearMessage() { _message.value = null }

    private var scanJob: Job? = null

    fun startScan() {
        scanJob?.cancel()
        _scan.value = ScanUiState(scanning = true)
        scanJob = viewModelScope.launch {
            container.scanner.scan()
                .catch { e -> _scan.update { it.copy(scanning = false, error = e.message) } }
                .collect { d -> _scan.update { s -> s.copy(found = (s.found.filter { it.address != d.address } + d).sortedByDescending { it.rssi }) } }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        _scan.update { it.copy(scanning = false) }
    }

    fun pair(address: String, name: String, authKey: String) {
        val key = normalizeAuthKey(authKey)
        if (key == null) {
            _message.value = "The auth key must be 32 hex characters (16 bytes)."
            return
        }
        if (!MAC.matches(address.uppercase())) {
            _message.value = "That doesn't look like a Bluetooth address."
            return
        }
        stopScan()
        viewModelScope.launch {
            container.settings.savePairedDevice(PairedDevice(address.uppercase(), key, name))
            container.strap.connect()
        }
    }

    /** Keeps the Zepp sign-in for firmware checks; the strap's address and key go into the pair form. */
    fun saveZeppSignIn(listing: DeviceListing, strap: BoundDevice?) = viewModelScope.launch {
        container.settings.saveZeppAccount(
            ZeppAccount(listing.session.appToken, listing.session.userId, listing.apiBase, strap?.china == true),
        )
        _message.value = if (strap?.authKey != null) "Signed in. Check the address and key, then tap Pair." else "Zepp sign-in saved."
    }

    fun loadSampleData() = viewModelScope.launch {
        org.openstrap.app.data.SampleData.load(container.database.healthDao())
        _message.value = "Loaded a week of sample data."
    }

    fun forget() = viewModelScope.launch {
        container.strap.disconnect()
        container.settings.forgetDevice()
    }

    fun exportCsv(uri: Uri) = viewModelScope.launch {
        _message.value = runCatching {
            withContext(Dispatchers.IO) {
                container.app.contentResolver.openOutputStream(uri)!!.use { container.csv.exportZip(it) }
            }
            "Exported."
        }.getOrElse { "Export failed: ${it.message}" }
    }

    fun exportHealthConnect() = viewModelScope.launch {
        _message.value = runCatching {
            val now = System.currentTimeMillis() / 1000
            val n = container.healthConnect.export(now - 30L * 24 * 3600, now)
            "Wrote $n records to Health Connect (last 30 days)."
        }.getOrElse { "Health Connect export failed: ${it.message}" }
    }

    companion object {
        private val MAC = Regex("^([0-9A-F]{2}:){5}[0-9A-F]{2}$")

        /** Accepts "0x…", spaces or colons; returns 32 lowercase hex chars or null. */
        fun normalizeAuthKey(input: String): String? {
            val hex = input.trim().removePrefix("0x").removePrefix("0X").filter { it.isLetterOrDigit() }.lowercase()
            return hex.takeIf { it.length == 32 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } }
        }
    }
}
