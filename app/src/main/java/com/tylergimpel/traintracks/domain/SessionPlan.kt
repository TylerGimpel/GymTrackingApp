package com.tylergimpel.traintracks.domain

/**
 * The list of exercises a session was supposed to contain, copied from its routine day when the
 * session began.
 *
 * A snapshot, with nothing pointing back at the routine it came from — the same move the model
 * already makes one level up, where a session copies its routine day's *name*. Revising or deleting
 * a routine therefore cannot reach a plan, in the same way it cannot reach a logged set.
 *
 * What it buys is a distinction the log cannot otherwise make: an exercise nobody trained and an
 * exercise nobody prescribed look identical in a list of what happened. With a plan, "I did four of
 * six" is answerable, and a prescribed exercise that went untrained is visible rather than absent.
 *
 * Empty for a session trained with no routine at all, which is an ordinary session rather than an
 * incomplete one.
 */
data class SessionPlan(
    /**
     * What the session was for, in the order the routine day held them, and how much of each.
     *
     * The set counts are copied with everything else, so a session trained in March still reads
     * what it was asked for however often the routine has changed since.
     */
    val exercises: List<PlannedExercise> = emptyList(),
) {
    val isEmpty: Boolean get() = exercises.isEmpty()

    /** How many of the planned exercises [performed] accounts for, out of how many were planned. */
    fun progress(performed: List<SessionExercise>): Pair<Int, Int> =
        performed(performed).size to exercises.size

    /**
     * The planned exercises [performed] does not account for, in the order they were planned.
     *
     * Matched on the exercise rather than on anything about the session, so a swap reads as the
     * planned exercise going untrained — which is exactly what happened.
     */
    fun unperformed(performed: List<SessionExercise>): List<Exercise> =
        exercises.map(PlannedExercise::exercise).filterNot { it.id in performed.exerciseIds() }

    /**
     * The planned exercises [performed] does account for.
     *
     * Reports on the plan rather than on the session: an exercise trained but never planned is not
     * here, because it answers "how much of what this was for got done".
     */
    fun performed(performed: List<SessionExercise>): List<Exercise> =
        exercises.map(PlannedExercise::exercise).filter { it.id in performed.exerciseIds() }
}

/**
 * How often each exercise was planned and then not trained, across [sessions], keyed by exercise id.
 *
 * The shape the revealed-preference signals for swap and dislike read — "you have not trained rear
 * delt flyes in six of the last eight sessions that asked for them". Counting only what was planned
 * is the whole point: an exercise that was never asked for cannot have been avoided.
 *
 * Deliberately not called a skip. A **Skip** in this project is a routine day dealt with without a
 * session, and it is a thing a lifter does on purpose; this is a count of what quietly did not
 * happen.
 */
fun unperformedExerciseCounts(sessions: List<LoggedSession>): Map<String, Int> =
    sessions
        .flatMap { it.plan.unperformed(it.exercises) }
        .groupingBy(Exercise::id)
        .eachCount()

private fun List<SessionExercise>.exerciseIds(): Set<String> =
    mapTo(mutableSetOf()) { it.exercise.id }
