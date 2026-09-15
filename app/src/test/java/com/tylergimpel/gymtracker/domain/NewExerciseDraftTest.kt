package com.tylergimpel.gymtracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Adding the machine a gym has that the app has never heard of.
 *
 * The fields are the same fields a shipped row carries, because a lifter's own exercise is
 * first-class: it has to be findable, filterable and countable exactly as a shipped one is.
 */
class NewExerciseDraftTest {

    @Test
    fun `a name and a muscle are what make it an exercise`() {
        assertFalse(NewExerciseDraft().isComplete)
        assertFalse(NewExerciseDraft(name = "Pendulum Squat").isComplete)
        assertFalse(NewExerciseDraft(primaryMuscles = listOf("quadriceps")).isComplete)
        assertTrue(
            NewExerciseDraft(name = "Pendulum Squat", primaryMuscles = listOf("quadriceps"))
                .isComplete,
        )
    }

    @Test
    fun `a name of nothing but spaces is not a name`() {
        assertFalse(
            NewExerciseDraft(name = "   ", primaryMuscles = listOf("quadriceps")).isComplete,
        )
    }

    @Test
    fun `a muscle can be added and taken away again`() {
        val draft = NewExerciseDraft().togglePrimary("chest")

        assertEquals(listOf("chest"), draft.primaryMuscles)
        assertEquals(emptyList<String>(), draft.togglePrimary("chest").primaryMuscles)
    }

    @Test
    fun `a muscle cannot be both primary and secondary`() {
        // Volume counts a primary in full and a secondary at half. A row naming one twice would be
        // asking to be credited one and a half times for one set of work.
        val draft = NewExerciseDraft()
            .toggleSecondary("chest")
            .togglePrimary("chest")

        assertEquals(listOf("chest"), draft.primaryMuscles)
        assertEquals(emptyList<String>(), draft.secondaryMuscles)
        assertTrue(draft.claims("chest"))
    }

    @Test
    fun `promoting a muscle the other way round works the same`() {
        val draft = NewExerciseDraft()
            .togglePrimary("triceps")
            .toggleSecondary("triceps")

        assertEquals(emptyList<String>(), draft.primaryMuscles)
        assertEquals(listOf("triceps"), draft.secondaryMuscles)
    }

    @Test
    fun `a name the bank already uses is taken, however it is capitalised`() {
        // The build step refuses this for shipped rows because the search shorthand resolves to
        // names: a second "Butterfly" makes "pec deck" mean two different machines.
        val bank = listOf(
            Exercise(
                id = "Butterfly",
                bankName = "Butterfly",
                primaryMuscles = listOf("chest"),
                secondaryMuscles = emptyList(),
                equipment = "machine",
                mechanic = "isolation",
                force = "push",
                category = "strength",
                level = "beginner",
                loggingType = LoggingType.WEIGHT_REPS,
                unilateral = false,
            ),
        )

        assertTrue(bank.hasNameLike("Butterfly"))
        assertTrue(bank.hasNameLike("  butterfly "))
        assertFalse(bank.hasNameLike("Pendulum Squat"))
    }

    @Test
    fun `a name the lifter gave counts as taken too`() {
        val renamed = listOf(
            Exercise(
                id = "Butterfly",
                bankName = "Butterfly",
                userName = "Pec Deck",
                primaryMuscles = listOf("chest"),
                secondaryMuscles = emptyList(),
                equipment = "machine",
                mechanic = "isolation",
                force = "push",
                category = "strength",
                level = "beginner",
                loggingType = LoggingType.WEIGHT_REPS,
                unilateral = false,
            ),
        )

        assertTrue(renamed.hasNameLike("pec deck"))
    }

    @Test
    fun `what it is for is asked, and what kind of lift it is has an answer already`() {
        // A lifter adding the machine in the corner has an opinion about what it trains and very
        // little about whether it is strength or powerlifting.
        val draft = NewExerciseDraft()

        assertEquals("strength", draft.category)
        assertEquals("beginner", draft.level)
        assertEquals(LoggingType.WEIGHT_REPS, draft.loggingType)
        assertEquals(null, draft.equipment)
    }
}
