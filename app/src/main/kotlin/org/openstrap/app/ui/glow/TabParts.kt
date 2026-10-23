package org.openstrap.app.ui.glow

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.openstrap.app.ui.dashboard.CardLook
import org.openstrap.app.ui.dashboard.Hue
import org.openstrap.app.ui.dashboard.TabSlot
import org.openstrap.app.ui.dashboard.Tier
import org.openstrap.app.ui.theme.AppIcons

/*
 * What the Recovery, Sleep and Activity tabs share: laying out a tab's planned cards ([tabCards]) with the edge each
 * one's look fixes, and the rows their lists need beyond [ListRow].
 */

/**
 * The edge card override that draws [this] look: the side it fixes, and the neutral light for a neutral card. Glow
 * cards and lists get none, so a list stays a list.
 */
internal fun CardLook.edgeOverride(): EdgeOverride =
    if (tier == Tier.Edge || tier == Tier.Strip) EdgeOverride(edge = edge, color = EdgeNeutral.takeIf { hue == Hue.Neutral }) else EdgeOverride()

/**
 * Adds a tab's [slots] to a dashboard, each in its pane, drawn by [draw] under the edge its look fixes. The slot
 * whose card is [focus] is the one the list scrolls to when asked.
 */
internal fun <C> GlowBlocks.tabCards(slots: List<TabSlot<C>>, focus: C? = null, draw: @Composable (C) -> Unit) {
    slots.forEach { slot ->
        if (slot.card == focus) focusNext()
        val override = slot.look.edgeOverride()
        val block: @Composable () -> Unit = { CompositionLocalProvider(LocalEdgeOverride provides override) { draw(slot.card) } }
        when (slot.pane) {
            Pane.Full -> full(block)
            Pane.Left -> left(block)
            Pane.Right -> right(block)
        }
    }
}

private val RowHairline = Color.White.copy(alpha = 0.05f)

/** A list row with a bar under it: a [ListRow] whose [fraction] (0..1) also shows as a [GlowBar] in [color]. */
@Composable
internal fun BarRow(
    name: String, color: Color, value: String?, status: String, fraction: Float, offNormal: Boolean = false, divider: Boolean = true,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        ListRow(name, color, value, status, offNormal = offNormal, divider = divider)
        GlowBar(fraction, if (offNormal) color else Color.White.copy(alpha = 0.55f), Modifier.padding(start = 20.dp))
    }
}

/** The last rows of a list: an action or a way on, with a caret. */
@Composable
internal fun LinkRow(text: String, onClick: () -> Unit, divider: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .let { if (divider) it.drawBehind { drawLine(RowHairline, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) } else it }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = Color.White, style = GlowType.label.copy(fontWeight = null), modifier = Modifier.weight(1f))
        Icon(AppIcons.CaretRight, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
    }
}

/** A small pill button inside a row: white for the main action, glass otherwise. */
@Composable
internal fun RowPill(text: String, onClick: () -> Unit, primary: Boolean = false) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier.height(32.dp)
            .let { if (primary) it.background(Color.White, shape).clickable(onClick = onClick) else it.glass(shape, onClick) }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (primary) Color.Black else Color.White, style = GlowType.secondary, maxLines = 1) }
}

/**
 * A few figures side by side, each a [HeroFigure] in an equal share of the width, so four still fit a phone. Four or
 * more take smaller dots.
 */
@Composable
internal fun FigureRow(figures: List<Pair<String?, String>>, modifier: Modifier = Modifier) {
    val dot = if (figures.size >= 4) 2.4.dp else 3.dp
    Row(modifier.fillMaxWidth()) {
        figures.forEachIndexed { i, (value, label) ->
            val end = i == figures.lastIndex && i > 0
            HeroFigure(value, label, horizontal = if (end) Alignment.End else Alignment.Start, dot = dot, modifier = Modifier.weight(1f))
        }
    }
}

/** A dot in [color] and a short [text], for a caveat like "Learning, 9 of 14 nights". */
@Composable
internal fun CaveatLine(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(text, color = Color.White.copy(alpha = 0.8f), style = GlowType.secondary)
    }
}
