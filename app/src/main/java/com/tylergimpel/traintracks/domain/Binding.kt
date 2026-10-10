package com.tylergimpel.traintracks.domain

import java.time.Duration

/**
 * How much training a lifter has behind them.
 *
 * Only ever read to resolve one conflict, and held as three coarse bands because that is all the
 * evidence supports: the advice a novice gets about training near failure is different, and
 * nothing finer than "new to this" is needed to apply it.
 */
enum class Experience { NOVICE, TRAINED, ADVANCED }

/**
 * Something the routine cannot satisfy, and what would give if the lifter relaxed it.
 *
 * **Never an error and never a refusal.** An impossible request is a conversation: the app says
 * what is binding, offers what could be relaxed, and the lifter decides. A bare failure would be
 * the app declining to have that conversation.
 */
sealed interface Binding {

    /** What could be changed to make this go away, in the lifter's own terms. */
    val relax: List<String>

    /**
     * A day that will not fit the time it has.
     *
     * **The time ceiling usually binds before volume does**, so this is the common one. It names
     * the day rather than the routine, because a lifter shortens a session rather than a plan.
     */
    data class SessionTooLong(
        val day: String,
        val estimated: Duration,
        val ceiling: Duration,
        override val relax: List<String>,
    ) : Binding

    /**
     * A muscle landing outside the band the goal aims at.
     *
     * Reported as the numbers and the band, never as a grade — the same register the routine
     * report keeps. A generated routine ordinarily lands inside its band, because the budget
     * counts the work the cycle already gives a muscle before adding its own, by the same rule
     * this is measured by (#84). What reaches this is mostly a routine the lifter built or edited,
     * a muscle the per-session cap holds short on too few days, one nothing in the gym can train,
     * or whole-set rounding at the edge of the band.
     */
    data class VolumeOutsideBand(
        val muscle: String,
        val weeklySets: Double,
        val target: ClosedFloatingPointRange<Double>,
        override val relax: List<String>,
    ) : Binding

    /**
     * One of the [MAIN_MUSCLES] that nothing in the cycle trains directly.
     *
     * **A fact about the cycle, never a verdict on it.** "This cycle never trains hamstrings" is
     * something a lifter can act on or choose to ignore — a routine that skips a muscle on purpose
     * is theirs to build, and nothing here blocks it.
     *
     * [weeklySets] is what it gets anyway, as secondary work in other muscles' exercises. Zero
     * means nothing touches it at all; more than zero means "never trained" would be false, and
     * the screen says which. Coverage is by direct work either way, as it is for the generator.
     */
    data class MuscleNotTrained(
        val muscle: String,
        val weeklySets: Double,
        override val relax: List<String>,
    ) : Binding

    /**
     * A muscle the lifter's exclusions have left nothing to train with.
     *
     * Carries the banned candidates rather than resolving it, because **the app asks which is
     * least bad**. Silently including one would break the only promise a **Dislike** makes, and
     * silently skipping the muscle would leave a hole in the training that nobody chose. Both
     * decide something only the lifter can decide.
     */
    data class OnlyDislikedLeft(
        val muscle: String,
        val candidates: List<Exercise>,
        override val relax: List<String>,
    ) : Binding
}

/**
 * A conflict between what the lifter asked for and what they said they prefer.
 *
 * **Every one of these resolves *and* explains itself.** Silently satisfying a preference produces
 * a routine that will not deliver the goal, and the lifter does not find out for three months —
 * which is the failure the whole table exists to prevent.
 */
data class Conflict(
    /**
     * What took precedence, or null where nothing was overridden.
     *
     * Null is the interesting case: one row of the table steers and overrules nothing, because
     * both sides of it are defensible.
     */
    val wonBy: RoutineGoal?,
    val explanation: String,
)

/**
 * The conflicts between a goal, a preference, the gym and the lifter's experience.
 *
 * The spec's table, less the one row settled where a **Rep range** is seeded — which is reported
 * here too, so a lifter meets all of them in one place rather than discovering one on the routine
 * screen and the rest somewhere else.
 *
 * **The explanations are the point**, not decoration. An override nobody is told about is exactly
 * what this exists to prevent.
 */
