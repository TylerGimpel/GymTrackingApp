package com.tylergimpel.gymtracker.domain

import java.time.Duration

/**
 * Where a session goes the moment a set is down.
 *
 * **The ordering is the whole of this type**, and it is easy to get wrong: the prototype pinned it
 * because two of the three orderings you might reach for leave a lifter standing still in the
 * middle of the one thing that is supposed to keep moving.
 */
sealed interface AfterLogging {

    /**
     * Ask whether that was the session.
     *
     * A piece of cardio is usually the end of a trip to the gym rather than one set of several,
     * and a lifter who has just finished on a treadmill is standing next to the door.
     */
    data object PromptToFinish : AfterLogging

    /**
     * Move to another member of a **Superset**, resting only where the group has come round.
     *
     * Rest belongs after the group rather than inside it — the point of alternating is not
     * stopping — so [rest] is null for every jump but the one that closes a round. That jump is
     * the moment every member has been trained once, which is the "after the group" a superset's
     * rest is named for, and it is the group's own rest rather than any member's.
     */
    data class JumpTo(val exercise: Exercise, val rest: Duration? = null) : AfterLogging

    /** Stay where the lifter is, and start the rest the set carried. */
    data class StartResting(val length: Duration) : AfterLogging

    /** Nothing to do: no partner to jump to, and no rest prescribed for what was just done. */
    data object Nothing : AfterLogging
}

/**
 * What happens after [exercise] has had a set logged against it.
 *
 * Takes the session's [positions] as they stand, so the question "is there a partner waiting" is
 * answered by what the lifter actually grouped rather than by anything remembered separately.
 *
 * Rest comes last on purpose: it starts only once focus has gone somewhere, and never at all
 * between the members of a group. A rest that began before the jump would be a timer counting
 * down while the lifter is mid-superset, which is the opposite of what a superset is.
 */
fun afterLogging(
    exercise: Exercise,
    positions: List<SessionPosition>,
    rest: Duration?,
): AfterLogging = when {
    exercise.isCardio -> AfterLogging.PromptToFinish
    else -> aroundTheGroupFrom(exercise, positions)
        ?: rest?.let(AfterLogging::StartResting)
        ?: AfterLogging.Nothing
}

/**
 * Where a **Superset** goes next from [exercise], or null where it is not in one that jumps.
 *
 * Null when the group says not to jump, which is a real answer rather than an oversight: two
 * machines at opposite ends of a gym are a group a lifter alternates on foot, and being moved
 * automatically between them is worse than being asked.
 */
private fun aroundTheGroupFrom(
    exercise: Exercise,
    positions: List<SessionPosition>,
): AfterLogging? {
    val group = positions.filterIsInstance<SessionPosition.Grouped>()
        .firstOrNull { position -> position.exercises.any { it.exercise.id == exercise.id } }
        ?: return null
    if (!group.superset.autoJump) return null

    val members = group.exercises.map(SessionExercise::exercise)
    val here = members.indexOfFirst { it.id == exercise.id }
    val groupRest = group.superset.restSeconds?.let { Duration.ofSeconds(it.toLong()) }

    // Round the group rather than off the end of it: a superset is alternated until its sets are
    // done, so the member after the last one is the first one again — and coming back round is
    // the one moment every member has been trained once, which is what the group rests for.
    return when (val next = members.getOrNull(here + 1)) {
        null -> when {
            // A group of one is what ungrouping down to a single member leaves. There is nowhere
            // to jump and every set closes a round, so the rest it carries is simply taken.
            members.size <= 1 -> groupRest?.let(AfterLogging::StartResting) ?: AfterLogging.Nothing
            else -> AfterLogging.JumpTo(members.first(), groupRest)
        }

        else -> AfterLogging.JumpTo(next)
    }
}
