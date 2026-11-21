package org.openstrap.app.ui.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.openstrap.app.data.Strength
import org.openstrap.app.data.WorkoutAnalysis
import org.openstrap.app.data.db.Workout
import org.openstrap.app.device.ConnectionState
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

class StartCopyTest {
    // English figures: the German and Arabic ones are WorkoutWordsTest's and the presentation tests'.
    @get:org.junit.Rule val localeRule = org.openstrap.app.testing.LocaleRule()

    private val today = LocalDate.of(2026, 10, 30)

    @Test fun `the strap line says it's ready, or what's wrong`() {
        assertEquals("Strap ready, 72 bpm", StartCopy.strapLine(ConnectionState.Connected(80, null), 72))
        assertEquals("Strap ready", StartCopy.strapLine(ConnectionState.Connected(80, null), null))
        assertEquals("Strap not connected, start anyway", StartCopy.strapLine(ConnectionState.Disconnected, null))
    }

    @Test fun `no goal is named for the sport`() {
        assertEquals("Just run", StartCopy.noGoal(1))
        assertEquals("Just run", StartCopy.noGoal(2))
        assertEquals("Just walk", StartCopy.noGoal(15))
        assertEquals("Just ride", StartCopy.noGoal(8))
        assertEquals("Just go", StartCopy.noGoal(9))
        assertEquals("Just row", StartCopy.noGoal(23))
    }

    @Test fun `strength's rest note, defaults and rest text`() {
        assertEquals("Once you're under 110 bpm", StartCopy.endRestNote(110))
        // End rest early turns on at a level the recorder knows.
        assertEquals(true, StartCopy.DEFAULT_BELOW in Strength.Rest.LEVELS)
        assertEquals(true, StartCopy.DEFAULT_SETS in Strength.SETS)
        assertEquals("2 min 15 s", StartCopy.restText(135))
    }

    @Test fun `a setting steps to the next multiple of its step, within its range`() {
        assertEquals(105L, StartCopy.stepBy(90, 15, 30L..300L, up = true))
        assertEquals(75L, StartCopy.stepBy(90, 15, 30L..300L, up = false))
        // A saved value off the steps goes to the step next to it.
        assertEquals(105L, StartCopy.stepBy(95, 15, 30L..300L, up = true))
        assertEquals(90L, StartCopy.stepBy(95, 15, 30L..300L, up = false))
        // At an end it stays.
        assertEquals(300L, StartCopy.stepBy(300, 15, 30L..300L, up = true))
        assertEquals(0L, StartCopy.stepBy(0, 5, 0L..600L, up = false))
        assertEquals("1 min", StartCopy.shortTime(60))
        assertEquals("20 min", StartCopy.shortTime(1200))
        assertEquals("90 s", StartCopy.shortTime(90))
        assertEquals("20 s", StartCopy.shortTime(20))
    }

    @Test fun `a run rep steps by 100 m under a km, half a km from it, and 30 s by time`() {
        val reps = org.openstrap.app.data.Structure.Reps(400, null, 90, 6)
        assertEquals(500, StartCopy.stepRep(reps, up = true).meters)
        assertEquals(300, StartCopy.stepRep(reps, up = false).meters)
        assertEquals(1500, StartCopy.stepRep(reps.copy(meters = 1000), up = true).meters)
        assertEquals(900, StartCopy.stepRep(reps.copy(meters = 1000), up = false).meters)
        assertEquals(1000, StartCopy.stepRep(reps.copy(meters = 1500), up = false).meters)
        assertEquals(100, StartCopy.stepRep(reps.copy(meters = 100), up = false).meters)
        val timed = org.openstrap.app.data.Structure.Reps(null, 240, 180, 4)
        assertEquals(270L, StartCopy.stepRep(timed, up = true).seconds)
        assertEquals("400 m", StartCopy.repText(reps))
        assertEquals("4 min", StartCopy.repText(timed))
    }

    @Test fun `a game's note says what the strap does`() {
        assertEquals("Phone can stay in your bag", StartCopy.gamesNote(null))
        assertEquals("The strap buzzes each half and break", StartCopy.gamesNote(org.openstrap.app.data.Structure.HALVES))
        assertEquals(
            "The strap buzzes each quarter",
            StartCopy.gamesNote(org.openstrap.app.data.Structure.QUARTERS.copy(rest = 0)),
        )
    }

