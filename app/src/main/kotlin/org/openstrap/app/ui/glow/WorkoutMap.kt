package org.openstrap.app.ui.glow

import org.openstrap.app.ui.theme.Space
import org.openstrap.app.ui.theme.Greys
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.layout.PaddingValues
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
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.openstrap.app.data.Route
import org.openstrap.app.ui.workout.ZONE_COLORS
import java.util.Locale

/** OpenFreeMap's dark style: no API key, no cookies; attribution is shown by the map's own button. */
private const val TILES_STYLE = "https://tiles.openfreemap.org/styles/dark"

/** The black of [BLANK_STYLE], for a map that can't be drawn. */
private val RouteOnlyBlack = Greys.Tint

/** "Route only": no tile requests at all, just the line on black. */
private const val BLANK_STYLE = """{"version":8,"sources":{},"layers":[{"id":"bg","type":"background","paint":{"background-color":"#0B0B0D"}}]}"""

/**
 * A workout's route on a map, each stretch in its heart-rate zone's colour ([Route.byZone]), with a white dot where
 * you are (or ended): [here] when given, so it shows before there's a line (standing still makes none), else the
 * line's end, ringed in [hereColor] when given; the start is a hollow dot. [routeOnly] loads no map tiles: the tile
 * server never learns where the map is. While [follow], the camera fits the whole route as it grows inside
 * [fitPadding] (clear of what's drawn over the map), or centres on the dot until then; a drag on the map calls
 * [onPanned], so the caller can stop following. [kmMarkers] puts a numbered circle at each kilometre's end; a
 * [highlight] of two or more points is drawn with a white casing under the line, and fitted instead of the route.
 * Bottom to top: the dark casing, the highlight, the route line, the kilometre markers, then the dots.
 */
