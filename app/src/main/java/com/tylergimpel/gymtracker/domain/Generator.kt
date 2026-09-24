package com.tylergimpel.gymtracker.domain

/** One position of a shape, with what the generator put in it and how much of each. */
data class FilledDay(
    val shape: SplitDay,
    val exercises: List<PlannedExercise>,
)

/**
 * Fills a **Split shape**'s days from the bank, against the lifter's gym and their exclusions.
 *
 * The half of generation that cannot be templated. Day structures can be written down because the
 * divisibility rule collapses the space; which exercises go in them cannot, because equipment
 * varies at every gym and a template would be wrong at most of them.
 *
 * **Coverage is the whole rule, and it is coverage by direct work.** A day names muscles and
 * comes back with an exercise that works each of them *primarily* — checked by muscle rather than
 * by movement pattern, because the exercise model carries no movement pattern and muscle is the
 * one field at full fill.
 *
 * Secondary work is emphatically not coverage. Letting it count collapses an upper day into
 * three exercises — a press, a row and a pulldown "cover" shoulders, biceps and triceps
 * between them — and leaves three of the muscles the day exists for with half-credit work and
 * no exercise of their own. What keeps a day from being as long as the muscles it names is the
 * **shape** naming what the day is *for*, not the generator counting incidental work as done.
 *
 * **How much of each, and in what order**, comes from the set budget: a muscle's weekly target
 * divided by how often this shape trains it, less what the rest of the cycle already gives it as
 * secondary work (see [settled]), spread over as many movements as what is left should be spread
 * over, and ordered compound-first where the goal is one the order matters to.
 *
 * **Equipment filters the pool and limits nothing else.** Machines, free weights and bands are
 * equivalent for hypertrophy, so a sparse gym gets a routine that satisfies the same coverage
 * checks rather than a worse one. Strength transfer is modality-specific, and that is the goal's
 * problem rather than the pool's.
 *
 * **Deterministic.** Candidates are totally ordered, so the same answers produce the same routine
 * however the bank happened to arrive. Variety is something the lifter asks for (#75), never
 * something that happens to them.
 *
 * A muscle nothing in the gym can train is left untrained rather than papered over with something
 * that does not work it. What to do about that is a conversation for the validation (#76).
 */
fun fillDays(
    shape: SplitShape,
    bank: List<Exercise>,
    equipment: Set<String>,
    goal: RoutineGoal,
    /**
     * How often the lifter trains, which is the only thing that turns a cycle into a week.
     *
     * **No default**, because every plausible one is wrong somewhere: assuming the cycle comes
     * round weekly asks a once-a-week full-body day for a whole week's volume in one session, and
     * the budget dutifully produces a twenty-exercise day. The lifter has already answered this
     * question at setup; nothing here should be guessing at it.
     */
    cadence: Int,
    /**
     * Exercises the lifter marked to keep, which regeneration treats as fixed.
     *
     * Keyed by the day they were pinned in, because a pin is about where an exercise sits in the
     * shape of the routine and not about the exercise itself (#75).
     */
    pinned: Map<String, List<PlannedExercise>> = emptyMap(),
    /**
     * Which of the equally good candidates to take, so that asking again gives something else.
     *
     * Zero is the first answer and the one a routine is generated with. **Nothing reseeds on its
     * own**: routines do not self-rotate exercises across cycles, so a different number here only
     * ever arrives because the lifter asked for variety (#75). Same seed, same routine — which is
     * what keeps "regenerate" reproducible rather than a slot machine.
     */
    seed: Int = 0,
): List<FilledDay> {
    val pool = bank.availableFor(equipment)

    val chosen = shape.days.map { day ->
        // Nothing here knows what belongs in a cardio position, and a resistance exercise in one
        // would put volume into a day that is meant to carry none.
        if (day.kind == RoutineDayKind.CARDIO) {
            DayPlan(mutableListOf(), emptyList())
        } else {
            pool.buildDay(day, shape, goal, cadence, pinned[day.name].orEmpty(), seed)
        }
    }

    return shape.days.zip(chosen.settled(shape, goal, cadence)) { day, plan ->
        FilledDay(day, plan.exercises.orderedBy(goal))
    }
}

/**
 * The movements a day chose for one muscle it names, and how many sets each has now.
 *
 * [candidates] is what was chosen against the muscle's whole target, which is the most it could
 * ever want; settling only ever takes fewer of them, never more.
 */
private data class MuscleWork(
    val muscle: String,
    val candidates: List<Exercise>,
    val pinned: Set<String>,
    /** Every muscle the day names, which is what decides whether a movement's coverage matters. */
    val named: Set<String>,
    val planned: List<PlannedExercise>,
)

