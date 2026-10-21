package org.openstrap.app.data

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A workout's GPS route, worked out in pure Kotlin like [Recovery]: distance, splits, and what an export may show.
 * The phone's fixes are filtered before they count: poor accuracy and impossible jumps are dropped.
 */
object Route {
    /** One GPS fix: epoch seconds, degrees, and the horizontal accuracy in metres. */
    data class Point(val t: Long, val lat: Double, val lon: Double, val accuracy: Float)

    /** Fixes worse than this are kept for the map's accuracy ring but not counted. */
    const val MAX_ACCURACY_M = 30f
    /** Faster than this between two fixes is a GPS jump, not a person (about 54 km/h, a fast descent on a bike). */
    const val MAX_SPEED_MS = 15.0
    /** An export leaves out this much of the start and the end, so a shared run doesn't show where you live. */
    const val PRIVACY_M = 300.0

    private const val EARTH_M = 6_371_000.0

    /** Great-circle distance in metres. */
    fun meters(a: Point, b: Point): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_M * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /** The fixes that count, in time order: accurate enough, and no faster than [MAX_SPEED_MS] from the last kept one. */
    fun clean(points: List<Point>): List<Point> {
        val out = mutableListOf<Point>()
        points.sortedBy { it.t }.forEach { p ->
            if (p.accuracy > MAX_ACCURACY_M) return@forEach
            val last = out.lastOrNull()
            if (last != null && p.t > last.t && meters(last, p) / (p.t - last.t) > MAX_SPEED_MS) return@forEach
            if (last != null && p.t <= last.t) return@forEach
            out += p
        }
        return out
    }

    /** Metres covered by [points] after [clean], leaving out [pauses] (a fix inside a pause starts a new stretch). */
    fun distance(points: List<Point>, pauses: List<LongRange> = emptyList()): Double =
        clean(points).zipWithNext().sumOf { (a, b) -> if (pauses.any { b.t in it || a.t in it }) 0.0 else meters(a, b) }

    /** One kilometre (or the last part of one): when it ended, its time and metres. */
    data class Split(val index: Int, val endT: Long, val seconds: Long, val meters: Double) {
        /** Seconds per kilometre. */
        val pace: Double get() = if (meters > 0) seconds / (meters / 1000) else 0.0
    }

    /** Per-kilometre splits along [points]; the last one may be partial. Paused time doesn't count. */
    fun splits(points: List<Point>, pauses: List<LongRange> = emptyList(), km: Double = 1000.0): List<Split> {
        val clean = clean(points)
        val out = mutableListOf<Split>()
        var startT = clean.firstOrNull()?.t ?: return out
        var meters = 0.0
        var seconds = 0L
        clean.zipWithNext().forEach { (a, b) ->
            if (pauses.any { b.t in it || a.t in it }) return@forEach
            meters += meters(a, b)
            seconds += b.t - a.t
            if (meters >= km) {
                out += Split(out.size, b.t, seconds, meters)
                meters = 0.0
                seconds = 0
                startT = b.t
            }
        }
        if (meters > 0) out += Split(out.size, clean.last().t, seconds, meters)
        return out
    }

    /** Seconds per kilometre over the last [windowS] seconds of [points], or null with too little to say. */
    fun currentPace(points: List<Point>, windowS: Long = 30): Double? {
        val clean = clean(points)
        val last = clean.lastOrNull() ?: return null
        val recent = clean.filter { it.t >= last.t - windowS }
        val m = recent.zipWithNext().sumOf { (a, b) -> meters(a, b) }
        val s = recent.last().t - recent.first().t
        return if (m < 10 || s <= 0) null else s / (m / 1000)
    }

    /** What an export may show: the route less its first and last [PRIVACY_M] metres. Empty when it's shorter. */
    fun privacyTrim(points: List<Point>, meters: Double = PRIVACY_M): List<Point> {
        val clean = clean(points)
        if (clean.size < 2) return emptyList()
        val along = DoubleArray(clean.size)
        for (i in 1 until clean.size) along[i] = along[i - 1] + meters(clean[i - 1], clean[i])
        val total = along.last()
        return clean.filterIndexed { i, _ -> along[i] >= meters && along[i] <= total - meters }
    }

    /** "5′42″" per kilometre. */
    fun formatPace(secondsPerKm: Double?): String {
        if (secondsPerKm == null || secondsPerKm <= 0 || secondsPerKm > 3600) return "—"
        val s = Math.round(secondsPerKm)
        return "%d′%02d″".format(s / 60, s % 60)
    }

    /**
     * A GPX 1.1 file of [points] (already privacy-trimmed by the caller) with heart rate from [heartRate] (epoch
     * seconds to bpm) in Garmin's TrackPointExtension, which Strava and others read.
     */
    fun gpx(name: String, points: List<Point>, heartRate: Map<Long, Int>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<gpx version=\"1.1\" creator=\"OpenStrap\" xmlns=\"http://www.topografix.com/GPX/1/1\" ")
        append("xmlns:gpxtpx=\"http://www.garmin.com/xmlschemas/TrackPointExtension/v1\">\n")
        append("  <trk><name>").append(name.replace("&", "&amp;").replace("<", "&lt;")).append("</name><trkseg>\n")
        points.forEach { p ->
            append("    <trkpt lat=\"%.7f\" lon=\"%.7f\"><time>%s</time>".format(java.util.Locale.ROOT, p.lat, p.lon, java.time.Instant.ofEpochSecond(p.t)))
            nearest(heartRate, p.t)?.let { append("<extensions><gpxtpx:TrackPointExtension><gpxtpx:hr>$it</gpxtpx:hr></gpxtpx:TrackPointExtension></extensions>") }
            append("</trkpt>\n")
        }
        append("  </trkseg></trk>\n</gpx>\n")
    }

    private fun nearest(hr: Map<Long, Int>, t: Long): Int? = (0L..5L).firstNotNullOfOrNull { d -> hr[t - d] ?: hr[t + d] }
}
