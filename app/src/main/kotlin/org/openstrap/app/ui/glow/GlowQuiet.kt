package org.openstrap.app.ui.glow

import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.border
import org.openstrap.app.ui.theme.MetricColors
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.openstrap.app.ui.components.pressClickable

/*
 * The quiet tier as a free-form panel (L0): the solid card round content that isn't rows, like controls, a chart or a
 * note. No light, no rim and no dots, so the lit cards on a screen stay special.
 */

/** A quiet panel: the solid card, [GlowLayout.CardPadding] inside, blocks 14 dp apart. */
@Composable
fun QuietPanel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.pressClickable(onClick, GlowLayout.CardShape) else it }
            .solidCard(GlowLayout.CardShape)
            .padding(GlowLayout.CardPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

/**
 * A card that is only its outline, 1.5 dp of white at 20% on black, for something on hold or gone with nothing lit:
 * the rest between sets, a deleted night. Not [GlowCard]'s outline, which draws the card's colour at 70%, louder
 * than these want.
 */
@Composable
internal fun OutlineCard(
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth().clip(GlowLayout.CardShape)
            .border(1.5.dp, Color.White.copy(alpha = 0.2f), GlowLayout.CardShape).padding(20.dp),
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        content = content,
    )
}

/**
 * The surface every control inside a quiet panel wears (rows, pills, buttons): the dark button fill, with no hairline
 * (Halo System board: no white rims), so it reads as something to press against the panel. With [onClick] it presses
 * like the rest of the glow controls.
 */
fun Modifier.glass(shape: Shape, onClick: (() -> Unit)? = null, scale: Float = 0.98f): Modifier =
    background(HaloButtonSurface, shape)
        .let { if (onClick != null) it.pressClickable(onClick, shape, scale) else it }

/** A change for the better, or a GPS fix good enough: the app's good green. */
internal val TrendGood = MetricColors.good

/** A change for the worse, or a GPS fix too poor: the app's warning amber. */
internal val TrendBad = MetricColors.warn
