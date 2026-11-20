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
import org.openstrap.app.ui.theme.Ink

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

/** The edge round a card or list panel ([GlowLayout.CardEdgeAlpha]). */
internal val CardHairline = Ink.Edge
