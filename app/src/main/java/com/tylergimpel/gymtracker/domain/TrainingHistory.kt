package com.tylergimpel.gymtracker.domain

import java.time.LocalDate

/**
 * One logged set of one exercise, as the engine reads it.
 *
 * A **Set** stripped to what a recommendation turns on: what the set was for, and what it amounted
 * to. Not a [PerformedSet] — a drop is a continuation the engine deliberately does not read, and a
 * per-side set is one set that was worked at one load, so both arrive here already resolved to the
 * single effort progression is measured by (ADR 0003).
 */
data class TrainedSet(val type: SetType, val performance: Performance) {

    /**
     * What this set says a one-rep maximum is, or null where it says nothing.
     *
     * Three ways to say nothing, and each of them matters: a warm-up is a ramp rather than work, a
     * hold or a run is not measured in a load at all, and a set above [Anchor.MAX_REPS_ESTIMATED_FROM]
     * reps is out where the formula stops meaning anything.
     */
    val estimated1rm: Kilograms?
        get() {
            if (!type.informsProgression) return null
            val load = performance.loadOrNull ?: return null
            val reps = performance.repsOrNull ?: return null
            if (reps > Anchor.MAX_REPS_ESTIMATED_FROM) return null
            return epley(load, reps)
        }

    /** The load this set was worked at, where it was work and was measured in one. */
    val workedLoad: Kilograms?
        get() = if (type.informsProgression) performance.loadOrNull else null
}

/**
 * One session's worth of one exercise: the day it was trained, and the sets it held.
 *
 * Grouped by session rather than kept as a flat list of sets, because a session is the unit the
 * engine reasons in — it contributes one point to the estimate however many sets it took, and "what
 * did I do last time" is a question about a session rather than about a row.
 */
data class TrainedSession(val date: LocalDate, val sets: List<TrainedSet>) {

    /**
     * What this session says a one-rep maximum is: its best set, or null where none of them says
     * anything.
     *
     * The best rather than the last or the mean. A back-off set is lighter by design and a fatigued
     * final set says less than the top set did, and taking the maximum means neither has to be
     * filtered out by type.
     */
    val estimated1rm: Kilograms?
        get() = sets.mapNotNull(TrainedSet::estimated1rm).maxByOrNull(Kilograms::value)

    /**
     * The most reps worked at [load] in this session, or null where it was not worked at all.
     *
     * The most, because double progression asks whether the top of the range has been reached at
     * this weight, and the answer to that is a lifter's best set rather than their most tired one.
     */
    fun bestRepsAt(load: Kilograms): Int? = sets
        .filter { it.workedLoad?.isSameLoadAs(load) == true }
        .mapNotNull { it.performance.repsOrNull }
        .maxOrNull()
}

/**
 * Everything one exercise has been trained with, and what the app makes of it.
 *
 * One exercise's own sets and nothing else, which is the constraint that matters most here: a bench
 * press does not decide a dumbbell press, and no set of another exercise can reach this list to
 * move a recommendation (ADR 0004).
 */
@JvmInline
value class TrainingHistory(val sessions: List<TrainedSession>) {

    /**
     * What the app has worked out about this exercise, or null where it has nothing to work from.
     *
     * Null is the honest answer for a first session and for an exercise that has only ever been
     * warmed up with: there is no number to borrow from a neighbouring lift, so the app says so and
     * calibrates instead (#49).
     */
    fun progressIn(
        repRange: RepRange = RepRange.DEFAULT,
        rounding: Increment = Increment.DEFAULT,
    ): ExerciseProgress? {
        val anchor = Anchor.from(sessions) ?: return null
        return ExerciseProgress(
            anchor = anchor,
            repRange = repRange,
            workingLoad = rounding.roundDown(anchor.workingLoadFor(repRange)),
        )
    }

    /**
     * What to ask of the next set of this exercise, given what the app has worked out about it.
     *
     * The load is [progress]'s and the rep count is the **conservative** one inside the range: what
     * was done last time where the weight has not moved, and the bottom where it has. Offering the
     * top instead would make a one-tap log claim an advance that may not have happened, and being
     * confidently wrong in the direction of too much is the one failure in this engine that can
     * hurt somebody.
     */
    fun nextSetOf(progress: ExerciseProgress): Prescription = Prescription(
        load = progress.workingLoad,
        reps = repsCarriedForward(progress) ?: progress.repRange.bottom,
    )

    /**
     * Last session's reps at the weight being prescribed, brought inside the range.
     *
     * Last session rather than the last session that used this weight: what "unchanged" means is
     * that nothing has moved since the lifter was last here, and a weight they have not worked at
     * for a month says nothing about today.
     */
    private fun repsCarriedForward(progress: ExerciseProgress): Int? =
        sessions.sortedBy(TrainedSession::date).lastOrNull()
            ?.bestRepsAt(progress.workingLoad)
            ?.let(progress.repRange::coerce)
}
