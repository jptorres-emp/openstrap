@file:Suppress("MatchingDeclarationName")

package org.openstrap.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The app's grey ladder, and the only place a neutral grey is written (a test fails on one anywhere else under ui/).
 * The five greys, each one step up from what it sits on (lightness about 0, 6, 11, 16, 21):
 * Page (the page), Card (grey cards, panels, lists, sheets), Raised (what is drawn on a card or a sheet),
 * Control (chips, buttons, pills, text fields), Picked (the picked chip, segment or day).
 */
internal object Greys {
    val Page = Color(0xFF000000)
    val Card = Color(0xFF141414)
    val Raised = Color(0xFF1E1E1E)
    val Control = Color(0xFF282828)
    val Picked = Color(0xFF323232)

    /** Grabbers, ring outlines and off dots: a hairline lighter than Picked, off the ladder on purpose. */
    val Line = Color(0xFF3D3D3D)

    /** The split bars' ink (summary and live), off the ladder on purpose. */
    val SplitBar = Color(0xFF303030)

    /** The medal's locked rim and its quiet ring: medal art, not UI. */
    val MedalQuiet = Color(0xFF262626)

    /** The locked medal's mark: medal art, not UI. */
    val MedalMark = Color(0xFF4E4E4E)

    /** The glass haze's tint, and the backdrop of a route-only map. */
    val Tint = Color(0xFF0B0B0B)

    /** The map's start dot (a picture on a map). */
    val MapDot = Color(0xFF131313)

    /** The moving border's dim base ring (MovingBorder): a cool dark grey. */
    val RingBase = Color(0xFF333340)

    /** Over a map: near black, a little see-through (board HaloLiveMap: rgba(12,13,16,0.82)). */
    val OverMap = Color(0xD10A0A0A)

    /** The strap picture's body (StrapDrawing). */
    val StrapBody = Color(0xFF0C0C0C)
}

/** The Control surface (spec 2.2): buttons, chips, pills and text fields on a page. */
internal val ControlSurface = Greys.Control

/**
 * The grey a part sits on: the page ([Greys.Page]) by default, [Greys.Card] inside a card and on a sheet (a sheet's fill
 * is Card), [Greys.Raised] inside a panel on a sheet. [HaloCard] and the sheet set it, and every grey drawn on top
 * reads it through [OnSurface], so a part always steps up the ladder from what is under it and never down (a panel on
 * a sheet is lighter than the sheet, not darker).
 */
// Every grey part reads it, and no caller could thread the surface through every layer to them.
@Suppress("CompositionLocalAllowlist", "ComposeCompositionLocalUsage")
internal val LocalSurface = staticCompositionLocalOf { Greys.Page }

/** The ladder in order, darkest first: what [OnSurface] steps up. */
internal val GreyLadder: List<Color> = listOf(Greys.Page, Greys.Card, Greys.Raised, Greys.Control, Greys.Picked)

/**
 * The grey [steps] up the ladder from [surface], at least [floor], at most [Greys.Picked]. A surface off the ladder (a lit
 * card's colour) counts as [Greys.Card], since that is what parts on a card were drawn for.
 */
internal fun stepUp(surface: Color, steps: Int, floor: Color = Greys.Page): Color {
    val at = GreyLadder.indexOf(surface).takeIf { it >= 0 } ?: 1
    val least = GreyLadder.indexOf(floor).coerceAtLeast(0)
    return GreyLadder[maxOf(at + steps, least).coerceAtMost(GreyLadder.lastIndex)]
}

/**
 * The greys of what sits on the current [LocalSurface], one step above it (components audit, grey on a sheet): on the
 * page, on a card and on a sheet they are what they always were on a card, on a panel inside a sheet each moves up one.
 */
internal object OnSurface {
    /** A grey card or panel: one step up (Card on the page, Raised on a card or a sheet). */
    val panel: Color @Composable @ReadOnlyComposable get() = stepUp(LocalSurface.current, 1)

    /** A divider, a figure rule, a progress track: one step up, never darker than [Greys.Raised]. */
    val line: Color @Composable @ReadOnlyComposable get() = stepUp(LocalSurface.current, 1, Greys.Raised)

    /** A segmented control's track: one step up, never darker than [Greys.Control]. */
    val track: Color @Composable @ReadOnlyComposable get() = stepUp(LocalSurface.current, 1, Greys.Control)

    /** A chip, a neutral tile, a control's fill: one step up, never darker than [Greys.Control]. */
    val control: Color @Composable @ReadOnlyComposable get() = stepUp(LocalSurface.current, 1, Greys.Control)

    /** The picked chip, segment or day: the top of the ladder. */
    val picked: Color get() = Greys.Picked
}
