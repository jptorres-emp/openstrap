@file:Suppress("MatchingDeclarationName")

package org.openstrap.app.ui.glow

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.openstrap.app.R
import org.openstrap.app.data.WorkoutSetup
import org.openstrap.app.data.WorkoutSetup.GoalKind
import org.openstrap.app.data.WorkoutTarget
import org.openstrap.app.data.Workouts
import org.openstrap.app.ui.components.HapticEvent
import org.openstrap.app.ui.components.pressClickable
import org.openstrap.app.ui.components.rememberHaptics
import org.openstrap.app.ui.theme.Corner
import org.openstrap.app.ui.theme.Greys
import org.openstrap.app.ui.theme.IconSize
import org.openstrap.app.ui.theme.Ink
import org.openstrap.app.ui.theme.SportIcons
import org.openstrap.app.ui.theme.Space
import org.openstrap.app.ui.workout.StartCopy

/*
 * Ready's goal type and where you are (board HaloReadyEveryKind): the goal type as plain text tabs on the page, or one
 * Goal row with a menu for a sport with five or more; the zone buttons inside the hero; and the Outdoor switch of a
 * sport with an indoor side.
 */

/** The goal type controls' measurements. */
internal object GoalTypeSpec {
    /** Each tab's height: the tap target. */
    val TabHeight = IconSize.TileM

    /** Between the tabs, and in from the page's edge. */
    val TabGap = Space.S16
    val Side = Space.S8

    /** The white line under the picked tab. */
    val Underline = 2.dp

    /** A sport with more goal types than this gets the Goal menu row instead of tabs. */
    const val MOST_TABS = 4
}

/** The goal types as tabs: [names] in a row, the one at [picked] white, semibold and underlined, the rest grey. */
@Suppress("UnstableCollections", "ComposeUnstableCollections")
@Composable
internal fun GoalTabs(names: List<String>, picked: Int, onPick: (Int) -> Unit) {
    val group = stringResource(R.string.ready_goal_type)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = GoalTypeSpec.Side)
            .selectableGroup().semantics { contentDescription = group },
        horizontalArrangement = Arrangement.spacedBy(GoalTypeSpec.TabGap),
    ) {
        names.forEachIndexed { i, name ->
            val on = i == picked
            Box(
                Modifier.height(GoalTypeSpec.TabHeight)
                    .selectable(on, role = Role.Tab, onClick = { if (!on) onPick(i) })
                    .drawWithContent {
                        drawContent()
                        if (on) {
                            val h = GoalTypeSpec.Underline.toPx()
                            drawRect(Color.White, Offset(0f, size.height - h), androidx.compose.ui.geometry.Size(size.width, h))
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    name, color = if (on) Color.White else GlowType.noteColor,
                    style = if (on) GlowType.body.copy(fontWeight = FontWeight.SemiBold) else GlowType.body, maxLines = 1,
                )
            }
        }
    }
}

/** The goal type as one row: "Goal" at the left, the picked type as a chip with a caret at the right opening a menu of [names]. */
@Suppress("UnstableCollections", "ComposeUnstableCollections")
@Composable
internal fun GoalMenuRow(names: List<String>, picked: Int, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val change = stringResource(R.string.ready_goal_type_change, names[picked])
    Row(
        Modifier.fillMaxWidth().height(GoalTypeSpec.TabHeight).padding(horizontal = GoalTypeSpec.Side),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.ready_goal), Modifier.weight(1f), color = GlowType.noteColor, style = GlowType.body)
        Box {
            HaloChip(names[picked], { open = true }, caret = true, description = change, role = Role.DropdownList)
            DropdownMenu(
                expanded = open, onDismissRequest = { open = false },
                offset = DpOffset(0.dp, 4.dp), shape = Corner.Inner, containerColor = Greys.Control,
                tonalElevation = 0.dp, shadowElevation = 12.dp, border = BorderStroke(1.dp, Ink.Edge),
            ) {
                names.forEachIndexed { i, name ->
                    val on = i == picked
                    DropdownMenuItem(
                        text = { Text(name, style = GlowType.body, color = if (on) Color.White else Ink.Strong) },
                        onClick = { open = false; if (!on) onPick(i) },
                        trailingIcon = if (on) ({ RowTrail.Check() }) else null,
                        modifier = Modifier.widthIn(min = 180.dp).semantics { this.selected = on },
                    )
                }
            }
        }
    }
}

