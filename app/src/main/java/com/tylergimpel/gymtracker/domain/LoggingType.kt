package com.tylergimpel.gymtracker.domain

/**
 * What a set of a given exercise records, and therefore which inputs the logging screen shows.
 *
 * [ASSISTED] inverts progression: less weight means more progress, so every comparison built on it
 * — beat, missed, personal best, graph direction — flips.
 */
enum class LoggingType(
    val stored: String,
    /**
     * What a set of this type records.
     *
     * Held here so that a screen asks the type what to show rather than deciding for it, and so
     * that adding a type is one line here instead of a hunt for every `when` that forgot it.
     */
    val recordsWeight: Boolean,
    val recordsReps: Boolean,
    val recordsDuration: Boolean,
    val recordsDistance: Boolean = false,
) {
    WEIGHT_REPS("weight_reps", recordsWeight = true, recordsReps = true, recordsDuration = false),

    // These two record a weight that is the belt or the machine's assistance rather than the load.
    // Giving that weight its own meaning is its own ticket; until then the field it is typed into
    // is the only place a lifter has to put it, so it stays.
    BODYWEIGHT_REPS("bodyweight_reps", recordsWeight = true, recordsReps = true, recordsDuration = false),
    ASSISTED("assisted", recordsWeight = true, recordsReps = true, recordsDuration = false),

    TIME("time", recordsWeight = false, recordsReps = false, recordsDuration = true),

    DISTANCE_TIME(
        "distance_time",
        recordsWeight = false,
        recordsReps = false,
        recordsDuration = true,
        recordsDistance = true,
    ),
    ;

    companion object {
        private val BY_STORED = entries.associateBy(LoggingType::stored)

        /**
         * The build step validates every logging type against this same closed vocabulary, so an
         * unknown value here means the asset database and the app have diverged.
         */
        fun fromStored(value: String): LoggingType = requireNotNull(BY_STORED[value]) {
            "Unknown logging type `$value` in the exercise bank"
        }
    }
}
