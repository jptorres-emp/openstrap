package org.openstrap.app.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openstrap.app.data.Route
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.time.Instant
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

class GpxWriterTest {
    private val start = 1_700_000_000L

    private fun east(t: Long, metres: Double, altitude: Double? = null) = Route.Point(t, 0.0, metres / 111_195.0, 5f, altitude = altitude)

    /** A steady 3 m/s run east for 100 s, one fix a second. */
    private fun run(altitude: Double? = null) = (0..100).map { east(start + it, it * 3.0, altitude) }

    private fun parse(xml: String): Document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        .newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray()))

    private fun Document.all(tag: String) = getElementsByTagNameNS("*", tag)

    @Test fun `a run is one segment of timed points`() {
        val doc = parse(GpxWriter.write("Run", run(), emptyList()))
        assertEquals("1.1", doc.documentElement.getAttribute("version"))
        assertEquals(1, doc.all("trk").length)
        assertEquals(1, doc.all("trkseg").length)
        assertTrue(doc.all("trkpt").length > 50)
        val first = Route.track(run()).first()
        val time = (doc.all("trkpt").item(0) as Element).getElementsByTagNameNS("*", "time").item(0).textContent
        assertEquals(Instant.ofEpochSecond(first.t).toString(), time)
    }

    @Test fun `a pause splits the track in two segments`() {
        val doc = parse(GpxWriter.write("Run", run(), emptyList(), listOf(start + 40..start + 60)))
        assertEquals(2, doc.all("trkseg").length)
        val times = (0 until doc.all("time").length).map { Instant.parse(doc.all("time").item(it).textContent).epochSecond }
        assertTrue(times.isNotEmpty() && times.none { it > start + 40 && it < start + 60 })
        assertTrue(times.any { it <= start + 40 } && times.any { it >= start + 60 })
    }

    @Test fun `a pause between two fixes still splits the track`() {
        val gap = (0..40).map { east(start + it, it * 3.0) } + (42..100).map { east(start + it, it * 3.0) }
        val doc = parse(GpxWriter.write("Run", gap, emptyList(), listOf(start + 40..start + 42)))
        assertEquals(2, doc.all("trkseg").length)
    }

    @Test fun `heart rate rides along when a reading is near`() {
        val hr = (0L..50L step 5).map { start + it to 140 }
        val pts = parse(GpxWriter.write("Run", run(), hr)).all("trkpt")
        val first = pts.item(0) as Element
        assertEquals("140", first.getElementsByTagNameNS("*", "hr").item(0).textContent)
        val last = pts.item(pts.length - 1) as Element
        assertEquals(0, last.getElementsByTagNameNS("*", "extensions").length)
    }

    @Test fun `altitude appears only when the fix has one`() {
        assertTrue(parse(GpxWriter.write("Run", run(12.3), emptyList())).all("ele").item(0).textContent == "12.3")
        assertEquals(0, parse(GpxWriter.write("Run", run(), emptyList())).all("ele").length)
    }

    @Test fun `a name with markup is escaped`() {
        val name = "Run & \"Hill\" <1>"
        assertEquals(name, parse(GpxWriter.write(name, run(), emptyList())).all("name").item(0).textContent)
    }

    @Test fun `coordinates use a dot whatever the locale`() {
        val saved = Locale.getDefault()
        Locale.setDefault(Locale.GERMANY)
        try {
            val xml = GpxWriter.write("Run", run(), emptyList())
            assertTrue(xml.contains("lat=\"0.0000000\""))
            assertFalse(Regex("""(lat|lon)="[^"]*,""").containsMatchIn(xml))
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test fun `no route is a valid document with no points`() {
        assertEquals(0, parse(GpxWriter.write("Run", emptyList(), emptyList())).all("trkpt").length)
    }
}
