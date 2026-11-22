package org.openstrap.app.ui.glow

import org.openstrap.app.ui.theme.Space
import org.openstrap.app.ui.theme.Greys
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle as DayStyle
import java.util.Locale
import org.openstrap.app.R
import org.openstrap.app.data.Rewards
import org.openstrap.app.data.settings.Goals
import org.openstrap.app.i18n.Strings
import org.openstrap.app.presentation.DayWords
import org.openstrap.app.presentation.Ranges
import org.openstrap.app.presentation.RewardsCopy
import org.openstrap.app.presentation.RewardsView
import org.openstrap.app.presentation.Units
import org.openstrap.app.ui.theme.AppIcons

/*
 * The Goals and bests page from You (workouts spec 3.5, board HaloGoalsBests), All badges, and the weekly recap the
 * Monday notification opens (board HaloWeeklyRecap). The pages show each best and badge as its token ([Medal]) with only
 * its name and figure; a tap opens its sheet, with what it is, when it was earned and how far along a locked one is.
 */

/** The days of [first]'s week, each a letter and whether a workout in [view] counted on it. */
private fun weekDots(view: RewardsView, first: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<Pair<String, Boolean>> {
    val days = view.counted.map { Instant.ofEpochSecond(it.shownStart).atZone(zone).toLocalDate() }.toSet()
    return (0L until 7L).map { i ->
        first.plusDays(i).let { d -> d.dayOfWeek.getDisplayName(DayStyle.NARROW, Locale.getDefault()) to (d in days) }
    }
}

/**
 * A week against its goal on a solid card (rules A3, boards HaloGoalsBests and HaloWeeklyRecap): the goal's ring at 72, the
 * count against the goal in Doto beside it, what the count is under it and [line] under that, in lime when it's good
 * news ([good]) and grey when not. The page's one Doto number.
 */
@Composable
private fun WeekCard(count: Int, goal: Int, caption: String, line: String, good: Boolean) {
    val shown = count.coerceAtMost(goal)
    HaloCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S16)) {
            WeeklyGoalRing(shown, goal, 72.dp, pluralStringResource(R.plurals.glow_goals_count_of_goal, goal, shown, goal))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.S4)) {
                HaloBigNumber(stringResource(R.string.glow_goals_count_big, shown, goal), BigNumber.Tile)
                Text(caption, color = GlowType.noteColor, style = GlowType.secondary)
                Text(line, color = if (good) GoodLine else GlowType.noteColor, style = GlowType.secondary)
            }
        }
    }
}

/** The tint of a good line under the week's figure (#DEF99F, lime toward white). */
private val GoodLine = lerp(RewardLime, Color.White, 0.42f)

/** A week's distance shows from a tenth of a km, the first the figure can write. */
private const val MIN_WEEK_METERS = 100f

/**
 * The share card for the week from [first]: the count against the goal, its days, the hours, km and active kcal, and
 * chips for the weeks in a row and the week's first best.
 */
private fun weekStory(view: RewardsView, first: LocalDate, strings: Strings = Strings.app): StoryCard.Week {
    val week = view.week(first)
    val count = week?.count ?: 0
    val goal = week?.goal ?: view.goal
    val zone = ZoneId.systemDefault()
    val from = first.atStartOfDay(zone).toEpochSecond()
    val to = first.plusDays(7).atStartOfDay(zone).toEpochSecond()
    val mine = view.all.filter { it.startedByYou && it.shownStart in from until to }
    val seconds = mine.sumOf { it.activeSeconds }
    val meters = mine.sumOf { (it.meters ?: 0f).toDouble() }.toFloat()
    val kcal = Math.round(mine.mapNotNull { it.activeKcal }.sum())
    val totals = if (mine.isEmpty()) emptyList() else listOfNotNull(
        StoryStat(Units.clockDuration(seconds), null, strings.get(R.string.glow_goals_hours)),
        meters.takeIf { it >= MIN_WEEK_METERS }?.let {
            StoryStat(Units.km(it, Units.Precision.Day), strings.get(R.string.glow_unit_km), strings.get(R.string.glow_goals_distance))
        },
        kcal.takeIf { it > 0 }?.let { StoryStat(Units.count(it), null, strings.get(R.string.glow_goals_active_kcal)) },
    )
    val run = Rewards.inARow(view.weeks, first)
    val best = view.bests.firstOrNull { it.date in from until to }
    val chips = listOfNotNull(
        if (week?.met == true && run > 0) {
            StoryChip(strings.plural(R.plurals.glow_goals_weeks_in_a_row, run, run), best = false, met = true)
        } else {
            null
        },
        best?.let {
            val text = strings.format(R.string.glow_goals_best, RewardsCopy.bestTitle(it.kind), RewardsCopy.bestValue(it.kind, it.value))
            StoryChip(text, best = true, met = false)
        },
    )
    return StoryCard.Week(
        Ranges.of(DayWords.dayMonth(first), DayWords.dayMonth(first.plusDays(6))), strings.get(R.string.glow_share_my_week), "$count",
        strings.format(R.string.glow_goals_story_goal, strings.plural(R.plurals.glow_goals_workouts_word, count), goal),
        weekDots(view, first), totals, chips,
    )
}

