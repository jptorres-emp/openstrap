package org.openstrap.app.ui.glow

import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.border
import org.openstrap.app.ui.theme.MetricColors
import androidx.compose.ui.draw.rotate
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import org.openstrap.app.ui.components.pressClickable
import org.openstrap.app.ui.theme.PhosphorIcons

/*
 * The quiet half of the glow language: plain black panels drawn with dots. Use these for lists and
 * secondary charts so the few glow cards on a screen stay special.
 */

private val PanelShape = RoundedCornerShape(28.dp)

/** A black panel outlined in a row of faint dots instead of a border. */
@Composable
fun DotPanel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.pressClickable(onClick, PanelShape) else it }
            .drawBehind {
                val inset = 1.dp.toPx()
                // A clear step above the charts' dotted gridlines (16%), still well under the text.
                drawRoundRect(
                    Color.White.copy(alpha = 0.3f),
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2, size.height - inset * 2),
                    cornerRadius = CornerRadius(28.dp.toPx() - inset),
                    style = Stroke(
                        width = 1.6.dp.toPx(), cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(0f, 7.dp.toPx())),
                    ),
                )
            }
            .padding(LocalCardPadding.current),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

/**
 * The title row of a dotted panel: the title, an optional note on the right and an optional chevron. No
 * leading mark: these panels are the quiet layer, and colour belongs to the data inside them. [warning]
 * puts an amber warning icon in front, for panels that flag a problem. [expanded], when set, shows a caret
 * that turns as a folding panel opens.
 */
@Composable
fun DotHeader(title: String, trailing: String? = null, chevron: Boolean = false, warning: Boolean = false, expanded: Boolean? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (warning) {
            Icon(PhosphorIcons.Warning, null, tint = MetricColors.warn, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(title, color = Color.White, style = GlowType.title, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
        if (chevron) {
            Spacer(Modifier.width(4.dp))
            Icon(PhosphorIcons.CaretRight, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
        }
        if (expanded != null) {
            // In a glass circle, like every other control inside a dotted panel.
            val turn by animateFloatAsState(if (expanded) 180f else 0f, label = "dot header caret")
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(32.dp).glass(CircleShape), contentAlignment = Alignment.Center) {
                Icon(PhosphorIcons.CaretDown, if (expanded) "Hide" else "Show", tint = Color.White, modifier = Modifier.size(16.dp).rotate(turn))
            }
        }
    }
}

/**
 * The glass surface every control inside a dotted panel wears (rows, pills, buttons): a faint white fill
 * and hairline, so it reads as something to press against the panel's bare black. With [onClick] it
 * presses like the rest of the glow controls.
 */
fun Modifier.glass(shape: Shape, onClick: (() -> Unit)? = null, scale: Float = 0.98f): Modifier =
    background(Color.White.copy(alpha = 0.08f), shape)
        .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
        .let { if (onClick != null) it.pressClickable(onClick, shape, scale) else it }

/** The shape of a glass row inside a dotted panel. */
val GlassRowShape = RoundedCornerShape(16.dp)

/** A dotted rule between rows. */
@Composable
fun DotDivider(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(2.dp)) {
        drawLine(
            Color.White.copy(alpha = 0.16f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2),
            strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(0f, 6.dp.toPx())),
        )
    }
}

/** A small arrow made of five dots, pointing up ([up]) or down. */
@Composable
fun DotArrow(up: Boolean, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(11.dp, 9.dp)) {
        val r = 1.3.dp.toPx()
        val sx = size.width / 4
        val sy = size.height / 2
        // A chevron: tip in the middle, two dots each side stepping away from it.
        val rows = if (up) listOf(0f, 1f, 2f) else listOf(2f, 1f, 0f)
        listOf(-2 to rows[2], -1 to rows[1], 0 to rows[0], 1 to rows[1], 2 to rows[2]).forEach { (dx, row) ->
            drawCircle(color, r, Offset(size.width / 2 + dx * sx * 0.95f, r + row * (sy - r)))
        }
    }
}

/** Three dots in a straight line, the [DotArrow] for "no change". Same footprint, so rows line up. */
@Composable
fun DotLevel(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(11.dp, 9.dp)) {
        val r = 1.3.dp.toPx()
        val sx = size.width / 4
        listOf(-1, 0, 1).forEach { dx ->
            drawCircle(color, r, Offset(size.width / 2 + dx * sx * 1.4f, size.height / 2))
        }
    }
}

private val TileShape = RoundedCornerShape(18.dp)

/**
 * A tappable tile for a grid of statistics: a soft raised surface, the metric's dot and label, and a chevron
 * that says it opens the metric's detail. [content] sits underneath.
 */
@Composable
fun GlowTile(label: String, accent: Color, onClick: (() -> Unit)?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .background(Color.White.copy(alpha = 0.05f), TileShape)
            .let { if (onClick != null) it.pressClickable(onClick, TileShape, scale = 0.97f) else it }
            .drawBehind {
                drawRoundRect(
                    Color.White.copy(alpha = 0.08f), cornerRadius = CornerRadius(18.dp.toPx()),
                    style = Stroke(1.dp.toPx()),
                )
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(accent, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(label, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall, maxLines = 1, modifier = Modifier.weight(1f))
            if (onClick != null) Icon(PhosphorIcons.CaretRight, null, tint = Color.White.copy(alpha = 0.55f), modifier = Modifier.size(14.dp))
        }
        content()
    }
}

/** A small rounded tag for a status word ("Normal", "Higher"), tinted in [color]. */
@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text, color = color, style = MaterialTheme.typography.labelSmall,
        modifier = modifier.background(color.copy(alpha = 0.14f), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Two tiles a row, evenly spaced; an odd last tile keeps its half width. */
@Composable
fun <T> TileGrid(items: List<T>, tile: @Composable (T, Modifier) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Max)) {
                pair.forEach { tile(it, Modifier.weight(1f).fillMaxHeight()) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * One key statistic as a grid tile: the value with its trend arrow, and its usual value underneath.
 */
@Composable
fun DotStatTile(
    label: String,
    accent: Color,
    value: String?,
    unit: String,
    baseline: String?,
    /** +1 above baseline, -1 below, 0 or null when about the same or unknown. */
    direction: Int?,
    /** Whether going up is good; colours the arrow. */
    higherIsBetter: Boolean?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    GlowTile(label, accent, onClick, modifier) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(value ?: "—", color = Color.White, style = MaterialTheme.typography.titleLarge)
            if (value != null && unit.isNotEmpty()) {
                Text(unit, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 3.dp))
            }
            if (value != null && direction != null && direction != 0) {
                val good = higherIsBetter?.let { (direction > 0) == it }
                val tint = when (good) { true -> TrendGood; false -> TrendBad; null -> Color.White.copy(alpha = 0.6f) }
                Spacer(Modifier.width(8.dp))
                DotArrow(direction > 0, tint)
            }
        }
        if (baseline != null) Text("usually $baseline", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
    }
}

internal val TrendGood = Color(0xFF3DDC97)
internal val TrendBad = Color(0xFFFFB547)
