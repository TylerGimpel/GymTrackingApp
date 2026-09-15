package com.tylergimpel.gymtracker.domain

/**
 * Two or more exercises within a session performed alternately with no rest between them.
 *
 * Groups **across** exercises, where a drop set groups **within** one set. Deliberately two
 * structures rather than one generic grouping: collapsing them is what makes counting ambiguous
 * again, and a set's identity has to stay positional within its exercise for volume to mean
 * anything (ADR 0003). Grouping says something about rest and nothing about work — the sets a
 * lifter did are the same sets whether or not they were alternated.
 */
data class Superset(
    val id: Long,
    /**
     * The order this group was formed in, among the session's groups, from 1.
     *
     * Not what lays a session out: a group is shown where its first member was performed, so that
     * the exercises around it keep their places. Kept because the spec says a group carries an
     * order and forming order is the one order nothing else records — but nothing reads it yet,
     * and it is worth deleting if nothing ever does.
     */
    val orderIndex: Int,
    /**
     * Whether the logging screen moves to the next member on its own after a set is logged.
     *
     * The point of a superset is not stopping, so being asked to choose the next exercise defeats
     * it. Recorded here and acted on by the live logging screen, which arrives with its own spec.
     */
    val autoJump: Boolean,
    /**
     * The rest taken after the whole group, in seconds, or null to use whatever the app would
     * otherwise rest for.
     *
     * On the group because that is where rest belongs: a superset's members have none between them
     * by definition, so there is deliberately nowhere to put a per-member value that could disagree.
     */
    val restSeconds: Int?,
)

/**
 * One position in a session as it reads back: an exercise on its own, or a group and its members.
 *
 * A sealed pair rather than a nullable group on every exercise, so that a caller laying a session
 * out cannot forget that members belong together and render one of them alone.
 *
 * Deliberately not called an entry: an entry here is a stored set row (`set_entry`), and a set
 * logged as a single entry is already a phrase this project uses about something else.
 */
sealed interface SessionPosition {

    /** The exercises at this position, in the order they were performed. Never empty. */
    val exercises: List<SessionExercise>

    data class Alone(val exercise: SessionExercise) : SessionPosition {
        override val exercises: List<SessionExercise> get() = listOf(exercise)
    }

    data class Grouped(
        val superset: Superset,
        override val exercises: List<SessionExercise>,
    ) : SessionPosition
}

/**
 * The session's exercises read in the order they were performed, with superset members gathered.
 *
 * A group takes the place of its **first** member, and every member is drawn there — so an
 * exercise performed between two members follows the group rather than interrupting it. Grouping
 * only ever joins neighbours, so that case does not arise from the app; this says what would
 * happen rather than claiming it cannot.
 *
 * An exercise whose group is not in [supersets] reads on its own. The foreign key already releases
 * members when a group is deleted, so this is belt and braces — but showing the training that
 * happened beats dropping it from the session over an id.
 */
fun List<SessionExercise>.asPositions(supersets: List<Superset>): List<SessionPosition> {
    val byId = supersets.associateBy(Superset::id)
    val drawn = mutableSetOf<Long>()
    val positions = mutableListOf<SessionPosition>()

    forEach { performed ->
        val superset = byId[performed.supersetId]
        if (superset == null) {
            positions += SessionPosition.Alone(performed)
        } else if (drawn.add(superset.id)) {
            positions += SessionPosition.Grouped(
                superset = superset,
                exercises = filter { it.supersetId == superset.id },
            )
        }
    }

    return positions
}
