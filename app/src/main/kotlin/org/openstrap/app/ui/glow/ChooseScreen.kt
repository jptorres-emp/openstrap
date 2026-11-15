package org.openstrap.app.ui.glow

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.openstrap.app.R
import org.openstrap.app.data.RecentChip
import org.openstrap.app.data.SportGroup
import org.openstrap.app.data.SportGroups
import org.openstrap.app.data.SportSection
import org.openstrap.app.data.Workouts
import org.openstrap.app.device.ConnectionState
import org.openstrap.app.ui.components.Motion
import org.openstrap.app.ui.components.pressClickable
import org.openstrap.app.ui.dashboard.ScreenList
import org.openstrap.app.ui.theme.AppIcons
import org.openstrap.app.ui.theme.SportIcons
import org.openstrap.app.ui.workout.familyColor

/* ---------------------------------------------- Choose ----------------------------------------------- */

/** Your usual sport on Choose, with its one line: what you did last time. */
data class ChooseSport(val type: Int, val line: String)

/**
 * Choose (workouts spec 2.1), the first screen of a workout and every sport on it, so one tap from Activity's All
 * sports reaches them all: "Start a workout" and the strap in one line; your usual as a slim unlit row with Start, which starts with last time's settings and skips Ready ([onStartUsual]),
 * while the row elsewhere opens Ready ([onReady]); the search over every sport; Recent as chips, saved workouts among
 * them ([onSaved]); then every sport as tiles in its family's section (board HaloChooseSport). A new user has no usual,
 * and Run, Walk and Strength as recent.
 */
@Composable
fun GlowChooseScreen(
    usual: ChooseSport?,
    recent: List<RecentChip>,
    connection: ConnectionState,
    liveHeartRate: Int?,
    contentPadding: PaddingValues,
    onStartUsual: (Int) -> Unit,
    onReady: (Int) -> Unit,
    onBack: () -> Unit,
    onSaved: (RecentChip.Saved) -> Unit = {},
) {
    var page by rememberSaveable { mutableStateOf(ChoosePage.Choose) }
    BackHandler { if (page != ChoosePage.Choose) page = ChoosePage.Choose else onBack() }
    SharedTransitionLayout {
        AnimatedContent(page, transitionSpec = { choosePageMotion() }, label = "choose") { shown ->
            // The search box glides from its place on Choose to the top of its page, and back, as the rest crossfades.
            val searchBox = Modifier.sharedBounds(
                rememberSharedContentState(SEARCH_BOX), this@AnimatedContent, boundsTransform = { _, _ -> SearchBounds },
                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            )
            when (shown) {
                ChoosePage.Search -> SearchPage(contentPadding, onReady, { page = ChoosePage.Choose }, searchBox)
                ChoosePage.Choose -> ChooseBody(
                    usual, recent, connection, liveHeartRate, contentPadding, onStartUsual, onReady, onBack, onSaved,
                    onSearch = { page = ChoosePage.Search }, modifier = searchBox,
                )
            }
        }
    }
}

/** Choose and the search page it opens in place. */
private enum class ChoosePage { Choose, Search }

private const val SEARCH_BOX = "choose_search"
private val SearchBounds = tween<Rect>(Motion.SWIFT, easing = Motion.easing)

/** To and from the search, a crossfade the search box glides through. */
private fun choosePageMotion(): ContentTransform =
    ContentTransform(fadeIn(tween(Motion.SWIFT, easing = Motion.easing)), fadeOut(tween(Motion.QUICK)), sizeTransform = null)

