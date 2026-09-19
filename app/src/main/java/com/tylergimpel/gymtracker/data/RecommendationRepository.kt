package com.tylergimpel.gymtracker.data

import androidx.room.withTransaction
import com.tylergimpel.gymtracker.domain.CalibrationRamp
import com.tylergimpel.gymtracker.domain.Exercise
import com.tylergimpel.gymtracker.domain.ExerciseProgress
import com.tylergimpel.gymtracker.domain.Increment
import com.tylergimpel.gymtracker.domain.Recommendation
import com.tylergimpel.gymtracker.domain.RepRange
import com.tylergimpel.gymtracker.domain.TrainingHistory
import com.tylergimpel.gymtracker.domain.recommendNextSet

/**
 * Tells the lifter what to lift: from what they have lifted before, or from a ramp where there is
 * no before.
 *
 * The rules are the domain's — Epley, smoothed, read back down a **Rep range** and rounded to
 * something loadable (ADR 0004), or a calibration ramp where nothing supports an estimate — and
 * this is the part that reads the log and keeps what the app works out. What it works out lives in
 * `exercise_progress`, apart from the shipped bank, so an app release that improves the exercise
 * rows cannot throw it away (ADR 0009).
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
     * What to tell the lifter to do for the next set of [exercise], or null where there is nothing
     * honest to say.
     *
     * Two modes, and which one this is turns on whether the exercise's own logged sets support an
     * estimate. Null where neither has anything: the lifter — or the bank — decided this lift is
     * recorded rather than coached, or it is a hold or a run, or today's ramp has run its course.
     */
    suspend fun nextSet(exercise: Exercise): Recommendation? {
        if (!exercise.progressionModel.isPrescribed) return null

        return database.withTransaction {
            val open = sessions.activeSession()?.id
            val history = historyOf(exercise.id, open)
            val progress = history.progressIn(repRangeFor(exercise.id), ROUNDING)
            remember(exercise.id, progress)
            recommendNextSet(
                history = history,
                progress = progress,
                calibration = CalibrationRamp.of(exercise, ROUNDING),
                setsToday = open?.let { sessions.setsLoggedInSession(exercise.id, it) } ?: 0,
            )
        }
    }

    /**
     * Every session this exercise has been trained in, apart from [open], the one being logged into.
     *
     * Today is not history yet: a set logged ten minutes ago must not move the weight prescribed
     * for the next set of the same session, because advancing takes two consecutive sessions
     * rather than one good one. It is also what keeps a first session calibrating all the way
     * through rather than switching modes halfway down the exercise.
     */
    private suspend fun historyOf(exerciseId: String, open: Long?): TrainingHistory =
        sessions.trainedSetsFor(exerciseId, open ?: SessionDao.NO_SESSION).toTrainingHistory()

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
     *
     * It takes the **Rep range** with it, which is safe only while every range in the table is the
     * default this same code wrote. The ticket that starts storing a chosen one has to keep the row
     * or make the estimate nullable, and is told so on #26.
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
         * One name in one place, so that the three tiers the spec settles — the plate inventory,
         * the dumbbell rack's own step and the stack positions a machine has been seen at — replace
         * a constant here rather than arriving from the screen through every caller.
         */
        val ROUNDING = Increment.DEFAULT
    }
}
