package org.openstrap.app.ui.firmware

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.openstrap.app.data.settings.ZeppAccount
import org.openstrap.app.device.ConnectionState
import org.openstrap.app.firmware.UpdateCheck
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import org.openstrap.app.ui.glow.DestructiveInk
import org.openstrap.app.ui.glow.GlowLayout
import org.openstrap.app.ui.glow.GlowType
import org.openstrap.app.ui.glow.PageButton
import org.openstrap.app.ui.glow.PageConfirm
import org.openstrap.app.ui.glow.PageField
import org.openstrap.app.ui.glow.PageProgress
import org.openstrap.app.ui.glow.PageHeader
import org.openstrap.app.ui.glow.PageNote
import org.openstrap.app.ui.glow.PagePanel
import org.openstrap.app.ui.glow.PageRow
import org.openstrap.app.ui.glow.PageRows
import org.openstrap.app.ui.glow.PageSwitchRow
import org.openstrap.app.ui.dashboard.ScreenList
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.app.ui.theme.AppIcons
import org.openstrap.app.ui.zepp.ZeppSignInDialog
import org.openstrap.app.zepp.BoundDevice
import org.openstrap.app.zepp.DeviceListing
import org.openstrap.app.l10n.tr

/**
 * The strap's firmware: what it runs, checking for and installing an update, or installing from a file. Opened from
 * the Strap page. The Zepp account the update check signs in with has its own page ([ZeppAccountScreen]).
 */
@Composable
fun FirmwareScreen(vm: FirmwareViewModel, contentPadding: PaddingValues, onBack: () -> Unit, onZeppAccount: () -> Unit) {
    val step by vm.step.collectAsState()
    val account by vm.account.collectAsState()
    val connection by vm.strap.connection.collectAsState()
    val info by vm.strap.info.collectAsState()
    val installing = step is FirmwareStep.Installing

    // Leaving mid-install would be harmless for the strap, but keep the user here and the screen on.
    BackHandler { if (!installing) { vm.reset(); onBack() } }
    KeepScreenOn(installing)

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::loadFile) }
    var confirm by remember { mutableStateOf<ReadyImage?>(null) }
    confirm?.let { image -> ConfirmInstall(image, vm.blockers(), onDismiss = { confirm = null }, onInstall = { confirm = null; vm.install(image) }) }

    ScreenList(contentPadding, gutter = GlowLayout.Gutter) {
        item { PageHeader(tr("Firmware", "Firmware"), { vm.reset(); onBack() }, backEnabled = !installing) }

        item {
            PagePanel(title = tr("This strap", "Esta strap")) {
                val c = connection as? ConnectionState.Connected
                InfoLine(tr("Firmware", "Firmware"), info?.firmware ?: c?.firmware ?: tr("Unknown", "Desconhecido"))
                info?.hardware?.let { InfoLine(tr("Hardware", "Hardware"), it) }
                InfoLine(tr("Battery", "Bateria"), c?.batteryPercent?.let { "$it%" } ?: tr("Not connected", "Desligada"))
                InfoLine(tr("Update service", "Serviço de atualização"), when (info?.dfuAvailable) { true -> tr("Available", "Disponível"); false -> tr("Not found", "Não encontrado"); null -> tr("Connect to check", "Liga para verificar") })
            }
        }

        item { StepPanel(step, vm, onInstall = { confirm = it }) }

        item {
            PageRows {
                // Update checks need a signed-in Zepp account; its page is a row away, as on the Strap page.
                PageRow(tr("Zepp account", "Conta Zepp"), if (account != null) tr("Signed in", "Sessão iniciada") else tr("Not signed in", "Sem sessão iniciada"), onClick = onZeppAccount, divider = false, enabled = !installing)
                PageRow(tr("Install from a file", "Instalar a partir de um ficheiro"), tr("For an image you have", "Para uma imagem que já tenhas"), onClick = { pickFile.launch(arrayOf("*/*")) }, enabled = !installing)
            }
        }

        item {
            PagePanel(title = tr("Privacy and safety", "Privacidade e segurança")) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Point(AppIcons.Key, tr("Sign-in", "Início de sessão"), tr(
                        "Happens on Zepp's own page. OpenStrap keeps only the app token and user ID, and fetches your device " +
                            "list once.",
                        "Acontece na própria página da Zepp. A OpenStrap só guarda o token da app e o ID de utilizador, e " +
                            "vai buscar a tua lista de dispositivos uma vez.",
                    ))
                    Point(AppIcons.CloudArrowDown, tr("Update check", "Procura de atualizações"), tr(
                        "Sends the update server your token, user ID and the strap's firmware, hardware and serial number. " +
                            "Nothing else.",
                        "Envia ao servidor de atualizações o teu token, ID de utilizador e o firmware, hardware e número de " +
                            "série da strap. Mais nada.",
                    ))
                    Point(AppIcons.CheckCircle, tr("Safe install", "Instalação segura"), tr(
                        "Downloads are checked against the server's checksum, and the strap verifies the signature before " +
                            "installing.",
                        "As transferências são verificadas com o checksum do servidor, e a strap confirma a assinatura antes " +
                            "de instalar.",
                    ))
                }
            }
        }
    }
}