// Callers remember their lists, so a stable list type wouldn't let this skip any more often.
@Suppress("UnstableCollections", "ComposeUnstableCollections")
@Composable
fun WorkoutMap(
    stretches: List<Pair<List<Route.Point>, Int>>, routeOnly: Boolean, modifier: Modifier = Modifier, here: Route.Point? = null,
    fitPadding: PaddingValues = PaddingValues(horizontal = Space.S24, vertical = Space.S24),
    /** How far up from the map's bottom edge the OpenStreetMap credit sits, clear of a panel drawn over it. */
    creditBottom: Dp = 18.dp,
    hereColor: Color? = null,
    follow: Boolean = true,
    onPanned: () -> Unit = {},
    kmMarkers: List<Pair<Int, Route.Point>> = emptyList(),
    highlight: List<Route.Point> = emptyList(),
) {
    // A preview or a layout test has no native map library: the route only map's black stands in for it.
    if (LocalInspectionMode.current) {
        Box(modifier.background(RouteOnlyBlack))
        return
    }
    val panned by rememberUpdatedState(onPanned)
    val density = LocalDensity.current
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
                // Fitting a route only metres long would zoom to single buildings; streets stay in view.
                m.setMaxZoomPreference(MAX_ZOOM)
                // MapLibre's licence doesn't ask for its logo; the ⓘ stays, for the OpenStreetMap credit the tiles need.
                m.uiSettings.isLogoEnabled = false
                m.uiSettings.attributionGravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
                m.addOnCameraMoveStartedListener { reason ->
                    if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) panned()
                }
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
    // In the bottom-right corner, inset to clear the card's rounded edge; set again when what's under it changes height.
    LaunchedEffect(map, creditBottom) {
        val inset = (18 * context.resources.displayMetrics.density).toInt()
        map?.uiSettings?.setAttributionMargins(0, 0, inset, with(density) { creditBottom.roundToPx() })
    }
    LaunchedEffect(map, routeOnly) {
        val m = map ?: return@LaunchedEffect
        style = null
        val builder = if (routeOnly) Style.Builder().fromJson(BLANK_STYLE) else Style.Builder().fromUri(TILES_STYLE)
        m.setStyle(builder) { s ->
            s.addSource(GeoJsonSource(SOURCE, geoJson(emptyList())))
            s.addSource(GeoJsonSource(HERE, geoJson(emptyList())))
            s.addSource(GeoJsonSource(START, geoJson(emptyList())))
            s.addSource(GeoJsonSource(KM_SRC, geoJson(emptyList())))
            s.addSource(GeoJsonSource(HIGHLIGHT_SRC, geoJson(emptyList())))
            underRoute(s, streets = !routeOnly)
            s.addLayer(
                LineLayer(HIGHLIGHT_LINE, HIGHLIGHT_SRC).withProperties(
                    PropertyFactory.lineColor(Color.White.toArgb()),
                    PropertyFactory.lineWidth(HIGHLIGHT_WIDTH),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                ),
            )
            s.addLayer(
                LineLayer(LINE, SOURCE).withProperties(
                    PropertyFactory.lineColor(Expression.get("color")),
                    PropertyFactory.lineWidth(5f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                ),
            )
            s.addLayer(
                SymbolLayer(KM_LAYER, KM_SRC).withProperties(
                    PropertyFactory.iconImage(Expression.get("icon")),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true),
                ),
            )
            s.addLayer(
                CircleLayer(START_DOT, START).withProperties(
                    PropertyFactory.circleRadius(5f),
                    PropertyFactory.circleColor(Greys.MapDot.toArgb()),
                    PropertyFactory.circleStrokeWidth(2f),
                    PropertyFactory.circleStrokeColor(Color.White.toArgb()),
                ),
            )
            s.addLayer(
                CircleLayer(HALO, HERE).withProperties(
                    PropertyFactory.circleRadius(18f),
                    PropertyFactory.circleOpacity(0.25f),
                    PropertyFactory.visibility(Property.NONE),
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
    LaunchedEffect(style, hereColor) {
        style?.getLayerAs<CircleLayer>(HALO)?.setProperties(
            *listOfNotNull(
                PropertyFactory.visibility(if (hereColor != null) Property.VISIBLE else Property.NONE),
                hereColor?.let { PropertyFactory.circleColor(it.toArgb()) },
            ).toTypedArray(),
        )
    }
    // Again for each new style: switching Route only replaces the style, and its images with it.
    LaunchedEffect(style, kmMarkers) { style?.showKmMarkers(kmMarkers, density.density) }
    LaunchedEffect(style, highlight) {
        style?.getSourceAs<GeoJsonSource>(HIGHLIGHT_SRC)?.setGeoJson(highlightGeoJson(highlight))
    }
    LaunchedEffect(style, stretches, here, follow, highlight, fitPadding) {
        val s = style ?: return@LaunchedEffect
        s.getSourceAs<GeoJsonSource>(SOURCE)?.setGeoJson(geoJson(stretches))
        val last = here ?: stretches.lastOrNull()?.first?.lastOrNull()
        s.getSourceAs<GeoJsonSource>(HERE)?.setGeoJson(point(last))
        val all = stretches.flatMap { it.first }
        s.getSourceAs<GeoJsonSource>(START)?.setGeoJson(point(all.firstOrNull()?.takeIf { all.size >= 2 }))
        if (!follow) return@LaunchedEffect
        val fit = if (highlight.size >= 2) highlight else all
        if (fit.size >= 2) {
            val bounds = LatLngBounds.Builder().includes(fit.map { LatLng(it.lat, it.lon) }).build()
            val px = with(density) { listOf(fitPadding.calculateLeftPadding(LayoutDirection.Ltr), fitPadding.calculateTopPadding(), fitPadding.calculateRightPadding(LayoutDirection.Ltr), fitPadding.calculateBottomPadding()).map { it.roundToPx() } }
            runCatching { map?.easeCamera(CameraUpdateFactory.newLatLngBounds(bounds, px[0], px[1], px[2], px[3])) }
        } else if (last != null) {
            map?.easeCamera(CameraUpdateFactory.newLatLngZoom(LatLng(last.lat, last.lon), MAX_ZOOM))
        }
    }
    AndroidView({ view }, modifier)
}

/** Close enough to read the streets, far enough to see where they go. */
private const val MAX_ZOOM = 16.5

/** The dark edge under the route line: a line 5 wide on one 9 wide leaves 2 each side. */
private const val CASING_WIDTH = 9f
private const val CASING_OPACITY = 0.6f

/** A picked kilometre's white casing, wider than the dark one so it shows on both sides of the line. */
private const val HIGHLIGHT_WIDTH = 11f

/**
 * OpenFreeMap's dark style draws streets a shade off its ground (#181818 on #0C0C0C), which is about invisible on a phone
 * outdoors, so a route seems to run through blocks rather than along streets. These lift each kind of street just enough
 * to read, still well under the route's colours. A layer the style no longer has is skipped.
 */
private val STREET_COLOURS = listOf(
    "highway_minor" to 0xFF2C2C30.toInt(),
    "highway_path" to 0xFF3C3C42.toInt(),
    "highway_major_casing" to 0xFF404046.toInt(),
    "highway_major_inner" to 0xFF27272B.toInt(),
    "highway_motorway_casing" to 0xFF46464C.toInt(),
    "highway_motorway_inner" to 0xFF2A2A2E.toInt(),
)

/**
 * What the route line goes over: the [streets] lifted when the map has them ([STREET_COLOURS]), and a dark edge under
 * the line, so a zone's colour reads the same over a street, a park or a building.
 */
private fun underRoute(style: Style, streets: Boolean) {
    if (streets) {
        for ((id, colour) in STREET_COLOURS) (style.getLayer(id) as? LineLayer)?.setProperties(PropertyFactory.lineColor(colour))
    }
    style.addLayer(
        LineLayer(CASING, SOURCE).withProperties(
            PropertyFactory.lineColor(Color.Black.toArgb()),
            PropertyFactory.lineOpacity(CASING_OPACITY),
            PropertyFactory.lineWidth(CASING_WIDTH),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        ),
    )
}

/** Each marker's image, then the markers: the style has no images of its own, and a new style drops added ones. */
private fun Style.showKmMarkers(markers: List<Pair<Int, Route.Point>>, density: Float) {
    markers.forEach { (km, _) -> addImage("km-$km", kmMarkerBitmap(km, density)) }
    getSourceAs<GeoJsonSource>(KM_SRC)?.setGeoJson(kmMarkerFeatures(markers))
}

private const val SOURCE = "route"
private const val CASING = "route-casing"
private const val KM_SRC = "km"
private const val KM_LAYER = "km-markers"
private const val HIGHLIGHT_SRC = "highlight"
private const val HIGHLIGHT_LINE = "highlight-line"
private const val HERE = "here"
private const val LINE = "route-line"
private const val DOT = "here-dot"
private const val HALO = "here-halo"
private const val START = "start"
private const val START_DOT = "start-dot"

/** One point as GeoJSON, or nothing. */
private fun point(p: Route.Point?): String =
    p?.let { """{"type":"Feature","geometry":{"type":"Point","coordinates":[${coord(it)}]},"properties":{}}""" }
        ?: """{"type":"FeatureCollection","features":[]}"""

internal fun coord(p: Route.Point) = String.format(Locale.ROOT, "%.7f,%.7f", p.lon, p.lat)

/** Each stretch a LineString feature with its zone's colour (grey without heart rate). */
private fun geoJson(stretches: List<Pair<List<Route.Point>, Int>>): String = buildString {
    append("""{"type":"FeatureCollection","features":[""")
    stretches.forEachIndexed { i, (points, zone) ->
        if (i > 0) append(',')
        val color = if (zone in ZONE_COLORS.indices) ZONE_COLORS[zone] else PausedColor
        val hex = String.format(Locale.ROOT, "#%06X", color.toArgb() and 0xFFFFFF)
        append("""{"type":"Feature","properties":{"color":"$hex"},"geometry":{"type":"LineString","coordinates":[""")
        append(points.joinToString(",") { "[${coord(it)}]" })
        append("]}}")
    }
    append("]}")
}
