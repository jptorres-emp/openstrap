package org.openstrap.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteTest {
    // Along the equator a degree of longitude is 111.195 km, so 0.001° is about 111 m.
    private fun east(t: Long, metres: Double, accuracy: Float = 5f) = Route.Point(t, 0.0, metres / 111_195.0, accuracy)

    /** A steady 3 m/s run east for [seconds], one fix a second. */
    private fun run(seconds: Int, from: Long = 0) = (0..seconds).map { east(from + it, it * 3.0) }

    @Test
    fun distanceAlongTheEquator() {
        assertEquals(300.0, Route.distance(run(100)), 1.0)
    }

    @Test
    fun poorFixesAndJumpsDoNotCount() {
        val points = run(100).toMutableList()
        points[50] = east(50, 5_000.0) // a jump
        points[60] = east(60, 180.0, accuracy = 80f)
        // Measured along fixes about 10 m apart, so the last few metres may not be in yet.
        assertEquals(300.0, Route.distance(points), 10.0)
    }

    @Test
    fun theFirstFixesBeforeGpsSettlesDoNotCount() {
        // A cold start: a fix 150 m off, a couple still poor, then good ones where you actually are.
        val start = listOf(east(0, 150.0, 15f), east(1, 60.0, 25f), east(2, 20.0, 40f))
        assertEquals(300.0, Route.distance(start + run(100, from = 3)), 10.0)
    }

    @Test
    fun standingStillAddsNothing() {
        // Ten minutes at home: fixes claiming ±5 m, each somewhere within 5 m of where you are.
        val home = (0L..600L).map { t -> Route.Point(t, kotlin.math.sin(t * 0.7) * 5 / 111_195.0, kotlin.math.cos(t * 0.7) * 5 / 111_195.0, 5f) }
        assertEquals(0.0, Route.distance(home), 0.0)
        assertTrue(Route.track(home).size == 1)
    }

    @Test
    fun noRouteUntilGpsSettles() {
        assertTrue(Route.distance(run(3)).let { it == 0.0 })
        assertTrue(Route.clean(run(100).map { it.copy(accuracy = 25f) }).isEmpty())
    }

    @Test
    fun pausedStretchesDoNotCount() {
        assertEquals(240.0, Route.distance(run(100), listOf(40L..60L)), 7.0)
    }

    @Test
    fun splitsEveryKilometre() {
        val s = Route.splits(run(1000))
        assertEquals(3, s.size)
        assertEquals(1000.0, s[0].meters, 12.0)
        // 3 m/s is 5′33″ a kilometre.
        assertEquals(333.0, s[0].pace, 2.0)
        assertTrue(s[2].meters < 1000)
    }

    @Test
    fun exportsHideTheStartAndEnd() {
        val trimmed = Route.privacyTrim(run(1000))
        val first = trimmed.first().lon * 111_195.0
        val last = trimmed.last().lon * 111_195.0
        assertTrue(first >= 300 && first < 304)
        assertTrue(last <= 2700 && last > 2696)
        assertTrue(Route.privacyTrim(run(150)).isEmpty())
    }

    @Test
    fun paceAndItsFormat() {
        assertEquals(333.0, Route.currentPace(run(100))!!, 2.0)
        assertEquals("5′33″", Route.formatPace(333.0))
        assertEquals("—", Route.formatPace(null))
    }

    @Test
    fun gpxCarriesHeartRate() {
        val g = Route.gpx("Run <1>", run(2), mapOf(0L to 120, 2L to 125))
        assertTrue(g.contains("<name>Run &lt;1></name>"))
        assertTrue(g.contains("<gpxtpx:hr>120</gpxtpx:hr>"))
        assertEquals(3, Regex("<trkpt").findAll(g).count())
    }
}
