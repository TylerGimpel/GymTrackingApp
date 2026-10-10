package com.tylergimpel.traintracks.domain

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
    fun `a name the bank shipped an exercise under before renaming it is matched to that exercise`() {
        // FitNotes calls it "Barbell Squat", which is what the bank did until #149 made it
        // "Back Squat". The same lift, so the same history.
        val backSquat = squat.copy(bankName = "Back Squat")
        val formerNames = mapOf("Barbell Squat" to "Back Squat")

        assertEquals(backSquat, matchExactly(setOf("barbell squat"), listOf(backSquat), formerNames)["barbell squat"])
        assertEquals(emptyList<Unmatched>(), unmatchedIn(NeutralImport(source = FITNOTES, fingerprint = "f", sets = listOf(importedSet("Barbell Squat"))), listOf(backSquat), formerNames))
    }

    @Test
    fun `a former name never outranks an exercise that is called that now`() {
        // The lifter's own "Barbell Squat" is what they mean by it, not the bank's renamed row.
        val backSquat = squat.copy(bankName = "Back Squat")
        val theirs = exercise("Their Squat").copy(id = "user_1", userName = "Barbell Squat")

        val matched = matchExactly(setOf("Barbell Squat"), listOf(backSquat, theirs), mapOf("Barbell Squat" to "Back Squat"))

        assertEquals(theirs, matched["Barbell Squat"])
    }

    @Test
    fun `a dropped bank row is not somewhere to put new history`() {
        val dropped = squat.copy(isTombstoned = true)

        assertNull(matchExactly(setOf("Barbell Squat"), listOf(dropped))["Barbell Squat"])
    }

    @Test
    fun `the names needing a decision are listed once each, with how much history hangs on them`() {
        val records = NeutralImport(
            source = FITNOTES,
            fingerprint = "f",
            sets = listOf(
                importedSet("Hammer Strength Row"),
                importedSet("Barbell Squat"),
                importedSet("Hip Thrust Machine"),
                importedSet("Hammer Strength Row"),
            ),
        )

        // Once per name, never per set, and the ones with the most history first: that is where a
        // wrong decision would cost the most (#99).
        assertEquals(
            listOf(Unmatched("Hammer Strength Row", sets = 2), Unmatched("Hip Thrust Machine", sets = 1)),
            unmatchedIn(records, listOf(squat, bench)),
        )
    }

    @Test
    fun `each kind of set maps to the logging type that records it`() {
        // A plank that arrives as a hold is a timed exercise, not a weight x reps one with no
        // weight: the created exercise is whatever the history says it was.
        assertEquals(LoggingType.TIME, loggingTypeOf(Performance.Timed(java.time.Duration.ofSeconds(40))))
        assertEquals(LoggingType.BODYWEIGHT_REPS, loggingTypeOf(Performance.BodyweightReps(null, 12)))
        assertEquals(LoggingType.WEIGHT_REPS, loggingTypeOf(Performance.WeightAndReps(Kilograms(60.0), 8)))
        assertEquals(
            LoggingType.DISTANCE_TIME,
            loggingTypeOf(Performance.DistanceAndTime(Metres(5000.0), java.time.Duration.ofMinutes(28))),
        )
        assertEquals(LoggingType.ASSISTED, loggingTypeOf(Performance.Assisted(Kilograms(20.0), 8)))
    }

    @Test
    fun `a preview counts what an import will bring in, one session a date`() {
        val records = NeutralImport(
            source = FITNOTES,
            fingerprint = "f",
            sets = listOf(
                importedSet("Barbell Squat").copy(date = LocalDate.parse("2024-01-08")),
                importedSet("Barbell Squat").copy(date = LocalDate.parse("2024-01-08")),
                importedSet("Hammer Strength Row").copy(date = LocalDate.parse("2026-03-02")),
                importedSet("Bench Press").copy(date = LocalDate.parse("2025-06-10")),
            ),
            notYetSupported = mapOf("unreadable" to 3),
        )

        // One session a date, as the import files them (#103): what it says is what it will do.
        assertEquals(
            ImportPreview(
                from = LocalDate.parse("2024-01-08"),
                to = LocalDate.parse("2026-03-02"),
                sessions = 3,
                sets = 4,
                unreadable = 3,
                unmatched = listOf(Unmatched("Hammer Strength Row", sets = 1)),
                alreadyHere = 0,
            ),
            previewOf(records, listOf(squat, bench)),
        )
    }

    @Test
    fun `dates an earlier import from the same source brought in are counted, not brought in again`() {
        val records = NeutralImport(
            source = FITNOTES,
            fingerprint = "a newer backup",
            sets = listOf(
                importedSet("Barbell Squat").copy(date = LocalDate.parse("2024-01-08")),
                importedSet("Hammer Strength Row").copy(date = LocalDate.parse("2024-01-08")),
                importedSet("Bench Press").copy(date = LocalDate.parse("2025-06-10")),
                importedSet("Bench Press").copy(date = LocalDate.parse("2026-03-02")),
            ),
        )

        val preview = previewOf(
            records,
            listOf(squat, bench),
            alreadyHere = setOf(LocalDate.parse("2024-01-08"), LocalDate.parse("2026-03-02")),
        )

        // A newer backup of the same history is mostly the same history (#116): only what is new
        // is counted as coming in, and what is already here is said so.
        assertEquals(1, preview.sessions)
        assertEquals(1, preview.sets)
        assertEquals(2, preview.alreadyHere)
        assertEquals(LocalDate.parse("2025-06-10"), preview.from)
        assertEquals(LocalDate.parse("2025-06-10"), preview.to)
        // A name used only on a date already here needs no decision: nothing of it is coming in.
        assertTrue(preview.unmatched.isEmpty())
    }

    @Test
    fun `a file with nothing readable in it previews as empty rather than failing`() {
        val preview = previewOf(NeutralImport(source = FITNOTES, fingerprint = "f", sets = emptyList()), listOf(squat))

        assertNull(preview.from)
        assertEquals(0, preview.sessions)
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
