package org.openstrap.app.data

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A workout's GPS route, worked out in pure Kotlin like [Recovery]: distance, splits, and what an export may show.
 * The phone's fixes are filtered before they count: nothing until GPS has settled, poor accuracy and impossible jumps
 * are dropped, and movement within GPS's own error (a phone standing still wanders) doesn't add distance.
 */
object Route {
    /** One GPS fix: epoch seconds, degrees, and the horizontal accuracy in metres. */
    data class Point(val t: Long, val lat: Double, val lon: Double, val accuracy: Float)

    /** Fixes worse than this are kept for the map's accuracy ring but not counted. */
    const val MAX_ACCURACY_M = 30f
    /** Faster than this between two fixes is a GPS jump, not a person (about 54 km/h, a fast descent on a bike). */
    const val MAX_SPEED_MS = 15.0
    /** The route begins once this many fixes in a row agree: the first after a cold start can be far off. */
    const val SETTLE_FIXES = 5
    /** How accurate those settling fixes must be. */
    const val SETTLED_ACCURACY_M = 20f
    /** Least movement counted, however good the fixes claim to be. */
    const val MIN_MOVE_M = 5.0
    /** Plausible on foot between two fixes while settling (a sprint), so a runner's start still settles. */
    private const val SETTLE_SPEED_MS = 8.0

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

    /**
     * The fixes that count, in time order: from the first of [SETTLE_FIXES] in a row that are within
     * [SETTLED_ACCURACY_M] and agree with each other, then accurate enough and no faster than [MAX_SPEED_MS] from the
     * last kept one.
     */
    fun clean(points: List<Point>): List<Point> {
        val good = points.filter { it.accuracy <= MAX_ACCURACY_M }.sortedBy { it.t }.distinctBy { it.t }
        var run = 0
        val start = good.indices.firstOrNull { i ->
            val p = good[i]
            val agrees = p.accuracy <= SETTLED_ACCURACY_M &&
                (run == 0 || meters(good[i - 1], p) <= noise(good[i - 1], p) + SETTLE_SPEED_MS * (p.t - good[i - 1].t))
            run = if (agrees) run + 1 else if (p.accuracy <= SETTLED_ACCURACY_M) 1 else 0
            run >= SETTLE_FIXES
        }?.let { it - SETTLE_FIXES + 1 } ?: return emptyList()
        val out = mutableListOf<Point>()
        for (p in good.subList(start, good.size)) {
            val last = out.lastOrNull()
            if (last != null && meters(last, p) / (p.t - last.t) > MAX_SPEED_MS) continue
            out += p
        }
        return out
    }

    /**
     * [clean] less the wander of standing still: a fix is taken only once it's further from the last one taken than
     * both fixes' error together. What distance, pace, splits and the map's line are measured along.
     */
    fun track(points: List<Point>): List<Point> {
        val out = mutableListOf<Point>()
        for (p in clean(points)) {
            val last = out.lastOrNull()
            if (last == null || meters(last, p) >= noise(last, p)) out += p
        }
        return out
    }

    /** How far two fixes can lie apart from GPS error alone. */
    private fun noise(a: Point, b: Point): Double = maxOf(MIN_MOVE_M, (a.accuracy + b.accuracy).toDouble())

    /** Metres covered by [points] along [track], leaving out stretches that overlap [pauses]. */
    fun distance(points: List<Point>, pauses: List<LongRange> = emptyList()): Double =
        track(points).zipWithNext().sumOf { (a, b) -> if (paused(a, b, pauses)) 0.0 else meters(a, b) }

    /** Whether the stretch from [a] to [b] overlaps a pause; one that only ends or begins at a pause's edge counts. */
    private fun paused(a: Point, b: Point, pauses: List<LongRange>) = pauses.any { it.first < b.t && it.last > a.t }

    /** One kilometre (or the last part of one): when it ended, its time and metres. */
    data class Split(val index: Int, val endT: Long, val seconds: Long, val meters: Double) {
        /** Seconds per kilometre. */
        val pace: Double get() = if (meters > 0) seconds / (meters / 1000) else 0.0
    }

    /** Per-kilometre splits along [points]; the last one may be partial. Paused time doesn't count. */
    fun splits(points: List<Point>, pauses: List<LongRange> = emptyList(), km: Double = 1000.0): List<Split> {
        val clean = track(points)
        val out = mutableListOf<Split>()
        var startT = clean.firstOrNull()?.t ?: return out
        var meters = 0.0
        var seconds = 0L
        clean.zipWithNext().forEach { (a, b) ->
            if (paused(a, b, pauses)) return@forEach
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
        val clean = track(points)
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

    /**
     * The route cut into stretches of one zone each, for drawing it in zone colours: each fix takes the zone of the
     * nearest heart rate within [MAX_HR_GAP_S] (time, bpm, ascending), else none (−1, drawn grey).
     */
    fun byZone(points: List<Point>, hr: List<Pair<Long, Int>>, maxHr: Int): List<Pair<List<Point>, Int>> {
        val clean = track(points)
        if (clean.size < 2) return emptyList()
        val times = hr.map { it.first }
        fun zoneAt(t: Long): Int {
            var i = times.binarySearch(t).let { if (it < 0) -it - 1 else it }
            val near = listOf(i - 1, i).filter { it in hr.indices }.minByOrNull { kotlin.math.abs(hr[it].first - t) } ?: return -1
            if (kotlin.math.abs(hr[near].first - t) > MAX_HR_GAP_S) return -1
            return WorkoutRecorder.liveZone(hr[near].second, maxHr) ?: -1
        }
        val out = mutableListOf<Pair<MutableList<Point>, Int>>()
        clean.forEach { p ->
            val z = zoneAt(p.t)
            val last = out.lastOrNull()
            if (last != null && last.second == z) last.first += p
            else {
                // Each stretch starts where the last ended, so the line has no gaps.
                out += (mutableListOf<Point>().also { s -> last?.first?.lastOrNull()?.let(s::add); s += p }) to z
            }
        }
        return out.filter { it.first.size >= 2 }
    }

    private const val MAX_HR_GAP_S = 30L

    private fun nearest(hr: Map<Long, Int>, t: Long): Int? = (0L..5L).firstNotNullOfOrNull { d -> hr[t - d] ?: hr[t + d] }
}
