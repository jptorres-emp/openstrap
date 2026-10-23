package org.openstrap.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** One destination in [FloatingNavBar]; [tint] colours its capsule while it's the tab showing (white on Today). */
data class NavItem(val label: String, val icon: ImageVector, val selectedIcon: ImageVector = icon, val tint: Color = Color.White)

/**
 * The floating tab bar in black glass (spec 3.2): a pill of tabs, the content scrolling under it (marked with
 * `hazeSource` on [glass]) showing through blurred and darkened, and a circle detached on its right holding the
 * moment's [action]. The capsule behind the tab showing slides to it and takes that section's colour.
 */
@Composable
fun FloatingNavBar(
    items: List<NavItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, glass: HazeState,
    action: @Composable () -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        Surface(
            shape = CircleShape,
            color = Color.Transparent,
            border = GlassEdge,
            // No shadow: it would show through the glass as a dark smudge.
            modifier = Modifier.weight(1f, fill = false).widthIn(max = 440.dp).fillMaxWidth()
                .clip(CircleShape).hazeBlur(HazeInput.Sources(glass), GlassBlur),
        ) {
            // Measured width, so the pill knows how wide a cell is (BoxWithConstraints can't size to its row).
            var barWidth by remember { mutableIntStateOf(0) }
            val density = androidx.compose.ui.platform.LocalDensity.current
            Box(
                Modifier.padding(6.dp).height(androidx.compose.foundation.layout.IntrinsicSize.Min)
                    .onSizeChanged { barWidth = it.width },
            ) {
                // One capsule that slides to the selected tab, rather than each cell lighting up on its own.
                val gap = 2.dp
                val cell = (with(density) { barWidth.toDp() } - gap * (items.size - 1)) / items.size
                val x by androidx.compose.animation.core.animateDpAsState(
                    (cell + gap) * selected, tween(Motion.SWIFT, easing = Motion.easing), label = "nav pill",
                )
                Box(
                    Modifier.offset(x = x).width(cell).fillMaxHeight().clip(CircleShape)
                        .background(capsuleColor(items.getOrNull(selected)?.tint)).border(GlassChipEdge, CircleShape),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    items.forEachIndexed { i, item ->
                        NavCell(item, i == selected, modifier = Modifier.clip(CircleShape).clickable { onSelect(i) }
                            .weight(1f)
                            .padding(vertical = 8.dp))
                    }
                }
            }
        }
        action()
    }
}

/**
 * [FloatingNavBar] standing up along the left edge, for windows wider than tall (a phone on its side, an
 * unfolded foldable turned sideways): the same glass pill and sliding capsule, the [action] circle under it, giving
 * the height back to the pages.
 */
@Composable
fun FloatingNavRail(
    items: List<NavItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, glass: HazeState,
    action: @Composable () -> Unit = {},
) {
    Column(
        modifier.fillMaxHeight().systemBarsPadding().displayCutoutPadding().padding(horizontal = 10.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = RoundedCornerShape(40.dp),
            color = Color.Transparent,
            border = GlassEdge,
            modifier = Modifier.width(NavRailWidth - 20.dp).heightIn(max = 380.dp).weight(1f, fill = false).fillMaxHeight()
                .clip(RoundedCornerShape(40.dp)).hazeBlur(HazeInput.Sources(glass), GlassBlur),
        ) {
            var barHeight by remember { mutableIntStateOf(0) }
            val density = androidx.compose.ui.platform.LocalDensity.current
            Box(Modifier.padding(6.dp).onSizeChanged { barHeight = it.height }) {
                val gap = 2.dp
                val cell = (with(density) { barHeight.toDp() } - gap * (items.size - 1)) / items.size
                val y by androidx.compose.animation.core.animateDpAsState(
                    (cell + gap) * selected, tween(Motion.SWIFT, easing = Motion.easing), label = "nav pill",
                )
                Box(
                    Modifier.offset(y = y).height(cell).fillMaxWidth().clip(RoundedCornerShape(24.dp))
                        .background(capsuleColor(items.getOrNull(selected)?.tint)).border(GlassChipEdge, RoundedCornerShape(24.dp)),
                )
                Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                    items.forEachIndexed { i, item ->
                        NavCell(
                            item, i == selected,
                            modifier = Modifier.clip(RoundedCornerShape(24.dp)).clickable { onSelect(i) }.weight(1f).fillMaxWidth(),
                            verticalArrangement = Arrangement.Center,
                        )
                    }
                }
            }
        }
        action()
    }
}

/** The capsule under the tab showing: its section colour, eased as it slides; plain glass white on Today. */
@Composable
private fun capsuleColor(tint: Color?): Color {
    val target = if (tint == null || tint == Color.White) GlassChip else tint.copy(alpha = 0.22f)
    val eased by androidx.compose.animation.animateColorAsState(target, tween(Motion.SWIFT), label = "nav capsule")
    return eased
}

/**
 * The circle beside the tab bar (spec 3.2), in the same black glass. [filled] makes it white, for an action to take
 * now (Start workout); [content] is its icon or the live heart rate.
 */
@Composable
fun NavActionCircle(
    label: String, onClick: () -> Unit, glass: HazeState, filled: Boolean = false, content: @Composable () -> Unit,
) {
    Box(
        Modifier.size(58.dp).clip(CircleShape)
            .let { if (filled) it.background(Color.White) else it.hazeBlur(HazeInput.Sources(glass), GlassBlur).border(GlassEdge, CircleShape) }
            .clickable(onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** How much of the window's width [FloatingNavRail] takes, its margins included. */
val NavRailWidth = 92.dp

/** One destination's icon and label, tinted for whether it's [on]. */
@Composable
private fun NavCell(item: NavItem, on: Boolean, modifier: Modifier, verticalArrangement: Arrangement.Vertical = Arrangement.Top) {
    Column(modifier, verticalArrangement = verticalArrangement, horizontalAlignment = Alignment.CenterHorizontally) {
        val tint by androidx.compose.animation.animateColorAsState(
            if (on) Color.White else Color.White.copy(alpha = 0.55f),
            tween(Motion.QUICK), label = "nav tint",
        )
        val iconScale by animateFloatAsState(if (on) 1.08f else 1f, tween(Motion.SWIFT, easing = Motion.easing), label = "nav icon")
        Icon(
            if (on) item.selectedIcon else item.icon, item.label, tint = tint,
            modifier = Modifier.size(22.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale },
        )
        Text(item.label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
    }
}

/* Black glass: what passes under is blurred and darkened until only its colour shows; a light catching the top edge, shade along the bottom. */
private val GlassBlur = HazeBlurStyle {
    blurRadius(24.dp)
    backgroundColor(Color.Black)
    colorEffects(listOf(HazeColorEffect.tint(Color(0xFF0B0B0D).copy(alpha = 0.7f))))
    // Without blur (before Android 12), a near-opaque tint instead.
    fallbackColorEffect(HazeColorEffect.tint(Color(0xFF0B0B0D).copy(alpha = 0.92f)))
}
private val GlassChip = Color.White.copy(alpha = 0.1f)
private val GlassChipEdge = BorderStroke(
    1.dp, Brush.verticalGradient(0f to Color.White.copy(alpha = 0.16f), 1f to Color.Transparent),
)
