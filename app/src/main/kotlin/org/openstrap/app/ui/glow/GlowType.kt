package org.openstrap.app.ui.glow

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.openstrap.app.ui.theme.FunnelDisplay

/**
 * Display type for the glow language: Funnel Display Light with tight tracking, as in the
 * inspiration's big headlines. Body text stays in the app's Funnel Sans.
 */
object GlowType {
    /** Verdicts and hero headlines ("Good energy", "Ready"). */
    val headline = TextStyle(fontFamily = FunnelDisplay, fontWeight = FontWeight.Light, fontSize = 26.sp, letterSpacing = (-0.02).em, lineHeight = 30.sp)

    /** Card titles ("Heart rate", "Activity"). */
    val title = TextStyle(fontFamily = FunnelDisplay, fontWeight = FontWeight.Light, fontSize = 20.sp, letterSpacing = (-0.02).em)

    /** Key numbers next to rings. */
    val number = TextStyle(fontFamily = FunnelDisplay, fontWeight = FontWeight.Light, fontSize = 20.sp, letterSpacing = (-0.02).em)
}
