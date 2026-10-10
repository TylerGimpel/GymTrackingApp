package com.tylergimpel.traintracks.domain

import java.time.Duration

/**
 * What an exercise was asked for in a session: the whole list, and the part of it that is work.
 *
 * Two numbers because a ramp is not training. The [total] is the rungs plus the work, which is
 * what a lifter counting sets off in their head is counting; [working] is what the volume is.
 */
data class PlannedSets(val total: Int, val working: Int)

/**
 * How far through an exercise a lifter is, and how far through the work that counts.
 *
 * Two numbers rather than one, because a lifter three sets into an exercise wants to know both
 * "how much of this is left" and "how much of it was real" — a ramp of three warm-ups and one
 * working set is not four sets of training, and a screen that says `set 4` alone makes it look
 * like one.
 *
 * Each carries a total where the session knows one. **Nothing is clamped to it**: a lifter who
 * does five of three did five sets, and a counter that stopped at three would be the app telling
 * somebody they had finished while they were still training.
 */
data class SessionPace(
    val setsDone: Int,
    val workingDone: Int,
    /**
     * What this exercise was asked for, or null where nothing asked.
     *
     * Null is an ordinary session trained without a routine — or one the app is still calibrating
     * — rather than an incomplete one, and a denominator invented for either would be the screen
     * stating a plan nobody made. One nullable rather than two, so there is no way to be holding
     * half a plan.
     */
    val planned: PlannedSets? = null,
) {
    companion object {
        /**
         * Where [sets] leaves the lifter, counting the [pending] one they are about to do.
         *
         * Which counts toward the working total only if it is training: a lifter on the second
         * rung of a ramp is on their second set and none of it has been work yet, and a screen
         * that said otherwise would be making the exact claim the two numbers exist to separate.
         *
         * A [pending] nothing prescribed counts as work, because a set the lifter chose to do
         * with the app saying nothing is training they did.
         */
        fun of(
            sets: List<PerformedSet>,
            pending: SetType?,
            planned: PlannedSets? = null,
        ): SessionPace = SessionPace(
            setsDone = sets.size + 1,
            workingDone = sets.count { it.type.countsTowardVolume } +
                if (pending?.countsTowardVolume != false) 1 else 0,
            planned = planned,
        )
    }
}

/**
 * Roughly how much longer this session has to run, cardio included.
 *
 * **Roughly, and shown as such.** A lifter asking this is deciding whether they have time to
 * finish before something else, and an estimate they can act on beats a precise number the app
 * cannot produce: nothing here knows how many sets an exercise will get until the generator says
 * (#26), or how long the lifter will stand around between them.
 *
 * Every number below is an invented, tuneable judgement call. They are averages of an ordinary
 * gym session rather than measurements of this lifter, and the honest presentation of the total
 * is with a `~` in front of it.
 */
fun timeLeftIn(remaining: List<Exercise>): Duration =
    remaining.fold(Duration.ZERO) { total, exercise ->
        total + if (exercise.isCardio) TYPICAL_CARDIO else TYPICAL_LIFT
    }

/** How long an ordinary exercise takes: a few sets, each with its rest. */
private val TYPICAL_LIFT: Duration = Duration.ofMinutes(8)

/** How long a piece of cardio takes, where nothing has prescribed a duration for it yet. */
private val TYPICAL_CARDIO: Duration = Duration.ofMinutes(20)

/**
 * Roughly how long [day] will take, for the estimate Today shows beside Start.
 *
 * The same arithmetic the time ceiling is checked with, where the day says how many sets it asks
 * for; a day built before set counts existed says nothing, and falls back to an ordinary session's
 * length per exercise rather than to zero.
 */
fun Routine.estimatedLengthOf(day: RoutineDay): Duration {
    val counted = dayLength(day, cardio, RepRange.DEFAULT)
    return if (counted > Duration.ZERO) counted else timeLeftIn(day.exercises.map(PlannedExercise::exercise))
}
