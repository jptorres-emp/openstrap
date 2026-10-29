package org.openstrap.app.device

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import org.openstrap.app.MainActivity
import org.openstrap.app.R
import org.openstrap.app.l10n.tr

/**
 * The move reminder's notification ([org.openstrap.app.data.MoveReminder]): the strap's buzz is the nudge, so it's
 * quiet on the phone, and it goes away by itself after a while or once tapped.
 */
class MoveNotifier(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        manager.createNotificationChannel(NotificationChannel(CHANNEL, tr("Move reminders", "Lembretes para te mexeres"), NotificationManager.IMPORTANCE_LOW))
    }

    fun show(minutes: Int) {
        val open = PendingIntent.getActivity(context, NOTIFICATION_ID, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_openstrap)
            .setContentTitle(tr("Time to move", "Hora de te mexeres"))
            .setContentText(tr(
                "Almost no steps in $minutes min. Get up and walk for 2 or 3 minutes.",
                "Quase nenhum passo em $minutes min. Levanta-te e anda 2 ou 3 minutos.",
            ))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setTimeoutAfter(TIMEOUT_MS)
            .setContentIntent(open)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL = "move"
        private const val NOTIFICATION_ID = 18
        private const val TIMEOUT_MS = 15 * 60_000L
    }
}
