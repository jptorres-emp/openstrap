package org.openstrap.app.ui.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import org.openstrap.app.data.JournalHabit
import org.openstrap.app.data.Reports
import org.openstrap.app.l10n.tr
import org.openstrap.app.ui.dashboard.JournalCopy
import org.openstrap.app.ui.glow.GlowSwitch
import org.openstrap.app.ui.glow.PageButton
import org.openstrap.app.ui.glow.PageRow
import org.openstrap.app.ui.glow.PageRows
import org.openstrap.app.ui.glow.PageSheet
import org.openstrap.app.ui.theme.AppIcons

/**
 * The journal for [day] ([JournalHabit]), in a sheet: each habit with a switch, and under its name what it does to
 * the morning after once the report can tell ([effects], from the day's report), else what counts. It starts from the
 * day's answer ([current]); Save keeps the ticked ones as the day's answer, even none. Cancel, or pulling the sheet
 * down, changes nothing.
 */
@Composable
fun JournalSheet(
    day: LocalDate, today: LocalDate, current: Set<JournalHabit>?, effects: Map<JournalHabit, Reports.Effect>,
    onSave: (Set<JournalHabit>) -> Unit, onDismiss: () -> Unit,
) {
    var ticked by rememberSaveable(day) { mutableStateOf(current.orEmpty().map { it.key }.toSet()) }
    fun toggle(h: JournalHabit) { ticked = if (h.key in ticked) ticked - h.key else ticked + h.key }
    PageSheet(onDismiss, JournalCopy.title(day, today), note = JournalCopy.NOTE) {
        PageRows {
            JournalHabit.entries.forEachIndexed { i, h ->
                PageRow(h.label, JournalCopy.line(h, effects[h]), onClick = { toggle(h) }, below = true, divider = i > 0, icon = journalIcon(h)) {
                    GlowSwitch(h.key in ticked, { toggle(h) })
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PageButton(tr("Cancel", "Cancelar"), onDismiss, Modifier.weight(1f))
            PageButton(tr("Save", "Guardar"), { onSave(JournalHabit.entries.filter { it.key in ticked }.toSet()) }, Modifier.weight(1f), primary = true)
        }
    }
}

/** Each journal habit's icon, in the sheet and in the report's habits. */
fun journalIcon(h: JournalHabit): ImageVector = when (h) {
    JournalHabit.Alcohol -> AppIcons.Wine
    JournalHabit.LateCaffeine -> AppIcons.Coffee
    JournalHabit.LateMeal -> AppIcons.ForkKnife
    JournalHabit.ScreensInBed -> AppIcons.DeviceMobile
    JournalHabit.ReadingInBed -> AppIcons.Book
    JournalHabit.Stress -> AppIcons.Brain
    JournalHabit.Travel -> AppIcons.Globe
    JournalHabit.Sick -> AppIcons.ThermometerSimple
    JournalHabit.Meditation -> AppIcons.FlowerLotus
    JournalHabit.Stretching -> AppIcons.PersonArmsSpread
}
