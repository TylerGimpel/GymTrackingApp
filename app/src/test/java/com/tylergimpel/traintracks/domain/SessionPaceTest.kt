package com.tylergimpel.traintracks.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * How far through an exercise a lifter is, and how far through the work that counts.
 *
 * Two numbers because a ramp of three warm-ups and one working set is not four sets of training,
 * and the whole point of the second number is that it disagrees with the first.
 */
class SessionPaceTest {

    @Test
    fun `a warm-up about to be done is a set but not yet work`() {
        val pace = SessionPace.of(listOf(set(SetType.WARMUP)), pending = SetType.WARMUP)

        // Second set of the exercise, and none of it has been training yet. A screen counting
        // this as work would make the exact claim the two numbers exist to separate.
        assertEquals(2, pace.setsDone)
        assertEquals(0, pace.workingDone)
    }

    @Test
    fun `the working set a ramp led up to is the first of the work`() {
        val ramp = listOf(set(SetType.WARMUP), set(SetType.WARMUP))

        val pace = SessionPace.of(ramp, pending = SetType.WORKING)

        assertEquals(3, pace.setsDone)
        assertEquals(1, pace.workingDone)
    }

    @Test
    fun `a set nothing prescribed counts as the training it was`() {
        val pace = SessionPace.of(listOf(set(SetType.WORKING)), pending = null)

        // The app had nothing to say and the lifter trained anyway. Counting it as work is the
        // honest reading: they did it.
        assertEquals(2, pace.workingDone)
    }

    @Test
    fun `a session that knows what it asked for says how much is left`() {
        val ramp = listOf(set(SetType.WARMUP), set(SetType.WARMUP))

        val pace = SessionPace.of(ramp, pending = SetType.WORKING, planned = PlannedSets(5, 3))

        // Two rungs down and the first working set in front of them: third of five overall, first
        // of three that count. Both halves of "how much is left", which is what the header is for.
        assertEquals(3, pace.setsDone)
        assertEquals(PlannedSets(5, 3), pace.planned)
        assertEquals(1, pace.workingDone)
    }

    @Test
    fun `a session nothing planned says where the lifter is and claims no total`() {
        val pace = SessionPace.of(listOf(set(SetType.WORKING)), pending = SetType.WORKING)

        // Training with no routine is an ordinary session, not an incomplete one. A denominator
        // here would be the screen stating a plan nobody made.
        assertEquals(2, pace.setsDone)
        assertNull(pace.planned)
    }

    @Test
    fun `a lifter past what was asked of them is not corrected`() {
        val done = List(4) { set(SetType.WORKING) }

        val pace = SessionPace.of(done, pending = SetType.WORKING, planned = PlannedSets(3, 3))

        // Five of three. Absurd to read and exactly right to show: they did the sets. A header
        // that clamped this would be the app telling somebody they had stopped training.
        assertEquals(5, pace.setsDone)
        assertEquals(PlannedSets(3, 3), pace.planned)
        assertEquals(5, pace.workingDone)
    }

    @Test
    fun `what an exercise asks for follows what the lifter is training for`() {
        // Invented numbers, and the only property worth asserting is the one with a reason behind
        // it: strength is more sets of fewer reps than endurance is. #74 replaces the derivation
        // with one that divides a real volume budget.
        assert(setsFor(RoutineGoal.STRENGTH) > setsFor(RoutineGoal.ENDURANCE))
        RoutineGoal.entries.forEach { assert(setsFor(it) in 1..10) }
    }

    private companion object {
        fun set(type: SetType) = PerformedSet(
            setIndex = 1,
            type = type,
            efforts = listOf(
                Effort(
                    id = 1,
                    side = null,
                    performance = Performance.WeightAndReps(Kilograms(60.0), 8),
                    note = null,
                ),
            ),
        )
    }
}