/** A day's work, one entry per muscle it budgets, plus pins for muscles it does not name. */
private data class DayPlan(
    val work: MutableList<MuscleWork>,
    val unnamedPins: List<PlannedExercise>,
) {
    val exercises: List<PlannedExercise> get() = work.flatMap { it.planned } + unnamedPins
}

/**
 * The set counts, settled against what the whole cycle already gives each muscle.
 *
 * **Fractional on both sides of the sum.** The targets are stated in fractional sets, so a press
 * that works triceps at a half counts toward triceps' target (ADR 0005), and only what is left is
 * budgeted as triceps' own direct work. Budgeting the whole target as direct work on top of every
 * press and row overshot each muscle that sits second in a compound, and it was most of why an
 * ordinary upper day ran past two hours.
 *
 * **Across the cycle, and in no particular order.** The credit is everything anywhere in the
 * rotation, on any day and chosen before or after, because a lat pulldown's work does not care
 * that the rows were picked second, and triceps trained on one day still had every press on the
 * other. Spread over the days that train the muscle directly, as its own budget is. Counted by
 * [creditFor], the same rule the reports count by, so what the budget assumed and what the routine
 * is then said to train are one number.
 *
 * **Held under the per-session cap day by day**, not only on average: a day already carrying a lot
 * of a muscle's secondary work gets less of its direct work, so no one session goes past the cap
 * because the average session does not.
 *
 * Settled because the credits lean on each other — fewer triceps sets means less for chest from
 * the dips — so the counts are revised one muscle at a time, in the order the days name them,
 * until a round changes nothing. The lists are updated in place on purpose: each muscle is budgeted
 * against the latest answer for every other, which settles sooner than revising them all at once.
 * The order and the cap on rounds are fixed, so the same answers still give the same routine.
 *
 * **Credit changes how much and never whether.** A muscle a day names keeps a movement of its
 * own, because secondary work is not coverage; with nothing left to budget, that movement gets the
 * one set a prescription cannot go below. A muscle's own work on another day is never credited,
 * because its per-session budget already accounts for it.
 */
private fun List<DayPlan>.settled(
    shape: SplitShape,
    goal: RoutineGoal,
    cadence: Int,
): List<DayPlan> {
    val plans = map { it.copy(work = it.work.toMutableList()) }
    val lifting = shape.days.filter { it.kind == RoutineDayKind.LIFTING }

    repeat(MOST_SETTLING_ROUNDS) {
        var changed = false
        plans.forEach { today ->
            val work = today.work
            work.indices.forEach { index ->
                val muscle = work[index].muscle
                val fromOthers = { plan: DayPlan ->
                    plan.work.filter { it.muscle != muscle }.flatMap { it.planned }
                        .plus(plan.unnamedPins)
                        .sumOf { (it.sets ?: 0) * it.exercise.creditFor(muscle) }
                }
                val days = lifting.count { muscle in it.muscles }.coerceAtLeast(1)
                val onAverage = setsPerSessionFor(
                    muscle, shape, goal, cadence, credited = plans.sumOf(fromOthers) / days,
                )
                val budget = onAverage.coerceAtMost(
                    (PER_SESSION_CAP - fromOthers(today)).coerceAtLeast(0.0),
                )
                val next = work[index].budgetedAt(budget)
                if (next != work[index]) {
                    work[index] = next
                    changed = true
                }
            }
        }
        if (!changed) return plans
    }
    return plans
}

/**
 * This muscle's work at [budget] direct sets: as few of its movements as that many sets want.
 *
 * Pins stay whatever the budget, and so does any movement that is also the direct work for
 * another muscle the day names — trimming that would take away coverage the credit never gave.
 */
private fun MuscleWork.budgetedAt(budget: Double): MuscleWork {
    val essential = candidates.filter { candidate ->
        candidate.id in pinned ||
            candidate.primaryMuscles.any { it != muscle && it in named }
    }
    val optional = candidates.filterNot { it in essential }
        .take((exercisesForSets(budget) - essential.size).coerceAtLeast(0))
    // In the order they were chosen, which is the order the app would recommend them.
    val keep = candidates.filter { it in essential || it in optional }
    return copy(
        planned = keep.zip(setsAcross(keep.size, budget)) { exercise, count ->
            PlannedExercise(exercise, count, pinned = exercise.id in pinned)
        },
    )
}

/**
 * How many rounds settling gets before it stops where it is.
 *
 * Invented, and generous: each credit is at most half of work that is itself being reduced, so
 * the counts close in quickly. The cap exists so that whole-set rounding flickering between two
 * answers cannot keep going, and so what comes back is still a function of the answers alone.
 */
private const val MOST_SETTLING_ROUNDS = 8

