package org.openstrap.app.screenshots

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.junit.Test
import org.openstrap.app.ui.glow.CardFill
import org.openstrap.app.ui.glow.HaloCard
import org.openstrap.app.ui.glow.HaloTitle
import org.openstrap.app.ui.glow.MetricRow
import org.openstrap.app.ui.glow.NavRow
import org.openstrap.app.ui.theme.AppIcons
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.app.ui.theme.Space

/**
 * Pins the card of rows ([HaloCard] with [CardFill.Rows]), titled and untitled: the rows run edge to edge with their
 * dividers, the title takes the rows' side inset, and a metric row's value, status and chevron keep their places as
 * the text grows. The sample is the Today list's own kind of content (metric rows) and a settings row.
 */
class CardRowsShots : ScreenshotTest() {
    @Test fun titled() = shoot("card_rows", "card_rows_titled") {
        Column(Modifier.padding(Space.S16)) {
            HaloCard(fill = CardFill.Rows, title = { HaloTitle("Your body this morning", note = "Against your normal") }) { Rows() }
        }
    }

    @Test fun untitled() = shoot("card_rows", "card_rows_untitled") {
        Column(Modifier.padding(Space.S16)) {
            HaloCard(fill = CardFill.Rows) { Rows() }
        }
    }

    @Composable
    private fun Rows() {
        MetricRow("Resting heart rate", MetricColors.heart, "52", "Usual", icon = AppIcons.Heart, onClick = {})
        MetricRow("Blood oxygen", MetricColors.spo2, "97%", "Usual", icon = AppIcons.Heart, onClick = {})
        MetricRow("Breathing rate", MetricColors.sleepLight, null, "After your first night", icon = AppIcons.Heart)
        NavRow("Units", "Metric", onClick = {}, divider = false)
    }
}
