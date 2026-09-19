package com.tylergimpel.gymtracker.data

import androidx.room.withTransaction
import com.tylergimpel.gymtracker.domain.Exercise
import com.tylergimpel.gymtracker.domain.ExerciseProgress
import com.tylergimpel.gymtracker.domain.Increment
import com.tylergimpel.gymtracker.domain.Prescription
import com.tylergimpel.gymtracker.domain.RepRange
import com.tylergimpel.gymtracker.domain.TrainingHistory

/**
 * Tells the lifter what to lift, from what they have lifted before.
 *
 * The rules are the domain's — Epley, smoothed, read back down a **Rep range** and rounded to
 * something loadable (ADR 0004) — and this is the part that reads the log and keeps what the app
 * works out. What it works out lives in `exercise_progress`, apart from the shipped bank, so an
 * app release that improves the exercise rows cannot throw it away (ADR 0009).
 *
 * **Recomputed from the log rather than accumulated.** Every recommendation folds the exercise's
 * sessions again and writes the result, which costs a query and buys two things: a set corrected
 * or deleted heals the estimate instead of poisoning it for good, and an installed copy carrying
 * years of history needs no backfill to arrive at the same numbers as a fresh one.
 */
class RecommendationRepository(
    private val database: GymDatabase,
    private val exercises: ExerciseDao,
    private val sessions: SessionDao,
) {

    /**
     * What to tell the lifter to do for the next set of [exercise], or null where the app has
     * nothing to say.
     *
     * Null in three cases, and they are all the same case: there is nothing honest to say. The
     * lifter — or the bank — decided this lift is recorded rather than coached; the exercise has
     * never been trained, so a first session is a ramp rather than a prediction (#49); or nothing
     * logged against it says anything about a load, which is what a plank and a treadmill are.
     */
    suspend fun prescribeNextSet(exercise: Exercise): Prescription? {
        if (!exercise.progressionModel.isPrescribed) return null

        return database.withTransaction {
            val history = historyOf(exercise.id)
            val progress = history.progressIn(repRangeFor(exercise.id), ROUNDING)
            remember(exercise.id, progress)
            progress?.let(history::nextSetOf)
        }
    }

    /**
     * Every session this exercise has been trained in, apart from the one being logged into.
     *
     * Today is not history yet: a set logged ten minutes ago must not move the weight prescribed
     * for the next set of the same session, because advancing takes two consecutive sessions
     * rather than one good one.
     */
    private suspend fun historyOf(exerciseId: String): TrainingHistory =
        sessions.trainedSetsFor(exerciseId, sessions.activeSession()?.id ?: SessionDao.NO_SESSION)
            .toTrainingHistory()

    /**
     * The band this exercise is worked in: the one it already carries, or the default.
     *
     * Read back rather than recomputed, because unlike the anchor it is not derived from anything —
     * it is a decision, and until the generator seeds it from the routine's goal and rep preference
     * (#26) the default is the whole of that decision.
     */
    private suspend fun repRangeFor(exerciseId: String): RepRange =
        exercises.progress(exerciseId)?.toDomain()?.repRange ?: RepRange.DEFAULT

    /**
     * Keeps what the app now makes of this exercise, or forgets it where there is nothing to make.
     *
     * Forgetting matters: an exercise whose only logged set has been deleted has no estimate, and a
     * row left behind would go on prescribing from training that is no longer there.
     */
    private suspend fun remember(exerciseId: String, progress: ExerciseProgress?) {
        if (progress == null) {
            exercises.forgetProgress(exerciseId)
        } else {
            exercises.upsertProgress(progress.toEntity(exerciseId))
        }
    }

    private companion object {
        /**
         * What a recommendation is rounded to, until the equipment gets a say.
         *
         * Named here rather than passed in, so that the three tiers the spec settles — the plate
         * inventory, the dumbbell rack's own step and the stack positions a machine has been seen
         * at — arrive at one call site rather than being threaded through from the screen.
         */
        val ROUNDING = Increment.DEFAULT
    }
}
