package org.openstrap.app.ui.device

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.openstrap.app.data.settings.Goals
import org.openstrap.app.ui.glow.GlowType
import org.openstrap.app.ui.glow.PageButton
import org.openstrap.app.ui.glow.PageNote
import org.openstrap.app.ui.glow.PageSheet

/**
 * Steps, sleep and active hours to aim for each day, in a sheet over the You page (spec 3.4). Every goal ring and
 * "goal reached" uses these. Cancel, or pulling the sheet down, leaves them as they were.
 */
@Composable
fun GoalsSheet(goals: Goals, onSave: (Goals) -> Unit, onDismiss: () -> Unit) {
    var steps by rememberSaveable { mutableStateOf(goals.steps.toString()) }
    var sleepH by rememberSaveable { mutableStateOf((goals.sleepMinutes / 60).toString()) }
    var sleepM by rememberSaveable { mutableStateOf((goals.sleepMinutes % 60).toString()) }
    var active by rememberSaveable { mutableStateOf(goals.activeHours.toString()) }

    val stepsValue = steps.toIntOrNull()?.takeIf { it in Goals.STEPS }
    val sleepValue = sleepH.toIntOrNull()?.let { h -> sleepM.ifEmpty { "0" }.toIntOrNull()?.takeIf { it in 0..59 }?.let { h * 60 + it } }
        ?.takeIf { it in Goals.SLEEP_MINUTES }
    val activeValue = active.toIntOrNull()?.takeIf { it in Goals.ACTIVE_HOURS }

    PageSheet(onDismiss, "Your goals", note = "What you aim for each day. Change them whenever they stop fitting.") {
        NumberField(steps, { steps = it }, "Steps", "steps", stepsValue == null)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Sleep", color = Color.White.copy(alpha = GlowType.SECONDARY_ALPHA), style = GlowType.secondary)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberField(sleepH, { sleepH = it.take(2) }, "", "h", sleepValue == null, Modifier.weight(1f))
                NumberField(sleepM, { sleepM = it.take(2) }, "", "min", sleepValue == null, Modifier.weight(1f))
            }
        }
        NumberField(active, { active = it.take(2) }, "Active hours", "of 24", activeValue == null)
        PageNote("An active hour is one with at least 250 steps.")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PageButton("Cancel", onDismiss, Modifier.weight(1f))
            PageButton(
                "Save", { onSave(Goals(stepsValue!!, sleepValue!!, activeValue!!)) }, Modifier.weight(1f),
                enabled = stepsValue != null && sleepValue != null && activeValue != null, primary = true,
            )
        }
    }
}
