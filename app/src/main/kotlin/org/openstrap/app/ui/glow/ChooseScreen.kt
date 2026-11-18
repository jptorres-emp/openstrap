package org.openstrap.app.ui.glow

import org.openstrap.app.ui.theme.IconSize
import org.openstrap.app.ui.theme.Corner
import org.openstrap.app.ui.theme.Space
import org.openstrap.app.ui.theme.Greys
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import org.openstrap.app.ui.theme.AppIcons
import org.openstrap.app.ui.theme.Ink
import org.openstrap.app.ui.theme.SportIcons
import org.openstrap.app.ui.workout.familyColor
import org.openstrap.app.ui.workout.sportFamilyColor

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
            HaloIconButton(AppIcons.X, stringResource(R.string.glow_close), onBack)
            Spacer(Modifier.width(Space.S12))
            Text(stringResource(R.string.glow_choose_title), color = Color.White, style = GlowType.pageTitle, maxLines = 1)
        }
    }
    val top: @Composable ColumnScope.() -> Unit = {
        StrapLine(connection, liveHeartRate)
        if (usual != null) UsualRow(usual, { onStartUsual(usual.type) }, { onReady(usual.type) })
        // The search opens a page of its own (board ActivitySearchTyping).
        SearchEntry(stringResource(R.string.glow_choose_search_all), onSearch, modifier)
        if (recent.isNotEmpty()) {
            HaloTitle(stringResource(R.string.glow_choose_recent), variant = TitleVariant.Group)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.S8)) {
                recent.forEach { chip ->
                    when (chip) {
                        is RecentChip.Sport -> SportChip(chip.type, Workouts.shortName(chip.type)) { onReady(chip.type) }
                        is RecentChip.Saved -> SportChip(chip.sport, chip.name) { onPickPreset(chip) }
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
            HaloPageColumn(contentPadding, scroll = false) {
                header()
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GlowLayout.ColumnGap * 2)) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(Space.S12), content = top)
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(Space.S12), content = sections)
                }
            }
        } else HaloPageColumn(contentPadding) {
            header()
            top()
            sections()
        }
    }
}

/**
 * Your usual: a card of one row, the sport's tile, its name, "Your usual" and what you did last time under it, and Start
 * at its end. The card itself opens Ready.
 */
@Composable
private fun UsualRow(s: ChooseSport, onStart: () -> Unit, onOpen: () -> Unit) {
    val color = sportFamilyColor(s.type)
    val haptics = org.openstrap.app.ui.components.rememberHaptics()
    HaloCard(fill = CardFill.Rows, onClick = onOpen) {
        HaloRow(
            Workouts.sportName(s.type), lead = RowLead.sport(SportIcons.of(s.type), color), divider = false,
            line = {
                Column {
                    RowLine(stringResource(R.string.glow_choose_your_usual))
                    if (s.line.isNotEmpty()) RowLine(s.line)
                }
            },
            trail = {
                HaloButton(
                    stringResource(R.string.glow_choose_start),
                    { haptics.perform(org.openstrap.app.ui.components.HapticEvent.WorkoutStart); onStart() },
                    variant = HaloButtonVariant.Primary, icon = AppIcons.PlayFill,
                )
            },
        )
    }
}

/** Change sport's "Now" card (board HaloSwitchSport): a card of one row, the sport you're on, with what carries over. */
@Composable
private fun NowCard(type: Int) {
    HaloCard(fill = CardFill.Rows) {
        HaloRow(
            Workouts.sportName(type), lead = RowLead.sport(SportIcons.of(type), sportFamilyColor(type)), divider = false,
            line = {
                Column {
                    RowLine(stringResource(R.string.glow_choose_now))
                    RowLine(stringResource(R.string.glow_choose_now_note))
                }
            },
        )
    }
}

/** A Recent sport as a [HaloChip]: its icon in its family's colour and a name, a sport's or a saved workout's. */
@Composable
private fun SportChip(sport: Int, name: String, onClick: () -> Unit) =
    HaloChip(name, onClick, icon = SportIcons.of(sport), tint = sportFamilyColor(sport), size = ChipSize.Main)

/* --------------------------------------------- Sport picker -------------------------------------------- */

/**
 * The mark of a sport done outdoors (boards HaloChooseSport, HaloSwitchSport): a white pin in a 22 dp circle on the
 * tile's top right edge, ringed in the page's black so the round tile never crops it.
 */
