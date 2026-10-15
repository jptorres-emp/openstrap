package org.openstrap.app.ui.components

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.drawText
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

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
) {
    Surface(
        modifier = modifier.fillMaxWidth().clip(PanelShape).let { if (onClick != null) it.clickable(onClick = onClick) else it },
        shape = PanelShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HorizonGauge(progress, accent, Modifier.fillMaxWidth().height(78.dp))
            SectionLabel(label)
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
fun HorizonGauge(progress: Float?, color: Color, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val tick = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val knobCore = MaterialTheme.colorScheme.onSurface
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val endStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val target = progress?.coerceIn(0f, 1f) ?: 0f
    val anim = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(target) { anim.animateTo(target, androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }

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
                drawArc(
                    Brush.horizontalGradient(listOf(color.copy(alpha = 0.45f), color), startX = c.x - chord / 2, endX = at(p, r).x.coerceAtLeast(c.x - chord / 2 + 1f)),
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
fun ScoreChip(value: String?, label: String, icon: ImageVector, accent: Color, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.size(60.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Icon(icon, null, tint = if (value != null) accent else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
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
        Box(Modifier.size(34.dp).clip(CircleShape).background(accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp))
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
                    Icon(if (direction > 0) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown, null, tint = tint, modifier = Modifier.size(20.dp))
                }
            }
            if (baseline != null) {
                Text(baseline, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (onClick != null) {
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

private val TrendGood = Color(0xFF3DDC97)
private val TrendBad = Color(0xFFFFB547)

/** Oura/Bevel sleep contributor: label, rating word on the right, a thin bar underneath. */
@Composable
fun ContributorRow(label: String, rating: String, fraction: Float, color: Color, detail: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(10.dp))
            }
            Text(rating, style = MaterialTheme.typography.titleSmall, color = color)
        }
        Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0.02f, 1f)).height(5.dp).clip(CircleShape).background(color))
        }
    }
}

/** One destination in [FloatingNavBar]. */
data class NavItem(val label: String, val icon: ImageVector)

/** Oura's floating pill tab bar: sits above the content with a hairline border. */
@Composable
fun FloatingNavBar(items: List<NavItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shadowElevation = 12.dp,
        ) {
            Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                items.forEachIndexed { i, item ->
                    val on = i == selected
                    Column(
                        Modifier.clip(CircleShape)
                            .background(if (on) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent)
                            .clickable { onSelect(i) }
                            .padding(horizontal = 18.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val tint = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        Icon(item.icon, item.label, tint = tint, modifier = Modifier.size(22.dp))
                        Text(item.label, style = MaterialTheme.typography.labelSmall, color = tint)
                    }
                }
            }
        }
    }
}
