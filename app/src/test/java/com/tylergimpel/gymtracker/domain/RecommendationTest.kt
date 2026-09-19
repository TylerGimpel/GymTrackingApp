package com.tylergimpel.gymtracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * Given these logged sets, what the next set asks for.
 *
 * Training scenarios rather than arithmetic: each one is a lifter's history and the single weight
 * and rep count the app puts in front of them, which is the thing a one-tap log actually records.
 * The rules behind them are ADR 0004's — double progression inside a **Rep range**, anchored to a
 * smoothed Epley estimate of the lifter's own sets, and rounded down to something loadable.
 */
class RecommendationTest {

    @Test
    fun `a session at sixty for six asks for sixty for six again`() {
        val history = TrainingHistory(trained(sets(working(60.0, 6))))

        val progress = history.progressIn(SIX_TO_EIGHT)!!
        val next = history.nextSetOf(progress)

        // Epley makes that a 72 kg single; read back down to the bottom of the range it is the
        // same 60 they just lifted, which is what double progression asks for — the weight holds
        // while the reps climb.
        assertEquals(72.0, progress.anchor.estimated1rm.value, TOLERANCE)
        assertEquals(60.0, progress.workingLoad.value, TOLERANCE)
        assertEquals(SIX_TO_EIGHT, progress.repRange)
        assertLoad(60.0, next)
        assertEquals(6, next.reps)
    }

    @Test
    fun `taking a set to twelve earns a heavier weight, and the reps start at the bottom again`() {
        val history = TrainingHistory(trained(sets(working(60.0, 12))))

        val next = history.nextSet()

        // The weight moved, so last session's twelve reps say nothing about this one: a lifter
        // landing at the bottom of the range is the whole shape of double progression.
        assertLoad(70.0, next)
        assertEquals(6, next.reps)
    }

    @Test
    fun `the weight is never rounded up past what can be loaded`() {
        val history = TrainingHistory(trained(sets(working(60.0, 7))))

        val next = history.nextSet()

        // The formula says 61.67, which is not a weight anybody can build. Rounding up would ask
        // for arithmetic at the rack at best, and confidently too heavy at worst.
        assertLoad(60.0, next)
        assertEquals(7, next.reps)
    }

    @Test
    fun `a bigger increment rounds further down rather than landing between notches`() {
        val history = TrainingHistory(trained(sets(working(62.5, 6))))

        val next = history.nextSet(rounding = Increment(Kilograms(5.0)))

        // 62.5 exactly, on equipment that cannot build it. The plate inventory, the dumbbell rack
        // and the machine stack each answer this differently, and each arrives with its own ticket.
        assertLoad(60.0, next)
    }

    @Test
    fun `reps beyond the top of the range are asked for at the top`() {
        val history = TrainingHistory(trained(sets(working(60.0, 6)), sets(working(60.0, 10))))

        val next = history.nextSet()

        // Ten at 60 has cleared the range, and the answer to that is one increment on the bar after
        // a second session of it (#50) — not a prescription outside the band the exercise is worked
        // in. Asking for the top is the conservative reading of a session that beat it.
        assertLoad(60.0, next)
        assertEquals(8, next.reps)
    }

    @Test
    fun `an exercise nobody has trained has nothing to prescribe`() {
        // Not zero, and not a number borrowed from a similar lift: nothing. Seeding a first session
        // is calibration's job (#49), and it is a ramp rather than a prediction.
        assertNull(TrainingHistory(emptyList()).progressIn(SIX_TO_EIGHT))
    }

    @Test
    fun `an exercise trained only as a warm-up has nothing to prescribe either`() {
        val history = TrainingHistory(trained(sets(warmup(40.0, 5))))

        assertNull(history.progressIn(SIX_TO_EIGHT))
    }

    @Test
    fun `an assisted machine is prescribed less help than the session that earned it`() {
        val history = TrainingHistory(trained(sets(assisted(30.0, 8))))

        val next = history.nextSet()

        // Eight reps on 30 kg of help; six reps should need less of it. The load is negative
        // because help is weight taken off the lifter, so "less help" is the larger number and
        // every comparison downstream points the right way without being told (ADR 0004).
        assertLoad(-27.5, next)
        assertEquals(6, next.reps)
    }

    @Test
    fun `rounding an assisted prescription leaves more help on the machine, not less`() {
        val history = TrainingHistory(trained(sets(assisted(30.0, 7))))

        val next = history.nextSet()

        // 28.75 kg of help is not a setting the machine has. Rounding toward zero would hand the
        // lifter a harder set than the app worked out, which is the inverted form of rounding a
        // barbell up — and the same arithmetic prevents both.
        assertLoad(-30.0, next)
    }

    @Test
    fun `a pull-up with no belt asks for reps and nothing to hang off one`() {
        val history = TrainingHistory(trained(sets(bodyweight(8))))

        val next = history.nextSet()

        // Nothing added is a real load rather than a missing one, so it prescribes as a load of
        // nothing and the reps carry the progression until a belt appears.
        assertLoad(0.0, next)
        assertEquals(8, next.reps)
    }

    @Test
    fun `the rep count offered is last session's, not the top of the range`() {
        val history = TrainingHistory(trained(sets(working(60.0, 6)), sets(working(60.0, 7))))

        val next = history.nextSet()

        // Seven, because that is what happened last time at this weight. Offering the top would
        // make the one-tap path claim an advance that may not have happened.
        assertLoad(60.0, next)
        assertEquals(7, next.reps)
    }

    @Test
    fun `a warm-up at the working weight is not last session's reps`() {
        val history = TrainingHistory(
            trained(sets(working(60.0, 6)), sets(warmup(60.0, 12), working(60.0, 7))),
        )

        val next = history.nextSet()

        // Twelve easy reps of a ramp are not twelve reps of work. Counting them would both inflate
        // the estimate and report a rep count the lifter never worked for.
        assertLoad(60.0, next)
        assertEquals(7, next.reps)
    }

    private fun TrainingHistory.nextSet(
        range: RepRange = SIX_TO_EIGHT,
        rounding: Increment = Increment.DEFAULT,
    ): Prescription = nextSetOf(progressIn(range, rounding)!!)

    private fun assertLoad(expected: Double, prescription: Prescription) =
        assertEquals(expected, prescription.load!!.value, TOLERANCE)

    private companion object {
        val SIX_TO_EIGHT = RepRange(6, 8)

        /** Finer than any plate anybody owns, and coarse enough to ignore binary floating point. */
        const val TOLERANCE = 0.0001

        /** Sessions on consecutive days, in the order they were trained. */
        fun trained(vararg sessions: List<TrainedSet>): List<TrainedSession> =
            sessions.mapIndexed { index, sets ->
                TrainedSession(LocalDate.of(2026, 9, index + 1), sets)
            }

        fun sets(vararg sets: TrainedSet): List<TrainedSet> = sets.toList()

        fun working(weight: Double, reps: Int) =
            TrainedSet(SetType.WORKING, Performance.WeightAndReps(Kilograms(weight), reps))

        fun warmup(weight: Double, reps: Int) =
            TrainedSet(SetType.WARMUP, Performance.WeightAndReps(Kilograms(weight), reps))

        fun assisted(help: Double, reps: Int) =
            TrainedSet(SetType.WORKING, Performance.Assisted(Kilograms(help), reps))

        fun bodyweight(reps: Int) =
            TrainedSet(SetType.WORKING, Performance.BodyweightReps(addedWeight = null, reps = reps))
    }
}
