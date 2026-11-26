package org.openstrap.app.screenshots

import androidx.compose.ui.res.stringResource
import org.junit.Test
import org.openstrap.app.R
import org.openstrap.app.ui.glow.HaloIconButton
import org.openstrap.app.ui.glow.HaloIconVariant
import org.openstrap.app.ui.glow.HaloPageHeader
import org.openstrap.app.ui.glow.HeaderVariant
import org.openstrap.app.ui.theme.AppIcons

/**
 * Pins the page header ([HaloPageHeader]) as a detail page shows it: back at the left, the title with its switcher
 * caret, and the (i) passed the way GlowMetricDetail passes it today (a trailing lambda, which lands on the header's
 * title suffix, so the (i) follows the title on its line), or nothing there. A short and a long title ("Resting heart rate" and
 * "Blood oxygen" are the real long ones) each with and without the (i), so a change to the title's room, its size or
 * the side columns shows in the pictures at all three font sizes.
 */
class HeaderShots : ScreenshotTest() {
    @Test fun shortTitleNoInfo() = header("header_short_title", "Steps", info = false)

    @Test fun shortTitleInfo() = header("header_short_title_info", "Steps", info = true)

    @Test fun longTitleNoInfo() = header("header_long_title", "Resting heart rate", info = false)

    @Test fun longTitleInfo() = header("header_long_title_info", "Resting heart rate", info = true)

    @Test fun bloodOxygenNoInfo() = header("header_blood_oxygen", "Blood oxygen", info = false)

    @Test fun bloodOxygenInfo() = header("header_blood_oxygen_info", "Blood oxygen", info = true)

    private fun header(name: String, title: String, info: Boolean) = shoot("header", name) {
        // The detail's own variant, over its wash, as the detail template builds it; the (i) is a flat circle over the wash.
        HaloPageHeader(title, onBack = {}, variant = HeaderVariant.Detail, onTitle = {}) {
            if (info) HaloIconButton(
                AppIcons.Info, stringResource(R.string.glow_metrics_about, title), {}, variant = HaloIconVariant.OverWash,
            )
        }
    }
}