@Composable
private fun OutdoorBadge(modifier: Modifier = Modifier) {
    Box(
        modifier.offset(x = 5.dp, y = (-5).dp).size(26.dp).clip(CircleShape).background(Color.Black).padding(Space.S2)
            .clip(CircleShape).background(Greys.Raised),
        contentAlignment = Alignment.Center,
    ) { Icon(SportIcons.Location, stringResource(R.string.glow_choose_outdoors), tint = Color.White, modifier = Modifier.size(12.dp)) }
}


/**
 * A sport's tile (spec 6.3), in the picker and on Activity's Start a workout: a 64 [HaloTile], the [icon] at 24, the
 * [name] in Secondary under it. The chosen sport's tile is white with a black icon; an
 * [outdoor] sport carries the [OutdoorBadge].
 */
@Composable
internal fun SportTile(
    icon: ImageVector,
    name: String,
    modifier: Modifier,
    outdoor: Boolean,
    selected: Boolean = false,
    /** Tints the tile in this colour at 14% with the icon in it (board HaloActivity); null keeps it grey. */
    color: Color? = null,
    onClick: () -> Unit,
) {
    // Tight corners and room underneath, so the press area never clips a label that fills the width.
    Column(
        modifier.pressClickable(onClick, Corner.Chip).semantics(mergeDescendants = true) { role = Role.Button }
            .padding(vertical = Space.S4),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HaloTile(
            icon, size = TileSize.Large,
            tone = when {
                selected -> TileTone.Selected
                color != null -> TileTone.Ink(color)
                else -> TileTone.Neutral
            },
            glyph = if (selected || color != null) null else Color.White,
            badge = if (outdoor) ({ OutdoorBadge(Modifier.align(Alignment.TopEnd)) }) else null,
        )
        Spacer(Modifier.height(Space.S8))
        // Two lines, so a long name in More sports' search isn't cut short. A name wider than the tile breaks after its
        // hyphen ("Cross-" over "training"), else at a hyphenation point, never between any two letters.
        Text(
            name.replace("-", "-\u200B"), color = Ink.Secondary,
            style = GlowType.secondary.copy(hyphens = Hyphens.Auto), maxLines = 2,
            textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis,
        )
    }
}

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
    HaloPageList(contentPadding, variant = PageVariant.Workout) {
        item { HaloPageHeader(stringResource(if (selected >= 0) R.string.glow_choose_change_title else
            R.string.glow_choose_pick_title), onBack) }
        if (selected >= 0) item(key = "now") { NowCard(selected) }
        item { SearchField(query, stringResource(R.string.glow_choose_search)) { query = it } }
        if (query.isNotBlank()) {
            item(key = "found") { SearchResults(query, onPick) }
            return@HaloPageList
        }
        if (recent.isNotEmpty()) item(key = "recent") {
            Column(verticalArrangement = Arrangement.spacedBy(Space.S8)) {
                HaloTitle(stringResource(R.string.glow_choose_recent), variant = TitleVariant.Group)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Space.S8)) {
                    recent.forEach { SportChip(it, Workouts.shortName(it)) { onPick(it) } }
                }
            }
        }
        item(key = "sections") {
            Column(verticalArrangement = Arrangement.spacedBy(Space.S12)) {
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
        stringResource(R.string.glow_choose_no_match, query.trim()), color = Ink.Secondary,
        style = GlowType.secondary, modifier = Modifier.padding(start = Space.S4, top = Space.S8),
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
internal fun sportColor(type: Int): Color = groupColor(SportGroups.of(Workouts.tileType(type)))

/** The sections of sport tiles, four to a row, each under its family's title (board HaloChooseSport). */
@Suppress("UnstableCollections", "ComposeUnstableCollections")
@Composable
private fun SportSections(sections: List<SportSection>, isSelected: (Int) -> Boolean, onPick: (Int) -> Unit) {
    sections.forEach { section ->
        Column(Modifier.padding(top = Space.S4), verticalArrangement = Arrangement.spacedBy(Space.S12)) {
            SectionHeader(section.group, section.types.size)
            section.types.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.S8), verticalAlignment = Alignment.Top) {
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
    Row(Modifier.fillMaxWidth().padding(horizontal = Space.S4), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 4.dp, height = 28.dp).clip(Corner.Pill).background(groupColor(group)))
        Spacer(Modifier.width(Space.S8))
        Column(Modifier.weight(1f)) {
            Text(groupTitle(group), color = Color.White, style = GlowType.pageTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(groupNote(group), color = GlowType.noteColor, style = GlowType.noteSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(Space.S8))
        Text(
            pluralStringResource(R.plurals.glow_choose_sports_count, count, count), color = GlowType.footnoteColor,
            style = GlowType.noteSmall,
        )
    }
}

/**
 * A sport's tile in a section (board HaloChooseSport): a 64 [HaloTile] in its family's colour, the short name under it.
 * The sport you are on is tinted 30 percent and ringed white (board HaloSwitchSport). A sport done only outdoors carries the location mark.
 */
@Composable
private fun FamilyTile(type: Int, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val color = sportColor(type)
    Column(
        modifier.pressClickable(onClick, Corner.Chip).semantics(mergeDescendants = true) {
            role = Role.Button
            this.selected = selected
        }.padding(vertical = Space.S4),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HaloTile(
            SportIcons.of(type), size = TileSize.Large, tone = TileTone.Ink(color, picked = selected),
            badge = if (type in Workouts.OUTDOOR_ONLY) ({ OutdoorBadge(Modifier.align(Alignment.TopEnd)) }) else null,
        )
        Spacer(Modifier.height(Space.S8))
        // A name wider than the tile breaks after its hyphen ("Cross-" over "training"), never between two letters.
        Text(
            Workouts.shortName(type).replace("-", "-\u200B"), color = Ink.Secondary,
            style = GlowType.secondary.copy(hyphens = Hyphens.Auto), maxLines = 2,
            textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** One search row (board HaloSearchSports): the icon on a tile in its family's colour, the name over its family, a chevron. */
@Composable
private fun FoundRow(type: Int, divider: Boolean, onClick: () -> Unit) = HaloRow(
    Workouts.sportName(type), lead = RowLead.tile(SportIcons.of(type), sportColor(type)), titleSuffix = workoutTags(type),
    line = { RowLine(groupTitle(SportGroups.of(Workouts.tileType(type)))) }, trail = { RowTrail.Chevron() }, onClick = onClick, divider = divider,
)

/** [types] as rows in one panel, each with its family, in the order given. */
@Composable
private fun FoundRows(types: List<Int>, onPick: (Int) -> Unit) = ListPanel {
    types.forEachIndexed { i, type -> FoundRow(type, i > 0) { onPick(type) } }
}

/** Choose's search field before it's typed in: it looks like the field and opens [SearchPage]. */
@Composable
private fun SearchEntry(hint: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().height(48.dp).clip(GlowLayout.TileShape).background(Greys.Control)
            .pressClickable(onClick, GlowLayout.TileShape).semantics { role = Role.Button }.padding(horizontal = Space.S16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(SportIcons.Search, null, tint = Ink.Secondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Space.S8))
        Text(hint, color = Ink.Secondary, style = GlowType.body, maxLines = 1)
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
            val name = Workouts.exactName(type).lowercase()
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
    HaloPageList(contentPadding, variant = PageVariant.Workout) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HaloIconButton(AppIcons.ArrowLeft, stringResource(R.string.glow_back), onBack)
                Spacer(Modifier.width(Space.S12))
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
                color = Ink.Secondary, style = GlowType.secondary,
                    modifier = Modifier.padding(start = Space.S4),
            )
        }
        val pick: (Int) -> Unit = { keyboard?.hide(); onPick(it) }
        // Before anything is typed, every sport under its family, A to Z in each; a search lists what it finds by
        // relevance with each one's family (board HaloSearchSports).
        if (found.isNotEmpty()) {
            if (query.isBlank()) SportGroups.sections(found).forEach { section ->
                item(key = section.group) {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.S8)) {
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
                Modifier.fillMaxWidth().height(48.dp).clip(GlowLayout.TileShape).background(Greys.Control).padding(horizontal = Space.S16),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(SportIcons.Search, null, tint = Ink.Secondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Space.S8))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text(hint, color = Ink.Secondary,
                        style = GlowType.body, maxLines = 1)
                    field()
                }
                if (query.isNotEmpty()) Icon(
                    AppIcons.X, stringResource(R.string.glow_choose_clear), tint = Ink.Secondary,
                    // 48 dp to tap, the mark drawn 16 dp as before, and moved out so it stays where it was.
                    modifier = Modifier.offset(x = 10.dp).size(48.dp).clip(CircleShape).clickable { onChange("") }.padding(Space.S16),
                )
            }
        },
    )
}
