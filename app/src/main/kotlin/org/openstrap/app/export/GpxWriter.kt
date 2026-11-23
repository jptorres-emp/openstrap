package org.openstrap.app.export

import org.openstrap.app.data.Route
import java.time.Instant
import java.util.Locale
import kotlin.math.abs

/** A workout's route as GPX 1.1 with Garmin's heart-rate extension, for sharing to other apps. */
object GpxWriter {
    /**
     * [points] go through [Route.track], the line the map draws; a pause starts a new segment. [heartRate] is (epoch
     * seconds, bpm) ascending. `ele` is GPS altitude as recorded (WGS84 ellipsoid, not sea level); Strava and similar
     * services re-derive elevation.
     */
    fun write(name: String, points: List<Route.Point>, heartRate: List<Pair<Long, Int>>, pauses: List<LongRange> = emptyList()): String {
        val times = heartRate.map { it.first }
        fun hrAt(t: Long): Int? {
            val i = times.binarySearch(t).let { if (it < 0) -it - 1 else it }
            val near = listOf(i - 1, i).filter { it in heartRate.indices }
                .minByOrNull { abs(heartRate[it].first - t) } ?: return null
            return heartRate[near].takeIf { abs(it.first - t) <= Route.MAX_HR_GAP_S }?.second
        }
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"unstrap\" xmlns=\"http://www.topografix.com/GPX/1/1\" ")
        sb.append("xmlns:gpxtpx=\"http://www.garmin.com/xmlschemas/TrackPointExtension/v1\">\n")
        sb.append("<trk><name>").append(escape(name)).append("</name>\n")
        // Fixes strictly inside a pause are left out, so no line runs through it; a pause ends a segment.
        var prev: Route.Point? = null
        var gap = false
        Route.track(points).forEach { p ->
            if (pauses.any { p.t > it.first && p.t < it.last }) {
                gap = true
                return@forEach
            }
            val last = prev
            val pausedBetween = last != null && pauses.any { it.first < p.t && it.last > last.t }
            if (last == null || gap || pausedBetween) {
                if (last != null) sb.append("</trkseg>\n")
                sb.append("<trkseg>\n")
            }
            prev = p
            gap = false
            sb.append("<trkpt lat=\"").append(coord(p.lat)).append("\" lon=\"").append(coord(p.lon)).append("\">")
            p.altitude?.let { sb.append("<ele>").append(String.format(Locale.ROOT, "%.1f", it)).append("</ele>") }
            sb.append("<time>").append(Instant.ofEpochSecond(p.t)).append("</time>")
            hrAt(p.t)?.let {
                sb.append("<extensions><gpxtpx:TrackPointExtension><gpxtpx:hr>").append(it)
                sb.append("</gpxtpx:hr></gpxtpx:TrackPointExtension></extensions>")
            }
            sb.append("</trkpt>\n")
        }
        if (prev != null) sb.append("</trkseg>\n")
        sb.append("</trk>\n</gpx>\n")
        return sb.toString()
    }

    private fun coord(v: Double) = String.format(Locale.ROOT, "%.7f", v)

    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
