package com.tylergimpel.gymtracker.domain

/**
 * How an exercise's recommendation advances over time.
 *
 * Derived from the **Logging type** and overridable by the lifter, because the bank can only guess
 * from what an exercise measures and the lifter knows what they want coached.
 *
 * [NONE] is not a placeholder for a model nobody has written yet. A stretch, a hold and a treadmill
 * are training a lifter keeps track of rather than training the app tells them how to do, and a lift
 * somebody simply does not want coached is their business. Without a value that says so, "do not
 * prescribe this" would have to be inferred from the logging type wherever it was asked, and could
 * not be said at all about a barbell lift.
 *
 * Deliberately two values. ADR 0004 settles what the engine does — double progression inside a rep
 * range, anchored to an Epley estimate — so that is what a loaded exercise advances by; anything
 * finer belongs with the engine that reads it rather than with the bank that carries it.
 */
enum class ProgressionModel(val stored: String) {
    /**
     * The weight holds while reps climb through the rep range, and only once the top is reached does
     * the weight go up and the reps return to the bottom (ADR 0004).
     */
    DOUBLE_PROGRESSION("double_progression"),

    /** Recorded, never prescribed. */
    NONE("none"),
    ;

    /** Whether the app has anything to say about what to do next on this exercise. */
    val isPrescribed: Boolean get() = this != NONE

    companion object {
        /**
         * What an exercise that measures [loggingType] advances by, until the lifter says otherwise.
         *
         * A guess from the only thing the bank knows, and a cheap one to be wrong about: it costs a
         * tap to change and nothing is prescribed from it until the engine exists.
         */
        fun of(loggingType: LoggingType): ProgressionModel = when (loggingType) {
            LoggingType.WEIGHT_REPS,
            LoggingType.BODYWEIGHT_REPS,
            LoggingType.ASSISTED,
            -> DOUBLE_PROGRESSION

            // Nothing here has a load for a recommendation to advance.
            LoggingType.TIME, LoggingType.DISTANCE_TIME -> NONE
        }

        private val BY_STORED = entries.associateBy(ProgressionModel::stored)

        /** The model a stored value names, or null where nothing was stored. */
        fun fromStored(value: String?): ProgressionModel? = value?.let {
            requireNotNull(BY_STORED[it]) { "Unknown progression model `$it`" }
        }
    }
}
