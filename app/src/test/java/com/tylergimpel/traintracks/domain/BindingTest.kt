package com.tylergimpel.traintracks.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

/**
 * What happens when the constraints cannot all be met, which is ordinary rather than exceptional.
 *
 * **Nothing here returns an error and nothing here refuses.** An impossible request is a
 * conversation: the app says what is binding and what would give if the lifter relaxed it, and the
 * lifter decides. Every conflict resolves *and* explains itself, because an override nobody is
 * told about produces training that will not deliver the goal and nobody finds out for months.
 */
class BindingTest {

    @Test
    fun `a routine that fits reports nothing binding`() {
        val findings = bindingOn(everyMainMuscle)

        // Every main muscle trained directly, each inside the hypertrophy band, and no day near
        // ninety minutes. Nothing to say, and the app says nothing.
        assertTrue(findings.toString(), findings.isEmpty())
    }

    @Test
    fun `a main muscle the cycle never trains is said by name`() {
        val missing = bindingOn(fittingDay).filterIsInstance<Binding.MuscleNotTrained>()

        // Fourteen sets of chest and nothing else. Legs never trained is the one fact most worth
        // knowing about this routine, and the report of what it trains cannot say it: an
        // untrained muscle is absent from that report, not a row of zeroes.
        assertEquals(MAIN_MUSCLES.toSet() - "chest", missing.map { it.muscle }.toSet())
        val hamstrings = missing.single { it.muscle == "hamstrings" }
        assertEquals(0.0, hamstrings.weeklySets, 0.0)
        assertTrue(hamstrings.relax.isNotEmpty())
    }

    @Test
    fun `a muscle trained only as secondary work is said to be, not called untrained`() {
        val pressing = routineOf(
            day("Push", exercise("Bench", "chest", secondary = listOf("triceps")) to 14),
        )

        val triceps = bindingOn(pressing)
            .filterIsInstance<Binding.MuscleNotTrained>()
            .single { it.muscle == "triceps" }

        // Coverage is by direct work, as the generator's is — but "never trained" would be false
        // about a muscle that gets seven sets a week out of every bench press.
        assertEquals(7.0, triceps.weeklySets, 0.001)
    }

    @Test
    fun `a muscle reported as not trained directly is not reported again as under its band`() {
        val pressing = routineOf(
            day("Push", exercise("Bench", "chest", secondary = listOf("triceps")) to 14),
        )

        val findings = bindingOn(pressing)

        // One fact, said once. Seven sets of triceps is under the band, but the band is not the
        // problem: nothing in the cycle is for triceps at all.
        assertTrue(
            findings.filterIsInstance<Binding.VolumeOutsideBand>().none { it.muscle == "triceps" },
        )
    }

    @Test
    fun `an empty routine is not a list of every muscle`() {
        val empty = routineOf(day("Nothing yet"))

        // The screen already says there is nothing in it. Twelve findings saying the same thing
        // one muscle at a time would bury that under a list.
        assertTrue(bindingOn(empty).filterIsInstance<Binding.MuscleNotTrained>().isEmpty())
    }

    @Test
    fun `a muscle the lifter's exclusions emptied is asked about, not also called untrained`() {
        // The shape asked for chest and back; every chest exercise is banned, so the generator
        // skipped chest and only back reached the days. That is one fact, and the question —
        // which banned exercise is least bad — is the more useful way to say it.
        val shape = SplitShape(
            id = "chest-and-back",
            description = "Chest and back",
            rotation = 1,
            days = listOf(SplitDay("Both", listOf("chest", "middle back"))),
        )
        val allBanned = bank.map {
            if ("chest" in it.primaryMuscles) it.copy(isDisliked = true) else it
        }
        val emptied = routineOf(day("Both", exercise("Row", "middle back") to 14)).copy(
            shape = shape,
            equipment = setOf("barbell", "dumbbell"),
        )

        val findings = bindingOn(emptied, bank = allBanned)

        assertEquals(
            listOf("chest"),
            findings.filterIsInstance<Binding.OnlyDislikedLeft>().map { it.muscle },
        )
        assertTrue(
            findings.filterIsInstance<Binding.MuscleNotTrained>().none { it.muscle == "chest" },
        )
    }

    @Test
    fun `a muscle only ever trained as secondary work is still said to be over its band`() {
        val pressing = routineOf(
            day("Push", exercise("Bench", "chest", secondary = listOf("triceps")) to 40),
        )

        val findings = bindingOn(pressing, ceiling = Duration.ofHours(8))

        // Twenty sets of triceps a week out of the pressing alone. Nothing in the cycle is for
        // triceps, and it is also getting more than the band — two facts, not one said twice.
        assertTrue(findings.filterIsInstance<Binding.MuscleNotTrained>().any { it.muscle == "triceps" })
        assertTrue(findings.filterIsInstance<Binding.VolumeOutsideBand>().any { it.muscle == "triceps" })
    }