/** How the weekly goal, the bests and the badges are counted: the page's (i). */
@Composable
private fun BestsInfoSheet(onDismiss: () -> Unit) {
    HaloSheet(
        onDismiss, stringResource(R.string.glow_goals_title), variant = HaloSheetVariant.Info,
        footer = { HaloButton(stringResource(R.string.glow_close), onDismiss, Modifier.fillMaxWidth()) },
    ) {
        SheetSection(stringResource(R.string.glow_goals_weekly_goal), stringResource(R.string.glow_goals_about_goal))
        SheetSection(stringResource(R.string.glow_goals_personal_bests), stringResource(R.string.glow_goals_about_bests))
        SheetSection(stringResource(R.string.glow_goals_badges), stringResource(R.string.glow_goals_about_badges))
    }
}

/**
 * The weekly goal on its own, in a sheet (board HaloWeeklyGoalSheet): the pencil on the Goals and bests page and the
 * recap. A stepper with the number in Funnel Display Light 44 and its word, a note, then Cancel and Save.
 */
@Composable
fun WeeklyGoalSheet(goal: Int, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    var value by rememberSaveable { mutableIntStateOf(goal) }
    HaloSheet(
        onDismiss, stringResource(R.string.glow_goals_weekly_goal),
        footer = {
            ButtonPair(
                stringResource(R.string.glow_cancel),
                onDismiss,
                stringResource(R.string.glow_save),
                { onSave(value) },
            )
        },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = Space.S8), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HaloIconButton(
                AppIcons.Minus, stringResource(R.string.you_goals_workouts_fewer),
                { value = (value - 1).coerceIn(Goals.WORKOUTS_PER_WEEK) },
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$value", color = Color.White, style = GlowType.goalValue, maxLines = 1)
                Text(
                    pluralStringResource(R.plurals.glow_goals_workouts_word, value), Modifier.padding(start = Space.S8, bottom = Space.S8),
                    color = GlowType.noteColor, style = GlowType.body, maxLines = 1,
                )
            }
            HaloIconButton(
                AppIcons.Plus, stringResource(R.string.you_goals_workouts_more), { value = (value + 1).coerceIn(Goals.WORKOUTS_PER_WEEK) },
            )
        }
        Text(stringResource(R.string.glow_goals_sheet_note), color = GlowType.noteColor, style = GlowType.secondary)
    }
}

/**
 * Goals and bests (board HaloGoalsBests): this week against the goal with the weeks in a row, the personal bests as
 * rosettes, the next badge to earn and how far along, the badges as medals, and the ways on. Each best and badge opens
 * its sheet. Before any workout there's still a page: the week at nought, the bests and badges there are to go for.
 * [celebrations] is the Celebrations row's line, what is switched on.
 */
