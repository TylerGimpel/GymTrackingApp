package com.tylergimpel.gymtracker.domain

import org.junit.Assert.assertEquals
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
