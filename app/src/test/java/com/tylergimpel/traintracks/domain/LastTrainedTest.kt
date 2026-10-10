package com.tylergimpel.traintracks.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * "Legs: 12 days ago" — information that helps decide what to train next (#127, ADR 0008).
 *
 * Nothing here counts a miss: the only input is what was trained, and when.
 */
class LastTrainedTest {

    @Test
    fun `a muscle was last trained the last time any exercise trained it directly`() {
        val report = lastTrainedByMuscle(
            listOf(
                ExerciseLastTrained(exercise("Bench Press", "chest"), TODAY.minusDays(12)),
                ExerciseLastTrained(exercise("Pec Deck", "chest"), TODAY.minusDays(3)),
                ExerciseLastTrained(exercise("Barbell Squat", "quadriceps"), TODAY.minusDays(8)),
            ),
            TODAY,
        )

        // Longest ago first: that is the one a lifter deciding what to do next is looking for.
        assertEquals(
            listOf(MuscleLastTrained("quadriceps", daysAgo = 8), MuscleLastTrained("chest", daysAgo = 3)),
            report,
        )
    }

    @Test
    fun `a run does not count as training the muscles it happens to use`() {
        val report = lastTrainedByMuscle(
            listOf(
                ExerciseLastTrained(exercise("Barbell Squat", "quadriceps"), TODAY.minusDays(8)),
                ExerciseLastTrained(exercise("Running, Treadmill", "quadriceps", cardio = true), TODAY),
            ),
            TODAY,
        )

        // A cardio-only day since the squat changes nothing about when the legs were last trained.
        assertEquals(listOf(MuscleLastTrained("quadriceps", daysAgo = 8)), report)
    }

    @Test
    fun `a muscle worked only as a secondary one is not reported as trained`() {
        val bench = exercise("Bench Press", "chest").copy(secondaryMuscles = listOf("triceps"))

        val report = lastTrainedByMuscle(listOf(ExerciseLastTrained(bench, TODAY.minusDays(2))), TODAY)

        assertTrue(report.none { it.muscle == "triceps" })
    }

    @Test
    fun `nothing trained is nothing to report`() {
        assertTrue(lastTrainedByMuscle(emptyList(), TODAY).isEmpty())
    }

    private fun exercise(name: String, muscle: String, cardio: Boolean = false) = Exercise(
        id = name.replace(' ', '_'),
        bankName = name,
        primaryMuscles = listOf(muscle),
        secondaryMuscles = emptyList(),
        equipment = null,
        mechanic = null,
        force = null,
        category = if (cardio) "cardio" else "strength",
        level = "beginner",
        loggingType = if (cardio) LoggingType.DISTANCE_TIME else LoggingType.WEIGHT_REPS,
        unilateral = false,
    )

    private companion object {
        val TODAY: LocalDate = LocalDate.parse("2026-09-24")
    }
}
