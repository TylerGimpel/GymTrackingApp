package com.tylergimpel.gymtracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How a muscle's sets divide across exercises, and what order the result is trained in.
 *
 * The numbers here are invented and the tests say so rather than pinning them: what is asserted is
 * the shape the research does support — more sets than one movement should carry get split, and a
 * priority lift comes first only where the goal is one the order matters to.
 */
class SetBudgetTest {

    @Test
    fun `a small budget is one exercise`() {
        assertEquals(1, exercisesForSets(2.0))
        assertEquals(1, exercisesForSets(MOST_SETS_PER_EXERCISE.toDouble()))
    }

    @Test
    fun `a day is never one exercise carrying the whole budget`() {
        // The line the ticket is named for: nine sets is not nine sets of one movement.
        assertTrue(exercisesForSets(9.0) > 1)

        (MOST_SETS_PER_EXERCISE + 1..12).forEach {
            assertTrue("$it sets came back as one exercise", exercisesForSets(it.toDouble()) > 1)
        }
    }

    @Test
    fun `a budget never divides past four ways`() {
        // Invented, like the rest. Past four the app would be prescribing a movement for its own
        // sake, and there is no dose-response data for exercises-per-muscle to argue otherwise.
        assertEquals(MOST_EXERCISES_PER_MUSCLE, exercisesForSets(40.0))
        (1..40).forEach { assertTrue(exercisesForSets(it.toDouble()) <= MOST_EXERCISES_PER_MUSCLE) }
    }

    @Test
    fun `the real arithmetic lands in the band for every shape the app offers`() {
        // The guard below used the band top over two sessions, which is not what any routine
        // actually asks. This runs the real budget for every shape, goal and frequency, and
        // asserts where it lands — including the one place it lands at one, and why that is
        // right rather than a violation.
        val landings = mutableSetOf<Int>()
        SPLIT_SHAPES.forEach { shape ->
            RoutineGoal.entries.forEach { goal ->
                (2..6).forEach { frequency ->
                    shape.days.flatMap { it.muscles }.distinct().forEach { muscle ->
                        val sets = setsPerSessionFor(muscle, shape, goal, frequency)
                        if (sets > 0) landings += exercisesForSets(sets)
                    }
                }
            }
        }

        assertTrue("nothing reached the band at all", landings.any { it in 2..4 })
        assertTrue("something divided past four ways", landings.all { it <= 4 })

        // A budget under two sets is one exercise, and deliberately so: the spec asks for a count
        // "derived from how the set budget divides rather than chosen", and forcing a second
        // movement onto two sets would be choosing. It happens for strength trained often, where
        // one exercise for three sets is better training than two for one apiece.
        assertTrue(landings.contains(1))
    }

    @Test
    fun `a real weekly target divides into two to four exercises`() {
        // What the research-shaped budget actually produces, which is the band the spec names.
        // Asserted over the goals rather than by pinning a number, because the number is not the
        // claim — the band is.
        RoutineGoal.entries.forEach { goal ->
            val target = weeklyVolumeTargetFor(goal)
            val perSession = target.endInclusive / 2
            assertTrue(
                "$goal at ${target.endInclusive} a week over two sessions left the band",
                exercisesForSets(perSession) in 2..MOST_EXERCISES_PER_MUSCLE,
            )
        }
    }

    @Test
    fun `sets are spread evenly and none of them vanish`() {
        val spread = setsAcross(exercises = 3, sets = 8.0)

        // Eight across three is 3/3/2 rather than 2/2/2 with two sets quietly dropped.
        assertEquals(3, spread.size)
        assertEquals(8, spread.sum())
        assertTrue(spread.max() - spread.min() <= 1)
    }

    @Test
    fun `every exercise gets at least one set`() {
        // A prescription of nought sets is not a prescription; it is an exercise nobody should
        // have been offered.
        assertTrue(setsAcross(exercises = 4, sets = 2.0).all { it >= 1 })
    }

    @Test
    fun `training for strength puts the compound first`() {
        val ordered = listOf(curl, bench, pushdown, squat).orderedFor(RoutineGoal.STRENGTH)

        // A lifter training for strength wants the priority lift while they are freshest. The
        // compounds move up; within each group nothing is re-sorted, because nothing justifies it.
        assertEquals(listOf("Bench", "Squat", "Curl", "Pushdown"), ordered.map { it.id })
    }

    @Test
    fun `no ordering claim is made for anything else`() {
        val asChosen = listOf(curl, bench, pushdown, squat)

        // Ordering has no hypertrophy effect, so applying it universally would be inventing a
        // rule the evidence does not support. The day is trained in the order it was built.
        listOf(RoutineGoal.HYPERTROPHY, RoutineGoal.ENDURANCE).forEach { goal ->
            assertEquals(asChosen.map { it.id }, asChosen.orderedFor(goal).map { it.id })
        }
    }

    private companion object {
        fun exercise(id: String, mechanic: String) = Exercise(
            id = id,
            bankName = id,
            primaryMuscles = listOf("chest"),
            secondaryMuscles = emptyList(),
            equipment = "barbell",
            mechanic = mechanic,
            force = "push",
            category = "strength",
            level = "intermediate",
            loggingType = LoggingType.WEIGHT_REPS,
            unilateral = false,
        )

        val bench = exercise("Bench", "compound")
        val squat = exercise("Squat", "compound")
        val curl = exercise("Curl", "isolation")
        val pushdown = exercise("Pushdown", "isolation")
    }
}
