package org.openstrap.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.openstrap.app.R
import org.openstrap.app.ui.glow.GlowType
import org.openstrap.app.ui.theme.MetricColors

/*
 * The strap pill in the header (board HaloTodayMidday): a 36 dp dark pill with a status dot and a few words, no battery
 * fill and no rim. The dot says the state: grey when connected or not, amber when the battery is low or the sync
 * failed, green while charging. Green and amber are for the strap and the sync only, never a health value (spec 2.2).
 */

/** The pill's fill, the same dark as the header's avatar (board: #17181C). */
private val PillSurface = Color(0xFF17181C)
private val PillInk = Color(0xFFC9CED6)
private val DotConnected = Color(0xFF9A9AA2)
private val DotOffline = Color(0xFF6E7480)

private fun dotColor(dot: StrapChip.Dot): Color = when (dot) {
    StrapChip.Dot.Fine -> DotConnected
    StrapChip.Dot.Charging -> MetricColors.good
    StrapChip.Dot.Low, StrapChip.Dot.Unsynced -> MetricColors.warn
    StrapChip.Dot.Warn, StrapChip.Dot.Quiet -> DotOffline
}

@Composable
internal fun StrapPill(chip: StrapChip, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        // 48 dp tall to tap, the pill drawn 36 dp tall in the middle of it; the header row is 60 dp, so nothing moves.
        // [modifier] comes after, so the Strap page grows out of the pill as drawn, not the room round it.
        Modifier.minimumInteractiveComponentSize().then(modifier)
            .clip(CircleShape).background(PillSurface)
            .clickable(onClickLabel = stringResource(R.string.day_header_open_strap), onClick = onClick)
            .semantics { contentDescription = chip.description }
            .defaultMinSize(minHeight = HEIGHT)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).background(dotColor(chip.dot), CircleShape))
        val words = if (chip.dot == StrapChip.Dot.Charging) chip.battery?.toString() ?: chip.text else chip.text
        if (words.isNotEmpty()) {
            Text(words, maxLines = 1, softWrap = false, color = PillInk, style = GlowType.noteSmall)
        }
    }
}

private val HEIGHT = 36.dp