@Composable
private fun StepPanel(step: FirmwareStep, vm: FirmwareViewModel, onInstall: (ReadyImage) -> Unit) {
    PagePanel {
        when (step) {
            FirmwareStep.Idle -> {
                Title(tr("Updates", "Atualizações"))
                Body(tr(
                    "Ask Zepp's update server whether there's newer firmware for this strap.",
                    "Pergunta ao servidor de atualizações da Zepp se há firmware mais recente para esta strap.",
                ))
                PageButton(tr("Check for update", "Procurar atualização"), vm::check, Modifier.fillMaxWidth(), primary = true)
            }
            FirmwareStep.Checking -> Busy(tr("Checking for updates…", "À procura de atualizações…"), null)
            is FirmwareStep.Checked -> CheckResult(step.result, vm)
            is FirmwareStep.Downloading -> Busy(tr(
                "Downloading ${step.part.label} ${step.part.version}",
                "A transferir ${step.part.label} ${step.part.version}",
            ), step.fraction)
            is FirmwareStep.Ready -> {
                val image = step.image
                Title(tr("Ready to install", "Pronto para instalar"))
                InfoLine(tr("Image", "Imagem"), image.title)
                InfoLine(tr("Size", "Tamanho"), tr("%,d bytes", "%,d bytes").format(image.bytes.size))
                image.trailer?.let { InfoLine(tr("Signed for", "Assinado para"), tr("product ${it.pnpId} v${it.pnpVersion}", "produto ${it.pnpId} v${it.pnpVersion}")) }
                image.warnings.forEach { Warning(it) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PageButton(tr("Cancel", "Cancelar"), vm::reset, Modifier.weight(1f))
                    PageButton(tr("Install", "Instalar"), { onInstall(image) }, Modifier.weight(1f), primary = true)
                }
            }
            is FirmwareStep.Installing -> {
                Busy(step.label, step.fraction)
                PageNote(tr(
                    "Keep the strap close and this screen open. This can take several minutes.",
                    "Mantém a strap perto e este ecrã aberto. Pode levar vários minutos.",
                ))
            }
            FirmwareStep.Installed -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(AppIcons.CheckCircle, null, tint = MetricColors.good)
                    Title(tr("Installed", "Instalado"))
                }
                Body(tr(
                    "The strap is restarting. OpenStrap reconnects by itself. Check again afterwards in case there are " +
                        "more parts to install.",
                    "A strap está a reiniciar. A OpenStrap volta a ligar sozinha. Depois procura de novo, caso haja mais " +
                        "partes para instalar.",
                ))
                PageButton(tr("Done", "Feito"), vm::reset, Modifier.fillMaxWidth())
            }
            is FirmwareStep.Failed -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(AppIcons.WarningCircle, null, tint = MetricColors.warn)
                    Title(tr("Something went wrong", "Algo correu mal"))
                }
                Body(step.message)
                PageButton(tr("OK", "OK"), vm::reset, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun CheckResult(result: UpdateCheck, vm: FirmwareViewModel) {
    if (result.upToDate) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(AppIcons.CheckCircle, null, tint = MetricColors.good)
            Title(tr("Up to date", "Atualizado"))
        }
        Body(tr(
            "The update server has nothing newer for this strap.",
            "O servidor de atualizações não tem nada mais recente para esta strap.",
        ))
        PageButton(tr("OK", "OK"), vm::reset, Modifier.fillMaxWidth())
        return
    }
    val next = result.installOrder.firstOrNull()
    Title(if (result.forced) tr("Required update", "Atualização obrigatória") else tr("Update available", "Atualização disponível"))
    result.parts.forEach { InfoLine(it.label, "${it.version}, ${"%,d".format(it.length / 1024)} KB") }
    result.changeLog?.let { PageNote(it.substringBefore("###summary###").trim()) }
    if (result.installOrder.size > 1) {
        PageNote(tr(
            "Parts are installed one at a time. After the strap restarts, check again to install the next one.",
            "As partes são instaladas uma de cada vez. Depois de a strap reiniciar, procura de novo para " +
                "instalar a seguinte.",
        ))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PageButton(tr("Not now", "Agora não"), vm::reset, Modifier.weight(1f))
        PageButton(if (next == null) tr("Unsupported", "Não suportado") else tr("Download", "Transferir"), { next?.let(vm::download) }, Modifier.weight(1f), enabled = next != null, primary = true)
    }
}

