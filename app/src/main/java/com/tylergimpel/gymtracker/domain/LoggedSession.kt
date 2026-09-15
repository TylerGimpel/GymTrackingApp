package com.tylergimpel.gymtracker.domain

/**
 * A session, everything logged in it, and what it was supposed to contain.
 *
 * The three have to arrive together: what happened is only half the story of a session, and the
 * half that makes a skipped exercise visible is the plan it is read against.
 */
data class LoggedSession(
    val session: Session,
    val exercises: List<SessionExercise>,
    /** What the session was for. Empty for one trained with no routine, which is ordinary. */
    val plan: SessionPlan = SessionPlan(),
) {
    /** The planned exercises that went untrained — what "I did four of six" is counted from. */
    val unperformed: List<Exercise> get() = plan.unperformed(exercises)
}
