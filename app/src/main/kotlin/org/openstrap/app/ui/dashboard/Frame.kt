package org.openstrap.app.ui.dashboard

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import org.openstrap.app.data.Moment
import org.openstrap.app.device.ConnectionState
import org.openstrap.app.device.SyncState
import org.openstrap.app.ui.glow.GlowMood
import org.openstrap.app.ui.glow.LightAngle

/*
 * The frame around every tab (spec 3): what the header says about the day and the strap, and how the tabs answer
 * taps and back. Plain functions, so the rules are tested without a screen.
 */

/** The header's title for [date] on Today: "Today", "Yesterday", or the weekday ("Tuesday"). */
fun dayTitle(date: LocalDate, today: LocalDate = LocalDate.now(), locale: Locale = Locale.getDefault()): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
}

/**
 * The header's title on Today (spec 3.1): the [moment] for today, [dayTitle] for a past day, which keeps its name.
 * After a workout it names the sport when it can ([sportType], "After your ride").
 */
fun todayTitle(moment: Moment, date: LocalDate, sportType: Int? = null, today: LocalDate = LocalDate.now(), locale: Locale = Locale.getDefault()): String =
    if (date < today) dayTitle(date, today, locale) else when (moment) {
        Moment.During -> "Workout"
        Moment.After -> "After your ${sportType?.let(::sportNoun) ?: "workout"}"
        Moment.WorkoutWindow -> "Workout time"
        Moment.Waking -> "Waking up"
        Moment.Night -> "Night"
        Moment.Evening -> "Evening"
        Moment.Midday -> "Midday"
    }

/** A sport as a short noun ("run", "ride"), for "After your run"; null where none reads well, so it says "workout". */
internal fun sportNoun(sportType: Int): String? = when (sportType) {
    1, 2, 11 -> "run"
    3, 40, 215 -> "walk"
    4, 8, 143 -> "ride"
    6, 7 -> "swim"
    15, 224 -> "hike"
    23 -> "row"
    else -> null
}

/** The screen's light for a moment (spec 2.6): dimmed in the evening, almost unlit at night. */
fun momentMood(moment: Moment): GlowMood = when (moment) {
    Moment.Evening -> GlowMood.Dim
    Moment.Night -> GlowMood.Night
    else -> GlowMood.Day
}

/**
 * Where the Today hero's light comes from for a moment (spec 2.5): low on the left on waking, up on the right before a
 * workout, overhead at midday, low on the right in the evening, and below after a workout and at night. During a
 * workout the hero keeps its own light (null).
 */
fun heroLightAngle(moment: Moment): Float? = when (moment) {
    Moment.Waking -> LightAngle.LowerLeft
    Moment.WorkoutWindow -> LightAngle.UpperRight
    Moment.Midday -> LightAngle.Above
    Moment.Evening -> LightAngle.LowerRight
    Moment.After, Moment.Night -> LightAngle.Below
    Moment.During -> null
}

/** The day under the header's title: "Thu 23 Oct". */
fun dateLine(date: LocalDate, locale: Locale = Locale.getDefault()): String =
    date.format(DateTimeFormatter.ofPattern("EEE d MMM", locale))

/** A neighbouring day's short name, peeking in beside the title: "Wed". */
fun peekDay(date: LocalDate, locale: Locale = Locale.getDefault()): String =
    date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)

/**
 * What the strap chip in the header shows: a [dot] and, only when they matter, a few words ([text] may be empty).
 * [description] is read out by a screen reader.
 */
data class StrapChip(val text: String, val dot: Dot, val description: String) {
    enum class Dot {
        /** Connected and all is well. */
        Fine,

        /** Connected, battery at or under [LOW_BATTERY]. */
        Low,

        /** Connected and charging. */
        Charging,

        /** Something needs a look: not synced, or not seen for a while. Amber. */
        Warn,

        /** Nothing to report yet (connecting), or nothing paired. Dim. */
        Quiet,
    }

    companion object {
        const val LOW_BATTERY = 15
    }
}

/** Connecting for longer than this after the strap was last seen shows how long ago that was, instead of a quiet dot. */
private const val CONNECTING_GRACE_S = 120L

/** For this long after the app's process starts, the service is still reconnecting: no news yet either way. */
const val STARTUP_GRACE_S = 120L

/**
 * The chip for the strap's state (spec 3.1): the battery and a dot when all is well, words only when they matter.
 * [lastSeen] is when the strap was last connected (epoch seconds), null when this run hasn't seen it. [sinceStart] is
 * how long the app's process has been running (seconds): during its first [STARTUP_GRACE_S] a strap that isn't
 * connected yet is still being reconnected, so it shows a quiet dot like a brief connect.
 */
