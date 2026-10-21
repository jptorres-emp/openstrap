package org.openstrap.app.device

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.openstrap.app.data.settings.BuzzPattern
import org.openstrap.app.data.settings.SettingsStore

/**
 * Buzzes the strap for phone calls and notifications. The strap has no notification module (Zepp leaves the
 * feature off for it), so a ringing call uses its find-device buzz until the call is answered or ends, and a
 * notification plays a short motor pattern (docs/protocol/01 §5.8). Both follow Do Not Disturb.
 */
class PhoneAlerts(
    private val context: Context,
    private val settings: SettingsStore,
    private val strap: StrapController,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val telephony = context.getSystemService(TelephonyManager::class.java)

    @Volatile private var callsOn = false
    @Volatile var notificationsOn = false
        private set
    @Volatile private var pattern = BuzzPattern.TWO_SHORT

    /** Re-checked by [refresh]; the screen calls it on resume, after the user may have granted access. */
    val callPermission = MutableStateFlow(hasCallPermission())
    val notificationAccess = MutableStateFlow(hasNotificationAccess())

    private var callListener: Any? = null
    private var ringing: Job? = null
    private var lastBuzz = 0L

    init {
        scope.launch {
            combine(settings.buzzOnCalls, settings.buzzOnNotifications, callPermission) { c, n, _ -> c to n }.collect { (c, n) ->
                callsOn = c
                notificationsOn = n
                updateCallListener()
            }
        }
        scope.launch { settings.notificationPattern.collect { pattern = it } }
    }

    fun refresh() {
        callPermission.value = hasCallPermission()
        notificationAccess.value = hasNotificationAccess()
    }

    fun hasCallPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

    fun hasNotificationAccess(): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
        val me = ComponentName(context, StrapNotificationListener::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    /** Plays [p] once, so the pattern picker can be felt. */
    suspend fun preview(p: BuzzPattern) = strap.buzz(p.millis)

    // ---- Calls ----

    private fun updateCallListener() {
        val want = callsOn && hasCallPermission()
        if (want == (callListener != null)) return
        // Callbacks are registered and removed on the main thread, which PhoneStateListener needs (it binds a Looper).
        ContextCompat.getMainExecutor(context).execute {
            if (want && callListener == null) {
                log("call listener on")
                callListener = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                        override fun onCallStateChanged(state: Int) = onCallState(state)
                    }.also { telephony.registerTelephonyCallback(context.mainExecutor, it) }
                } else {
                    @Suppress("DEPRECATION")
                    object : PhoneStateListener() {
                        @Deprecated("Deprecated in Java")
                        override fun onCallStateChanged(state: Int, phoneNumber: String?) = onCallState(state)
                    }.also { @Suppress("DEPRECATION") telephony.listen(it, PhoneStateListener.LISTEN_CALL_STATE) }
                }
            } else if (!want) {
                val l = callListener
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && l is TelephonyCallback) {
                    telephony.unregisterTelephonyCallback(l)
                } else if (l is PhoneStateListener) {
                    @Suppress("DEPRECATION") telephony.listen(l, PhoneStateListener.LISTEN_NONE)
                }
                callListener = null
                stopRinging()
            }
        }
    }

    @Volatile private var phoneRinging = false

    private fun onCallState(state: Int) {
        log("call state $state (calls ${if (callsOn) "on" else "off"})")
        phoneRinging = state == TelephonyManager.CALL_STATE_RINGING
        if (phoneRinging) reconnectStrap()
        if (phoneRinging && callsOn) startRinging() else stopRinging()
    }

    private fun startRinging() {
        if (ringing?.isActive == true) return
        ringing = scope.launch {
            val allowed = dndAllowsAnyCaller()
            val filter = context.getSystemService(NotificationManager::class.java).currentInterruptionFilter
            log("call ringing: ${if (allowed) "buzz" else "held back by Do Not Disturb"} (interruption filter $filter)")
            if (!allowed) return@launch
            runCatching { strap.startBuzzing() }.onFailure { return@launch }
            // A missed call can end without a state change reaching us; don't leave the strap buzzing.
            delay(MAX_RING_MS)
            runCatching { strap.stopBuzzing() }
        }
    }

    private fun stopRinging() {
        val job = ringing ?: return
        ringing = null
        job.cancel()
        scope.launch { runCatching { strap.stopBuzzing() } }
    }

    /**
     * True unless Do Not Disturb would block a call from an unknown caller. Exceptions for starred contacts and repeat
     * callers aren't followed: telling them apart needs the caller's number, which needs the call-log permission.
     * The call notification's ranking doesn't help: on Samsung it said "blocked" for a repeat caller the phone let
     * ring (2026-10-21).
     */
    private fun dndAllowsAnyCaller(): Boolean {
        val nm = context.getSystemService(NotificationManager::class.java)
        return when (nm.currentInterruptionFilter) {
            NotificationManager.INTERRUPTION_FILTER_ALL, NotificationManager.INTERRUPTION_FILTER_UNKNOWN -> true
            NotificationManager.INTERRUPTION_FILTER_PRIORITY -> runCatching {
                val p = nm.notificationPolicy
                p.priorityCategories and NotificationManager.Policy.PRIORITY_CATEGORY_CALLS != 0 &&
                    p.priorityCallSenders == NotificationManager.Policy.PRIORITY_SENDERS_ANY
            }.getOrDefault(false)
            else -> false
        }
    }

    private fun log(message: String) = (strap as? StrapManager)?.log(message)

    /**
     * Reconnects a paired strap that isn't connected. Android can restart the process just to deliver a call or a
     * notification (or to bind [StrapNotificationListener]); this alert is lost, but the next one reaches the strap.
     * A no-op while connected or connecting, and when the strap turned the key down (retrying can't help).
     */
    fun reconnectStrap() {
        val c = strap.connection.value
        if (c is ConnectionState.Connected || (c as? ConnectionState.Failed)?.keyRejected == true) return
        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return
        strap.autoConnect()
    }

    // ---- Notifications ----

    /** Called by [StrapNotificationListener] for every posted notification. */
    fun onNotification(sbn: StatusBarNotification, ranking: NotificationListenerService.Ranking?, update: Boolean) {
        reconnectStrap()
        // A pattern played on the motor cuts off the call's continuous buzz (seen 2026-10-20), so stay quiet while it rings.
        if (phoneRinging) return
        if (!notificationsOn || !shouldBuzz(sbn, ranking, update)) return
        val now = SystemClock.elapsedRealtime()
        synchronized(this) {
            if (now - lastBuzz < MIN_GAP_MS) return
            lastBuzz = now
        }
        scope.launch { runCatching { strap.buzz(pattern.millis) } }
    }

    private fun shouldBuzz(sbn: StatusBarNotification, ranking: NotificationListenerService.Ranking?, update: Boolean): Boolean {
        val n = sbn.notification
        if (sbn.packageName == context.packageName || sbn.isOngoing) return false
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
        // An update that the app marked "alert once" makes no sound on the phone either.
        if (update && n.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0) return false
        // A phone call that arrived before its ringing state is handled by the call-state listener above.
        if (n.category == Notification.CATEGORY_CALL && sbn.packageName == defaultDialer()) return false
        if (ranking != null) {
            // Follow the phone: nothing during Do Not Disturb that the phone itself would hold back, and nothing
            // from channels the user made silent.
            if (!ranking.matchesInterruptionFilter()) return false
            if (ranking.importance < NotificationManager.IMPORTANCE_DEFAULT) return false
            if (ranking.isSuspended) return false
        }
        return true
    }

    private fun defaultDialer(): String? = runCatching { context.getSystemService(TelecomManager::class.java).defaultDialerPackage }.getOrNull()

    private companion object {
        const val MAX_RING_MS = 60_000L
        /** A burst of notifications (a group chat) buzzes once. */
        const val MIN_GAP_MS = 5_000L
    }
}
