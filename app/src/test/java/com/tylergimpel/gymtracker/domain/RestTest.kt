package com.tylergimpel.gymtracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

/**
 * How long the app tells a lifter to rest, and why it is a different answer for different sets.
 *
 * The point of prescribing it at all is that nobody should be looking a rest period up between
 * sets, and the point of deriving it per set is that a heavy triple and a set of twelve on the
 * same machine are not the same work.
 */
class RestTest {

    @Test
    fun `a heavy low-rep set gets the long rest`() {
        assertEquals(Duration.ofMinutes(3), restFor(squat(), SetType.WORKING, RepRange(3, 6)))
    }

    @Test
    fun `a multi-joint set at a working range gets two to three minutes`() {
        assertEquals(Duration.ofSeconds(150), restFor(squat(), SetType.WORKING, RepRange(8, 10)))
    }

    @Test
    fun `single-joint work gets half of that`() {
        // A curl takes less putting back together than a squat, and a lifter kept standing around
        // for three minutes between curls is a lifter who stops using the timer.
        assertEquals(Duration.ofSeconds(90), restFor(curl(), SetType.WORKING, RepRange(8, 10)))
    }

    @Test
    fun `endurance work rests under a minute`() {
        // Short rests are the point of training this way rather than a compromise.
        assertTrue(restFor(curl(), SetType.WORKING, RepRange(12, 15)) < Duration.ofMinutes(1))
        assertTrue(restFor(squat(), SetType.WORKING, RepRange(12, 15)) < Duration.ofMinutes(1))
    }

    @Test
    fun `two sets of the same exercise can rest differently`() {
        val heavy = restFor(squat(), SetType.WORKING, RepRange(4, 6))
        val lighter = restFor(squat(), SetType.WORKING, RepRange(12, 15))

        // The whole reason rest is prescribed per set rather than per exercise.
        assertTrue(heavy > lighter)
    }

    @Test
    fun `a warm-up rests briefly, whatever it is warming up for`() {
        // Standing around during a ramp is wasted gym time.
        assertEquals(Duration.ofSeconds(60), restFor(squat(), SetType.WARMUP, RepRange(3, 6)))
        assertEquals(Duration.ofSeconds(60), restFor(squat(), SetType.CALIBRATION, RepRange(3, 6)))
    }

    @Test
    fun `a superset rests once, for as long as the hardest thing in it asked`() {
        val rests = listOf(Duration.ofSeconds(90), Duration.ofSeconds(150))

        // Rest after the group rather than inside it — the point of alternating is not stopping —
        // and the lifter is recovering from the heaviest of what they just did.
        assertEquals(Duration.ofSeconds(150), restAfterSuperset(rests))
    }

    @Test
    fun `a group with nothing in it asks for no rest`() {
        assertNull(restAfterSuperset(emptyList()))
    }

    private companion object {
        fun squat() = exercise(mechanic = "compound")

        fun curl() = exercise(mechanic = "isolation")

        fun exercise(mechanic: String) = Exercise(
            id = "Test",
            bankName = "Test",
            primaryMuscles = listOf("quadriceps"),
            secondaryMuscles = emptyList(),
            equipment = "barbell",
            mechanic = mechanic,
            force = "push",
            category = "strength",
            level = "beginner",
            loggingType = LoggingType.WEIGHT_REPS,
            unilateral = false,
        )
    }
}
