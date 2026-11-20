package org.openstrap.app.ui.glow

import org.openstrap.app.ui.theme.Corner
import org.openstrap.app.ui.theme.Space
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.openstrap.app.ui.glow.draw.DotSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.openstrap.app.ui.components.cardOpening
import org.openstrap.app.ui.dashboard.CardLook
import org.openstrap.app.ui.dashboard.TabSlot
import org.openstrap.app.ui.dashboard.Tier
import org.openstrap.app.ui.dashboard.items
import org.openstrap.app.ui.theme.Ink

/*
 * What the Recovery, Sleep and Activity tabs share: laying out a tab's planned cards ([tabCards]) with the edge each
 * one's look fixes, and the rows their lists need beyond [MetricRow].
 */

/**
 * The edge card override that draws [this] look: the side it fixes, and the neutral light for a neutral card. Glow
 * cards and lists get none, so a list stays a list.
 */
internal fun CardLook.cardOverride(): CardOverride = when (tier) {
    // An unlit card drawn as a picture card (Activity's Start a workout, Plain) has no light; a list ignores it.
    Tier.List, Tier.Plain -> CardOverride(plain = true)
    else -> CardOverride()
}

/**
 * Adds a tab's [slots] to a dashboard as grid rows ([packGrid]), each row in its first card's pane ([tabPanes]), every card drawn
 * by [draw] under the edge its look fixes. [draw] gets the modifier that sizes a Small or a Tall in its row; a
 * full-width card is as high as its content. The row holding [focus] is the one the list scrolls to when asked.
 */
internal fun <C> GlowBlocks.tabCards(slots: List<TabSlot<C>>, focus: C? = null, draw: @Composable (C, Modifier) -> Unit) {
    // On columns too narrow for Smalls, a row of them spans both columns ([GlowBlocks.smallsAcross]).
    val panes = org.openstrap.app.ui.dashboard.tabPanes(slots, smallsAcross)
    packGrid(slots.indices.map { slots[it].size to it }).forEach { row ->
        val pane = panes[row.items().first()]
        if (row.items().any { slots[it].card == focus }) focusNext()
        val block: @Composable () -> Unit = {
            GridRowBlock(row) { i, m ->
                val slot = slots[i]
                CompositionLocalProvider(LocalCardOverride provides slot.look.cardOverride()) { draw(slot.card, m) }
            }
        }
        // Keyed on its cards, so a card keeps its saved state when one is added or hidden above it (audit K5).
        add(pane, GlowBlocks.cardsKey(row.items().map { "${slots[it].card}" }), block)
    }
}

/**
 * One grid row as the screens draw it: a full-width card as given, two Smalls side by side at a unit's height, or a
 * Tall beside two stacked Smalls. [draw] gets each card's sizing modifier.
 */
@Composable
internal fun <T> GridRowBlock(row: GridRow<T>, draw: @Composable (T, Modifier) -> Unit) {
    when (row) {
        is GridRow.Full -> draw(row.item, Modifier.fillMaxWidth())
        is GridRow.Pair -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GlowGrid.Gap)) {
            CompositionLocalProvider(LocalSmallTile provides true) {
                draw(row.left, Modifier.weight(1f).height(GlowGrid.SmallHeight))
                draw(row.right, Modifier.weight(1f).height(GlowGrid.SmallHeight))
            }
        }
        is GridRow.TallPair -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GlowGrid.Gap)) {
            draw(row.tall, Modifier.weight(1f).height(GlowGrid.height(CardSize.Tall)!!))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(GlowGrid.Gap)) {
                CompositionLocalProvider(LocalSmallTile provides true) {
                    draw(row.top, Modifier.fillMaxWidth().height(GlowGrid.StackedHeight))
                    draw(row.bottom, Modifier.fillMaxWidth().height(GlowGrid.StackedHeight))
                }
            }
        }
    }
}

/**
 * Your normal for [metric] as of [date] (mean ± one standard deviation of the last days, [History.normal]), loaded
 * from [history]; null without a history or before there are enough days. For your resting range behind a heart
 * rate line, from the nightly resting heart rate.
 */
@Composable
internal fun rememberNormal(metric: org.openstrap.app.presentation.Metric, date: java.time.LocalDate, history: org.openstrap.app.ui.dashboard.DailyHistory?): org.openstrap.app.data.History.Normal? {
    val from = date.minusDays(org.openstrap.app.data.History.NORMAL_DAYS - 1L)
    val stats by androidx.compose.runtime.remember(metric, date, history) {
        history?.invoke(metric.daily, from, date) ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsStateWithLifecycle(emptyList())
    return androidx.compose.runtime.remember(stats, date) { org.openstrap.app.data.History.normal(stats, date) }
}

