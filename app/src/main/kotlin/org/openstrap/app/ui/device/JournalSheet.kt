package org.openstrap.app.ui.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.openstrap.app.R
import org.openstrap.app.data.JournalHabit
import org.openstrap.app.ui.glow.PageButton
import org.openstrap.app.ui.glow.PageRows
import org.openstrap.app.ui.glow.PageSheet
import org.openstrap.app.ui.glow.PageSwitchRow

/** What a journal day has ticked, as one stable value for Compose. */
@Immutable
class JournalTicks(val habits: Set<JournalHabit>)

/**
 * Yesterday's journal in a sheet (the fork's, jptorres-emp/openstrap), from the You page and the Reports page: a
 * switch for each habit, filled in with what was ticked ([ticked]). Save keeps them, even with none ticked (the day
 * then counts as one without them, [onSave]); Cancel, or pulling the sheet down, changes nothing.
 */
@Composable
internal fun JournalSheet(ticked: JournalTicks, onSave: (Set<JournalHabit>) -> Unit, onDismiss: () -> Unit) {
    var habits by remember(ticked) { mutableStateOf(ticked.habits) }
    PageSheet(onDismiss, stringResource(R.string.fork_journal_title), note = stringResource(R.string.fork_journal_note)) {
        JournalContent(JournalTicks(habits), { h, on -> habits = if (on) habits + h else habits - h })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PageButton(stringResource(R.string.glow_cancel), onDismiss, Modifier.weight(1f))
            PageButton(stringResource(R.string.glow_save), { onSave(habits) }, Modifier.weight(1f), primary = true)
        }
    }
}

/** The journal sheet's switches, one a habit with what counts under its name, without the sheet and its buttons. */
@Composable
internal fun JournalContent(ticked: JournalTicks, onTick: (JournalHabit, Boolean) -> Unit) {
    PageRows {
        JournalHabit.entries.forEachIndexed { i, h ->
            PageSwitchRow(stringResource(h.label), stringResource(h.hint), h in ticked.habits, { on -> onTick(h, on) }, divider = i > 0)
        }
    }
}
