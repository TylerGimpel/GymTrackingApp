package com.tylergimpel.traintracks.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tonight's order of exercises, and where logging moves through it (#142). */
class LineupTest {

    private val bench = exercise("bench")
    private val row = exercise("row")
    private val press = exercise("press")
    private val fly = exercise("fly")

    private val plan = SessionPlan(listOf(PlannedExercise(bench, 3), PlannedExercise(row, 3), PlannedExercise(press, 2)))

    @Test
    fun `the plan in order, counting work and not warm-ups`() {
        val lineup = lineupOf(plan, listOf(performed(bench, SetType.WARMUP, SetType.WORKING, SetType.WORKING)))

        assertEquals(listOf("bench", "row", "press"), lineup.map { it.exercise.id })
        assertEquals(2, lineup.first().done)
        assertEquals(3, lineup.first().planned)
    }

    @Test
    fun `a swap for an untouched exercise takes its place, and one already started goes after it`() {
        val untouched = lineupOf(plan, emptyList(), listOf(Addition(fly, replaces = "row")))
        assertEquals(listOf("bench", "fly", "press"), untouched.map { it.exercise.id })
        assertEquals(3, untouched[1].planned)

        val started = lineupOf(plan, listOf(performed(row, SetType.WORKING)), listOf(Addition(fly, after = "row")))
        assertEquals(listOf("bench", "row", "fly", "press"), started.map { it.exercise.id })
    }

    @Test
    fun `logging moves to the next exercise with work left, coming round, and ends when all is done`() {
        val lineup = lineupOf(
            plan,
            listOf(performed(bench, SetType.WORKING, SetType.WORKING, SetType.WORKING), performed(press, SetType.WORKING, SetType.WORKING)),
        )
        assertTrue(lineup.isComplete("bench"))
        assertEquals("row", lineup.nextAfter("press")?.id)

        val skipped = lineupOf(plan, listOf(performed(bench, SetType.WORKING, SetType.WORKING, SetType.WORKING)), skipped = setOf("row", "press"))
        assertNull(skipped.nextAfter("bench"))
    }

    @Test
    fun `an exercise nothing asked of is never complete`() {
        val lineup = lineupOf(SessionPlan(), listOf(performed(fly, SetType.WORKING, SetType.WORKING)))
        assertFalse(lineup.isComplete("fly"))
        assertEquals(listOf("fly"), lineup.map { it.exercise.id })
    }

    private fun performed(exercise: Exercise, vararg types: SetType) = SessionExercise(
        id = exercise.id.hashCode().toLong(),
        exercise = exercise,
        orderIndex = 1,
        sets = types.mapIndexed { index, type ->
            PerformedSet(
                setIndex = index + 1,
                type = type,
                efforts = listOf(Effort(index.toLong(), null, Performance.WeightAndReps(Kilograms(60.0), reps = 5), null)),
            )
        },
    )

    private fun exercise(id: String) = Exercise(
        id = id,
        bankName = id,
        primaryMuscles = listOf("chest"),
        secondaryMuscles = emptyList(),
        equipment = null,
        mechanic = null,
        force = null,
        category = "strength",
        level = "beginner",
        loggingType = LoggingType.WEIGHT_REPS,
        unilateral = false,
    )
}
