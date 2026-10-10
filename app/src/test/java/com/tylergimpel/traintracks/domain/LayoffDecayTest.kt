package com.tylergimpel.traintracks.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * What happens to an estimate while nobody trains the exercise it is about.
 *
 * **It ages because it stops being informative, not because the lifter has become fragile.** The
 * research does not support the injury framing — pre-break strength comes back within weeks — so
 * the job of decay is to trigger re-calibration rather than to compute a smaller number. That is
 * why every test here states the policy it is testing rather than asserting a constant: the
 * threshold is the part that does the work, and the rate is a judgement call somebody will want
 * to change.
 */
class LayoffDecayTest {

    private val policy = LayoffDecay(graceWeeks = 3, perWeek = 0.10, forgetAfterWeeks = 8)

    @Test
    fun `ordinary life costs nothing`() {
        val history = trainedOn(TRAINED)

        // A fortnight away — a holiday, a cold, a busy two weeks — changes no recommendation.
        val fortnight = history.progressIn(SIX_TO_EIGHT, decay = policy, asOf = TRAINED.plusWeeks(2))
        val same = history.progressIn(SIX_TO_EIGHT, decay = policy, asOf = TRAINED)

        assertEquals(same!!.workingLoad.value, fortnight!!.workingLoad.value, TOLERANCE)
    }

    @Test
    fun `a longer absence comes down gradually rather than in one step`() {
        val history = trainedOn(TRAINED)

        val untouched = history.estimateAfter(weeks = 3, policy)
        val four = history.estimateAfter(weeks = 4, policy)
        val five = history.estimateAfter(weeks = 5, policy)

        // Each week past the grace period takes another slice of what the estimate started at,
        // and the policy is what says how big a slice. Stated against the policy rather than
        // against a number, so that changing the rate changes one object and no tests.
        assertEquals(policy.perWeek * untouched, untouched - four, TOLERANCE)
        assertEquals(2 * policy.perWeek * untouched, untouched - five, TOLERANCE)
        assertTrue(five < four)
    }

    @Test
    fun `long enough away and the app admits it does not know`() {
        val history = trainedOn(TRAINED)

        // Past the threshold there is no estimate at all rather than a small one, which is what
        // puts the exercise back into calibrating: the honest move is to find out again.
        assertNotNull(history.progressIn(SIX_TO_EIGHT, decay = policy, asOf = TRAINED.plusWeeks(7)))
        assertNull(history.progressIn(SIX_TO_EIGHT, decay = policy, asOf = TRAINED.plusWeeks(8)))
    }

    @Test
    fun `the threshold is the policy's, not the engine's`() {
        val patient = policy.copy(forgetAfterWeeks = 20)
        val history = trainedOn(TRAINED)

        // Same history, same absence, different policy, different answer. The engine holds no
        // opinion about how long is too long.
        assertNull(history.progressIn(SIX_TO_EIGHT, decay = policy, asOf = TRAINED.plusWeeks(10)))
        assertNotNull(history.progressIn(SIX_TO_EIGHT, decay = patient, asOf = TRAINED.plusWeeks(10)))
    }

    @Test
    fun `not having squatted since February says nothing about a bench press`() {
        val squat = trainedOn(TRAINED)
        val bench = trainedOn(TRAINED.plusWeeks(9))

        // Tracked per exercise, because the estimate is per exercise. One history knows nothing
        // about the other, which is the same rule that stops a bench press deciding a dumbbell
        // press (ADR 0004).
        val today = TRAINED.plusWeeks(10)
        assertNull(squat.progressIn(SIX_TO_EIGHT, decay = policy, asOf = today))
        assertNotNull(bench.progressIn(SIX_TO_EIGHT, decay = policy, asOf = today))
    }

    @Test
    fun `an assisted machine ages toward more help rather than less`() {
        val helped = TrainingHistory(
            listOf(
                TrainedSession(
                    TRAINED,
                    listOf(TrainedSet(SetType.WORKING, Performance.Assisted(Kilograms(30.0), 8))),
                ),
            ),
        )

        val fresh = helped.progressIn(SIX_TO_EIGHT, decay = policy, asOf = TRAINED)!!
        val stale = helped.progressIn(SIX_TO_EIGHT, decay = policy, asOf = TRAINED.plusWeeks(6))!!

        // Weaker on this machine means needing more of its help, which is the smaller signed
        // number — the same direction a barbell's estimate moves, written once (ADR 0004).
        assertTrue(stale.workingLoad.value < fresh.workingLoad.value)
    }

    @Test
    fun `coming back is not handed the old working weight`() {
        // The whole point: the weight a lifter was working at is held while they are training,
        // and a held weight is exactly what should not come back after two months away.
        val history = TrainingHistory(
            listOf(
                TrainedSession(
                    TRAINED,
                    listOf(
                        TrainedSet(
                            type = SetType.WORKING,
                            performance = Performance.WeightAndReps(Kilograms(100.0), 6),
                            prescription = Prescription(load = Kilograms(100.0), reps = 6),
                        ),
                    ),
                ),
            ),
        )

        val back = history.progressIn(SIX_TO_EIGHT, decay = policy, asOf = TRAINED.plusWeeks(6))!!

        assertTrue(back.workingLoad.value < 100.0)
    }

    private fun TrainingHistory.estimateAfter(weeks: Long, decay: LayoffDecay): Double =
        progressIn(SIX_TO_EIGHT, decay = decay, asOf = TRAINED.plusWeeks(weeks))!!
            .anchor.estimated1rm.value

    private companion object {
        val TRAINED: LocalDate = LocalDate.of(2026, 2, 1)
        val SIX_TO_EIGHT = RepRange(6, 8)
        const val TOLERANCE = 0.0001

        fun trainedOn(date: LocalDate) = TrainingHistory(
            listOf(
                TrainedSession(
                    date,
                    listOf(
                        TrainedSet(
                            SetType.WORKING,
                            Performance.WeightAndReps(Kilograms(100.0), 6),
                        ),
                    ),
                ),
            ),
        )
    }
}
