package org.openstrap.app.ui.glow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.openstrap.app.ui.theme.PhosphorIcons

/*
 * One set of spacing rules for every glow card and dot panel, so their contents line up down the
 * screen: same gutter, same inset from the rounded edge, same rhythm from title to number to chart.
 */
object GlowLayout {
    /** Space between the screen edge and a card. */
    val Gutter = 20.dp
    /** Inside a card: clear of the big rounded corners and the rim light, so text sits on the calm middle. Dot panels use the same, so text columns align. */
    val CardPadding = PaddingValues(start = 30.dp, end = 30.dp, top = 32.dp, bottom = 26.dp)
    /** Title row to the hero number. */
    val TitleGap = 14.dp
    /** Hero number (or the last line of text) to the chart. */
    val ChartGap = 20.dp
    /** Every chart in a card has the same height, axis labels included. */
    val ChartHeight = 116.dp
    /** Dot-matrix hero numbers. */
    val HeroDot = 4.5.dp
    val HeroGap = 1.8.dp
}

/**
 * The title row of a card: the title, an optional note on the right (text, or [trailingContent] for
 * anything richer) and an optional chevron.
 */
@Composable
fun GlowHeader(
    title: String,
    trailing: String? = null,
    chevron: Boolean = false,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White, style = GlowType.title, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall)
        trailingContent?.invoke()
        if (chevron) {
            Spacer(Modifier.width(4.dp))
            Icon(PhosphorIcons.CaretRight, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(16.dp))
        }
    }
}

/**
 * The hero number of a card in dot-matrix, its unit beside it, and up to two short facts on the right
 * (text, or [side] for anything richer). Sits [GlowLayout.TitleGap] under the title.
 */
@Composable
fun GlowHeroNumber(value: String, unit: String, facts: List<String> = emptyList(), side: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = GlowLayout.TitleGap), verticalAlignment = Alignment.Bottom) {
        DotMatrixText(value, dot = GlowLayout.HeroDot, gap = GlowLayout.HeroGap)
        Text(unit, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp).weight(1f))
        if (facts.isNotEmpty()) Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            facts.forEach { Text(it, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall) }
        }
        side?.invoke()
    }
}

/** The gap above a card's chart. */
@Composable
fun GlowChartGap() = Spacer(Modifier.padding(top = GlowLayout.ChartGap))
