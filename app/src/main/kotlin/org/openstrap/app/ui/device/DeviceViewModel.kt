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
import org.openstrap.app.device.StrapController
import org.openstrap.app.zepp.BoundDevice
import org.openstrap.app.zepp.DeviceListing
import org.openstrap.app.l10n.tr

data class ScanUiState(
    val scanning: Boolean = false,
    val found: List<FoundDevice> = emptyList(),
    val error: String? = null,
)

class DeviceViewModel(private val container: AppContainer) : ViewModel() {
    val strap: StrapController get() = container.strap
    val companion: org.openstrap.app.device.CompanionLink get() = container.companion
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
            val n = authKey.trim().removePrefix("0x").removePrefix("0X").count { it.isLetterOrDigit() }
            _message.value = tr(
                "The auth key must be 32 characters of 0–9 and a–f. You entered $n.",
                "A chave de autenticação tem de ter 32 caracteres de 0–9 e a–f. Introduziste $n.",
            )
            return
        }
        if (!MAC.matches(address.uppercase())) {
            _message.value = tr(
                "The Bluetooth address should look like C8:0F:10:AB:CD:EF. Scan to fill it in.",
                "O endereço Bluetooth deve ser parecido com C8:0F:10:AB:CD:EF. Procura para o preencher.",
            )
            return
        }
        stopScan()
        viewModelScope.launch {
            container.settings.savePairedDevice(PairedDevice(address.uppercase(), key, name))
            container.strap.connect()
            container.companion.refresh().join()
            if (container.companion.supported && !container.companion.associated.value) _offerBackground.value = true
        }
    }

    /** Keeps the Zepp sign-in for firmware checks and, when Zepp returned the strap's key, pairs with it straight away. */
    fun signInAndPair(listing: DeviceListing, strap: BoundDevice?) = viewModelScope.launch {
        container.settings.saveZeppAccount(listing.session.toAccount(listing.apiBase, strap?.china == true, System.currentTimeMillis()))
        val key = strap?.authKey
        if (strap != null && key != null) pair(strap.macAddress, DEFAULT_NAME, key)
        else _message.value = tr(
            "Zepp sign-in saved, but no auth key came with it. Get the key on a computer and enter it by hand.",
            "O início de sessão da Zepp ficou guardado, mas não veio nenhuma chave de autenticação. Obtém a " +
                "chave num computador e introduz à mão.",
        )
    }

    fun loadSampleData() = viewModelScope.launch {
        org.openstrap.app.data.SampleData.load(container.database.healthDao())
        _message.value = tr("Loaded a week of sample data.", "Carreguei uma semana de dados de exemplo.")
    }

    /** Set at the end of pairing: the strap page then asks Android to associate the strap, once. */
    private val _offerBackground = MutableStateFlow(false)
    val offerBackground = _offerBackground.asStateFlow()
    fun backgroundOffered() { _offerBackground.value = false }

    val companionDeclined: StateFlow<Boolean> =
        container.settings.companionDeclined.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun showMessage(text: String) { _message.value = text }

    /** Find strap: buzzes it until stopped or Zepp's 60 s are up. */
    fun findStrap() = viewModelScope.launch {
        runCatching { container.strap.findStrap() }.onFailure { _message.value = it.message ?: tr("Couldn't buzz the strap", "Não deu para vibrar a strap") }
    }

    fun stopFindingStrap() = viewModelScope.launch {
        runCatching { container.strap.stopFindingStrap() }.onFailure { _message.value = it.message ?: tr("Couldn't stop the strap buzzing", "Não deu para parar a vibração da strap") }
    }

    /** Restarts the strap; the link drops and the usual reconnect brings it back. */
    fun restartStrap() = viewModelScope.launch {
        _message.value = runCatching { container.strap.restartStrap(); tr(
            "Restarting the strap. OpenStrap reconnects when it's back.",
            "A reiniciar a strap. A OpenStrap volta a ligar quando ela voltar.",
        ) }
            .getOrElse { it.message ?: tr("Couldn't restart the strap", "Não deu para reiniciar a strap") }
    }

    fun forget() = viewModelScope.launch {
        container.strap.disconnect()
        paired.value?.let { container.companion.disassociate(it.address) }
        container.settings.forgetDevice()
    }

    fun exportCsv(uri: Uri) = viewModelScope.launch {
        _message.value = runCatching {
            withContext(Dispatchers.IO) {
                container.app.contentResolver.openOutputStream(uri)!!.use { container.csv.exportZip(it) }
            }
            tr("Saved a copy.", "Cópia guardada.")
        }.getOrElse { tr("Couldn't save the copy: ${it.message}", "Não deu para guardar a cópia: ${it.message}") }
    }

    /** Restores a zip from [exportCsv] into the days before the strap's history. Safe to run twice. */
    fun importCsv(uri: Uri) = viewModelScope.launch {
        _message.value = runCatching {
            val result = withContext(Dispatchers.IO) {
                container.app.contentResolver.openInputStream(uri)!!.use { org.openstrap.app.export.CsvImporter(container.database).importZip(it) }
            }
            tr(
                "Restored %,d records from before the strap's history. Left out %,d from days it has.",
                "Repostos %,d registos de antes do histórico da strap. Ficaram de fora %,d de dias que ela já tem.",
            ).format(result.total, result.present) +
                (if (result.skipped > 0) tr(" %,d unreadable rows skipped.", " %,d linhas ilegíveis ignoradas.").format(result.skipped) else "")
        }.getOrElse { tr("Couldn't restore the copy: ${it.message}", "Não deu para repor a cópia: ${it.message}") }
    }

    /** Brings back what OpenStrap exported to Health Connect, filling gaps only. */
    fun importHealthConnect() = viewModelScope.launch {
        _message.value = runCatching {
            val n = withContext(Dispatchers.IO) { container.healthConnect.import() }
            if (n == 0) tr(
                "Health Connect has no OpenStrap data to restore.",
                "O Health Connect não tem dados da OpenStrap para repor.",
            ) else tr("Restored %,d records from Health Connect.", "Repostos %,d registos do Health Connect.").format(n)
        }.getOrElse { tr(
            "Couldn't restore from Health Connect: ${it.message}",
            "Não deu para repor do Health Connect: ${it.message}",
        ) }
    }

    fun exportHealthConnect() = viewModelScope.launch {
        _message.value = runCatching {
            val now = System.currentTimeMillis() / 1000
            val n = container.healthConnect.export(now - 30L * 24 * 3600, now)
            tr(
                "Wrote $n records to Health Connect (last 30 days).",
                "Escrevi $n registos no Health Connect (últimos 30 dias).",
            )
        }.getOrElse { tr(
            "Couldn't write to Health Connect: ${it.message}",
            "Não deu para escrever no Health Connect: ${it.message}",
        ) }
    }

    companion object {
        const val DEFAULT_NAME = "Amazfit Helio Strap"
        private val MAC = Regex("^([0-9A-F]{2}:){5}[0-9A-F]{2}$")

        fun isAddress(input: String) = MAC.matches(input.trim().uppercase())

        /** Accepts "0x…", spaces or colons; returns 32 lowercase hex chars or null. */
        fun normalizeAuthKey(input: String): String? {
            val hex = input.trim().removePrefix("0x").removePrefix("0X").filter { it.isLetterOrDigit() }.lowercase()
            return hex.takeIf { it.length == 32 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } }
        }
    }
}
