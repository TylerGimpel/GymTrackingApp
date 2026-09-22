package com.tylergimpel.gymtracker.domain

/**
 * The span of reps a working set is prescribed in, such as 6 - 8.
 *
 * Held per exercise, because it is the exercise's own progression that moves through it: the weight
 * stays while the reps climb from the bottom to the top, and only once the top is reached does the
 * weight go up and the reps return to the bottom. A recommendation therefore targets the range
 * rather than one rep count — and the two ends do different jobs, which is why this is a pair
 * rather than a single target the two of them would hide.
 *
 * The bottom is what ADR 0004's load formula takes, because the bottom is where a lifter lands
 * after the weight goes up.
 */
data class RepRange(val bottom: Int, val top: Int) {
    init {
        require(bottom >= 1) { "A rep range starts at one rep or more, not $bottom" }
        require(top >= bottom) { "A rep range of $bottom - $top has its top below its bottom" }
        // Far outside anything anybody trains in, and cheaper to refuse where the range is built
        // than to explain further downstream. It is not what keeps the load formula honest — that
        // is the cutoff in `Anchor.workingLoadFor`, which caps the reps it reads an estimate back
        // through whatever range it is handed.
        require(top <= MAX_PRESCRIBED_REPS) { "A rep range topping out at $top is not training" }
    }

    /**
     * [reps] brought inside the range.
     *
     * What a session that cleared the top is asked for next time: the band is what the exercise is
     * worked in, and prescribing outside it would claim an advance the weight has not made yet.
     */
    fun coerce(reps: Int): Int = reps.coerceIn(bottom, top)

    companion object {
        /** The widest range this project will prescribe in. Past it, nobody is lifting. */
        const val MAX_PRESCRIBED_REPS = 25

        /**
         * The range an exercise is worked in where nothing at all has been said.
         *
         * A real answer rather than a placeholder: 8 - 12 is the commonest band in the research
         * this project is built on and the one whose mistakes are cheapest. What normally decides
         * a band is [seedRepRange], reading the routine's goal and rep preference — this is what
         * answers before there is a routine to read.
         */
        val DEFAULT = RepRange(8, 12)
    }
}

/**
 * The band a **Routine** puts an exercise in, and what the goal had to overrule to get there.
 *
 * Two values rather than one because an override the lifter is not told about is the failure this
 * whole conflict rule exists to prevent: silently satisfying a preference produces training that
 * will not deliver the goal, and nobody finds out for three months.
 */
data class SeededRepRange(
    val range: RepRange,
    /**
     * The preference the goal overruled, or null where nothing was overruled.
     *
     * Null is the ordinary case and says nothing on screen, like everything else the app is not
     * obliged to announce.
     */
    val overruled: RepPreference? = null,
)

/**
 * The band an exercise is worked in, given what the lifter is training for and how they like to
 * train.
 *
 * **The goal decides the span and the preference chooses inside it.** A goal is what the lifter
 * asked the app to deliver; a preference is how they would rather get there, which is a real
 * thing to want but not one that can be honoured at the cost of the answer.
 *
 * Only one combination is a genuine conflict, and this spec's table names it: **a strength goal
 * with a high rep preference**. Strength is a load adaptation and high reps cannot supply the
 * load, so the goal wins and the lifter is told. An endurance goal with a low preference is not
 * the mirror image and is not overruled — it lands at the bottom of an endurance span, which is
 * still endurance work.
 *
 * **The spans are anchored; the splits inside them are invented.** 1–6 RM for strength is where
 * the load evidence puts it and the effort line at fifteen reps is why an endurance band reaches
 * it. Exactly where a low preference stops and a moderate one starts is a judgement call, like
 * the layoff decay rate and the skip threshold, and should look like one.
 */
fun seedRepRange(goal: RoutineGoal, preference: RepPreference): SeededRepRange = when (goal) {
    RoutineGoal.STRENGTH -> when (preference) {
        RepPreference.LOW -> SeededRepRange(RepRange(3, 5))
        RepPreference.MODERATE -> SeededRepRange(RepRange(4, 6))
        // Opposed by the load evidence, so the goal takes it — and says so.
        RepPreference.HIGH -> SeededRepRange(RepRange(4, 6), overruled = RepPreference.HIGH)
    }

    RoutineGoal.HYPERTROPHY -> when (preference) {
        RepPreference.LOW -> SeededRepRange(RepRange(6, 8))
        RepPreference.MODERATE -> SeededRepRange(RepRange(8, 12))
        // Load-independent up here only if the effort is high, which is what the screen's effort
        // line exists to say. Nothing is overruled: this is a band hypertrophy genuinely works in.
        RepPreference.HIGH -> SeededRepRange(RepRange(12, 15))
    }

    RoutineGoal.ENDURANCE -> when (preference) {
        RepPreference.LOW -> SeededRepRange(RepRange(12, 15))
        RepPreference.MODERATE -> SeededRepRange(RepRange(15, 20))
        RepPreference.HIGH -> SeededRepRange(RepRange(20, 25))
    }
}

/**
 * The band in force for one exercise: what the lifter chose, or what the routine seeded.
 *
 * The lifter has the last word and keeps it. A goal seeds a band for an exercise nobody has
 * spoken about; it does not reach back and correct one somebody has. That is why a chosen range
 * lives where the lifter's other choices live rather than beside the estimate — clearing a choice
 * and forgetting an estimate must never be the same operation.
 */
fun repRangeOf(chosen: RepRange?, seeded: RepRange): RepRange = chosen ?: seeded
