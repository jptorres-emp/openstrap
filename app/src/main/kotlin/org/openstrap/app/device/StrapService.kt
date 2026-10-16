package org.openstrap.app.device

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.openstrap.app.MainActivity
import org.openstrap.app.OpenStrapApp

/** Keeps the process alive while connected so syncing and live HR survive the app being backgrounded. */
class StrapService : LifecycleService() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val strap = (application as OpenStrapApp).container.strap
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification("Connecting…"),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0,
        )
        lifecycleScope.launch {
            // Live HR changes about once a second, and every post wakes the CPU and redraws the system UI.
            // Connection, battery and charging changes post at once; a bpm-only change waits for HR_REFRESH_MS.
            var status: String? = null
            var lastPost = 0L
            combine(strap.connection, strap.liveHeartRate) { c, hr -> c to hr }.collect { (c, hr) ->
                val state = when (c) {
                    is ConnectionState.Connected -> buildString {
                        append(if (c.charging) "Charging" else "Connected")
                        c.batteryPercent?.let { append(" · $it%") }
                    }
                    ConnectionState.Connecting -> "Connecting…"
                    ConnectionState.Authenticating -> "Authenticating…"
                    is ConnectionState.Failed -> "Connection failed"
                    else -> { stopSelf(); return@collect }
                }
                // The first bpm (or losing it) posts at once too.
                val key = state + (hr != null)
                val now = SystemClock.elapsedRealtime()
                if (key == status && now - lastPost < HR_REFRESH_MS) return@collect
                status = key
                lastPost = now
                val text = if (c is ConnectionState.Connected && hr != null) "$state · $hr bpm" else state
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
            }
        }
        return START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "Strap connection", NotificationManager.IMPORTANCE_LOW))
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("Helio Strap")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(open)
            .build()
    }

    companion object {
        private const val CHANNEL = "strap"
        private const val NOTIFICATION_ID = 1
        private const val HR_REFRESH_MS = 60_000L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, StrapService::class.java))
        }
    }
}
