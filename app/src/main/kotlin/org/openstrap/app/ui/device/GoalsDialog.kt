package org.openstrap.app.ui.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.openstrap.app.data.settings.Goals
import org.openstrap.app.ui.theme.PhosphorIcons

/** Steps, sleep and active hours to aim for each day. Every goal ring and "goal reached" uses these. */
@Composable
fun GoalsDialog(goals: Goals, onSave: (Goals) -> Unit, onDismiss: () -> Unit) {
    var steps by rememberSaveable { mutableStateOf(goals.steps.toString()) }
    var sleepH by rememberSaveable { mutableStateOf((goals.sleepMinutes / 60).toString()) }
    var sleepM by rememberSaveable { mutableStateOf((goals.sleepMinutes % 60).toString()) }
    var active by rememberSaveable { mutableStateOf(goals.activeHours.toString()) }

    val stepsValue = steps.toIntOrNull()?.takeIf { it in Goals.STEPS }
    val sleepValue = sleepH.toIntOrNull()?.let { h -> sleepM.ifEmpty { "0" }.toIntOrNull()?.takeIf { it in 0..59 }?.let { h * 60 + it } }
        ?.takeIf { it in Goals.SLEEP_MINUTES }
    val activeValue = active.toIntOrNull()?.takeIf { it in Goals.ACTIVE_HOURS }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(PhosphorIcons.ChartLineUp, null) },
        title = { Text("Your goals") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("What you aim for each day. Change them whenever they stop fitting.", style = MaterialTheme.typography.bodyMedium)
                NumberField(steps, { steps = it }, "Steps", "steps", stepsValue == null)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberField(sleepH, { sleepH = it.take(2) }, "Sleep", "h", sleepValue == null, Modifier.weight(1f))
                    NumberField(sleepM, { sleepM = it.take(2) }, "", "min", sleepValue == null, Modifier.weight(1f))
                }
                NumberField(active, { active = it.take(2) }, "Active hours", "of 24", activeValue == null)
                Text(
                    "An active hour is one with at least 250 steps.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = stepsValue != null && sleepValue != null && activeValue != null,
                onClick = { onSave(Goals(stepsValue!!, sleepValue!!, activeValue!!)) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