/**
 * The Zepp account the firmware check signs in with: sign in, paste a token, or remove it. Opened from the Strap page
 * (and from Firmware). Kept on this phone only.
 */
@Composable
fun ZeppAccountScreen(vm: FirmwareViewModel, contentPadding: PaddingValues, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val account by vm.account.collectAsState()
    val installing = vm.step.collectAsState().value is FirmwareStep.Installing
    ScreenList(contentPadding, gutter = GlowLayout.Gutter) {
        item { PageHeader(tr("Zepp account", "Conta Zepp"), onBack) }
        item { AccountPanel(account, enabled = !installing, onSave = vm::saveAccount, onClear = vm::clearAccount, onSignIn = vm::saveSignIn) }
    }
}

@Composable
private fun AccountPanel(
    account: ZeppAccount?,
    enabled: Boolean,
    onSave: (String, String, String, Boolean) -> Unit,
    onClear: () -> Unit,
    onSignIn: (DeviceListing, BoundDevice?) -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var signIn by rememberSaveable { mutableStateOf(false) }
    if (signIn) {
        ZeppSignInDialog(
            preferredApiBase = account?.apiBase,
            confirmLabel = tr("Save sign-in", "Guardar início de sessão"),
            onDismiss = { signIn = false },
            onConfirm = { listing, strap -> signIn = false; onSignIn(listing, strap) },
        )
    }
    PagePanel {
        if (!editing) {
            Body(
                if (account == null) tr(
                    "Zepp only answers update checks for signed-in users. Sign in, or paste your own app token and user " +
                        "ID. They're stored on this phone only.",
                    "A Zepp só responde a procuras de atualizações de quem tem sessão iniciada. Inicia sessão, ou cola o " +
                        "teu token da app e ID de utilizador. Ficam guardados só neste telemóvel.",
                )
                else tr(
                    "Update checks use your saved " +
                        "token${if (account.userId.isNotEmpty()) " for user ${account.userId}" else ""} on " +
                        "${account.apiBase}.",
                    "As procuras de atualizações usam o teu token " +
                        "guardado${if (account.userId.isNotEmpty()) " do utilizador ${account.userId}" else ""} em " +
                        "${account.apiBase}.",
                ),
            )
            PageButton(if (account == null) tr("Sign in with Zepp", "Iniciar sessão com a Zepp") else tr("Sign in again", "Iniciar sessão de novo"), { signIn = true }, Modifier.fillMaxWidth(), enabled = enabled, primary = true)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PageButton(if (account == null) tr("Paste token", "Colar token") else tr("Edit", "Editar"), { editing = true }, Modifier.weight(1f), enabled = enabled)
                if (account != null) PageButton(tr("Remove", "Remover"), onClear, Modifier.weight(1f), enabled = enabled, color = DestructiveInk)
            }
            return@PagePanel
        }
        var token by rememberSaveable { mutableStateOf("") }
        var user by rememberSaveable { mutableStateOf(account?.userId.orEmpty()) }
        var api by rememberSaveable { mutableStateOf(account?.apiBase ?: ZeppAccount.DEFAULT_API_BASE) }
        var china by rememberSaveable { mutableStateOf(account?.china == true) }
        PageField(
            token, { token = it }, tr("App token", "Token da app"), Modifier.fillMaxWidth(),
            placeholder = if (account != null) tr("Leave empty to keep the saved token", "Deixa vazio para manter o token guardado") else null,
            visualTransformation = PasswordVisualTransformation(),
        )
        PageField(
            user, { user = it }, tr("User ID", "ID de utilizador"), Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        PageField(api, { api = it }, tr("Server", "Servidor"), Modifier.fillMaxWidth())
        PageSwitchRow(tr("Chinese-market strap", "Strap do mercado chinês"), tr("Only if the strap was bought in mainland China", "Só se a strap foi comprada na China continental"), china, { china = it }, divider = false)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PageButton(tr("Cancel", "Cancelar"), { editing = false }, Modifier.weight(1f))
            PageButton(
                tr("Save", "Guardar"), { onSave(token.ifBlank { account?.appToken.orEmpty() }, user, api, china); editing = false }, Modifier.weight(1f),
                enabled = token.isNotBlank() || account != null, primary = true,
            )
        }
    }
}

