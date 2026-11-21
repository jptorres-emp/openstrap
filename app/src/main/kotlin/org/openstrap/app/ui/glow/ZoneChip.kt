package org.openstrap.app.ui.glow

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.openstrap.app.presentation.WorkoutCopy
import org.openstrap.app.ui.theme.Ink
import org.openstrap.app.ui.theme.Space
import org.openstrap.app.ui.workout.ZONE_COLORS

/**
 * The live heart rate zone as a mark (workout-rules.md, Colour 3): a dot in the zone's colour with a thin white ring,
 * then "Zone 3", on a dark pill. The same chip next to the bpm everywhere it shows during a workout, on any card colour,
 * so the zone never lights a card itself and still reads where its colour matches the sport's.
 */
@Composable
fun ZoneChip(zone: Int, modifier: Modifier = Modifier) {
    val color = ZONE_COLORS[zone.coerceIn(ZONE_COLORS.indices)]
    Row(
        modifier.clip(CircleShape).background(Ink.Scrim).padding(horizontal = Space.S12, vertical = Space.S4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.S8),
    ) {
        Canvas(Modifier.size(11.dp)) {
            drawCircle(Ink.Strong)
            drawCircle(color, radius = 4.dp.toPx())
        }
        Text(WorkoutCopy.zoneTitle(zone).first, color = Color.White, style = GlowType.noteSmall, maxLines = 1)
    }
}
