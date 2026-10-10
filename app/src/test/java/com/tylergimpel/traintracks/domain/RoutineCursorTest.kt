package com.tylergimpel.traintracks.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cursor is the first routine day in the current cycle not yet completed or skipped, derived
 * rather than stored (ADR 0001).
 *
 * Nothing here takes a date, a clock or a duration, and that is the point: a fortnight off costs
 * nothing because there is no elapsed time for the rule to notice.
 */
class RoutineCursorTest {

    private val push = RoutineDay(id = 1, orderIndex = 1, name = "Push")
    private val pull = RoutineDay(id = 2, orderIndex = 2, name = "Pull")
    private val legs = RoutineDay(id = 3, orderIndex = 3, name = "Legs")
    private val routine = routineOf(push, pull, legs)

    @Test
    fun `a cycle with nothing done yet points at the first day`() {
        assertEquals(push, routine.cursor(cycleWith()))
    }

    @Test
    fun `a routine that has never started a cycle points at the first day`() {
        assertEquals(push, routine.cursor(cycle = null))
    }

    @Test
    fun `a routine with no days has no cursor`() {
        assertNull(routineOf().cursor(cycleWith()))
    }

    @Test
    fun `completing the day it pointed at advances it`() {
        val cycle = cycleWith(push to DayOutcome.COMPLETED)

        assertEquals(pull, routine.cursor(cycle))
    }

    @Test
    fun `training out of order holds the cursor where it was`() {
        // The whole reason the cursor is derived rather than stored (ADR 0001). Pull was trained,
        // but Push was the day that came next and still is.
        val cycle = cycleWith(pull to DayOutcome.COMPLETED)

        assertEquals(push, routine.cursor(cycle))
    }

    @Test
    fun `a day skipped explicitly is passed over`() {
        val cycle = cycleWith(push to DayOutcome.SKIPPED)

        assertEquals(pull, routine.cursor(cycle))
    }

    @Test
    fun `completing the pointed-at day later advances to the first day still undone`() {
        // Pull was trained out of order earlier, so finishing Push skips straight past it to Legs
        // rather than pointing at a day already done.
        val cycle = cycleWith(pull to DayOutcome.COMPLETED, push to DayOutcome.COMPLETED)

        assertEquals(legs, routine.cursor(cycle))
    }

    @Test
    fun `a cycle with every day dealt with rolls over to the first day again`() {
        val cycle = cycleWith(
            push to DayOutcome.COMPLETED,
            pull to DayOutcome.SKIPPED,
            legs to DayOutcome.COMPLETED,
        )

        assertTrue(cycle.isComplete(routine))
        assertEquals(push, routine.cursor(cycle))
    }

    @Test
    fun `a cycle is not complete while any day is still undone`() {
        val cycle = cycleWith(push to DayOutcome.COMPLETED, legs to DayOutcome.COMPLETED)

        assertFalse(cycle.isComplete(routine))
    }

    @Test
    fun `the rotation is read in order regardless of how the days arrive`() {
        val shuffled = routineOf(legs, push, pull)

        assertEquals(push, shuffled.cursor(cycleWith()))
        assertEquals(listOf("Push", "Pull", "Legs"), shuffled.rotation.map(RoutineDay::name))
    }

    private fun routineOf(vararg days: RoutineDay) = Routine(
        id = 1,
        name = "Push / Pull / Legs",
        goal = RoutineGoal.HYPERTROPHY,
        cadence = 4,
        equipment = setOf("barbell", "dumbbell"),
        repPreference = RepPreference.MODERATE,
        days = days.toList(),
    )

    private fun cycleWith(vararg outcomes: Pair<RoutineDay, DayOutcome>) = Cycle(
        id = 1,
        ordinal = 1,
        outcomes = outcomes.associate { (day, outcome) -> day.id to outcome },
    )
}
