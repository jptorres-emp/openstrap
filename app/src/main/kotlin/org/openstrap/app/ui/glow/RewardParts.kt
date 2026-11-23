package org.openstrap.app.ui.glow

import org.openstrap.app.ui.theme.OnSurface

import org.openstrap.app.ui.theme.Corner
import org.openstrap.app.ui.theme.Space
import org.openstrap.app.ui.theme.Greys
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import java.time.ZoneId
import org.openstrap.app.data.Rewards
import org.openstrap.app.presentation.RewardsCopy
import org.openstrap.app.presentation.RewardsView
import org.openstrap.app.ui.theme.Ink

/*
 * The small parts the rewards pages share (rules A3, boards HaloGoalsBests, HaloGoalMoment, HaloWeeklyRecap): a section's
 * head, the grid of three tokens, the 4 dp progress bar, the week's days as solid pills and the one Doto figure with its
 * words.
 */

/** What a badge's art reads on these pages: the first weekly goal shows the goal it was met at, or this week's while locked. */
internal fun RewardsView.face(b: Rewards.Badge): String =
    earned.firstOrNull { it.id == b.id }?.let { RewardsCopy.face(b, Rewards.goalAt(it, weeks, firstDay, ZoneId.systemDefault())) }
        ?: RewardsCopy.face(b, goal)

/** [items] as a grid of three, 16 apart (rules A3), each cell given its column's width. */
// The items arrive whole with each reading and never change in place.
@Suppress("UnstableCollections", "ComposeUnstableCollections")
@Composable
internal fun <T> TokenGrid(items: List<T>, cell: @Composable (T, Modifier) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(GRID_GAP)) {
        items.chunked(GRID_COLUMNS).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GRID_GAP)) {
                row.forEach { cell(it, Modifier.weight(1f)) }
                repeat(GRID_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * The week's days as seven solid pills, a day's letter under each (boards HaloGoalMoment, HaloWeeklyRecap): a day with a
 * workout in [ink], the rest #17181C, and the [today] pill ringed in white with its letter white.
 */
// The days arrive whole with each reading and never change in place.
@Suppress("UnstableCollections", "ComposeUnstableCollections")
@Composable
internal fun DayPills(days: List<Pair<String, Boolean>>, today: Int?, modifier: Modifier = Modifier, ink: Color = RewardLime) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.S8)) {
        days.forEachIndexed { i, (letter, on) ->
            Column(
                Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Space.S8),
            ) {
                Box(
                    Modifier.fillMaxWidth().height(PILL_HEIGHT).clip(Corner.Pill)
                        .background(if (on) ink else OnSurface.control)
                        .let { if (i == today) it.border(ChipSpec.Ring, Color.White, Corner.Pill) else it },
                )
                Text(letter, color = if (i == today) Color.White else GlowType.noteColor, style = GlowType.caption, maxLines = 1)
            }
        }
    }
}

private const val GRID_COLUMNS = 3
private val GRID_GAP = 16.dp
private val PILL_HEIGHT = 44.dp
