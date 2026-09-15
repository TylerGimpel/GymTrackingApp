package com.tylergimpel.gymtracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A set is identified by its position within an exercise in a session, not by the row that stores
 * it (ADR 0003). These tests pin the consequence that costs the most to get wrong: deleting a set
 * leaves a gap, and the gap is never reused.
 */
class SessionExerciseTest {

    @Test
    fun `the first set logged against an exercise takes index one`() {
        val exercise = sessionExercise()

        assertEquals(1, exercise.nextSetIndex)
    }

    @Test
    fun `the next set continues from the highest index already logged`() {
        val exercise = sessionExercise(1, 2, 3)

        assertEquals(4, exercise.nextSetIndex)
    }

    @Test
    fun `deleting a set leaves every other set exactly as it was`() {
        val exercise = sessionExercise(1, 2, 3, 4, 5)

        val remaining = exercise.withoutSet(2).sets

        assertEquals(listOf(1, 3, 4, 5), remaining.map(PerformedSet::setIndex))
        // Weights are seeded from the set index, so renumbering would show up as a set carrying
        // someone else's weight rather than merely as a different index.
        assertEquals(listOf(61.0, 63.0, 64.0, 65.0), remaining.map { it.leadingEffort.performance.weightOrNull!!.value })
    }

    @Test
    fun `the gap a deleted set leaves is never refilled`() {
        val exercise = sessionExercise(1, 2, 3, 4, 5)

        assertEquals(6, exercise.withoutSet(2).nextSetIndex)
    }

    @Test
    fun `deleting the highest set frees its index again`() {
        val exercise = sessionExercise(1, 2, 3)

        // Nothing is left above it, so there is no gap to preserve — this is the boundary of the
        // rule above, and what separates counting from the top from a counter that only ever rises.
        assertEquals(3, exercise.withoutSet(3).nextSetIndex)
    }

    private fun sessionExercise(vararg setIndices: Int) = SessionExercise(
        id = 1,
        exercise = Exercise(
            id = "Barbell_Bench_Press",
            bankName = "Barbell Bench Press",
            primaryMuscles = listOf("chest"),
            secondaryMuscles = listOf("shoulders", "triceps"),
            equipment = "barbell",
            mechanic = "compound",
            force = "push",
            category = "strength",
            level = "beginner",
            loggingType = LoggingType.WEIGHT_REPS,
            unilateral = false,
        ),
        orderIndex = 1,
        sets = setIndices.map { index ->
            PerformedSet(
                setIndex = index,
                type = SetType.WORKING,
                efforts = listOf(
                    Effort(
                        id = index.toLong(),
                        side = null,
                        performance = Performance.WeightAndReps(Kilograms(60.0 + index), reps = 5),
                        note = null,
                    ),
                ),
            )
        },
    )
}
