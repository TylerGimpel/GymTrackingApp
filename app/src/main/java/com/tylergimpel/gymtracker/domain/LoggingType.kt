package com.tylergimpel.gymtracker.domain

/**
 * What a set of a given exercise records, and therefore which inputs the logging screen shows.
 *
 * [ASSISTED] inverts progression: less weight means more progress, so every comparison built on it
 * — beat, missed, personal best, graph direction — flips.
 */
enum class LoggingType(val stored: String) {
    WEIGHT_REPS("weight_reps"),
    BODYWEIGHT_REPS("bodyweight_reps"),
    ASSISTED("assisted"),
    TIME("time"),
    DISTANCE_TIME("distance_time"),
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
