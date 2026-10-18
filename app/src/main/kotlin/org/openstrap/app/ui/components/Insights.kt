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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.text.drawText
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import org.openstrap.app.ui.theme.PhosphorIcons

/**
 * Oura-style hero: a wide, shallow arc over the score with a glowing knob where the score lands, then
 * the label, the number, a headline and a sentence that explains it.
 */
@Composable
fun HeroPanel(
    label: String,
    value: String,
    progress: Float?,
    accent: Color,
    headline: String,
    body: String?,
    modifier: Modifier = Modifier,
    unit: String = "",
    onClick: (() -> Unit)? = null,
    /** Colours the gauge fill runs through from 0 to 100 (Charge); null fills it in [accent]. */
    gaugeGradient: List<Color>? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth().let { if (onClick != null) it.pressClickable(onClick, PanelShape) else it },
        shape = PanelShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HorizonGauge(progress, accent, Modifier.fillMaxWidth().height(78.dp), gaugeGradient)
            // "READINESS ›": the chevron says the card opens the details.
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(label)
                if (onClick != null) {
                    Spacer(Modifier.width(2.dp))
                    Icon(PhosphorIcons.CaretRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.displayLarge)
                if (unit.isNotEmpty()) {
                    Text(
                        unit, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
                    )
                }
            }
            Text(headline, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            if (body != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * A shallow arc across the card, like Oura's: a thin faint track with dot ticks above it, the filled
 * part in [color] with a soft glow, a knob at the score and "0" / "100" at the ends. The fill sweeps
 * in when the card appears; [progress] null draws only the track.
 */
@Composable
fun HorizonGauge(progress: Float?, color: Color, modifier: Modifier = Modifier, gradient: List<Color>? = null) {
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val tick = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val knobCore = MaterialTheme.colorScheme.onSurface
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val endStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val target = progress?.coerceIn(0f, 1f) ?: 0f
    val anim = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(target) { anim.animateTo(target, androidx.compose.animation.core.tween(600, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }

    Canvas(modifier) {
        val sw = 4.dp.toPx()
        val sweep = 84f
        val start = 270f - sweep / 2
        // A circle wide enough that an 84° slice spans the card, centred well below it.
        val chord = size.width - 28.dp.toPx()
        val r = chord / 2f / sin(Math.toRadians(sweep / 2.0)).toFloat()
        val sag = r * (1f - cos(Math.toRadians(sweep / 2.0)).toFloat())
        val top = 14.dp.toPx()
        val c = Offset(size.width / 2f, top + r)
        val arcTopLeft = Offset(c.x - r, c.y - r)
        val arcSize = Size(r * 2, r * 2)
        fun at(fraction: Float, radius: Float): Offset {
            val a = Math.toRadians((start + sweep * fraction).toDouble())
            return Offset(c.x + radius * cos(a).toFloat(), c.y + radius * sin(a).toFloat())
        }

        drawArc(track, start, sweep, false, arcTopLeft, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
        for (i in 0..4) drawCircle(tick, 1.6.dp.toPx(), at(i / 4f, r + 9.dp.toPx()))

        if (progress != null) {
            val p = anim.value
            if (p > 0.001f) {
                // Glow first, then the line on top.
                drawArc(color.copy(alpha = 0.18f), start, sweep * p, false, arcTopLeft, arcSize, style = Stroke(sw * 3.5f, cap = StrokeCap.Round))
                // A gradient runs across the whole arc, so the fill shows how far along the scale it got.
                val fill = if (gradient != null) Brush.horizontalGradient(gradient, startX = c.x - chord / 2, endX = c.x + chord / 2)
                else Brush.horizontalGradient(listOf(color.copy(alpha = 0.45f), color), startX = c.x - chord / 2, endX = at(p, r).x.coerceAtLeast(c.x - chord / 2 + 1f))
                drawArc(
                    fill,
                    start, sweep * p, false, arcTopLeft, arcSize, style = Stroke(sw, cap = StrokeCap.Round),
                )
            }
            val k = at(p, r)
            drawCircle(color.copy(alpha = 0.25f), 11.dp.toPx(), k)
            drawCircle(color, 7.dp.toPx(), k)
            drawCircle(knobCore, 3.dp.toPx(), k)
        }

        val zero = measurer.measure("0", endStyle)
        val hundred = measurer.measure("100", endStyle)
        val left = at(0f, r)
        val right = at(1f, r)
        val labelY = top + sag + 8.dp.toPx()
        drawText(zero, topLeft = Offset(left.x - zero.size.width / 2f, labelY))
        drawText(hundred, topLeft = Offset(right.x - hundred.size.width / 2f, labelY))
    }
}

/**
 * Oura's top row: a soft disc with a small icon and the value, the label underneath. No coloured
 * ring; the icon carries the metric's colour.
 */
@Composable
fun ScoreChip(
    value: String?,
    label: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    /** How full the ring is, 0..1 (WHOOP/Bevel-style); null leaves just the faint track. */
    progress: Float? = null,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier.let { if (onClick != null) it.pressClickable(onClick, RoundedCornerShape(16.dp), scale = 0.93f) else it }.padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            val shown by animateFloatAsState(progress ?: 0f, label = "$label ring")
            ProgressRing(shown, if (value != null) accent else MaterialTheme.colorScheme.onSurfaceVariant, size = 64.dp, stroke = 5.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Icon(icon, null, tint = if (value != null) accent else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Text(
                    value ?: "—", style = MaterialTheme.typography.titleMedium,
                    color = if (value != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/**
 * WHOOP's "key statistics" row: icon and label on the left, today's value on the right with the
 * baseline underneath and an arrow showing which way today differs from it.
 */
@Composable
fun KeyStatRow(
    label: String,
    icon: ImageVector,
    accent: Color,
    value: String?,
    unit: String,
    baseline: String?,
    /** +1 above baseline, -1 below, 0 or null when about the same or unknown. */
    direction: Int?,
    /** Whether going up is good (HRV) or bad (resting HR); colours the arrow. */
    higherIsBetter: Boolean?,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value ?: "—", style = MaterialTheme.typography.titleLarge)
                if (value != null && unit.isNotEmpty()) {
                    Text(unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 3.dp, bottom = 2.dp))
                }
                if (value != null && direction != null && direction != 0) {
                    val good = higherIsBetter?.let { (direction > 0) == it }
                    val tint = when (good) { true -> TrendGood; false -> TrendBad; null -> MaterialTheme.colorScheme.onSurfaceVariant }
                    Icon(if (direction > 0) PhosphorIcons.CaretUpFill else PhosphorIcons.CaretDownFill, null, tint = tint, modifier = Modifier.size(20.dp))
                }
            }
            if (baseline != null) {
                Text(baseline, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (onClick != null) {
            Spacer(Modifier.width(4.dp))
            Icon(PhosphorIcons.CaretRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

private val TrendGood = Color(0xFF3DDC97)
private val TrendBad = Color(0xFFFFB547)

/** Oura/Bevel sleep contributor: label, rating word on the right, a thin bar underneath. */
@Composable
fun ContributorRow(label: String, rating: String, fraction: Float, color: Color, detail: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // The detail gets its own line: inline, a long one squeezed the label into a word per line.
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                Text(rating, style = MaterialTheme.typography.titleSmall, color = color)
            }
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0.02f, 1f)).height(5.dp).clip(CircleShape).background(color))
        }
    }
}

/** One destination in [FloatingNavBar]. */
data class NavItem(val label: String, val icon: ImageVector, val selectedIcon: ImageVector = icon)

/**
 * Oura's floating pill tab bar: sits above the content with a hairline border. With [glass] it's black
 * glass for the glow language: the content scrolling under it (marked with `hazeSource` on [glass])
 * shows through blurred and darkened, and the edge catches light along the top.
 */
@Composable
fun FloatingNavBar(items: List<NavItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, glass: HazeState? = null) {
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        Surface(
            shape = CircleShape,
            color = if (glass != null) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
            border = if (glass != null) GlassEdge else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            // A shadow would show through the glass as a dark smudge.
            shadowElevation = if (glass != null) 0.dp else 12.dp,
            modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth()
                .let { if (glass != null) it.clip(CircleShape).hazeBlur(HazeInput.Sources(glass), GlassBlur) else it },
        ) {
            // Measured width, so the pill knows how wide a cell is (BoxWithConstraints can't size to its row).
            var barWidth by remember { mutableIntStateOf(0) }
            val density = androidx.compose.ui.platform.LocalDensity.current
            Box(
                Modifier.padding(6.dp).height(androidx.compose.foundation.layout.IntrinsicSize.Min)
                    .onSizeChanged { barWidth = it.width },
            ) {
                // One pill that slides to the selected tab, rather than each cell lighting up on its own.
                val gap = 2.dp
                val cell = (with(density) { barWidth.toDp() } - gap * (items.size - 1)) / items.size
                val x by androidx.compose.animation.core.animateDpAsState(
                    (cell + gap) * selected, tween(Motion.SWIFT, easing = Motion.easing), label = "nav pill",
                )
                Box(
                    Modifier.offset(x = x).width(cell).fillMaxHeight().clip(CircleShape)
                        .let { if (glass != null) it.background(GlassChip).border(GlassChipEdge, CircleShape) else it.background(MaterialTheme.colorScheme.surfaceContainerHighest) },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    items.forEachIndexed { i, item ->
                        NavCell(item, i == selected, glass, Modifier.clip(CircleShape).clickable { onSelect(i) }
                            // Every cell gets an equal share of the bar, so labels like "Workouts" have room.
                            .weight(1f)
                            .padding(vertical = 8.dp))
                    }
                }
            }
        }
    }
}

/**
 * [FloatingNavBar] standing up along the left edge, for windows wider than tall (a phone on its side, an
 * unfolded foldable turned sideways): the same glass pill and sliding highlight, giving the height back to the pages.
 */
@Composable
fun FloatingNavRail(items: List<NavItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, glass: HazeState? = null) {
    Box(
        modifier.fillMaxHeight().systemBarsPadding().displayCutoutPadding().padding(horizontal = 10.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(40.dp),
            color = if (glass != null) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
            border = if (glass != null) GlassEdge else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shadowElevation = if (glass != null) 0.dp else 12.dp,
            modifier = Modifier.width(NavRailWidth - 20.dp).heightIn(max = 440.dp).fillMaxHeight()
                .let { if (glass != null) it.clip(RoundedCornerShape(40.dp)).hazeBlur(HazeInput.Sources(glass), GlassBlur) else it },
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
                        .let { if (glass != null) it.background(GlassChip).border(GlassChipEdge, RoundedCornerShape(24.dp)) else it.background(MaterialTheme.colorScheme.surfaceContainerHighest) },
                )
                Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                    items.forEachIndexed { i, item ->
                        NavCell(
                            item, i == selected, glass,
                            Modifier.clip(RoundedCornerShape(24.dp)).clickable { onSelect(i) }.weight(1f).fillMaxWidth(),
                            verticalArrangement = Arrangement.Center,
                        )
                    }
                }
            }
        }
    }
}

/** How much of the window's width [FloatingNavRail] takes, its margins included. */
val NavRailWidth = 92.dp

/** One destination's icon and label, tinted for whether it's [on]. */
@Composable
private fun NavCell(item: NavItem, on: Boolean, glass: HazeState?, modifier: Modifier, verticalArrangement: Arrangement.Vertical = Arrangement.Top) {
    Column(modifier, verticalArrangement = verticalArrangement, horizontalAlignment = Alignment.CenterHorizontally) {
        val tint by androidx.compose.animation.animateColorAsState(
            when {
                glass != null -> if (on) Color.White else Color.White.copy(alpha = 0.55f)
                else -> if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            },
            tween(Motion.QUICK), label = "nav tint",
        )
        val iconScale by animateFloatAsState(if (on) 1.08f else 1f, tween(Motion.SWIFT, easing = Motion.easing), label = "nav icon")
        Icon(
            if (on) item.selectedIcon else item.icon, item.label, tint = tint,
            modifier = Modifier.size(24.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale },
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
