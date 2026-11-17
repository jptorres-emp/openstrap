package org.openstrap.app.ui.dashboard

import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import org.openstrap.app.R
import org.openstrap.app.ui.glow.HaloChip
import org.openstrap.app.ui.theme.Ink
import org.openstrap.app.ui.theme.MetricColors

/*
 * The strap pill in the header (board HaloTodayMidday): a 36 dark chip with a status dot and a few words, no battery
 * fill and no rim. The dot says the state: grey when connected or not, amber when the battery is low or the sync
 * failed, green while charging. Green and amber are for the strap and the sync only, never a health value (spec 2.2).
 */

private val DotConnected = Ink.Secondary
private val DotOffline = Ink.Tertiary

private fun dotColor(dot: StrapChip.Dot): Color = when (dot) {
    StrapChip.Dot.Fine -> DotConnected
    StrapChip.Dot.Charging -> MetricColors.good
    StrapChip.Dot.Low, StrapChip.Dot.Unsynced -> MetricColors.warn
    StrapChip.Dot.Warn, StrapChip.Dot.Quiet -> DotOffline
}

/**
 * The strap's state as a [HaloChip] (Filled, a status dot): 48 tall to tap, the chip drawn 36 tall in the middle of it,
 * so the 60 header row doesn't move. [modifier] comes after the tap room, so the Strap page grows out of the chip as
 * drawn, not the room round it.
 */
@Composable
internal fun StrapPill(chip: StrapChip, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val words = if (chip.dot == StrapChip.Dot.Charging) chip.battery?.toString() ?: chip.text else chip.text
    HaloChip(
        words, onClick, Modifier.minimumInteractiveComponentSize().then(modifier), dot = dotColor(chip.dot),
        description = chip.description, clickLabel = stringResource(R.string.day_header_open_strap),
    )
}
