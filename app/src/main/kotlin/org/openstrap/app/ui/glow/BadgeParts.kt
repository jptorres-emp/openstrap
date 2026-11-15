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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.ZoneId
import org.openstrap.app.R
import org.openstrap.app.data.Rewards
import org.openstrap.app.data.db.PersonalBest
import org.openstrap.app.presentation.RewardsCopy
import org.openstrap.app.presentation.RewardsView
import org.openstrap.app.ui.components.pressClickable

/*
 * The parts of Goals and bests and All badges (boards HaloGoalsBests and HaloAllBadges): a section head with its note,
 * the tokens in a grid of three with their names and figures, the next badge as one solid row, and the week's days as
 * solid pills. A tap on a token opens its sheet (BadgeSheets.kt) with the rest.
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
@Composable
private fun <T> TokenGrid(items: List<T>, cell: @Composable (T, Modifier) -> Unit) {
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
 * The personal bests as rosettes, three to a row, each with its name and figure (rules A3). Before the first, the kinds
 * there are to set, quiet, with a line on when they come.
 */
@Composable
internal fun BestsPanel(view: RewardsView, onOpen: (String) -> Unit) {
    val shown = view.bests
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(GRID_GAP)) {
        SectionHead(stringResource(R.string.glow_goals_personal_bests))
        if (shown.isEmpty()) {
            Text(
                stringResource(R.string.glow_goals_no_bests), Modifier.padding(horizontal = 4.dp), color = GlowType.noteColor,
                style = GlowType.secondary,
            )
            TokenGrid(FIRST_BESTS) { kind, m -> BestCell(kind, null, onOpen, m) }
        } else {
            BestsGrid(shown, onOpen)
        }
    }
}

/** [bests] as rosettes in a grid of three, in a fixed order, each with its name and figure. */
@Composable
internal fun BestsGrid(bests: List<PersonalBest>, onOpen: (String) -> Unit) {
    TokenGrid(bests.sortedBy { order(it.kind) }) { b, m -> BestCell(b.kind, b, onOpen, m) }
}

/**
 * The badge closest to done, as one solid row (rules A3): its token still to earn at 56, its name, and how far along as a
 * bar and in words. Opens its sheet.
 */
@Composable
internal fun NextBadgeCard(badge: Rewards.Badge, view: RewardsView, onOpen: (Rewards.Badge) -> Unit) {
    val ink = badgeInk(badge)
    val (have, need) = view.progress[badge.id] ?: (0L to 1L)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(GRID_GAP)) {
        SectionHead(stringResource(R.string.glow_goals_next_badge))
        Row(
            Modifier.fillMaxWidth().pressClickable({ onOpen(badge) }, GlowLayout.CardShape).solidCard(GlowLayout.CardShape)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Medal(badgeLook(badge, view.face(badge)), NEXT_MEDAL, locked = true)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(badge.title, color = Color.White, style = GlowType.cardTitle, maxLines = 1)
                Text(RewardsCopy.progressLine(have, need, badge.unit), color = GlowType.noteColor, style = GlowType.secondary)
                ShareBar(have.toFloat() / need, ink, Modifier.fillMaxWidth())
            }
            RowChevron()
        }
    }
}

/**
 * The badges as a grid of three (rules A3): the ones earned, newest first, then the ones to go for, the closest first.
 * The note says how many are earned.
 */
@Composable
internal fun BadgesPanel(view: RewardsView, onOpen: (Rewards.Badge) -> Unit) {
    val earned = view.earned.sortedByDescending { it.earnedAt }.mapNotNull { Rewards.badge(it.id) }
    val got = earned.map { it.id }.toSet()
    val next = Rewards.BADGES.filter { it.id !in got }.sortedByDescending { share(view, it) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(GRID_GAP)) {
        SectionHead(
            stringResource(R.string.glow_goals_badges),
            note = stringResource(R.string.glow_goals_earned_count, view.earned.size, Rewards.BADGES.size),
        )
        TokenGrid((earned + next).take(PANEL_BADGES)) { b, m -> BadgeCell(b, view, onOpen, modifier = m) }
    }
}

/** How far along [b] is, 0..1, from what the view counts. */
private fun share(view: RewardsView, b: Rewards.Badge): Float = view.progress[b.id]?.let { (have, need) -> have.toFloat() / need } ?: 0f

