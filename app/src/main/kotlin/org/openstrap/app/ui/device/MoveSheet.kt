package org.openstrap.app.ui.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.openstrap.app.data.MoveReminder
import org.openstrap.app.data.MoveSettings
import org.openstrap.app.l10n.tr
import org.openstrap.app.ui.glow.GlowSegments
import org.openstrap.app.ui.glow.GlowType
import org.openstrap.app.ui.glow.PageButton
import org.openstrap.app.ui.glow.PageNote
import org.openstrap.app.ui.glow.PageSheet

/**
 * The move reminder's choices, in a sheet over the Strap page: the working hours, how long without steps before the
 * strap buzzes, and weekdays or every day. Saving turns it on. Cancel, or pulling the sheet down, changes nothing.
 */
@Composable
fun MoveSheet(current: MoveSettings, onSave: (MoveSettings) -> Unit, onDismiss: () -> Unit) {
    var hours by rememberSaveable { mutableIntStateOf(MoveReminder.HOURS.indexOf(current.startMinute to current.endMinute).coerceAtLeast(0)) }
    var every by rememberSaveable { mutableIntStateOf(MoveReminder.EVERY.indexOf(current.everyMinutes).coerceAtLeast(0)) }
    var weekdays by rememberSaveable { mutableStateOf(current.weekdaysOnly) }
    PageSheet(onDismiss, MoveCopy.TITLE, note = tr(
        "The strap buzzes when you've taken almost no steps for a while, and the phone says so. Get up and walk for 2 or 3 minutes: it adds up over a desk day.",
        "A strap vibra quando passaste algum tempo quase sem passos, e o telemóvel avisa. Levanta-te e anda 2 ou 3 minutos: ao longo de um dia à secretária soma.",
    )) {
        Choice(tr("Hours", "Horário"), MoveReminder.HOURS.map { (a, b) -> MoveCopy.hours(a, b) }, hours) { hours = it }
        Choice(tr("After", "Ao fim de"), MoveReminder.EVERY.map { "$it min" }, every) { every = it }
        Choice(tr("Days", "Dias"), listOf(tr("Weekdays", "Dias úteis"), tr("Every day", "Todos os dias")), if (weekdays) 0 else 1) { weekdays = it == 0 }
        PageNote(tr(
            "Only while the strap is connected and on your wrist, never during a workout or a call.",
            "Só com a strap ligada e no pulso, nunca durante um treino ou uma chamada.",
        ))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PageButton(tr("Cancel", "Cancelar"), onDismiss, Modifier.weight(1f))
            PageButton(
                tr("Save", "Guardar"),
                {
                    val (start, end) = MoveReminder.HOURS[hours]
                    onSave(MoveSettings(on = true, startMinute = start, endMinute = end, everyMinutes = MoveReminder.EVERY[every], weekdaysOnly = weekdays))
                },
                Modifier.weight(1f), primary = true,
            )
        }
    }
}

@Composable
private fun Choice(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = Color.White.copy(alpha = GlowType.SECONDARY_ALPHA), style = GlowType.secondary)
        GlowSegments(options, selected, onSelect)
    }
}

/** The move reminder's words, for its row on the Strap page and its sheet. */
object MoveCopy {
    val TITLE: String get() = tr("Move reminder", "Lembrete para te mexeres")

    fun hours(start: Int, end: Int): String = "%d–%d".format(start / 60, end / 60)

    /** The row's second line: what it's set to, or what it does while it's off. */
    fun summary(m: MoveSettings): String = if (!m.on) {
        tr("The strap buzzes after a long time without steps", "A strap vibra quando passas muito tempo sem passos")
    } else {
        val days = if (m.weekdaysOnly) tr("Weekdays", "Dias úteis") else tr("Every day", "Todos os dias")
        val hours = "%d:00–%d:00".format(m.startMinute / 60, m.endMinute / 60)
        tr("$days, $hours, after ${m.everyMinutes} min without steps", "$days, $hours, após ${m.everyMinutes} min sem passos")
    }
}
