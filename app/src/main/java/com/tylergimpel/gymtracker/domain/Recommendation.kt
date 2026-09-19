package com.tylergimpel.gymtracker.domain

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
    data class Prescribing(override val prescription: Prescription) : Recommendation {
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
}

/**
 * What to tell the lifter to do for the next set of one exercise, or nothing where there is
 * nothing honest to say.
 *
 * The boundary between the two modes, and it turns on one thing: whether this exercise's own
 * logged sets support an estimate. [progress] is what the caller worked out from [history] — it is
 * passed in rather than recomputed because the caller has already stored it — and where it exists,
 * history is prescribed from. Where it does not, the ramp runs; and the app leaves calibrating by
 * itself the moment the first session becomes history.
 *
 * Null where even calibration has nothing to offer: a hold, a run, a lift the lifter has said they
 * do not want coached, or a ramp that has run out for today.
 */
fun recommendNextSet(
    history: TrainingHistory,
    progress: ExerciseProgress?,
    calibration: CalibrationRamp?,
    setsToday: Int,
): Recommendation? =
    if (progress != null) {
        Recommendation.Prescribing(history.nextSetOf(progress))
    } else {
        calibration?.nextSet(setsToday)
    }