/**
 * The same candidates, starting from a different one.
 *
 * A rotation rather than a shuffle, because the list is already in the order the app would
 * recommend: rotating keeps every candidate reachable and keeps the answer reproducible from the
 * seed alone, where a shuffle would need its own random source threaded through everything.
 */
private fun List<Exercise>.rotatedBy(seed: Int): List<Exercise> = when {
    isEmpty() || seed % size == 0 -> this
    else -> {
        val from = ((seed % size) + size) % size
        drop(from) + take(from)
    }
}

/** The day's exercises in training order, carrying their set counts with them. */
private fun List<PlannedExercise>.orderedBy(goal: RoutineGoal): List<PlannedExercise> {
    val order = map(PlannedExercise::exercise).orderedFor(goal).map(Exercise::id)
    return sortedBy { order.indexOf(it.exercise.id) }
}

/**
 * The exercises this gym can actually offer, in a stable order.
 *
 * Sorted here rather than at the point of choosing, so that the order the bank arrived in cannot
 * reach the result. Compounds first, because one of them covers several of a day's muscles and
 * picking them first is what keeps the day short; then by id, which is arbitrary and total, which
 * is all a tie-break has to be.
 */
private fun List<Exercise>.availableFor(equipment: Set<String>): List<Exercise> =
    // The same rule the picker offers by, so a lifter is never generated something the app would
    // not have suggested them. Nothing is ever *generated* from a banned exercise, which is why
    // the picker's exception for one typed by name has no equivalent here.
    worthOffering(equipment)
        .sortedWith(compareByDescending<Exercise> { it.isCompound }.thenBy { it.id })

/**
 * The day, chosen: an exercise for every muscle it names, as many as its whole target could want.
 *
 * Chosen against the whole target, before any credit, so that settling ([settled]) only ever
 * takes movements away. The counts here are a first answer and are revised there.
 *
 * **Direct work on both sides of the question.** A muscle is answered by exercises that train it
 * primarily, and a muscle those exercises already train primarily is not answered again — so
 * a day
 * naming chest and triceps gets a press and a pushdown, and one naming chest twice gets one press.
 * What it never gets is a pushdown skipped because the press happens to involve triceps.
 *
 * **A pin constrains the day; it does not satisfy it.** An exercise the lifter kept takes one of
 * the places its muscle's budget asked for, and the rest are still filled around it. Treating a
 * pin as an answer would collapse a muscle budgeted eleven sets across four movements down to the
 * one pinned movement — a silent under-prescription, and exactly the kind of invisible volume
 * error ADR 0005 exists to prevent.
 *
 * A muscle nothing in the pool trains directly is skipped rather than filled with something that
 * does not train it. The day comes back honestly short, and what to do about that is a
 * conversation for the validation (#76).
 */
private fun List<Exercise>.buildDay(
    day: SplitDay,
    shape: SplitShape,
    goal: RoutineGoal,
    cadence: Int,
    kept: List<PlannedExercise>,
    seed: Int,
): DayPlan {
    val chosen = mutableListOf<MuscleWork>()
    val answered = mutableSetOf<String>()
    val keptIds = kept.mapTo(mutableSetOf()) { it.exercise.id }
    val taken = keptIds.toMutableSet()

    day.muscles.forEach { muscle ->
        if (muscle in answered) return@forEach

        val sets = setsPerSessionFor(muscle, shape, goal, cadence)
        val wanted = exercisesForSets(sets)

        val pinnedHere = kept.filter { muscle in it.exercise.primaryMuscles }.map { it.exercise }
        val filling = filter { muscle in it.primaryMuscles && it.id !in taken }
            .rotatedBy(seed)
            .take((wanted - pinnedHere.size).coerceAtLeast(0))

        val forThisMuscle = pinnedHere + filling
        if (forThisMuscle.isEmpty()) return@forEach

        taken += forThisMuscle.map(Exercise::id)
        // Everything these train primarily now has direct work, so nothing else is chosen for it.
        answered += forThisMuscle.flatMap(Exercise::primaryMuscles)
        val pinnedIds = forThisMuscle.map(Exercise::id).filterTo(mutableSetOf()) { it in keptIds }
        chosen += MuscleWork(
            muscle = muscle,
            candidates = forThisMuscle,
            pinned = pinnedIds,
            named = day.muscles.toSet(),
            planned = emptyList(),
        ).budgetedAt(sets)
    }

    // A pin for something the day does not name is still the lifter's: they put it there, and a
    // regeneration that quietly dropped it would not be keeping what it promised to keep.
    val placed = chosen.flatMapTo(mutableSetOf()) { work -> work.candidates.map(Exercise::id) }
    return DayPlan(
        work = chosen,
        unnamedPins = kept.filterNot { it.exercise.id in placed }.map { it.copy(pinned = true) },
    )
}
