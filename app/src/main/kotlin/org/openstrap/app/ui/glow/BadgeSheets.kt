package org.openstrap.app.ui.glow

import org.openstrap.app.ui.theme.Space
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.openstrap.app.R
import org.openstrap.app.data.Rewards
import org.openstrap.app.data.Workouts
import org.openstrap.app.data.db.PersonalBest
import org.openstrap.app.presentation.RewardsCopy
import org.openstrap.app.presentation.RewardsView
import org.openstrap.app.presentation.Units
import org.openstrap.app.ui.theme.Ink

/*
 * A badge or a personal best on its own, in a sheet over Goals and bests or All badges (rules A4, boards HaloBadgeSheet
 * and HaloBestSheet): the token at 96, lighting up as the sheet opens when it's earned, its group, its name, how it's
 * earned, and when it was or how far along, then a dark Close. The pages show only the token and its name, so the words
 * are here (spec 2.5: an explanation behind a tap, never on the page).
 */

/**
 * A badge on its own: its token, its group and name, how it's earned, then when it was in a pill of its colour, or how
 * far along with a bar, or that it's still to earn. Badges counted from workouts say which workouts count.
 */
@Composable
internal fun BadgeSheet(badge: Rewards.Badge, view: RewardsView, onDismiss: () -> Unit) {
    val row = view.earned.firstOrNull { it.id == badge.id }
    val progress = view.progress[badge.id]
    val lit = rememberLightRise(row != null)
    val ink = badgeInk(badge)
    HaloSheet(
        onDismiss, badge.title, variant = HaloSheetVariant.Token,
        lead = {
            Medal(badgeLook(badge, view.face(badge)), TokenSize.Sheet.size, locked = row == null, lit = lit)
            SheetTokenLine(groupName(badge.group))
        },
        footer = { HaloButton(stringResource(R.string.glow_close), onDismiss, Modifier.fillMaxWidth()) },
    ) {
        howToEarn(badge)?.let { SheetTokenLine(it, Ink.Strong, GlowType.body) }
        when {
            row != null -> HaloTag(
                stringResource(R.string.glow_goals_earned_on, RewardsCopy.dayLong(row.earnedAt)), variant = TagVariant.Status, color = ink,
            )
            progress != null -> Column(
                Modifier.fillMaxWidth().padding(horizontal = Space.S24),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.S8),
            ) {
                HaloProgress(progress.first.toFloat() / progress.second, Modifier.fillMaxWidth(), ink)
                SheetTokenLine(RewardsCopy.progressLine(progress.first, progress.second, badge.unit), Color.White)
            }
            else -> SheetTokenLine(stringResource(R.string.glow_goals_not_earned))
        }
        if (countsWorkouts(badge)) {
            SheetTokenLine(stringResource(R.string.glow_goals_counts_rule), GlowType.footnoteColor, GlowType.footnote)
        }
    }
}

/**
 * A personal best on its own: its rosette, its name and figure, when it was set and in what in a pill of its colour, and
 * what it measures. One not set yet says so, with what it will measure.
 */
@Composable
internal fun BestSheet(kind: String, view: RewardsView, onDismiss: () -> Unit) {
    val best = view.bests.firstOrNull { it.kind == kind }
    val lit = rememberLightRise(best != null)
    HaloSheet(
        onDismiss, RewardsCopy.bestTitle(kind), variant = HaloSheetVariant.Token,
        lead = {
            Medal(bestLook(kind), TokenSize.Sheet.size, locked = best == null, lit = lit)
            SheetTokenLine(stringResource(R.string.glow_goals_personal_bests))
        },
        footer = { HaloButton(stringResource(R.string.glow_close), onDismiss, Modifier.fillMaxWidth()) },
    ) {
        if (best != null) {
            Text(RewardsCopy.bestValue(kind, best.value), color = Color.White, style = GlowType.goalValue, maxLines = 1)
            HaloTag(whenSet(best, view), variant = TagVariant.Status, color = bestInk(kind))
        } else {
            SheetTokenLine(stringResource(R.string.glow_goals_not_set))
        }
        Column(Modifier.fillMaxWidth().padding(top = Space.S8), verticalArrangement = Arrangement.spacedBy(Space.S4)) {
            Text(stringResource(R.string.glow_goals_what_it_measures), color = GlowType.noteColor, style = GlowType.secondary)
            Text(bestHow(kind), color = Ink.Strong, style = GlowType.body)
        }
    }
}

