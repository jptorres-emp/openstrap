package org.openstrap.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SportGroupsTest {
    private fun names(s: SportSection) = s.types.map { Workouts.shortName(it) }

    @Test
    fun hiitIsInStrengthAndIntervalsAtTheEnd() {
        assertEquals(SportGroup.StrengthIntervals, SportGroups.of(49))
        assertEquals(SportGroup.StrengthIntervals, SportGroups.of(52))
        val s = SportGroups.sections(listOf(49, 52, 50)).single()
        assertEquals(listOf(52, 50, 49), s.types)
    }

    @Test
    fun freeTrainingIsOtherNotIndoorCardio() {
        assertEquals(SportGroup.Other, SportGroups.of(5))
        assertEquals(SportGroup.Other, SportGroups.of(Workouts.GENERIC_ACTIVITY))
        assertEquals(SportGroup.IndoorCardio, SportGroups.of(23))
    }

    @Test
    fun sectionsComeInBoardOrderAndSkipEmptyOnes() {
        val s = SportGroups.sections(listOf(60, 1, 17))
        assertEquals(listOf(SportGroup.Distance, SportGroup.MindBody, SportGroup.Games), s.map { it.group })
    }

    @Test
    fun everyOfferedSportIsInExactlyOneSection() {
        val sections = SportGroups.chooseSections()
        val all = sections.flatMap { it.types }
        assertEquals(SportGroups.OFFERED.sorted(), all.sorted())
        assertEquals(all.size, all.toSet().size)
        assertTrue(sections.all { it.types.isNotEmpty() })
    }

    @Test
    fun chooseFollowsTheBoard() {
        val byGroup = SportGroups.chooseSections().associate { it.group to names(it) }
        assertEquals(listOf("Run", "Walk", "Cycling"), byGroup[SportGroup.Distance])
        assertEquals(listOf("Strength", "Core training", "HYROX training", "HIIT"), byGroup[SportGroup.StrengthIntervals])
        assertEquals(listOf("Yoga", "Pilates", "Stretching"), byGroup[SportGroup.MindBody])
        assertEquals(listOf("Other"), byGroup[SportGroup.Other])
        assertEquals(SportGroup.entries.toList(), SportGroups.chooseSections().map { it.group })
    }
}
