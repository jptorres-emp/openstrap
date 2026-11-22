package org.openstrap.app.widget

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openstrap.app.data.WorkoutAnalysis
import org.openstrap.app.data.Workouts
import org.openstrap.app.data.db.Workout
import org.openstrap.app.data.HeartZones

/** Widgets spec 3.5: what the Start widget offers from your workouts, this week's line, recording, and how it fits. */
class StartWidgetModelTest {
    private val zone: ZoneId = ZoneOffset.UTC
    /** Saturday 24 October 2026, midday. */
    private val now: Instant = at(24, 12, 0)

    private fun at(day: Int, hour: Int, minute: Int): Instant = LocalDateTime.of(2026, 10, day, hour, minute).toInstant(ZoneOffset.UTC)

    private fun workout(day: Int, hour: Int, type: Int, minutes: Int, meters: Float? = null, sets: Int? = null): WorkoutAnalysis {
        val start = at(day, hour, 0).epochSecond
        val w = Workout(start, start + minutes * 60L, type, minutes * 60, null, null, null, null, null, null, null, null, null)
        return Workouts.analyse(w, emptyList(), emptyList(), 190, HeartZones.STANDARD).copy(distanceMeters = meters, sets = sets)
    }

    /**
     * Runs three times (Tuesday's 5.2 km this week), walks and rides twice, yoga on Friday, strength once,
     * and the strap's own "Activity" this week, which isn't a workout.
     */
    private val history = listOf(
        workout(5, 7, 1, 45),
        workout(1, 7, 1, 45),
        workout(20, 18, 1, 60, meters = 5_200f),
        workout(16, 8, 3, 30),
        workout(12, 8, 3, 30),
        workout(15, 17, 4, 50),
        workout(9, 17, 4, 50),
        workout(23, 19, 60, 35),
        workout(18, 10, 52, 40, sets = 12),
        workout(22, 13, Workouts.GENERIC_ACTIVITY, 20),
    )

    private fun model(
        workouts: List<WorkoutAnalysis> = history, sides: Map<Int, Boolean> = emptyMap(), location: Boolean = true,
        recording: StartRecordingInput? = null,
    ) = StartWidgetModel.from(workouts, sides, location, recording, now, zone, Locale.UK)

    @Test fun `the usual is the sport done most, the next four by use`() {
        val m = model()
        assertEquals(1, m.usual.sportType)
        assertEquals("Run", m.usual.name)
        assertEquals("Start run", m.usual.action)
        // Walk and cycling twice, the walk more recently; yoga and strength once, the yoga more recently.
        assertEquals(listOf("Walk", "Cycling", "Yoga", "Strength"), m.next.take(4).map { it.caption })
        assertEquals(listOf(3, 4, 60, 52), m.next.take(4).map { it.sportType })
        // Then the everyday sports, each once (Hike isn't on the strap for now: Cross training has its place).
        assertEquals("Cross-training", m.next[4].caption)
        assertEquals(m.next.size, m.next.distinctBy { it.shownType }.size)
        assertTrue(m.next.none { it.shownType == Workouts.GENERIC_ACTIVITY })
        assertNull(m.recording)
    }

    @Test fun `before any workout, the default sports, then the everyday ones`() {
        val m = model(workouts = emptyList())
        assertEquals(Workouts.DEFAULT_SPORTS.first(), m.usual.sportType)
        assertEquals(listOf("Walk", "Strength", "Cross-training", "Cycling"), m.next.take(4).map { it.caption })
        assertEquals("No workouts yet", m.weekLine)
        assertEquals("Last run", m.lastLabel)
        assertEquals("None yet", m.lastLine)
    }

    @Test fun `GPS on or off as Ready would start the usual`() {
        val on = model()
        assertEquals("Your usual, GPS on", on.usualLine)
        assertTrue(on.gpsOn)
        val off = model(location = false)
        assertEquals("Your usual, GPS off", off.usualLine)
        assertFalse(off.gpsOn)
        // Strength has no GPS to speak of.
        val indoor = model(workouts = listOf(workout(20, 8, 52, 40), workout(21, 8, 52, 40)))
        assertEquals("Strength", indoor.usual.name)
        assertEquals("Your usual", indoor.usualLine)
        assertFalse(indoor.gpsOn)
    }

