package org.openstrap.app.ui.glow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.openstrap.app.OpenStrapApp
import org.openstrap.app.data.Route
import org.openstrap.app.maps.RouteSnapper
import org.openstrap.app.ui.dashboard.LocalHeartZones
import org.openstrap.app.ui.workout.PhoneSummary

/**
 * The line a finished workout's route map draws, the same on the card and on the route map page: along the roads when
 * [roads] is chosen and the route matched them ([RouteSnapper]), else as GPS recorded it. [legs] are the kilometres
 * moved onto that line, for the km markers and a picked kilometre; the splits themselves stay the track's.
 */
@Immutable
internal class RouteLine(
    /** The line in its heart zones' colours ([Route.byZone]). */
    val stretches: List<Pair<List<Route.Point>, Int>>,
    val legs: List<Route.Leg>,
    /** The Roads or GPS choice, null until read (and with no app around it: a preview or a test). */
    val roads: Boolean?,
    /** What matching gave, null while it runs or with GPS chosen. */
    val outcome: RouteSnapper.Outcome?,
    /** Sets the choice; null without the app. */
    val choose: ((Boolean) -> Unit)?,
) {
    /** Roads is chosen and the route is still being matched to them. */
    val matching: Boolean get() = roads == true && outcome == null

    /** Each whole kilometre's number at its end. */
    val kmMarkers: List<Pair<Int, Route.Point>>
        get() = legs.filter { it.split.meters >= 1000.0 }.map { it.split.index + 1 to it.points.last() }
}

/** [summary]'s route line ([RouteLine]); nothing is fetched until the setting is read, and nothing with GPS chosen. */
@Composable
internal fun rememberRouteLine(summary: PhoneSummary): RouteLine {
    val heartZones = LocalHeartZones.current
    val container = (LocalContext.current.applicationContext as? OpenStrapApp)?.container
    val setting = remember(container) { container?.settings?.routeRoads ?: flowOf(null) }
    val roads by setting.collectAsStateWithLifecycle(initialValue = null as Boolean?)
    val outcome by produceState<RouteSnapper.Outcome?>(null, summary.route, roads, container) {
        value = null
        if (roads == true && container != null) value = container.routeSnapper.snap(summary.route)
    }
    val snapped = (outcome as? RouteSnapper.Outcome.Snapped)?.line?.takeIf { roads == true }
    val scope = rememberCoroutineScope()
    val choose: ((Boolean) -> Unit)? = remember(container, scope) {
        container?.let { c -> { on: Boolean -> scope.launch { c.settings.setRouteRoads(on) }; Unit } }
    }
    val stretches = remember(summary, heartZones, snapped) {
        val maxHr = summary.analysis.maxHeartRate
        if (snapped != null) Route.lineByZone(snapped, summary.history, maxHr, heartZones)
        else Route.byZone(summary.route, summary.history, maxHr, heartZones)
    }
    val legs = remember(summary, snapped) {
        val track = Route.legs(summary.route, summary.pauses)
        if (snapped != null) Route.legsOnLine(snapped, track) else track
    }
    return RouteLine(stretches, legs, roads, outcome.takeIf { roads == true }, choose)
}
