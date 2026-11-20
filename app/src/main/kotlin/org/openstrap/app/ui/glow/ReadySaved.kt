package org.openstrap.app.ui.glow

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.openstrap.app.R
import org.openstrap.app.data.Structure
import org.openstrap.app.data.db.SavedWorkout
import org.openstrap.app.ui.theme.AppIcons
import org.openstrap.app.ui.theme.Ink
import org.openstrap.app.ui.theme.Space
import org.openstrap.app.ui.workout.StartCopy

/*
 * Run intervals saved by name (boards HaloReadyEveryKind and HaloSaveIntervals): Save intervals and Saved under the
 * rep and rest steppers. Saved ones are also offered on Choose.
 */

private const val SHEET_NONE = 0
private const val SHEET_SAVE = 1
private const val SHEET_SAVED = 2
private const val NAME_MAX = 40

/**
 * Save intervals, which names [reps] in a sheet and hands the name to [onSave], and Saved when there are any
 * ([saved], the sport's), which lists them and opens the one picked ([onReps]).
 */
@Suppress("UnstableCollections", "ComposeUnstableCollections")
@Composable
internal fun SavedIntervals(reps: Structure.Reps, saved: List<SavedWorkout>, onReps: (Structure.Reps) -> Unit, onSave: (String) -> Unit) {
    var sheet by rememberSaveable { mutableIntStateOf(SHEET_NONE) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.S8, androidx.compose.ui.Alignment.CenterHorizontally)) {
        HaloButton(
            stringResource(R.string.ready_save_intervals), { sheet = SHEET_SAVE }, size = HaloButtonSize.Card, icon = AppIcons.Plus,
        )
        if (saved.isNotEmpty()) {
            HaloButton(
                stringResource(R.string.ready_saved_intervals), { sheet = SHEET_SAVED }, size = HaloButtonSize.Card,
                icon = AppIcons.FolderOpen,
            )
        }
    }
    when (sheet) {
        SHEET_SAVE -> SaveIntervalsSheet(reps, { onSave(it); sheet = SHEET_NONE }) { sheet = SHEET_NONE }
        SHEET_SAVED -> HaloSheet({ sheet = SHEET_NONE }, stringResource(R.string.ready_saved_intervals)) {
            Column {
                saved.forEachIndexed { i, w ->
                    val s = Structure.parse(w.structure) as? Structure.Reps ?: return@forEachIndexed
                    NavRow(w.name, value = StartCopy.structureName(s), divider = i > 0, onClick = { onReps(s); sheet = SHEET_NONE })
                }
            }
        }
    }
}

/** Naming [reps] to save them (board HaloSaveIntervals): the title, the reps under it, the name, the note, Cancel and Save. */
@Composable
private fun SaveIntervalsSheet(reps: Structure.Reps, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    HaloSheet(
        onDismiss, stringResource(R.string.ready_save_intervals), note = stringResource(R.string.ready_save_note),
        footer = {
            ButtonPair(
                stringResource(R.string.glow_cancel), onDismiss, stringResource(R.string.glow_save), { onSave(name.trim()) },
                rightEnabled = name.isNotBlank(),
            )
        },
    ) {
        Text(StartCopy.structureName(reps), color = Ink.Secondary, style = GlowType.body)
        PageField(name, { name = it.take(NAME_MAX) }, null, placeholder = stringResource(R.string.ready_name_hint))
    }
}
