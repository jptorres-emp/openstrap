package org.openstrap.app.device

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import org.openstrap.app.data.Route

/**
 * GPS for an outdoor workout through Android's own location service: no Google services, no network location. Fixes
 * go to [onFix] once a second while [start]ed. Off unless the wearer turned GPS on for this workout.
 */
class WorkoutLocation(private val context: Context, private val onFix: (Route.Point) -> Unit) {
    private val manager = context.getSystemService(LocationManager::class.java)
    private var listening = false

    private val listener = LocationListener { l: Location ->
        onFix(Route.Point(l.time / 1000, l.latitude, l.longitude, if (l.hasAccuracy()) l.accuracy else Float.MAX_VALUE))
    }

    /** False without permission or a GPS provider; the workout then measures distance from the strap's steps. */
    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (listening) return true
        if (!granted(context) || manager?.isProviderEnabled(LocationManager.GPS_PROVIDER) != true) return false
        runCatching { manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, INTERVAL_MS, 0f, listener, Looper.getMainLooper()) }
            .onFailure { return false }
        listening = true
        return true
    }

    fun stop() {
        if (!listening) return
        runCatching { manager?.removeUpdates(listener) }
        listening = false
    }

    companion object {
        private const val INTERVAL_MS = 1_000L

        /** Fine or approximate; approximate fixes are too coarse to count, so distance falls back to steps. */
        fun granted(context: Context) =
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
}
