package org.openstrap.app.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test
import org.openstrap.app.data.Recovery
import org.openstrap.app.ui.dashboard.Narrative.Rating

/** OpenStrap's four readiness bands, the Alert cap's place in them, and Zepp's three bands for the strap's values. */
class ReadinessBandsTest {
    @Test
    fun alertCapSitsInTheLowestBand() {
        assertEquals(Recovery.FAIR_FROM - 1, Recovery.ALERT_CAP)
        assertEquals(Rating.LOW, Narrative.ratingFor(Recovery.ALERT_CAP))
        assertEquals("Take it easy", Narrative.readinessHeadline(Recovery.ALERT_CAP, alert = false))
        // One point higher is Fair, so the cap is as high as it can be and still stay out of it.
        assertEquals(Rating.FAIR, Narrative.ratingFor(Recovery.ALERT_CAP + 1))
    }

    @Test
    fun strapScoresUseZeppsThreeBands() {
        assertEquals(Rating.LOW, Narrative.zeppReadinessRating(0))
        assertEquals(Rating.LOW, Narrative.zeppReadinessRating(54))
        assertEquals(Rating.LOW, Narrative.zeppReadinessRating(69))
        assertEquals(Rating.GOOD, Narrative.zeppReadinessRating(70))
        assertEquals(Rating.GOOD, Narrative.zeppReadinessRating(84))
        assertEquals(Rating.OPTIMAL, Narrative.zeppReadinessRating(85))
        assertEquals(Rating.OPTIMAL, Narrative.zeppReadinessRating(100))
        assertEquals("Low", Narrative.zeppReadinessRating(69).label)
        // OpenStrap's own bands are unchanged: 60 is Fair there, Low in Zepp's.
        assertEquals(Rating.FAIR, Narrative.ratingFor(60))
    }
}
