package org.openstrap.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.exp

/** Training load, max HR, daytime resting HR, CTL/ATL/Form and Strain (evidence-review.md, "Training load and Strain"). */
class LoadTest {
    private val rest = 60f
    private val max = 190f
    private fun bpmAt(x: Float) = Math.round(rest + x * (max - rest))

    @Test fun `isolated spikes don't count, sustained effort does`() {
        // A spike every 10 minutes over an hour: never 3 of 5.
        val spikes = (0 until 60).map { it * 60L to if (it % 10 == 0) bpmAt(0.7f) else 70 }
        assertEquals(0f, Recovery.dailyLoad(spikes, rest, max), 1e-6f)
        // Ten minutes at 60 % of reserve: the first two minutes wait for the run, the other eight count.
        val bout = (0 until 10).map { it * 60L to bpmAt(0.6f) }
        val x = Recovery.hrr(bpmAt(0.6f), rest, max)
        assertEquals(8 * Recovery.trimpMinute(x), Recovery.dailyLoad(bout, rest, max), 1e-3f)
        // Two minutes on, one off, still counts: 3 of the last 5 minutes are above.
        val broken = listOf(0, 1, 3, 4).map { it * 60L to bpmAt(0.6f) }
        assertEquals(2 * Recovery.trimpMinute(x), Recovery.dailyLoad(broken, rest, max), 1e-3f)
        // Asleep minutes never count.
        assertEquals(0f, Recovery.dailyLoad(bout, rest, max) { true }, 1e-6f)
    }

    @Test fun `max HR needs two days that agree`() {
        assertEquals(183, Recovery.observedPeak(listOf(200, 185, 183, 150)))
        assertEquals(188, Recovery.observedPeak(listOf(190, 188)))
        assertNull(Recovery.observedPeak(listOf(200, 190, 180)))
        assertNull(Recovery.observedPeak(listOf(200)))
        // A tested value wins over everything.
        assertEquals(176f, Recovery.maxHeartRate(30, observedPeak = 200, tested = 176), 1e-6f)
        assertEquals(187f, Recovery.maxHeartRate(30, observedPeak = 180), 1e-6f) // never lowered
    }

    @Test fun `a lone 1-minute spike isn't a day's maximum`() {
        val minutes = listOf(0L to 120, 60L to 122, 120L to 210, 180L to 121)
        assertEquals(122, Recovery.filteredMax(minutes))
        assertNull(Recovery.filteredMax(listOf(0L to 240)))
    }

    @Test fun `daytime resting HR prefers the strap's daily values, else nightly plus an offset`() {
        val d = 86_400L
        val daily = (1..20).associate { it * d to 62f }
        val nightly = (1..20).associate { it * d to 56f }
        assertEquals(62f, Recovery.daytimeRestingHr(daily, nightly, 20 * d)!!, 1e-6f)
        // No daily values in the last 14 days: nightly lowest + the learnt 6 bpm offset.
        val nightlyLater = nightly + (30..40).associate { it * d to 54f }
        assertEquals(60f, Recovery.daytimeRestingHr(daily, nightlyLater, 40 * d)!!, 1e-6f)
        // Never seen both: + 3 bpm.
        assertEquals(57f, Recovery.daytimeRestingHr(emptyMap(), nightlyLater, 40 * d)!!, 1e-6f)
        assertNull(Recovery.daytimeRestingHr(emptyMap(), emptyMap(), 40 * d))
    }

    @Test fun `ctl atl and form`() {
        val flat = Recovery.fitnessFatigue(List(60) { 50f })
        assertEquals(50f, flat.last().ctl, 1e-3f)
        assertEquals(0f, flat.last().form, 1e-3f)
        // One step of the recursion after a week of 50: TRIMP 200.
        val t = Recovery.fitnessFatigue(List(7) { 50f } + 200f).last()
        assertEquals(50f + 150f * (1 - exp(-1f / 42)), t.ctl, 1e-3f)
        assertEquals(50f + 150f * (1 - exp(-1f / 7)), t.atl, 1e-3f)
        // Form uses yesterday's values, so a hard day today doesn't change today's form.
        assertEquals(0f, t.form, 1e-3f)
        val next = Recovery.fitnessFatigue(List(7) { 50f } + 200f + 50f).last()
        assertTrue(next.form < 0)
    }

    @Test fun `strain curve`() {
        val m = Recovery.trimpMinute(0.6f) * 60 // 1 h at 60 % HRR, male curve
        assertEquals(7f, Recovery.strain(m), 0.2f)
        assertEquals(15.5f, Recovery.strain(Recovery.trimpMinute(0.75f) * 120), 0.3f) // 2 h at 75 %
        assertEquals(0f, Recovery.strain(0f), 1e-6f)
        assertTrue(Recovery.strain(10_000f) <= Recovery.STRAIN_MAX)
        assertTrue(Recovery.strain(500f) < Recovery.STRAIN_MAX)
        assertEquals(50, Recovery.loadPercentile(50f, List(10) { it * 10f + 5f }))
        assertNull(Recovery.loadPercentile(50f, List(6) { 1f }))
    }

    @Test fun `acwr is uncoupled`() {
        assertEquals(1f, Recovery.acwr(List(35) { 60f })!!, 1e-4f)
        assertNull(Recovery.acwr(List(20) { 60f }))
        // Last week doubled: the chronic part ends before it, so the ratio sees the whole jump.
        val r = Recovery.acwr(List(28) { 60f } + List(7) { 120f })!!
        assertTrue("acwr $r", r > 1.6f)
    }
}
