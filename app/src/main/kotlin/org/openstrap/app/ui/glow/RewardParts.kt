package org.openstrap.app.ui.glow

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

/*
 * The small parts the rewards pages share (rules A3, boards HaloGoalsBests, HaloGoalMoment, HaloWeeklyRecap): a section's
 * head, the grid of three tokens, the 4 dp progress bar, the week's days as solid pills and the one Doto figure with its
 * words.
 */

/** What a badge's art reads on these pages: the first weekly goal shows the goal it was met at, or this week's while locked. */
internal fun RewardsView.face(b: Rewards.Badge): String =
    earned.firstOrNull { it.id == b.id }?.let { RewardsCopy.face(b, Rewards.goalAt(it, weeks, firstDay, ZoneId.systemDefault())) }
        ?: RewardsCopy.face(b, goal)

/** A section's head (rules A3): its title in 15 semibold and a 12 px note on the right, 4 in. */
@Composable
internal fun SectionHead(title: String, modifier: Modifier = Modifier, note: String? = null) {
    Row(modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.Bottom) {
        Text(title, Modifier.weight(1f), color = Color.White, style = GlowType.cardTitle, maxLines = 1)
        if (note != null) Text(note, color = GlowType.noteColor, style = GlowType.footnote, maxLines = 1)
    }
}

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

/** How far along, 0..1, as a 4 dp bar: [ink] over #1C1D22 (rules A1). */
@Composable
internal fun ShareBar(share: Float, ink: Color, modifier: Modifier = Modifier) {
    Box(modifier.height(4.dp).clip(CircleShape).background(BarTrack)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(share.coerceIn(0f, 1f)).clip(CircleShape).background(ink))
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
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        days.forEachIndexed { i, (letter, on) ->
            Column(
                Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    Modifier.fillMaxWidth().height(PILL_HEIGHT).clip(GlowLayout.IconShape)
                        .background(if (on) ink else HaloButtonSurface)
                        .let { if (i == today) it.border(2.dp, Color.White, GlowLayout.IconShape) else it },
                )
                Text(letter, color = if (i == today) Color.White else GlowType.noteColor, style = GlowType.caption, maxLines = 1)
            }
        }
    }
}

/**
 * A figure with its words: each run of ASCII digits (with its point, comma or colon) drawn in Doto at [size] through
 * [BigNumberText], a slash in the display type at the same height (the dot font has none), and any other word small
 * after it in [wordStyle] on the digits' baseline ("4 of 4", "105 kg", "29:48", "4/4"). The screen's one big figure.
 */
@Composable
internal fun DotFigure(text: String, size: BigNumber, modifier: Modifier = Modifier, wordStyle: TextStyle = GlowType.body) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        FIGURE_PART.findAll(text).forEach { m ->
            val part = m.value
            when {
                part[0] in '0'..'9' -> BigNumberText(part, size)
                part == "/" -> Text(
                    "/", Modifier.padding(horizontal = 2.dp), color = Color.White.copy(alpha = SLASH_ALPHA), style = GlowType.figureLarge,
                )
                part.isBlank() -> Unit
                else -> Text(
                    part.trim(), Modifier.padding(horizontal = 8.dp, vertical = 2.dp), color = GlowType.noteColor, style = wordStyle,
                    maxLines = 1,
                )
            }
        }
    }
}

private val FIGURE_PART = Regex("[0-9][0-9.,:]*|/|[^0-9/]+")
private const val SLASH_ALPHA = 0.6f

private const val GRID_COLUMNS = 3
private val GRID_GAP = 16.dp
private val PILL_HEIGHT = 44.dp
private val BarTrack = Color(0xFF1C1D22)
