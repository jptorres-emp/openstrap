package org.openstrap.app.widget

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openstrap.app.device.WidgetUpdater

/**
 * Widgets spec 5: the strap status line's wordings, its pill, and that every widget still fits with it showing, at
 * the three sizes' minimums and on One UI's cells, at the fonts phones use and the largest, no gap over 32 dp on One UI.
 */
class WidgetStatusLineTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 24) // a Saturday
    private val now: Long = today.atTime(12, 0).atZone(zone).toEpochSecond()

    private fun line(s: StrapStatus) = StatusLine.of(s, now, zone, Locale.UK)

    /** Every state, the longest wording of each: "Last sync Wednesday" is the widest the line gets. */
    private val lines = listOf(
        line(StrapStatus.NotPaired),
        line(StrapStatus.LowBattery(12)),
        line(StrapStatus.StaleSync(now - 3 * 3600 - 60)),
        line(StrapStatus.StaleSync(now - 86_400)),
        line(StrapStatus.StaleSync(now - 3 * 86_400)),
        line(StrapStatus.StaleSync(now - 11 * 86_400)),
        line(StrapStatus.Syncing),
    )

    private val scales = listOf(1f, 1.08f, 1.3f)

    /** Content sizes inside the padding: the three minimums and One UI's cells (spec 2.1). */
    private val minimum = Triple(110f to 110f, 260f to 120f, 260f to 220f)
    private val oneUi = Triple(140f to 180f, 313f to 180f, 313f to 290f)
    private val target = Spread.TARGET_GAP_DP + 0.01f

    /* --------------------------------------------- The line itself --------------------------------------------- */

    @Test fun `each state's wordings, longest first, the line's own copy first`() {
        assertEquals(listOf("Pair your strap", "Pair strap"), line(StrapStatus.NotPaired).options)
        assertEquals(listOf("Strap at 12%", "12%"), line(StrapStatus.LowBattery(12)).options)
        assertEquals(listOf("Syncing"), line(StrapStatus.Syncing).options)
        assertEquals(listOf("Last sync 08:59", "08:59"), line(StrapStatus.StaleSync(now - 3 * 3600 - 60)).options)
        assertEquals(listOf("Last sync yesterday", "Yesterday"), line(StrapStatus.StaleSync(now - 86_400)).options)
        assertEquals(listOf("Last sync Wednesday", "Last sync Wed", "Wednesday", "Wed"), line(StrapStatus.StaleSync(now - 3 * 86_400)).options)
        assertEquals(listOf("Last sync 13 Oct", "13 Oct"), line(StrapStatus.StaleSync(now - 11 * 86_400)).options)
        lines.forEach { l ->
            assertEquals(strapStatusLine(l.status, now, zone, Locale.UK), l.full)
            l.options.forEach { assertFalse(it, it.any { c -> c in "·;—–" }) }
        }
    }

    @Test fun `the pill takes a shorter wording, then Caption size, before it drops`() {
        val l = line(StrapStatus.StaleSync(now - 3 * 86_400))
        val full = StatusPill.width("Last sync Wednesday", WidgetText.SECONDARY_SP, 1f)
        assertEquals("Last sync Wednesday", StatusPill.fit(l, full, 1f)!!.text)
        // The whole line at Caption size, then a shorter wording.
        assertEquals("Last sync Wednesday" to WidgetText.CAPTION_SP, StatusPill.fit(l, full - 1f, 1f)!!.let { it.text to it.sp })
        val caption = StatusPill.width("Last sync Wednesday", WidgetText.CAPTION_SP, 1f)
        assertEquals("Last sync Wed", StatusPill.fit(l, caption - 1f, 1f)!!.text)
        val tiny = StatusPill.width("Wed", WidgetText.CAPTION_SP, 1f)
        val p = StatusPill.fit(l, tiny, 1f)!!
        assertEquals("Wed" to WidgetText.CAPTION_SP, p.text to p.sp)
        assertNull(StatusPill.fit(l, tiny - 1f, 1f))
        assertNull(StatusPill.fit(null, 400f, 1f))
        // One line: the pill's height is a line of its type and its padding.
        assertEquals(WidgetText.line(WidgetText.SECONDARY_SP, 1.3f) + 2 * StatusPill.PAD_V_DP, StatusPill.height(WidgetText.SECONDARY_SP, 1.3f), 1e-4f)
    }

    @Test fun `the pill takes the title's place and the note goes before it does`() {
        val l = line(StrapStatus.StaleSync(now - 86_400))
        val wide = TopRow.of("Steps", "Goal 10,000", l, 313f, 1f)
        assertNotNull(wide.pill)
        assertTrue(wide.note)
        val narrow = TopRow.of("Steps", "Goal 10,000", l, 200f, 1f)
        assertEquals("Last sync yesterday", narrow.pill!!.text)
        assertFalse(narrow.note)
        val none = TopRow.of("Steps", "Goal 10,000", null, 313f, 1f)
        assertNull(none.pill)
        assertTrue(none.note)
        assertEquals(WidgetText.line(WidgetText.TITLE_SP, 1f), none.height, 0f)
        assertEquals(wide.pill!!.height(1f), wide.height, 0f)
    }

    @Test fun `a stale line has an alarm of its own, only while widgets are placed`() {
        assertEquals(now + 1, WidgetUpdater.staleAt(true, now - SYNC_STALE_SECONDS, now - 10))
        assertNull(WidgetUpdater.staleAt(false, now, now))
        assertNull(WidgetUpdater.staleAt(true, null, now))
        // Already stale: the line shows now, no alarm needed.
        assertNull(WidgetUpdater.staleAt(true, now - SYNC_STALE_SECONDS - 5, now))
    }

    @Test fun `the gaps share the spare height, half above and below`() {
        val s = Spread.of(20f, null, 0f, 30f, 10f)
        assertEquals(Spread(60f, 2), s)
        assertEquals(40f / 3, s.gap(100f), 1e-4f)
        assertEquals(20f / 3, s.edge(100f), 1e-4f)
        assertTrue(s.fits(68f, 4f))
        assertFalse(s.fits(67f, 4f))
    }

    /* ---------------------------------------------- Each widget ---------------------------------------------- */

    /** Checks [fits] and, on One UI, [gap] at every line, font and size; [pill] must be on One UI. */
    private fun every(
        what: String,
        check: (size: Int, w: Float, h: Float, s: Float, l: StatusLine) -> Triple<Boolean, Float, StatusPill.Pill?>,
    ) {
        for (l in lines) for (s in scales) for ((cells, real) in listOf(minimum to false, oneUi to true)) {
            listOf(cells.first, cells.second, cells.third).forEachIndexed { i, (w, h) ->
                val (fits, gap, pill) = check(i, w, h, s, l)
                val where = "$what ${listOf("Small", "Wide", "Large")[i]} $w x $h at $s with \"${l.full}\""
                assertTrue("$where: doesn't fit", fits)
                pill?.let { assertTrue("$where: pill ${it.width(s)} wide", it.width(s) <= w + 0.01f) }
                if (real) {
                    assertTrue("$where: gap $gap", gap <= target)
                    assertNotNull("$where: the line dropped", pill)
                }
            }
        }
    }

    @Test fun `Recovery fits with the status line`() {
        val m = WidgetSamples.recovery(today, Locale.UK)
        every("Recovery") { i, w, h, s, l ->
            val f = when (i) {
                0 -> RecoveryLayout.small(m, w, h, s, l)
                1 -> RecoveryLayout.wide(m, w, h, s, l)
                else -> RecoveryLayout.large(m, w, h, s, l)
            }
            assertTrue(WidgetDots.width(m.score!!, f.dot) <= w)
            Triple(f.spread.fits(h, RecoveryLayout.MIN_GAP_DP), f.spread.gap(h), f.top.pill)
        }
    }

    @Test fun `Steps fits with the status line`() {
        val m = WidgetSamples.steps(today, Locale.UK)
        every("Steps") { i, w, h, s, l ->
            if (i == 0) {
                val f = StepsLayout.small(m.stepsText, m.share, w, h, s, l)
                Triple(f.spread.fits(h, StepsLayout.SMALL_BAR_GAP_DP / 2), f.spread.gap(h), f.top.pill)
            } else {
                val f = if (i == 1) StepsLayout.wide(m, w, h, s, l) else StepsLayout.large(m, w, h, s, l)
                Triple(f.spread.fits(h, StepsLayout.MIN_GAP_DP), f.spread.gap(h), f.top.pill)
            }
        }
    }

    @Test fun `Sleep fits with the status line`() {
        val m = WidgetSamples.sleep(today, zone)
        every("Sleep") { i, w, h, s, l ->
            when (i) {
                0 -> SleepLayout.small(m, w, h, s, l).let { Triple(SleepLayout.smallHeight(it, s) <= h + 0.01f, it.spread.gap(h), it.top.pill) }
                1 -> SleepLayout.wide(m, w, h, s, l).let { Triple(SleepLayout.wideHeight(it, s) <= h + 0.01f, it.spread.gap(h), it.top.pill) }
                else -> SleepLayout.large(m, w, h, s, l).let {
                    Triple(SleepLayout.largeHeight(it, m.stages.size, s) <= h + 0.01f, SleepLayout.largeSpread(it, m.stages.size, s).gap(h), it.top.pill)
                }
            }
        }
    }

    @Test fun `Start fits with the status line above it`() {
        val m = WidgetSamples.start(Instant.ofEpochSecond(now), zone, Locale.UK)
        val sizes = listOf(WidgetSizes.Small, WidgetSizes.Wide, WidgetSizes.Large)
        for (l in lines) for (s in scales) for ((cells, real) in listOf(minimum to false, oneUi to true)) {
            listOf(cells.first, cells.second, cells.third).forEachIndexed { i, (w, h) ->
                val where = "Start ${sizes[i]} $w x $h at $s with \"${l.full}\""
                val pill = StartLayout.statusPill(m, l, sizes[i], w, h, s)
                val under = h - StartLayout.statusHeight(pill, s)
                val (content, gaps, least) = when (i) {
                    0 -> StartLayout.small(m.usual, listOf(StartWidgetModel.USUAL), false, w, under, s).let { Triple(StartLayout.smallHeight(it, s), it.gaps, StartLayout.MIN_GAP_DP) }
                    1 -> StartLayout.wide(m, w, under, s).let { Triple(it.content, it.gaps, StartLayout.MIN_GAP_DP) }
                    else -> StartLayout.large(m, w, under, s).let { Triple(it.content, it.gaps, 3 * StartLayout.MIN_GAP_DP) }
                }
                assertTrue("$where: ${content + least} in $under", content + least <= under + 0.01f)
                if (real) {
                    assertNotNull("$where: the line dropped", pill)
                    assertTrue("$where: gap ${(under - content) / gaps}", (under - content) / gaps <= target)
                }
            }
        }
    }

    @Test fun `Moment fits with the status line`() {
        val m = WidgetSamples.moment(today, zone)
        every("Moment") { i, w, h, s, l ->
            val f = when (i) {
                0 -> MomentLayout.small(m, w, h, s, l)
                1 -> MomentLayout.wide(m, w, h, s, l)
                else -> MomentLayout.large(m, w, h, s, l)
            }
            Triple(f.fits(h), f.gap(h), f.pill)
        }
    }

    @Test fun `the samples are each widget's real look, in the copy rules`() {
        assertEquals("78", WidgetSamples.recovery(today).score)
        assertEquals("8,432", WidgetSamples.steps(today).stepsText)
        assertEquals("82", WidgetSamples.sleep(today, zone).score)
        assertEquals("Outdoor run", WidgetSamples.start(Instant.ofEpochSecond(now), zone, Locale.UK).usual.name)
        val moment = WidgetSamples.moment(today, zone)
        assertEquals("62", moment.number)
        val texts = listOfNotNull(moment.name, moment.number) + moment.sentences + moment.figures + moment.words +
            WidgetSamples.recovery(today).let { listOfNotNull(it.title, it.word, it.sentence, it.note) } +
            WidgetSamples.steps(today).let { listOf(it.title, it.word, it.toGo, it.share, it.weekLine) }
        texts.forEach { assertFalse(it, it.any { c -> c in "·;—" }) }
    }
}