    @Test
    fun `a day that will not fit the time says so and says what to relax`() {
        val findings = bindingOn(longDay)

        val tooLong = findings.filterIsInstance<Binding.SessionTooLong>().single()

        // The time ceiling usually binds before volume does, which is why it is checked first and
        // why it names the day rather than the routine: a lifter shortens a session, not a plan.
        assertEquals("Long", tooLong.day)
        assertTrue(tooLong.estimated > tooLong.ceiling)
        assertTrue(tooLong.relax.isNotEmpty())
    }

    @Test
    fun `the length it estimates is the work plus the rest between it`() {
        // Rest is most of a session and the engine already prescribes it per set, so a ceiling
        // computed without it would be measuring the wrong thing entirely.
        // A day of compounds rests longer than a day of machine isolation work, because the
        // engine prescribes rest from what the set is. A ceiling assuming one number for both
        // would be measuring the wrong session.
        val compounds = lengthOf(longDay)
        val isolation = lengthOf(isolationDay)

        assertTrue(compounds > isolation)
    }

    @Test
    fun `cardio after lifting counts against the ceiling`() {
        val lifting = lengthOf(fittingDay)
        val andThenARun = lengthOf(fittingDay, CardioPlacement.AFTER_LIFTING)

        // It takes no position in the rotation and does not touch the cadence, because the lifting
        // session still happened — but the lifter is in the gym for longer, and this is the one
        // number that has to know it.
        assertTrue(andThenARun > lifting)
    }

    @Test
    fun `cardio on its own day does not lengthen a lifting one`() {
        val lifting = lengthOf(fittingDay)
        val ownDay = lengthOf(fittingDay, CardioPlacement.OWN_DAY)

        assertEquals(lifting, ownDay)
    }

    @Test
    fun `a muscle outside its band is reported with the band it missed`() {
        val findings = bindingOn(barelyAnything, ceiling = Duration.ofHours(4))
            .filterIsInstance<Binding.VolumeOutsideBand>()

        // Two sets of one exercise a week is under any band the app aims at. Said as the numbers
        // rather than as a verdict, like everything else the app tells a lifter about a routine.
        val chest = findings.single { it.muscle == "chest" }
        assertTrue(chest.weeklySets < chest.target.start)
        assertTrue(chest.relax.isNotEmpty())
    }

    @Test
    fun `a strength goal with a high rep preference is resolved and explained`() {
        val conflict = conflictsFor(
            goal = RoutineGoal.STRENGTH,
            preference = RepPreference.HIGH,
            experience = Experience.TRAINED,
            equipment = setOf("barbell"),
        ).single()

        // Settled where the band is seeded; reported here so the lifter meets it in one place
        // with the others.
        assertEquals(RoutineGoal.STRENGTH, conflict.wonBy)
        assertTrue(conflict.explanation.isNotEmpty())
    }

    @Test
    fun `a low rep preference with nothing heavy to lift is overridden and explained`() {
        val conflict = conflictsFor(
            goal = RoutineGoal.HYPERTROPHY,
            preference = RepPreference.LOW,
            experience = Experience.TRAINED,
            equipment = setOf("bands", "body only"),
        ).single()

        // The load ceiling makes a 1-6RM unreachable, so the preference cannot be honoured
        // whatever anybody wants. Said out loud, because a silently ignored preference is a lifter
        // wondering for three months why the app is not doing what they asked.
        assertTrue(conflict.explanation.isNotEmpty())
    }

    @Test
    fun `a novice asking for high reps is steered and nothing is overridden`() {
        val conflict = conflictsFor(
            goal = RoutineGoal.HYPERTROPHY,
            preference = RepPreference.HIGH,
            experience = Experience.NOVICE,
            equipment = setOf("barbell"),
        ).single()

        // The one row of the table that overrides nothing. High reps only work near failure,
        // novices are advised away from training there, and both sides are defensible — so the
        // app says what it thinks and leaves the preference exactly where the lifter put it.
        assertEquals(null, conflict.wonBy)
        assertTrue(conflict.explanation.isNotEmpty())
    }

    @Test
    fun `a trained lifter with equipment and a matching preference meets no conflicts`() {
        assertTrue(
            conflictsFor(
                goal = RoutineGoal.HYPERTROPHY,
                preference = RepPreference.MODERATE,
                experience = Experience.TRAINED,
                equipment = setOf("barbell"),
            ).isEmpty(),
        )
    }

    @Test
    fun `exclusions leaving a muscle untrainable ask which is least bad`() {
        val banned = bank.map {
            if ("chest" in it.primaryMuscles) it.copy(isDisliked = true) else it
        }

        val choice = leastBadFor("chest", banned, equipment = setOf("barbell"))

        // Never silently including a banned exercise, and never silently skipping the muscle:
        // both decide something only the lifter can decide.
        assertNotNull(choice)
        assertTrue(choice!!.candidates.isNotEmpty())
        assertTrue(choice.candidates.all { it.isDisliked })
    }

