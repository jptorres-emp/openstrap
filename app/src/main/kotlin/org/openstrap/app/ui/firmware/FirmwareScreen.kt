package org.openstrap.app.ui.firmware

import org.openstrap.app.ui.glow.HaloNote
import org.openstrap.app.ui.glow.HaloProgress
import org.openstrap.app.ui.glow.BannerKind
import org.openstrap.app.ui.glow.HaloBanner
import org.openstrap.app.ui.glow.HaloCard
import org.openstrap.app.ui.glow.HaloPageHeader
import org.openstrap.app.ui.glow.HaloPageList
import org.openstrap.app.ui.glow.HaloTitle
import org.openstrap.app.ui.glow.PageVariant
import org.openstrap.app.ui.theme.IconSize
import org.openstrap.app.ui.theme.Space
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.ui.res.stringResource
import org.openstrap.app.R
import org.openstrap.app.data.settings.ZeppAccount
import org.openstrap.app.device.ConnectionState
import org.openstrap.app.firmware.UpdateCheck
import org.openstrap.app.presentation.Units
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import org.openstrap.app.ui.glow.ButtonPair
import org.openstrap.app.ui.glow.HaloButton
import org.openstrap.app.ui.glow.HaloButtonVariant
import org.openstrap.app.ui.glow.GlowLayout
import org.openstrap.app.ui.glow.GlowType
import org.openstrap.app.ui.glow.ActionRow
import org.openstrap.app.ui.glow.PageGroup
import org.openstrap.app.ui.glow.InfoRow
import org.openstrap.app.ui.glow.HaloConfirmSheet
import org.openstrap.app.ui.glow.PageField
import org.openstrap.app.ui.glow.NavRow
import org.openstrap.app.ui.glow.SwitchRow
import org.openstrap.app.ui.theme.Ink
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.app.ui.theme.AppIcons
import org.openstrap.app.ui.zepp.ZeppSignInDialog
import org.openstrap.app.zepp.BoundDevice
import org.openstrap.app.zepp.DeviceListing

/**
 * The strap's firmware: what it runs, checking for and installing an update, or installing from a file. Opened from
 * the Strap page. The Zepp account the update check signs in with has its own page ([ZeppAccountScreen]).
 */
