package org.openstrap.app.data

import java.time.LocalDate
import org.openstrap.protocol.services.StepTotals

/**
 * Today's step count from synced minutes plus the strap's live counter.
 *
 * A plausible live total is used as is (it also covers minutes another app already fetched). On firmware 3.25
 * the live total carries a large constant offset (11,368 steps with 1,836 m), while its increments stay right.
 * Then the counter is anchored to the synced history: each time the history total grows, the live reading
 * at that moment is remembered, and only the steps counted since are added on top.
 *
 * One counter serves the whole app ([shared]): Today and the Steps and Moment widgets read the same anchor, so they
 * agree, and coming back to the app keeps the steps counted since the last sync instead of starting again from it.
 */
class LiveSteps {
    private var anchorDay: LocalDate? = null
    private var anchoredHistory = -1
    private var liveAtAnchor = 0

    @Synchronized
    fun today(history: Int, live: StepTotals?, day: LocalDate = LocalDate.now()): Int {
        if (live == null) return history
        if (live.isPlausible) return maxOf(history, live.steps)
        // Re-anchor on a new day, on newer synced data (a day's history only grows), and when the counter goes
        // backwards (midnight reset or strap restart). A caller still holding older history counts from the newer anchor.
        if (day != anchorDay || history > anchoredHistory || live.steps < liveAtAnchor) {
            anchorDay = day
            anchoredHistory = history
            liveAtAnchor = live.steps
        }
        return maxOf(history, anchoredHistory) + (live.steps - liveAtAnchor)
    }

    companion object {
        /** The app's one counter. */
        val shared = LiveSteps()
    }
}
