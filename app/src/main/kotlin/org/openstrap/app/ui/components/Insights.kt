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
 * Oura-style hero: an open arc gauge with tick marks, the score in the middle, then a headline and a
 * sentence that explains it. The card glows faintly in the metric's colour (Ultrahuman).
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
        Box(Modifier.background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.16f), Color.Transparent)))) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    ArcGauge(progress, accent, Modifier.size(width = 240.dp, height = 168.dp))
                    Column(
                        Modifier.padding(top = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(value, style = MaterialTheme.typography.displayLarge)
                            if (unit.isNotEmpty()) {
                                Text(
                                    unit, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
                                )
                            }
                        }
                        SectionLabel(label)
                    }
                }
                Spacer(Modifier.height(4.dp))
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
}

/** A 220° arc with tick marks; [progress] null draws only the track. */
@Composable
fun ArcGauge(progress: Float?, color: Color, modifier: Modifier = Modifier, stroke: Dp = 10.dp) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val tick = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    Canvas(modifier) {
        val sw = stroke.toPx()
        // The arc dips sin(20°) of its radius below the centre; size the radius so it fits the canvas.
        val r0 = minOf((size.width - sw * 4) / 2f, (size.height - sw * 3) / (1f + sin(Math.toRadians(20.0)).toFloat()))
        val diameter = r0 * 2
        val topLeft = Offset((size.width - diameter) / 2f, sw * 1.6f)
        val arcSize = Size(diameter, diameter)
        val start = 160f
        val sweep = 220f
        drawArc(track, start, sweep, false, topLeft, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
        if (progress != null) {
            val p = progress.coerceIn(0f, 1f)
            drawArc(
                Brush.sweepGradient(listOf(color.copy(alpha = 0.55f), color), center = Offset(size.width / 2f, topLeft.y + diameter / 2f)),
                start, (sweep * p).coerceAtLeast(0.5f), false, topLeft, arcSize, style = Stroke(sw, cap = StrokeCap.Round),
            )
        }
        // Five ticks just outside the arc, like Oura's gauge.
        val c = Offset(size.width / 2f, topLeft.y + diameter / 2f)
        val r = diameter / 2f + sw * 1.3f
        for (i in 0..4) {
            val a = Math.toRadians((start + sweep * i / 4f).toDouble())
            drawCircle(tick, radius = 2.dp.toPx(), center = Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat()))
        }
    }
}

/** Oura's top row: a round chip with the value and a label underneath. */
@Composable
fun ScoreChip(value: String?, label: String, icon: ImageVector, accent: Color, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.size(58.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(1.5.dp, if (value != null) accent.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(13.dp))
                Text(value ?: "—", style = MaterialTheme.typography.titleMedium)
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
