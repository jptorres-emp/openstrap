package org.openstrap.app.ui.components

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/** The moments the app answers with touch (spec 3.6). Ordinary taps and the pull to sync ratchet aren't here. */
enum class HapticEvent {
    /** A scrub lands on a new point: a day, an hour, a stage. Throttled, see [Haptics.SCRUB_INTERVAL_MS]. */
    ScrubStep,

    /** A scrub crosses into or out of your normal, or reaches the highest or lowest point. */
    ScrubMark,

    /** The heart rate zone changes. */
    ZoneChange,

    /** A strength set is done. */
    SetDone,

    WorkoutStart,

    /** The hold to end a workout completes. */
    WorkoutEnd,

    PairingDone,

    /** A manual sync can't start, or fails. */
    SyncFailed,

    /** A range handle passes the detected start or end of a workout. */
    RangeMark,
}

/** The feedback for each [HapticEvent]. */
fun HapticEvent.feedbackType(): HapticFeedbackType = when (this) {
    HapticEvent.ScrubStep -> HapticFeedbackType.SegmentFrequentTick
    HapticEvent.ScrubMark, HapticEvent.ZoneChange, HapticEvent.SetDone, HapticEvent.RangeMark -> HapticFeedbackType.SegmentTick
    HapticEvent.WorkoutStart, HapticEvent.WorkoutEnd, HapticEvent.PairingDone -> HapticFeedbackType.Confirm
    HapticEvent.SyncFailed -> HapticFeedbackType.Reject
}

/** Lets a tick through at most once every [intervalMs]; times are any monotonic milliseconds. */
class TickThrottle(private val intervalMs: Long) {
    private var last: Long? = null

    /** Whether a tick at [nowMs] may play; if so, it counts as the last one. */
    fun allow(nowMs: Long): Boolean {
        val prev = last
        if (prev != null && nowMs - prev < intervalMs) return false
        last = nowMs
        return true
    }
}

/** Plays [HapticEvent]s, throttling scrub steps so a fast scrub doesn't turn into a buzz. */
class Haptics(private val feedback: HapticFeedback, private val now: () -> Long = SystemClock::uptimeMillis) {
    private val scrub = TickThrottle(SCRUB_INTERVAL_MS)

    fun perform(event: HapticEvent) {
        if (event == HapticEvent.ScrubStep && !scrub.allow(now())) return
        feedback.performHapticFeedback(event.feedbackType())
    }

    companion object {
        /** Scrub steps play at most this often. */
        const val SCRUB_INTERVAL_MS = 40L
    }
}

/** [Haptics] for this composition, on the platform's haptic feedback. */
@Composable
fun rememberHaptics(): Haptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) { Haptics(feedback) }
}
