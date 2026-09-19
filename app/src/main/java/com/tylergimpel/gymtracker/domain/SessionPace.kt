package com.tylergimpel.gymtracker.domain

import java.time.Duration

/**
 * How far through an exercise a lifter is, and how far through the work that counts.
 *
 * Two numbers rather than one, because a lifter three sets into an exercise wants to know both
 * "how much of this is left" and "how much of it was real" — a ramp of three warm-ups and one
 * working set is not four sets of training, and a screen that says `set 4` alone makes it look
 * like one.
 *
 * Two numbers and no totals. Nothing prescribes how many sets an exercise gets until the
 * generator does (#26), and a screen that invented one would be stating a plan nobody made — so
 * `set 3` is the honest thing to say and `set 3 of 5` waits for something that knows.
 */
data class SessionPace(
    val setsDone: Int,
    val workingDone: Int,
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
        fun of(sets: List<PerformedSet>, pending: SetType?): SessionPace = SessionPace(
            setsDone = sets.size + 1,
            workingDone = sets.count { it.type.countsTowardVolume } +
                if (pending?.countsTowardVolume != false) 1 else 0,
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
fun timeLeftIn(remaining: List<Exercise>): Duration = remaining.fold(Duration.ZERO) { total, exercise ->
    total + if (exercise.isCardio) TYPICAL_CARDIO else TYPICAL_LIFT
}

/** How long an ordinary exercise takes: a few sets, each with its rest. */
private val TYPICAL_LIFT: Duration = Duration.ofMinutes(8)

/** How long a piece of cardio takes, where nothing has prescribed a duration for it yet. */
private val TYPICAL_CARDIO: Duration = Duration.ofMinutes(20)