/** Choose itself. [modifier] goes on its search box, which glides into the search page. */
@Suppress("LongParameterList", "ModifierNotUsedAtRoot", "UnstableCollections", "ComposeUnstableCollections")
@Composable
private fun ChooseBody(
    usual: ChooseSport?,
    recent: List<RecentChip>,
    connection: ConnectionState,
    liveHeartRate: Int?,
    contentPadding: PaddingValues,
    onStartUsual: (Int) -> Unit,
    onReady: (Int) -> Unit,
    onBack: () -> Unit,
    onPickPreset: (RecentChip.Saved) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val header: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(AppIcons.X, stringResource(R.string.glow_close), onBack)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.glow_choose_title), color = Color.White, style = GlowType.pageTitle, maxLines = 1)
        }
    }
    val top: @Composable ColumnScope.() -> Unit = {
        StrapLine(connection, liveHeartRate)
        if (usual != null) UsualRow(usual, { onStartUsual(usual.type) }, { onReady(usual.type) })
        // The search opens a page of its own (board ActivitySearchTyping).
        SearchEntry(stringResource(R.string.glow_choose_search_all), onSearch, modifier)
        if (recent.isNotEmpty()) {
            SectionLabel(stringResource(R.string.glow_choose_recent))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                recent.forEach { chip ->
                    when (chip) {
                        is RecentChip.Sport -> RecentPill(chip.type, Workouts.shortName(chip.type)) { onReady(chip.type) }
                        is RecentChip.Saved -> RecentPill(chip.sport, chip.name) { onPickPreset(chip) }
                    }
                }
            }
        }
    }
    val sections: @Composable ColumnScope.() -> Unit = { SportSections(SportGroups.chooseSections(), { false }, onReady) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Unfolded (spec 2.4): the sections in a column of their own beside the strap, usual, search and Recent, each
        // side scrolling by itself.
        if (maxWidth >= GlowLayout.TwoColumnWidth) CompositionLocalProvider(LocalPageColumn provides GlowLayout.MaxContentWidth) {
            WorkoutPage(Color.Transparent, contentPadding, scroll = false) {
                header()
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GlowLayout.ColumnGap * 2)) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp), content = top)
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp), content = sections)
                }
            }
        } else WorkoutPage(Color.Transparent, contentPadding) {
            header()
            top()
            sections()
        }
    }
}

/**
 * Your usual: a slim unlit card with its name, what you did last time, and Start at its end. The row itself opens
 * Ready.
 */
@Composable
private fun UsualRow(s: ChooseSport, onStart: () -> Unit, onOpen: () -> Unit) {
    val color = familyColor(Workouts.family(s.type))
    val haptics = org.openstrap.app.ui.components.rememberHaptics()
    PictureCard(color, Modifier.fillMaxWidth(), onClick = onOpen, contentPadding = PaddingValues(start = 18.dp, top = 14.dp,
        end = 14.dp, bottom = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SportIconTile(s.type, color)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.glow_choose_your_usual), color = Color.White.copy(alpha = GlowType.SECONDARY_ALPHA),
                    style = GlowType.secondary,
                )
                Text(
                    Workouts.sportName(s.type), color = Color.White, style = GlowType.headerDate, maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
                if (s.line.isNotEmpty()) Text(
                    s.line, color = Color.White.copy(alpha = GlowType.SECONDARY_ALPHA), style = GlowType.secondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            PageButton(
                stringResource(R.string.glow_choose_start),
                { haptics.perform(org.openstrap.app.ui.components.HapticEvent.WorkoutStart); onStart() },
                primary = true, icon = AppIcons.PlayFill,
            )
        }
    }
}

/** A sport's icon on a 44 tile tinted its family's colour at 16 percent (boards HaloChooseSport, HaloSwitchSport). */
@Composable
private fun SportIconTile(type: Int, color: Color) {
    Box(Modifier.size(44.dp).clip(GlowLayout.IconShape).background(color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        Icon(SportIcons.of(type), null, tint = color, modifier = Modifier.size(24.dp))
    }
}

/** Change sport's "Now" card (board HaloSwitchSport): the sport you're on, on a plain panel, with what carries over. */
@Composable
private fun NowCard(type: Int) {
    Row(
        Modifier.fillMaxWidth().clip(GlowLayout.TileShape).background(PlainPanel).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SportIconTile(type, familyColor(Workouts.family(type)))
        Spacer(Modifier.width(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.glow_choose_now), color = GlowType.noteColor, style = GlowType.noteSmall)
            Text(Workouts.sportName(type), color = Color.White, style = GlowType.pageTitle, maxLines = 1)
            Text(stringResource(R.string.glow_choose_now_note), color = GlowType.noteColor, style = GlowType.noteSmall)
        }
    }
}

/** Choose's quiet chips, a step off black. */
private val QuietCard = SolidTop

/** A Recent chip: the sport's icon in its colour and a name, a sport's or a saved workout's, on a quiet pill. */
@Composable
private fun RecentPill(sport: Int, name: String, onClick: () -> Unit) =
    IconPill(name, onClick, icon = SportIcons.of(sport), tint = familyColor(Workouts.family(sport)), surface = QuietCard, hairline = true)

/* --------------------------------------------- Sport picker -------------------------------------------- */

/**
 * A sport's tile (spec 6.3), in the picker and on Activity's Start a workout: a 64 dp Control tile with 16 dp corners,
 * the [icon] at 24 dp, the [name] in Secondary under it. The chosen sport's tile is white with a black icon; an
 * [outdoor] sport carries the 12 dp location mark in its corner.
 */
