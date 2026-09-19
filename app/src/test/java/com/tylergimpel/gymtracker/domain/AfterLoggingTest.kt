package com.tylergimpel.gymtracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

/**
 * Where the session goes the moment a set is down.
 *
 * The ordering the prototype pinned, and the reason it is worth pinning: two of the three
 * orderings you might reach for leave a lifter standing still in the middle of a superset.
 */
class AfterLoggingTest {

    @Test
    fun `an ordinary set starts the rest it carried`() {
        val next = afterLogging(bench, alone(bench), Duration.ofSeconds(150))

        assertEquals(AfterLogging.StartResting(Duration.ofSeconds(150)), next)
    }

    @Test
    fun `a superset jumps to the other exercise instead of resting`() {
        val next = afterLogging(bench, grouped(bench, row), Duration.ofSeconds(150))

        // The jump takes precedence, and the rest that would have run does not start: the point
        // of alternating is not stopping.
        assertEquals(AfterLogging.JumpTo(row), next)
    }

    @Test
    fun `the far side of a superset jumps back to the near one`() {
        assertEquals(AfterLogging.JumpTo(bench), afterLogging(row, grouped(bench, row), null))
    }

    @Test
    fun `a group told not to jump rests like anything else`() {
        val positions = grouped(bench, row, autoJump = false)

        // Two machines at opposite ends of a gym: being moved automatically between them is worse
        // than being asked, which is what the flag is for.
        assertEquals(
            AfterLogging.StartResting(Duration.ofSeconds(90)),
            afterLogging(bench, positions, Duration.ofSeconds(90)),
        )
    }

    @Test
    fun `finishing a piece of cardio asks whether that was the session`() {
        val treadmill = exercise("Treadmill", LoggingType.DISTANCE_TIME)

        assertEquals(
            AfterLogging.PromptToFinish,
            afterLogging(treadmill, alone(treadmill), Duration.ofSeconds(90)),
        )
    }

    @Test
    fun `a set with no rest prescribed leaves the lifter where they are`() {
        assertEquals(AfterLogging.Nothing, afterLogging(bench, alone(bench), null))
    }

    private companion object {
        val bench = exercise("Bench")
        val row = exercise("Row")

        fun exercise(id: String, loggingType: LoggingType = LoggingType.WEIGHT_REPS) = Exercise(
            id = id,
            bankName = id,
            primaryMuscles = listOf("chest"),
            secondaryMuscles = emptyList(),
            equipment = "barbell",
            mechanic = "compound",
            force = "push",
            category = "strength",
            level = "beginner",
            loggingType = loggingType,
            unilateral = false,
        )

        fun performed(exercise: Exercise, order: Int) =
            SessionExercise(id = order.toLong(), exercise = exercise, orderIndex = order, sets = emptyList())

        fun alone(exercise: Exercise) = listOf(SessionPosition.Alone(performed(exercise, 1)))

        fun grouped(vararg members: Exercise, autoJump: Boolean = true) = listOf(
            SessionPosition.Grouped(
                superset = Superset(id = 1, orderIndex = 1, autoJump = autoJump, restSeconds = null),
                exercises = members.mapIndexed { index, member -> performed(member, index + 1) },
            ),
        )
    }
}
