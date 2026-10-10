package com.tylergimpel.traintracks.domain

/**
 * Something the lifter changed about tonight's order of exercises (#142): a swap, or an exercise
 * added after another.
 *
 * Tonight's only, like a **Swap**: the session's **Plan** still records what the evening was for,
 * so a planned exercise that gave way reads as **Unperformed** rather than absent.
 */
data class Addition(
    val exercise: Exercise,
    /** The exercise it goes after, or null for the end. */
    val after: String? = null,
    /** The planned exercise it takes the place of, where it was swapped in for one with nothing logged. */
    val replaces: String? = null,
)

/** One exercise in tonight's workout, as the drawer lists it and logging moves through it. */
data class LineupItem(
    val exercise: Exercise,
    /** Sets that count as work, done so far. */
    val done: Int,
    /** Working sets asked for, or null where nothing asked. */
    val planned: Int?,
    val skipped: Boolean = false,
) {
    /** Whether there is asked-for work left in it. An exercise nothing asked for is open-ended. */
    val hasWorkLeft: Boolean get() = !skipped && if (planned != null) done < planned else done == 0
}

/**
 * Tonight's workout in order: the plan, with swaps put in place, additions after what they follow,
 * and anything else trained where it was trained.
 */
fun lineupOf(
    plan: SessionPlan,
    performed: List<SessionExercise>,
    additions: List<Addition> = emptyList(),
    skipped: Set<String> = emptySet(),
): List<LineupItem> {
    val done = performed.groupBy { it.exercise.id }
        .mapValues { (_, rows) -> rows.sumOf { row -> row.sets.count { it.type.countsTowardVolume } } }
    val planned = plan.exercises.associate { it.exercise.id to it.sets }

    fun item(exercise: Exercise, asked: Int?) = LineupItem(
        exercise = exercise,
        done = done[exercise.id] ?: 0,
        planned = asked,
        skipped = exercise.id in skipped,
    )

    val order = mutableListOf<LineupItem>()
    plan.exercises.forEach { asked ->
        val swap = additions.lastOrNull { it.replaces == asked.exercise.id }
        // Swapped for one with nothing logged: the new one takes its place and its set count.
        val started = performed.any { it.exercise.id == asked.exercise.id && it.sets.isNotEmpty() }
        order += if (swap != null && !started) {
            item(swap.exercise, asked.sets)
        } else {
            item(asked.exercise, asked.sets)
        }
    }
    additions.filter { it.replaces == null }.forEach { addition ->
        if (order.any { it.exercise.id == addition.exercise.id }) return@forEach
        val at = order.indexOfFirst { it.exercise.id == addition.after }
        val asked = planned[addition.exercise.id]
        if (at == -1) order += item(addition.exercise, asked) else order.add(at + 1, item(addition.exercise, asked))
    }
    performed.sortedBy(SessionExercise::orderIndex).forEach { row ->
        if (order.none { it.exercise.id == row.exercise.id }) order += item(row.exercise, planned[row.exercise.id])
    }
    return order
}

/**
 * Where logging goes once [current] has had all it asked for: the next exercise with work left,
 * coming round from the start, or null when the whole workout is done.
 */
fun List<LineupItem>.nextAfter(current: String): Exercise? {
    val here = indexOfFirst { it.exercise.id == current }
    val rest = if (here == -1) this else drop(here + 1) + take(here)
    return rest.firstOrNull { it.hasWorkLeft }?.exercise
}

/** Whether [exercise] has had every working set it asked for. Never for one nothing asked of. */
fun List<LineupItem>.isComplete(exercise: String): Boolean =
    firstOrNull { it.exercise.id == exercise }?.let { it.planned != null && it.done >= it.planned } == true
