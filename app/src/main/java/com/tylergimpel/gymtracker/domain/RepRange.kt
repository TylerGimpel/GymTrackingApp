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
         * The range an exercise is worked in until something says otherwise.
         *
         * A placeholder with a real answer in it. What seeds a range properly is the routine's goal
         * and rep preference, which belongs with the generator that reads them (#26) — and until
         * that exists, every exercise being worked 8 - 12 is both the commonest band in the
         * research this project is built on and the one whose mistakes are cheapest.
         */
        val DEFAULT = RepRange(8, 12)
    }
}
