package org.openstrap.app.ui.glow

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
                drawRoundRect(
                    Color.White.copy(alpha = 0.2f),
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2, size.height - inset * 2),
                    cornerRadius = CornerRadius(28.dp.toPx() - inset),
                    style = Stroke(
                        width = 1.6.dp.toPx(), cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(0f, 7.dp.toPx())),
                    ),
                )
            }
            .padding(GlowLayout.CardPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

/** Panel title: a small dot in the metric's colour, the title, an optional note and chevron. */
@Composable
fun DotHeader(title: String, accent: Color, trailing: String? = null, chevron: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).background(accent, CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(title, color = Color.White, style = GlowType.title, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
        if (chevron) {
            Spacer(Modifier.width(4.dp))
            Icon(PhosphorIcons.CaretRight, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
        }
    }
}

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

/**
 * A key-statistic row for a [DotPanel]: dot and label on the left; value, unit and a dot arrow on the
 * right, the 7-day baseline underneath.
 */
@Composable
fun DotStatRow(
    label: String,
    accent: Color,
    value: String?,
    unit: String,
    baseline: String?,
    /** +1 above baseline, -1 below, 0 or null when about the same or unknown. */
    direction: Int?,
    /** Whether going up is good; colours the arrow. */
    higherIsBetter: Boolean?,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth()
            .let { if (onClick != null) it.pressClickable(onClick, RoundedCornerShape(12.dp), scale = 0.98f) else it }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(accent, CircleShape))
        Spacer(Modifier.width(12.dp))
        Text(label, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (value != null && direction != null && direction != 0) {
                    val good = higherIsBetter?.let { (direction > 0) == it }
                    val tint = when (good) { true -> TrendGood; false -> TrendBad; null -> Color.White.copy(alpha = 0.6f) }
                    DotArrow(direction > 0, tint)
                    Spacer(Modifier.width(8.dp))
                }
                Text(value ?: "—", color = Color.White, style = MaterialTheme.typography.titleLarge)
                if (value != null && unit.isNotEmpty()) {
                    Text(unit, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 3.dp))
                }
            }
            if (baseline != null) Text("usually $baseline", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

internal val TrendGood = Color(0xFF3DDC97)
internal val TrendBad = Color(0xFFFFB547)
