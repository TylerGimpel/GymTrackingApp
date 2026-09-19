package com.tylergimpel.gymtracker.domain

import java.time.LocalDate
import kotlin.math.abs

/** The last value [selector] answers for, or null where it answers for none of them. */
private fun <T, R> List<T>.lastNotNullOfOrNull(selector: (T) -> R?): R? =
    asReversed().firstNotNullOfOrNull(selector)

/**
 * One logged set of one exercise, as the engine reads it.
 *
 * A **Set** stripped to what a recommendation turns on: what the set was for, what it amounted to,
 * and what the app had asked of it. Not a [PerformedSet] — a drop is a continuation the engine
 * deliberately does not read, and a per-side set is one set that was worked at one load, so both
 * arrive here already resolved to the single effort progression is measured by (ADR 0003).
 */
data class TrainedSet(
    val type: SetType,
    val performance: Performance,
    /**
     * What the app asked of this set, where it asked anything.
     *
     * Empty for every set logged before there was an engine, and for imported history. That is not
     * a gap to be filled in: a set nobody prescribed cannot have been beaten or missed, and the
     * ladder says so rather than guessing.
     */
    val prescription: Prescription = Prescription(),
) {

    /** How this set answered what it was asked, or null where nothing asked anything. */
    val outcome: SetOutcome? get() = prescription.outcomeOf(performance)

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

    /**
     * Whether this set answered the weight it was asked to work at — done as asked, or tried and
     * short.
     *
     * Two ways not to answer, and the ladder must read neither as a failure. A set at a weight of
     * the lifter's own is a decision rather than a miss: they changed what they were doing rather
     * than falling short of it. And a set that was never at the working weight — a warm-up, or a
     * rung of a ramp the lifter stopped when it got hard, which is what a ramp is for — was not
     * answering that question at all.
     */
    val answeredThePrescription: Boolean
        get() = type.isAtTheWorkingWeight &&
            (outcome == SetOutcome.FOLLOWED || outcome == SetOutcome.FELL_SHORT)
}

/**
 * One session's worth of one exercise: the day it was trained, and the sets it held.
 *
 * Grouped by session rather than kept as a flat list of sets, because a session is the unit the
 * engine reasons in — it contributes one point to the estimate however many sets it took, and both
 * "what did I do last time" and "how did that go" are questions about a session rather than a row.
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
     * The weight the app asked this session to work at, where it asked for one.
     *
     * Working sets only, and the last of them rather than the first: a session that warmed up, was
     * asked for a weight and then took a back-off set was asked to work at the middle one.
     */
    val prescribedWorkingLoad: Kilograms?
        get() = sets.filter { it.type.isAtTheWorkingWeight }
            .lastNotNullOfOrNull { it.prescription.load }

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

    /**
     * How this session answered what the app asked of it.
     *
     * Judged on the best set that answered the prescription, which is what keeps a fatigued last
     * set from reading as a failed session: a lifter who managed the reps once managed them.
     *
     * **Judged against the range, not against the rep count asked for.** The count asked for is
     * last session's, so a lifter drifting 10, 9, 8 reps through an 8 - 12 range falls short of
     * the *prescription* twice running while never once leaving the band they are supposed to be
     * training in. Backing them off a tenth for that is precisely the demotion this ladder exists
     * to avoid. Missing means failing to reach the bottom of the range at the weight asked for.
     */
    fun verdict(repRange: RepRange): SessionVerdict {
        val asked = sets.filter { it.type.isAtTheWorkingWeight && it.prescription.load != null }
        if (asked.isEmpty()) return SessionVerdict.UNPRESCRIBED

        val best = asked.filter(TrainedSet::answeredThePrescription)
            .mapNotNull { it.performance.repsOrNull }
            .maxOrNull()
            ?: return SessionVerdict.OVERRIDDEN

        return when {
            best > repRange.top -> SessionVerdict.BEATEN
            best < repRange.bottom -> SessionVerdict.MISSED
            else -> SessionVerdict.MET
        }
    }

    /**
     * What this session lifted, where it went so far past the range that the weight was wrong.
     *
     * The rescue for a cold start that was seeded far too light: a lifter asked for eight who does
     * twenty has not had a good session, they have been handed the wrong weight, and stepping it
     * up one increment a fortnight would take a season to fix (spec #25's
     * `beatTargetBy >= 4: re-anchor from actual`).
     *
     * **Epley's rep cutoff is deliberately waived here**, and only here. The cutoff exists because
     * the formula stops meaning much past a dozen reps — but a rough estimate from the set that
     * actually happened beats an exact one from a weight nobody is lifting, and the next session
     * corrects it either way. Discarding the only evidence there is would leave the app prescribing
     * a weight it has already been shown is far too light.
     */
    fun blownThrough(repRange: RepRange): Kilograms? = sets
        .filter(TrainedSet::answeredThePrescription)
        .filter {
            (it.performance.repsOrNull ?: 0) >=
                repRange.top + Ladder.REPS_PAST_THE_TOP_THAT_BLOW_THROUGH_IT
        }
        .mapNotNull { set ->
            set.performance.loadOrNull?.let { load ->
                set.performance.repsOrNull?.let { reps -> epley(load, reps) }
            }
        }
        .maxByOrNull(Kilograms::value)
}

/**
 * Everything one exercise has been trained with, and what the app makes of it.
 *
 * One exercise's own sets and nothing else, which is the constraint that matters most here: a bench
 * press does not decide a dumbbell press, and no set of another exercise can reach this list to
 * move a recommendation (ADR 0004).
 */
class TrainingHistory(val sessions: List<TrainedSession>) {

