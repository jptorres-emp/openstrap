package org.openstrap.app.screenshots

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import org.junit.Test
import org.openstrap.app.ui.glow.GlowSegments
import org.openstrap.app.ui.glow.SegmentSize
import org.openstrap.app.ui.theme.Space

/**
 * Pins the segmented control ([GlowSegments]) with the Day, Week, Month and Year ranges as a detail shows them
 * (compact, as a tab row), and the regular size, each with one range chosen, so the pill's height and its labels'
 * room show at every font size.
 */
class SegmentsShots : ScreenshotTest() {
    private val ranges = listOf("Day", "Week", "Month", "Year")

    @Test fun compactDay() = shoot("segments", "segments_compact_day") {
        GlowSegments(ranges, 0, {}, Modifier.padding(Space.S16), role = Role.Tab, size = SegmentSize.Compact)
    }

    @Test fun compactYear() = shoot("segments", "segments_compact_year") {
        GlowSegments(ranges, 3, {}, Modifier.padding(Space.S16), role = Role.Tab, size = SegmentSize.Compact)
    }

    @Test fun regularWeek() = shoot("segments", "segments_regular_week") {
        GlowSegments(ranges, 1, {}, Modifier.padding(Space.S16))
    }
}
