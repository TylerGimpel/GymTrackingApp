package com.tylergimpel.traintracks.domain

/**
 * How one session answered what the app asked of an exercise.
 *
 * The unit the ladder counts in. A session rather than a set, because one hard set at the end of
 * a session is fatigue and a whole session short of the weight is information.
 */
enum class SessionVerdict {
    /** Cleared the top of the **Rep range** at the weight asked for. */
    BEATEN,

    /** Worked inside the range at the weight asked for. */
    MET,

    /** The weight asked for, and fewer reps than the range asks for at all. */
    MISSED,

    /**
     * Something was asked and the lifter did something else.
     *
     * **A different weight is a decision, not a failure**, and a ladder that read an override as a
     * miss would back off a weight nobody failed. It still breaks a run, because a run of sessions
     * doing what was asked has to be a run of sessions doing what was asked.
     */
    OVERRIDDEN,

    /**
     * Nothing was asked, so there is nothing to judge.
     *
     * Imported history, a session logged before there was an engine, a first session's calibration
     * ramp, or a session the app had already stopped prescribing for. **Invisible to the ladder**
     * rather than a break in a run: a session the app said nothing about is not evidence that what
     * it said before has stopped being true.
     */
    UNPRESCRIBED,
}

/**
 * What a run of sessions says to do about the weight.
 *
 * Graded rather than binary in both directions, because the two mistakes available here are
 * opposite and both bad: advancing on one good session walks a lifter up to a weight they cannot
 * hold, and demoting on one bad session punishes a poor night's sleep (ADR 0004).
 */
enum class Ladder {
    /** Nothing has happened often enough to act on. The same weight comes back. */
    HOLD,

    /** Two sessions past the top of the range: one increment, and the reps start again. */
    ADVANCE,

    /** Two sessions short: about a tenth off the weight. */
    BACK_OFF,

    /** Three: the weight has come down once already, so what gives now is the amount of work. */
    CUT_VOLUME,

    /** Short session after session: stop prescribing, and say so rather than guessing forever. */
    STOP,
    ;

    companion object {
        /**
         * How many sessions past the top of the range earn an increment.
         *
         * **Two, and it is the hysteresis the whole engine leans on.** One good session is a good
         * day — better sleep, more coffee, a spotter — and a weight raised on it is a weight the
         * lifter may not see again for a fortnight. Waiting for the second costs one session of
         * under-loading, which costs a fraction of a session's stimulus in a population where
         * almost any load works.
         */
        const val SESSIONS_BEFORE_ADVANCING = 2

        /**
         * How many sessions short of the range before the weight comes down.
         *
         * The same hysteresis pointed the other way: the first miss is free, always, because the
         * most likely explanation for one bad session is the lifter's week rather than the app's
         * arithmetic.
         */
        const val SESSIONS_BEFORE_BACKING_OFF = 2

        /**
         * After this many, the weight has already come down once.
         *
         * Taking it down again chases the lifter to the floor, so the intensity holds and the
         * amount of work is what gives.
         */
        const val SESSIONS_BEFORE_CUTTING_VOLUME = 3

        /**
         * How many sessions short before the app stops prescribing for the lift entirely.
         *
         * **An invented, tuneable number**, like the rest of these. Nothing in the literature says
         * when a recommendation has failed often enough to be worth withdrawing — but one that has
         * been wrong five sessions running is not going to be fixed by a sixth guess, and an app
         * that keeps guessing is one whose numbers stop being read at all.
         */
        const val SESSIONS_BEFORE_STOPPING = 5

        /**
         * How much comes off the weight on the second session short of the range.
         *
         * **An invented, tuneable number.** ADR 0004 records that the graded ladder's shape is
         * what the evidence supports — react rather than schedule, and grade rather than collapse
         * — while the size of the step is a judgement call nobody has measured. Roughly a tenth is
         * enough to feel and small enough to climb back from in a session or two. It must never
         * read as derived: a number that looks derived is a number nobody revisits.
         */
        const val BACK_OFF_FRACTION = 0.10

        /**
         * How many reps past the top of the range count as having blown through the weight.
         *
         * Spec #25 puts the rule in its session-transition pseudocode as `beatTargetBy >= 4:
         * re-anchor from actual`, and four is its number. It is counted from the **top of the
         * range** rather than from the reps asked for, because the reps asked for are the
         * conservative count inside the range: measured from there, a lifter who simply reached
         * the top of an 8 - 12 range would re-anchor on one session, which is the advance the
         * two-session rule exists to prevent.
         */
        const val REPS_PAST_THE_TOP_THAT_BLOW_THROUGH_IT = 4
    }
}

/**
 * What to do about the weight, given how the last few sessions went, oldest first.
 *
 * Reads the **run** the exercise is currently on rather than a tally, so a good session answers a
 * bad one instead of being averaged with it — and so nothing carries over from a month ago.
 *
 * Sessions the app said nothing about are dropped rather than counted as a break in the run. They
 * are why a lift the app has stopped prescribing for stays stopped: every session after the stop
 * is one the app asked nothing of, and a run of five misses that ended because the app stopped
 * asking has not been answered by anything.
 */
fun ladderFrom(verdicts: List<SessionVerdict>): Ladder {
    val answered = verdicts.filterNot { it == SessionVerdict.UNPRESCRIBED }
    val latest = answered.lastOrNull() ?: return Ladder.HOLD
    val run = answered.takeLastWhile { it == latest }.size
    return when (latest) {
        SessionVerdict.BEATEN ->
            if (run >= Ladder.SESSIONS_BEFORE_ADVANCING) Ladder.ADVANCE else Ladder.HOLD

        SessionVerdict.MISSED -> when {
            run >= Ladder.SESSIONS_BEFORE_STOPPING -> Ladder.STOP
            run >= Ladder.SESSIONS_BEFORE_CUTTING_VOLUME -> Ladder.CUT_VOLUME
            run >= Ladder.SESSIONS_BEFORE_BACKING_OFF -> Ladder.BACK_OFF
            else -> Ladder.HOLD
        }

        SessionVerdict.MET, SessionVerdict.OVERRIDDEN -> Ladder.HOLD

        // Filtered out above; a run of them cannot be the latest thing that happened.
        SessionVerdict.UNPRESCRIBED -> Ladder.HOLD
    }
}
