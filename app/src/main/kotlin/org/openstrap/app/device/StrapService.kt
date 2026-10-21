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
import org.openstrap.app.R
import org.openstrap.app.data.WorkoutRecorder
import org.openstrap.app.data.Workouts
import org.openstrap.app.ui.workout.ZONE_COLORS
import org.openstrap.app.ui.workout.ZONE_NAMES
import androidx.compose.ui.graphics.toArgb

/**
 * Keeps the process alive while connected so syncing and live HR survive the app being backgrounded, and for the
 * whole of a workout, connected or not. During a workout its notification is the workout's: sport, a running clock,
 * heart rate and zone, and Pause or Resume.
 */
class StrapService : LifecycleService() {

    private var observing = false
    /** What the notification shows now; each start command must put it back, not a placeholder over a workout. */
    private var shown: Notification? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val container = (application as OpenStrapApp).container
        val strap = container.strap
        val workout = container.workout
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, shown ?: notification("Helio Strap", "Connecting…"),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0,
        )
        when (intent?.action) {
            ACTION_PAUSE -> workout.pause()
            ACTION_RESUME -> workout.resume()
            ACTION_END -> workout.end()
            ACTION_END_AT -> workout.endAtPrompt()
            ACTION_KEEP -> workout.keepGoing()
        }
        val recording = workout.state.value is WorkoutState.Recording
        if (observing) return if (recording) START_STICKY else START_NOT_STICKY
        observing = true
        lifecycleScope.launch {
            // Live HR changes about once a second, and every post wakes the CPU and redraws the system UI.
            // Connection, battery, charging and workout changes post at once; a bpm-only change waits for the refresh.
            var status: String? = null
            var lastPost = 0L
            // Until the session has checked for a workout left recording (a restart after the process died), stay up.
            combine(strap.connection, strap.liveHeartRate, workout.state, workout.restored) { c, hr, w, restored ->
                Status(c, hr, w, restored)
            }.collect { (c, hr, w, restored) ->
                val state = when (c) {
                    is ConnectionState.Connected -> buildString {
                        append(if (c.charging) "Charging" else "Connected")
                        c.batteryPercent?.let { append(" · $it%") }
                    }
                    ConnectionState.Connecting -> "Connecting…"
                    ConnectionState.Authenticating -> "Authenticating…"
                    is ConnectionState.Failed -> "Connection failed"
                    else -> if (w is WorkoutState.Recording || !restored) "Strap not connected" else { stopSelf(); return@collect }
                }
                val key = state + (hr != null) + (w as? WorkoutState.Recording)
                val now = SystemClock.elapsedRealtime()
                val refresh = if (w is WorkoutState.Recording) WORKOUT_REFRESH_MS else HR_REFRESH_MS
                if (key == status && now - lastPost < refresh) return@collect
                status = key
                lastPost = now
                val n = if (w is WorkoutState.Recording) workoutNotification(w, hr.takeIf { c is ConnectionState.Connected }, state)
                else notification("Helio Strap", if (c is ConnectionState.Connected && hr != null) "$state · $hr bpm" else state)
                shown = n
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, n)
            }
        }
        return if (recording) START_STICKY else START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Strap connection", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(WORKOUT_CHANNEL, "Workout", NotificationManager.IMPORTANCE_LOW))
    }

    // Its own request code: a pending intent keeps the flags it was first made with, and an older one without
    // SINGLE_TOP would only bring the app forward, never delivering the extra that opens the live screen.
    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, REQUEST_OPEN,
        Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_SHOW_WORKOUT, true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun notification(title: String, text: String): Notification = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_stat_openstrap)
        .setContentTitle(title)
        .setContentText(text)
        .setOngoing(true)
        .setContentIntent(openApp())
        .build()

    /**
     * The workout as a Live Update (Android 16): promoted, it shows as a chip by the status-bar clock with the
     * running time, and on the lock screen. The clock runs on its own (a chronometer), so it needs no posts to tick.
     * The five zones are the progress bar's segments, with the bar filled up to where the heart rate sits.
     */
    private fun workoutNotification(r: WorkoutState.Recording, bpm: Int?, connection: String): Notification {
        r.endPrompt?.let { return stoppedNotification(r, it) }
        val w = r.workout
        val now = System.currentTimeMillis()
        val active = WorkoutRecorder.activeSeconds(w, now / 1000)
        val zone = WorkoutRecorder.liveZone(bpm, r.maxHeartRate)
        val text = when {
            r.paused -> "Paused at ${clock(active)}"
            bpm != null && zone != null -> "$bpm bpm · Zone ${zone + 1} ${ZONE_NAMES[zone]} · ${zoneRange(zone, r.maxHeartRate)}"
            else -> "$connection · recording continues"
        }
        val toggle = if (r.paused) {
            NotificationCompat.Action(0, "Resume", service(ACTION_RESUME))
        } else {
            NotificationCompat.Action(0, "Pause", service(ACTION_PAUSE))
        }
        val bar = NotificationCompat.ProgressStyle()
            .setStyledByProgress(false)
            .setProgressSegments(ZONE_COLORS.map { NotificationCompat.ProgressStyle.Segment(ZONE_SPAN).setColor(it.toArgb()) })
            // `also`, not `apply`: ProgressStyle has an apply() of its own. The tracker, a white circle as on the live
            // screen's zone bar, marks where the heart rate sits; a progress point would draw as a square on some phones.
            .also { style ->
                if (bpm != null && !r.paused) {
                    style.setProgress((WorkoutRecorder.zonePosition(bpm, r.maxHeartRate) * ZONE_SPAN * ZONE_COLORS.size).toInt())
                    style.setProgressTrackerIcon(tracker)
                }
            }
        return NotificationCompat.Builder(this, WORKOUT_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_openstrap)
            .setContentTitle(Workouts.sportName(w.sportType))
            .setContentText(text)
            .setStyle(bar)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setRequestPromotedOngoing(true)
            .apply { if (r.paused) setShortCriticalText("Paused") }
            .setContentIntent(openApp())
            .addAction(toggle)
            .addAction(NotificationCompat.Action(0, "End", service(ACTION_END)))
            .setShowWhen(true)
            .setUsesChronometer(!r.paused)
            .setWhen(if (r.paused) now else now - active * 1000)
            .build()
    }

    /** "Looks like you've stopped": the same notification, asking whether to end where the wearer stopped. */
    private fun stoppedNotification(r: WorkoutState.Recording, at: Long): Notification {
        val time = java.time.Instant.ofEpochSecond(at).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
        val sport = Workouts.sportName(r.workout.sportType).lowercase()
        val text = if (r.inCar) "You last moved at $time. End the $sport there, so the drive isn't counted?"
        else "Your heart rate has been back near resting since $time. End the $sport at $time?"
        return NotificationCompat.Builder(this, WORKOUT_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_openstrap)
            .setContentTitle(if (r.inCar) "Looks like you're in a car" else "Looks like you've stopped")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setRequestPromotedOngoing(true)
            .setShortCriticalText("End?")
            .setContentIntent(openApp())
            .addAction(NotificationCompat.Action(0, "End at $time", service(ACTION_END_AT)))
            .addAction(NotificationCompat.Action(0, "Keep going", service(ACTION_KEEP)))
            .build()
    }

    /** A white dot ringed in the notification's dark grey, drawn once. */
    private val tracker: androidx.core.graphics.drawable.IconCompat by lazy {
        val size = (24 * resources.displayMetrics.density).toInt()
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.color = 0xFF1C1C1E.toInt()
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.color = android.graphics.Color.WHITE
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 3 * resources.displayMetrics.density, paint)
        androidx.core.graphics.drawable.IconCompat.createWithBitmap(bitmap)
    }

    private fun service(action: String): PendingIntent =
        PendingIntent.getService(this, action.hashCode(), Intent(this, StrapService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE)

    private data class Status(val connection: ConnectionState, val heartRate: Int?, val workout: WorkoutState, val restored: Boolean)

    companion object {
        private const val CHANNEL = "strap"
        private const val WORKOUT_CHANNEL = "workout"
        private const val NOTIFICATION_ID = 1
        private const val HR_REFRESH_MS = 60_000L
        private const val WORKOUT_REFRESH_MS = 10_000L
        private const val ACTION_PAUSE = "org.openstrap.app.workout.PAUSE"
        private const val ACTION_RESUME = "org.openstrap.app.workout.RESUME"
        private const val ACTION_END = "org.openstrap.app.workout.END"
        private const val ACTION_END_AT = "org.openstrap.app.workout.END_AT"
        private const val ACTION_KEEP = "org.openstrap.app.workout.KEEP"
        /** Each zone's length on the notification's progress bar. */
        private const val ZONE_SPAN = 20
        private const val REQUEST_OPEN = 2

        private fun zoneRange(zone: Int, maxHr: Int) =
            if (zone == 0) "under ${60 * maxHr / 100} bpm" else "${(50 + zone * 10) * maxHr / 100}–${if (zone == 4) maxHr else (60 + zone * 10) * maxHr / 100 - 1} bpm"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, StrapService::class.java))
        }

        private fun clock(seconds: Long) = if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
        else "%d:%02d".format(seconds / 60, seconds % 60)
    }
}
