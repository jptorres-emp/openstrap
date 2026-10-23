package org.openstrap.app.ui.dashboard

import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openstrap.app.device.ConnectionState
import org.openstrap.app.device.SyncState

/** The header's day labels, the strap chip's words and the tab rules (spec 3.1 to 3.3). */
class FrameTest {
    private val today = LocalDate.of(2026, 10, 23) // a Friday
    private val uk = Locale.UK

    @Test fun `title says today, yesterday, then the weekday`() {
        assertEquals("Today", dayTitle(today, today, uk))
        assertEquals("Yesterday", dayTitle(today.minusDays(1), today, uk))
        assertEquals("Wednesday", dayTitle(today.minusDays(2), today, uk))
        assertEquals("Friday", dayTitle(today.minusDays(7), today, uk))
    }

    @Test fun `the day under the title is short and never has an interpunct`() {
        assertEquals("Fri 23 Oct", dateLine(today, uk))
        assertEquals("Thu", peekDay(today.minusDays(1), uk))
    }

    private val now = 1_800_000_000L
    private fun connected(battery: Int? = 86, charging: Boolean = false) = ConnectionState.Connected(battery, "3.25", charging)

    @Test fun `all well is the battery and a dot`() {
        val chip = strapChip(true, connected(), SyncState.Done(now - 60), now - 10, now)
        assertEquals("86%", chip.text)
        assertEquals(StrapChip.Dot.Fine, chip.dot)
    }

    @Test fun `words only when they matter`() {
        assertEquals("Charging 64%", strapChip(true, connected(64, charging = true), SyncState.Idle, null, now).text)
        assertEquals(StrapChip.Dot.Charging, strapChip(true, connected(64, charging = true), SyncState.Idle, null, now).dot)
        val failed = strapChip(true, connected(), SyncState.Failed("timeout"), now, now)
        assertEquals("Not synced", failed.text)
        assertEquals(StrapChip.Dot.Warn, failed.dot)
        assertEquals("Not paired", strapChip(false, ConnectionState.NotPaired, SyncState.Idle, null, now).text)
        assertEquals("Not paired", strapChip(true, ConnectionState.NotPaired, SyncState.Idle, null, now).text)
    }

    @Test fun `a low battery keeps the number and changes the dot`() {
        val chip = strapChip(true, connected(12), SyncState.Idle, now, now)
        assertEquals("12%", chip.text)
        assertEquals(StrapChip.Dot.Low, chip.dot)
    }

    @Test fun `a strap gone away says when it was last seen, in amber`() {
        val chip = strapChip(true, ConnectionState.Disconnected, SyncState.Idle, now - 18 * 60, now)
        assertEquals("18 min ago", chip.text)
        assertEquals(StrapChip.Dot.Warn, chip.dot)
        assertEquals("Not connected", strapChip(true, ConnectionState.Disconnected, SyncState.Idle, null, now).text)
        assertEquals("18 min ago", strapChip(true, ConnectionState.Failed("gone"), SyncState.Idle, now - 18 * 60, now).text)
    }

    @Test fun `a moment's connecting stays quiet, a long one shows the time`() {
        val brief = strapChip(true, ConnectionState.Connecting, SyncState.Idle, now - 30, now)
        assertEquals("", brief.text)
        assertEquals(StrapChip.Dot.Quiet, brief.dot)
        assertEquals("", strapChip(true, ConnectionState.Authenticating, SyncState.Idle, null, now).text)
        assertEquals("3 h ago", strapChip(true, ConnectionState.Connecting, SyncState.Idle, now - 3 * 3600, now).text)
    }

