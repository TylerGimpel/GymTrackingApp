package com.tylergimpel.gymtracker.domain

/**
 * What a routine is built toward.
 *
 * Not yet in `CONTEXT.md`'s glossary — the generator that consumes it arrives with its own spec,
 * and these are the values this project's own research consistently speaks in.
 */
enum class RoutineGoal { HYPERTROPHY, STRENGTH, ENDURANCE }

/** The rep range a lifter prefers to work in, where the goal leaves room for a choice. */
enum class RepPreference { LOW, MODERATE, HIGH }

/**
 * One ordered position within a routine, such as "Push".
 *
 * Never a calendar day and never a day of the week. Rest is implied by not training, so it occupies
 * no position of its own.
 */
data class RoutineDay(
    val id: Long,
    /** Where this day sits in the rotation, from 1. */
    val orderIndex: Int,
    val name: String,
    /**
     * A position carrying cardio instead of resistance work. It takes its turn in the cycle like
     * any other day, but contributes no training volume and is excluded when translating volume
     * targets (ADR 0005).
     */
    val isCardio: Boolean = false,
)

/**
 * The whole repeating training plan a lifter follows.
 *
 * Goal, cadence, equipment and rep preference are properties of the routine, editable without
 * touching its day structure — changing a goal is not a revision of the rotation.
 */
data class Routine(
    val id: Long,
    val name: String,
    val goal: RoutineGoal,
    /**
     * Expected sessions per week. It exists only to translate volume targets into an unanchored
     * cycle; it never schedules anything and never nags.
     */
    val cadence: Int,
    /** The equipment available, doubling as the gym checklist. */
    val equipment: Set<String>,
    val repPreference: RepPreference,
    val days: List<RoutineDay>,
) {
    /** The days in rotation order, whatever order they arrived in. */
    val rotation: List<RoutineDay> get() = days.sortedBy(RoutineDay::orderIndex)

    /**
     * The first day of [cycle] not yet completed or skipped — what to train next.
     *
     * Takes no date, no clock and no duration, which is what makes a layoff free: there is no
     * elapsed time for the rule to notice and no missed-workout state to fall into. A [cycle] of
     * null is a routine that has not started one yet.
     *
     * A cycle with every day dealt with has finished, and the next one begins with everything
     * undone — so the cursor returns to the first day rather than to nothing.
     */
    fun cursor(cycle: Cycle?): RoutineDay? {
        if (cycle == null) return rotation.firstOrNull()
        return rotation.firstOrNull { it.id !in cycle.outcomes } ?: rotation.firstOrNull()
    }
}