    /**
     * Oldest first, whatever order they arrived in. A backdated session sorts where it was trained.
     *
     * Sorted once rather than on each of the half-dozen questions a recommendation asks of it.
     */
    private val chronological: List<TrainedSession> = sessions.sortedBy(TrainedSession::date)

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
        decay: LayoffDecay = LayoffDecay.DEFAULT,
        asOf: LocalDate? = null,
    ): ExerciseProgress? {
        val fresh = Anchor.from(sessions) ?: return null
        val weeksAway = asOf?.let { today -> lastTrained()?.let { weeksBetween(it, today) } } ?: 0L
        // Past the threshold there is no estimate worth prescribing from, and saying so is what
        // puts the exercise back into calibrating rather than quietly guessing at it.
        val anchor = decay.applyTo(fresh, weeksAway) ?: return null

        return ExerciseProgress(
            anchor = anchor,
            repRange = repRange,
            // A held weight is what the lifter was working at, and after a layoff that is exactly
            // what they should not be handed back. Where the estimate has aged, it answers.
            workingLoad = if (anchor != fresh) {
                rounding.roundDown(anchor.workingLoadFor(repRange))
            } else {
                workingLoad(anchor, repRange, rounding)
            },
        )
    }

    /** The day this exercise was last trained, where it has been. */
    fun lastTrained(): LocalDate? = chronological.lastOrNull()?.date

    /**
     * What the recent run of sessions says to do about the weight.
     *
     * A session that left the estimate behind clears the ladder rather than climbing it: whatever
     * the old weight had been doing says nothing about the new one, including a stop — a lift the
     * app gave up on and the lifter then blew through is one it has something to say about again.
     */
    fun ladderIn(repRange: RepRange): Ladder =
        if (substantialBeat(repRange) != null) {
            Ladder.HOLD
        } else {
            ladderFrom(chronological.map { it.verdict(repRange) })
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
     * The weight to work at: the one the lifter has been working at, moved by the ladder.
     *
     * **Held rather than followed.** Once the app has prescribed a weight, that weight is what
     * double progression holds while the reps climb — not whatever the estimate currently says.
     * Letting the estimate drive it would raise the bar on a single good session, which is what
     * the two-session rule exists to prevent, and lower it on a single bad one.
     *
     * Two things override the hold. A session that beat the estimate outright re-anchors, because
     * a held weight that was never right is not worth holding; and an exercise nothing has ever
     * been prescribed for has no held weight to start from, so the estimate answers — which is
     * every imported history and every exercise's first session after calibration.
     */
    private fun workingLoad(
        anchor: Anchor,
        repRange: RepRange,
        rounding: Increment,
    ): Kilograms {
        // #25 also asks for a jump like this to be surfaced as it happens, which belongs with the
        // logging flow that sees the set being logged (#53) rather than here.
        substantialBeat(repRange)?.let {
            return rounding.roundDown(Anchor(it).workingLoadFor(repRange))
        }

        val held = lastPrescribedLoad ?: return rounding.roundDown(anchor.workingLoadFor(repRange))

        return when (ladderIn(repRange)) {
            Ladder.ADVANCE -> rounding.oneStepAbove(held)
            // Signed, so a tenth off a barbell is lighter and a tenth off an assisted machine is
            // more help, and rounded down, which is the conservative direction in both.
            Ladder.BACK_OFF ->
                rounding.roundDown(Kilograms(held.value - abs(held.value) * Ladder.BACK_OFF_FRACTION))

            Ladder.HOLD, Ladder.CUT_VOLUME, Ladder.STOP -> held
        }
    }

    /**
     * The weight the app last asked for, which is the one the lifter has been working at.
     *
     * Null until the app has asked for one — through every imported history, and through the
     * calibration ramp, whose rungs are measurements rather than a weight to hold. Until then the
     * estimate answers.
     */
    private val lastPrescribedLoad: Kilograms?
        get() = chronological.lastNotNullOfOrNull { it.prescribedWorkingLoad }

    /**
     * Whether the weight now being prescribed is a jump rather than a step.
     *
     * The parent spec (#25) asks for this to be surfaced as it happens, and what "as it happens"
     * means was settled by #50: today is not history, so a session that blows past its target
     * moves nothing until the next one. The jump therefore shows up at the start of the session
     * that inherits it, which is the first moment a lifter sees the number it produced — and a
     * number that has moved by more than an **Increment** reads as a mistake without a word.
     */
    fun reAnchoredIn(repRange: RepRange): Boolean = substantialBeat(repRange) != null

    /**
     * What the last session proved the estimate wrong by, or null where it did not.
     *
     * Two ways to be wrong, and each catches what the other cannot. A lifter can blow past a
     * weight **in reps** — twenty at a weight asked for eight — which the estimate never sees,
     * because Epley stops reading reps at a dozen. Or they can blow past it **in weight**, by
     * loading what they actually wanted and lifting it, which the estimate sees plainly.
     *
     * The second is folded twice on purpose — once without the last session and once with it —
     * because what makes a session substantial is how it compares with what the app believed
     * *before* it.
     */
    private fun substantialBeat(repRange: RepRange): Kilograms? {
        val latest = chronological.lastOrNull() ?: return null
        latest.blownThrough(repRange)?.let { return it }

        val best = latest.estimated1rm ?: return null
        val before = Anchor.from(chronological.dropLast(1)) ?: return null
        return best.takeIf { before.isSubstantiallyBeatenBy(it) }
    }

    /**
     * Last session's reps at the weight being prescribed, brought inside the range.
     *
     * Last session rather than the last session that used this weight: what "unchanged" means is
     * that nothing has moved since the lifter was last here, and a weight they have not worked at
     * for a month says nothing about today.
     */
    private fun repsCarriedForward(progress: ExerciseProgress): Int? =
        chronological.lastOrNull()
            ?.bestRepsAt(progress.workingLoad)
            ?.let(progress.repRange::coerce)
}