@Composable
internal fun SportTile(
    icon: ImageVector,
    name: String,
    modifier: Modifier,
    outdoor: Boolean,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    // Tight corners and room underneath, so the press area never clips a label that fills the width.
    Column(
        modifier.pressClickable(onClick, GlowLayout.ChipShape).semantics(mergeDescendants = true) { role = Role.Button }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(SportTileSize).clip(GlowLayout.TileShape).background(if (selected) Color.White else HaloButtonSurface),
            contentAlignment = Alignment.Center,
        ) {
            val ink = if (selected) Color.Black else Color.White
            Icon(icon, null, tint = ink, modifier = Modifier.size(24.dp))
            if (outdoor) Icon(
                SportIcons.Location, stringResource(R.string.glow_choose_outdoors), tint = ink.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(12.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        // Two lines, so a long name in More sports' search isn't cut short. A name wider than the tile breaks after its
        // hyphen ("Cross-" over "training"), else at a hyphenation point, never between any two letters.
        Text(
            name.replace("-", "-\u200B"), color = Color.White.copy(alpha = GlowType.SECONDARY_ALPHA),
            style = GlowType.secondary.copy(hyphens = Hyphens.Auto), maxLines = 2,
            textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis,
        )
    }
}

private val SportTileSize = 64.dp

/** The sport list on its own, for saving a stretch nobody started. */
@Composable
fun GlowSportPickerScreen(
    contentPadding: PaddingValues, onPick: (Int) -> Unit, onBack: () -> Unit,
    recentSports: List<Int> = emptyList(), sides: Map<Int, Boolean> = emptyMap(),
) = SportPicker(-1, recentSports, contentPadding, { onPick(pickedType(it, sides)) }, onBack)

/* ------------------------------------------- Choose a sport ------------------------------------------ */

/**
 * Choose a sport (spec 6.3), for Ready's sport and for saving a stretch nobody started: a page with the search field,
 * your Recent sports as chips, then every sport as tiles in its family's section (board HaloSwitchSport). Search looks
 * through every sport, both kinds of a two-kind sport included, and lists them as rows with their family. A tile picks
 * its sport's key ([Workouts.tileType]); Ready's switch then picks the kind. [selected] is the sport chosen now, -1
 * for none, drawn as a stronger tile with a white ring. Choose has the same parts.
 */
@Composable
internal fun SportPicker(selected: Int, recent: List<Int>, contentPadding: PaddingValues, onPick: (Int) -> Unit, onBack: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val chosen = Workouts.tileType(selected)
    ScreenList(contentPadding, gutter = GlowLayout.Gutter) {
        item { PageHeader(stringResource(if (selected >= 0) R.string.glow_choose_change_title else
            R.string.glow_choose_pick_title), onBack) }
        if (selected >= 0) item(key = "now") { NowCard(selected) }
        item { SearchField(query, stringResource(R.string.glow_choose_search)) { query = it } }
        if (query.isNotBlank()) {
            item(key = "found") { SearchResults(query, onPick) }
            return@ScreenList
        }
        if (recent.isNotEmpty()) item(key = "recent") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(stringResource(R.string.glow_choose_recent))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    recent.forEach { RecentPill(it, Workouts.shortName(it)) { onPick(it) } }
                }
            }
        }
        item(key = "sections") {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SportSections(SportGroups.chooseSections(), { it == chosen }, onPick)
            }
        }
    }
}

