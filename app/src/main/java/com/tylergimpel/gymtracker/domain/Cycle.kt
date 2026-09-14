package com.tylergimpel.gymtracker.domain

/** What became of a routine day within a cycle. */
enum class DayOutcome {
    /** Trained. A session realised it. */
    COMPLETED,

    /**
     * Marked as dealt with without a session. Recorded distinctly from a completion so that history
     * never claims training that did not happen.
     */
    SKIPPED,
}

/**
 * One full pass through every routine day.
 *
 * A cycle has no fixed duration in time — it is over when every day has been trained or skipped,
 * whether that took five days or five weeks.
 */
data class Cycle(
    val id: Long,
    /** Which pass through the routine this is, from 1. */
    val ordinal: Int,
    /** What has become of each routine day so far, keyed by routine day id. */
    val outcomes: Map<Long, DayOutcome>,
) {
    /** Whether every day of [routine] has been dealt with, so the next cycle begins. */
    fun isComplete(routine: Routine): Boolean =
        routine.days.isNotEmpty() && routine.days.all { it.id in outcomes }
}
