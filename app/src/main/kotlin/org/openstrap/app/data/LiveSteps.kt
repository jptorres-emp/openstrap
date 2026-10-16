package org.openstrap.app.data

import org.openstrap.protocol.services.StepTotals

/**
 * Today's step count from synced minutes plus the strap's live counter.
 *
 * A plausible live total is used as is (it also covers minutes another app already fetched). On firmware 3.25
 * the live total carries a large constant offset (11,368 steps with 1,836 m), while its increments stay right.
 * Then the counter is anchored to the synced history: each time the history total changes, the live reading
 * at that moment is remembered, and only the steps counted since are added on top.
 */
class LiveSteps {
    private var anchoredHistory = -1
    private var liveAtAnchor = 0

    fun today(history: Int, live: StepTotals?): Int {
        if (live == null) return history
        if (live.isPlausible) return maxOf(history, live.steps)
        // Re-anchor on new synced data, and when the counter goes backwards (midnight reset or strap restart).
        if (history != anchoredHistory || live.steps < liveAtAnchor) {
            anchoredHistory = history
            liveAtAnchor = live.steps
        }
        return history + (live.steps - liveAtAnchor)
    }
}
