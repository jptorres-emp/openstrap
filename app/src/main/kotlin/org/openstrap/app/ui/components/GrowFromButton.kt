package org.openstrap.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/*
 * Avatar to You, strap chip to Strap (spec 3.5): the page over the tab grows out of the round button tapped, and
 * closing it shrinks it back in. The same springs as card to detail, 500 in and 300 out, with no bounce.
 */

/**
 * The header button a page over the tab was last opened from, in the window's coordinates. A button records itself
 * as it's tapped ([growClick]); [take] hands it to the page opening and clears it, so a page opened any other way
 * (Profile from a card, the Strap page from the pairing card) has no button to grow from and fades in.
 */
class GrowOrigin {
    private var tapped: Rect? = null

    internal fun tap(bounds: Rect?) { tapped = bounds }

    fun take(): Rect? = tapped.also { tapped = null }
}

val LocalGrowOrigin = staticCompositionLocalOf<GrowOrigin?> { null }

/** A button's [onClick] that first records where the button is, for the page it opens to grow from; and its modifier. */
class GrowClick internal constructor(val modifier: Modifier, val onClick: () -> Unit)

@Composable
fun growClick(onClick: () -> Unit): GrowClick {
    val origin = LocalGrowOrigin.current
    val click by rememberUpdatedState(onClick)
    val bounds = remember { arrayOfNulls<Rect>(1) }
    return remember(origin) {
        GrowClick(Modifier.onGloballyPositioned { bounds[0] = it.boundsInRoot() }) { origin?.tap(bounds[0]); click() }
    }
}

/** How far a page has grown out of its button: its clip at [progress], from the button's [from] to [page]. */
object GrowMotion {
    /** By this much of the way the page is fully opaque: it fades in over the start of its growth only. */
    const val FADE_PART = 0.25f

    /** The page's outline at [progress] (0 the button, 1 the page): the rect between them, round as the button at the start. */
    fun outline(from: Rect, page: Rect, progress: Float): RoundRect {
        val p = progress.coerceIn(0f, 1f)
        val r = lerp(from, page, p)
        val radius = from.minDimension / 2f * (1f - p)
        return RoundRect(r, CornerRadius(radius))
    }

    fun alpha(progress: Float): Float = (progress / FADE_PART).coerceIn(0f, 1f)

    /** Spring stiffness opening (500) and closing (300), as card to detail. */
    fun stiffness(opening: Boolean): Float = if (opening) CardMotion.STIFFNESS_IN else CardMotion.STIFFNESS_OUT
}

/**
 * [content] while [visible], growing out of [from] (a button's bounds in the window) as it turns visible and shrinking
 * back into it as it turns hidden; with no [from] it fades. [content] stays composed until it has shrunk away, told
 * it's closing meanwhile (so the caller can keep back away from a page on its way out). With the system's animations
 * off it jumps.
 */
@Composable
fun GrowFromButton(visible: Boolean, from: Rect?, modifier: Modifier = Modifier, content: @Composable (closing: Boolean) -> Unit) {
    val animate = rememberAnimationsOn()
    val progress = remember { Animatable(if (visible) 1f else 0f) }
    LaunchedEffect(visible, animate) {
        val target = if (visible) 1f else 0f
        if (!animate) progress.snapTo(target)
        else progress.animateTo(target, spring(Spring.DampingRatioNoBouncy, GrowMotion.stiffness(visible)))
    }
    val shrunk by remember { derivedStateOf { progress.value == 0f && !progress.isRunning } }
    if (!visible && shrunk) return
    val self = remember { arrayOf(Rect.Zero) }
    val origin by rememberUpdatedState(from)
    Box(
        modifier.onGloballyPositioned { self[0] = it.boundsInRoot() }.graphicsLayer {
            val p = progress.value
            alpha = GrowMotion.alpha(p)
            val start = origin?.translate(-self[0].topLeft)
            if (start != null && p < 1f) {
                shape = GrowShape(GrowMotion.outline(start, Rect(Offset.Zero, size), p))
                clip = true
            }
        },
    ) { content(!visible) }
}

/** A clip of exactly [outline], wherever the layer is. */
private class GrowShape(private val outline: RoundRect) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline = Outline.Rounded(outline)
}