    @Test fun `last time says what and when`() {
        val tuesday = LocalDate.of(2026, 10, 27).atTime(18, 0).toEpochSecond(ZoneOffset.UTC)
        val run = WorkoutAnalysis(Workout(tuesday, tuesday + 1800, 1, 1800, null, null, null, null, null, null, null, null, null),
            tuesday + 1800, emptyList(), null, null, null, null, List(5) { 0 }, 190, fromPhone = true, distanceMeters = 5210f)
        assertEquals("Last time 5.21 km, Tuesday", StartCopy.lastTime(run, today, ZoneOffset.UTC, Locale.UK))
        // The decimal mark is the language's.
        assertEquals("Last time 5,21 km, Dienstag", StartCopy.lastTime(run, today, ZoneOffset.UTC, Locale.GERMANY))
        val lift = run.copy(workout = run.workout.copy(sportType = 52), distanceMeters = null, sets = 5)
        assertEquals("Last time 5 sets, Tuesday", StartCopy.lastTime(lift, today, ZoneOffset.UTC, Locale.UK))
        val yoga = run.copy(workout = run.workout.copy(sportType = 60), distanceMeters = null)
        assertEquals("Last time 30 min, Tuesday", StartCopy.lastTime(yoga, today, ZoneOffset.UTC, Locale.UK))
    }

    @Test fun `days are today, yesterday, a weekday, then a date`() {
        fun on(day: LocalDate) = WorkoutAnalysis(
            Workout(day.atTime(18, 0).toEpochSecond(ZoneOffset.UTC), 0, 52, 1800, null, null, null, null, null, null, null, null, null),
            0, emptyList(), null, null, null, null, List(5) { 0 }, 190, fromPhone = true, sets = 1,
        )
        assertEquals("Last time 1 set, today", StartCopy.lastTime(on(today), today, ZoneOffset.UTC, Locale.UK))
        assertEquals("Last time 1 set, yesterday", StartCopy.lastTime(on(today.minusDays(1)), today, ZoneOffset.UTC, Locale.UK))
        assertEquals("Last time 1 set, Saturday", StartCopy.lastTime(on(today.minusDays(6)), today, ZoneOffset.UTC, Locale.UK))
        // A week back, the date with its weekday, as every day word reads.
        assertEquals("Last time 1 set, Fri 23 Oct", StartCopy.lastTime(on(today.minusDays(7)), today, ZoneOffset.UTC, Locale.UK))
    }

    @Test fun `rounds are named by their preset, else by what they are`() {
        assertEquals("Tabata", StartCopy.structureName(org.openstrap.app.data.Structure.TABATA))
        assertEquals("Tabata", StartCopy.structureName(org.openstrap.app.data.Structure.TABATA.warm(true)))
        assertEquals("40 / 20", StartCopy.structureName(org.openstrap.app.data.Structure.FORTY_TWENTY))
        assertEquals("6 × 400 m", StartCopy.structureName(org.openstrap.app.data.Structure.Reps(400, null, 90, 6)))
        assertEquals("8 × 2 min", StartCopy.structureName(org.openstrap.app.data.Structure.Reps(null, 120, 60, 8)))
        // As the rep field under it says it, not "1500 m".
        assertEquals("6 × 1.5 km", StartCopy.structureName(org.openstrap.app.data.Structure.Reps(1500, null, 90, 6)))
        assertEquals(230L, StartCopy.totalTime(org.openstrap.app.data.Structure.TABATA))
        // Reps fit a run, rounds fit HIIT, never the other way round.
        assertEquals(true, org.openstrap.app.data.Structure.fits(org.openstrap.app.data.Structure.Reps(400, null, 90, 6), 2))
        assertEquals(false, org.openstrap.app.data.Structure.fits(org.openstrap.app.data.Structure.TABATA, 1))
        assertEquals(true, org.openstrap.app.data.Structure.fits(org.openstrap.app.data.Structure.TABATA, 49))
    }

    @Test fun `the Outdoor switch says how close the GPS fix is, and what indoors gives`() {
        assertEquals("GPS for distance and a map, finding you", StartCopy.outdoorNote(null))
        assertEquals("GPS for distance and a map, 48 m so far", StartCopy.outdoorNote(48f))
        assertEquals("GPS for distance and a map, ready to 8 m", StartCopy.outdoorNote(8f))
        assertEquals("GPS for distance and a map, ready to 6 m", StartCopy.outdoorNote(6.4f))
        assertEquals("Distance from your steps", StartCopy.indoorLine(2))
        assertEquals("No distance indoors", StartCopy.indoorLine(8))
    }

