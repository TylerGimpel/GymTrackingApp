package com.tylergimpel.gymtracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * What a session was supposed to contain, set against what it did.
 *
 * Without the plan, an exercise nobody trained is indistinguishable from one nobody prescribed —
 * and "I did four of six" cannot be answered at all. This is where that difference lives.
 */
class SessionPlanTest {

    @Test
    fun `a session that did everything it was for has nothing unperformed`() {
        val plan = planOf(bench, row, curl)

        val unperformed = plan.unperformed(performed(bench, row, curl))

        assertTrue(unperformed.isEmpty())
    }

    @Test
    fun `what was planned and not trained is named, rather than merely absent`() {
        val plan = planOf(bench, row, curl)

        val unperformed = plan.unperformed(performed(bench, row))

        assertEquals(listOf(curl), unperformed)
    }

    @Test
    fun `a partial session reports what it did against what it was for`() {
        val plan = planOf(bench, row, curl)

        assertEquals(listOf(bench, row), plan.performed(performed(bench, row)))
        assertEquals(2, plan.performed(performed(bench, row)).size)
        assertEquals(3, plan.exercises.size)
    }

    @Test
    fun `the unperformed keep the order the routine day held them in`() {
        // So a lifter reading them back sees their session in the shape it was meant to take.
        val plan = planOf(bench, row, curl)

        assertEquals(listOf(bench, curl), plan.unperformed(performed(row)))
    }

    @Test
    fun `a session with no plan is an ordinary session rather than an empty one`() {
        // Training with no routine at all is normal, and nothing about it is missing.
        val plan = SessionPlan()

        assertTrue(plan.isEmpty)
        assertTrue(plan.unperformed(performed(bench)).isEmpty())
        assertTrue(plan.performed(performed(bench)).isEmpty())
    }

    @Test
    fun `an exercise trained but never planned is not counted against the plan`() {
        // Swapping in something else is ordinary; the plan reports on itself, not on the session.
        val plan = planOf(bench, row)

        assertEquals(listOf(row), plan.unperformed(performed(bench, curl)))
        assertEquals(listOf(bench), plan.performed(performed(bench, curl)))
    }

    @Test
    fun `how often each exercise was planned and left untrained is counted across sessions`() {
        // The signal swap and dislike are meant to read: "you have not trained curls in three of
        // the last four sessions that asked for them" is a fact about the plan, not about what
        // happened to be logged.
        val sessions = listOf(
            logged(plan = listOf(bench, row, curl), did = listOf(bench, row)),
            logged(plan = listOf(bench, row, curl), did = listOf(bench, row)),
            logged(plan = listOf(bench, curl), did = listOf(bench, curl)),
            logged(plan = listOf(bench, row, curl), did = listOf(bench)),
        )

        assertEquals(mapOf(curl.id to 3, row.id to 1), unperformedExerciseCounts(sessions))
    }

    @Test
    fun `sessions with no plan contribute nothing to the counts`() {
        val sessions = listOf(
            logged(plan = emptyList(), did = listOf(bench)),
            logged(plan = listOf(curl), did = emptyList()),
        )

        assertEquals(mapOf(curl.id to 1), unperformedExerciseCounts(sessions))
    }

    private fun performed(vararg exercises: Exercise) = exercises.mapIndexed { index, exercise ->
        SessionExercise(
            id = index + 1L,
            exercise = exercise,
            orderIndex = index + 1,
            sets = emptyList(),
        )
    }

    /** A plan of exercises nothing prescribed a set count for, which is what these are about. */
    private fun planOf(vararg exercises: Exercise) =
        SessionPlan(exercises.map { PlannedExercise(it) })

    private fun logged(plan: List<Exercise>, did: List<Exercise>) = LoggedSession(
        session = Session.start(Instant.parse("2026-09-14T18:00:00Z"), ZONE),
        exercises = performed(*did.toTypedArray()),
        plan = planOf(*plan.toTypedArray()),
    )

    private companion object {
        val ZONE: ZoneId = ZoneId.of("Europe/London")

        val bench = exercise("Barbell_Bench_Press", "Barbell Bench Press", "chest")
        val row = exercise("Seated_Cable_Row", "Seated Cable Row", "middle back")
        val curl = exercise("Barbell_Curl", "Barbell Curl", "biceps")

        fun exercise(id: String, name: String, muscle: String) = Exercise(
            id = id,
            bankName = name,
            primaryMuscles = listOf(muscle),
            secondaryMuscles = emptyList(),
            equipment = "barbell",
            mechanic = "compound",
            force = "push",
            category = "strength",
            level = "beginner",
            loggingType = LoggingType.WEIGHT_REPS,
            unilateral = false,
        )
    }
}