@Composable
fun GoalsAndBestsPage(
    view: RewardsView?,
    contentPadding: PaddingValues,
    onGoal: () -> Unit,
    onAllBadges: () -> Unit,
    onRecap: () -> Unit,
    onCelebrations: () -> Unit,
    onBack: () -> Unit,
    celebrations: String? = null,
) {
    BackHandler(onBack = onBack)
    var openBadge by rememberSaveable { mutableStateOf<String?>(null) }
    var openBest by rememberSaveable { mutableStateOf<String?>(null) }
    var info by rememberSaveable { mutableStateOf(false) }
    HaloPageList(contentPadding, variant = PageVariant.Detail) {
        item {
            HaloPageHeader(stringResource(R.string.glow_goals_title), onBack, trailing = {
                InfoButton(stringResource(R.string.glow_goals_about), { info = true })
            })
        }
        if (view == null) {
            item { HaloNote(stringResource(R.string.glow_goals_loading)) }
            return@HaloPageList
        }
        item {
            val count = view.week(view.thisWeek)?.count ?: 0
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SECTION_GAP)) {
                HaloTitle(stringResource(R.string.glow_goals_this_week), variant = TitleVariant.OutOfCard)
                WeekCard(
                    count, view.goal, pluralStringResource(R.plurals.glow_goals_workouts_this_week, count, count),
                    RewardsCopy.runLine(view.inARow, view.bestRun), good = view.inARow > 0,
                )
            }
        }
        item { BestsPanel(view, onOpen = { openBest = it }) }
        view.next?.let { next -> item { NextBadgeCard(next, view, onOpen = { openBadge = it.id }) } }
        item { BadgesPanel(view, onOpen = { openBadge = it.id }) }
        item { WaysOn(view, celebrations, onAllBadges, onGoal, onRecap, onCelebrations) }
    }
    if (info) BestsInfoSheet { info = false }
    if (view != null) {
        openBadge?.let(Rewards::badge)?.let { b -> BadgeSheet(b, view, onDismiss = { openBadge = null }) }
        openBest?.let { kind -> BestSheet(kind, view, onDismiss = { openBest = null }) }
    }
}

/** All badges, the weekly goal, last week's recap and the celebration settings, as nav rows in one panel (rules A3, B3). */
@Composable
private fun WaysOn(
    view: RewardsView, celebrations: String?, onAllBadges: () -> Unit, onGoal: () -> Unit, onRecap: () -> Unit, onCelebrations: () -> Unit,
) {
    PageGroup(footnote = stringResource(R.string.glow_goals_counts_rule)) {
        NavRow(
            stringResource(R.string.glow_goals_all_badges),
            stringResource(R.string.glow_goals_earned_count, view.earned.size, Rewards.BADGES.size),
            onClick = onAllBadges, below = true, icon = AppIcons.CheckCircle, divider = false,
        )
        NavRow(
            stringResource(R.string.glow_goals_weekly_goal), pluralStringResource(R.plurals.you_goals_workouts_value, view.goal, view.goal),
            onClick = onGoal, below = true, icon = AppIcons.Target,
        )
        NavRow(stringResource(R.string.glow_goals_last_week), onClick = onRecap, icon = AppIcons.CalendarBlank)
        NavRow(
            stringResource(R.string.glow_goals_celebrations), celebrations, onClick = onCelebrations, below = celebrations != null,
            icon = AppIcons.SlidersHorizontal,
        )
    }
}

/**
 * Every badge by group (board HaloAllBadges), each group a section with how many of it are earned and its badges three to
 * a row with only their names: earned ones filled, locked ones quiet with a bar for how far along. Each opens its sheet.
 */
