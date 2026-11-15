package org.openstrap.app.ui.glow

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.openstrap.app.ui.components.LocalCardNumbers
import org.openstrap.app.ui.components.cardOpening
import org.openstrap.app.ui.components.pressClickable

/*
 * The middle tier (Picture card): a solid squircle card that draws its metric's picture in the ink, always unlit. The
 * rim it once took off your normal is gone (Halo System board).
 */

/** A cool soft white for things that speak for no one metric: info sheets, the zone dots with no reading. */
val EdgeNeutral = Color.hsl(220f, 0.10f, 0.55f)

/** Where a [PictureCard]'s rim is lit: [org.openstrap.app.ui.glow.draw.Rim], drawn on a canvas by the widgets too. */
typealias Rim = org.openstrap.app.ui.glow.draw.Rim

/**
 * A screen's say over the next picture card: [plain] draws it as a list panel,
 * for a card [varyLooks] moved to another look.
 */
@Immutable
data class CardOverride(val plain: Boolean = false)

// A layout's say over whichever card it places, several composables down: passing it would thread a parameter
// through every card, for the few a screen moves to another look.
@Suppress("CompositionLocalAllowlist", "ComposeCompositionLocalUsage")
val LocalCardOverride = staticCompositionLocalOf { CardOverride() }

private val CardShape = GlowLayout.CardShape

/**
 * A picture card in [ink], its metric's colour: a solid card, the picture drawn on it in the ink. Halo has no coloured
 * rim for a reading off your normal, so [rim] and [ink] no longer draw anything here; the card's tag says it (Halo
 * System board). The parameters stay so the screens placing it compile. [content] lays out the title row, the picture,
 * the number and its tag.
 */
@Suppress("UnusedParameter", "UNUSED_PARAMETER")
@Composable
fun PictureCard(
    ink: Color,
    modifier: Modifier = Modifier,
    rim: Rim? = null,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = GlowLayout.CardPadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Tapped, the card grows into what it opens (spec 3.5).
    val opening = cardOpening(onClick, CardShape)
    Column(
        modifier
            .then(opening?.modifier ?: Modifier)
            .let { if (onClick != null) it.pressClickable(opening?.onClick ?: onClick, CardShape) else it }
            // Solid card in the squircle, no white rim and no coloured one (Halo System board, "Every other card").
            .solidCard(CardShape)
            .padding(contentPadding),
    ) {
        CompositionLocalProvider(LocalCardNumbers provides opening?.numbers, LocalCardOpens provides (onClick != null)) { content() }
    }
}

/** The edge round a card or list panel ([GlowLayout.CardEdgeAlpha]). */
internal val CardHairline = Color.White.copy(alpha = GlowLayout.CardEdgeAlpha)
