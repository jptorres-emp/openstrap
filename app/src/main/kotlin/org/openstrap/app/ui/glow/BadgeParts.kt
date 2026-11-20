package org.openstrap.app.ui.glow

import org.openstrap.app.ui.theme.Space
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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

/**
 * The personal bests as rosettes, three to a row, each with its name and figure (rules A3). Before the first, the kinds
 * there are to set, quiet, with a line on when they come.
 */
@Composable
internal fun BestsPanel(view: RewardsView, onOpen: (String) -> Unit) {
    val shown = view.bests
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(GRID_GAP)) {
        HaloTitle(stringResource(R.string.glow_goals_personal_bests), variant = TitleVariant.OutOfCard)
        if (shown.isEmpty()) {
            Text(
                stringResource(R.string.glow_goals_no_bests), Modifier.padding(horizontal = Space.S4), color = GlowType.noteColor,
                style = GlowType.secondary,
            )
            TokenGrid(FIRST_BESTS) { kind, m -> BestCell(kind, null, onOpen, m) }
        } else {
            BestsGrid(shown, onOpen)
        }
    }
}

/** [bests] as rosettes in a grid of three, in a fixed order, each with its name and figure. */
// The bests arrive whole with each reading and never change in place.
@Suppress("UnstableCollections", "ComposeUnstableCollections")
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
        HaloTitle(stringResource(R.string.glow_goals_next_badge), variant = TitleVariant.OutOfCard)
        HaloCard(onClick = { onOpen(badge) }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S12)) {
                Medal(badgeLook(badge, view.face(badge)), TokenSize.Next.size, locked = true)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.S8)) {
                    Text(badge.title, color = Color.White, style = GlowType.cardTitle, maxLines = 1)
                    Text(RewardsCopy.progressLine(have, need, badge.unit), color = GlowType.noteColor, style = GlowType.secondary)
                    HaloProgress(have.toFloat() / need, Modifier.fillMaxWidth(), ink)
                }
                RowChevron()
            }
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
        HaloTitle(
            stringResource(R.string.glow_goals_badges), variant = TitleVariant.OutOfCard,
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
        { onOpen(badge) }, described,
        token = { Medal(badgeLook(badge, view.face(badge)), TokenSize.Grid.size, locked = row == null) },
        bar = if (row == null) share(view, badge) else null, ink = badgeInk(badge), modifier = modifier,
    ) {
        Text(
            badge.title, Modifier.fillMaxWidth(), color = if (row != null) Color.White else GlowType.noteColor, style = nameStyle,
            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
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
        { onOpen(kind) }, described,
        token = { Medal(bestLook(kind), TokenSize.Grid.size, locked = best == null) },
        bar = if (best == null) 0f else null, ink = bestInk(kind), modifier = modifier,
    ) {
        Text(
            title, Modifier.fillMaxWidth(), color = if (best != null) Color.White else GlowType.noteColor, style = GlowType.footnote,
            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
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
    onClick: () -> Unit,
    described: String,
    token: @Composable () -> Unit,
    bar: Float?,
    ink: Color,
    modifier: Modifier = Modifier,
    lines: @Composable () -> Unit,
) {
    Column(
        modifier.fillMaxWidth().pressClickable(onClick)
            .clearAndSetSemantics {
                contentDescription = described
                role = Role.Button
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.S8),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.S8)) {
            token()
            if (bar != null) HaloProgress(bar, Modifier.width(CELL_BAR), ink)
        }
        lines()
    }
}

/** Bests in a fixed order: runs, then the longest workout, the most rounds, lifts, steps. */
private fun order(kind: String): Int = Rewards.Kind.fastestMeters(kind)
    ?: when {
        kind == Rewards.Kind.LONGEST_RUN -> RUN_ORDER
        kind == Rewards.Kind.LONGEST_WORKOUT -> RUN_ORDER + 1
        kind == Rewards.Kind.MOST_ROUNDS -> ROUNDS_ORDER
        Rewards.Kind.liftName(kind) != null -> LIFT_ORDER
        else -> STEPS_ORDER
    }

/** Before any best, the kinds there are to set: the ones any workout or day can set. */
private val FIRST_BESTS = listOf(Rewards.Kind.LONGEST_WORKOUT, Rewards.Kind.MOST_STEPS, Rewards.Kind.fastest(FIVE_KM))

/** After every fastest stretch (their metres), the longest run, the longest workout, the most rounds, the lifts, then the steps. */
private const val RUN_ORDER = 100_000
private const val ROUNDS_ORDER = 100_002
private const val LIFT_ORDER = 100_003
private const val STEPS_ORDER = 100_004
private const val FIVE_KM = 5000
private const val PANEL_BADGES = 9
private val GRID_GAP = 16.dp
private val CELL_BAR = 58.dp

