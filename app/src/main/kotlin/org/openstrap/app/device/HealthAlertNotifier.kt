package org.openstrap.app.device

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import org.openstrap.app.MainActivity
import org.openstrap.app.data.BodySignalsCopy
import org.openstrap.protocol.services.HealthAlert
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Shows the strap's own health alerts (0x001D `0D`) as notifications, like Zepp's "Health Reminders" channel
 * (07 §4 N6). The strap only raises them for the alerts switched on in its settings.
 */
class HealthAlertNotifier(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Health alerts", NotificationManager.IMPORTANCE_HIGH))
    }

    fun show(alert: HealthAlert) {
        val now = System.currentTimeMillis() / 1000
        // The timestamp unit isn't confirmed on hardware (03 §7); fall back to now if it doesn't look like seconds.
        val at = alert.timestamp.takeIf { it in MIN_EPOCH..now + 86_400 } ?: now
        val (title, text) = when (alert.type) {
            HealthAlert.Type.HIGH_HEART_RATE -> "High heart rate" to "Your heart rate reached ${alert.value} bpm."
            HealthAlert.Type.LOW_HEART_RATE -> "Low heart rate" to "Your heart rate dropped to ${alert.value} bpm."
            HealthAlert.Type.HIGH_STRESS -> "High stress" to "Your stress is at ${alert.value}. Take a moment to breathe."
            HealthAlert.Type.LOW_SPO2 -> "Low blood oxygen" to "Your blood oxygen was ${alert.value}%."
            HealthAlert.Type.IRREGULAR_RHYTHM ->
                "Irregular heart rhythm" to "The strap noticed an irregular heart rhythm at ${TIME.format(Instant.ofEpochSecond(at))}."
        }
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText(text)
            .setWhen(at * 1000)
            .setShowWhen(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        // One notification per kind, so a repeat replaces the last one instead of stacking up.
        manager.notify(NOTIFICATION_BASE + alert.type.code, notification)
    }

    /**
     * OpenStrap's own body-signal alert, in the wellness copy of [BodySignalsCopy]: it never names a condition or a
     * threshold. One notification, replaced by the next.
     */
    fun showBodySignals(signals: List<String>, nights: Int) {
        val text = BodySignalsCopy.pushText(signals, nights)
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(BodySignalsCopy.TITLE)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        manager.notify(BODY_SIGNALS_ID, notification)
    }

    private companion object {
        const val CHANNEL = "health"
        const val BODY_SIGNALS_ID = 99
        const val NOTIFICATION_BASE = 100
        const val MIN_EPOCH = 1_704_067_200L // 2024-01-01
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
    }
}