@Composable
fun FirmwareScreen(vm: FirmwareViewModel, contentPadding: PaddingValues, onBack: () -> Unit, onZeppAccount: () -> Unit) {
    val step by vm.step.collectAsStateWithLifecycle()
    val account by vm.account.collectAsStateWithLifecycle()
    val connection by vm.connection.collectAsStateWithLifecycle()
    val info by vm.info.collectAsStateWithLifecycle()
    val installing = step is FirmwareStep.Installing

    // Leaving mid-install would be harmless for the strap, but keep the user here and the screen on.
    BackHandler { if (!installing) { vm.reset(); onBack() } }
    KeepScreenOn(installing)

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::loadFile) }
    var confirm by remember { mutableStateOf<ReadyImage?>(null) }
    confirm?.let { image -> ConfirmInstall(image, vm.blockers(), onDismiss = { confirm = null },
        onInstall = { confirm = null; vm.install(image) }) }

    HaloPageList(contentPadding, variant = PageVariant.Settings) {
        item { HaloPageHeader(stringResource(R.string.you_firmware_title), { vm.reset(); onBack() }, backEnabled = !installing) }

        // This strap: read only values as info rows (HaloFirmware board).
        item {
            PageGroup(heading = stringResource(R.string.you_firmware_this_strap)) {
                val c = connection as? ConnectionState.Connected
                InfoRow(
                    stringResource(R.string.you_firmware_title),
                    info?.firmware ?: c?.firmware ?: stringResource(R.string.you_firmware_unknown), divider = false,
                )
                info?.hardware?.let { InfoRow(stringResource(R.string.you_firmware_hardware), it) }
                InfoRow(
                    stringResource(R.string.you_firmware_battery),
                    c?.batteryPercent?.let { stringResource(R.string.you_firmware_percent, it) }
                        ?: stringResource(R.string.you_firmware_not_connected),
                )
                InfoRow(
                    stringResource(R.string.you_firmware_service),
                    stringResource(
                        when (info?.dfuAvailable) {
                            true -> R.string.you_firmware_service_available
                            false -> R.string.you_firmware_service_not_found
                            null -> R.string.you_firmware_service_unknown
                        },
                    ),
                )
            }
        }

        // While a check, download or install is under way (or has an answer to read) its panel stands in for the Check row.
        if (step != FirmwareStep.Idle) item { StepPanel(step, vm, onInstall = { confirm = it }) }

        item {
            PageGroup(heading = stringResource(R.string.you_firmware_group_new),
                footnote = stringResource(R.string.you_firmware_updates_text)) {
                if (step == FirmwareStep.Idle) ActionRow(
                    stringResource(R.string.you_firmware_check), vm::check, icon = AppIcons.Refresh04, divider = false,
                )
                ActionRow(
                    stringResource(R.string.you_firmware_from_file), { pickFile.launch(arrayOf("*/*")) },
                    icon = AppIcons.DownloadSimple, enabled = !installing, divider = step == FirmwareStep.Idle,
                )
            }
        }

        // Update checks need a signed-in Zepp account; its page is a row away, as on the Strap page.
        item {
            PageGroup(heading = stringResource(R.string.you_firmware_group_updates)) {
                NavRow(
                    stringResource(R.string.you_zepp_title),
                    stringResource(if (account != null) R.string.you_firmware_account_signed_in else R.string.you_strap_not_signed_in),
                    onClick = onZeppAccount, below = true, divider = false, enabled = !installing, icon = AppIcons.Key,
                )
            }
        }

        item {
            HaloCard(title = { HaloTitle(stringResource(R.string.you_firmware_privacy)) }) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.S12)) {
                    Point(
                        AppIcons.Key, stringResource(R.string.you_firmware_privacy_sign_in),
                        stringResource(R.string.you_firmware_privacy_sign_in_text),
                    )
                    Point(
                        AppIcons.CloudArrowDown, stringResource(R.string.you_firmware_privacy_check),
                        stringResource(R.string.you_firmware_privacy_check_text),
                    )
                    Point(
                        AppIcons.CheckCircle, stringResource(R.string.you_firmware_privacy_install),
                        stringResource(R.string.you_firmware_privacy_install_text),
                    )
                }
            }
        }
    }
}

@Composable
private fun StepPanel(step: FirmwareStep, vm: FirmwareViewModel, onInstall: (ReadyImage) -> Unit) {
    HaloCard {
        when (step) {
            FirmwareStep.Idle -> Unit
            FirmwareStep.Checking -> Busy(stringResource(R.string.you_firmware_checking), null)
            is FirmwareStep.Checked -> CheckResult(step.result, vm)
            is FirmwareStep.Downloading -> Busy(
                stringResource(R.string.you_firmware_downloading, step.part.label, step.part.version), step.fraction,
            )
            is FirmwareStep.Ready -> {
                val image = step.image
                HaloTitle(stringResource(R.string.you_firmware_ready))
                InfoRow(stringResource(R.string.you_firmware_image), image.title)
                InfoRow(
                    stringResource(R.string.you_firmware_size),
                    stringResource(R.string.you_firmware_megabytes, image.bytes.size / BYTES_PER_MB),
                )
                image.trailer?.let {
                    InfoRow(
                        stringResource(R.string.you_firmware_signed_for),
                        stringResource(R.string.you_firmware_signed_for_value, it.pnpId, it.pnpVersion),
                    )
                }
                image.warnings.forEach { HaloBanner(BannerKind.Warn, it, framed = false) }
                ButtonPair(
                    stringResource(R.string.you_action_cancel),
                    vm::reset,
                    stringResource(R.string.you_firmware_install),
                    { onInstall(image) },
                )
            }
            is FirmwareStep.Installing -> {
                Busy(step.label, step.fraction)
                HaloNote(stringResource(R.string.you_firmware_installing_note))
            }
            FirmwareStep.Installed -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S8)) {
                    Icon(AppIcons.CheckCircle, null, tint = MetricColors.good)
                    HaloTitle(stringResource(R.string.you_firmware_installed))
                }
                Body(stringResource(R.string.you_firmware_installed_text))
                HaloButton(stringResource(R.string.you_firmware_done), vm::reset, Modifier.fillMaxWidth())
            }
            is FirmwareStep.Failed -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S8)) {
                    Icon(AppIcons.WarningCircle, null, tint = MetricColors.warn)
                    HaloTitle(stringResource(R.string.you_firmware_failed))
                }
                Body(step.message)
                HaloButton(stringResource(R.string.you_firmware_done), vm::reset, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun CheckResult(result: UpdateCheck, vm: FirmwareViewModel) {
    if (result.upToDate) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S8)) {
            Icon(AppIcons.CheckCircle, null, tint = MetricColors.good)
            HaloTitle(stringResource(R.string.you_firmware_up_to_date))
        }
        Body(stringResource(R.string.you_firmware_up_to_date_text))
        HaloButton(stringResource(R.string.you_firmware_done), vm::reset, Modifier.fillMaxWidth())
        return
    }
    val next = result.installOrder.firstOrNull()
    HaloTitle(stringResource(if (result.forced) R.string.you_firmware_required else R.string.you_firmware_available))
    result.parts.forEach { InfoRow(it.label, stringResource(R.string.you_firmware_part_size, it.version, Units.count(it.length / 1024))) }
    result.changeLog?.let { HaloNote(it.substringBefore("###summary###").trim()) }
    if (result.installOrder.size > 1) {
        HaloNote(stringResource(R.string.you_firmware_parts_note))
    }
    ButtonPair(
        stringResource(R.string.you_firmware_not_now),
        vm::reset,
        stringResource(if (next == null) R.string.you_firmware_unsupported else R.string.you_firmware_download),
        { next?.let(vm::download) },
        rightEnabled = next != null,
    )
}

