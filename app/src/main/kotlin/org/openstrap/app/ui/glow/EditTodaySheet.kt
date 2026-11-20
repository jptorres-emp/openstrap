package org.openstrap.app.ui.glow

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import org.openstrap.app.R
import org.openstrap.app.ui.components.HapticEvent
import org.openstrap.app.ui.components.rememberHaptics
import org.openstrap.app.ui.dashboard.TodayCard
import org.openstrap.app.ui.dashboard.TodayEdits
import org.openstrap.app.ui.theme.AppIcons
import org.openstrap.app.ui.theme.Ink
import org.openstrap.app.ui.theme.Space
import kotlin.math.roundToInt

/**
 * Edit Today (board HaloEditToday): the pinned cards first, each with a handle to drag them into order, then the rest,
 * which follow the time of day, each with a Pin chip when it can be pinned and a switch for whether it shows. Saved as
 * a whole on Done. Pulling the sheet down or Cancel leaves Today as it was. The pins and hides not yet saved outlive a
 * rotation, a fold and process death ([TodayEditsSaver]).
 */
@Composable
internal fun EditTodaySheet(edits: TodayEdits, onSave: (TodayEdits) -> Unit, onDismiss: () -> Unit) {
    var e by rememberSaveable(stateSaver = TodayEditsSaver) { mutableStateOf(edits) }
    HaloSheet(
        onDismiss, stringResource(R.string.glow_today_edit), note = stringResource(R.string.glow_today_edit_note),
        footer = {
            ButtonPair(
                stringResource(R.string.glow_cancel),
                onDismiss,
                stringResource(R.string.glow_done),
                { onSave(e) },
            )
        },
    ) {
        if (e.pinned.isNotEmpty()) {
            HaloTitle(stringResource(R.string.glow_today_edit_pinned), variant = TitleVariant.Group)
            PinnedCards(e.pinned, onMove = { from, to -> e = e.movePin(from, to) }, onUnpin = { e = e.togglePin(it) })
        }
        val rest = TodayCard.entries.filter { it.editable && it !in e.pinned }
        if (e.pinned.isNotEmpty()) HaloTitle(stringResource(R.string.glow_today_edit_rest), variant = TitleVariant.Group)
        HaloCard(fill = CardFill.Rows) {
            rest.forEachIndexed { i, card ->
                val shown = card !in e.hidden
                // The whole row flips the switch, as a SwitchRow does, so the switch needs no padded target of its own
                // and every row is as high as a pinned one.
                HaloRow(
                    card.label,
                    tapModifier = Modifier.toggleable(shown, role = Role.Switch) { e = e.toggleHidden(card) },
                    trail = {
                        Row(
                            Modifier.height(ChipSize.Regular.height), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Space.S8),
                        ) {
                            if (card.pinnable) HaloChip(
                                stringResource(R.string.glow_today_edit_pin), { e = e.togglePin(card) },
                                variant = ChipVariant.Choice, selected = false, enabled = e.canPin(card),
                            )
                            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                                RowTrail.Toggle(shown, { e = e.toggleHidden(card) })
                            }
                        }
                    },
                    divider = i > 0,
                )
            }
        }
    }
}

/**
 * The pinned cards in their order under the hero. Each row's handle drags it up or down; it lands where it's let go,
 * a row at a time. A screen reader gets Move up and Move down instead. The Pinned chip unpins.
 */
@Suppress("UnstableCollections", "ComposeUnstableCollections")
@Composable
private fun PinnedCards(pinned: List<TodayCard>, onMove: (Int, Int) -> Unit, onUnpin: (TodayCard) -> Unit) {
    var dragged by remember { mutableIntStateOf(-1) }
    var offset by remember { mutableFloatStateOf(0f) }
    var rowHeight by remember { mutableIntStateOf(0) }
    val haptics = rememberHaptics()
    val order by rememberUpdatedState(pinned)
    val move by rememberUpdatedState(onMove)
    // How many rows the dragged one has passed, so the others make room as it goes.
    val shift = if (rowHeight > 0) (offset / rowHeight).roundToInt() else 0
    val target = (dragged + shift).coerceIn(0, (pinned.size - 1).coerceAtLeast(0))
    val drag = stringResource(R.string.glow_today_drag)
    val up = stringResource(R.string.glow_today_move_up)
    val down = stringResource(R.string.glow_today_move_down)
    HaloCard(fill = CardFill.Rows) {
        Column {
            pinned.forEachIndexed { i, card ->
                val lift = when {
                    dragged < 0 -> 0f
                    i == dragged -> offset
                    i in (dragged + 1)..target -> -rowHeight.toFloat()
                    i in target until dragged -> rowHeight.toFloat()
                    else -> 0f
                }
                Row(
                    Modifier.fillMaxWidth().zIndex(if (i == dragged) 1f else 0f).graphicsLayer { translationY = lift }
                        .onSizeChanged { rowHeight = it.height }
                        .semantics {
                            customActions = listOfNotNull(
                                CustomAccessibilityAction(up) { onMove(i, i - 1); true }.takeIf { i > 0 },
                                CustomAccessibilityAction(down) { onMove(i, i + 1); true }.takeIf { i < pinned.lastIndex },
                            )
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(HANDLE).semantics { contentDescription = drag }
                            .pointerInput(card) {
                                detectVerticalDragGestures(
                                    onDragStart = { dragged = order.indexOf(card); offset = 0f; haptics.perform(HapticEvent.ScrubMark) },
                                    onDragEnd = {
                                        val to = (dragged + (offset / rowHeight.coerceAtLeast(1)).roundToInt()).coerceIn(0, order.lastIndex)
                                        if (to != dragged) move(dragged, to)
                                        dragged = -1; offset = 0f
                                    },
                                    onDragCancel = { dragged = -1; offset = 0f },
                                ) { change, dy ->
                                    change.consume()
                                    val from = dragged
                                    val before = (offset / rowHeight.coerceAtLeast(1)).roundToInt()
                                    // Held to the list: a row can't be dragged past the first or the last place.
                                    val h = rowHeight.toFloat()
                                    offset = (offset + dy).coerceIn(-from * h, (order.lastIndex - from) * h)
                                    if ((offset / rowHeight.coerceAtLeast(1)).roundToInt() != before) haptics.perform(HapticEvent.ScrubStep)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(AppIcons.DragDots, null, Modifier.size(Space.S16), tint = Ink.Tertiary)
                    }
                    Box(Modifier.weight(1f)) {
                        NavRow(card.label, divider = i > 0) {
                            HaloChip(
                                stringResource(R.string.glow_today_edit_pinned), { onUnpin(card) },
                                variant = ChipVariant.Choice, selected = true,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The drag handle's touch target, before each pinned row. */
private val HANDLE = 44.dp

/** Keeps [TodayEdits] in a saved state bundle as the hidden and the pinned cards' names, pins in their order. */
internal val TodayEditsSaver: Saver<TodayEdits, Any> = listSaver(
    save = { e -> listOf(ArrayList(e.hidden.map { it.name }), ArrayList(e.pinned.map { it.name })) },
    restore = { v ->
        @Suppress("UNCHECKED_CAST")
        fun cards(i: Int) = (v[i] as List<String>).map(TodayCard::valueOf)
        TodayEdits(cards(0).toSet(), cards(1))
    },
)
