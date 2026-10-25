package org.openstrap.app.device

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.openstrap.app.data.MomentInputs
import org.openstrap.app.widget.WidgetKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The one place that asks home screen widgets to refresh (widgets spec 7). Everything that changes what a widget
 * shows calls [changed]; nothing refreshes on a timer it doesn't need. Calls are cheap, return at once and never
 * throw: the work runs on its own scope and failures are logged.
 *
 * Only widgets that are placed are updated, and the moment and midnight alarms are set only while one is. Until a
 * widget is registered in [widgets] nothing is placed, so all of this is a no-op.
 */
object WidgetUpdater {
    /** A widget's receiver, which the launcher knows it by, and its Glance widget. */
    class Registration(val receiver: Class<out GlanceAppWidgetReceiver>, val widget: () -> GlanceAppWidget)

    /** Each widget, once it exists: step 3 on adds `WidgetKind.Recovery to Registration(...)` and so on. */
    private val widgets: Map<WidgetKind, Registration> = emptyMap()

    /** Steps change by the second while the strap is connected; the widget follows at most this often. */
    const val STEPS_EVERY_MS = 15 * 60_000L

    /** Without exact alarms a moment may change this late on a widget (spec 7: a few minutes late is fine). */
    const val ALARM_WINDOW_MS = 5 * 60_000L

    private const val TAG = "OpenStrap"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val steps = UpdateThrottle(STEPS_EVERY_MS)

    /** Refreshes the placed widgets of [kinds]. */
    fun changed(context: Context, kinds: Set<WidgetKind>) {
        val app = context.applicationContext
        scope.launch { update(app, kinds) }
    }

    /** Refreshes every placed widget: a sync ended, a new day, the strap's state changed. */
    fun changedAll(context: Context) = changed(context, WidgetKind.entries.toSet())

    /** Today's steps moved: the Steps widget follows, at most every [STEPS_EVERY_MS]. */
    fun stepsChanged(context: Context) {
        val app = context.applicationContext
        val wait = steps.request(System.currentTimeMillis()) ?: return
        if (wait == 0L) { changed(app, setOf(WidgetKind.Steps)); return }
        scope.launch {
            delay(wait)
            steps.ran(System.currentTimeMillis())
            update(app, setOf(WidgetKind.Steps))
        }
    }

    /** The widgets on the home screen, any page: a kind counts once its receiver has an id (spec 3.1). */
    fun placed(context: Context): Set<WidgetKind> = try {
        val manager = AppWidgetManager.getInstance(context) ?: return emptySet()
        widgets.filter { (_, r) -> manager.getAppWidgetIds(ComponentName(context, r.receiver)).isNotEmpty() }.keys
    } catch (e: Exception) {
        Log.w(TAG, "widgets: placed check failed: $e")
        emptySet()
    }

    /**
     * Sets the alarm for the moment's next change ([MomentInputs.nextChange]), from the inputs the Moment widget just
     * rendered with, so the two can't drift apart. When it fires Moment refreshes, and its render schedules the next.
     * Clears the alarm when there's no next change or no Moment widget.
     */
    fun scheduleMoment(context: Context, inputs: MomentInputs, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()) {
        try {
            val at = if (WidgetKind.Moment in placed(context)) inputs.nextChange(now, zone) else null
            if (at == null) cancel(context, ACTION_MOMENT) else setAlarm(context, ACTION_MOMENT, at.toEpochMilli())
        } catch (e: Exception) {
            Log.w(TAG, "widgets: moment alarm not set: $e")
        }
    }

    /** Sets the alarm for the next local midnight while any widget is placed, and clears it when none is. */
    fun scheduleMidnight(context: Context, zone: ZoneId = ZoneId.systemDefault()) {
        try {
            if (placed(context).isEmpty()) { cancel(context, ACTION_MIDNIGHT); return }
            val midnight = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            setAlarm(context, ACTION_MIDNIGHT, midnight)
        } catch (e: Exception) {
            Log.w(TAG, "widgets: midnight alarm not set: $e")
        }
    }

    internal suspend fun update(context: Context, kinds: Set<WidgetKind>) {
        val shown = placed(context).intersect(kinds)
        for (kind in shown) {
            try {
                widgets.getValue(kind).widget().updateAll(context)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.w(TAG, "widgets: $kind not updated: $e")
            }
        }
        if (shown.isNotEmpty()) scheduleMidnight(context)
    }

    /**
     * Exact while the app may set exact alarms (Android 11 and older always, later with "Alarms and reminders"),
     * otherwise inside [ALARM_WINDOW_MS]. Never `USE_EXACT_ALARM`: that's for clocks and calendars (spec 7).
     */
    private fun setAlarm(context: Context, action: String, atMs: Long) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = pendingIntent(context, action)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        try {
            if (exact) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pending)
            else alarms.setWindow(AlarmManager.RTC_WAKEUP, atMs, ALARM_WINDOW_MS, pending)
        } catch (e: SecurityException) {
            // The exact-alarm access was taken back between the check and the call.
            alarms.setWindow(AlarmManager.RTC_WAKEUP, atMs, ALARM_WINDOW_MS, pending)
        }
    }

    private fun cancel(context: Context, action: String) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context, action))
    }

    private fun pendingIntent(context: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
        context, if (action == ACTION_MOMENT) 1 else 2,
        Intent(context, WidgetAlarmReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    internal const val ACTION_MOMENT = "org.openstrap.app.widget.MOMENT"
    internal const val ACTION_MIDNIGHT = "org.openstrap.app.widget.MIDNIGHT"
}

/** Where [WidgetUpdater]'s alarms land: the moment changed, or a new day began. Not a widget, and not exported. */
class WidgetAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kinds = when (intent.action) {
            WidgetUpdater.ACTION_MOMENT -> setOf(WidgetKind.Moment)
            WidgetUpdater.ACTION_MIDNIGHT -> WidgetKind.entries.toSet()
            else -> return
        }
        val app = context.applicationContext
        val done = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                WidgetUpdater.update(app, kinds)
                // The update sets the next midnight only when something was placed; this one has passed either way.
                if (intent.action == WidgetUpdater.ACTION_MIDNIGHT) WidgetUpdater.scheduleMidnight(app)
            } catch (e: Exception) {
                Log.w("OpenStrap", "widgets: alarm ${intent.action} failed: $e")
            } finally {
                done.finish()
            }
        }
    }
}

/**
 * At most one update every [everyMs], and none lost: a request inside the gap waits for its end, and requests while
 * one waits join it.
 */
class UpdateThrottle(private val everyMs: Long) {
    private var last: Long? = null
    private var waiting = false

    /** A request at [now] (ms): 0 to update now, the ms to wait before updating, or null when one already waits. */
    @Synchronized
    fun request(now: Long): Long? {
        if (waiting) return null
        val l = last
        if (l == null || now - l >= everyMs) { last = now; return 0 }
        waiting = true
        return l + everyMs - now
    }

    /** The waiting update ran at [now] (ms). */
    @Synchronized
    fun ran(now: Long) {
        last = now
        waiting = false
    }
}
