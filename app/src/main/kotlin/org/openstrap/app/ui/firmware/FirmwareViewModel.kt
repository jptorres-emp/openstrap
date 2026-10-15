package org.openstrap.app.ui.firmware

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openstrap.app.AppContainer
import org.openstrap.app.data.settings.ZeppAccount
import org.openstrap.app.device.ConnectionState
import org.openstrap.app.firmware.FirmwareCloud
import org.openstrap.app.firmware.FirmwarePart
import org.openstrap.app.firmware.StrapIdentity
import org.openstrap.app.firmware.UpdateCheck
import org.openstrap.app.zepp.BoundDevice
import org.openstrap.app.zepp.DeviceListing
import org.openstrap.protocol.firmware.DfuProgress
import org.openstrap.protocol.firmware.FirmwareType
import org.openstrap.protocol.firmware.HmfwTrailer

/** A firmware image that has been fetched and checked, waiting for the user to confirm the install. */
data class ReadyImage(
    val bytes: ByteArray,
    val type: Int,
    val title: String,
    val version: String?,
    val fromCloud: Boolean,
    val trailer: HmfwTrailer?,
    /** Things the user must read before installing; empty for a matching cloud image. */
    val warnings: List<String>,
)

sealed interface FirmwareStep {
    data object Idle : FirmwareStep
    data object Checking : FirmwareStep
    data class Checked(val result: UpdateCheck) : FirmwareStep
    data class Downloading(val part: FirmwarePart, val fraction: Float) : FirmwareStep
    data class Ready(val image: ReadyImage) : FirmwareStep
    data class Installing(val label: String, val fraction: Float?) : FirmwareStep
    data object Installed : FirmwareStep
    data class Failed(val message: String) : FirmwareStep
}

class FirmwareViewModel(private val container: AppContainer) : ViewModel() {
    private val cloud = FirmwareCloud(container.app.cacheDir)
    val strap get() = container.strap

    val account: StateFlow<ZeppAccount?> =
        container.settings.zeppAccount.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _step = MutableStateFlow<FirmwareStep>(FirmwareStep.Idle)
    val step = _step.asStateFlow()

    fun saveAccount(token: String, userId: String, apiBase: String, china: Boolean) = viewModelScope.launch {
        container.settings.saveZeppAccount(
            ZeppAccount(token.trim(), userId.trim(), apiBase.trim().ifEmpty { ZeppAccount.DEFAULT_API_BASE }, china),
        )
    }

    fun saveSignIn(listing: DeviceListing, strap: BoundDevice?) = viewModelScope.launch {
        container.settings.saveZeppAccount(
            ZeppAccount(listing.session.appToken, listing.session.userId, listing.apiBase, strap?.china ?: account.value?.china ?: false),
        )
    }

    fun clearAccount() = viewModelScope.launch { container.settings.clearZeppAccount() }

    fun reset() { if (!busy) _step.value = FirmwareStep.Idle }

    private val busy get() = _step.value.let { it is FirmwareStep.Checking || it is FirmwareStep.Downloading || it is FirmwareStep.Installing }

    fun check() {
        val acc = account.value ?: return fail("Add your Zepp app token first.")
        val info = strap.info.value
        val firmware = info?.firmware ?: return fail("Connect the strap first so its firmware version is known.")
        _step.value = FirmwareStep.Checking
        viewModelScope.launch {
            _step.value = runCatching {
                val identity = StrapIdentity(
                    firmwareVersion = firmware,
                    hardwareVersion = info.hardware,
                    serial = info.serial,
                    deviceSource = if (acc.china) StrapIdentity.DEVICE_SOURCE_CHINA else StrapIdentity.DEVICE_SOURCE_GLOBAL,
                )
                FirmwareStep.Checked(cloud.check(acc.apiBase, acc.appToken, acc.userId, identity))
            }.getOrElse { FirmwareStep.Failed(it.message ?: "Update check failed") }
        }
    }

