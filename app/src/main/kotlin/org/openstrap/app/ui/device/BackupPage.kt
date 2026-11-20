package org.openstrap.app.ui.device

import org.openstrap.app.ui.glow.HaloBanner
import org.openstrap.app.ui.glow.HaloPageHeader
import org.openstrap.app.ui.glow.HaloPageList
import org.openstrap.app.ui.glow.PageVariant
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import org.openstrap.app.R
import org.openstrap.app.data.settings.BackupState
import org.openstrap.app.ui.glow.GlowLayout
import org.openstrap.app.ui.glow.ActionRow
import org.openstrap.app.ui.glow.BannerKind
import org.openstrap.app.ui.glow.PageGroup
import org.openstrap.app.ui.glow.SwitchRow
import org.openstrap.app.ui.glow.NavRow
import org.openstrap.app.ui.theme.AppIcons

/**
 * The Backup page, from the You page, in one panel: automatic backup (the switch, with the last backup or why it failed
 * under it, then, while on, the folder and Back up now), one Restore for any zip, and a copy saved by hand where the
 * user chooses, which the keep-7 rule never clears. Turning backup on asks for the folder first ([onTurnOn]).
 */
@Composable
fun BackupPage(
    state: BackupState,
    backingUp: Boolean,
    contentPadding: PaddingValues,
    onTurnOn: () -> Unit,
    onTurnOff: () -> Unit,
    onFolder: () -> Unit,
    onBackUpNow: () -> Unit,
    onRestore: () -> Unit,
    onSaveCopy: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    // Read again whenever the state changes, so "today" follows the last backup.
    val now = remember(state, backingUp) { System.currentTimeMillis() / 1000 }
    val toggle = { on: Boolean -> if (on) onTurnOn() else onTurnOff() }
    HaloPageList(contentPadding, variant = PageVariant.Settings) {
        item { HaloPageHeader(stringResource(R.string.you_backup_title), onBack) }
        // A failed backup is news, so it heads the page as a banner (HaloBackup board, B1).
        state.failed?.let { failure ->
            item {
                HaloBanner(
                    BannerKind.Warn, stringResource(R.string.you_backup_banner_failed),
                    note = BackupCopy.failureShort(failure), action = stringResource(R.string.you_alarms_try_again),
                    onAction = onBackUpNow,
                )
            }
        }
        // Groups as on the HaloBackup board: the switch, the folder and Back up now; Restore; Save a copy.
        item {
            PageGroup(
                footnote = if (state.on) stringResource(R.string.you_backup_now_note) else null,
            ) {
                SwitchRow(
                    stringResource(R.string.you_backup_automatic), BackupCopy.status(state.copy(failed = null),
                        now), state.on, toggle, divider = false,
                    icon = AppIcons.ShieldCheck,
                )
                if (state.on) {
                    NavRow(stringResource(R.string.you_backup_folder), BackupCopy.folder(state), onClick = onFolder)
                    ActionRow(
                        stringResource(if (backingUp) R.string.you_backup_now_running else R.string.you_backup_now), onBackUpNow,
                        icon = AppIcons.CloudArrowDown, busy = backingUp,
                    )
                }
            }
        }
        item {
            PageGroup(footnote = stringResource(R.string.you_backup_restore_note)) {
                ActionRow(
                    stringResource(R.string.you_action_restore), onRestore, icon = AppIcons.ArrowCounterClockwise, divider = false,
                )
            }
        }
        item {
            PageGroup(footnote = stringResource(R.string.you_backup_save_copy_note)) {
                ActionRow(stringResource(R.string.you_backup_save_copy), onSaveCopy, icon = AppIcons.DownloadSimple, divider = false)
            }
        }
    }
}

/** The picker's Downloads view, which lists the newest files first, so a zip just saved is at the top. */
private val DOWNLOADS: android.net.Uri =
    android.provider.DocumentsContract.buildDocumentUri("com.android.providers.downloads.documents", "downloads")

/** The file picker for Save a copy, opening on Downloads. */
class SaveBackup : androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip") {
    override fun createIntent(context: android.content.Context, input: String): android.content.Intent =
        super.createIntent(context, input).putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, DOWNLOADS)
}

/** The file picker for Restore, opening on the backup folder (a tree URI) when there is one, else on Downloads. */
class PickBackup : androidx.activity.result.contract.ActivityResultContract<android.net.Uri?, android.net.Uri?>() {
    override fun createIntent(context: android.content.Context, input: android.net.Uri?): android.content.Intent =
        android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(android.content.Intent.CATEGORY_OPENABLE)
            .setType("*/*")
            .putExtra(android.content.Intent.EXTRA_MIME_TYPES, arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
            .putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, input?.let(::folderDocument) ?: DOWNLOADS)

    private fun folderDocument(tree: android.net.Uri): android.net.Uri? = runCatching {
        android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, android.provider.DocumentsContract.getTreeDocumentId(tree))
    }.getOrNull()

    override fun parseResult(resultCode: Int, intent: android.content.Intent?): android.net.Uri? =
        intent?.data?.takeIf { resultCode == android.app.Activity.RESULT_OK }
}
