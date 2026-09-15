package com.tylergimpel.gymtracker.domain

/** The sets logged against one exercise within one session, in the order they were performed. */
data class SessionExercise(
    val id: Long,
    /**
     * The exercise itself, not just its name.
     *
     * Volume is counted per muscle, so interpreting these sets needs what the movement targets —
     * carrying the whole exercise keeps that a fact about the exercise rather than something each
     * caller has to go and look up.
     */
    val exercise: Exercise,
    /** Where this exercise sits among the others in the session. Unrelated to any set's index. */
    val orderIndex: Int,
    val sets: List<PerformedSet>,
    /**
     * The superset this exercise was performed as part of, or null where it was performed alone.
     *
     * A reference rather than a copy, because a group is a live thing a lifter forms and breaks
     * mid-session — unlike the routine, which history only ever snapshots.
     */
    val supersetId: Long? = null,
) {
    /**
     * The index the next set logged against this exercise will take.
     *
     * Counted from the highest index already used rather than from the number of sets, so deleting
     * a set leaves a gap that is never refilled.
     */
    val nextSetIndex: Int get() = nextSetIndex(sets)

    /** The unit [set] reads in: its own where it overrode one, and the exercise's otherwise. */
    fun displayUnitFor(set: PerformedSet): WeightUnit = set.displayUnit ?: exercise.displayUnit

    /**
     * This exercise without the set at [setIndex]. Every remaining set keeps the index it had:
     * renumbering mid-session can corrupt the set the user is editing at that moment.
     */
    fun withoutSet(setIndex: Int): SessionExercise =
        copy(sets = sets.filterNot { it.setIndex == setIndex })

    companion object {
        /**
         * The index the next set logged after [sets] will take. Lives here rather than in a query
         * so that the rule about gaps has exactly one implementation.
         */
        fun nextSetIndex(sets: List<PerformedSet>): Int =
            (sets.maxOfOrNull(PerformedSet::setIndex) ?: 0) + 1
    }
}
