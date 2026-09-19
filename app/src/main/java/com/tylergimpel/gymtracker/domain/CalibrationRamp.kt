package com.tylergimpel.gymtracker.domain

import kotlin.math.abs

/**
 * The short climb a first session makes to find a working weight, rather than predicting one.
 *
 * **A calibration protocol, not a prediction.** The best anthropometric estimate of an untrained
 * bench press carries about 20% error, wide enough to be double or half the truth; a first session
 * that measures instead is three to four times more precise and costs one session, most of which
 * the lifter would have spent warming up anyway (`docs/research/cold-start-weights.md`). Every
 * novice programme worth the name does the same thing: start light, step up, stop when it gets
 * hard. So the only job left for a model is picking where the ramp *starts*, where being wrong
 * costs a set rather than an injury.
 *
 * **Every seed is biased low on purpose.** The loss function is one-sided: too light still trains
 * a novice, since loads of 45–50% of a one-rep maximum and less raise strength in people who have
 * not trained; too heavy breaks form in exactly the population — inexperienced, alone, following
 * an app — that the injury literature identifies as most at risk.
 */
data class CalibrationRamp(
    /**
     * The loads to offer, in order: already rounded to something loadable, and **each one harder
     * than the last**.
     *
     * Built rather than computed on demand, because "harder than the last" is a fact about the
     * sequence rather than about any one rung, and rounding is what puts it at risk.
     */
    val rungs: List<Kilograms>,
    /**
     * What a set at one of these rungs is.
     *
     * A **Calibration set** where the ramp is finding a weight, because an empty bar for five is a
     * measurement rather than training and counting it would corrupt the very history it is
     * establishing. **Working** where there is no weight to find: a first set of pull-ups is the
     * only load there is, so it is real work, and filing it as a measurement would delete a set
     * the lifter actually did from their **Volume**.
     */
    val setType: SetType,
) {

    /** The [index]th rung, or null once the ramp has run out. */
    fun rung(index: Int): Kilograms? = rungs.getOrNull(index)

    /**
     * What to ask of the next set, having already logged [setsToday] of this exercise today.
     *
     * Null once the ramp is spent: the app has said what it has to say, and the lifter carries on
     * by hand. Counting today's sets rather than tracking a position keeps this answerable from the
     * log alone, and means a lifter who logged their own weight has moved the ramp on with it.
     */
    fun nextSet(setsToday: Int): Recommendation.Calibrating? = rung(setsToday)?.let {
        Recommendation.Calibrating(Prescription(load = it, reps = RAMP_REPS), setType)
    }

    companion object {
        /**
         * The reps a ramp set asks for.
         *
         * A **Plain rep count** rather than a **Rep range**: a ramp set is not progressed, so it
         * has no top to reach. Five is what every novice protocol ramps in — short enough to repeat
         * six times, long enough that "that felt hard" means something.
         */
        const val RAMP_REPS = 5

        /**
         * The ramp to run on [exercise], or null where there is nothing to calibrate.
         *
         * Three shapes, decided here where the **Logging type** is known rather than inferred
         * later from the seed: a weight to climb, a machine's help to take away, and a body that
         * cannot be made lighter. A hold and a run are recorded rather than prescribed, so there
         * is no weight to go looking for at all.
         */
        fun of(exercise: Exercise, rounding: Increment = Increment.DEFAULT): CalibrationRamp? =
            when (exercise.loggingType) {
                LoggingType.WEIGHT_REPS ->
                    climbing(Kilograms(seedFor(exercise.equipment)), LOADED_STEPS, rounding)

                LoggingType.ASSISTED ->
                    climbing(Kilograms(-ASSISTED_SEED), ASSISTED_STEPS, rounding)

                // The load is the lifter's own body and no ramp can change it, so every set of the
                // first session asks the same thing and the only question is how many.
                LoggingType.BODYWEIGHT_REPS -> CalibrationRamp(
                    rungs = List(LOADED_STEPS.size) { Kilograms(0.0) },
                    setType = SetType.WORKING,
                )

                LoggingType.TIME, LoggingType.DISTANCE_TIME -> null
            }

        /**
         * A ramp from [seed], stepping by [steps] of it and rounded to what can be loaded.
         *
         * **The rounding is why this ends in `distinct`.** Two steps of a light seed can land on
         * the same weight once the plate is gone — 5 kg stepped by 1 and by 1.25 are both 10 kg on
         * 2.5 kg plates — and a ramp that offers the same set twice wastes one of the six it gets.
         * Dropping the repeat rather than nudging it up by an increment keeps every rung a weight
         * the steps actually asked for: a gym with nothing lighter than twenties has a shorter
         * ramp, which is the truth about that gym rather than a reason to invent rungs above it.
         *
         * The steps rise and rounding is monotone, so dropping repeats leaves rungs that strictly
         * climb — which is what makes every one of them harder than the last.
         */
        private fun climbing(
            seed: Kilograms,
            steps: List<Double>,
            rounding: Increment,
        ) = CalibrationRamp(
            rungs = steps
                .map { rounding.roundDown(Kilograms(seed.value + abs(seed.value) * it)) }
                .distinct(),
            setType = SetType.CALIBRATION,
        )

        /**
         * Where a ramp starts, by what the exercise is loaded with.
         *
         * Invented numbers, and cheap ones: getting a seed wrong costs ramp sets rather than
         * safety, which is the whole reason the ramp exists. Only the barbell is not invented — an
         * Olympic bar weighs 20 kg, and every novice programme starts there.
         *
         * A machine's number is a **Stack number** rather than a weight, so its seed is a notch
         * near the light end of the stack rather than a load: pulley ratios differ between
         * manufacturers and nothing can be inferred across machines.
         *
         * Bodyweight and sex could raise a seed and shorten the ramp, and deliberately do not yet:
         * the profile holds neither until onboarding does (#29), and the research is explicit that
         * the raise is optional because the ramp corrects it either way.
         */
        private fun seedFor(equipment: String?): Double = SEEDS[equipment] ?: UNKNOWN_EQUIPMENT_SEED

        private val SEEDS = mapOf(
            "barbell" to 20.0,
            "e-z curl bar" to 10.0,
            "machine" to 10.0,
            "cable" to 10.0,
            "dumbbell" to 5.0,
            "kettlebells" to 5.0,
        )

        /**
         * Light enough to be trivial on anything the map does not name — bands, a medicine ball,
         * or an exercise the lifter added themselves.
         *
         * Not a silent fallback for a renamed vocabulary: the bank's equipment values are a closed
         * set the build step enforces (`data/README.md`), so a value this map has never heard of
         * fails the build rather than quietly reaching here.
         */
        private const val UNKNOWN_EQUIPMENT_SEED = 5.0

        /**
         * How much help an assisted machine starts by giving.
         *
         * Generous, because generous is the safe direction here: too much help is a set that was
         * easy, and too little is a rep the lifter cannot finish. Roughly half a bodyweight, which
         * is what these machines are labelled in.
         */
        private const val ASSISTED_SEED = 40.0

        /**
         * How far above the seed each rung sits, as a fraction of it.
         *
         * Proportional rather than absolute, so the same ramp works from an empty bar and from a
         * seed three times heavier, and shrinking as it climbs: big steps while the weight is
         * trivial keep session one short, small ones near the top stop it stepping clean past the
         * weight it is looking for.
         *
         * Capped at six rungs. A first session that takes forty minutes to find a weight is a
         * first session somebody abandons, and whatever the ramp reached becomes the exercise's
         * first data point — which beating the range substantially re-anchors from in one session
         * rather than a month (#50).
         */
        private val LOADED_STEPS = listOf(0.0, 0.5, 1.0, 1.25, 1.5, 1.75)

        /**
         * The same ramp on a machine that is helping, where the room runs the other way.
         *
         * A weight can double; help can only shrink to nothing, and nothing is not an assisted set
         * at all — it is a pull-up, which the lifter can log if they have one. So this stops short
         * of zero rather than walking somebody off the end of the machine.
         */
        private val ASSISTED_STEPS = listOf(0.0, 0.25, 0.5, 0.625, 0.75)
    }
}
