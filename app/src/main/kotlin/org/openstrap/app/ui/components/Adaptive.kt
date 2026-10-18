package org.openstrap.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified

/** From this width a settings page opens beside the list of them: the list is short rows, so it can be narrow. */
val SidePaneWidth = 600.dp

/** From this width a detail opens beside a dashboard: two phone-width (360 dp and up) screens side by side. */
val SideBySideWidth = 720.dp

/**
 * A screen and what it opens. On a phone it's [PushContent]: the opened screen slides over [list]. From
 * [minWidth] the opened screen sits in a pane on the right and [list] stays on the left,
 * narrower, so you can tap through one thing after another; back closes the pane. [list] keeps its place in
 * the tree either way, so its scroll position survives the pane opening and closing.
 *
 * [isOpen] says whether a [target] has anything to show in the pane. With the pane closed, [closedMaxWidth]
 * keeps [list] from stretching across the whole window.
 */
@Composable
fun <S> ListDetail(
    target: S,
    isOpen: (S) -> Boolean,
    depth: (S) -> Int,
    minWidth: Dp = SidePaneWidth,
    closedMaxWidth: Dp = Dp.Unspecified,
    detail: @Composable (S) -> Unit,
    list: @Composable () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth < minWidth) {
            PushContent(target, depth) { if (isOpen(it)) detail(it) else list() }
            return@BoxWithConstraints
        }
        val open = isOpen(target)
        // Half the window, up to a little more than a phone: the opened screens were laid out for a phone.
        val pane = (maxWidth / 2).coerceAtMost(480.dp)
        Row(Modifier.fillMaxSize()) {
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .let { if (!open && closedMaxWidth.isSpecified) it.wrapContentWidth().widthIn(max = closedMaxWidth) else it },
            ) { list() }
            AnimatedVisibility(open) {
                Box(Modifier.width(pane).fillMaxHeight()) {
                    PushContent(target, depth) { if (isOpen(it)) detail(it) }
                }
            }
        }
    }
}
