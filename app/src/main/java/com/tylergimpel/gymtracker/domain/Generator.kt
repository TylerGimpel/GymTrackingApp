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
 * divided by how often this shape trains it, spread over as many movements as that many sets
 * should be spread over, and ordered compound-first where the goal is one the order matters to.
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

    return shape.days.map { day ->
        // Nothing here knows what belongs in a cardio position, and a resistance exercise in one
        // would put volume into a day that is meant to carry none.
        if (day.kind == RoutineDayKind.CARDIO) return@map FilledDay(day, emptyList())

        FilledDay(
            day,
            pool.buildDay(day, shape, goal, cadence, pinned[day.name].orEmpty(), seed)
                .orderedBy(goal),
        )
    }
}

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
 * The day, built: an exercise for every muscle it names, as many as the budget wants of each.
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
): List<PlannedExercise> {
    val chosen = mutableListOf<PlannedExercise>()
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
        chosen += forThisMuscle.zip(setsAcross(forThisMuscle.size, sets)) { exercise, count ->
            PlannedExercise(exercise, count, pinned = exercise.id in keptIds)
        }
    }

    // A pin for something the day does not name is still the lifter's: they put it there, and a
    // regeneration that quietly dropped it would not be keeping what it promised to keep.
    val placed = chosen.mapTo(mutableSetOf()) { it.exercise.id }
    return chosen + kept.filterNot { it.exercise.id in placed }.map { it.copy(pinned = true) }
}
