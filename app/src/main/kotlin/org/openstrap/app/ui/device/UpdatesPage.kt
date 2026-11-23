@file:Suppress("MagicNumber") // the panel colours are the boards' hex values

package org.openstrap.app.ui.device

import org.openstrap.app.ui.glow.HaloCard
import org.openstrap.app.ui.glow.HaloBanner
import org.openstrap.app.ui.glow.HaloPageHeader
import org.openstrap.app.ui.glow.HaloPageList
import org.openstrap.app.ui.glow.HaloTitle
import org.openstrap.app.ui.glow.PageVariant
import org.openstrap.app.ui.glow.TitleVariant
import org.openstrap.app.ui.theme.Space
import org.openstrap.app.ui.theme.Greys
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.openstrap.app.R
import org.openstrap.app.ui.glow.SwitchRow
import org.openstrap.app.ui.glow.GlowType
import org.openstrap.app.ui.glow.BannerKind
import org.openstrap.app.ui.glow.ActionRow
import org.openstrap.app.ui.glow.PageGroup
import org.openstrap.app.ui.glow.NavRow
import org.openstrap.app.ui.theme.AppIcons
import org.openstrap.app.ui.theme.Ink
import org.openstrap.app.update.Check
import org.openstrap.app.update.Install
import org.openstrap.app.update.NoteBlock
import org.openstrap.app.update.NoteSpan
import org.openstrap.app.update.Release
import org.openstrap.app.update.ReleaseNotes
import org.openstrap.app.update.UpdateCopy
import org.openstrap.app.update.Releases
import org.openstrap.app.update.UpdateState

/**
 * The Updates page, from the You page or an update notification: the newest release to install when there is one,
 * Check now, the daily check's switch and the releases on GitHub, then the release notes in one panel. Those are every
 * release newer than this one, newest first, or else this version's own.
 */
@Composable
fun UpdatesPage(
    state: UpdateState,
    installed: String,
    auto: Boolean,
    /** A workout is recording: installing would close it, so it waits. */
    recording: Boolean,
    contentPadding: PaddingValues,
    onInstall: (Release) -> Unit,
    onCheck: () -> Unit,
    onAuto: (Boolean) -> Unit,
    onGitHub: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    // Read again whenever the state changes, so "today" follows the last check.
    val now = remember(state) { System.currentTimeMillis() / 1000 }
    val newer = Releases.newer(state.releases, installed)
    val newest = newer.firstOrNull()
    // A build ahead of every release (a prerelease, or a developer's) says so, rather than listing an older "latest".
    val latest = state.releases.firstOrNull()
    val ahead = latest != null && newer.isEmpty() && Releases.version(installed)?.let { Releases.compare(it, latest.version) > 0 } == true
    val busy = state.install is Install.Downloading || state.install is Install.Installing
    HaloPageList(contentPadding, variant = PageVariant.Settings) {
        item { HaloPageHeader(stringResource(R.string.you_updates_title), onBack) }
        // An update ready is news, so it heads the page as a banner with Install (HaloUpdates board, B1).
        if (newest != null) item {
            val waits = busy || (recording && newest.apk != null)
            HaloBanner(
                if (state.install is Install.Failed) BannerKind.Warn else BannerKind.Good,
                stringResource(R.string.you_updates_ready, newest.versionName),
                note = UpdateCopy.install(newest, state.install, recording),
                action = if (waits) null else stringResource(R.string.you_updates_install),
                onAction = { onInstall(newest) }, icon = AppIcons.DownloadSimple,
            )
        }
        item {
            PageGroup(
                footnote = if (ahead && latest != null) {
                    stringResource(R.string.you_updates_footnote_ahead, UpdateCopy.checkStatus(state, now), installed, latest.versionName)
                } else {
                    UpdateCopy.footnote(state, installed, now)
                },
            ) {
                ActionRow(
                    stringResource(R.string.you_updates_check_now), onCheck, icon = AppIcons.Refresh04,
                    busy = state.check == Check.Checking, divider = false,
                )
                SwitchRow(stringResource(R.string.you_updates_daily), stringResource(R.string.you_updates_daily_note), auto, onAuto)
            }
        }
        item {
            PageGroup {
                NavRow(
                    stringResource(R.string.you_updates_all_releases), stringResource(R.string.you_updates_on_github), onClick = onGitHub,
                    below = true, divider = false, icon = AppIcons.Info,
                )
            }
        }
        val current = Releases.current(state.releases, installed)
        // A build newer than any release (a developer's) shows the latest release's notes.
        val shown = newer.ifEmpty { listOfNotNull(current ?: state.releases.firstOrNull()) }
        if (shown.isNotEmpty()) item {
            val title = stringResource(
                when {
                    newer.isNotEmpty() -> R.string.you_updates_whats_new
                    current != null -> R.string.you_updates_in_your_version
                    else -> R.string.you_updates_latest
                },
            )
            // The release notes are a text panel (rules B3, row type 7): a plain panel, no rows.
            Column(verticalArrangement = Arrangement.spacedBy(Space.S8)) {
                HaloTitle(title, variant = TitleVariant.Group)
                HaloCard {
                    shown.forEachIndexed { i, release ->
                        if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Greys.Raised))
                        ReleaseNotesView(release)
                    }
                }
            }
        }
    }
}

/** One release: its version and date, then its notes as headings, bullets and paragraphs. */
@Composable
private fun ReleaseNotesView(release: Release) {
    val blocks = remember(release.notes) { ReleaseNotes.parse(release.notes) }
    Column(verticalArrangement = Arrangement.spacedBy(Space.S8)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(release.versionName, color = Color.White, style = GlowType.body.copy(fontWeight = FontWeight.Medium),
                modifier = Modifier.weight(1f))
            release.publishedAt?.let {
                Text(UpdateCopy.date(it), color = Ink.Secondary, style = GlowType.secondary)
            }
        }
        if (blocks.isEmpty()) {
            Text(
                stringResource(R.string.you_updates_no_notes), color = Ink.Secondary,
                style = GlowType.secondary,
            )
        }
        blocks.forEachIndexed { i, block -> when (block) {
            is NoteBlock.Heading -> Text(
                spans(block.spans), color = Color.White, style = GlowType.body.copy(fontWeight = FontWeight.Medium),
                    modifier = Modifier.padding(top = Space.S8),
            )
            // The opening paragraph leads, larger; any later one reads like the bullets.
            is NoteBlock.Paragraph ->
                if (i == 0) Text(spans(block.spans), color = Ink.Strong, style = GlowType.body)
                else Text(spans(block.spans), color = Ink.Strong, style = GlowType.secondary)
            is NoteBlock.Bullet -> Row {
                Text("•", color = Ink.Tertiary, style = GlowType.body, modifier = Modifier.width(16.dp))
                Text(spans(block.spans), color = Ink.Strong, style = GlowType.secondary,
                    modifier = Modifier.padding(top = 1.dp))
            }
        } }
    }
}

/** Bold runs in white at medium weight, the rest in the text's own colour. */
private fun spans(spans: List<NoteSpan>): AnnotatedString = buildAnnotatedString {
    for (s in spans) {
        if (s.bold) withStyle(SpanStyle(fontWeight = FontWeight.Medium, color = Color.White)) { append(s.text) } else append(s.text)
    }
}
