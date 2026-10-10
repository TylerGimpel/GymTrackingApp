package com.tylergimpel.traintracks.domain

/**
 * An exercise the lifter's sessions keep asking for and their training keeps not containing.
 *
 * Counted from **Unperformed** exercises, which is deliberately not a **Skip**: a skip is a
 * routine day dealt with on purpose, and this is what quietly did not happen.
 */
data class Avoided(
    val exercise: Exercise,
    /** How many of the recent sessions that asked for it went without it. */
    val unperformed: Int,
    /** How many asked. The denominator matters: three of four is not three of twelve. */
    val asked: Int,
)

/**
 * The exercises worth asking the lifter about, from the sessions they have most recently trained.
 *
 * **The app notices; the lifter decides.** Nothing here removes, swaps or bans anything, and that
 * restraint is the whole point of the feature: people skip squats because squats are hard, not
 * because they dislike them, and an app inferring dislike from avoidance would quietly strip the
 * most valuable work out of a programme.
 *
 * Counted per exercise over the last [window] sessions **that asked for it**, so an exercise in
 * every session and one in every third are held to the same standard. Sessions that never planned
 * it are not evidence about it either way.
 */
fun avoidedExercises(
    recent: List<LoggedSession>,
    threshold: Int = SESSIONS_BEFORE_ASKING,
    window: Int = SESSIONS_LOOKED_AT,
): List<Avoided> {
    val newestFirst = recent.sortedByDescending { it.session.date }

    return newestFirst.flatMap { it.plan.exercises }
        .map(PlannedExercise::exercise)
        .distinctBy(Exercise::id)
        .mapNotNull { exercise ->
            val asking = newestFirst
                .filter { session ->
                    session.plan.exercises.any { it.exercise.id == exercise.id }
                }
                .take(window)
            val missed = asking.count { session ->
                session.plan.unperformed(session.exercises).any { it.id == exercise.id }
            }

            Avoided(exercise, missed, asking.size).takeIf { missed >= threshold }
        }
}

/**
 * How many sessions have to go without an exercise before the app says anything.
 *
 * **An invented, tuneable judgement call**, like every other threshold in this engine. Three is
 * enough to be a pattern rather than a bad week, and asking sooner would turn a helpful question
 * into nagging — which is how a prompt gets dismissed without being read.
 */
const val SESSIONS_BEFORE_ASKING = 3

/**
 * How far back the question looks.
 *
 * Recent enough to be about now. An exercise somebody avoided all spring and has trained happily
 * since is not a question worth asking, and a window is what keeps an old pattern from following
 * a lifter around.
 */
const val SESSIONS_LOOKED_AT = 5