    /** Downloads the first part in install order and stages it. Further parts follow after the strap restarts. */
    fun download(part: FirmwarePart) {
        val type = part.type ?: return fail("Unknown part type ${part.prefix}")
        _step.value = FirmwareStep.Downloading(part, 0f)
        viewModelScope.launch {
            _step.value = runCatching {
                val bytes = cloud.download(part) { done, total ->
                    if (total > 0) _step.value = FirmwareStep.Downloading(part, done.toFloat() / total)
                }
                val trailer = HmfwTrailer.read(bytes)
                FirmwareStep.Ready(
                    ReadyImage(bytes, type, "${part.label} ${part.version}", part.version, fromCloud = true, trailer, trailerWarnings(bytes, trailer, type)),
                )
            }.getOrElse { FirmwareStep.Failed(it.message ?: "Download failed") }
        }
    }

    /** Stages a file the user picked. Only plain firmware images are accepted from files. */
    fun loadFile(uri: Uri) = viewModelScope.launch {
        _step.value = runCatching {
            val bytes = withContext(Dispatchers.IO) {
                container.app.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            }
            require(bytes.size in 1024..16 * 1024 * 1024) { "That file is ${bytes.size} bytes, which isn't a plausible firmware image." }
            val trailer = HmfwTrailer.read(bytes)
            val warnings = buildList {
                add("This file didn't come from Zepp's update server for this strap. Only install files you trust.")
                if (trailer == null) add("The file has no HMFW signature block. It's probably not a strap firmware image.")
                addAll(trailerWarnings(bytes, trailer, FirmwareType.FIRMWARE))
            }
            FirmwareStep.Ready(ReadyImage(bytes, FirmwareType.FIRMWARE, "Firmware from file", null, fromCloud = false, trailer, warnings))
        }.getOrElse { FirmwareStep.Failed(it.message ?: "Couldn't read the file") }
    }

    /** Problems that make an image look wrong for this strap (docs/protocol/03 §2.2, advisory). */
    private fun trailerWarnings(bytes: ByteArray, trailer: HmfwTrailer?, type: Int): List<String> = buildList {
        if (trailer == null || type != FirmwareType.FIRMWARE) return@buildList
        if (trailer.pnpId != StrapIdentity.PRODUCT_ID) {
            add("The image says it's for product ${trailer.pnpId}, but this strap is product ${StrapIdentity.PRODUCT_ID}.")
        }
        if (trailer.fwSize > bytes.size) add("The image's size field (${trailer.fwSize}) is larger than the file.")
    }

    /** Problems that must be fixed before installing. */
    fun blockers(): List<String> = buildList {
        val c = strap.connection.value
        if (c !is ConnectionState.Connected) add("Connect the strap.")
        else {
            val battery = c.batteryPercent
            if (battery == null) add("Battery level unknown — reconnect the strap.")
            else if (battery < MIN_BATTERY) add("Charge the strap to at least $MIN_BATTERY% (now $battery%).")
        }
        if (strap.info.value?.dfuAvailable == false) add("This strap doesn't offer the firmware update service.")
    }

    fun install(image: ReadyImage) {
        val blocking = blockers()
        if (blocking.isNotEmpty()) return fail(blocking.joinToString(" "))
        _step.value = FirmwareStep.Installing("Starting…", null)
        viewModelScope.launch {
            _step.value = runCatching {
                strap.installFirmware(image.bytes, image.type) { p ->
                    _step.value = when (p) {
                        is DfuProgress.Transferring -> FirmwareStep.Installing("Sending to strap", p.sent.toFloat() / p.total)
                        DfuProgress.Validating -> FirmwareStep.Installing("Strap is checking the image", null)
                        DfuProgress.Applying -> FirmwareStep.Installing("Strap is installing", null)
                        DfuProgress.Done -> FirmwareStep.Installing("Restarting", 1f)
                    }
                }
                FirmwareStep.Installed
            }.getOrElse { FirmwareStep.Failed(it.message ?: "Install failed") }
        }
    }

    private fun fail(message: String) { _step.value = FirmwareStep.Failed(message) }

    companion object {
        const val MIN_BATTERY = 20
    }
}
