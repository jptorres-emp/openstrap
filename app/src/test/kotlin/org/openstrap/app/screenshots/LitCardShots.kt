package org.openstrap.app.screenshots

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import org.junit.Test
import org.openstrap.app.ui.glow.CardTone
import org.openstrap.app.ui.glow.GlowType
import org.openstrap.app.ui.glow.HaloCard
import org.openstrap.app.ui.glow.HaloCardSize
import org.openstrap.app.ui.glow.HaloTitle
import org.openstrap.app.ui.glow.draw.CoreShape
import org.openstrap.app.ui.glow.draw.GlowStyle
import org.openstrap.app.ui.glow.draw.LightAngle
import org.openstrap.app.ui.theme.Ink
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.app.ui.theme.Space
import androidx.compose.ui.unit.dp

/**
 * Pins a lit card ([HaloCard] with [CardTone.Lit]) the way Today's hero is built: a title in the hero's size, a line of
 * secondary text on the light, and a second card with a longer sentence, so how the secondary ink reads on the light
 * and how the card grows with the text show at every font size. The light is the gallery's Recovery card, lit from the
 * lower left in Recovery's ink. The card's film grain starts at a random spot on each run, so these two pictures are
 * compared with a small allowance per pixel (`grainy`), which text or shape changes still exceed.
 */
class LitCardShots : ScreenshotTest() {
    private val style = GlowStyle(
        color = MetricColors.readiness,
        coreShape = CoreShape.Radial,
        coreWidth = 0.95f, coreHeight = 0.85f, coreBlur = 26.dp, coreLightness = 0.03f,
        toneShift = -12f, toneWidth = 14.dp,
        glowSize = 26.dp, glowStrength = 0.85f,
        lightAngle = LightAngle.LowerLeft,
    )

    @Test fun hero() = shoot("lit_card", "lit_card_hero", grainy = true) {
        Column(Modifier.padding(Space.S16)) {
            HaloCard(Modifier.fillMaxWidth(), tone = CardTone.lit(style), size = HaloCardSize.Wide, title = { HaloTitle("Recovery") }) {
                Text("78", color = androidx.compose.ui.graphics.Color.White, style = GlowType.verdict)
                Text("A good day for effort", color = Ink.Secondary, style = GlowType.secondary)
            }
        }
    }

    @Test fun heroLongText() = shoot("lit_card", "lit_card_hero_long_text", grainy = true) {
        Column(Modifier.padding(Space.S16)) {
            HaloCard(Modifier.fillMaxWidth(), tone = CardTone.lit(style), size = HaloCardSize.Wide, title = { HaloTitle("Recovery") }) {
                Text("78", color = androidx.compose.ui.graphics.Color.White, style = GlowType.verdict)
                Text(
                    "Your resting heart rate is a little under your usual and your sleep was long, so a good day for effort.",
                    color = Ink.Secondary, style = GlowType.secondary,
                )
            }
        }
    }
}
