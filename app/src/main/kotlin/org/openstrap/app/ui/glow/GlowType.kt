package org.openstrap.app.ui.glow

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.openstrap.app.ui.theme.FunnelDisplay
import org.openstrap.app.ui.theme.FunnelSans

/**
 * Display type for the glow language: Funnel Display Light with tight tracking, as in the
 * inspiration's big headlines. Body text stays in the app's Funnel Sans.
 */
object GlowType {
    /** Verdicts and hero headlines ("Good energy", "Ready"). */
    val headline = TextStyle(fontFamily = FunnelDisplay, fontWeight = FontWeight.Light, fontSize = 26.sp, letterSpacing = (-0.02).em, lineHeight = 30.sp)

    /** The sentence that leads a hero ("Slept 7 h 40. Recovered enough for your usual training."): 22 sp, line height 1.22. */
    val sentence = TextStyle(fontFamily = FunnelDisplay, fontWeight = FontWeight.Light, fontSize = 22.sp, letterSpacing = (-0.01).em, lineHeight = 26.8.sp)

    /** Card titles ("Heart rate", "Activity"). */
    val title = TextStyle(fontFamily = FunnelDisplay, fontWeight = FontWeight.Light, fontSize = 20.sp, letterSpacing = (-0.02).em)

    /** Key numbers next to rings. */
    val number = TextStyle(fontFamily = FunnelDisplay, fontWeight = FontWeight.Light, fontSize = 20.sp, letterSpacing = (-0.02).em)

    /** Titles on edge cards, list panels and list rows: 15 sp medium. */
    val label = TextStyle(fontFamily = FunnelSans, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp)

    /** Secondary lines (a status under a name, a note): 12.5 sp, drawn at [SECONDARY_ALPHA] white unless coloured. */
    val secondary = TextStyle(fontFamily = FunnelSans, fontSize = 12.5.sp, lineHeight = 17.sp)

    /** White for [secondary] text. */
    const val SECONDARY_ALPHA = 0.72f
}
