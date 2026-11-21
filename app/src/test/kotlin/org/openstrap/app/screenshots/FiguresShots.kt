package org.openstrap.app.screenshots

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import org.junit.Test
import org.openstrap.app.ui.glow.HaloFigures
import org.openstrap.app.ui.theme.Space

/**
 * Pins the figures row ([HaloFigures]): one, two and three columns, a column with no value (the waiting dash), and a
 * column whose unit is long ("br/min"), so the rule between columns, the value over its label and the way a long unit
 * wraps or clips at each font size show in the pictures.
 */
class FiguresShots : ScreenshotTest() {
    @Test fun oneColumn() = shoot("figures", "figures_1_column") {
        HaloFigures(listOf("72 bpm" to "Average"), Modifier.padding(Space.S16))
    }

    @Test fun twoColumns() = shoot("figures", "figures_2_columns") {
        HaloFigures(listOf("52 bpm" to "Lowest", "148 bpm" to "Highest"), Modifier.padding(Space.S16))
    }

    @Test fun threeColumns() = shoot("figures", "figures_3_columns") {
        HaloFigures(listOf("52 bpm" to "Lowest", "71 bpm" to "Average", "148 bpm" to "Highest"), Modifier.padding(Space.S16))
    }

    @Test fun withDash() = shoot("figures", "figures_dash") {
        HaloFigures(listOf("7 h 30 min" to "Slept", null to "Deep sleep", "93 %" to "Blood oxygen"), Modifier.padding(Space.S16))
    }

    @Test fun longUnit() = shoot("figures", "figures_long_unit") {
        HaloFigures(
            listOf("15.2" to "Breathing rate", "41" to "HRV", "98" to "Blood oxygen"),
            Modifier.padding(Space.S16), units = listOf("br/min", "ms", "%"),
        )
    }
}
