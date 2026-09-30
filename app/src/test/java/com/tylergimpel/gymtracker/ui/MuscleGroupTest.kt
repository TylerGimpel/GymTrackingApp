package com.tylergimpel.gymtracker.ui

import com.tylergimpel.gymtracker.domain.Exercise
import com.tylergimpel.gymtracker.domain.LoggingType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** The ten groups the screens name muscles by (#141). */
class MuscleGroupTest {

    @Test
    fun `every muscle the bank names reads as a group`() {
        val bank = listOf(
            "abdominals", "abductors", "adductors", "biceps", "calves", "chest", "forearms",
            "glutes", "hamstrings", "lats", "lower back", "middle back", "neck", "quadriceps",
            "shoulders", "traps", "triceps",
        )
        bank.forEach { assertNotNull(it, MuscleGroup.of(it)) }
    }

    @Test
    fun `a day's groups are its primary muscles, the most trained first, and a back is one back`() {
        val day = listOf(
            lift("lats"), lift("middle back"), lift("chest", secondary = "triceps"), lift("biceps"),
        )

        assertEquals(listOf(MuscleGroup.BACK, MuscleGroup.CHEST, MuscleGroup.BICEPS), muscleGroupsOf(day))
    }

    private var next = 0

    private fun lift(primary: String, secondary: String? = null) = Exercise(
        id = "lift-${next++}",
        bankName = primary,
        primaryMuscles = listOf(primary),
        secondaryMuscles = listOfNotNull(secondary),
        equipment = null,
        mechanic = null,
        force = null,
        category = "strength",
        level = "beginner",
        loggingType = LoggingType.WEIGHT_REPS,
        unilateral = false,
    )
}