/** A badge group's name: Firsts, Milestones, Weekly goal, Something new. */
@Composable
@ReadOnlyComposable
internal fun groupName(group: Rewards.Group): String = stringResource(
    when (group) {
        Rewards.Group.Firsts -> R.string.glow_goals_group_firsts
        Rewards.Group.Milestones -> R.string.glow_goals_group_milestones
        Rewards.Group.Weekly -> R.string.glow_goals_weekly_goal
        Rewards.Group.Kinds -> R.string.glow_goals_group_kinds
    },
)

/**
 * How [b] is earned, in a sentence, with its count for the milestones and the weeks in a row. Null for a badge with
 * no sentence in [BADGE_HOW] yet: its sheet shows without one rather than crash.
 */
@Composable
@ReadOnlyComposable
private fun howToEarn(b: Rewards.Badge): String? {
    val need = b.needs?.let { Units.count(it) }.orEmpty()
    return when {
        b.id.startsWith(WORKOUTS_PREFIX) -> stringResource(R.string.core_badge_how_workouts, need)
        b.id.startsWith(KM_PREFIX) -> stringResource(R.string.core_badge_how_km, need)
        b.id.startsWith(GOAL_PREFIX) && b.id != GOAL_FIRST_ID -> stringResource(R.string.core_badge_how_goal_run, need)
        else -> BADGE_HOW[b.id]?.let { stringResource(it) }
    }
}

/** A best of [kind]'s measure, in a sentence. */
@Composable
@ReadOnlyComposable
private fun bestHow(kind: String): String {
    Rewards.Kind.fastestMeters(kind)?.let { return stringResource(R.string.glow_goals_best_fastest_how, RewardsCopy.distanceName(it)) }
    return stringResource(
        when {
            Rewards.Kind.liftName(kind) != null -> R.string.glow_goals_best_lift_how
            kind == Rewards.Kind.LONGEST_RUN -> R.string.glow_goals_best_longest_run_how
            kind == Rewards.Kind.MOST_STEPS -> R.string.glow_goals_best_steps_how
            else -> R.string.glow_goals_best_longest_workout_how
        },
    )
}

/** When [best] was set, with the workout it was set in ("Thursday, 30 October 2026, morning run"), or its day alone. */
private fun whenSet(best: PersonalBest, view: RewardsView): String {
    val sport = best.workoutStart?.let { start -> view.all.firstOrNull { it.start == start } }?.sport
    return if (sport != null) RewardsCopy.whenSet(best.date, Workouts.sportName(sport)) else RewardsCopy.dayLong(best.date)
}

/** Whether [b] is counted from workouts, which count from 10 minutes when you start or keep them; not the steps or distance. */
private fun countsWorkouts(b: Rewards.Badge): Boolean = !b.id.startsWith(KM_PREFIX) && b.id !in STEPS_BADGES

/** Each badge earned by a single thing done, by id, with the sentence on how. */
private val BADGE_HOW: Map<String, Int> = mapOf(
    "first_workout" to R.string.core_badge_how_first_workout,
    "first_10k_steps" to R.string.core_badge_how_first_10k_steps,
    "steps_1m" to R.string.core_badge_how_steps_1m,
    "goal_first" to R.string.core_badge_how_goal_first,
    "first_run" to R.string.core_badge_how_first_run,
    "first_5k" to R.string.core_badge_how_first_5k,
    "first_10k" to R.string.core_badge_how_first_10k,
    "first_indoor" to R.string.core_badge_how_first_indoor,
    "first_log" to R.string.core_badge_how_first_log,
    "first_tabata" to R.string.core_badge_how_first_tabata,
    "first_reps" to R.string.core_badge_how_first_reps,
    "first_mind" to R.string.core_badge_how_first_mind,
    "first_game" to R.string.core_badge_how_first_game,
)

/** The badges counted from the strap's steps, not from workouts. */
private val STEPS_BADGES = setOf("first_10k_steps", "steps_1m")

private const val WORKOUTS_PREFIX = "workouts_"
private const val KM_PREFIX = "km_"
private const val GOAL_PREFIX = "goal_"
private const val GOAL_FIRST_ID = "goal_first"
