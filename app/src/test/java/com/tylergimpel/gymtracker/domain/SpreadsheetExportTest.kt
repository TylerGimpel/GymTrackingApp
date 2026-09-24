package com.tylergimpel.gymtracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * The export for spreadsheets: the lifter's numbers in a file anything can open (#98).
 *
 * **Lossy, on purpose, and asserted.** What it carries and what it flattens are both stated here,
 * so the loss is a decision rather than an accident — the author's own FitNotes CSV dropped 98
 * superset groups with nothing to say so, and this is the test that stops that happening quietly.
 */
class SpreadsheetExportTest {

    @Test
    fun `one header row, then one row per effort, oldest session first`() {
        val csv = spreadsheetExport(
            listOf(
                session("2026-09-16", bench(working(1, 60.0, 8))),
                session("2026-09-14", bench(working(1, 55.0, 8))),
            ),
        ).lines()

        assertEquals(HEADER, csv.first())
        assertEquals("2026-09-14,Bench Press,1,working,,55,,kg,8,,,", csv[1])
        assertEquals("2026-09-16,Bench Press,1,working,,60,,kg,8,,,", csv[2])
    }

    @Test
    fun `a weight is written in the unit the lifter read it in`() {
        val inPounds = working(1, 61.235, 8).copy(displayUnit = WeightUnit.POUNDS)

        val row = spreadsheetExport(listOf(session("2026-09-14", bench(inPounds)))).lines()[1]

        // The number off the machine, and the unit beside it: a pound stack exported as 61.235 kg
        // is a number nobody ever saw on the pin.
        assertEquals("2026-09-14,Bench Press,1,working,,135,,lb,8,,,", row)
    }

    @Test
    fun `a drop set's drops and a per-side set's sides are rows of their own`() {
        val dropSet = PerformedSet(
            setIndex = 1,
            type = SetType.WORKING,
            efforts = listOf(
                Effort(
                    id = 1,
                    side = null,
                    performance = Performance.WeightAndReps(Kilograms(100.0), 8),
                    note = null,
                    drops = listOf(Drop(id = 2, orderIndex = 1, weight = Kilograms(80.0), reps = 6)),
                ),
            ),
        )
        val perSide = PerformedSet(
            setIndex = 2,
            type = SetType.WORKING,
            efforts = listOf(
                effort(Side.LEFT, Performance.WeightAndReps(Kilograms(20.0), 10)),
                effort(Side.RIGHT, Performance.WeightAndReps(Kilograms(20.0), 9)),
            ),
        )

        val rows = spreadsheetExport(listOf(session("2026-09-14", bench(dropSet, perSide)))).lines()

        // Flattened: the drop is a row typed "drop" under the set it belongs to, and each side is
        // its own row. What a spreadsheet cannot hold is the grouping, which the backup keeps.
        assertEquals("2026-09-14,Bench Press,1,working,,100,,kg,8,,,", rows[1])
        assertEquals("2026-09-14,Bench Press,1,drop,,80,,kg,6,,,", rows[2])
        assertEquals("2026-09-14,Bench Press,2,working,left,20,,kg,10,,,", rows[3])
        assertEquals("2026-09-14,Bench Press,2,working,right,20,,kg,9,,,", rows[4])
    }

    @Test
    fun `a hold has a duration, help has its own column, and a run has a distance`() {
        val plank = exercise("Plank")
        val pullUp = exercise("Assisted Pull-Up")
        val run = exercise("Running")

        val rows = spreadsheetExport(
            listOf(
                session(
                    "2026-09-14",
                    performed(plank, 1, PerformedSet(1, SetType.WORKING, listOf(effort(null, Performance.Timed(Duration.ofSeconds(90)))))),
                    performed(pullUp, 2, PerformedSet(1, SetType.WORKING, listOf(effort(null, Performance.Assisted(Kilograms(20.0), 8))))),
                    performed(
                        run,
                        3,
                        PerformedSet(1, SetType.WORKING, listOf(effort(null, Performance.DistanceAndTime(Metres(5000.0), Duration.ofMinutes(28))))),
                    ),
                ),
            ),
        ).lines()

        // Assistance is not a negative weight to anybody reading a spreadsheet, so it is said as
        // what it is, in a column of its own.
        assertEquals("2026-09-14,Plank,1,working,,,,,,90,,", rows[1])
        assertEquals("2026-09-14,Assisted Pull-Up,1,working,,,20,kg,8,,,", rows[2])
        assertEquals("2026-09-14,Running,1,working,,,,,,1680,5000,", rows[3])
    }

    @Test
    fun `names and notes with commas or quotes survive being opened`() {
        val set = PerformedSet(
            1,
            SetType.WORKING,
            listOf(
                Effort(
                    id = 1,
                    side = null,
                    performance = Performance.WeightAndReps(Kilograms(60.0), 8),
                    note = "grip \"wide\", slow",
                ),
            ),
        )

        val row = spreadsheetExport(listOf(session("2026-09-14", performed(exercise("Row, Seated"), 1, set)))).lines()[1]

        assertEquals("2026-09-14,\"Row, Seated\",1,working,,60,,kg,8,,,\"grip \"\"wide\"\", slow\"", row)
    }

    @Test
    fun `a name or note that starts like a formula is shown rather than run`() {
        val set = PerformedSet(
            1,
            SetType.WORKING,
            listOf(Effort(id = 1, side = null, performance = Performance.WeightAndReps(Kilograms(60.0), 8), note = "=HYPERLINK(1)")),
        )

        val row = spreadsheetExport(listOf(session("2026-09-14", performed(exercise("+Cable Fly"), 1, set)))).lines()[1]

        // Typed text is the lifter's; a spreadsheet running it as a formula would be the export
        // doing something with their note that they never asked for.
        assertEquals("2026-09-14,'+Cable Fly,1,working,,60,,kg,8,,,'=HYPERLINK(1)", row)
    }

    @Test
    fun `supersets are not in it, and it says so rather than pretending`() {
        // Asserted so the loss is stable: nothing in the header can carry a grouping, which is the
        // first thing the settings screen says about this file (#98).
        assertFalse(HEADER.contains("superset", ignoreCase = true))
        assertEquals(
            listOf("Date", "Exercise", "Set", "Type", "Side", "Weight", "Assistance", "Unit", "Reps", "Duration (s)", "Distance (m)", "Note"),
            HEADER.split(","),
        )
    }

    private val bench = exercise("Bench Press")

    private fun bench(vararg sets: PerformedSet) = performed(bench, 1, *sets)

    private fun performed(exercise: Exercise, order: Int, vararg sets: PerformedSet) =
        SessionExercise(id = order.toLong(), exercise = exercise, orderIndex = order, sets = sets.toList())

    private fun session(date: String, vararg exercises: SessionExercise) = LoggedSession(
        session = Session(
            id = date.hashCode().toLong(),
            startedAt = Instant.parse("${date}T18:00:00Z"),
            finishedAt = Instant.parse("${date}T19:00:00Z"),
            date = LocalDate.parse(date),
            note = null,
        ),
        exercises = exercises.toList(),
    )

    private fun working(index: Int, kg: Double, reps: Int) = PerformedSet(
        setIndex = index,
        type = SetType.WORKING,
        efforts = listOf(effort(null, Performance.WeightAndReps(Kilograms(kg), reps))),
    )

    private fun effort(side: Side?, performance: Performance) =
        Effort(id = 1, side = side, performance = performance, note = null)

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

    private companion object {
        val HEADER = SPREADSHEET_HEADER
    }
}
