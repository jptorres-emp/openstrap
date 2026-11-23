package org.openstrap.app.screenshots

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.junit.Test
import org.openstrap.app.ui.dashboard.NewsTag
import org.openstrap.app.ui.glow.CardSize
import org.openstrap.app.ui.glow.SleepPictureCard
import org.openstrap.app.ui.glow.gridSize
import org.openstrap.app.ui.glow.pictures.PictureSize
import org.openstrap.app.ui.glow.pictures.TempDial
import org.openstrap.app.ui.glow.pictures.WeekOfDots
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.app.ui.theme.Space

/**
 * Pins two Wide pictures that were cut off on a phone: Breathing disturbances with its "More than usual" tag, whose card
 * grows past a Wide's height when the tag and a large font need it, and Skin temperature's dial over a wide normal and
 * a night past the old 1.5 °C end, whose zones stop at the dial's ends and whose marker stays on the arc.
 */
class SleepRecoveryCardShots : ScreenshotTest() {
    @Test fun breathingWideTagged() = shoot("sleep_recovery_cards", "breathing_wide_tagged") {
        val week = listOf(0.4f, 0.1f, null, 0.1f, 0.4f, 0.9f)
        // In a lazy list, as on the Sleep tab: a card there has no height to fill, only its least height.
        LazyColumn(Modifier.padding(Space.S16)) {
            item {
                SleepPictureCard(
                    MetricColors.sleepLight, "Breathing disturbances", null, onClick = {},
                    value = "0.3", unit = "an hour", tag = NewsTag("More than usual", goal = false), status = "Your normal 0.1",
                    picture = { m -> WeekOfDots(week, 0.0f..0.2f, MetricColors.sleepLight, PictureSize.Wide, m) },
                    modifier = Modifier.fillMaxWidth().gridSize(CardSize.Wide),
                    pictureHeight = 48.dp,
                )
            }
        }
    }

    @Test fun tempDialWideNormal() = shoot("sleep_recovery_cards", "temp_dial_wide_normal") {
        Column(Modifier.padding(Space.S16)) {
            TempDial(1.9f, -1.4f..1.4f, MetricColors.temperature, PictureSize.Wide, Modifier.fillMaxWidth(0.44f).height(84.dp))
            TempDial(-0.5f, -0.3f..0.3f, MetricColors.temperature, PictureSize.Wide, Modifier.fillMaxWidth(0.44f).height(84.dp))
        }
    }
}
