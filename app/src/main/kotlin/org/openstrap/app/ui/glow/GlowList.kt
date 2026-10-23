package org.openstrap.app.ui.glow

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/*
 * The quiet tier of the glow language: a grouped list for small facts (body signals, time to recover, energy) and
 * for lists of things (workouts, contributors, settings). No glow at all, so the cards above it keep the light.
 */

private val ListShape = RoundedCornerShape(28.dp)
private val ListSurface = Color(0xFF0F0F11)
private val RowHairline = Color.White.copy(alpha = 0.05f)

/** Row values in dot-matrix digits. */
private val ValueDot = 2.4.dp
private val ValueGap = 0.9.dp

/**
 * A grouped list: a dark panel with a title row ([title] left, an optional [note] right) over its rows, usually
 * [ListRow]s. Without a title the rows start at the top.
 */
@Composable
fun ListPanel(
    modifier: Modifier = Modifier,
    title: String? = null,
    note: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(ListSurface, ListShape)
            .border(1.dp, Color.White.copy(alpha = 0.06f), ListShape)
            .padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 8.dp),
    ) {
        if (title != null || note != null) Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title.orEmpty(), color = Color.White, style = GlowType.label, modifier = Modifier.weight(1f))
            if (note != null) Text(note, color = Color.White.copy(alpha = GlowType.SECONDARY_ALPHA), style = GlowType.secondary)
        }
        content()
    }
}

/**
 * A row in a [ListPanel]: an 8 dp dot in the metric's [color], the [name] with its [status] under it, and the
 * [value] in dot-matrix digits in its own column on the right. Value and status take the metric colour only when
 * [offNormal]. A null [value] is a waiting row: a dim dash, and [status] says what it waits for ("After your first
 * night"). [onClick] opens the metric's detail. [divider] draws the hairline above the row.
 */
@Composable
fun ListRow(
    name: String,
    color: Color,
    value: String?,
    status: String?,
    modifier: Modifier = Modifier,
    offNormal: Boolean = false,
    onClick: (() -> Unit)? = null,
    divider: Boolean = true,
) {
    val waiting = value == null
    val accent = if (offNormal && !waiting) color else Color.White
    Row(
        modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .let { if (divider) it.drawBehind { drawLine(RowHairline, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) } else it }
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(name, color = Color.White, style = GlowType.label.copy(fontWeight = null))
            if (status != null) Text(
                status,
                color = when {
                    waiting -> Color.White.copy(alpha = 0.45f)
                    offNormal -> color
                    else -> Color.White.copy(alpha = 0.6f)
                },
                style = GlowType.secondary,
            )
        }
        Spacer(Modifier.width(12.dp))
        if (value != null) DotMatrixText(value, dot = ValueDot, gap = ValueGap, color = accent)
        else WaitingDash()
    }
}

/** The empty value: a short dim bar drawn by the UI, never a dash character. */
@Composable
fun WaitingDash(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(14.dp, 3.dp)
            .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
            .semantics { contentDescription = "No value yet" },
    )
}