    @Test fun `right after the app starts, a strap not back yet is a quiet dot`() {
        val starting = strapChip(true, ConnectionState.Disconnected, SyncState.Idle, now - 29 * 60, now, sinceStart = 10)
        assertEquals("", starting.text)
        assertEquals(StrapChip.Dot.Quiet, starting.dot)
        assertEquals("", strapChip(true, ConnectionState.Connecting, SyncState.Idle, now - 29 * 60, now, sinceStart = 119).text)
        // After the grace, or once connected, as usual; an unpaired strap says so at once.
        assertEquals("29 min ago", strapChip(true, ConnectionState.Disconnected, SyncState.Idle, now - 29 * 60, now, sinceStart = STARTUP_GRACE_S).text)
        assertEquals("45%", strapChip(true, connected(45), SyncState.Idle, now, now, sinceStart = 5).text)
        assertEquals("Not paired", strapChip(false, ConnectionState.NotPaired, SyncState.Idle, null, now, sinceStart = 5).text)
    }

    @Test fun `connected without a battery reading is just the dot`() {
        val chip = strapChip(true, connected(null), SyncState.Idle, now, now)
        assertEquals("", chip.text)
        assertEquals(StrapChip.Dot.Fine, chip.dot)
    }

    @Test fun `ago reads like a person would say it`() {
        assertEquals("Just now", agoText(20))
        assertEquals("1 min ago", agoText(60))
        assertEquals("59 min ago", agoText(59 * 60 + 59))
        assertEquals("1 h ago", agoText(3600))
        assertEquals("1 day ago", agoText(30 * 3600))
        assertEquals("3 days ago", agoText(3 * 86_400))
        assertEquals("Just now", agoText(-5))
    }

    private val recovery = 1
    private val sleep = 2
    private val activity = 3

    @Test fun `back goes to the tab before, then Today, then leaves`() {
        val nav = TabNav().select(recovery).select(sleep).select(activity)
        val first = nav.back()!!
        assertEquals(sleep, first.current)
        val second = first.back()!!
        assertEquals(TabNav.HOME, second.current)
        assertNull(second.back())
    }

    @Test fun `back from a tab opened from Today goes to Today`() {
        val nav = TabNav().select(sleep)
        assertEquals(TabNav.HOME, nav.back()!!.current)
        assertNull(TabNav().back())
    }

    @Test fun `selecting the tab showing changes nothing`() {
        val nav = TabNav().select(sleep)
        assertEquals(nav, nav.select(sleep))
    }

    @Test fun `tapping the tab showing scrolls, then pops`() {
        val (once, first) = TabNav().select(sleep).reselect(depth = 2)
        assertEquals(Reselect.ScrollToTop, first)
        val (twice, second) = once.reselect(depth = 2)
        assertEquals(Reselect.PopToRoot, second)
        assertFalse(twice.reselected)
        // At the root there's nothing to pop: it scrolls again.
        assertEquals(Reselect.ScrollToTop, twice.reselect(depth = 0).second)
        assertEquals(Reselect.ScrollToTop, once.reselect(depth = 0).second)
    }

    @Test fun `opening a screen in between makes the next tap scroll first`() {
        val (once, _) = TabNav().reselect(depth = 1)
        assertTrue(once.reselected)
        assertEquals(Reselect.ScrollToTop, once.moved().reselect(depth = 1).second)
        // Moving to another tab starts over too.
        assertFalse(once.select(sleep).reselected)
    }

    @Test fun `open_metric routes name a metric, Recovery or Sleep`() {
        assertEquals(Detail.OfMetric(Metric.Hrv), Detail.fromRoute("Hrv"))
        assertEquals(Detail.Recovery, Detail.fromRoute("Recovery"))
        assertEquals(Detail.Sleep, Detail.fromRoute("Sleep"))
        assertNull(Detail.fromRoute("Readiness"))
    }

    @Test fun `stacks with the new screens survive process death`() {
        val stack = listOf(Detail.AllMetrics, Detail.Recovery, Detail.OfMetric(Metric.Spo2), Detail.Sleep, Detail.Workout(1792227300))
        val saved = with(Detail.StackSaver) { androidx.compose.runtime.saveable.SaverScope { true }.save(stack) }!!
        assertEquals(stack, Detail.StackSaver.restore(saved))
    }
}