fun conflictsFor(
    goal: RoutineGoal,
    preference: RepPreference,
    experience: Experience,
    equipment: Set<String>,
): List<Conflict> = buildList {
    // Opposed by the load evidence: strength is a load adaptation and high reps cannot supply it.
    if (goal == RoutineGoal.STRENGTH && preference == RepPreference.HIGH) {
        add(
            Conflict(
                wonBy = RoutineGoal.STRENGTH,
                explanation = "Training for strength needs heavy sets, so your exercises are " +
                    "set to low reps rather than the higher reps you prefer.",
            ),
        )
    }

    // A gym with no way to load a lift heavily cannot deliver a 1-6RM whatever anybody prefers.
    val noHeavyLoad = equipment.isNotEmpty() && !equipment.canLoadHeavy()
    if (preference == RepPreference.LOW && noHeavyLoad) {
        add(
            Conflict(
                wonBy = goal,
                explanation = "Nothing at your gym loads heavily enough for the low reps you " +
                    "prefer, so your exercises are set to a range it can actually deliver.",
            ),
        )
    }

    // The row that overrides nothing. High reps only grow muscle when taken near failure, and
    // novices are advised away from training there — so the app says what it thinks and leaves
    // the preference where the lifter put it. Both sides are defensible.
    if (goal == RoutineGoal.HYPERTROPHY &&
        preference == RepPreference.HIGH &&
        experience == Experience.NOVICE
    ) {
        add(
            Conflict(
                wonBy = null,
                explanation = "High reps only build muscle when the sets are taken close to " +
                    "failure, which is a hard thing to judge early on. Moderate reps are easier " +
                    "to get right — but this is your call and nothing has been changed.",
            ),
        )
    }
}

/**
 * Whether any of this equipment can be loaded heavily enough for low-rep work.
 *
 * One rule with two readers — the band a goal seeds and the conflict that explains overruling a
 * preference — and they must agree, or the app overrules somebody for a reason it then denies.
 */
internal fun Set<String>.canLoadHeavy(): Boolean = any {
    it in setOf("barbell", "dumbbell", "machine", "kettlebells", "cable", "e-z curl bar")
}

/**
 * How long one day runs, which is the number the ceiling is checked against.
 *
 * **Rest is most of a session**, and it is the engine's own — derived per exercise from what that
 * set is, so a heavy multi-joint lift rests three minutes and a machine isolation set ninety
 * seconds. A ceiling assuming one number for both would be measuring the wrong session.
 *
 * Same-day cardio is added because the lifter is in the gym for it: it takes no position in the
 * rotation and does not touch the cadence, since the lifting session still happened, but it is
 * time (ADR 0005).
 *
 * Both constants below are invented, tuneable judgement calls — averages of an ordinary session
 * rather than measurements of this lifter.
 */
internal fun dayLength(
    day: RoutineDay,
    cardio: CardioPlacement,
    repRange: RepRange,
): Duration {
    val lifting = day.exercises.fold(Duration.ZERO) { total, planned ->
        val rest = planned.exercise.chosenRest
            ?: restFor(planned.exercise, SetType.WORKING, repRange)
        total + (UNDER_THE_BAR + rest).multipliedBy((planned.sets ?: 0).toLong())
    }
    val andCardio =
        if (cardio == CardioPlacement.AFTER_LIFTING) SAME_DAY_CARDIO else Duration.ZERO
    return lifting + andCardio
}

/** How long a set takes to actually perform. Invented, and an average of nothing in particular. */
private val UNDER_THE_BAR: Duration = Duration.ofSeconds(45)

/** How long cardio after lifting runs for, where the lifter does any. Invented. */
private val SAME_DAY_CARDIO: Duration = Duration.ofMinutes(20)

/**
 * How long a session is allowed to run before the app says so.
 *
 * **Invented and tuneable**, like the rest. Ninety minutes is where an ordinary gym session stops
 * being ordinary; nothing measured it.
 */
val SESSION_CEILING: Duration = Duration.ofMinutes(Routine.USUAL_SESSION_MINUTES.toLong())

/**
 * Everything about this routine that cannot be satisfied, and what would give.
 *
 * Checked in the order they bind: the time ceiling first, because it usually binds before volume
 * does, then the volume bands. Empty is the ordinary answer and means every constraint holds.
 */
