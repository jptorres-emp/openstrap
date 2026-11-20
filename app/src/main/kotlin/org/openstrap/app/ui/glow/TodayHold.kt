package org.openstrap.app.ui.glow

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import org.openstrap.app.R
import org.openstrap.app.ui.dashboard.TodayCard
import org.openstrap.app.ui.dashboard.TodayEdits
import org.openstrap.app.ui.theme.AppIcons
import org.openstrap.app.ui.theme.Corner
import org.openstrap.app.ui.theme.Greys
import org.openstrap.app.ui.theme.Ink
import kotlin.math.abs

/*
 * Holding a card on Today (board HaloTodayHold): the card lifts and a menu opens beside it, to pin it under the top
 * card, hide it, or open Edit Today. A light tap still opens the card. A screen reader gets the same as actions.
 */

/**
 * Calls [onHold] when the card is held still for a long press, before the card's own tap sees it: the press that
 * follows is taken, so letting go opens nothing. A drag before then is a scroll and is left alone. The gesture starts
 * again when [key] changes, so [onHold] should only say which card it was.
 */
internal fun Modifier.holdToEdit(key: Any, enabled: Boolean, onHold: () -> Unit): Modifier = if (!enabled) this else pointerInput(key) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        // Null when the press outlasts the long press timeout without letting go or moving: a hold.
        val held = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { letGoOrMoved(down.id, viewConfiguration.touchSlop) }
        if (held == null) {
            onHold()
            // The rest of this press is the menu's: the card underneath sees it taken and doesn't open.
            do {
                val e = awaitPointerEvent(PointerEventPass.Initial)
                e.changes.forEach { it.consume() }
            } while (e.changes.any { it.pressed })
        }
    }
}

/** Returns once the press [id] lets go, is taken by something else, or moves past [slop]. */
private suspend fun AwaitPointerEventScope.letGoOrMoved(id: PointerId, slop: Float) {
    var moved = 0f
    do {
        val c = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == id }
        // Still held where it went down: neither let go nor taken by something else.
        val still = c != null && !c.changedToUp() && !c.isConsumed
        if (c != null) moved += abs(c.positionChange().x) + abs(c.positionChange().y)
    } while (still && moved <= slop)
}

/** The held card's menu items and actions, for the menu and for a screen reader. */
internal class TodayHoldActions(card: TodayCard, edits: TodayEdits, onEdits: (TodayEdits) -> Unit, onEditToday: () -> Unit) {
    val pinned = card in edits.pinned
    val canPin = edits.canPin(card)
    val pin: () -> Unit = { onEdits(edits.togglePin(card)) }
    val hide: () -> Unit = { onEdits(edits.toggleHidden(card)) }
    val editToday = onEditToday
    val pinnable = card.pinnable
}

/** The screen reader's way to what holding a card offers. */
@Composable
internal fun Modifier.holdActions(card: TodayCard, actions: TodayHoldActions?): Modifier {
    if (actions == null || !card.editable) return this
    val pin = stringResource(if (actions.pinned) R.string.glow_today_hold_unpin else R.string.glow_today_hold_pin)
    val hide = stringResource(R.string.glow_today_hold_hide)
    val edit = stringResource(R.string.glow_today_edit)
    val a by rememberUpdatedState(actions)
    return semantics {
        customActions = listOfNotNull(
            CustomAccessibilityAction(pin) { a.pin(); true }.takeIf { actions.pinnable && actions.canPin },
            CustomAccessibilityAction(hide) { a.hide(); true },
            CustomAccessibilityAction(edit) { a.editToday(); true },
        )
    }
}

/**
 * The menu a held card opens, beside it (below, or above near the foot of the screen): Pin under the top card (Unpin
 * once pinned, off while two are pinned already), Hide from Today, Edit Today. Each closes it.
 */
@Composable
internal fun TodayHoldMenu(open: Boolean, actions: TodayHoldActions, onDismiss: () -> Unit) {
    DropdownMenu(
        expanded = open, onDismissRequest = onDismiss,
        offset = DpOffset(0.dp, 8.dp), shape = Corner.Inner, containerColor = Greys.Control,
        tonalElevation = 0.dp, shadowElevation = 12.dp, border = BorderStroke(1.dp, Ink.Edge),
    ) {
        if (actions.pinnable) HoldItem(
            if (actions.pinned) R.string.glow_today_hold_unpin else R.string.glow_today_hold_pin, AppIcons.Pin,
            enabled = actions.canPin,
        ) { onDismiss(); actions.pin() }
        HoldItem(R.string.glow_today_hold_hide, AppIcons.ViewOff) { onDismiss(); actions.hide() }
        HoldItem(R.string.glow_today_edit, AppIcons.SlidersHorizontal) { onDismiss(); actions.editToday() }
    }
}

@Composable
private fun HoldItem(label: Int, icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    val ink = if (enabled) Ink.Strong else Ink.Disabled
    DropdownMenuItem(
        text = { Text(stringResource(label), style = GlowType.body, color = ink) },
        onClick = onClick, enabled = enabled,
        leadingIcon = { Icon(icon, null, Modifier.size(20.dp), tint = ink) },
        modifier = Modifier.widthIn(min = 220.dp),
    )
}