    @Test fun `what the strap will do, without banned punctuation`() {
        val reps = org.openstrap.app.data.Structure.Reps(400, null, 90, 6)
        val all = listOf(
            StartCopy.noGoalNote(true), StartCopy.distanceNote(7500), StartCopy.ZONE_NOTE, StartCopy.intervalsNote(reps),
            StartCopy.ASK_TO_END_NOTE, StartCopy.indoorLine(8), StartCopy.indoorLine(2), StartCopy.outdoorNote(6f),
            HeartZonesCopy.NOTE, HeartZonesCopy.SET_NOTE, HeartZonesCopy.edgesNote(0), HeartZonesCopy.edgesNote(4),
            HeartZonesCopy.source(org.openstrap.app.data.MaxHeartRate(190, org.openstrap.app.data.MaxHeartRate.Source.Guess)),
            StartCopy.OPEN_SETS_NOTE, StartCopy.endRestNote(110), StartCopy.LOG_SETS_NOTE, StartCopy.HIIT_FREE_NOTE,
            StartCopy.WARM_UP_NOTE, StartCopy.MIND_OPEN_NOTE, StartCopy.gamesNote(org.openstrap.app.data.Structure.QUARTERS),
        )
        all.forEach { s -> assertFalse(s, s.any { it in "·;—–" }) }
    }

    @Test fun `Start is named for the sport`() {
        assertEquals("Start run", StartCopy.startLabel(2))
        assertEquals("Start run", StartCopy.startLabel(1))
    }

    @Test fun `a distance reads as typed`() {
        assertEquals("5", StartCopy.distanceValue(5000))
        assertEquals("7.5", StartCopy.distanceValue(7500))
        assertEquals("12.45", StartCopy.distanceValue(12_450))
        assertEquals("21.1", StartCopy.distanceValue(21_097))
        assertEquals("A buzz at halfway and at 7.5 km", StartCopy.distanceNote(7500))
    }

    @Test fun `a time reads in hours and minutes`() {
        assertEquals("1 h 15", StartCopy.timeValue(4500))
        assertEquals("45 min", StartCopy.timeValue(2700))
        assertEquals("1 h", StartCopy.timeValue(3600))
    }

    @Test fun `run intervals' default, parts and note`() {
        assertEquals("6 × 400 m", StartCopy.structureName(StartCopy.DEFAULT_INTERVALS))
        val reps = org.openstrap.app.data.Structure.Reps(400, null, 90, 6)
        assertEquals("400" to "m", StartCopy.repParts(reps))
        assertEquals("1.5" to "km", StartCopy.repParts(reps.copy(meters = 1500)))
        assertEquals("4" to "min", StartCopy.repParts(reps.copy(meters = null, seconds = 240)))
        assertEquals("90" to "s", StartCopy.repParts(reps.copy(meters = null, seconds = 90)))
        assertEquals("A buzz each 400 m rep, 2.4 km in all", StartCopy.intervalsNote(reps))
        assertEquals("A buzz each 200 m rep, 800 m in all", StartCopy.intervalsNote(reps.copy(meters = 200, rounds = 4)))
        assertEquals(
            "A buzz each 4 min rep, 16 min in all",
            StartCopy.intervalsNote(org.openstrap.app.data.Structure.Reps(null, 240, 180, 4)),
        )
    }

    @Test fun `no goal says what's recorded`() {
        assertEquals("Records your time, distance and heart rate", StartCopy.noGoalNote(hasDistance = true))
        assertEquals("Records your time and heart rate", StartCopy.noGoalNote(hasDistance = false))
    }

    @Test fun `the zones page says where your max comes from and each zone's heart rates`() {
        val d = LocalDate.of(2026, 10, 3)
        assertEquals("Learned from hard efforts, 3 Oct", HeartZonesCopy.source(org.openstrap.app.data.MaxHeartRate(192, org.openstrap.app.data.MaxHeartRate.Source.Learned, d), Locale.UK))
        assertEquals("Set by you", HeartZonesCopy.source(org.openstrap.app.data.MaxHeartRate(185, org.openstrap.app.data.MaxHeartRate.Source.Set)))
        val zones = org.openstrap.app.data.HeartZones.STANDARD
        assertEquals("96 to 115 bpm", HeartZonesCopy.zoneBpm(zones, 0, 192))
        assertEquals("173 bpm and up", HeartZonesCopy.zoneBpm(zones, 4, 192))
        assertEquals("Zone 2", HeartZonesCopy.zoneName(1))
        assertEquals(185, HeartZonesCopy.parseMax("185"))
        assertEquals(null, HeartZonesCopy.parseMax("100"))
    }
}
