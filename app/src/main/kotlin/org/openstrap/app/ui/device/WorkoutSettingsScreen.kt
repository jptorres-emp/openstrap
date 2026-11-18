@file:Suppress("MatchingDeclarationName")

package org.openstrap.app.ui.device

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.openstrap.app.R
import org.openstrap.app.data.settings.AskToEnd
import org.openstrap.app.data.settings.AskToEndGroup
import org.openstrap.app.i18n.Strings
import org.openstrap.app.ui.alarms.Wheel
import org.openstrap.app.ui.glow.ButtonPair
import org.openstrap.app.ui.glow.GlowLayout
import org.openstrap.app.ui.glow.GlowType
import org.openstrap.app.ui.glow.HaloNote
import org.openstrap.app.ui.glow.HaloPageHeader
import org.openstrap.app.ui.glow.HaloPageList
import org.openstrap.app.ui.glow.HaloSheet
import org.openstrap.app.ui.glow.HaloRow
import org.openstrap.app.ui.glow.NavRow
import org.openstrap.app.ui.glow.PageGroup
import org.openstrap.app.ui.glow.PageVariant
import org.openstrap.app.ui.glow.RowLine
import org.openstrap.app.ui.glow.RowTrail
import org.openstrap.app.ui.glow.SwitchRow
import org.openstrap.app.ui.theme.Greys
import org.openstrap.app.ui.theme.Space
import org.openstrap.app.ui.workout.HeartZonesCopy

/** The words of the Workouts settings page and its time picker, as plain Kotlin for the tests. */
object AskToEndCopy {
    /** A group's time on its row and in the picker: "After 8 min", or "Off". */
    fun value(minutes: Int?, strings: Strings = Strings.app): String =
        if (minutes == null) strings.get(R.string.ask_end_off) else strings.format(R.string.ask_end_after, minutes)

    /** Under the picker: what the time chosen does. */
    fun sheetNote(minutes: Int?, strings: Strings = Strings.app): String =
        if (minutes == null) strings.get(R.string.ask_end_sheet_note_off) else strings.format(R.string.ask_end_sheet_note, minutes)

    /** The You page's Workouts row, under its name: Most workouts' time, or that it's off. */
    fun youLine(a: AskToEnd, strings: Strings = Strings.app): String = when {
        !a.on || AskToEndGroup.entries.all { a.minutes(it) == null } -> strings.get(R.string.ask_end_you_line_off)
        a.most != null -> strings.format(R.string.ask_end_you_line, a.most)
        else -> strings.get(R.string.ask_end_you_line_some)
    }

    /** A group's row name and the line under it. */
    fun title(group: AskToEndGroup, strings: Strings = Strings.app): String = strings.get(
        when (group) {
            AskToEndGroup.Most -> R.string.ask_end_most
            AskToEndGroup.Games -> R.string.ask_end_games
            AskToEndGroup.MindBody -> R.string.ask_end_mind_body
        },
    )

    fun note(group: AskToEndGroup, strings: Strings = Strings.app): String = strings.get(
        when (group) {
            AskToEndGroup.Most -> R.string.ask_end_most_note
            AskToEndGroup.Games -> R.string.ask_end_games_note
            AskToEndGroup.MindBody -> R.string.ask_end_mind_body_note
        },
    )
}

/**
 * The Workouts settings page (HaloWorkoutSettings board), opened from the You page: "Ask to end when I stop" with its
 * switch and a time for each group of sports, each opening a picker sheet, and under "During a workout" the way to the
 * Heart rate zones page, with your [maxHeartRate] as its value.
 */
@Composable
fun WorkoutSettingsScreen(
    askToEnd: AskToEnd,
    maxHeartRate: Int?,
    contentPadding: PaddingValues,
    onAskToEnd: (AskToEnd) -> Unit,
    onHeartZones: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    // The group whose time is being picked, by name so it survives a rotation.
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    HaloPageList(contentPadding, variant = PageVariant.Settings) {
        item { HaloPageHeader(stringResource(R.string.ask_end_page_title), onBack) }
        item {
            PageGroup(heading = stringResource(R.string.ask_end_section), footnote = stringResource(R.string.ask_end_footnote)) {
                SwitchRow(
                    stringResource(R.string.ask_end_switch), stringResource(R.string.ask_end_switch_note), askToEnd.on,
                    { on -> onAskToEnd(askToEnd.copy(on = on)) },
                    divider = false,
                )
                if (askToEnd.on) AskToEndGroup.entries.forEach { group ->
                    GroupRow(group, askToEnd.minutes(group)) { picking = group.name }
                }
            }
        }
        item {
            PageGroup(heading = stringResource(R.string.ask_end_during)) {
                NavRow(
                    HeartZonesCopy.TITLE,
                    maxHeartRate?.let { stringResource(R.string.ask_end_zones_max, it) },
                    onClick = onHeartZones, divider = false,
                )
            }
        }
    }
    picking?.let { name -> AskToEndGroup.entries.firstOrNull { it.name == name } }?.let { group ->
        AskToEndSheet(
            AskToEndCopy.title(group), askToEnd.minutes(group),
            onSave = { minutes ->
                picking = null
                onAskToEnd(askToEnd.with(group, minutes))
            },
            onDismiss = { picking = null },
        )
    }
}

/** A group's row: its name, which sports it holds, its time and the chevron. */
@Composable
private fun GroupRow(group: AskToEndGroup, minutes: Int?, onClick: () -> Unit) {
    HaloRow(
        AskToEndCopy.title(group),
        line = { RowLine(AskToEndCopy.note(group)) },
        value = AskToEndCopy.value(minutes),
        trail = { RowTrail.Chevron() },
        onClick = onClick,
    )
}

/**
 * A group's time on one wheel (HaloAskToEndSheet board): the minutes with "min" beside them and Off at the end, a note
 * on what the time does, then Cancel and Save. Cancel, or pulling the sheet down, leaves it as it was.
 */
@Composable
private fun AskToEndSheet(title: String, minutes: Int?, onSave: (Int?) -> Unit, onDismiss: () -> Unit) {
    val choices = AskToEnd.CHOICES + AskToEnd.OFF
    var picked by rememberSaveable { mutableIntStateOf(minutes ?: AskToEnd.OFF) }
    val off = stringResource(R.string.ask_end_off)
    val wheelName = stringResource(R.string.ask_end_wheel)
    HaloSheet(
        onDismiss, title,
        footer = {
            ButtonPair(
                stringResource(R.string.you_action_cancel), onDismiss, stringResource(R.string.you_action_save),
                { onSave(picked.takeIf { it != AskToEnd.OFF }) },
            )
        },
    ) {
        Box(Modifier.fillMaxWidth().padding(vertical = Space.S4), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().height(WheelBand).clip(GlowLayout.TileShape).background(Greys.Control))
            Row(
                Modifier.fillMaxWidth().semantics { contentDescription = wheelName },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.S16, Alignment.CenterHorizontally),
            ) {
                Wheel(
                    choices, picked, { picked = it }, { if (it == AskToEnd.OFF) off else it.toString() },
                    Modifier.width(WheelWidth),
                )
                Text(
                    stringResource(R.string.you_unit_min),
                    Modifier.alpha(if (picked == AskToEnd.OFF) 0f else 1f),
                    color = GlowType.noteColor, style = GlowType.body,
                )
            }
        }
        HaloNote(AskToEndCopy.sheetNote(picked.takeIf { it != AskToEnd.OFF }))
    }
}

/** The band behind the chosen minute, as the time wheels' (HaloQuietHoursSheet board), and the wheel's width. */
private val WheelBand = 64.dp
private val WheelWidth = 96.dp