/**
 * The Zepp account the firmware check signs in with: sign in, paste a token, or remove it. Opened from the Strap page
 * (and from Firmware). Kept on this phone only.
 */
@Composable
fun ZeppAccountScreen(vm: FirmwareViewModel, contentPadding: PaddingValues, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val account by vm.account.collectAsStateWithLifecycle()
    val installing = vm.step.collectAsStateWithLifecycle().value is FirmwareStep.Installing
    HaloPageList(contentPadding, variant = PageVariant.Settings) {
        item { HaloPageHeader(stringResource(R.string.you_zepp_title), onBack) }
        item { AccountPanel(account, enabled = !installing, onSave = vm::saveAccount, onClear = vm::clearAccount,
            onSignIn = vm::saveSignIn) }
    }
}

/** What the Zepp account panel is doing: one value, so editing the token and signing in can't both be on. */
private enum class AccountMode { Idle, EditingToken, SigningIn }

@Composable
private fun AccountPanel(
    account: ZeppAccount?,
    enabled: Boolean,
    onSave: (String, String, String, Boolean) -> Unit,
    onClear: () -> Unit,
    onSignIn: (DeviceListing, BoundDevice?) -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf(AccountMode.Idle) }
    if (mode == AccountMode.SigningIn) {
        ZeppSignInDialog(
            preferredApiBase = account?.apiBase,
            confirmLabel = stringResource(R.string.you_zepp_save_sign_in),
            onDismiss = { mode = AccountMode.Idle },
            onConfirm = { listing, strap -> mode = AccountMode.Idle; onSignIn(listing, strap) },
        )
    }
    if (mode != AccountMode.EditingToken) {
        // The saved sign-in as read only rows, then what can be done with it (HaloZeppAccount board).
        Column(verticalArrangement = Arrangement.spacedBy(Space.S24)) {
            if (account == null) HaloNote(stringResource(R.string.you_zepp_account_none))
            else PageGroup(heading = stringResource(R.string.you_zepp_saved), footnote = stringResource(R.string.you_zepp_token_stays)) {
                val hasUser = account.userId.isNotEmpty()
                if (hasUser) InfoRow(stringResource(R.string.you_zepp_user), account.userId, divider = false)
                InfoRow(stringResource(R.string.you_zepp_server), account.apiBase.removePrefix("https://"), divider = hasUser)
                InfoRow(
                    stringResource(R.string.you_zepp_china),
                    stringResource(if (account.china) R.string.you_value_on else R.string.you_value_off),
                )
            }
            PageGroup {
                ActionRow(
                    stringResource(if (account == null) R.string.you_pair_sign_in else R.string.you_zepp_sign_in_again),
                    { mode = AccountMode.SigningIn }, icon = AppIcons.Key, enabled = enabled, divider = false,
                )
                NavRow(
                    stringResource(if (account == null) R.string.you_zepp_paste_token else R.string.you_zepp_edit),
                    stringResource(R.string.you_zepp_edit_note), onClick = { mode = AccountMode.EditingToken }, below = true,
                    enabled = enabled, icon = AppIcons.SlidersHorizontal,
                )
            }
            if (account != null) PageGroup(footnote = stringResource(R.string.you_zepp_remove_note)) {
                ActionRow(
                    stringResource(R.string.you_zepp_remove), onClear, icon = AppIcons.Trash, destructive = true, enabled = enabled,
                    divider = false,
                )
            }
        }
        return
    }
    HaloCard {
        // Keyed on the account, so a fresh sign-in or renewed token starts the fields again from it and a stale copy
        // can't be written back over it. The pasted token is the secret: plain remember, so it never reaches the saved state.
        var token by remember(account) { mutableStateOf("") }
        var user by rememberSaveable(account) { mutableStateOf(account?.userId.orEmpty()) }
        var api by rememberSaveable(account) { mutableStateOf(account?.apiBase ?: ZeppAccount.DEFAULT_API_BASE) }
        var china by rememberSaveable(account) { mutableStateOf(account?.china == true) }
        PageField(
            token, { token = it }, stringResource(R.string.you_zepp_app_token), Modifier.fillMaxWidth(),
            placeholder = if (account != null) stringResource(R.string.you_zepp_keep_token) else null,
            visualTransformation = PasswordVisualTransformation(),
        )
        PageField(
            user, { user = it }, stringResource(R.string.you_zepp_user_id), Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        PageField(api, { api = it }, stringResource(R.string.you_zepp_server), Modifier.fillMaxWidth())
        SwitchRow(
            stringResource(R.string.you_zepp_china), stringResource(R.string.you_zepp_china_note), china, { china = it }, divider = false,
        )
        ButtonPair(
            stringResource(R.string.you_action_cancel),
            { mode = AccountMode.Idle },
            stringResource(R.string.you_action_save),
            { onSave(token.ifBlank { account?.appToken.orEmpty() }, user, api, china); mode = AccountMode.Idle },
            rightEnabled = token.isNotBlank() || account != null,
        )
    }
}