    @Test fun `a sport with two sides shows the side Ready opens on, once`() {
        // Your runs, last done on the treadmill side: Ready opens on the treadmill, and the run keeps its one name.
        val m = model(workouts = history + workout(24, 7, 2, 35), sides = mapOf(1 to true))
        assertEquals(1, m.usual.sportType)
        assertEquals(2, m.usual.shownType)
        assertEquals("Run", m.usual.name)
        assertEquals("Your usual", m.usualLine)
        assertFalse(m.gpsOn)
        // The treadmill run is the same sport as the usual: no second Run tile.
        assertTrue(m.next.none { Workouts.tileType(it.shownType) == 1 })
    }

    @Test fun `this week counts workouts since Monday, not the strap's Activity`() {
        val m = model()
        // Tuesday's hour and Friday's 35 minutes.
        assertEquals("2 workouts, 1 h 35", m.weekLine)
        assertEquals("1 workout, 40 min", StartWidgetModel.weekLine(1, 40 * 60L))
        assertEquals("3 workouts, 2 h", StartWidgetModel.weekLine(3, 7_200L))
        assertEquals("No workouts yet", StartWidgetModel.weekLine(0, 0L))
    }

    @Test fun `this week starts on the locale's first day`() {
        // In the US the week began on Sunday the 18th, so that day's strength session counts too.
        val us = StartWidgetModel.from(history, emptyMap(), true, null, now, zone, Locale.US)
        assertEquals("3 workouts, 2 h 15", us.weekLine)
        // The last run's distance in the locale's own decimal mark.
        val german = StartWidgetModel.from(history.filter { it.workout.sportType != 2 }, emptyMap(), true, null, now, zone, Locale.GERMANY)
        assertEquals("Dienstag, 5,2 km", german.lastLine)
    }

    @Test fun `the usual sport's last, by its tile, with its distance, sets or length`() {
        assertEquals("Last run", model().lastLabel)
        assertEquals("Tuesday, 5.2 km", model().lastLine)
        // A treadmill on Friday is a run too.
        val treadmill = model(workouts = history + workout(23, 19, 2, 35))
        assertEquals("Yesterday, 35 min", treadmill.lastLine)
        val strength = model(workouts = listOf(workout(18, 10, 52, 40, sets = 12), workout(10, 10, 52, 40)))
        assertEquals("Last strength", strength.lastLabel)
        assertEquals("Sunday, 12 sets", strength.lastLine)
        assertEquals("Last HIIT", StartWidgetModel.lastLabel(49))
        assertEquals("Last workout", StartWidgetModel.lastLabel(5))
        assertEquals("Last walk", StartWidgetModel.lastLabel(40))
        assertEquals("Today, 35 min", "${recentDay(now.atZone(zone).toLocalDate(), now.atZone(zone).toLocalDate())}, 35 min")
        assertEquals("Sat 10 Oct", recentDay(java.time.LocalDate.of(2026, 10, 10), java.time.LocalDate.of(2026, 10, 24), Locale.UK))
    }

    @Test fun `while recording, the sport, since when and the active time`() {
        val m = model(recording = StartRecordingInput(4, at(24, 11, 58).epochSecond, 1_500L, paused = false))
        val rec = assertNotNullAnd(m.recording)
        assertEquals("Cycling", rec.sport.name)
        assertEquals("Since 11:58", rec.since)
        assertEquals("Recording", rec.status)
        assertEquals(4, rec.sport.shownType)
        assertEquals(1_500_000L, rec.activeMillis)
        assertEquals(now.toEpochMilli(), rec.atMillis)
        assertEquals("Paused", model(recording = StartRecordingInput(4, 0L, 60L, paused = true)).recording!!.status)
    }

    @Test fun `the timer's base puts the active time at now, and stands still paused`() {
        val r = model(recording = StartRecordingInput(4, at(24, 11, 58).epochSecond, 1_500L, paused = false)).recording!!
        val elapsed = 9_000_000L
        // Loaded now: 25 minutes back from the elapsed clock.
        assertEquals(elapsed - 1_500_000L, StartRecording.chronometerBase(elapsed, r.atMillis, r))
        // Drawn 10 s after loading: 10 s more have passed.
        assertEquals(elapsed - 1_510_000L, StartRecording.chronometerBase(elapsed, r.atMillis + 10_000L, r))
        // Paused, the time at the pause whenever it's drawn.
        val p = r.copy(paused = true)
        assertEquals(elapsed - 1_500_000L, StartRecording.chronometerBase(elapsed, r.atMillis + 60_000L, p))
    }

