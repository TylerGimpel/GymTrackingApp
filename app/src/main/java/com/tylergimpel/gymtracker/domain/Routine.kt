package com.tylergimpel.gymtracker.domain

/**
 * What a routine is built toward.
 *
 * Not yet in `CONTEXT.md`'s glossary — the generator that consumes it arrives with its own spec,
 * and these are the values this project's own research consistently speaks in.
 */
enum class RoutineGoal { HYPERTROPHY, STRENGTH, ENDURANCE }

/**
 * Which end of the goal's span a lifter prefers to train at, where the goal leaves room for a choice.
 *
 * Not a rep range: a range is held per exercise and is the thing double progression climbs. This is
 * one of the inputs a range is seeded from, which is why it sits on the routine rather than the set.
 *
 * Not yet in `CONTEXT.md`'s glossary, and the three bands are placeholders — what each resolves to
 * is the recommendation engine's to settle.
 */
enum class RepPreference { LOW, MODERATE, HIGH }

/**
 * One exercise a day holds, and how many working sets it asks for.
 *
 * The set count is the engine's answer rather than the lifter's, and it does not care how the
 * exercise arrived in the day — generated or added by hand, a routine day says what to do and how
 * much of it. **Nothing enforces it**: a lifter who does more sets has trained more, and the app
 * has no business saying otherwise.
 */
data class PlannedExercise(
    val exercise: Exercise,
    /**
     * How many working sets, or null where nothing asked for any.
     *
     * Null is what a session trained before the app prescribed set counts reads back as, and it
     * is the truth about it: nothing was asked. Writing a number over those would be inventing a
     * prescription in the log, which is the one thing a training record must not do.
     *
     * The ramp is generated per session from the working weight and is not counted here.
     */
    val sets: Int? = null,
)

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
     * What this day holds, in the order it is to be trained.
     *
     * The plan side of the model: what a lifter intends, which a session copies rather than points
     * at. Empty is ordinary — a day is named before it is filled.
     */
    val exercises: List<PlannedExercise> = emptyList(),
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
