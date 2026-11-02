package com.tylergimpel.traintracks.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What to train instead, when a machine is busy.
 *
 * The bar for a candidate is that switching to it costs the lifter nothing but a walk across the
 * gym: the same muscle, the same kind of movement, equipment they actually have — and, first,
 * the ones they have done before, because those need no calibrating.
 */
class SwapTest {

    @Test
    fun `candidates share the muscle and the kind of movement`() {
        val candidates = swapCandidatesFor(benchPress, bank, GYM)

        // A substitute that works a different muscle is not a substitute, and a curl is not a
        // stand-in for a press however free the rack is.
        assertTrue(candidates.contains(inclinePress))
        assertFalse(candidates.contains(curl))
        assertFalse(candidates.contains(flye))
    }

    @Test
    fun `exercises the lifter has trained before come first`() {
        val candidates = swapCandidatesFor(benchPress, bank, GYM, trainedBefore = setOf(dipMachine.id))

        // Those need no ramp and no calibration: the machine being busy is not a reason to spend
        // a session finding out what somebody can do on its neighbour.
        assertEquals(dipMachine, candidates.first())
    }

    @Test
    fun `an exercise the lifter added themselves is a candidate like any other`() {
        val candidates = swapCandidatesFor(benchPress, bank + ownMachine, GYM)

        // First-class everywhere else in the app, and no less so here.
        assertTrue(candidates.contains(ownMachine))
    }

    @Test
    fun `equipment the gym does not have is not offered`() {
        val candidates = swapCandidatesFor(benchPress, bank, setOf("barbell"))

        assertTrue(candidates.contains(inclinePress))
        assertFalse(candidates.contains(dipMachine))
    }

    @Test
    fun `a gym nobody has described yet offers everything rather than nothing`() {
        val candidates = swapCandidatesFor(benchPress, bank, equipment = emptySet())

        // An empty checklist means nothing has been said about the gym, not that it is empty.
        assertTrue(candidates.contains(dipMachine))
    }

    @Test
    fun `an exercise a bank update dropped is never offered`() {
        val candidates = swapCandidatesFor(benchPress, bank + gone, GYM)

        // It is still in the bank for the history that points at it, and that is all.
        assertFalse(candidates.contains(gone))
    }

    @Test
    fun `nothing suggests the exercise being replaced`() {
        assertFalse(swapCandidatesFor(benchPress, bank, GYM).contains(benchPress))
    }


    @Test
    fun `an exercise the lifter never wants to see again is never suggested`() {
        val banned = inclinePress.copy(isDisliked = true)

        val candidates = swapCandidatesFor(benchPress, listOf(banned, dipMachine), GYM)

        // A single hard ban rather than a ranking penalty: offering it back because the
        // alternatives scored worse would break the only promise the setting makes.
        assertFalse(candidates.contains(banned))
        assertTrue(candidates.contains(dipMachine))
    }

    @Test
    fun `when the bans leave nothing, the app can still ask which is least bad`() {
        val banned = listOf(inclinePress, dipMachine).map { it.copy(isDisliked = true) }

        assertTrue(swapCandidatesFor(benchPress, banned, GYM).isEmpty())
        // Asked for explicitly, by a caller that has found there is nothing else and is about to
        // put the decision to the lifter rather than make it for them.
        assertEquals(2, swapCandidatesFor(benchPress, banned, GYM, includeDisliked = true).size)
    }

    @Test
    fun `the lifts programs use come before the rest, best first`() {
        val press = exercise("Dumbbell_Shoulder_Press", "Dumbbell Shoulder Press", "shoulders", "compound", "dumbbell")
        val military = exercise("Standing_Military_Press", "Overhead Press", "shoulders", "compound", "barbell")
        val arnold = exercise("Arnold_Dumbbell_Press", "Arnold Dumbbell Press", "shoulders", "compound", "dumbbell")
        val machine = exercise("Machine_Shoulder_Military_Press", "Machine Shoulder Press", "shoulders", "compound", "machine")

        val candidates = swapCandidatesFor(press, listOf(arnold, machine, military, press), GYM)

        // Alphabetical would put Arnold first; programs put the overhead press there (ADR 0013).
        assertEquals(listOf(military, machine, arnold), candidates)
    }

    @Test
    fun `a lift that is never generated is offered only once the lifter has trained it`() {
        val press = exercise("Dumbbell_Shoulder_Press", "Dumbbell Shoulder Press", "shoulders", "compound", "dumbbell")
        val throwing = exercise("Throw", "Backward Medicine Ball Throw", "shoulders", "compound", "dumbbell")
            .copy(category = "plyometrics")

        assertFalse(swapCandidatesFor(press, listOf(press, throwing), GYM).contains(throwing))
        assertTrue(swapCandidatesFor(press, listOf(press, throwing), GYM, trainedBefore = setOf("Throw")).contains(throwing))
    }

    @Test
    fun `each candidate says why it is offered`() {
        val press = exercise("Dumbbell_Shoulder_Press", "Dumbbell Shoulder Press", "shoulders", "compound", "dumbbell")
        val military = exercise("Standing_Military_Press", "Overhead Press", "shoulders", "compound", "barbell")
        val arnold = exercise("Arnold_Dumbbell_Press", "Arnold Dumbbell Press", "shoulders", "compound", "dumbbell")

        assertEquals(SwapWhy(SwapReason.DONE_BEFORE, "shoulders"), swapWhyFor(arnold, press, trainedBefore = setOf(arnold.id)))
        assertEquals(SwapWhy(SwapReason.COMMON, "shoulders"), swapWhyFor(military, press, trainedBefore = emptySet()))
        assertEquals(SwapWhy(SwapReason.SAME_MUSCLE, "shoulders"), swapWhyFor(arnold, press, trainedBefore = emptySet()))
    }

    private companion object {
        val GYM = setOf("barbell", "machine", "dumbbell")

        val benchPress = exercise("Bench", "Bench Press", "chest", "compound", "barbell")
        val inclinePress = exercise("Incline", "Incline Press", "chest", "compound", "barbell")
        val dipMachine = exercise("Dip", "Dip Machine", "chest", "compound", "machine")
        val flye = exercise("Flye", "Cable Flye", "chest", "isolation", "cable")
        val curl = exercise("Curl", "Barbell Curl", "biceps", "compound", "barbell")
        val ownMachine =
            exercise("uuid-1", "The Press In The Corner", "chest", "compound", "machine")
        val gone = exercise("Gone", "Dropped Press", "chest", "compound", "barbell")
            .copy(isTombstoned = true)

        val bank = listOf(benchPress, inclinePress, dipMachine, flye, curl)

        fun exercise(
            id: String,
            name: String,
            muscle: String,
            mechanic: String,
            equipment: String,
        ) = Exercise(
            id = id,
            bankName = name,
            primaryMuscles = listOf(muscle),
            secondaryMuscles = emptyList(),
            equipment = equipment,
            mechanic = mechanic,
            force = "push",
            category = "strength",
            level = "beginner",
            loggingType = LoggingType.WEIGHT_REPS,
            unilateral = false,
        )
    }
}
