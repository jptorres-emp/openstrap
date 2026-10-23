package org.openstrap.app.device

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import org.openstrap.app.MainActivity

/**
 * Zepp's "Wear Reminders" for the strap (codemap 07 §4 N5): a notification while the strap reports it is off-body,
 * cleared once it is back on.
 */
object WearReminder {
    enum class Action { Post, Clear, None }

    /**
     * What to do with the notification given the wear state ([worn]: null when unknown, e.g. the link is down), the
     * user's switch and whether it is [shown] now. It is posted once per off-body stretch, and cleared when the strap
     * is back on, the state is no longer known, or the switch is turned off.
     */
    fun decide(worn: Boolean?, enabled: Boolean, shown: Boolean): Action = when {
        worn == false && enabled -> if (shown) Action.None else Action.Post
        shown -> Action.Clear
        else -> Action.None
    }
}

/** Posts and clears the off-body notification on its own channel, as Zepp's `channel_device_wear` (07 §4 N5). */
class WearNotifier(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    @Volatile var shown = false
        private set

    init {
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Wear reminders", NotificationManager.IMPORTANCE_DEFAULT))
        // Left from before the app was stopped: whether the strap is still off isn't known now.
        manager.cancel(NOTIFICATION_ID)
    }

    fun apply(action: WearReminder.Action) {
        when (action) {
            WearReminder.Action.Post -> show()
            WearReminder.Action.Clear -> { manager.cancel(NOTIFICATION_ID); shown = false }
            WearReminder.Action.None -> Unit
        }
    }

    private fun show() {
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Data paused — your Helio Strap is off-body")
            .setContentText("Put it back on to resume monitoring.")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
        shown = true
    }

    private companion object {
        const val CHANNEL = "wear"
        /** Zepp's id for this notification. */
        const val NOTIFICATION_ID = 17
    }
}
