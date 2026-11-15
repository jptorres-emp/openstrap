package org.openstrap.app.ui.glow

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
import org.openstrap.app.ui.dashboard.ScreenList
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
    Row(
        Modifier.fillMaxWidth().solidCard(GlowLayout.CardShape).padding(20.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        WeeklyGoalRing(shown, goal, 72.dp, pluralStringResource(R.plurals.glow_goals_count_of_goal, goal, shown, goal))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            DotFigure(stringResource(R.string.glow_goals_count_big, shown, goal), BigNumber.Tile)
            Text(caption, color = GlowType.noteColor, style = GlowType.secondary)
            Text(line, color = if (good) GoodLine else GlowType.noteColor, style = GlowType.secondary)
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

/**
 * The weekly goal on its own, in a sheet (board HaloWeeklyGoalSheet): the pencil on the Goals and bests page and the
 * recap. A stepper with the number in Funnel Display Light 44 and its word, a note, then Cancel and Save.
 */
@Composable
fun WeeklyGoalSheet(goal: Int, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    var value by rememberSaveable { mutableIntStateOf(goal) }
    PageSheet(onDismiss, stringResource(R.string.glow_goals_weekly_goal)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIconButton(
                AppIcons.Minus, stringResource(R.string.you_goals_workouts_fewer),
                { value = (value - 1).coerceIn(Goals.WORKOUTS_PER_WEEK) }, size = 44.dp, iconSize = 18.dp,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$value", color = Color.White, style = GlowType.goalValue, maxLines = 1)
                Text(
                    pluralStringResource(R.plurals.glow_goals_workouts_word, value), Modifier.padding(start = 6.dp, bottom = 6.dp),
                    color = GlowType.noteColor, style = GlowType.body, maxLines = 1,
                )
            }
            RoundIconButton(
                AppIcons.Plus, stringResource(R.string.you_goals_workouts_more), { value = (value + 1).coerceIn(Goals.WORKOUTS_PER_WEEK) },
                size = 44.dp, iconSize = 18.dp,
            )
        }
        Text(stringResource(R.string.glow_goals_sheet_note), color = GlowType.noteColor, style = GlowType.secondary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PageButton(stringResource(R.string.glow_cancel), onDismiss, Modifier.weight(1f))
            PageButton(stringResource(R.string.glow_save), { onSave(value) }, Modifier.weight(1f), primary = true)
        }
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
    ScreenList(contentPadding, gutter = GlowLayout.Gutter) {
        item { PageHeader(stringResource(R.string.glow_goals_title), onBack) }
        if (view == null) {
            item { PageNote(stringResource(R.string.glow_goals_loading)) }
            return@ScreenList
        }
        item {
            val count = view.week(view.thisWeek)?.count ?: 0
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SECTION_GAP)) {
                SectionHead(stringResource(R.string.glow_goals_this_week))
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
        PageRow(
            stringResource(R.string.glow_goals_all_badges),
            stringResource(R.string.glow_goals_earned_count, view.earned.size, Rewards.BADGES.size),
            onClick = onAllBadges, below = true, icon = AppIcons.CheckCircle, divider = false,
        )
        PageRow(
            stringResource(R.string.glow_goals_weekly_goal), pluralStringResource(R.plurals.you_goals_workouts_value, view.goal, view.goal),
            onClick = onGoal, below = true, icon = AppIcons.Target,
        )
        PageRow(stringResource(R.string.glow_goals_last_week), onClick = onRecap, icon = AppIcons.CalendarBlank)
        PageRow(
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
    ScreenList(contentPadding, gutter = GlowLayout.Gutter) {
        item { PageHeader(stringResource(R.string.glow_goals_all_badges), onBack) }
        if (view == null) return@ScreenList
        Rewards.Group.entries.forEach { group ->
            item {
                val badges = Rewards.BADGES.filter { it.group == group }
                val got = badges.count { b -> view.earned.any { it.id == b.id } }
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SECTION_GAP)) {
                    SectionHead(groupName(group), note = stringResource(R.string.glow_goals_earned_count, got, badges.size))
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
    ScreenList(contentPadding, gutter = GlowLayout.Gutter) {
        item { PageHeader(stringResource(R.string.glow_goals_your_week), onBack, subtitle = RewardsCopy.weekRange(first)) }
        if (view == null) return@ScreenList
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
                SectionHead(
                    stringResource(R.string.glow_goals_highlights),
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
                SectionHead(
                    stringResource(R.string.glow_goals_in_all),
                    note = pluralStringResource(R.plurals.glow_goals_workouts_count, mine.size, mine.size),
                )
                RecapFigures(
                    RecapFigure(
                        Units.clockDuration(seconds), stringResource(R.string.you_unit_h), stringResource(R.string.glow_goals_hours),
                    ),
                    RecapFigure(
                        Units.km(meters, Units.Precision.Day), stringResource(R.string.glow_unit_km),
                        stringResource(R.string.glow_goals_distance),
                    ),
                    RecapFigure(Units.count(Math.round(kcal)), "", stringResource(R.string.glow_goals_active_kcal)),
                )
            }
        }
        item {
            PageRows {
                PageRow(
                    stringResource(R.string.glow_goals_next_week_goal),
                    pluralStringResource(R.plurals.glow_goals_workouts_count, view.goal, view.goal),
                    onClick = onGoal, divider = false,
                )
            }
        }
        item {
            PageButton(
                stringResource(R.string.glow_goals_share_week), { sharing = true }, Modifier.fillMaxWidth(), icon = AppIcons.UploadSimple,
            )
        }
    }
    if (view != null) openBest?.let { kind -> BestSheet(kind, view, onDismiss = { openBest = null }) }
}

/**
 * The week's totals as plain columns (board HaloWeeklyRecap): the figure in Funnel Display Light 28 with its unit, its
 * label under, a 1 px rule between.
 */
@Composable
private fun RecapFigures(vararg figures: RecapFigure) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp).height(IntrinsicSize.Min)) {
        figures.forEachIndexed { i, (value, unit, label) ->
            if (i > 0) Box(Modifier.fillMaxHeight().width(1.dp).background(FigureRule))
            Column(
                Modifier.weight(1f).padding(start = if (i > 0) 16.dp else 0.dp), verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(value, color = Color.White, style = GlowType.figure, maxLines = 1)
                    if (unit.isNotEmpty()) {
                        Text(
                            unit, Modifier.padding(start = 4.dp, bottom = 3.dp), color = GlowType.noteColor, style = GlowType.secondary,
                            maxLines = 1,
                        )
                    }
                }
                Text(label, color = GlowType.noteColor, style = GlowType.footnote, maxLines = 1)
            }
        }
    }
}

/** One of the recap's totals: the figure, its unit after it (blank for none) and its label under. */
private data class RecapFigure(val value: String, val unit: String, val label: String)

private val FigureRule = Color(0xFF24262C)
private val SECTION_GAP = 16.dp
private const val GRID_COLUMNS = 3
