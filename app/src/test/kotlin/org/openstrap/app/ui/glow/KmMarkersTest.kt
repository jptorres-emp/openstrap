package org.openstrap.app.ui.glow

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.openstrap.app.data.Route
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The full-screen map's km markers and split highlight, as GeoJSON and bitmaps. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KmMarkersTest {
    private val p1 = Route.Point(0, 1.5, 10.25, 5f)
    private val p2 = Route.Point(1, 1.6, 10.35, 5f)

    @Test fun `features carry the km as an icon name`() {
        val features = JSONObject(kmMarkerFeatures(listOf(1 to p1, 2 to p2))).getJSONArray("features")
        assertEquals(2, features.length())
        assertEquals("km-1", features.getJSONObject(0).getJSONObject("properties").getString("icon"))
        assertEquals("km-2", features.getJSONObject(1).getJSONObject("properties").getString("icon"))
        val coordinates = features.getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates")
        assertEquals(10.25, coordinates.getDouble(0), 1e-9)
        assertEquals(1.5, coordinates.getDouble(1), 1e-9)
    }

    @Test fun `a marker bitmap is square, sized by density, and drawn`() {
        val b = kmMarkerBitmap(7, 2f)
        assertEquals(44, b.width)
        assertEquals(44, b.height)
        assertNotEquals(0, b.getPixel(22, 22) ushr 24)
    }

    @Test fun `three digits still fit`() {
        val b = kmMarkerBitmap(123, 2f)
        assertEquals(44, b.width)
        assertEquals(44, b.height)
    }

    @Test fun `highlight needs two points`() {
        assertEquals(0, JSONObject(highlightGeoJson(emptyList())).getJSONArray("features").length())
        val one = JSONObject(highlightGeoJson(listOf(p1, p2))).getJSONArray("features")
        assertEquals(1, one.length())
        assertEquals("LineString", one.getJSONObject(0).getJSONObject("geometry").getString("type"))
    }
}
