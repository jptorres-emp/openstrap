package org.openstrap.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.openstrap.app.ui.theme.PhosphorIcons

val PanelShape = RoundedCornerShape(22.dp)

/** The base card: a slightly raised surface with a hairline border, as in WHOOP/Oura. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    /** When set, the border fades to this colour (used to point at a card after jumping to it). */
    highlight: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val border by androidx.compose.animation.animateColorAsState(
        highlight ?: MaterialTheme.colorScheme.outlineVariant, androidx.compose.animation.core.tween(450), label = "panel border",
    )
    Surface(
        modifier = modifier.fillMaxWidth().let { if (onClick != null) it.pressClickable(onClick, PanelShape) else it },
        shape = PanelShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(if (highlight != null) 1.5.dp else 1.dp, border),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

/** Small uppercase tracked label: "TIME ASLEEP". */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = color, modifier = modifier)
}

/** Label row with an optional leading accent icon and trailing text (e.g. "Last 7 days"). */
@Composable
fun PanelHeader(
    title: String,
    icon: ImageVector? = null,
    accent: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    trailing: String? = null,
    /** Shows a "›" at the end: the card opens something when tapped. */
    chevron: Boolean = false,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
        }
        SectionLabel(title, Modifier.weight(1f))
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (chevron) {
            Spacer(Modifier.width(4.dp))
            Icon(PhosphorIcons.CaretRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

/** Big number + unit, e.g. "62 bpm", with an optional caption underneath. */
@Composable
fun BigValue(
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    large: Boolean = false,
    captionColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Column(modifier) {
        if (caption != null) SectionLabel(caption, color = captionColor)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = if (large) MaterialTheme.typography.displayMedium else MaterialTheme.typography.displaySmall)
            if (unit.isNotEmpty()) {
                Spacer(Modifier.width(4.dp))
                Text(
                    unit,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = if (large) 8.dp else 4.dp),
                )
            }
        }
    }
}

/** Pill-shaped segmented control (D / W / M). */
@Composable
fun SegmentedTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val isSelected = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(if (isSelected) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent)
                    .border(if (isSelected) 1.dp else 0.dp, if (isSelected) MaterialTheme.colorScheme.outline else Color.Transparent, CircleShape)
                    .clickable { onSelect(i) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Settings-style row with a title, optional subtitle and trailing content. */
@Composable
fun SettingRow(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    titleStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = titleStyle)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

/** A colored dot + label used in chart legends. */
@Composable
fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun EmptyHint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return when {
        h > 0 && m == 0L -> "${h}h"
        h > 0 -> "${h}h ${m}m"
        else -> "${m}m"
    }
}
