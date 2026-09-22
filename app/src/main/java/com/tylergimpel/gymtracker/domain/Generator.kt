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
    weeklyFrequency: Int,
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

        val kept = pinned[day.name].orEmpty()
        val answered = kept.flatMap { it.exercise.primaryMuscles }.toSet()

        val generated = pool.coveringMusclesOf(day, already = answered)
            .flatMap { (muscle, chosen) ->
                val sets = setsPerSessionFor(muscle, shape, goal, weeklyFrequency)
                val across = chosen.rotatedBy(seed).take(exercisesForSets(sets))
                across.zip(setsAcross(across.size, sets)) { exercise, count ->
                    PlannedExercise(exercise, count)
                }
            }

        // Pinned first, then what was generated around them, then ordered as one day: a lifter
        // who kept their squat still wants it before their curls.
        val all = (kept + generated).distinctBy { it.exercise.id }
        FilledDay(day, all.orderedBy(goal))
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
 * An exercise for every muscle [day] asks for, in the order the shape names them.
 *
 * **Primary work only, on both sides of the question.** A muscle is answered by an exercise that
 * trains it directly, and a muscle already answered that way is not answered twice — so a day
 * naming chest and triceps gets a press and a pushdown, and a day naming chest twice gets one
 * press. What it never gets is a pushdown skipped because the press happens to involve triceps.
 *
 * A muscle nothing in the pool trains directly is skipped rather than filled with something that
 * does not train it. The day comes back honestly short, and what to do about that is a
 * conversation for the validation (#76).
 */
private fun List<Exercise>.coveringMusclesOf(
    day: SplitDay,
    already: Set<String> = emptySet(),
): List<Pair<String, List<Exercise>>> {
    val chosen = mutableListOf<Pair<String, List<Exercise>>>()
    val answered = already.toMutableSet()
    val taken = mutableSetOf<String>()

    day.muscles.forEach { muscle ->
        if (muscle in answered) return@forEach

        // As many as the budget could possibly want, so the caller can take what it needs without
        // asking again. Fewer where the gym has fewer, which is a short day rather than an error.
        val picks = filter { muscle in it.primaryMuscles && it.id !in taken }
            .take(MOST_EXERCISES_PER_MUSCLE)
        if (picks.isEmpty()) return@forEach

        chosen += muscle to picks
        taken += picks.map(Exercise::id)
        answered += picks.first().primaryMuscles
    }

    return chosen
}
