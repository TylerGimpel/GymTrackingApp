package com.tylergimpel.gymtracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate

/**
 * What the app believes a lifter can do, from what that lifter has actually done.
 *
 * Every number here comes from one exercise's own sets and nothing else (ADR 0004). The estimate is
 * Epley, smoothed: an unusually good session moves it part of the way rather than all of it,
 * because a recommendation built on one lucky set is a recommendation that walks somebody up to a
 * weight they cannot lift.
 */
class AnchorTest {

    @Test
    fun `a first session is taken at its word`() {
        // Nothing to smooth against: 60 for 6 is everything the app knows, so Epley's answer stands.
        val anchor = Anchor.from(trained(sets(working(60.0, 6))))

        assertEquals(72.0, anchor!!.estimated1rm.value, TOLERANCE)
    }

    @Test
    fun `a better session moves the estimate part of the way, not all of it`() {
        val anchor = Anchor.from(
            trained(sets(working(60.0, 6)), sets(working(60.0, 6)), sets(working(80.0, 6))),
        )!!

        // Epley calls that last session 96, and the estimate goes nowhere near it in one step.
        // What closes the rest of the gap is beating the range substantially, which re-anchors from
        // what was actually lifted rather than creeping (#50) — not a larger smoothing factor.
        assertEquals(79.2, anchor.estimated1rm.value, TOLERANCE)
        assertTrue(anchor.estimated1rm.value < 96.0)
    }

    @Test
    fun `a worse session moves it the same way`() {
        val anchor = Anchor.from(
            trained(sets(working(60.0, 6)), sets(working(60.0, 6)), sets(working(50.0, 6))),
        )!!

        // Symmetrical on purpose: one bad night is not a demotion, and the graded ladder is what
        // responds to a miss (#50). Smoothing is about noise, not about which way the noise went.
        assertEquals(68.4, anchor.estimated1rm.value, TOLERANCE)
    }

    @Test
    fun `a set above twelve reps says nothing about a one-rep maximum`() {
        // Epley would call 70 for 15 an estimate of 105 — every rep formula falls apart out here,
        // which is why ADR 0004 draws the line rather than trusting the arithmetic.
        val anchor = Anchor.from(trained(sets(working(60.0, 6), working(70.0, 15))))!!

        assertEquals(72.0, anchor.estimated1rm.value, TOLERANCE)
    }

    @Test
    fun `a session of nothing but warm-ups says nothing at all`() {
        assertNull(Anchor.from(trained(sets(warmup(40.0, 5)))))
    }

    @Test
    fun `finding out what somebody can lift is exactly what the estimate is for`() {
        // A calibration set earns no volume and still informs: answering this question is the whole
        // reason it was performed. The two flags say different things about the same set.
        val anchor = Anchor.from(trained(sets(calibration(60.0, 6))))

        assertEquals(72.0, anchor!!.estimated1rm.value, TOLERANCE)
    }

    @Test
    fun `an exercise nobody has trained has no estimate`() {
        assertNull(Anchor.from(emptyList()))
    }

    @Test
    fun `a hold is not measured in a weight, so it says nothing`() {
        val plank = TrainedSet(SetType.WORKING, Performance.Timed(Duration.ofSeconds(60)))

        assertNull(Anchor.from(trained(sets(plank))))
    }

    @Test
    fun `more reps at the same assistance is a better estimate, not a worse one`() {
        val fewer = Anchor.from(trained(sets(assisted(30.0, 6))))!!
        val more = Anchor.from(trained(sets(assisted(30.0, 10))))!!

        // Both are negative, because help is weight taken off the lifter. Ten reps on the same
        // setting means needing less of it, which is the larger of the two signed loads — the
        // comparison points the right way by arithmetic rather than by anyone remembering to flip
        // it (ADR 0004).
        assertEquals(-24.0, fewer.estimated1rm.value, TOLERANCE)
        assertEquals(-20.0, more.estimated1rm.value, TOLERANCE)
        assertTrue(more.estimated1rm.value > fewer.estimated1rm.value)
    }

    @Test
    fun `the best set of a session is the one it is judged by`() {
        // A back-off set is lighter by design and says less about a maximum than the top set did.
        val anchor = Anchor.from(trained(sets(working(60.0, 6), backoff(45.0, 10))))!!

        assertEquals(72.0, anchor.estimated1rm.value, TOLERANCE)
    }

    @Test
    fun `the load to work at is the estimate read back down the rep range`() {
        val anchor = Anchor(Kilograms(72.0))

        assertEquals(60.0, anchor.workingLoadFor(RepRange(6, 8)).value, TOLERANCE)
    }

    @Test
    fun `reading an assisted estimate back down the range asks for more help, not less`() {
        // The one place a bare division would quietly coach somebody to get weaker: six reps need
        // more help than a single rep does, so the load has to move away from zero rather than
        // toward it.
        val anchor = Anchor(Kilograms(-22.0))

        assertEquals(-27.5, anchor.workingLoadFor(RepRange(6, 8)).value, TOLERANCE)
    }

    private companion object {
        /** Finer than any plate anybody owns, and coarse enough to ignore binary floating point. */
        const val TOLERANCE = 0.0001

        /** Sessions on consecutive days, in the order they were trained. */
        fun trained(vararg sessions: List<TrainedSet>): List<TrainedSession> =
            sessions.mapIndexed { index, sets ->
                TrainedSession(LocalDate.of(2026, 9, index + 1), sets)
            }

        fun sets(vararg sets: TrainedSet): List<TrainedSet> = sets.toList()

        fun working(weight: Double, reps: Int) = loaded(SetType.WORKING, weight, reps)

        fun warmup(weight: Double, reps: Int) = loaded(SetType.WARMUP, weight, reps)

        fun calibration(weight: Double, reps: Int) = loaded(SetType.CALIBRATION, weight, reps)

        fun backoff(weight: Double, reps: Int) = loaded(SetType.BACKOFF, weight, reps)

        fun loaded(type: SetType, weight: Double, reps: Int) =
            TrainedSet(type, Performance.WeightAndReps(Kilograms(weight), reps))

        fun assisted(help: Double, reps: Int) =
            TrainedSet(SetType.WORKING, Performance.Assisted(Kilograms(help), reps))
    }
}