fun bindingOn(
    routine: Routine,
    ceiling: Duration = Duration.ofMinutes(routine.sessionMinutes.toLong()),
    /**
     * The bank, where the least-bad question is worth asking.
     *
     * Empty skips it — not because the question does not matter, but because it cannot be
     * answered without knowing what the gym could have offered instead.
     */
    bank: List<Exercise> = emptyList(),
): List<Binding> = buildList {
    routine.days.filter { it.kind == RoutineDayKind.LIFTING }.forEach { day ->
        val length = dayLength(day, routine.cardio, RepRange.DEFAULT)
        // Only past a margin (#136): an estimate of 52 minutes against 50 is the estimate being
        // an estimate, and a flag on it would be noise.
        if (length > ceiling.multipliedBy(11).dividedBy(10)) {
            add(
                Binding.SessionTooLong(
                    day = day.name,
                    estimated = length,
                    ceiling = ceiling,
                    relax = listOf(
                        "Train more often, so each session carries less",
                        "Aim for less volume a week",
                        "Take shorter rests on the lighter exercises",
                    ),
                ),
            )
        }
    }

    val trains = routine.whatItTrains().associateBy { it.muscle }

    // Asked before anything else is said about a muscle, because it is the most useful thing to
    // say about one: see below for why it reads what the routine meant to train.
    val askedAbout = if (bank.isEmpty()) {
        emptyList()
    } else {
        routine.musclesItMeansToTrain().mapNotNull { leastBadFor(it, bank, routine.equipment) }
    }

    // The main muscles nothing in the cycle is for. Checked only where the routine trains
    // something, because an empty routine already says it is empty and twelve findings saying so
    // one muscle at a time would bury that.
    //
    // **Direct work, as the generator counts coverage.** A muscle that only ever turns up as
    // secondary is not trained by anything in the cycle — but it is not untouched either, so the
    // finding carries what it does get and the screen says which of the two is true.
    //
    // Not said of a muscle the lifter's exclusions emptied: that muscle is already being asked
    // about, and the question of which banned exercise is least bad is the same fact put to them
    // as something they can answer.
    val notTrained = if (trains.isEmpty()) {
        emptyList()
    } else {
        MAIN_MUSCLES.filter { muscle ->
            (trains[muscle]?.sessions ?: 0) == 0 && askedAbout.none { it.muscle == muscle }
        }
    }
    notTrained.forEach { muscle ->
        add(
            Binding.MuscleNotTrained(
                muscle = muscle,
                weeklySets = trains[muscle]?.weeklySets ?: 0.0,
                relax = listOf(
                    "Add an exercise for $muscle to one of your days",
                    "Leave it out, if this cycle is not meant to train it",
                ),
            ),
        )
    }

    val target = weeklyVolumeTargetFor(routine.goal)
    trains.values.forEach { load ->
        // A muscle already said to be untrained is not said again as *under* its band: that is
        // one fact, and the band is not what is wrong with it. Over its band is a second fact —
        // twenty sets a week out of other muscles' pressing is too much whatever it is for.
        val saidAlready = load.muscle in notTrained && load.weeklySets < target.start
        if (load.weeklySets !in target && !saidAlready) {
            add(
                Binding.VolumeOutsideBand(
                    muscle = load.muscle,
                    weeklySets = load.weeklySets,
                    target = target,
                    relax = if (load.weeklySets < target.start) {
                        listOf(
                            "Add an exercise for ${load.muscle}",
                            "Train ${load.muscle} on more days of the cycle",
                        )
                    } else {
                        listOf(
                            "Take a set off ${load.muscle}'s exercises",
                            "Train ${load.muscle} on fewer days of the cycle",
                        )
                    },
                ),
            )
        }
    }

    // And the question only the lifter can answer: where their own exclusions have left a muscle
    // with nothing to train it, which is least bad.
    //
    // **Asked about what the routine was meant to train, not what it ended up with.** A muscle
    // the exclusions emptied never reaches the days at all — the generator skips what it cannot
    // fill — so reading the days back would ask only about muscles that are fine, and the one
    // case this exists for would go silently unasked. Which is precisely the "silently skipping a
    // muscle group" the spec forbids.
    addAll(askedAbout)
}

/**
 * The muscles this routine set out to train.
 *
 * The **Split shape** it was laid out from, where it has one, because that is what was *asked
 * for* — and a muscle the lifter's exclusions emptied is missing from the days precisely because
 * nothing could fill it. A routine built by hand or rebuilt from the log has no shape, and what
 * it holds is the only statement of intent there is.
 */
private fun Routine.musclesItMeansToTrain(): List<String> = when (val laidOutFrom = shape) {
    null -> days.filter { it.kind == RoutineDayKind.LIFTING }
        .flatMap { day -> day.exercises.flatMap { it.exercise.primaryMuscles } }
        .distinct()

    else -> laidOutFrom.days.flatMap { it.muscles }.distinct()
}

/**
 * The banned exercises to ask about, where a muscle has nothing else left.
 *
 * Null where there is nothing to ask: either something is available, or the gym has no exercise
 * for this muscle at all — which is a different problem and not one a lifter can solve by
 * reconsidering a **Dislike**.
 */
fun leastBadFor(
    muscle: String,
    bank: List<Exercise>,
    equipment: Set<String>,
): Binding.OnlyDislikedLeft? {
    val available = bank.worthOffering(equipment)
    if (available.any { muscle in it.primaryMuscles }) return null

    val banned = bank.worthOffering(equipment, includeDisliked = true)
        .filter { muscle in it.primaryMuscles && it.isDisliked }
    if (banned.isEmpty()) return null

    return Binding.OnlyDislikedLeft(
        muscle = muscle,
        candidates = banned,
        relax = listOf(
            "Allow one of these back for $muscle",
            "Leave $muscle out of this routine",
        ),
    )
}
