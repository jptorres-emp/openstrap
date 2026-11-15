package org.openstrap.app.ui.glow

import androidx.compose.runtime.staticCompositionLocalOf
import org.openstrap.app.ui.glow.draw.GlowMood

/**
 * The mood for the cards on this screen: every [GlowCard] and [EdgeCard] under it follows. Only Today (by its moment)
 * and the Sleep tab provide one, everywhere else it's [GlowMood.Day].
 */
// Moved as is from GlowStyle.kt: the screen's light is ambient by nature, every card under a screen follows it.
@Suppress("CompositionLocalAllowlist", "ComposeCompositionLocalUsage")
val LocalGlowMood = staticCompositionLocalOf { GlowMood.Day }

/**
 * Where the light of every hero under this screen comes from, in degrees ([org.openstrap.app.ui.glow.draw.LightAngle]):
 * the current moment's side ([org.openstrap.app.ui.dashboard.heroLightAngle]), or always below on the Sleep tab (Halo
 * System board, "Tabs and details take the current moment's side. The Sleep tab always lights from below"). Null when
 * there is no moment, and each hero keeps its own side.
 */
@Suppress("CompositionLocalAllowlist", "ComposeCompositionLocalUsage")
val LocalHeroLightAngle = staticCompositionLocalOf<Float?> { null }
