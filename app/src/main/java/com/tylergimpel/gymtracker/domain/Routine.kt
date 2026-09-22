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
 * What a **Routine day** is for.
 *
 * Two kinds, and the distinction is arithmetic rather than behavioural. **Nothing about the
 * rotation reads this**: the **Cursor** takes no interest in what a day contains, and it must
 * stay that way, because the moment skipping a cardio day means something different from skipping
 * a lifting one the lifter has two sets of rules to keep straight.
 *
 * What does read it is volume. A cardio position carries no resistance work, so it is excluded
 * from divisibility — `Upper / Lower / Cardio` balances despite three not dividing by two — and
 * from the cadence that volume targets are translated through (ADR 0005).
 */
enum class RoutineDayKind {
    LIFTING,
    CARDIO,
}

/**
 * Where a lifter's cardio goes, if they do any.
 *
 * A property of the routine because it is a statement about how they train rather than about any
 * one day, and what the generator reads when it decides whether to put cardio days in a rotation.
 */
enum class CardioPlacement {
    /** None in the routine. Cardio trained anyway is a session like any other. */
    NONE,

    /** Its own position in the rotation, taking its turn. */
    OWN_DAY,

    /**
     * After a lifting session, taking no position.
     *
     * The lifting day still happened, so neither the rotation nor the cadence changes. What it
     * does change is how long the session runs, which the time ceiling has to account for (#76).
     */
    AFTER_LIFTING,
}

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
    /**
     * Whether the lifter marked this one to keep through a regeneration.
     *
     * Means something on a **Routine day** and nothing in a session's **Plan**, where it is always
     * false: a plan records what a session was asked for, and whether the lifter intended to keep
     * an exercise next time they rebuilt the routine is not part of that.
     */
    val pinned: Boolean = false,
)

/**
 * How many working sets an exercise asks for, given what the lifter is training for.
 *
 * **Invented, and to be replaced.** There is no dose-response data behind a per-exercise set
 * count — only behind weekly volume per muscle — and what will prescribe this properly is the
 * generator dividing a real budget across a day (#74). Until then, more sets of fewer reps for
 * strength and fewer of more for endurance is the shape the research does support, and these are
 * three numbers chosen to sit in it.
 *
 * Deliberately not a function of the exercise. Whether a compound earns more sets than an
 * isolation movement is a budgeting question, and budgeting is exactly what this stands in for.
 */
fun setsFor(goal: RoutineGoal): Int = when (goal) {
    RoutineGoal.STRENGTH -> 5
    RoutineGoal.HYPERTROPHY -> 4
    RoutineGoal.ENDURANCE -> 3
}

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
     * Whether this is a lifting day or a cardio one.
     *
     * Lifting unless something says otherwise, which is what a day added by hand is until the
     * lifter says what it is for.
     */
    val kind: RoutineDayKind = RoutineDayKind.LIFTING,
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
    /** Where this lifter's cardio goes, if they do any. */
    val cardio: CardioPlacement = CardioPlacement.NONE,
    /**
     * The shape this routine was laid out from, where it was laid out from one.
     *
     * Null for one built by hand or rebuilt from the log. Regenerating those would be inventing a
     * structure nobody chose, so the offer is simply not made.
     */
    val shape: SplitShape? = null,
    val days: List<RoutineDay>,
) {
    /** The days in rotation order, whatever order they arrived in. */
    val rotation: List<RoutineDay> get() = days.sortedBy(RoutineDay::orderIndex)

    /**
     * How many positions carry resistance work, out of [days] in total.
     *
     *
     * The denominator half of the one piece of arithmetic in this project that is wrong silently.
     * Training five times a week on `Upper / Lower / Cardio` is **3.33 lifting sessions a week**,
     * not five, and feeding the raw cadence into volume translation under-prescribes lifting by
     * 40% for anyone with a cardio day (ADR 0005). Divisibility counts these too, which is why
     * `Upper / Lower / Cardio` is a valid two-rotation despite having three positions.
     */
    val liftingPositions: Int
        get() = days.count { it.kind == RoutineDayKind.LIFTING }

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
