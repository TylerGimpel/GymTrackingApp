package com.tylergimpel.gymtracker.domain

import java.time.Duration

/**
 * What the app told the lifter to do for one set.
 *
 * Recorded beside what they actually did, which is the only way the difference between overriding a
 * recommendation and falling short of it can ever be known. Without it the log holds "you did 8 at
 * 60" and nothing about whether that was the plan, so an app reading its own history cannot tell a
 * lifter who chose to go lighter from one who tried and missed — and those two want opposite
 * responses from it.
 *
 * Every part is optional, because a prescription may be partial: a set can be told what weight to
 * use without being told how long to rest. All of it is absent on a set nothing prescribed, which
 * is every set logged before there was an engine to prescribe one.
 */
data class Prescription(
    /**
     * The load asked for, signed as **Performance.loadOrNull** is: a weight on the bar, a belt, or
     * a machine's help as a negative. Not called a weight, because on an assisted exercise it is
     * not one — and the comparison below reads it against a load.
     */
    val load: Kilograms? = null,
    val reps: Int? = null,
    val rest: Duration? = null,
) {
    val isEmpty: Boolean get() = load == null && reps == null && rest == null

    /**
     * How [performance] answered this prescription.
     *
     * Null where there was nothing to answer — no prescribed weight and no prescribed reps means
     * nothing was asked, so nothing was followed or missed.
     */
    fun outcomeOf(performance: Performance): SetOutcome? {
        val asked = reps ?: return weightOutcomeOf(performance)
        val performedReps = performance.repsOrNull ?: return weightOutcomeOf(performance)
        val load = this.load

        return when {
            // A different weight is a decision, whatever the reps did afterwards: the lifter
            // changed what they were asked to do rather than failing at it.
            load != null && !load.matches(performance.loadOrNull) -> SetOutcome.OVERRIDDEN
            performedReps >= asked -> SetOutcome.FOLLOWED
            else -> SetOutcome.FELL_SHORT
        }
    }

    private fun weightOutcomeOf(performance: Performance): SetOutcome? {
        val load = this.load ?: return null
        return if (load.matches(performance.loadOrNull)) SetOutcome.FOLLOWED else SetOutcome.OVERRIDDEN
    }

    /**
     * Whether the load asked for is the load that was lifted.
     *
     * Never an exact comparison, for the reason [isSameLoadAs] gives: a set the lifter loaded
     * exactly as asked must not read as an override on a thousandth of a kilogram.
     */
    private fun Kilograms.matches(other: Kilograms?): Boolean =
        other != null && isSameLoadAs(other)
}

/** What a set did about the **Prescription** it was given. */
enum class SetOutcome {
    /** The weight asked for, and at least the reps asked for. */
    FOLLOWED,

    /** A different weight: the lifter decided to do something else. */
    OVERRIDDEN,

    /** The weight asked for, and fewer reps than it asked for. */
    FELL_SHORT,
}


/**
 * What the lifter measured about a set, beyond the work itself.
 *
 * Together because they travel together — every one of them is optional, every one is the lifter's
 * own reading rather than anything the app derived, and they belong to the set rather than to an
 * effort: a per-side set is one set and felt one way.
 */
data class SetMeasurements(
    /** How hard it felt, 1 to 10. */
    val rpe: Double? = null,
    /** Beats per minute, typed in: this project integrates with no wearable and never will. */
    val heartRate: Int? = null,
    /** How long the lifter actually rested afterwards. */
    val restTaken: Duration? = null,
) {
    val isEmpty: Boolean get() = rpe == null && heartRate == null && restTaken == null
}