@Composable
private fun ConfirmInstall(image: ReadyImage, blockers: List<String>, onDismiss: () -> Unit, onInstall: () -> Unit) {
    var accepted by remember { mutableStateOf(image.warnings.isEmpty()) }
    PageConfirm(
        tr("Install ${image.title}?", "Instalar ${image.title}?"),
        if (blockers.isNotEmpty()) null
        else tr(
            "The strap will receive the image, check it, install it and restart. Keep it near the phone and " +
                "don't close OpenStrap until it's done.",
            "A strap vai receber a imagem, verificá-la, instalá-la e reiniciar. Mantém-na perto do telemóvel e " +
                "não feches a OpenStrap até acabar.",
        ),
        confirm = tr("Install", "Instalar"), onConfirm = onInstall, onDismiss = onDismiss, confirmEnabled = blockers.isEmpty() && accepted,
    ) {
        if (blockers.isNotEmpty()) {
            blockers.forEach { Warning(it) }
        } else {
            image.warnings.forEach { Warning(it) }
            if (image.warnings.isNotEmpty()) PageSwitchRow(tr("I understand and want to install it anyway", "Percebo e quero instalar na mesma"), null, accepted, { accepted = it }, divider = false)
        }
    }
}

@Composable
private fun Busy(label: String, fraction: Float?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = GlowType.body, color = Color.White, modifier = Modifier.weight(1f))
        fraction?.let { Text("${(it * 100).toInt()}%", style = GlowType.body, color = Color.White) }
    }
    PageProgress(fraction)
}

@Composable
private fun Title(text: String) = Text(text, style = GlowType.cardTitle, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)

@Composable
private fun Body(text: String) = Text(text, style = GlowType.body, color = Color.White.copy(alpha = GlowType.SECONDARY_ALPHA))

/** One line of the privacy card: icon, short title, one sentence. */
@Composable
private fun Point(icon: ImageVector, title: String, body: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.padding(top = 2.dp).size(20.dp), tint = Color.White.copy(alpha = GlowType.SECONDARY_ALPHA))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = GlowType.body, color = Color.White)
            PageNote(body)
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = GlowType.body, color = Color.White.copy(alpha = GlowType.SECONDARY_ALPHA), modifier = Modifier.weight(1f))
        Text(value, style = GlowType.body, color = Color.White)
    }
}

@Composable
private fun Warning(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(AppIcons.Warning, null, tint = MetricColors.warn, modifier = Modifier.size(20.dp))
        Text(text, style = GlowType.secondary, color = Color.White.copy(alpha = GlowType.SECONDARY_ALPHA))
    }
}

@Composable
private fun KeepScreenOn(on: Boolean) {
    val activity = LocalContext.current as? Activity ?: return
    DisposableEffect(on) {
        if (on) activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}
