package org.openstrap.app.device

import android.location.Location
import android.location.LocationManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.openstrap.app.data.Route
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class WorkoutLocationTest {
    private fun fix(mock: Boolean) = Location(LocationManager.GPS_PROVIDER).apply {
        time = 1_000_000L
        latitude = 51.5
        longitude = -0.1
        accuracy = 5f
        if (mock) markMock()
    }

    // `setIsFromMockProvider` is hidden below API 31, so set it the way the system does.
    @Suppress("DEPRECATION")
    private fun Location.markMock() {
        if (android.os.Build.VERSION.SDK_INT >= 31) isMock = true
        else Location::class.java.getMethod("setIsFromMockProvider", Boolean::class.java).invoke(this, true)
    }

    private fun received(vararg fixes: Location): List<Route.Point> {
        val out = mutableListOf<Route.Point>()
        val gps = WorkoutLocation(ApplicationProvider.getApplicationContext()) { out += it }
        fixes.forEach(gps::receive)
        return out
    }

    @Test
    @Config(sdk = [35])
    fun mockFixesAreDropped() {
        val kept = received(fix(mock = false), fix(mock = true), fix(mock = false))
        assertEquals(2, kept.size)
        assertEquals(Route.Point(1_000, 51.5, -0.1, 5f), kept.first())
    }

    @Test
    @Config(sdk = [28])
    fun mockFixesAreDroppedBeforeApi31() {
        assertEquals(1, received(fix(mock = true), fix(mock = false)).size)
    }
}
