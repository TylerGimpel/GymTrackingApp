package com.tylergimpel.traintracks.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which unit a set reads in, and what changing that is allowed to touch.
 *
 * The gym has one lat pulldown labelled in kilograms and another in pounds. Both have to log
 * honestly, which means the unit is remembered per exercise, overridable for the one set trained on
 * the odd machine, and incapable of reaching the weight that was stored.
 */
class DisplayUnitTest {

    @Test
    fun `an exercise reads in its own unit`() {
        val performed = sessionExercise(unit = WeightUnit.POUNDS, sets = listOf(Kilograms(100.0)))

        assertEquals(WeightUnit.POUNDS, performed.displayUnitFor(performed.sets.first()))
    }

    @Test
    fun `a set with no unit of its own reads as the exercise does`() {
        val performed = sessionExercise(unit = WeightUnit.POUNDS, sets = listOf(Kilograms(100.0)))

        assertNull(performed.sets.first().displayUnit)
        assertEquals(WeightUnit.POUNDS, performed.displayUnitFor(performed.sets.first()))
    }

    @Test
    fun `a single set overrides the unit without changing the exercise's setting`() {
        val performed = sessionExercise(
            unit = WeightUnit.KILOGRAMS,
            sets = listOf(Kilograms(100.0), Kilograms(100.0)),
        ).overriding(setIndex = 2, unit = WeightUnit.POUNDS)

        assertEquals(WeightUnit.POUNDS, performed.displayUnitFor(performed.sets[1]))
        // The set next to it, and the exercise both sets belong to, are untouched.
        assertEquals(WeightUnit.KILOGRAMS, performed.displayUnitFor(performed.sets[0]))
        assertEquals(WeightUnit.KILOGRAMS, performed.exercise.displayUnit)
    }

    @Test
    fun `a set records a unit only where it departs from the exercise's`() {
        assertNull(WeightUnit.KILOGRAMS.overriding(WeightUnit.KILOGRAMS))
        assertEquals(WeightUnit.POUNDS, WeightUnit.POUNDS.overriding(WeightUnit.KILOGRAMS))
    }

    @Test
    fun `switching an exercise's unit leaves every weight already logged exactly as it was`() {
        val inKilograms = sessionExercise(
            unit = WeightUnit.KILOGRAMS,
            sets = listOf(Kilograms(100.0), Kilograms(102.5), Kilograms(105.0)),
        )

        val inPounds = inKilograms.copy(
            exercise = inKilograms.exercise.copy(displayUnit = WeightUnit.POUNDS),
        )

        // The readings change; the history does not. This is the whole reason storage is canonical:
        // a lifter who moves to the pound machine has not lifted a different amount last Tuesday.
        assertEquals(
            listOf(100.0, 102.5, 105.0),
            inPounds.sets.map { it.leadingEffort.performance.weightOrNull!!.value },
        )
        assertEquals(
            listOf(220.46, 225.97, 231.49),
            inPounds.sets.map { WeightUnit.POUNDS.fromKilograms(it.leadingEffort.performance.weightOrNull!!) },
        )
    }

    @Test
    fun `a weight read in pounds and logged is comparable with one logged in kilograms`() {
        // The same 100 kg reached from either machine is the same point on the graph, which is what
        // "comparable" has to mean if a lifter alternates between them.
        val fromPoundMachine = WeightUnit.POUNDS.toKilograms(220.46)
        val fromKilogramMachine = WeightUnit.KILOGRAMS.toKilograms(100.0)

        assertEquals(fromKilogramMachine.value, fromPoundMachine.value, 0.01)
    }

    private fun SessionExercise.overriding(setIndex: Int, unit: WeightUnit) = copy(
        sets = sets.map { if (it.setIndex == setIndex) it.copy(displayUnit = unit) else it },
    )

    private fun sessionExercise(unit: WeightUnit, sets: List<Kilograms>) = SessionExercise(
        id = 1,
        exercise = Exercise(
            id = "Lat_Pulldown",
            bankName = "Lat Pulldown",
            primaryMuscles = listOf("lats"),
            secondaryMuscles = listOf("biceps"),
            equipment = "cable",
            mechanic = "compound",
            force = "pull",
            category = "strength",
            level = "beginner",
            loggingType = LoggingType.WEIGHT_REPS,
            unilateral = false,
            displayUnit = unit,
        ),
        orderIndex = 1,
        sets = sets.mapIndexed { position, weight ->
            PerformedSet(
                setIndex = position + 1,
                type = SetType.WORKING,
                efforts = listOf(
                    Effort(
                        id = position + 1L,
                        side = null,
                        performance = Performance.WeightAndReps(weight, reps = 5),
                        note = null,
                    ),
                ),
            )
        },
    )
}
