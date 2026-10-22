package org.openstrap.app.device

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import org.openstrap.app.data.Route

/**
 * GPS for an outdoor workout through Android's own location service: no Google services, no network location. Fixes
 * go to [onFix] once a second while [start]ed. Off unless the wearer turned GPS on for this workout.
 */
class WorkoutLocation(private val context: Context, private val onFix: (Route.Point) -> Unit) {
    private val manager = context.getSystemService(LocationManager::class.java)
    private var listening = false
    private var mockFixes = 0

    private val listener = LocationListener(::receive)

    /** Mock fixes are dropped, as Zepp does before its engine (native 01 §2.3); the rest go to [onFix]. */
    internal fun receive(l: Location) {
        if (isMock(l)) {
            mockFixes++
            return
        }
        onFix(Route.Point(l.time / 1000, l.latitude, l.longitude, if (l.hasAccuracy()) l.accuracy else Float.MAX_VALUE))
    }

    /** False without permission or a GPS provider; the workout then measures distance from the strap's steps. */
    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (listening) return true
        if (!granted(context) || manager?.isProviderEnabled(LocationManager.GPS_PROVIDER) != true) return false
        warmUp(manager)
        runCatching { manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, INTERVAL_MS, 0f, listener, Looper.getMainLooper()) }
            .onFailure { return false }
        listening = true
        mockFixes = 0
        return true
    }

    /**
     * Ask the GPS chip to fetch satellite orbit predictions (XTRA) and the network time now, as Zepp does at the start
     * of a GPS workout, so the first good fix comes sooner. Both come from the chip vendor's servers set in the
     * phone's GNSS config, not Google services. Best effort: a phone that ignores the commands just settles slower.
     */
    private fun warmUp(manager: LocationManager) {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        for (command in listOf("force_xtra_injection", "force_time_injection")) {
            runCatching { manager.sendExtraCommand(LocationManager.GPS_PROVIDER, command, Bundle()) }
        }
    }

    fun stop() {
        if (!listening) return
        runCatching { manager?.removeUpdates(listener) }
        listening = false
        if (mockFixes > 0) Log.i(TAG, "gps: dropped $mockFixes mock fixes")
    }

    companion object {
        private const val INTERVAL_MS = 1_000L
        private const val TAG = "OpenStrap"

        /** From a mock-location app rather than the GPS chip. `isMock` replaced `isFromMockProvider` in API 31. */
        @Suppress("DEPRECATION")
        internal fun isMock(l: Location): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) l.isMock else l.isFromMockProvider

        /** Fine or approximate; approximate fixes are too coarse to count, so distance falls back to steps. */
        fun granted(context: Context) =
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
}
