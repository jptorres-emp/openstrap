package org.openstrap.app.presentation

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.openstrap.app.data.Route
import org.openstrap.app.data.WorkoutTarget
import org.openstrap.app.data.settings.UnitSetting
import org.openstrap.app.data.settings.UnitSystem
import org.openstrap.app.data.settings.UserProfile
import org.openstrap.app.presentation.Units.Precision
import org.openstrap.app.testing.LocaleRule
import org.openstrap.app.ui.glow.typedMeters
import org.openstrap.app.ui.workout.StartCopy
import org.openstrap.app.ui.workout.SummaryCopy
import java.util.Locale

/** The Imperial choice: miles, feet, paces and splits per mile, and goals in miles. Stored distances stay metres. */
class MilesTest {
    @get:Rule val localeRule = LocaleRule(Locale.US)

    @Before fun imperial() { UnitSetting.system = UnitSystem.Imperial }

    @After fun metricAgain() { UnitSetting.system = UnitSystem.Metric }

    @Test fun `the profile's choice picks the system`() {
        assertEquals(UnitSystem.Imperial, UserProfile(imperial = true).unitSystem)
        assertEquals(UnitSystem.Metric, UserProfile(imperial = false).unitSystem)
    }

    @Test fun `a workout shows two decimals and a day one, as in km`() {
        assertEquals("3.11 mi", Units.distance(5000f, Precision.Workout))
        assertEquals("3.1 mi", Units.distance(5000f, Precision.Day))
        assertEquals("1.00 mi", Units.distance(1609.344f, Precision.Workout))
        assertEquals("26.2 mi", Units.distance(42_195f, Precision.Day))
        assertEquals("≈ 3.1 mi", Units.distance(5000f, Precision.Day, fromSteps = true))
    }

    @Test fun `under a tenth of a mile is feet and under ten metres nothing`() {
        assertEquals("524 ft", Units.distance(160f, Precision.Workout))
        assertEquals("328 ft", Units.distance(100f, Precision.Day))
        assertEquals("32 ft", Units.distance(10f, Precision.Day))
        assertNull(Units.distance(9.9f, Precision.Day))
        assertEquals("0.10 mi", Units.distance(161f, Precision.Workout))
        assertEquals("0.1 mi", Units.distance(161f, Precision.Day))
        assertFalse(Units.isMajor(160.9f))
        assertTrue(Units.isMajor(160.94f))
    }

    @Test fun `a figure and its unit come apart`() {
        assertEquals(Units.Figure("3.11", "mi"), Units.figure(5000f, Precision.Workout))
        assertEquals(Units.Figure("328", "ft"), Units.figure(100f, Precision.Workout))
        assertNull(Units.figure(5f, Precision.Workout))
        assertEquals("mi", Units.majorUnit())
        assertEquals("ft", Units.minorUnit())
        assertEquals("/mi", Units.paceUnit())
        assertEquals("mph", Units.speedUnit())
    }

    @Test fun `pace and speed are per mile`() {
        // 324 s a km is 521 s a mile.
        assertEquals("8:41", Pace.clock(324.0))
        assertEquals("8:41/mi", Pace.text(324.0))
        assertEquals("7.5", Units.speed(300.0))
        assertNull(Pace.text(3601.0))
        assertNull(Units.speed(null))
        // The pace itself is still worked out in seconds a km.
        assertEquals(324.0, Pace.of(5000f, 1620)!!, 1e-9)
    }

    @Test fun `the same figures in km after switching back`() {
        UnitSetting.system = UnitSystem.Metric
        assertEquals("5.21 km", Units.distance(5210f, Precision.Workout))
        assertEquals("5:24/km", Pace.text(324.0))
        assertEquals("12.0", Units.speed(300.0))
    }

    private fun run(seconds: Int) = (0..seconds).map { Route.Point(it.toLong(), 0.0, it * 3.0 / 111_195.0, 5f) }

    @Test fun `splits come a mile at a time on a known route`() {
        // 3 m/s for 1000 s is 3000 m: one whole mile and 1390 m over.
        val splits = Route.splits(run(1000))
        assertEquals(2, splits.size)
        assertEquals(1609.344, splits[0].meters, 12.0)
        assertTrue(splits[1].meters < Units.splitMeters)
        assertEquals(3000.0, splits.sumOf { it.meters }, 40.0)
        // A mile at 3 m/s takes 536 s, which is 333 s a km all the same.
        assertEquals(333.0, splits[0].pace, 2.0)
    }

    @Test fun `the splits list names miles and a last part in miles`() {
        val splits = listOf(
            Route.Split(0, 100L + 520, 520, 1609.4),
            Route.Split(1, 100L + 700, 180, 482.0),
        )
        val rows = SummaryCopy.splitRows(splits, emptyList(), 100L, totalMeters = 2092f)
        assertEquals(listOf("Mile 1", "Last 0.30 mi"), rows.map { it.name })
        assertEquals("8:40", rows[0].time)
        assertEquals("Mile 2 in 8:40", WorkoutWords.split(1, 520))
    }

    @Test fun `a distance goal is typed and shown in miles`() {
        assertEquals(4989, typedMeters("3.1", 200))
        assertEquals(21_082, typedMeters("13.1", 200))
        assertEquals(161, typedMeters("0.1", 200))
        assertEquals(321_869, typedMeters("900", 200))
        assertEquals("5", StartCopy.distanceValue(8047))
        assertEquals("13.1", StartCopy.distanceValue(21_082))
        assertEquals(200, StartCopy.maxDistanceUnits)
        assertEquals("5 mi", WorkoutTarget.Distance(8047).label)
        assertEquals("13.1 mi", WorkoutTarget.Distance(21_082).label)
        assertEquals("328 ft", WorkoutTarget.Distance(100).label)
        assertTrue(StartCopy.distanceNote(8047).endsWith("5 mi"))
    }

    @Test fun `the progress toward a goal reads in the goal's unit`() {
        assertEquals("2.50 of 5 mi", WorkoutCopy.distanceProgress(4023f, WorkoutTarget.Distance(8047)))
    }
}
