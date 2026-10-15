package org.openstrap.app.device

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
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
            combine(strap.connection, strap.liveHeartRate) { c, hr -> c to hr }.collect { (c, hr) ->
                val text = when (c) {
                    is ConnectionState.Connected -> buildString {
                        append("Connected")
                        c.batteryPercent?.let { append(" · $it%") }
                        hr?.let { append(" · $it bpm") }
                    }
                    ConnectionState.Connecting -> "Connecting…"
                    ConnectionState.Authenticating -> "Authenticating…"
                    is ConnectionState.Failed -> "Connection failed"
                    else -> { stopSelf(); return@collect }
                }
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
            }
        }
        return START_NOT_STICKY
    }

    private fun notification(text: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Strap connection", NotificationManager.IMPORTANCE_LOW))
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

        fun start(context: Context) {
            context.startForegroundService(Intent(context, StrapService::class.java))
        }
    }
}
