package com.tylergimpel.gymtracker.domain

/**
 * What a set is for.
 *
 * An explicit enum, never inferred from where a set sits or what it weighs: a light technique set
 * after a heavy top set is indistinguishable from a warm-up by position, and is not one. Getting
 * this wrong is invisible — it shows up only as a lifter being persistently over- or under-trained.
 */
enum class SetType(val stored: String) {
    /** Ramping up to the working weight. Not training, so it earns no volume. */
    WARMUP("warmup"),

    /** The work itself. */
    WORKING("working"),

    /**
     * A set performed to find out what the lifter can lift rather than to train them. Produced by
     * the cold-start ramp on an exercise with no history, and never counted — without a type of its
     * own, the first session on every exercise would corrupt both volume and progression history.
     */
    CALIBRATION("calibration"),

    /** Lighter work after a top set, and still work. */
    BACKOFF("backoff"),

    /** Taken to as many reps as possible, and still work. */
    AMRAP("amrap"),
    ;

    /** Whether a set of this type contributes to training volume. */
    val countsTowardVolume: Boolean get() = this in COUNTING

    companion object {
        /**
         * The types that count. Held here rather than as a flag per constant so that the answer to
         * "what counts" is one list someone can read in a second.
         */
        private val COUNTING = setOf(WORKING, BACKOFF, AMRAP)

        private val BY_STORED = entries.associateBy(SetType::stored)

        fun fromStored(value: String): SetType = requireNotNull(BY_STORED[value]) {
            "Unknown set type `$value`"
        }
    }
}