    @Test
    fun `the question is asked about what the routine meant to train, not what it got`() {
        // The case the story exists for, and the one that went unasked. Exclusions empty a muscle
        // *before* generation, so the generator skips it and it never reaches the days — asking
        // over the days therefore asks only about muscles that are fine, and the one lifter who
        // needed the question never sees it. That is the "silently skipping a muscle group" the
        // spec forbids, arrived at by reading the wrong list.
        val shape = SplitShape(
            id = "chest-only",
            description = "Chest",
            rotation = 1,
            days = listOf(SplitDay("Chest", listOf("chest"))),
        )
        val allBanned = bank.map {
            if ("chest" in it.primaryMuscles) it.copy(isDisliked = true) else it
        }
        val emptied = routineOf(day("Chest")).copy(
            shape = shape,
            equipment = setOf("barbell", "dumbbell"),
        )

        val asked = bindingOn(emptied, bank = allBanned)
            .filterIsInstance<Binding.OnlyDislikedLeft>()

        assertEquals(listOf("chest"), asked.map { it.muscle })
        assertTrue(asked.single().candidates.all { it.isDisliked })
    }

    @Test
    fun `a routine that trains everything it meant to asks nothing`() {
        val shape = SplitShape(
            id = "chest-only",
            description = "Chest",
            rotation = 1,
            days = listOf(SplitDay("Chest", listOf("chest"))),
        )
        val fine = routineOf(day("Chest", exercise("Bench", "chest") to 4)).copy(
            shape = shape,
            equipment = setOf("barbell"),
        )

        assertTrue(
            bindingOn(fine, bank = bank).filterIsInstance<Binding.OnlyDislikedLeft>().isEmpty(),
        )
    }

    @Test
    fun `a muscle with something available asks nothing`() {
        assertEquals(null, leastBadFor("chest", bank, equipment = setOf("barbell")))
    }

    @Test
    fun `a muscle nothing in the gym trains is not a question about dislikes`() {
        // Nothing banned here — the gym simply has none. Asking "which of these banned exercises
        // is least bad" when there are none would be a question with no answers.
        assertEquals(null, leastBadFor("neck", bank, equipment = setOf("barbell")))
    }

    /** How long this routine's one day runs, which is what the ceiling is checked against. */
    private fun lengthOf(
        routine: Routine,
        cardio: CardioPlacement = routine.cardio,
    ) = dayLength(routine.days.single(), cardio, RepRange.DEFAULT)

    private companion object {
        fun exercise(
            id: String,
            primary: String,
            equipment: String? = "barbell",
            secondary: List<String> = emptyList(),
        ) = Exercise(
            id = id,
            bankName = id,
            primaryMuscles = listOf(primary),
            secondaryMuscles = secondary,
            equipment = equipment,
            mechanic = "compound",
            force = "push",
            category = "strength",
            level = "intermediate",
            loggingType = LoggingType.WEIGHT_REPS,
            unilateral = false,
        )

        val bank = listOf(
            exercise("Bench", "chest"),
            exercise("Fly", "chest", equipment = "dumbbell"),
            exercise("Row", "middle back"),
        )

        fun day(name: String, vararg entries: Pair<Exercise, Int>) = RoutineDay(
            id = 1,
            orderIndex = 1,
            name = name,
            exercises = entries.map { (exercise, sets) -> PlannedExercise(exercise, sets) },
        )

        /** Two sets a week, which is under any band the app aims at. */
        val barelyAnything = routineOf(day("Short", exercise("Bench", "chest") to 2))

        /**
         * Every main muscle, fourteen sets each, one muscle a day and as many days a week as
         * there are muscles. Contrived, but it lands every one of them in the middle of the band
         * and every day inside the ceiling, which is what a routine with nothing binding has to do.
         */
        val everyMainMuscle = routineOf(
            *MAIN_MUSCLES.map { day(it, exercise(it, it) to 14) }.toTypedArray(),
        ).copy(cadence = MAIN_MUSCLES.size)

        /** Fourteen sets of one muscle: inside the band, and inside the hour and a half. */
        val fittingDay = routineOf(day("Fits", exercise("Bench", "chest") to 14))

        /** The same amount of work on machines, which the engine rests far less between. */
        val isolationDay = routineOf(
            day(
                "Isolation",
                exercise("Fly", "chest", equipment = "machine").copy(mechanic = "isolation") to 14,
                exercise("Pushdown", "triceps", equipment = "machine")
                    .copy(mechanic = "isolation") to 14,
                exercise("Curl", "biceps", equipment = "machine")
                    .copy(mechanic = "isolation") to 14,
            ),
        )

        /** Three muscles at fourteen sets each, which is in band and nearly two hours long. */
        val longDay = routineOf(
            day(
                "Long",
                exercise("Bench", "chest") to 14,
                exercise("Row", "middle back") to 14,
                exercise("Squat", "quadriceps") to 14,
            ),
        )

        fun routineOf(vararg days: RoutineDay) = Routine(
            id = 1,
            name = "Routine",
            goal = RoutineGoal.HYPERTROPHY,
            cadence = 1,
            equipment = emptySet(),
            repPreference = RepPreference.MODERATE,
            days = days.toList(),
        )
    }
}
