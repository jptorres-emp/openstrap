package org.openstrap.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The app's ink ladder, and the only place white-on-dark text, icon and line inks are written (a test fails on a
 * `Color.White.copy(alpha = ...)`, a black scrim alpha or a light grey literal anywhere else under the app, bar the
 * drawing files InkLiteralTest lists). Every step is white at an alpha, so it reads the same on a grey card and on a
 * lit coloured one. Text, icons and rules step down in this order:
 * Primary (values, main text), Strong (body text, row values), Secondary (captions, subtitles, notes, chevrons),
 * Tertiary (hints, units, axis labels, footnotes), Disabled (off states, empty dots, a row's end caret),
 * Line (strong rules, rings, empty tracks), Edge (borders, dividers, chip fills), Faint (the faintest fills and bands).
 */
internal object Ink {
    val Primary = Color.White
    val Strong = Color.White.copy(alpha = 0.8f)
    val Secondary = Color.White.copy(alpha = 0.65f)
    val Tertiary = Color.White.copy(alpha = 0.5f)
    val Disabled = Color.White.copy(alpha = 0.3f)
    val Line = Color.White.copy(alpha = 0.18f)
    val Edge = Color.White.copy(alpha = 0.12f)
    val Faint = Color.White.copy(alpha = 0.06f)

    /** Black under chips, wells, grips and trim shades on a picture: a light scrim. */
    val Scrim = Color.Black.copy(alpha = 0.4f)

    /** Black behind a sheet's backdrop, a dot's halo on a chart or a label on a picture: a deep scrim. */
    val ScrimDeep = Color.Black.copy(alpha = 0.65f)

    /** A solid silver for pictures and glow ink (the strap card, the moving border, a dial marker), which must not show through. */
    val Silver = Color(0xFFCECECE)

    /** A paused workout's hero: a solid grey, as a glow colour needs one. */
    val Paused = Color(0xFFB5B5B5)

    /** The light snackbar's paper (a fill, so it sits off the ink steps). */
    val Paper = Color(0xFFF2F2F2)
}
