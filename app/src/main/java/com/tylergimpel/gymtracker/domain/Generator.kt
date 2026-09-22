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
private fun List<Exercise>.coveringMusclesOf(day: SplitDay): List<Exercise> {
    val chosen = mutableListOf<Exercise>()
    val answered = mutableSetOf<String>()

    day.muscles.forEach { muscle ->
        if (muscle in answered) return@forEach

        val pick = firstOrNull { muscle in it.primaryMuscles } ?: return@forEach
        chosen += pick
        answered += pick.primaryMuscles
    }

    return chosen
}
