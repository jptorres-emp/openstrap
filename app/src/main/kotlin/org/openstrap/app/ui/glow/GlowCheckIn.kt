package org.openstrap.app.ui.glow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.openstrap.app.data.CheckIn

/**
 * The morning check-in: how you feel, 1 to 5. It is the only within-user test of the score (evidence-review.md), so
 * Today asks once each morning; it never changes the score itself.
 */
@Composable
internal fun CheckInPanel(feeling: Int?, onCheckIn: (Int?) -> Unit) {
    DotPanel {
        DotHeader("Morning check-in", trailing = feeling?.let { CheckIn.label(it) })
        Text("How do you feel this morning?", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            (CheckIn.MIN..CheckIn.MAX).forEach { v ->
                ChoicePill("$v", selected = feeling == v, Modifier.weight(1f)) { onCheckIn(if (feeling == v) null else v) }
            }
        }
        Note("1 is ${CheckIn.label(CheckIn.MIN).lowercase()}, 5 is ${CheckIn.label(CheckIn.MAX).lowercase()}.")
    }
}

/** A glass pill that can be switched on, filled in white when it is. */
@Composable
private fun ChoicePill(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .let { if (selected) it.background(Color.White.copy(alpha = 0.85f), shape) else it }
            .glass(shape, onClick)
            .padding(vertical = 8.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) Color.Black else Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}
