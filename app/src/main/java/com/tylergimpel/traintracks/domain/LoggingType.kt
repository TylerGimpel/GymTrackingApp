package com.tylergimpel.traintracks.domain

/** One thing a set can measure. What a **Logging type** takes is a set of these. */
enum class Measurement {
    /** A load lifted off a rack or a stack. */
    WEIGHT,

    /** A belt or a plate hung off a lifter whose own body is the load. */
    ADDED_WEIGHT,

    /** How much of the work a machine did for the lifter. */
    ASSISTANCE,

    REPS,
    DURATION,
    DISTANCE,
}

/**
 * What a set of a given exercise records, and therefore which inputs the logging screen shows.
 *
 * A set of measurements rather than a flag apiece, so that a type reads as the sentence it is —
 * "a pull-up counts reps and may carry a belt" — and so a screen asks it what to show rather than
 * deciding for it.
 *
 * [ASSISTED] inverts progression: less assistance is more progress, so every comparison built on it
 * — beat, missed, personal best, graph direction — flips. Nothing here has to remember that, because
 * what a **Performance** reports as its load is signed; see `Performance.loadOrNull`.
 */
enum class LoggingType(val stored: String, val records: Set<Measurement>) {
    WEIGHT_REPS("weight_reps", setOf(Measurement.WEIGHT, Measurement.REPS)),

    /** A pull-up and a weighted pull-up are one exercise, so a belt is optional rather than a type. */
    BODYWEIGHT_REPS("bodyweight_reps", setOf(Measurement.ADDED_WEIGHT, Measurement.REPS)),

    ASSISTED("assisted", setOf(Measurement.ASSISTANCE, Measurement.REPS)),

    TIME("time", setOf(Measurement.DURATION)),

    DISTANCE_TIME("distance_time", setOf(Measurement.DISTANCE, Measurement.DURATION)),
    ;

    val recordsWeight: Boolean get() = Measurement.WEIGHT in records

    val recordsAddedWeight: Boolean get() = Measurement.ADDED_WEIGHT in records

    val recordsAssistance: Boolean get() = Measurement.ASSISTANCE in records

    /** Whether a weight of any kind is typed here — a load, a belt, or a machine's help. */
    val recordsAnyWeight: Boolean get() = recordsWeight || recordsAddedWeight || recordsAssistance

    val recordsReps: Boolean get() = Measurement.REPS in records

    val recordsDuration: Boolean get() = Measurement.DURATION in records

    val recordsDistance: Boolean get() = Measurement.DISTANCE in records

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
