package com.tylergimpel.gymtracker.domain

import java.time.Duration

/**
 * What the app has to say about the next set of an exercise, and which of its two modes it is in.
 *
 * Two modes rather than a confidence score. A number saying how sure the app is would be noise
 * nobody can act on; what a lifter can act on is knowing that this particular weight is a guess,
 * and that the rest of the time it is not. **Only one of the two ever appears on screen** — see
 * [Calibrating] and [Prescribing].
 *
 * Carries the **Set type** as well as the numbers, because what the app is asking for is not
 * always training: an empty bar for five is a measurement, and one filed as working would corrupt
 * the very history it is establishing.
 */
sealed interface Recommendation {

    /** The weight and reps to offer, and what is recorded beside what the lifter actually does. */
    val prescription: Prescription

    /** What kind of set this is, which is not the lifter's to get wrong on the app's behalf. */
    val setType: SetType

    /**
     * Real history backs the number, and **the screen says nothing at all about itself**.
     *
     * The prototype tried a badge here twice — first reading `prescribing`, then `from your
     * history` — and both were wrong: the first leaked the data model's vocabulary onto the screen,
     * and the second repeated what the previous set shown under every row already said. In the
     * normal state there is nothing to announce (`docs/design/logging-screen.md`).
     */
    data class Prescribing(
        override val prescription: Prescription,
        /**
         * The band this set is being worked in.
         *
         * Carried because the lifter has to see it: the number offered is one count inside a
         * range, and without the range a set of eight looks like a target rather than a rung of
         * something being climbed. It is also what tells the screen when a prescription is high
         * enough to need saying out loud that the reps only work if the effort is there.
         */
        val repRange: RepRange = RepRange.DEFAULT,
    ) : Recommendation {
        override val setType: SetType get() = SetType.WORKING
    }

    /**
     * The app has no useful **Anchor**, so it is guessing — and says so.
     *
     * Still a real number, because "we don't know" is not something a lifter standing at a rack can
     * do anything with. What makes being wrong safe is that the number is deliberately light and
     * the ramp expects to be corrected by what actually happens.
     *
     * Knowing when the app is guessing is what makes the rest of the time trustworthy, which is
     * why this mode is the one that speaks and the other is silent.
     *
     * The mode says how sure the app is; [setType] says whether the set was training, and the two
     * are not the same question. A rung of a ramp is a measurement, and a first set of pull-ups
     * at the only load there is, is work.
     */
    data class Calibrating(
        override val prescription: Prescription,
        override val setType: SetType = SetType.CALIBRATION,
    ) : Recommendation

    /**
     * A rung of the ramp up to the working weight.
     *
     * Not the work: it earns no **Volume** and asks for a **Plain rep count**, because a warm-up
     * is not progressed and has no top to reach. The screen shows it without a band for the same
     * reason — a range on a warm-up would invite a lifter to chase it.
     */
    data class WarmingUp(override val prescription: Prescription) : Recommendation {
        override val setType: SetType get() = SetType.WARMUP
    }

    /**
     * The same weight as last time, and a line asking for less of it.
     *
     * Three sessions short of a lift is past what a lighter bar fixes — the weight has already
     * come down once, and taking it down again chases the lifter to the floor. So the intensity
     * holds and the volume is what gives.
     *
     * **The app asks; it does not cut.** Nothing here prescribes a number of sets — a session is
     * as long as the lifter makes it, and what will prescribe set counts is the generator (#26),
     * which is what this rung will reduce when it exists. Until then the honest implementation of
     * "cut the volume" is to hold the weight and say so, which is also why this rung is one of the
     * two the screen breaks its silence for: a weight that has not moved looks like nothing
     * happening.
     */
    data class CuttingVolume(
        override val prescription: Prescription,
        val repRange: RepRange = RepRange.DEFAULT,
    ) : Recommendation {
        override val setType: SetType get() = SetType.WORKING
    }

    /**
     * Nothing prescribed, and the app says why.
     *
     * A lift that has fallen short session after session is not one more guess away from being
     * right, and an app that guesses forever is one whose numbers stop being read. Saying so is
     * the point: silence here would look like the engine having nothing to offer a lift it had
     * simply given up on.
     *
     * Carries an empty **Prescription**, so a set logged now records that nothing was asked of it
     * — which is true, and which keeps the ladder from reading the next session as an override.
     */
    data object Stopped : Recommendation {
        override val prescription: Prescription get() = Prescription()
        override val setType: SetType get() = SetType.WORKING
    }
}

/**
 * The same recommendation, carrying how long to rest after the set it asks for.
 *
 * Applied after the fact rather than computed with the weight, because rest depends on what kind
 * of set came out of all this — a ramp rung rests briefly and the work it ramps up to does not —
 * and on things outside the engine's reach, like the lifter having said they need longer on this
 * machine, or the set belonging to a **Superset** that rests after the group instead.
 *
 * A [Recommendation.Stopped] carries nothing, rest included: there is no set to rest after.
 */
fun Recommendation.resting(rest: Duration?): Recommendation = when (this) {
    is Recommendation.WarmingUp -> copy(prescription = prescription.copy(rest = rest))
    is Recommendation.Prescribing -> copy(prescription = prescription.copy(rest = rest))
    is Recommendation.CuttingVolume -> copy(prescription = prescription.copy(rest = rest))
    is Recommendation.Calibrating -> copy(prescription = prescription.copy(rest = rest))
    Recommendation.Stopped -> this
}

/**
 * What to tell the lifter to do for the next set of one exercise, or nothing where there is
 * nothing honest to say.
 *
 * The boundary between the modes, and it turns on one thing: whether this exercise's own logged
 * sets support an estimate. [progress] is what the caller worked out from [history] — it is passed
 * in rather than recomputed because the caller has already stored it — and where it exists,
 * history is prescribed from. Where it does not, the ramp runs; and the app leaves calibrating by
 * itself the moment the first session becomes history.
 *
 * What the ladder has to say is layered on top, because two of its rungs are things the lifter has
 * to be told rather than changes to a number.
 *
 * Null where nothing has anything to offer: a hold, a run, a lift the lifter has said they do not
 * want coached, or a ramp that has run out for today.
 */
fun recommendNextSet(
    history: TrainingHistory,
    progress: ExerciseProgress?,
    calibration: CalibrationRamp?,
    setsToday: Int,
): Recommendation? {
    if (progress == null) return calibration?.nextSet(setsToday)

    return when (history.ladderIn(progress.repRange)) {
        Ladder.STOP -> Recommendation.Stopped
        Ladder.CUT_VOLUME ->
            Recommendation.CuttingVolume(history.nextSetOf(progress), progress.repRange)

        // The other rungs have already moved the weight this prescribes, and say nothing further:
        // in the normal state the screen says nothing about itself.
        Ladder.HOLD, Ladder.ADVANCE, Ladder.BACK_OFF ->
            Recommendation.Prescribing(history.nextSetOf(progress), progress.repRange)
    }
}
