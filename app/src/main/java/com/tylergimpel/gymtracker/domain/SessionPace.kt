package com.tylergimpel.gymtracker.domain

import java.time.Duration

/**
 * What an exercise was asked for in a session: the whole list, and the part of it that is work.
 *
 * Two numbers because a ramp is not training. The [total] is the rungs plus the work, which is
 * what a lifter counting sets off in their head is counting; [working] is what the volume is.
 */
data class PlannedSets(val total: Int, val working: Int)

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
     * How many sets this exercise was asked for, or null where nothing asked.
     *
     * Null is an ordinary session trained without a routine, not an incomplete one — and a
     * denominator invented for it would be the screen stating a plan nobody made.
     */
    val setsPlanned: Int? = null,
    val workingPlanned: Int? = null,
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
            setsPlanned = planned?.total,
            workingPlanned = planned?.working,
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