    @Test fun `the recording layouts fit the grid at every font, one line each, the timer whole`() {
        val r = model(recording = StartRecordingInput(60, at(24, 11, 58).epochSecond, 1_500L, paused = false, bpm = 112, zone = 0)).recording!!
        val walk = r.copy(sport = StartSport.recorded(40))
        val m = model().copy(weekLine = "5 workouts, 10 min", lastLine = "Yesterday, 1 min")
        for (s in scales) for (rec in listOf(r, walk)) {
            // The grid's content: a Small is 150 dp less 2 x 20 each way, the Wide 280 x 160, the Large 280 x 260.
            val small = WidgetGrid.contentHeight(WidgetSizes.Small)
            val smallW = WidgetSizes.Small.width.value - 2 * WidgetGrid.PADDING_DP
            val fit = StartLayout.smallRecording(rec, smallW, small, s)
            val where = "Small at $s"
            assertTrue("$where: timer too wide", StartLayout.letterEms("00:00") * fit.timerSp * s <= smallW)
            assertTrue("$where: name wraps", one(fit.name, WidgetText.CARD_TITLE_SP, smallW, s) || fit.name == rec.sport.shortName)
            val rows = WidgetText.line(WidgetText.CARD_TITLE_SP, s) + WidgetText.line(WidgetText.SECONDARY_SP, s) + checkNotNull(fit.timerLine) +
                WidgetGrid.GAP_TIGHT_DP + maxOf(WidgetText.line(WidgetText.SECONDARY_SP, s), StartLayout.chipHeight(s))
            assertTrue("$where: the bpm row is cut, rows take $rows of $small", rows <= small + 0.01f || fit.timerSp == StartLayout.TIMER_MIN_SP)
            for (bucket in listOf(WidgetSizes.Wide, WidgetSizes.Large)) {
                val w = bucket.width.value - 2 * WidgetGrid.PADDING_DP
                val h = WidgetGrid.contentHeight(bucket)
                val wide = if (bucket == WidgetSizes.Wide) StartLayout.wideRecording(rec, w, h, s) else StartLayout.largeRecording(rec, m, w, h, s)
                assertTrue("${bucket.width} x ${bucket.height} at $s: timer too wide", StartLayout.timerWidth(wide.timerSp, s) <= w)
                assertTrue("${bucket.width} x ${bucket.height} at $s: too tall ${wide.content}", wide.content + (wide.gaps - 2) * StartLayout.MIN_GAP_DP <= h)
            }
        }
        // The timer is the hero: one size a bucket at the fonts phones use. With a heart rate beside it, the timer steps down
        // to leave it room across the bucket's narrowest 240 dp, by the font; without, it keeps the bucket's size.
        val still = model(recording = StartRecordingInput(60, at(24, 11, 58).epochSecond, 1_500L, paused = false)).recording!!
        assertEquals(StartLayout.TIMER_WIDE_SP, StartLayout.wideRecording(still, 240f, WidgetGrid.contentHeight(WidgetSizes.Wide), 1f).timerSp, 0f)
        assertTrue(StartLayout.largeRecording(still, m, 240f, WidgetGrid.contentHeight(WidgetSizes.Large), 1.08f).timerSp >= 44f)
        assertTrue(StartLayout.wideRecording(r, 240f, WidgetGrid.contentHeight(WidgetSizes.Wide), 1.08f).timerSp >= 36f)
    }

    @Test fun `the Small recording never cuts its heart rate row`() {
        val r = model(recording = StartRecordingInput(40, at(24, 11, 58).epochSecond, 1_500L, paused = false, bpm = 112, zone = 0)).recording!!
        val h = WidgetGrid.contentHeight(WidgetSizes.Small)
        val w = WidgetSizes.Small.width.value - 2 * WidgetGrid.PADDING_DP
        val fit = StartLayout.smallRecording(r, w, h, 1f)
        assertEquals(StartLayout.TIMER_SMALL_SP, fit.timerSp, 0f)
        assertTrue(fit.heart)
    }

    private fun <T> assertNotNullAnd(v: T?): T {
        assertNotNull(v)
        return v!!
    }

