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
import org.openstrap.app.ui.theme.AppIcons

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
            Icon(AppIcons.Warning, null, tint = MetricColors.warn, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(title, color = Color.White, style = GlowType.title, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
        if (chevron) {
            Spacer(Modifier.width(4.dp))
            Icon(AppIcons.CaretRight, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
        }
        if (expanded != null) {
            // In a glass circle, like every other control inside a dotted panel.
            val turn by animateFloatAsState(if (expanded) 180f else 0f, label = "dot header caret")
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(32.dp).glass(CircleShape), contentAlignment = Alignment.Center) {
                Icon(AppIcons.CaretDown, if (expanded) "Hide" else "Show", tint = Color.White, modifier = Modifier.size(16.dp).rotate(turn))
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

internal val TrendGood = Color(0xFF3DDC97)
internal val TrendBad = Color(0xFFFFB547)