fun strapChip(paired: Boolean, connection: ConnectionState, sync: SyncState, lastSeen: Long?, now: Long, sinceStart: Long = Long.MAX_VALUE): StrapChip {
    if (!paired || connection == ConnectionState.NotPaired) return StrapChip("Not paired", StrapChip.Dot.Quiet, "No strap paired")
    if (connection !is ConnectionState.Connected && sinceStart < STARTUP_GRACE_S) return StrapChip("", StrapChip.Dot.Quiet, "Connecting to the strap")
    val ago = lastSeen?.let { now - it }
    val unseen = if (ago != null) {
        val text = agoText(ago)
        StrapChip(text, StrapChip.Dot.Warn, "Strap last seen $text")
    } else StrapChip("Not connected", StrapChip.Dot.Warn, "Strap not connected")
    return when (connection) {
        is ConnectionState.Connected -> {
            val battery = connection.batteryPercent
            when {
                sync is SyncState.Failed -> StrapChip("Not synced", StrapChip.Dot.Warn, "Strap connected, not synced")
                connection.charging && battery != null -> StrapChip("Charging $battery%", StrapChip.Dot.Charging, "Strap charging, battery $battery percent")
                battery == null -> StrapChip("", StrapChip.Dot.Fine, "Strap connected")
                battery <= StrapChip.LOW_BATTERY -> StrapChip("$battery%", StrapChip.Dot.Low, "Strap connected, battery low, $battery percent")
                else -> StrapChip("$battery%", StrapChip.Dot.Fine, "Strap connected, battery $battery percent")
            }
        }
        // A moment's connecting (the app coming to the front) isn't news; a strap gone for minutes is.
        ConnectionState.Connecting, ConnectionState.Authenticating ->
            if (ago != null && ago >= CONNECTING_GRACE_S) unseen else StrapChip("", StrapChip.Dot.Quiet, "Connecting to the strap")
        else -> unseen
    }
}

/** "Just now", "18 min ago", "3 h ago", "2 days ago". */
fun agoText(seconds: Long): String {
    val minutes = seconds.coerceAtLeast(0) / 60
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 24 * 60 -> "${minutes / 60} h ago"
        minutes < 48 * 60 -> "1 day ago"
        else -> "${minutes / (24 * 60)} days ago"
    }
}

/** What tapping the tab that's already showing does (spec 3.3). */
enum class Reselect { ScrollToTop, PopToRoot }

/**
 * Which tab is showing, and the one before it for back (spec 3.3). [reselected] is set by a tap on the current tab
 * that scrolled it to the top, so the next tap pops its stack; anything else that moves clears it.
 */
data class TabNav(val current: Int = HOME, val previous: Int? = null, val reselected: Boolean = false) {
    /** Another tab: it becomes current, and back returns here. Every tab keeps its own stack. */
    fun select(tab: Int): TabNav = if (tab == current) this else TabNav(tab, current)

    /**
     * The pager came to rest on [page]. The tab is the one owner of what shows and the pager follows it, so only a
     * finished [swiped] gesture moves the tab; the pager's own animation to a tapped tab never writes back.
     */
    fun settled(page: Int, swiped: Boolean): TabNav = if (swiped) select(page) else this

    /** Back from the current tab's root: the tab before it, then Today. Null on Today: back leaves the app. */
    fun back(): TabNav? = when {
        current == HOME -> null
        previous != null && previous != HOME && previous != current -> TabNav(previous, null)
        else -> TabNav(HOME, null)
    }

    /**
     * A tap on the current tab, whose stack is [depth] screens deep: the first scrolls to the top, the next pops to
     * the root. With nothing pushed there's nothing to pop, so it scrolls again.
     */
    fun reselect(depth: Int): Pair<TabNav, Reselect> =
        if (reselected && depth > 0) copy(reselected = false) to Reselect.PopToRoot
        else copy(reselected = true) to Reselect.ScrollToTop

    /** Something else happened on the tab (a screen opened or closed): the next tap scrolls again first. */
    fun moved(): TabNav = if (reselected) copy(reselected = false) else this

    companion object {
        /** Today. */
        const val HOME = 0

        /** Keeps a [TabNav] across process death. */
        val Saver: androidx.compose.runtime.saveable.Saver<TabNav, IntArray> = androidx.compose.runtime.saveable.Saver(
            save = { intArrayOf(it.current, it.previous ?: -1) },
            restore = { TabNav(it[0], it[1].takeIf { p -> p >= 0 }) },
        )
    }
}