/** The goal types on the page: tabs for up to [GoalTypeSpec.MOST_TABS], else the Goal menu row. */
@Suppress("UnstableCollections", "ComposeUnstableCollections")
@Composable
internal fun GoalType(names: List<String>, picked: Int, onPick: (Int) -> Unit) =
    if (names.size > GoalTypeSpec.MOST_TABS) GoalMenuRow(names, picked, onPick) else GoalTabs(names, picked, onPick)

/** A cardio goal kind's name on its tab: Just go named for the sport, then Distance, Time, Zone and Intervals. */
internal fun kindName(k: GoalKind, type: Int): String = when (k) {
    GoalKind.None -> StartCopy.noGoal(type)
    GoalKind.Distance -> StartCopy.DISTANCE
    GoalKind.Time -> StartCopy.TIME
    GoalKind.Zone -> StartCopy.ZONE
    GoalKind.Intervals -> StartCopy.INTERVALS
}

/** The zone buttons in the hero: five, the chosen zone or range white with black figures, the rest a light glass. */
@Composable
internal fun ZoneButtons(goal: WorkoutTarget.Zones, onGoal: (WorkoutTarget.Zones) -> Unit) {
    val haptics = rememberHaptics()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.S4)) {
        for (z in 0 until Workouts.ZONES) {
            val on = z in goal.low..goal.high
            Box(
                Modifier.weight(1f).height(IconSize.TileM).clip(Corner.Inner).background(if (on) Color.White else Ink.Line)
                    .pressClickable({ haptics.perform(HapticEvent.ScrubMark); onGoal(WorkoutSetup.pickZone(goal, z)) }, Corner.Inner)
                    .semantics { selected = on },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${z + 1}", color = if (on) Color.Black else Color.White,
                    style = if (on) GlowType.body.copy(fontWeight = FontWeight.SemiBold) else GlowType.body,
                )
            }
        }
    }
}

/**
 * Outdoor, for a sport with an indoor side: on is outdoors with GPS ([gpsLine]), off is indoors ([offLine]). With the
 * phone's location off ([locationOn] false) the switch stays on, the pin is grey and struck through, and the line
 * says so with Turn it on ([onTurnOnLocation]).
 */
@Composable
internal fun OutdoorRow(
    outdoor: Boolean,
    onOutdoor: (Boolean) -> Unit,
    locationOn: Boolean,
    gpsLine: String,
    offLine: String,
    onTurnOnLocation: () -> Unit,
) {
    val blocked = outdoor && !locationOn
    HaloCard(fill = CardFill.Rows) {
        HaloRow(
            stringResource(R.string.ready_outdoor),
            lead = RowLead.custom(RowSpec.Tile) { PinTile(struck = blocked) },
            line = {
                if (blocked) Row(horizontalArrangement = Arrangement.spacedBy(Space.S4)) {
                    RowLine(stringResource(R.string.ready_location_off))
                    Text(
                        stringResource(R.string.ready_location_turn_on),
                        Modifier.clickable(role = Role.Button, onClick = onTurnOnLocation),
                        color = Color.White, style = LinkStyle,
                    )
                } else RowLine(if (outdoor) gpsLine else offLine)
            },
            trail = { RowTrail.Toggle(outdoor, onOutdoor) },
            tapModifier = Modifier.toggleable(outdoor, role = Role.Switch, onValueChange = onOutdoor),
            divider = false,
        )
    }
}

/** The location pin on a row's tile, grey and struck through when location is off. */
@Composable
private fun PinTile(struck: Boolean) {
    HaloTile(
        SportIcons.Location,
        Modifier.drawWithContent {
            drawContent()
            if (struck) {
                val inset = size.width * SLASH_INSET
                drawLine(
                    Ink.Tertiary, Offset(inset, inset), Offset(size.width - inset, size.height - inset),
                    strokeWidth = SLASH_WIDTH.toPx(), cap = StrokeCap.Round,
                )
            }
        },
        glyph = if (struck) Ink.Tertiary else null,
    )
}

/** "Turn it on" in a row's line: underlined, as a link. */
private val LinkStyle = GlowType.secondary.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)

private const val SLASH_INSET = 0.28f
private val SLASH_WIDTH = 1.5.dp
