package org.openstrap.app.screenshots

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import org.junit.Test
import org.openstrap.app.device.WorkoutState
import org.openstrap.app.testing.appWorkout
import org.openstrap.app.ui.glow.live.LapButton
import org.openstrap.app.ui.glow.live.LiveBottomBar
import org.openstrap.app.ui.glow.live.LiveControls

/**
 * Pins the Live frame's bottom bar ([LiveBottomBar]): the page dots over the controls row with Lap on the left, Pause
 * in the middle and the held stop with its "Hold to stop" label on the right, running and paused. The label sits under
 * a 72 dp column, so these pictures show whether it still fits when the text is larger.
 */
class LiveBarShots : ScreenshotTest() {
    private val controls = LiveControls({}, {}, {}, {}, {}, {})
    private val recording = WorkoutState.Recording(appWorkout(start = 0, end = null, sportType = 2), 190)
    private val paused = WorkoutState.Recording(appWorkout(start = 0, end = null, sportType = 2).copy(pausedAt = 600), 190)

    @Test fun running() = bar("live_bar_running", recording)

    @Test fun paused() = bar("live_bar_paused", paused)

    private fun bar(name: String, r: WorkoutState.Recording) = shoot("live_bar", name) {
        CompositionLocalProvider(LocalInspectionMode provides true) {
            LiveBottomBar(
                r, connected = true, contentPadding = PaddingValues(bottom = 16.dp), controls = controls,
                lap = { LapButton({}, label = true) }, position = { 0f }, count = 2, onPage = {}, onHeight = {},
            )
        }
    }
}
