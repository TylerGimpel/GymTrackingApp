package com.tylergimpel.gymtracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the one picker offers, whether it was opened to swap or to build.
 *
 * Both jobs ask the same question — "what could I do instead of, or as well as, this" — so both
 * get the same answers. What differs is only what the caller ranks in front of them.
 */
class ExercisePickerTest {

    @Test
    fun `the lifter's own gym answers first`() {
        val offered = bank.offeredFor(equipment = setOf("barbell"))

        // Nothing they cannot do. A picker that opened on machines their gym has never had would
        // make them scroll past the useless half of the bank every time. An exercise needing no
        // equipment at all is not "something they cannot do", so it stays.
        assertTrue(offered.none { it.equipment == "machine" })
        assertTrue(offered.any { it.equipment == "barbell" })
    }

    @Test
    fun `everything is reachable for the gym they are about to join`() {
        val offered = bank.offeredFor(equipment = setOf("barbell"), ownEquipmentOnly = false)

        // Somebody building a routine for a gym they have not joined yet needs past what they
        // have today, which is what the toggle is for.
        assertTrue(offered.any { it.equipment == "machine" })
    }

    @Test
    fun `a gym nobody has described offers everything rather than nothing`() {
        // An empty checklist is "they have not said", not "they own no equipment". Filtering to
        // it would leave a new lifter looking at a blank list — so everything the picker would
        // ever offer is offered, which is the bank less what is dropped and what is banned.
        val offerable = bank.filterNot { it.isTombstoned || it.isDisliked }
        assertEquals(offerable.size, bank.offeredFor(equipment = emptySet()).size)
    }

    @Test
    fun `an exercise with no equipment listed is never filtered out`() {
        val offered = bank.offeredFor(equipment = setOf("barbell"))

        // A bodyweight movement needs nothing, so no checklist can exclude it.
        assertTrue(offered.any { it.id == "PullUp" })
    }

    @Test
    fun `typing finds an exercise by name`() {
        val offered = bank.offeredFor(query = "press bench", equipment = emptySet())

        assertEquals(listOf("Bench"), offered.map { it.id })
    }

    @Test
    fun `filtering by muscle puts what targets it first`() {
        val offered = bank.offeredFor(muscle = "triceps", equipment = emptySet())

        // Secondary work is still work and is still offered, but "something for triceps" is
        // answered by triceps exercises rather than by every press that involves them.
        assertEquals(listOf("Pushdown", "Bench"), offered.map { it.id })
    }

    @Test
    fun `an exercise they said never to suggest is not suggested`() {
        val offered = bank.offeredFor(equipment = emptySet())

        // A hard ban, respected wherever the app offers anything.
        assertFalse(offered.any { it.id == "Burpee" })
    }

    @Test
    fun `an exercise they said never to suggest is still findable by name`() {
        val offered = bank.offeredFor(query = "burpee", equipment = emptySet())

        // Typing its name is not the app suggesting it — it is the lifter asking for that one
        // specifically, and refusing to find it would be the app arguing with them. What happens
        // next is a warning, not a refusal.
        assertEquals(listOf("Burpee"), offered.map { it.id })
    }

    @Test
    fun `a dropped exercise is never offered, typed or not`() {
        // A tombstone exists for the history pointing at it, and is not something to offer anybody.
        assertFalse(bank.offeredFor(equipment = emptySet()).any { it.id == "Dropped" })
        assertFalse(bank.offeredFor(query = "dropped", equipment = emptySet()).any { it.id == "Dropped" })
    }

    @Test
    fun `an exercise the lifter added is offered on the same terms as a shipped one`() {
        val offered = bank.offeredFor(query = "my", equipment = emptySet())

        // First-class means first-class: it searches, filters and ranks exactly as the bank's own
        // rows do, because a gym's machines should be programmable like anything else.
        assertEquals(listOf("Mine"), offered.map { it.id })
    }

    private companion object {

        fun exercise(
            id: String,
            name: String,
            primary: List<String>,
            secondary: List<String> = emptyList(),
            equipment: String? = "barbell",
            disliked: Boolean = false,
            tombstoned: Boolean = false,
            source: ExerciseSource = ExerciseSource.UPSTREAM,
        ) = Exercise(
            id = id,
            bankName = name,
            primaryMuscles = primary,
            secondaryMuscles = secondary,
            equipment = equipment,
            mechanic = "compound",
            force = "push",
            category = "strength",
            level = "intermediate",
            loggingType = LoggingType.WEIGHT_REPS,
            unilateral = false,
            isDisliked = disliked,
            source = source,
            isTombstoned = tombstoned,
        )

        val bank = listOf(
            exercise("Bench", "Barbell Bench Press", listOf("chest"), listOf("triceps")),
            exercise("Pushdown", "Cable Pushdown", listOf("triceps"), equipment = "machine"),
            exercise("PullUp", "Pull Up", listOf("lats"), equipment = null),
            exercise("Burpee", "Burpee", listOf("quadriceps"), disliked = true),
            exercise("Dropped", "Dropped Movement", listOf("chest"), tombstoned = true),
            exercise("Mine", "My Gym's Chest Press", listOf("chest"), source = ExerciseSource.USER),
        )
    }
}
