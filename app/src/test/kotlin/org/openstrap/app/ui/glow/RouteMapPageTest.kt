package org.openstrap.app.ui.glow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openstrap.app.R
import org.openstrap.app.data.HeartZones
import org.openstrap.app.data.Route
import org.openstrap.app.data.WorkoutRecorder
import org.openstrap.app.data.db.AppWorkout
import org.openstrap.app.testing.ComposeRules
import org.openstrap.app.ui.workout.PhoneSummary
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The route map page (boards HaloRouteMap to HaloRouteMapNotOnRoads), and the route card's map that opens it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-port")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RouteMapPageTest {
    @get:Rule val rules = ComposeRules()

    private val start = 1_792_000_000L

    private val app get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun text(id: Int, vararg args: Any) = app.getString(id, *args)

    /** A 3 m/s run east along the equator, a fix a second, [seconds] long: 2.4 km makes two whole kilometres and a part. */
    private fun summary(seconds: Int = 800): PhoneSummary {
        val w = AppWorkout(start, start + seconds, 1)
        val route = (0..seconds).map { Route.Point(start + it, 0.0, it * 3.0 / 111_195.0, 5f) }
        val analysis = WorkoutRecorder.analyse(w, emptyList(), 190, HeartZones.STANDARD)
        return PhoneSummary(analysis, emptyList(), start, start + seconds, null, route = route)
    }

    private val owner = object : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }
    private val model get() = ViewModelProvider(owner)[RouteMapModel::class.java]

    private fun show(content: @Composable () -> Unit) {
        rules.compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true, LocalViewModelStoreOwner provides owner) { content() }
        }
    }

    private fun page() = show { RouteMapPage(summary(), model, PaddingValues()) }

    @Test fun `the route card's map opens the page for its workout`() {
        show { RouteMap(summary()) }
        rules.compose.onNodeWithContentDescription(text(R.string.glow_route_map_open)).performClick()
        assertTrue(model.isOpen(start))
    }

    @Test fun `close closes it`() {
        model.open(start)
        page()
        rules.compose.onNodeWithContentDescription(text(R.string.glow_close)).performClick()
        assertFalse(model.isOpen(start))
    }

    @Test fun `route only switches on and back`() {
        page()
        rules.compose.onNodeWithContentDescription(text(R.string.workout_live_map_route_only)).performClick()
        assertTrue(model.routeOnly)
        rules.compose.onNodeWithContentDescription(text(R.string.workout_live_map_show_map)).performClick()
        assertFalse(model.routeOnly)
    }

    @Test fun `fit route shows only after the map was panned`() {
        page()
        rules.compose.onAllNodesWithText(text(R.string.glow_route_map_fit)).assertCountEquals(0)
    }

    @Test fun `picking a kilometre says it, and picking it again shows the whole route`() {
        page()
        rules.compose.onNodeWithText(text(R.string.glow_route_map_pick_hint)).assertExists()
        rules.compose.onNodeWithText("Km 2", substring = true).performClick()
        rules.compose.onNodeWithText(text(R.string.glow_route_map_unpick_hint)).assertExists()
        rules.compose.onAllNodesWithText("Km 2 in", substring = true).assertCountEquals(1)
        rules.compose.onNodeWithText("Km 2", substring = false, useUnmergedTree = true).performClick()
        rules.compose.onNodeWithText(text(R.string.glow_route_map_pick_hint)).assertExists()
    }
}