/** What the search finds in the picker, as rows with their family, or that nothing matches [query]. Picking one puts the keyboard away. */
@Composable
private fun SearchResults(query: String, onPick: (Int) -> Unit) {
    val found = remember(query) { Workouts.search(query) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    if (found.isEmpty()) Text(
        stringResource(R.string.glow_choose_no_match, query.trim()), color = Color.White.copy(alpha = GlowType.SECONDARY_ALPHA),
        style = GlowType.secondary, modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
    else FoundRows(found) { keyboard?.hide(); focus.clearFocus(); onPick(it) }
}

/* -------------------------------------------- Family sections ---------------------------------------- */

/** A section's title (board HaloChooseSport): the family, as the boards name it. */
@Composable
@ReadOnlyComposable
private fun groupTitle(group: SportGroup): String = stringResource(when (group) {
    SportGroup.Distance -> R.string.glow_choose_group_distance
    SportGroup.IndoorCardio -> R.string.glow_choose_group_indoor
    SportGroup.StrengthIntervals -> R.string.glow_choose_group_strength
    SportGroup.MindBody -> R.string.glow_choose_group_mind
    SportGroup.Games -> R.string.glow_choose_group_games
    SportGroup.Other -> R.string.glow_choose_group_other
})

/** The line under a section's title: what its sports record. */
@Composable
@ReadOnlyComposable
private fun groupNote(group: SportGroup): String = stringResource(when (group) {
    SportGroup.Distance -> R.string.glow_choose_group_distance_note
    SportGroup.IndoorCardio -> R.string.glow_choose_group_indoor_note
    SportGroup.StrengthIntervals -> R.string.glow_choose_group_strength_note
    SportGroup.MindBody -> R.string.glow_choose_group_mind_note
    SportGroup.Games -> R.string.glow_choose_group_games_note
    SportGroup.Other -> R.string.glow_choose_group_other_note
})

/** A section's colour: its family's, and the board's grey for Other, which has no family of its own. */
private fun groupColor(group: SportGroup): Color = when (group) {
    SportGroup.Distance -> familyColor(Workouts.Family.Distance)
    SportGroup.IndoorCardio -> familyColor(Workouts.Family.IndoorCardio)
    SportGroup.StrengthIntervals -> familyColor(Workouts.Family.Strength)
    SportGroup.MindBody -> familyColor(Workouts.Family.MindBody)
    SportGroup.Games -> familyColor(Workouts.Family.Games)
    SportGroup.Other -> GlowType.footnoteColor
}

/** A sport's own colour: its family's, or Other's grey. */
private fun sportColor(type: Int): Color = groupColor(SportGroups.of(type))

/** The sections of sport tiles, four to a row, each under its family's title (board HaloChooseSport). */
@Suppress("UnstableCollections", "ComposeUnstableCollections")
@Composable
private fun SportSections(sections: List<SportSection>, isSelected: (Int) -> Boolean, onPick: (Int) -> Unit) {
    sections.forEach { section ->
        Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(section.group, section.types.size)
            section.types.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    row.forEach { type ->
                        FamilyTile(type, isSelected(type), Modifier.weight(1f)) { onPick(type) }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** A section's heading: a 4 by 28 bar in its colour, the title over what it records, and the sport count at the end. */
@Composable
private fun SectionHeader(group: SportGroup, count: Int) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 4.dp, height = 28.dp).clip(GlowLayout.BarShape).background(groupColor(group)))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(groupTitle(group), color = Color.White, style = GlowType.pageTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(groupNote(group), color = GlowType.noteColor, style = GlowType.noteSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            pluralStringResource(R.plurals.glow_choose_sports_count, count, count), color = GlowType.footnoteColor,
            style = GlowType.noteSmall,
        )
    }
}

/**
 * A sport's tile in a section (board HaloChooseSport): a 64 dp tile tinted its family's colour at 14 percent with the
 * icon in that colour, the short name under it. The sport you are on is tinted 30 percent and ringed white (board
 * HaloSwitchSport). A sport done only outdoors carries the location mark.
 */
@Composable
private fun FamilyTile(type: Int, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val color = sportColor(type)
    Column(
        modifier.pressClickable(onClick, GlowLayout.ChipShape).semantics(mergeDescendants = true) {
            role = Role.Button
            this.selected = selected
        }.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(SportTileSize).clip(GlowLayout.TileShape).background(color.copy(alpha = if (selected) 0.3f else 0.14f))
                .let { if (selected) it.border(2.dp, Color.White, GlowLayout.TileShape) else it },
            contentAlignment = Alignment.Center,
        ) {
            Icon(SportIcons.of(type), null, tint = color, modifier = Modifier.size(26.dp))
            if (type in Workouts.OUTDOOR_ONLY) Icon(
                SportIcons.Location, stringResource(R.string.glow_choose_outdoors), tint = color.copy(alpha = 0.8f),
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(12.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        // A name wider than the tile breaks after its hyphen ("Cross-" over "training"), never between two letters.
        Text(
            Workouts.shortName(type).replace("-", "-\u200B"), color = Color.White.copy(alpha = GlowType.SECONDARY_ALPHA),
            style = GlowType.secondary.copy(hyphens = Hyphens.Auto), maxLines = 2,
            textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** One search row (board HaloSearchSports): the icon on a 32 dp tile in its family's colour, the name over its family, a chevron. */
@Composable
private fun FoundRow(type: Int, divider: Boolean, onClick: () -> Unit) {
    val color = sportColor(type)
    Column {
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(RowHairline))
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick)
                .padding(start = 12.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(32.dp).clip(GlowLayout.IconShape).background(color.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(SportIcons.of(type), null, tint = color, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(Workouts.sportName(type), color = Color.White, style = GlowType.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(groupTitle(SportGroups.of(type)), color = GlowType.noteColor, style = GlowType.secondary, maxLines = 1)
            }
            RowChevron()
        }
    }
}

/** [types] as rows in one panel, each with its family, in the order given. */
@Composable
private fun FoundRows(types: List<Int>, onPick: (Int) -> Unit) = ListPanel {
    types.forEachIndexed { i, type -> FoundRow(type, i > 0) { onPick(type) } }
}

/** Choose's search field before it's typed in: it looks like the field and opens [SearchPage]. */
@Composable
private fun SearchEntry(hint: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().height(48.dp).clip(GlowLayout.TileShape).background(HaloButtonSurface)
            .pressClickable(onClick, GlowLayout.TileShape).semantics { role = Role.Button }.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(SportIcons.Search, null, tint = Color.White.copy(alpha = GlowType.CAPTION_ALPHA), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(hint, color = Color.White.copy(alpha = GlowType.CAPTION_ALPHA), style = GlowType.body, maxLines = 1)
    }
}

/**
 * The search over every sport (boards ActivitySearchTyping, HaloSearchSports): back and the field, with the keyboard
 * up, then how many sports match and a row for each, its icon, full name and family. Before anything is typed, every
 * sport in its family's section.
 */
// [modifier] goes on the search box, the one part that glides in from Choose.
@Suppress("ModifierNotUsedAtRoot")
@Composable
private fun SearchPage(contentPadding: PaddingValues, onPick: (Int) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    val found = remember(query) {
        if (query.isBlank()) SportGroups.OFFERED.sortedBy { Workouts.sportName(it) }
        // Names starting with what's typed first, then a word in them starting with it, then the rest.
        else Workouts.search(query).sortedBy { type ->
            val name = Workouts.sportName(type).lowercase()
            val q = query.trim().lowercase()
            when {
                name.startsWith(q) -> 0
                name.split(' ').any { it.startsWith(q) } -> 1
                else -> 2
            }
        }
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { focus.requestFocus() }
    ScreenList(contentPadding, gutter = GlowLayout.Gutter) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BackButton(onBack)
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f).then(modifier)) {
                    SearchField(query, stringResource(R.string.glow_choose_search_all), Modifier.focusRequester(focus)) { query = it }
                }
            }
        }
        item {
            Text(
                when {
                    query.isBlank() -> stringResource(R.string.glow_choose_all_sports)
                    found.isEmpty() -> stringResource(R.string.glow_choose_no_match, query.trim())
                    else -> pluralStringResource(R.plurals.glow_choose_sports_count, found.size, found.size)
                },
                color = Color.White.copy(alpha = GlowType.SECONDARY_ALPHA), style = GlowType.secondary,
                    modifier = Modifier.padding(start = 4.dp),
            )
        }
        val pick: (Int) -> Unit = { keyboard?.hide(); onPick(it) }
        // Before anything is typed, every sport under its family, A to Z in each; a search lists what it finds by
        // relevance with each one's family (board HaloSearchSports).
        if (found.isNotEmpty()) {
            if (query.isBlank()) SportGroups.sections(found).forEach { section ->
                item(key = section.group) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeader(section.group, section.types.size)
                        FoundRows(section.types, pick)
                    }
                }
            } else item { FoundRows(found, pick) }
        }
    }
}

/** The search on Choose and the picker: a 48 dp field on the Control surface with 16 dp corners and the search icon (spec 6.3). */
@Composable
private fun SearchField(query: String, hint: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    BasicTextField(
        query, onChange, modifier.fillMaxWidth(), singleLine = true,
        textStyle = GlowType.body.copy(color = Color.White),
        cursorBrush = SolidColor(Color.White),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        decorationBox = { field ->
            Row(
                Modifier.fillMaxWidth().height(48.dp).clip(GlowLayout.TileShape).background(HaloButtonSurface).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(SportIcons.Search, null, tint = Color.White.copy(alpha = GlowType.CAPTION_ALPHA), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text(hint, color = Color.White.copy(alpha = GlowType.CAPTION_ALPHA),
                        style = GlowType.body, maxLines = 1)
                    field()
                }
                if (query.isNotEmpty()) Icon(
                    AppIcons.X, stringResource(R.string.glow_choose_clear), tint = Color.White.copy(alpha = GlowType.SECONDARY_ALPHA),
                    // 48 dp to tap, the mark drawn 16 dp as before, and moved out so it stays where it was.
                    modifier = Modifier.offset(x = 10.dp).size(48.dp).clip(CircleShape).clickable { onChange("") }.padding(16.dp),
                )
            }
        },
    )
}
