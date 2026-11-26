package org.openstrap.app.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import com.dropbox.differ.SimpleImageComparator
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.ThresholdValidator
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.runner.RunWith
import org.openstrap.app.testing.ComposeRules
import org.openstrap.app.ui.theme.OpenStrapTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The base of the screenshot tests: pictures of today's look, kept under `app/src/test/screenshots` so that a later UI
 * change shows up as a changed picture. `:app:recordRoborazziDebug` stores them and `:app:verifyRoborazziDebug` compares
 * against the stored ones (the CI job runs the second). Plain `testDebugUnitTest` only draws them, with no comparison.
 *
 * Every picture is drawn on a 411 dp wide phone in the app's dark theme, once for each of [FONT_SCALES], as the system
 * font size setting scales text. [shoot]'s `grainy` is for pictures with the lit card's random film grain. A picture is named `<name>_<scale>.png`, such as `header_long_title_info_1.15.png`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class ScreenshotTest {
    @get:Rule val rules = ComposeRules()

    protected val compose: ComposeContentTestRule get() = rules.compose

    /**
     * Draws [content] and stores a picture of it per font scale in the folder [group]. One test method makes one call,
     * as a compose rule takes its content once. [prepare] runs after the first draw and before the pictures, for a
     * tap that moves the screen to the state to show. Animations stand still (the clock is not advanced on its own)
     * so a picture is the same on every run.
     */
    protected fun shoot(
        group: String, name: String, grainy: Boolean = false, prepare: () -> Unit = {}, content: @Composable () -> Unit,
    ) {
        val scale = mutableFloatStateOf(1f)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, scale.floatValue)) {
                OpenStrapTheme {
                    Box(Modifier.fillMaxWidth().background(Color.Black).testTag(TAG)) { content() }
                }
            }
        }
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        prepare()
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        FONT_SCALES.forEach { fontScale ->
            scale.floatValue = fontScale
            // The write reaches the composition on the next frame and is laid out on the one after: two steps, or the
            // picture is of the size before.
            Snapshot.sendApplyNotifications()
            compose.mainClock.advanceTimeBy(SETTLE_MS)
            compose.mainClock.advanceTimeBy(SETTLE_MS)
            compose.waitForIdle()
            compose.onNodeWithTag(TAG).captureRoboImage(file(group, "${name}_$fontScale"), if (grainy) GRAINY else RoborazziOptions())
        }
    }

    companion object {
        const val TAG = "screenshot"
        const val SETTLE_MS = 2_000L

        /**
         * For a lit card, whose film grain starts at a random spot of its tile on every run (LitSurface): each pixel may
         * differ a little (up to [GRAIN_DISTANCE] of the colour range), none by a change that moves text or a shape.
         */
        private const val GRAIN_DISTANCE = 0.2f
        val GRAINY = RoborazziOptions(
            compareOptions = RoborazziOptions.CompareOptions(
                imageComparator = SimpleImageComparator(maxDistance = GRAIN_DISTANCE), resultValidator = ThresholdValidator(0f),
            ),
        )

        /**
         * For a widget drawn through RemoteViews to a bitmap: its rounded corners are smoothed a shade differently on
         * Linux (CI) than on a Mac, by at most a few levels of 255 along the curve. Each pixel may differ by up to
         * [EDGE_DISTANCE] of the colour range, too little to hide a moved text or shape.
         */
        private const val EDGE_DISTANCE = 0.02f
        val SMOOTHED = RoborazziOptions(
            compareOptions = RoborazziOptions.CompareOptions(
                imageComparator = SimpleImageComparator(maxDistance = EDGE_DISTANCE), resultValidator = ThresholdValidator(0f),
            ),
        )

        /** Normal, and the two larger sizes the app is checked at. */
        val FONT_SCALES = listOf(1.0f, 1.15f, 1.3f)

        /** Where pictures go: the folder the `roborazzi` block of app/build.gradle.kts names, else the same path. */
        val OUTPUT_DIR = System.getProperty("roborazzi.output.dir") ?: "src/test/screenshots"

        fun file(group: String, name: String): String = File(OUTPUT_DIR, "$group/$name.png").path
    }
}