@Composable
private fun ConfirmInstall(image: ReadyImage, blockers: List<String>, onDismiss: () -> Unit, onInstall: () -> Unit) {
    var accepted by remember { mutableStateOf(image.warnings.isEmpty()) }
    HaloConfirmSheet(
        stringResource(R.string.you_firmware_confirm_title),
        if (blockers.isNotEmpty()) null else stringResource(R.string.you_firmware_confirm_text, image.title),
        confirm = stringResource(R.string.you_firmware_install), onConfirm = onInstall, onDismiss = onDismiss,
        confirmEnabled = blockers.isEmpty() && accepted,
    ) {
        if (blockers.isNotEmpty()) {
            blockers.forEach { HaloBanner(BannerKind.Warn, it, framed = false) }
        } else {
            image.warnings.forEach { HaloBanner(BannerKind.Warn, it, framed = false) }
            if (image.warnings.isNotEmpty()) {
                SwitchRow(stringResource(R.string.you_firmware_confirm_anyway), null, accepted, { accepted = it }, divider = false)
            }
        }
    }
}

@Composable
private fun Busy(label: String, fraction: Float?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S12)) {
        Text(label, style = GlowType.body, color = Color.White, modifier = Modifier.weight(1f))
        fraction?.let {
            Text(stringResource(R.string.you_firmware_percent, (it * 100).toInt()), style = GlowType.body, color = Color.White)
        }
    }
    HaloProgress(fraction)
}

@Composable
private fun Body(text: String) = Text(text, style = GlowType.body, color = Ink.Secondary)

/** One line of the privacy card: icon, short title, one sentence. */
@Composable
private fun Point(icon: ImageVector, title: String, body: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.padding(top = Space.S2).size(20.dp), tint = Ink.Secondary)
        Spacer(Modifier.width(Space.S12))
        Column {
            Text(title, style = GlowType.body, color = Color.White)
            HaloNote(body)
        }
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

/** Bytes in a megabyte, as the update panel counts them ([org.openstrap.app.update.UpdateCopy.megabytes]). */
private const val BYTES_PER_MB = 1_000_000.0
