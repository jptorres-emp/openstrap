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
import androidx.compose.ui.unit.dp
import org.openstrap.app.data.settings.Units
import org.openstrap.app.l10n.tr
import org.openstrap.app.ui.glow.PageButton
import org.openstrap.app.ui.glow.PageNote
import org.openstrap.app.ui.glow.PageSheet

/**
 * Logs today's weight, in a sheet over the Training tab: one a day, so a second the same day replaces it. It starts
 * from today's weight, else the last one logged, in the profile's units. [onDelete] takes today's back, when there is
 * one. Cancel, or pulling the sheet down, changes nothing.
 */
@Composable
fun WeightSheet(today: Float?, last: Float?, imperial: Boolean, onSave: (Float) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf((today ?: last)?.let { shown(if (imperial) Units.kgToLb(it) else it) }.orEmpty()) }
    val kg = text.replace(',', '.').toFloatOrNull()?.let { if (imperial) Units.lbToKg(it) else it }?.takeIf { it in MIN_KG..MAX_KG }
    PageSheet(onDismiss, tr("Log your weight", "Registar o peso"), note = tr(
        "Today's, in the morning before eating if you can, so the weeks compare.",
        "O de hoje, de manhã antes de comer se puderes, para as semanas serem comparáveis.",
    )) {
        NumberField(text, { text = it }, tr("Weight", "Peso"), if (imperial) "lb" else "kg", kg == null && text.isNotEmpty(), decimal = true)
        PageNote(tr(
            "It also becomes your profile's weight, which the calorie estimate uses.",
            "Também passa a ser o peso do teu perfil, que a estimativa de calorias usa.",
        ))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PageButton(tr("Cancel", "Cancelar"), onDismiss, Modifier.weight(1f))
            PageButton(tr("Save", "Guardar"), { kg?.let(onSave) }, Modifier.weight(1f), enabled = kg != null, primary = true)
        }
        if (onDelete != null) PageButton(tr("Delete today's weight", "Apagar o peso de hoje"), onDelete, Modifier.fillMaxWidth())
    }
}

/** 78.0 -> "78", 78.4 -> "78.4" (the field takes a point or a comma). */
private fun shown(v: Float): String = "%.1f".format(java.util.Locale.ROOT, v).removeSuffix(".0")

private const val MIN_KG = 25f
private const val MAX_KG = 300f
