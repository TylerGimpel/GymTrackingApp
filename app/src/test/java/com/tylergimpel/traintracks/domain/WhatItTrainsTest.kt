package com.tylergimpel.traintracks.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a routine does, said as a fact.
 *
 * **The register is the whole of this.** "This cycle trains chest 3× and back 1×" helps
 * somebody decide; "your routine is unbalanced" is a grade nobody asked for. So everything
 * here is a count — how often, how much, where that sits — and nothing here is a verdict.
 *
 * Nothing here refuses anything either. A lifter who wants a lopsided routine has made a decision
 * that is theirs, and the app's job is to make sure they made it knowingly.
 */
class WhatItTrainsTest {

    @Test
    fun `a routine says how often it trains each muscle`() {
        val routine = routineOf(
            day(bench to 4),
            day(bench to 4),
            day(row to 4),
        )

        val load = routine.whatItTrains().associateBy { it.muscle }

        // The sentence the ticket is named for, as two numbers the screen can print.
        assertEquals(2, load.getValue("chest").sessions)
        assertEquals(1, load.getValue("middle back").sessions)
    }

    @Test
    fun `the heaviest trained muscle is reported first`() {
        val routine = routineOf(day(row to 4), day(bench to 4), day(bench to 4))

        // Ordered so the contrast is the first thing read. A lifter scanning this is looking for
        // what is getting the most and the least, not for an alphabet.
        assertEquals("chest", routine.whatItTrains().first().muscle)
    }

    @Test
    fun `a muscle the cycle never trains is simply absent`() {
        val routine = routineOf(day(bench to 4))

        // Absence rather than a row of zeroes for all forty muscles in the bank. What is missing
        // is said by the coverage finding in what is binding, not by a report of what is there.
        assertTrue(routine.whatItTrains().none { it.muscle == "middle back" })
    }

    @Test
    fun `an unbalanced routine is reported and never rejected`() {
        val routine = routineOf(day(bench to 6), day(bench to 6), day(bench to 6), day(row to 2))

        val load = routine.whatItTrains().associateBy { it.muscle }

        // Chest three times a week and back once. Nothing about this returns an error, throws, or
        // comes back empty — it comes back as the numbers, and the lifter decides.
        assertEquals(3, load.getValue("chest").sessions)
        assertEquals(1, load.getValue("middle back").sessions)
        assertTrue(load.getValue("chest").weeklySets > load.getValue("middle back").weeklySets)
    }

    @Test
    fun `how much a muscle gets is said in the tiers the research uses`() {
        // Three lifting days a week, six chest sets each: eighteen weekly sets, the top of the
        // intermediate tier. Said as where it sits rather than as whether it is enough.
        val routine = routineOf(day(bench to 6), day(bench to 6), day(bench to 6), cadence = 3)

        val chest = routine.whatItTrains().single { it.muscle == "chest" }

        assertEquals(18.0, chest.weeklySets, 0.001)
        assertEquals(VolumeTier.INTERMEDIATE_EFFICIENCY, chest.tier)
    }

    @Test
    fun `a day asking more of one muscle than a session can use says so`() {
        val routine = routineOf(day(bench to 12))

        val chest = routine.whatItTrains().single { it.muscle == "chest" }

        // Twelve in one session, past the point where more sets in the same session stop showing
        // up. A fact about how the work is distributed, not a reason to refuse the routine.
        assertEquals(12.0, chest.mostInOneSession, 0.001)
        assertTrue(chest.pastSessionCap)
    }

    @Test
    fun `a day inside the cap does not claim otherwise`() {
        val routine = routineOf(day(bench to 4), day(bench to 4))

        assertFalse(routine.whatItTrains().single { it.muscle == "chest" }.pastSessionCap)
    }

    @Test
    fun `a cardio day is not counted as training a muscle`() {
        val routine = routineOf(
            day(bench to 4),
            RoutineDay(id = 9, orderIndex = 9, name = "Run", kind = RoutineDayKind.CARDIO),
        )

        assertEquals(1, routine.whatItTrains().single { it.muscle == "chest" }.sessions)
    }

    @Test
    fun `a routine nobody has put anything in reports nothing rather than failing`() {
        assertEquals(emptyList<MuscleLoad>(), routineOf().whatItTrains())
    }

    private companion object {

        fun day(vararg entries: Pair<Exercise, Int>) = RoutineDay(
            id = 0,
            orderIndex = 0,
            name = "Day",
            exercises = entries.map { (exercise, sets) -> PlannedExercise(exercise, sets) },
        )

        fun routineOf(vararg days: RoutineDay, cadence: Int = days.size.coerceAtLeast(1)) = Routine(
            id = 1,
            name = "Routine",
            goal = RoutineGoal.HYPERTROPHY,
            cadence = cadence,
            equipment = emptySet(),
            repPreference = RepPreference.MODERATE,
            days = days.mapIndexed { index, day ->
                day.copy(id = index + 1L, orderIndex = index + 1)
            },
        )

        fun exercise(id: String, primary: String, secondary: List<String> = emptyList()) = Exercise(
            id = id,
            bankName = id,
            primaryMuscles = listOf(primary),
            secondaryMuscles = secondary,
            equipment = "barbell",
            mechanic = "compound",
            force = "push",
            category = "strength",
            level = "intermediate",
            loggingType = LoggingType.WEIGHT_REPS,
            unilateral = false,
        )

        val bench = exercise("Bench", "chest")
        val row = exercise("Row", "middle back")
    }
}