    @Test fun `captions are short names, a sport with two sides once by its one name`() {
        // Indoor cycling reads "Cycling", and a treadmill beside your outdoor runs is the same Run, not a second tile.
        // (Indoor walk isn't on the strap for now.)
        val m = model(workouts = listOf(workout(20, 8, 1, 30), workout(21, 8, 1, 30), workout(22, 8, 8, 30), workout(23, 8, 2, 30)))
        assertEquals("Run", m.usual.caption)
        assertEquals("Cycling", m.next[0].caption)
        assertEquals("Cycling", m.next[0].name)
        assertEquals(8, m.next[0].shownType)
        assertTrue(m.next.none { Workouts.tileType(it.shownType) == 1 })
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Fitting: nothing wraps, nothing cut, no holes                                              */
    /* ------------------------------------------------------------------------------------------ */

    /**
     * Content sizes, width to height: the buckets' minimum width and One UI's, at the bucket's own height. Rows are
     * decided per size bucket, so a taller launcher cell gives the one flexible space more and the rows nothing.
     */
    private val smallCells = listOf(110f to 110f, 127f to 110f, 140f to 110f)
    private val wideCells = listOf(240f to 120f, 313f to 120f)
    private val largeCells = listOf(240f to 220f, 313f to 220f)

    /** The status row above the tiles on the Wide and Large, as the widget always has it. */
    private val TOP = WidgetGrid.TOP_ROW_DP
    private val scales = listOf(1f, 1.08f, 1.3f)

    private fun one(text: String, sp: Float, room: Float, s: Float) = WidgetText.width(text, sp, s) <= room

    @Test fun `the Small's words are one line each, in every cell and font`() {
        val sports = listOf(1, 52, 7, 23, 222, 2).map { StartSport.of(it, emptyMap()) }
        for ((w, h) in smallCells) for (s in scales) for (sp in sports) for (rec in listOf(false)) {
            val lines = listOf(StartWidgetModel.USUAL)
            val fit = StartLayout.small(sp, lines, rec, w, h, s)
            val action = fit.sizes.action(rec, s)
            val room = if (fit.actionTop) w else w - action - StartLayout.SMALL_GAP_DP
            val where = "${sp.name} at $w x $h, font $s, recording $rec"
            assertTrue("$where: name wraps", one(fit.words.name, WidgetText.BODY_SP, room, s) || fit.words.name == sp.shortName)
            fit.words.line?.let { assertTrue("$where: line wraps", one(it, WidgetText.SECONDARY_SP, room, s)) }
            if (fit.actionTop && fit.tile) assertTrue("$where: top row too wide", fit.sizes.tile + StartLayout.SMALL_GAP_DP + action <= w)
        }
        // On One UI's 2 x 2 the usual keeps its full name and "Your usual".
        val fit = StartLayout.small(StartSport.of(1, emptyMap()), listOf(StartWidgetModel.USUAL), false, 127f, 161f, 1.08f)
        assertEquals("Run", fit.words.name)
        assertEquals("Your usual", fit.words.line)
    }

    @Test fun `the usual row is one line each, the mark and GPS giving way first`() {
        val m = model()
        for ((w, h) in wideCells + largeCells) for (s in scales) {
            val b = StartLayout.wide(m, w, h, s)
            val words = b.row.words
            val room = w - b.sizes.tile - 2 * StartLayout.ROW_GAP_DP - b.sizes.play - if (words.mark) StartLayout.MARK_DP + StartLayout.MARK_GAP_DP else 0f
            words.line?.let { assertTrue("$w at $s: line wraps", one(it, WidgetText.SECONDARY_SP, room, s)) }
        }
        // At One UI's 4 columns the whole line and its mark fit; at the minimum the mark gives way first.
        assertEquals(StartLayout.Words("Run", "Your usual, GPS on", true), StartLayout.wide(m, 313f, 161f, 1.08f).row.words)
        assertEquals("Your usual, GPS on", StartLayout.wide(m, 260f, 120f, 1.08f).row.words.line)
    }

    /** Every caption shown fits its box on one line: Glance shows it whole, no "W...". */
    private fun assertCaptionsWhole(t: StartLayout.Tiles, w: Float, s: Float, where: String) {
        if (!t.captioned) return
        assertTrue("$where: captions run together or out", StartLayout.captionsFit(t.captions, t.tile, t.captionSp, w, s))
        val boxes = StartLayout.captionBoxes(t.captions, t.tile, t.captionSp, w, s)
        t.captions.zip(boxes).forEach { (c, box) ->
            // With the safety margin: the rough sum underrates short words ("Swi..." on the phone).
            val need = StartLayout.captionWidth(c, t.captionSp, s)
            assertTrue("$where: $c needs $need dp in a ${box.second} dp box", need <= box.second + 0.01f)
        }
        // Each box centred on its own tile, the first and last too, in order, within half the side padding.
        val n = t.captions.size
        boxes.forEachIndexed { i, (left, bw) -> assertEquals("$where: box $i off centre", StartLayout.centre(i, n, t.tile, w), left + bw / 2, 0.01f) }
        boxes.zipWithNext().forEach { (a, b) -> assertTrue("$where: boxes overlap", a.first + a.second <= b.first + 0.01f) }
        val bleed = StartLayout.CAPTION_BLEED_DP
        assertTrue("$where: past the bleed", boxes.first().first >= -bleed - 0.01f && boxes.last().first + boxes.last().second <= w + bleed + 0.01f)
    }

    @Test fun `short captions get their column's room, not their rough width`() {
        val captions = listOf("Walk", "Strength", "Hike", "Swim", "All sports")
        // One UI's 4 x 2, where the phone showed "W..." and "S...".
        run {
            val w = 313f
            val boxes = StartLayout.captionBoxes(captions, 38f, 10f, w, 1.08f)
            // "Walk" and "Swim" get far more than their 25 dp, so a sum that underrates them can't cut them.
            assertTrue(boxes[0].second >= 1.2f * StartLayout.captionWidth("Walk", 10f, 1.08f))
            assertTrue(boxes[3].second >= 1.2f * StartLayout.captionWidth("Swim", 10f, 1.08f))
        }
    }

    @Test fun `the Wide keeps the tile row at the default font, four sports on One UI, at least three at the minimum`() {
        val m = model()
        for (s in listOf(1f)) {
            assertEquals("One UI 4 x 2 at $s", 4, StartLayout.wide(m, 313f, 120f, s, TOP).tiles?.sports?.size)
            val b = StartLayout.wide(m, 240f, 120f, s, TOP)
            val tiles = b.tiles
            assertNotNull("Wide 280 x 160 at $s lost its tiles", tiles)
            assertTrue(tiles!!.sports.size >= 3)
            assertCaptionsWhole(tiles, 240f, s, "Wide at $s")
            assertTrue(b.content + (b.gaps - 2) * StartLayout.MIN_GAP_DP <= 120f)
        }
    }

    @Test fun `the Large holds four sports on One UI and at least three at the minimum, captions on one line`() {
        val m = model(workouts = history + listOf(workout(24, 8, 40, 30), workout(17, 8, 40, 30), workout(10, 8, 40, 30)))
        for (s in listOf(1f, 1.08f)) {
            assertEquals("One UI 4 x 3 at $s", 4, StartLayout.large(m, 313f, 220f, s, TOP).tiles?.sports?.size)
            val b = StartLayout.large(m, 240f, 220f, s, TOP)
            assertNotNull("Large 280 x 260 at $s lost its tiles", b.tiles)
            assertTrue(b.tiles!!.sports.size >= 3)
            assertCaptionsWhole(b.tiles, 240f, s, "Large at $s")
            assertTrue(b.content + (b.gaps - 2) * StartLayout.MIN_GAP_DP <= 220f)
        }
    }

    @Test fun `tiles never cut or run captions together, at any cell or font`() {
        val long = listOf(14, 6, 8, 17, 18, 21).map { StartSport.of(it, emptyMap()) } + model().next
        val m = model().copy(next = long)
        for ((w, h) in wideCells) for (s in scales) {
            val b = StartLayout.wide(m, w, h, s, TOP)
            val t = b.tiles ?: continue
            assertCaptionsWhole(t, w, s, "Wide $w x $h at $s")
            assertTrue("Wide $w x $h at $s too tall", b.content + (b.gaps - 2) * StartLayout.MIN_GAP_DP <= h)
        }
        for ((w, h) in largeCells) for (s in scales) {
            val b = StartLayout.large(m, w, h, s, TOP)
            val t = b.tiles ?: continue
            assertCaptionsWhole(t, w, s, "Large $w x $h at $s")
            assertTrue("Large $w x $h at $s too tall", b.content + (b.gaps - 2) * StartLayout.MIN_GAP_DP <= h)
        }
        // The Wide's 120 dp holds the top row, the usual row and bare tiles exactly at the default font, each tile read out by
        // name; a larger font's taller usual row leaves no room for them and they go, never cut.
        for ((w, h) in wideCells) assertNotNull("Wide $w x $h at 1.0", StartLayout.wide(model(), w, h, 1f, TOP).tiles)
    }

    @Test fun `each bucket has one size whatever the launcher's cell or the font`() {
        val m = model().copy(weekLine = "5 workouts, 10 min", lastLine = "Yesterday, 1 min")
        val sizes = StartLayout.ROW_SIZES.single()
        for (s in listOf(1f, 1.08f, 1.3f)) {
            for ((w, h) in wideCells) {
                val wide = StartLayout.wide(m, w, h, s, TOP)
                assertEquals("Wide $w at $s", sizes, wide.sizes)
                assertTrue("Wide $w at $s: ${wide.content}", wide.content + (wide.gaps - 2) * StartLayout.MIN_GAP_DP <= h + 0.01f)
            }
            for ((w, h) in largeCells) {
                val large = StartLayout.large(m, w, h, s, TOP)
                assertEquals("Large $w at $s", sizes, large.sizes)
                assertTrue("Large $w at $s: ${large.content}", large.content + (large.gaps - 2) * StartLayout.MIN_GAP_DP <= h + 0.01f)
            }
        }
        // The Small is the top row, a 40 dp tile and play, and the name: it fits its 110 dp at the largest font.
        val small = StartLayout.GRID_SMALL
        assertEquals(40f, small.tile, 0f)
        assertEquals(40f, small.play, 0f)
        val rows = TOP + small.tile + WidgetGrid.GAP_TIGHT_DP + WidgetText.line(WidgetText.BODY_SP, 1.3f)
        assertTrue("Small rows $rows", rows <= WidgetGrid.contentHeight(WidgetSizes.Small))
    }

    @Test fun `the Large's blocks sit on the grid, the spare height in one flexible space`() {
        val m = model().copy(weekLine = "5 workouts, 10 min", lastLine = "Yesterday, 1 min")
        for (s in listOf(1f, 1.08f)) {
            val b = StartLayout.large(m, 313f, 220f, s, TOP)
            // The top row, the usual row, the tiles, the hairline and the figures: every block is there.
            assertNotNull("Large at $s: the tiles", b.tiles)
            assertNotNull("Large at $s: the figures", b.figures)
            // Between them the grid's gaps, the one flexible space under the top row taking what is left.
            assertEquals(6, b.gaps)
            assertTrue("Large at $s: ${b.content}", b.content + (b.gaps - 2) * StartLayout.MIN_GAP_DP <= 220f)
        }
    }

    @Test fun `Swim's caption needs more than its rough width, so a tight one gives way`() {
        // By letter: the W and m make Swim wider than four average letters, then the margin on top.
        assertTrue(StartLayout.letterEms("Swim") > StartLayout.letterEms("Hike"))
        assertEquals(StartLayout.letterEms("Swim") * 11f * 1.08f * 1.15f, StartLayout.captionWidth("Swim", 11f, 1.08f), 1e-3f)
        assertTrue(StartLayout.captionWidth("Swim", 11f, 1.08f) > 1.15f * WidgetText.width("Swim", 11f, 1.08f))
    }

    @Test fun `the tile row is the same sports in the same order at every size`() {
        // The phone's history: walks, strength, hikes, rides, then swims.
        val sports = listOf(3, 52, 15, 4, 7, 49, 60).map { StartSport.of(it, emptyMap()) }
        val m = model().copy(next = sports)
        val rows = mutableMapOf<String, List<String>>()
        for (s in listOf(1f, 1.08f)) {
            StartLayout.wide(m, 240f, 120f, s, TOP).tiles?.let { rows["Wide min $s"] = it.captions }
            StartLayout.wide(m, 313f, 120f, s, TOP).tiles?.let { rows["Wide One UI $s"] = it.captions }
            rows["Large min $s"] = StartLayout.large(m, 240f, 220f, s, TOP).tiles!!.captions
            rows["Large One UI $s"] = StartLayout.large(m, 313f, 220f, s, TOP).tiles!!.captions
        }
        val order = listOf("Walk", "Strength", "Hike", "Cycling", "All sports")
        rows.forEach { (where, captions) -> assertEquals(where, order, captions) }
        // Every size's row is the order's start, whatever its room.
        for ((w, h) in wideCells) for (s in scales) {
            val t = StartLayout.wide(m, w, h, s, TOP).tiles ?: continue
            assertEquals("Wide $w x $h at $s", sports.take(t.sports.size), t.sports)
        }
    }

    @Test fun `the Wide at its minimum keeps its bare tile row at font 1, and at 1_08 loses it rather than cut anything`() {
        assertNotNull("Wide 280 x 160 at 1.0", StartLayout.wide(model(), 240f, 120f, 1f, TOP).tiles)
        for (s in listOf(1.08f, 1.3f)) {
            val b = StartLayout.wide(model(), 240f, 120f, s, TOP)
            assertTrue("Wide 280 x 160 at $s: ${b.content}", b.content + (b.gaps - 2) * StartLayout.MIN_GAP_DP <= 120f)
        }
    }

    @Test fun `the figures never wrap a value`() {
        val m = model().copy(weekLine = "5 workouts, 10 min", lastLine = "Yesterday, 1 min")
        for ((w, _) in largeCells) for (s in scales) {
            val f = StartLayout.figures(m, w, s)
            val room = when (f.layout) {
                StartLayout.FigureLayout.Side -> (w - StartLayout.FIGURE_GAP_DP) / 2
                StartLayout.FigureLayout.Stacked, StartLayout.FigureLayout.Live -> w
            }
            assertTrue("$w at $s: ${f.layout}", one(m.lastLine, WidgetText.BODY_SP, room, s))
        }
        // The phone's case: at 300 wide and 1.08 the two don't fit half each, so one goes under the other.
        assertEquals(StartLayout.FigureLayout.Stacked, StartLayout.figures(m, 260f, 1.08f).layout)
        assertEquals(StartLayout.FigureLayout.Side, StartLayout.figures(model(), 313f, 1f).layout)
    }

    @Test fun `tiles spread edge to edge, evenly`() {
        assertEquals(19f, StartLayout.centre(0, 5, 38f, 260f), 1e-3f)
        assertEquals(241f, StartLayout.centre(4, 5, 38f, 260f), 1e-3f)
        assertEquals(StartLayout.centre(2, 5, 38f, 260f) - StartLayout.centre(1, 5, 38f, 260f), StartLayout.centre(1, 5, 38f, 260f) - StartLayout.centre(0, 5, 38f, 260f), 1e-3f)
        val boxes = StartLayout.captionBoxes(listOf("Walk", "Run", "Yoga", "Swim", "All sports"), 38f, 11f, 260f, 1f)
        // Every caption centred under its tile, Walk and All sports too.
        boxes.forEachIndexed { i, (l, w) -> assertEquals(StartLayout.centre(i, 5, 38f, 260f), l + w / 2, 1e-3f) }
        // All sports, wider than its tile, runs into the side padding by at most half of it.
        assertTrue(boxes.last().first + boxes.last().second > 260f)
        assertTrue(boxes.last().first + boxes.last().second <= 260f + StartLayout.CAPTION_BLEED_DP + 1e-3f)
    }

    @Test fun `with the strap status the Wide and the Small get a title row with the button`() {
        val m = WidgetSamples.start(now, zone, Locale.UK)
        val l = StatusLine(StrapStatus.StaleSync(0), listOf("Synced Wednesday", "Synced Wed", "Wednesday", "Wed"))
        val top = StartLayout.statusTop(l, WidgetSizes.Wide, 313f, 1f)
        assertEquals(StatusButton.SIZE_DP, top.height, 0f)
        assertEquals(WidgetGrid.TOP_ROW_DP, top.height, 0f)
        assertNotNull(top.words)
        val b = StartLayout.wide(m, 313f, 120f, 1f, top.height)
        assertNotNull(b.tiles)
        // The top row is always there, as in every widget: with no status it is the title and a quiet row of the same height.
        val quiet = StartLayout.statusTop(null, WidgetSizes.Wide, 313f, 1f)
        assertNull(quiet.status)
        assertNull(quiet.words)
        assertEquals(WidgetGrid.TOP_ROW_DP, quiet.height, 0f)
        assertEquals(StartLayout.wide(m, 313f, 120f, 1f, top.height).gaps, StartLayout.wide(m, 313f, 120f, 1f, quiet.height).gaps)
        // The Small: the top row with the button and no words, whatever the cell.
        val small = StartLayout.statusTop(l, WidgetSizes.Small, 110f, 1f)
        assertEquals(l, small.status)
        assertNull(small.words)
        assertEquals(WidgetGrid.TOP_ROW_DP, small.height, 0f)
    }
}
