package org.openstrap.app.ui.glow

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import org.openstrap.app.R
import org.openstrap.app.ui.dashboard.Info
import org.openstrap.app.ui.theme.Space

/*
 * The info sheet (spec 6.2): what a number means, the scale it's read on with where you are, and how it's worked out,
 * what moves it and why it might be off, folded until asked for. Opened from the (i) on a detail or a hero, over the
 * screen it came from.
 */

/**
 * [info] as a sheet over the screen, lit in [color]. [here] is the bar of [Info.scale] you're in (null leaves every bar
 * dim), [hereLine] the line under the scale ("You are here. Your normal is 44 to 58 ms, from 30 days."), and [notes]
 * what's particular about today, under Why it might be off. [rows], for a sheet that isn't about a number (the workout
 * Ready screen), replaces the three folded rows with its own titles and texts.
 */
@Composable
fun InfoSheet(
    info: Info, color: Color, here: Int?, hereLine: String?, onDismiss: () -> Unit, notes: List<String> = emptyList(),
    rows: List<Pair<String, String>>? = null,
) {
    // The metric's colour is only the 4 px bar beside the title (board HaloInfoSheet).
    HaloSheet(
        onDismiss, info.name, variant = HaloSheetVariant.Info, accent = color,
        footer = { HaloButton(stringResource(R.string.glow_close), onDismiss, Modifier.fillMaxWidth()) },
    ) {
        val sections = rows ?: listOf(
            stringResource(R.string.glow_info_what) to info.meaning,
            stringResource(R.string.glow_info_how) to info.how,
            stringResource(R.string.glow_info_normal) to listOfNotNull(hereLine, info.moves).joinToString(" "),
            stringResource(R.string.glow_info_off) to (listOf(info.off) + notes).joinToString("\n\n"),
        )
        sections.forEachIndexed { i, (title, text) ->
            SheetSection(title, text)
            // Some numbers have no scale to place them on (naps, whose only words are apnea bands); the rest show it
            // under what the number is.
            if (rows == null && i == 0 && info.scale.isNotEmpty()) SheetScale(info.scale, color, here, Modifier.padding(top = Space.S2))
        }
    }
}
