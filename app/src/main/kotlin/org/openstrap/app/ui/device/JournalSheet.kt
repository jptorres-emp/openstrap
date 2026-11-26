@file:Suppress("MatchingDeclarationName")

package org.openstrap.app.ui.device

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.openstrap.app.R
import org.openstrap.app.data.JournalHabit
import org.openstrap.app.ui.glow.ButtonPair
import org.openstrap.app.ui.glow.CardFill
import org.openstrap.app.ui.glow.HaloCard
import org.openstrap.app.ui.glow.HaloSheet
import org.openstrap.app.ui.glow.SwitchRow

/** What a journal day has ticked, as one stable value for Compose. */
@Immutable
class JournalTicks(val habits: Set<JournalHabit>)

/**
 * Yesterday's journal in a sheet (the fork's, jptorres-emp/openstrap), from the You page and the Reports page: a
 * switch for each habit, filled in with what was ticked ([ticked]), then Cancel and Save. Save keeps them, even with
 * none ticked (the day then counts as one without them, [onSave]); Cancel, or pulling the sheet down, changes nothing.
 */
@Composable
internal fun JournalSheet(ticked: JournalTicks, onSave: (Set<JournalHabit>) -> Unit, onDismiss: () -> Unit) {
    var habits by remember(ticked) { mutableStateOf(ticked.habits) }
    HaloSheet(
        onDismiss, stringResource(R.string.fork_journal_title), note = stringResource(R.string.fork_journal_note),
        footer = {
            ButtonPair(
                stringResource(R.string.you_action_cancel), onDismiss, stringResource(R.string.you_action_save), { onSave(habits) },
            )
        },
    ) {
        JournalContent(JournalTicks(habits), { h, on -> habits = if (on) habits + h else habits - h })
    }
}

/** The journal sheet's switches, one a habit with what counts under its name, in one card of rows. */
@Composable
internal fun JournalContent(ticked: JournalTicks, onTick: (JournalHabit, Boolean) -> Unit, modifier: Modifier = Modifier) {
    HaloCard(modifier, fill = CardFill.Rows) {
        JournalHabit.entries.forEachIndexed { i, h ->
            SwitchRow(stringResource(h.label), stringResource(h.hint), h in ticked.habits, { on -> onTick(h, on) }, divider = i > 0)
        }
    }
}
