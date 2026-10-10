package com.tylergimpel.traintracks.domain

/**
 * A session, everything logged in it, what it was supposed to contain, and how it was grouped.
 *
 * They have to arrive together: what happened is only half the story of a session, and the half
 * that makes an unperformed exercise visible is the plan it is read against.
 */
data class LoggedSession(
    val session: Session,
    val exercises: List<SessionExercise>,
    /** What the session was for. Empty for one trained with no routine, which is ordinary. */
    val plan: SessionPlan = SessionPlan(),
    /** The groups performed alternately within it, if any were formed. */
    val supersets: List<Superset> = emptyList(),
) {
    /** The planned exercises that went untrained — what "I did four of six" is counted from. */
    val unperformed: List<Exercise> get() = plan.unperformed(exercises)

    /** The sets logged against [exercise] in this session, in the order they were performed. */
    fun setsOf(exercise: Exercise): List<PerformedSet> =
        exercises.filter { it.exercise.id == exercise.id }.flatMap(SessionExercise::sets)

    /** The session laid out as it was performed, with superset members gathered. */
    val positions: List<SessionPosition> get() = exercises.asPositions(supersets)
}