@Composable
fun AllBadgesPage(view: RewardsView?, contentPadding: PaddingValues, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    HaloPageList(contentPadding, variant = PageVariant.Detail) {
        item { HaloPageHeader(stringResource(R.string.glow_goals_all_badges), onBack) }
        if (view == null) return@HaloPageList
        Rewards.Group.entries.forEach { group ->
            item {
                val badges = Rewards.BADGES.filter { it.group == group }
                val got = badges.count { b -> view.earned.any { it.id == b.id } }
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SECTION_GAP)) {
                    HaloTitle(
                        groupName(group), variant = TitleVariant.OutOfCard,
                        note = stringResource(R.string.glow_goals_earned_count, got, badges.size),
                    )
                    badges.chunked(GRID_COLUMNS).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SECTION_GAP)) {
                            row.forEach { b -> BadgeCell(b, view, { open = it.id }, Modifier.weight(1f)) }
                            repeat(GRID_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
    if (view != null) open?.let(Rewards::badge)?.let { b -> BadgeSheet(b, view, onDismiss = { open = null }) }
}

/**
 * The weekly recap (board HaloWeeklyRecap) for the week from [first]: the week card and its days as solid pills, the
 * bests set that week as tokens, its time, distance and active kcal, and next week's goal as a value row. Share week
 * is a dark button under them.
 */
@Composable
fun WeeklyRecapPage(view: RewardsView?, first: LocalDate, contentPadding: PaddingValues, onGoal: () -> Unit, onBack: () -> Unit) {
    // Share week opens the share screen in the recap's place; close comes back here.
    var sharing by rememberSaveable { mutableStateOf(false) }
    if (sharing && view != null) {
        val card = remember(view, first) { weekStory(view, first) }
        WeekShareScreen(card, "unstrap_week_$first.png") { sharing = false }
        return
    }
    BackHandler(onBack = onBack)
    var openBest by rememberSaveable { mutableStateOf<String?>(null) }
    HaloPageList(contentPadding, variant = PageVariant.Detail) {
        item { HaloPageHeader(stringResource(R.string.glow_goals_recap_title), onBack, subtitle = RewardsCopy.weekRange(first)) }
        if (view == null) return@HaloPageList
        val week = view.week(first)
        val count = week?.count ?: 0
        val goal = week?.goal ?: view.goal
        val met = week?.met == true
        val run = Rewards.inARow(view.weeks, first)
        item {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SECTION_GAP)) {
                WeekCard(
                    count, goal,
                    if (met) {
                        stringResource(R.string.glow_goals_workouts_goal_met)
                    } else {
                        pluralStringResource(R.plurals.glow_goals_workouts_word, count)
                    },
                    if (met && run > 0) {
                        pluralStringResource(R.plurals.glow_goals_weekly_streak, run, run)
                    } else {
                        (goal - count).coerceAtLeast(0).let { pluralStringResource(R.plurals.glow_goals_short_of_goal, it, it) }
                    },
                    good = met && run > 0,
                )
                val days = weekDots(view, first)
                val today = LocalDate.now()
                DayPills(days, today = (0 until 7).firstOrNull { first.plusDays(it.toLong()) == today })
            }
        }
        val zone = ZoneId.systemDefault()
        val from = first.atStartOfDay(zone).toEpochSecond()
        val to = first.plusDays(7).atStartOfDay(zone).toEpochSecond()
        val highlights = view.bests.filter { it.date in from until to }
        if (highlights.isNotEmpty()) item {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SECTION_GAP)) {
                HaloTitle(
                    stringResource(R.string.glow_goals_highlights), variant = TitleVariant.OutOfCard,
                    note = pluralStringResource(R.plurals.glow_goals_bests_count, highlights.size, highlights.size),
                )
                BestsGrid(highlights, onOpen = { openBest = it })
            }
        }
        item {
            val mine = view.all.filter { it.startedByYou && it.shownStart in from until to }
            val seconds = mine.sumOf { it.activeSeconds }
            val meters = mine.sumOf { (it.meters ?: 0f).toDouble() }.toFloat()
            val kcal = mine.mapNotNull { it.activeKcal }.sum()
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SECTION_GAP)) {
                HaloTitle(
                    stringResource(R.string.glow_goals_in_all), variant = TitleVariant.OutOfCard,
                    note = pluralStringResource(R.plurals.glow_goals_workouts_count, mine.size, mine.size),
                )
                HaloFigures(
                    listOf(
                        Units.clockDuration(seconds) to stringResource(R.string.glow_goals_hours),
                        Units.km(meters, Units.Precision.Day) to stringResource(R.string.glow_goals_distance),
                        Units.count(Math.round(kcal)) to stringResource(R.string.glow_goals_active_kcal),
                    ),
                    units = listOf(null, stringResource(R.string.glow_unit_km), null),
                )
            }
        }
        item {
            HaloCard(fill = CardFill.Rows) {
                NavRow(
                    stringResource(R.string.glow_goals_weekly_goal),
                    pluralStringResource(R.plurals.you_goals_workouts_value, view.goal, view.goal),
                    onClick = onGoal, divider = false,
                )
            }
        }
        item {
            HaloButton(
                stringResource(R.string.glow_goals_share_week), { sharing = true }, Modifier.fillMaxWidth(), icon = AppIcons.UploadSimple,
            )
        }
    }
    if (view != null) openBest?.let { kind -> BestSheet(kind, view, onDismiss = { openBest = null }) }
}

private val SECTION_GAP = 16.dp
private const val GRID_COLUMNS = 3
