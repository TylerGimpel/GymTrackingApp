package com.tylergimpel.gymtracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The format-agnostic half of importing: what a source declares, and how names meet the bank.
 *
 * Nothing here knows about FitNotes' file. That is ADR 0007's seam: a second source is one new
 * parser and nothing else, so everything that is not parsing is asserted without one.
 */
class ImportTest {

    @Test
    fun `volume is approximate for a source that never recorded set types, not for importing`() {
        // FitNotes has no warm-ups or drop sets, so a counted set might have been either. That is
        // a fact about FitNotes. A source that says what each set was is as trustworthy as the log.
        assertFalse(FITNOTES.volumeIsReliable)
        assertTrue(ImportSource(id = "declares-types", declaresSetTypes = true, declaresSupersets = false).volumeIsReliable)
    }

    @Test
    fun `a set's type is absent where the source never said it, and working where it arrives`() {
        val fromFitNotes = importedSet("Bench Press")

        // Absent rather than defaulted: the absence is what the reliability marking rests on.
        assertNull(fromFitNotes.setType)
        assertEquals(SetType.WORKING, fromFitNotes.typeOnArrival)
        assertEquals(SetType.WARMUP, fromFitNotes.copy(setType = SetType.WARMUP).typeOnArrival)
    }

    @Test
    fun `a name that is the bank's own, spacing and case aside, is matched`() {
        val matched = matchExactly(setOf("  barbell   squat ", "Bench Press"), listOf(squat, bench))

        assertEquals(squat, matched["  barbell   squat "])
        assertEquals(bench, matched["Bench Press"])
    }

    @Test
    fun `a name the lifter renamed an exercise to is matched to that exercise`() {
        val renamed = bench.copy(userName = "Flat bench")

        assertEquals(renamed, matchExactly(setOf("Flat Bench"), listOf(renamed))["Flat Bench"])
    }

    @Test
    fun `a near miss is never matched, however near`() {
        // The tempting improvement, pinned against: "Hammer Strength Row" onto "Barbell Row" would
        // fuse two lifts' histories and corrupt progression for both. Near is not the same.
        val matched = matchExactly(
            setOf("Barbell Squats", "Bench Press (Barbell)", "Hammer Strength Row"),
            listOf(squat, bench, exercise("Barbell Row")),
        )

        assertEquals(mapOf("Barbell Squats" to null, "Bench Press (Barbell)" to null, "Hammer Strength Row" to null), matched)
    }

    @Test
    fun `a dropped bank row is not somewhere to put new history`() {
        val dropped = squat.copy(isTombstoned = true)

        assertNull(matchExactly(setOf("Barbell Squat"), listOf(dropped))["Barbell Squat"])
    }

    private val squat = exercise("Barbell Squat")
    private val bench = exercise("Bench Press")

    private fun importedSet(name: String) = ImportedSet(
        date = LocalDate.parse("2026-03-02"),
        exerciseName = name,
        order = 1,
        performance = Performance.WeightAndReps(Kilograms(60.0), 8),
    )

    private fun exercise(name: String) = Exercise(
        id = name.replace(' ', '_'),
        bankName = name,
        primaryMuscles = listOf("chest"),
        secondaryMuscles = emptyList(),
        equipment = "barbell",
        mechanic = "compound",
        force = "push",
        category = "strength",
        level = "beginner",
        loggingType = LoggingType.WEIGHT_REPS,
        unilateral = false,
    )
}
