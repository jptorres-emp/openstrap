package org.openstrap.app.ui.glow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.openstrap.app.data.Route
import org.openstrap.app.ui.workout.ZONE_COLORS
import java.util.Locale

/** OpenFreeMap's dark style: no API key, no cookies; attribution is shown by the map's own button. */
private const val TILES_STYLE = "https://tiles.openfreemap.org/styles/dark"

/** "Route only": no tile requests at all, just the line on black. */
private const val BLANK_STYLE = """{"version":8,"sources":{},"layers":[{"id":"bg","type":"background","paint":{"background-color":"#0B0B0D"}}]}"""

/**
 * A workout's route on a map, each stretch in its heart-rate zone's colour ([Route.byZone]), with a white dot where
 * you are (or ended): [here] when given, so it shows before there's a line (standing still makes none), else the
 * line's end. [routeOnly] loads no map tiles: the tile server never learns where the map is. The camera fits the
 * whole route as it grows, or centres on the dot until then.
 */
@Composable
fun WorkoutMap(stretches: List<Pair<List<Route.Point>, Int>>, routeOnly: Boolean, modifier: Modifier = Modifier, here: Route.Point? = null) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    val view = remember {
        MapLibre.getInstance(context)
        MapView(context).apply {
            onCreate(null)
            getMapAsync { m ->
                m.uiSettings.isRotateGesturesEnabled = false
                m.uiSettings.isTiltGesturesEnabled = false
                m.uiSettings.isCompassEnabled = false
                // MapLibre's licence doesn't ask for its logo; the ⓘ stays, for the OpenStreetMap credit the tiles need.
                m.uiSettings.isLogoEnabled = false
                map = m
            }
        }
    }
    DisposableEffect(lifecycle, view) {
        val observer = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> view.onStart()
                Lifecycle.Event.ON_RESUME -> view.onResume()
                Lifecycle.Event.ON_PAUSE -> view.onPause()
                Lifecycle.Event.ON_STOP -> view.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            view.onDestroy()
        }
    }
    LaunchedEffect(map, routeOnly) {
        val m = map ?: return@LaunchedEffect
        style = null
        val builder = if (routeOnly) Style.Builder().fromJson(BLANK_STYLE) else Style.Builder().fromUri(TILES_STYLE)
        m.setStyle(builder) { s ->
            s.addSource(GeoJsonSource(SOURCE, geoJson(emptyList())))
            s.addSource(GeoJsonSource(HERE, geoJson(emptyList())))
            s.addLayer(
                LineLayer(LINE, SOURCE).withProperties(
                    PropertyFactory.lineColor(Expression.get("color")),
                    PropertyFactory.lineWidth(5f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                ),
            )
            s.addLayer(
                CircleLayer(DOT, HERE).withProperties(
                    PropertyFactory.circleRadius(6f),
                    PropertyFactory.circleColor(Color.White.toArgb()),
                    PropertyFactory.circleStrokeWidth(3f),
                    PropertyFactory.circleStrokeColor(Color.Black.toArgb()),
                ),
            )
            style = s
        }
    }
    LaunchedEffect(style, stretches, here) {
        val s = style ?: return@LaunchedEffect
        s.getSourceAs<GeoJsonSource>(SOURCE)?.setGeoJson(geoJson(stretches))
        val last = here ?: stretches.lastOrNull()?.first?.lastOrNull()
        s.getSourceAs<GeoJsonSource>(HERE)?.setGeoJson(
            last?.let { """{"type":"Feature","geometry":{"type":"Point","coordinates":[${coord(it)}]},"properties":{}}""" }
                ?: """{"type":"FeatureCollection","features":[]}""",
        )
        val all = stretches.flatMap { it.first }
        if (all.size >= 2) {
            val bounds = LatLngBounds.Builder().includes(all.map { LatLng(it.lat, it.lon) }).build()
            runCatching { map?.easeCamera(CameraUpdateFactory.newLatLngBounds(bounds, 80)) }
        } else if (last != null) {
            map?.easeCamera(CameraUpdateFactory.newLatLngZoom(LatLng(last.lat, last.lon), 16.0))
        }
    }
    AndroidView({ view }, modifier)
}

private const val SOURCE = "route"
private const val HERE = "here"
private const val LINE = "route-line"
private const val DOT = "here-dot"

private fun coord(p: Route.Point) = String.format(Locale.ROOT, "%.7f,%.7f", p.lon, p.lat)

/** Each stretch a LineString feature with its zone's colour (grey without heart rate). */
private fun geoJson(stretches: List<Pair<List<Route.Point>, Int>>): String = buildString {
    append("""{"type":"FeatureCollection","features":[""")
    stretches.forEachIndexed { i, (points, zone) ->
        if (i > 0) append(',')
        val color = if (zone in ZONE_COLORS.indices) ZONE_COLORS[zone] else Color(0xFF9AA0B4)
        val hex = String.format(Locale.ROOT, "#%06X", color.toArgb() and 0xFFFFFF)
        append("""{"type":"Feature","properties":{"color":"$hex"},"geometry":{"type":"LineString","coordinates":[""")
        append(points.joinToString(",") { "[${coord(it)}]" })
        append("]}}")
    }
    append("]}")
}
