package com.tylergimpel.gymtracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * The exercise a lifter's sessions keep asking for and their training keeps not containing.
 *
 * **The app notices; the lifter decides.** Nothing in here acts, and the restraint is the point:
 * people skip squats because squats are hard rather than because they dislike them, and an app
 * that inferred dislike from avoidance would quietly strip the most valuable work out of a
 * programme.
 */
class AvoidedTest {

    @Test
    fun `an exercise missed three of the last five times it was asked for is worth a question`() {
        val sessions = listOf(
            session(1, planned = listOf(squat, bench), performed = listOf(bench)),
            session(2, planned = listOf(squat, bench), performed = listOf(bench)),
            session(3, planned = listOf(squat, bench), performed = listOf(squat, bench)),
            session(4, planned = listOf(squat, bench), performed = listOf(bench)),
        )

        val avoided = avoidedExercises(sessions)

        assertEquals(listOf(squat.id), avoided.map { it.exercise.id })
        assertEquals(3, avoided.single().unperformed)
        assertEquals(4, avoided.single().asked)
    }

    @Test
    fun `an exercise avoided once says nothing`() {
        val sessions = listOf(
            session(1, planned = listOf(squat), performed = emptyList()),
            session(2, planned = listOf(squat), performed = listOf(squat)),
            session(3, planned = listOf(squat), performed = listOf(squat)),
        )

        // One bad week is one bad week, and an app that asked about it would be nagging.
        assertTrue(avoidedExercises(sessions).isEmpty())
    }

    @Test
    fun `an exercise avoided long ago and trained since says nothing`() {
        val sessions = listOf(
            // Three misses, and then five sessions of doing it.
            session(1, planned = listOf(squat), performed = emptyList()),
            session(2, planned = listOf(squat), performed = emptyList()),
            session(3, planned = listOf(squat), performed = emptyList()),
            session(4, planned = listOf(squat), performed = listOf(squat)),
            session(5, planned = listOf(squat), performed = listOf(squat)),
            session(6, planned = listOf(squat), performed = listOf(squat)),
            session(7, planned = listOf(squat), performed = listOf(squat)),
            session(8, planned = listOf(squat), performed = listOf(squat)),
        )

        // The window is what keeps an old pattern from following a lifter around.
        assertTrue(avoidedExercises(sessions).isEmpty())
    }

    @Test
    fun `sessions that never asked for it are not evidence about it`() {
        val sessions = listOf(
            session(1, planned = listOf(squat), performed = emptyList()),
            session(2, planned = listOf(bench), performed = listOf(bench)),
            session(3, planned = listOf(squat), performed = emptyList()),
            session(4, planned = listOf(bench), performed = listOf(bench)),
            session(5, planned = listOf(squat), performed = emptyList()),
        )

        // Three of the three that asked, with two bench sessions in between that say nothing
        // about squats either way.
        val avoided = avoidedExercises(sessions).single()
        assertEquals(squat.id, avoided.exercise.id)
        assertEquals(3, avoided.asked)
    }

    @Test
    fun `the threshold is a number somebody can change`() {
        val sessions = listOf(
            session(1, planned = listOf(squat), performed = emptyList()),
            session(2, planned = listOf(squat), performed = emptyList()),
        )

        // Two misses says nothing by default and everything to a stricter policy. Stated against
        // the threshold rather than around it, because it is a judgement call and will be tuned.
        assertTrue(avoidedExercises(sessions).isEmpty())
        assertEquals(1, avoidedExercises(sessions, threshold = 2).size)
    }

    @Test
    fun `noticing is all it does`() {
        val sessions = listOf(
            session(1, planned = listOf(squat), performed = emptyList()),
            session(2, planned = listOf(squat), performed = emptyList()),
            session(3, planned = listOf(squat), performed = emptyList()),
        )

        val avoided = avoidedExercises(sessions).single()

        // What comes back is an observation with its working shown — how many, out of how many —
        // and no verb anywhere. Removing, swapping and banning are the lifter's to choose, and
        // the app has no way to do any of them from here.
        assertEquals(squat, avoided.exercise)
        assertEquals(3, avoided.unperformed)
    }

    private companion object {
        val squat = exercise("Squat")
        val bench = exercise("Bench")

        fun exercise(id: String) = Exercise(
            id = id,
            bankName = id,
            primaryMuscles = listOf("quadriceps"),
            secondaryMuscles = emptyList(),
            equipment = "barbell",
            mechanic = "compound",
            force = "push",
            category = "strength",
            level = "beginner",
            loggingType = LoggingType.WEIGHT_REPS,
            unilateral = false,
        )

        fun session(day: Int, planned: List<Exercise>, performed: List<Exercise>) = LoggedSession(
            session = Session(
                id = day.toLong(),
                startedAt = Instant.parse("2026-09-01T18:00:00Z").plusSeconds(day * 86_400L),
                finishedAt = Instant.parse("2026-09-01T19:00:00Z").plusSeconds(day * 86_400L),
                date = LocalDate.of(2026, 9, day),
                note = null,
            ),
            exercises = performed.mapIndexed { index, exercise ->
                SessionExercise(
                    id = (day * 10 + index).toLong(),
                    exercise = exercise,
                    orderIndex = index + 1,
                    sets = emptyList(),
                )
            },
            plan = SessionPlan(planned),
            supersets = emptyList(),
        )
    }
}
