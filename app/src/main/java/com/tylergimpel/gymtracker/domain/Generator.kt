package com.tylergimpel.gymtracker.domain

/** One position of a shape, with what the generator put in it. */
data class FilledDay(
    val shape: SplitDay,
    val exercises: List<Exercise>,
)

/**
 * Fills a **Split shape**'s days from the bank, against the lifter's gym and their exclusions.
 *
 * The half of generation that cannot be templated. Day structures can be written down because the
 * divisibility rule collapses the space; which exercises go in them cannot, because equipment
 * varies at every gym and a template would be wrong at most of them.
 *
 * **Coverage is the whole rule.** A day names muscles and comes back training all of them —
 * checked by muscle rather than by movement pattern, because the exercise model carries no
 * movement pattern and muscle is the one field at full fill. A muscle is covered once something
 * chosen works it at all, directly or as secondary work, which is what keeps a full-body day a
 * handful of compounds instead of a list as long as the muscles it names.
 *
 * **How many sets each of these gets, and what order they are trained in, is not decided here**
 * (#74). This answers what, and only what.
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
): List<FilledDay> {
    val pool = bank.availableFor(equipment)

    return shape.days.map { day ->
        // Nothing here knows what belongs in a cardio position, and a resistance exercise in one
        // would put volume into a day that is meant to carry none.
        if (day.kind == RoutineDayKind.CARDIO) return@map FilledDay(day, emptyList())

        FilledDay(day, pool.coveringMusclesOf(day))
    }
}

/**
 * The exercises this gym can actually offer, in a stable order.
 *
 * Sorted here rather than at the point of choosing, so that the order the bank arrived in cannot
 * reach the result. Compounds first, because one of them covers several of a day's muscles and
 * picking them first is what keeps the day short; then by id, which is arbitrary and total, which
 * is all a tie-break has to be.
 */
private fun List<Exercise>.availableFor(equipment: Set<String>): List<Exercise> = asSequence()
    // A dropped exercise exists for the history pointing at it, and is not something to programme.
    .filterNot { it.isTombstoned }
    // A hard constraint. Including one because what was left scored worse would break the only
    // promise a **Dislike** makes.
    .filterNot { it.isDisliked }
    // A **User-authored exercise** passes this exactly as a shipped row does: it carries the same
    // equipment value, and a gym's own machines should be programmable like anything else.
    .filter { equipment.isEmpty() || it.equipment == null || it.equipment in equipment }
    .sortedWith(compareByDescending<Exercise> { it.isCompound }.thenBy { it.id })
    .toList()

/**
 * Enough of this pool to work every muscle [day] asks for.
 *
 * Greedy and in the order the shape lists its muscles, which is the order they were authored in
 * and roughly the order they matter to the day. A muscle already worked by something chosen is
 * not chosen for again — that is what turns twelve muscles into five or six exercises, and it is
 * why the first pick for a push day tends to cover the other two.
 *
 * Only an exercise that works the muscle **primarily** is picked for it. Secondary work counts
 * toward a muscle already being covered, but nothing is ever chosen *because* it works something
 * secondarily: a chest day covered by three presses is not a triceps day.
 */
private fun List<Exercise>.coveringMusclesOf(day: SplitDay): List<Exercise> {
    val chosen = mutableListOf<Exercise>()
    val worked = mutableSetOf<String>()

    day.muscles.forEach { muscle ->
        if (muscle in worked) return@forEach

        val pick = firstOrNull { muscle in it.primaryMuscles } ?: return@forEach
        chosen += pick
        worked += pick.primaryMuscles
        worked += pick.secondaryMuscles
    }

    return chosen
}