/**
 * A badge's medal and its name in [nameStyle], earned filled and a locked one quiet with a bar for how far along, in its
 * ink on #1C1D22 (rules A1). Opens its sheet.
 */
@Composable
internal fun BadgeCell(
    badge: Rewards.Badge,
    view: RewardsView,
    onOpen: (Rewards.Badge) -> Unit,
    modifier: Modifier = Modifier,
    nameStyle: TextStyle = GlowType.footnote,
) {
    val row = view.earned.firstOrNull { it.id == badge.id }
    val state = row?.let { stringResource(R.string.glow_goals_earned_on, RewardsCopy.dayLong(it.earnedAt)) }
        ?: stringResource(R.string.glow_goals_not_earned)
    val described = stringResource(R.string.glow_goals_badge_described, badge.title, state)
    TokenCell(
        modifier, { onOpen(badge) }, described,
        token = { Medal(badgeLook(badge, view.face(badge)), TOKEN, locked = row == null) },
        bar = if (row == null) share(view, badge) else null, ink = badgeInk(badge),
    ) {
        Text(
            badge.title, color = if (row != null) Color.White else GlowType.noteColor, style = nameStyle,
            textAlign = TextAlign.Center, maxLines = 2,
        )
    }
}

/** A best's rosette, its name and its figure; [best] null for one not set yet, quiet. Opens its sheet. */
@Composable
private fun BestCell(kind: String, best: PersonalBest?, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val title = RewardsCopy.bestTitle(kind)
    val figure = best?.let { RewardsCopy.bestValue(kind, it.value) } ?: stringResource(R.string.glow_goals_not_set)
    val described = stringResource(R.string.glow_goals_badge_described, title, figure)
    TokenCell(
        modifier, { onOpen(kind) }, described,
        token = { Medal(bestLook(kind), TOKEN, locked = best == null) },
        bar = if (best == null) 0f else null, ink = bestInk(kind),
    ) {
        Text(
            title, color = if (best != null) Color.White else GlowType.noteColor, style = GlowType.footnote,
            textAlign = TextAlign.Center, maxLines = 2,
        )
        Text(
            figure, color = if (best != null) Color.White else GlowType.footnoteColor, style = GlowType.secondary, maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

/** One cell of a token grid: the [token] (with a [bar] under it while locked), then [lines] under, centred, 6 apart. */
@Composable
private fun TokenCell(
    modifier: Modifier,
    onClick: () -> Unit,
    described: String,
    token: @Composable () -> Unit,
    bar: Float?,
    ink: Color,
    lines: @Composable () -> Unit,
) {
    Column(
        modifier.clip(GlowLayout.TileShape).pressClickable(onClick, GlowLayout.TileShape)
            .clearAndSetSemantics {
                contentDescription = described
                role = Role.Button
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            token()
            if (bar != null) ShareBar(bar, ink, Modifier.width(CELL_BAR))
        }
        lines()
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

/** Bests in a fixed order: runs, then the longest workout, lifts, steps. */
private fun order(kind: String): Int = Rewards.Kind.fastestMeters(kind)
    ?: when {
        kind == Rewards.Kind.LONGEST_RUN -> RUN_ORDER
        kind == Rewards.Kind.LONGEST_WORKOUT -> RUN_ORDER + 1
        Rewards.Kind.liftName(kind) != null -> RUN_ORDER + 2
        else -> STEPS_ORDER
    }

/** Before any best, the kinds there are to set: the ones any workout or day can set. */
private val FIRST_BESTS = listOf(Rewards.Kind.LONGEST_WORKOUT, Rewards.Kind.MOST_STEPS, Rewards.Kind.fastest(FIVE_KM))

/** After every fastest stretch (their metres), the longest run, the longest workout, the lifts, then the steps. */
private const val RUN_ORDER = 100_000
private const val STEPS_ORDER = 100_003
private const val FIVE_KM = 5000
private const val GRID_COLUMNS = 3
private const val PANEL_BADGES = 9
private val GRID_GAP = 16.dp
private val TOKEN = 72.dp
private val CELL_BAR = 58.dp
private val NEXT_MEDAL = 56.dp
private val PILL_HEIGHT = 44.dp
private val BarTrack = Color(0xFF1C1D22)

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
