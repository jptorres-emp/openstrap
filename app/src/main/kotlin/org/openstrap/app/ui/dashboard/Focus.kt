package org.openstrap.app.ui.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/** A chart another screen can jump to. */
enum class FocusTarget { HeartRate, RestingHr, Hrv, Spo2, Stress, SkinTemperature, Breathing }

/** One request to show [target]; [id] makes a repeated tap on the same row run again. */
data class FocusRequest(val target: FocusTarget, val id: Long = System.nanoTime())

/** Records each item's position as the list is declared, so a focus target can be scrolled to by index. */
class SectionIndex {
    private val positions = mutableMapOf<FocusTarget, Int>()
    private var count = 0

    fun reset() { positions.clear(); count = 0 }
    fun next(target: FocusTarget?): Int = count.also { if (target != null) positions[target] = it; count++ }
    operator fun get(target: FocusTarget): Int? = positions[target]
}

/** An item that can be a focus target. Every item on a focusable screen goes through here so indices line up. */
fun LazyListScope.section(index: SectionIndex, target: FocusTarget? = null, content: @Composable LazyItemScope.() -> Unit) {
    index.next(target)
    item(key = target?.name, content = content)
}

/** What a focused card needs: whether to light up, and a 0..1 reveal for its chart. */
class FocusState {
    var highlighted by mutableStateOf<FocusTarget?>(null)
        internal set
    internal val reveal = Animatable(1f)
    internal var revealing by mutableStateOf<FocusTarget?>(null)

    fun isHighlighted(t: FocusTarget) = highlighted == t
    fun reveal(t: FocusTarget): Float = if (revealing == t) reveal.value else 1f
}

/**
 * Scrolls [list] to [request]'s card, then lights it up and replays its chart. [onDone] clears the request
 * so returning to the tab later doesn't jump again.
 */
@Composable
fun rememberFocus(request: FocusRequest?, list: LazyListState, index: SectionIndex, onDone: () -> Unit): FocusState {
    val state = remember { FocusState() }
    LaunchedEffect(request) {
        val target = request?.target ?: return@LaunchedEffect
        snapshotFlow { list.layoutInfo.totalItemsCount }.first { it > 0 }
        val i = index[target] ?: run { onDone(); return@LaunchedEffect }
        list.animateScrollToItem(i)
        state.revealing = target
        state.highlighted = target
        state.reveal.snapTo(0f)
        state.reveal.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
        delay(700)
        state.highlighted = null
        state.revealing = null
        onDone()
    }
    return state
}
