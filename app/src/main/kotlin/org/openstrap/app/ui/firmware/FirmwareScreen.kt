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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.openstrap.app.data.settings.ZeppAccount
import org.openstrap.app.device.ConnectionState
import org.openstrap.app.firmware.UpdateCheck
import org.openstrap.app.ui.components.Panel
import org.openstrap.app.ui.components.PanelHeader
import org.openstrap.app.ui.components.SettingRow
import org.openstrap.app.ui.dashboard.ScreenList
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.app.ui.theme.PhosphorIcons
import org.openstrap.app.ui.zepp.ZeppSignInDialog
import org.openstrap.app.zepp.BoundDevice
import org.openstrap.app.zepp.DeviceListing

@Composable
fun FirmwareScreen(vm: FirmwareViewModel, contentPadding: PaddingValues, onBack: () -> Unit) {
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

    ScreenList(contentPadding) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                IconButton(onClick = { vm.reset(); onBack() }, enabled = !installing) { Icon(PhosphorIcons.ArrowLeft, "Back") }
                Text("Firmware", style = MaterialTheme.typography.headlineMedium)
            }
        }

        item {
            Panel {
                PanelHeader("This strap", PhosphorIcons.ArrowCircleUp, MetricColors.spo2)
                val c = connection as? ConnectionState.Connected
                InfoLine("Firmware", info?.firmware ?: c?.firmware ?: "Unknown")
                info?.hardware?.let { InfoLine("Hardware", it) }
                InfoLine("Battery", c?.batteryPercent?.let { "$it%" } ?: "Not connected")
                InfoLine("Update service", when (info?.dfuAvailable) { true -> "Available"; false -> "Not found"; null -> "Connect to check" })
            }
        }

        item { StepPanel(step, vm, onInstall = { confirm = it }) }

        item { AccountPanel(account, enabled = !installing, onSave = vm::saveAccount, onClear = vm::clearAccount, onSignIn = vm::saveSignIn) }

        item {
            Panel {
                SettingRow("Install from a file", "For a firmware image you already have", onClick = if (installing) null else ({ pickFile.launch(arrayOf("*/*")) })) {
                    Icon(PhosphorIcons.FolderOpen, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            Text(
                "Signing in with Zepp happens on Zepp's own page; OpenStrap reads only the resulting app token and user ID " +
                    "and asks Zepp once for your device list. " +
                    "Checking for updates sends your Zepp app token, user ID and the strap's firmware, hardware version and " +
                    "serial number to the update server you set. Nothing else leaves the phone. Downloads are checked against " +
                    "the server's MD5, and the strap checks the image's signature before installing it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun StepPanel(step: FirmwareStep, vm: FirmwareViewModel, onInstall: (ReadyImage) -> Unit) {
    Panel {
        when (step) {
            FirmwareStep.Idle -> {
                PanelHeader("Updates", PhosphorIcons.CloudArrowDown, MetricColors.steps)
                Text("Ask Zepp's update server whether there's newer firmware for this strap.", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = vm::check, modifier = Modifier.fillMaxWidth()) { Text("Check for update") }
            }
            FirmwareStep.Checking -> Busy("Checking for updates…", null)
            is FirmwareStep.Checked -> CheckResult(step.result, vm)
            is FirmwareStep.Downloading -> Busy("Downloading ${step.part.label} ${step.part.version}", step.fraction)
            is FirmwareStep.Ready -> {
                val image = step.image
                PanelHeader("Ready to install", PhosphorIcons.ArrowCircleUp, MetricColors.steps)
                InfoLine("Image", image.title)
                InfoLine("Size", "%,d bytes".format(image.bytes.size))
                image.trailer?.let { InfoLine("Signed for", "product ${it.pnpId} v${it.pnpVersion}") }
                image.warnings.forEach { Warning(it) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = vm::reset, Modifier.weight(1f)) { Text("Cancel") }
                    Button(onClick = { onInstall(image) }, Modifier.weight(1f)) { Text("Install") }
                }
            }
            is FirmwareStep.Installing -> {
                Busy(step.label, step.fraction)
                Text(
                    "Keep the strap close and this screen open. This can take several minutes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FirmwareStep.Installed -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(PhosphorIcons.CheckCircle, null, tint = MetricColors.good)
                    Text("Installed", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    "The strap is restarting. OpenStrap reconnects by itself; check again afterwards in case there are more parts to install.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = vm::reset, Modifier.fillMaxWidth()) { Text("Done") }
            }
            is FirmwareStep.Failed -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(PhosphorIcons.WarningCircle, null, tint = MetricColors.bad)
                    Text("Something went wrong", style = MaterialTheme.typography.titleMedium)
                }
                Text(step.message, style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = vm::reset, Modifier.fillMaxWidth()) { Text("OK") }
            }
        }
    }
}

@Composable
private fun CheckResult(result: UpdateCheck, vm: FirmwareViewModel) {
    if (result.upToDate) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(PhosphorIcons.CheckCircle, null, tint = MetricColors.good)
            Text("Up to date", style = MaterialTheme.typography.titleMedium)
        }
        Text("The update server has nothing newer for this strap.", style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = vm::reset, Modifier.fillMaxWidth()) { Text("OK") }
        return
    }
    val next = result.installOrder.firstOrNull()
    PanelHeader(if (result.forced) "Required update" else "Update available", PhosphorIcons.CloudArrowDown, MetricColors.steps)
    result.parts.forEach { InfoLine(it.label, "${it.version} · ${"%,d".format(it.length / 1024)} KB") }
    result.changeLog?.let {
        Text(it.substringBefore("###summary###").trim(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (result.installOrder.size > 1) {
        Text(
            "Parts are installed one at a time. After the strap restarts, check again to install the next one.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = vm::reset, Modifier.weight(1f)) { Text("Not now") }
        Button(onClick = { next?.let(vm::download) }, enabled = next != null, modifier = Modifier.weight(1f)) {
            Text(if (next == null) "Unsupported" else "Download", maxLines = 1, textAlign = TextAlign.Center)
        }
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
            confirmLabel = "Save sign-in",
            onDismiss = { signIn = false },
            onConfirm = { listing, strap -> signIn = false; onSignIn(listing, strap) },
        )
    }
    Panel {
        PanelHeader("Zepp account", PhosphorIcons.Key, MetricColors.warn, trailing = if (account != null) "Saved" else null)
        if (!editing) {
            Text(
                if (account == null) "Zepp only answers update checks for signed-in users. Sign in, or paste your own app token and user ID; they're stored on this phone only."
                else "Update checks use your saved token${if (account.userId.isNotEmpty()) " for user ${account.userId}" else ""} on ${account.apiBase}.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = { signIn = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text(if (account == null) "Sign in with Zepp" else "Sign in again")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { editing = true }, enabled = enabled, modifier = Modifier.weight(1f)) {
                    Text(if (account == null) "Paste token" else "Edit")
                }
                if (account != null) {
                    TextButton(
                        onClick = onClear, enabled = enabled, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("Remove") }
                }
            }
            return@Panel
        }
        var token by rememberSaveable { mutableStateOf("") }
        var user by rememberSaveable { mutableStateOf(account?.userId.orEmpty()) }
        var api by rememberSaveable { mutableStateOf(account?.apiBase ?: ZeppAccount.DEFAULT_API_BASE) }
        var china by rememberSaveable { mutableStateOf(account?.china == true) }
        OutlinedTextField(
            token, { token = it }, label = { Text("App token") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
            placeholder = { if (account != null) Text("Leave empty to keep the saved token") },
        )
        OutlinedTextField(
            user, { user = it }, label = { Text("User ID") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(api, { api = it }, label = { Text("Server") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Chinese-market strap", style = MaterialTheme.typography.bodyLarge)
                Text("Only if the strap was bought in mainland China", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(china, { china = it })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { editing = false }, Modifier.weight(1f)) { Text("Cancel") }
            Button(
                onClick = { onSave(token.ifBlank { account?.appToken.orEmpty() }, user, api, china); editing = false },
                enabled = token.isNotBlank() || account != null,
                modifier = Modifier.weight(1f),
            ) { Text("Save") }
        }
    }
}

@Composable
private fun ConfirmInstall(image: ReadyImage, blockers: List<String>, onDismiss: () -> Unit, onInstall: () -> Unit) {
    var accepted by remember { mutableStateOf(image.warnings.isEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(if (image.warnings.isEmpty()) PhosphorIcons.ArrowCircleUp else PhosphorIcons.Warning, null) },
        title = { Text("Install ${image.title}?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (blockers.isNotEmpty()) {
                    blockers.forEach { Warning(it) }
                } else {
                    Text("The strap will receive the image, check it, install it and restart. Keep it near the phone and don't close OpenStrap until it's done.")
                    image.warnings.forEach { Warning(it) }
                    if (image.warnings.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(accepted, { accepted = it })
                            Text("I understand and want to install it anyway", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onInstall, enabled = blockers.isEmpty() && accepted) { Text("Install") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Busy(label: String, fraction: Float?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (fraction == null) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        fraction?.let { Text("${(it * 100).toInt()}%", style = MaterialTheme.typography.titleMedium) }
    }
    if (fraction != null) LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Warning(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(PhosphorIcons.Warning, null, tint = MetricColors.warn, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
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
