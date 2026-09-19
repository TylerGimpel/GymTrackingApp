package com.tylergimpel.gymtracker.data

import androidx.room.withTransaction
import com.tylergimpel.gymtracker.domain.CalibrationRamp
import com.tylergimpel.gymtracker.domain.Exercise
import com.tylergimpel.gymtracker.domain.ExerciseProgress
import com.tylergimpel.gymtracker.domain.Increment
import com.tylergimpel.gymtracker.domain.LayoffDecay
import com.tylergimpel.gymtracker.domain.Recommendation
import com.tylergimpel.gymtracker.domain.RepRange
import com.tylergimpel.gymtracker.domain.SetType
import com.tylergimpel.gymtracker.domain.warmUpsFor
import com.tylergimpel.gymtracker.domain.TrainingHistory
import com.tylergimpel.gymtracker.domain.recommendNextSet
import com.tylergimpel.gymtracker.domain.restFor
import com.tylergimpel.gymtracker.domain.resting
import java.time.Clock
import java.time.Duration
import java.time.LocalDate

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
    /** What day it is, which is half of how long a lifter has been away from an exercise. */
    private val clock: Clock = Clock.systemDefaultZone(),
    /**
     * How an estimate ages while an exercise goes untrained.
     *
     * Passed in rather than reached for, because every number in it is invented and a policy that
     * can be handed to the engine is a policy a test can state rather than assert around.
     */
    private val decay: LayoffDecay = LayoffDecay.DEFAULT,
    /**
     * The plates in the room, which is what a barbell recommendation has to land on.
     *
     * Defaulted from the database rather than required, so that the engine can be built the way
     * it always was and the inventory arrives with it.
     */
    private val plates: PlateRepository =
        PlateRepository(database.plateDao(), database.profileRepository()),
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
            val repRange = repRangeFor(exercise.id)
            val rounding = incrementFor(exercise, history)
            val progress = history.progressIn(repRange, rounding, decay, LocalDate.now(clock))
            remember(exercise.id, progress)
            recommendNextSet(
                history = history,
                progress = progress,
                calibration = CalibrationRamp.of(exercise, rounding),
                setsToday = open?.let { sessions.setsLoggedInSession(exercise.id, it) } ?: 0,
            )
                ?.let { warmUpFirst(it, exercise, open, rounding) }
                ?.let { it.resting(restToPrescribe(exercise, it.setType, repRange, open)) }
        }
    }

    /**
     * The next rung of the ramp where one is still owed, and what was asked for otherwise.
     *
     * The ramp is generated from the weight the set is climbing to, so it arrives with whatever
     * the engine settled on rather than from a second opinion about the working weight. How much
     * of it is left is counted from the log, like everything else here — a lifter who logged
     * their own warm-ups has climbed the ramp with them.
     *
     * Nothing to warm up for while the app is still calibrating: the ramp *is* the session then.
     */
    private suspend fun warmUpFirst(
        recommendation: Recommendation,
        exercise: Exercise,
        open: Long?,
        rounding: Increment,
    ): Recommendation {
        val load = when (recommendation) {
            // Both prescribe a real working load. Holding the weight and cutting the volume is
            // still the weight, and a lifter on the rung that says so needs the same ramp.
            is Recommendation.Prescribing, is Recommendation.CuttingVolume ->
                recommendation.prescription.load

            else -> null
        } ?: return recommendation
        val session = open ?: return recommendation

        // A ramp leads up to the work and is over once the work starts. Offering one to a
        // lifter who is three sets in would be the app asking them to warm up for something they
        // are in the middle of.
        if (sessions.setsOfTypeInSession(exercise.id, session, SetType.WORKING.stored) > 0) {
            return recommendation
        }

        val ramp = warmUpsFor(
            workingLoad = load,
            exercise = exercise,
            alreadyWorkedTheMuscle = alreadyWarmed(exercise, session),
            rounding = rounding,
        )
        val done = sessions.setsOfTypeInSession(exercise.id, session, SetType.WARMUP.stored)
        return ramp.getOrNull(done)?.let(Recommendation::WarmingUp) ?: recommendation
    }

    /**
     * How many exercises earlier in this session already trained the same primary muscle.
     *
     * A ramp belongs to the muscle rather than to the exercise, so the third chest movement of an
     * evening has had its warming-up done for it by the first two.
     */
    private suspend fun alreadyWarmed(exercise: Exercise, session: Long): Int {
        val muscles = exercise.primaryMuscles.toSet()
        if (muscles.isEmpty()) return 0

        return sessions.exercisesTrainedIn(session)
            .filterNot { it == exercise.id }
            .distinct()
            .count { other ->
                exercises.byId(other)?.toDomain()?.let(exercise::sharesPrimaryMuscleWith) == true
            }
    }

    /**
     * How long to rest after the set being prescribed, or nothing where this set has no rest of
     * its own to take.
     *
     * Three answers in order of who gets the last word. A **Superset** member has none at all: the
     * point of alternating is not stopping, so the rest belongs to the group and is recorded there
     * as the longest any member asked for. Otherwise the lifter's own answer for this exercise
     * stands if they gave one — they know their gym — and the derived one stands if they did not.
     */
    private suspend fun restToPrescribe(
        exercise: Exercise,
        setType: SetType,
        repRange: RepRange,
        open: Long?,
    ): Duration? {
        val derived = exercise.chosenRest ?: restFor(exercise, setType, repRange)
        val group = open?.let { sessions.supersetOf(it, exercise.id) } ?: return derived

        sessions.restAtLeast(group, derived.seconds.toInt())
        return null
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

    /**
     * What this exercise can actually be loaded to.
     *
     * Three tiers, chosen by what the bank says the exercise is loaded with (#51):
     *
     * - **A barbell** is the bar and the pairs in the room, so the number offered is one the
     *   lifter's own plates can build and the plate line can show them how to.
     * - **A machine** is only ever a notch it has been logged at, because a **Stack number** is a
     *   gym-local scale and nothing can be inferred about its gaps from any other machine. Until
     *   it has been seen at all there is nothing to learn from, so a step stands in.
     * - **Everything else** moves by a step: dumbbells really do come in even ones, and for
     *   whatever nobody has described a step is close enough to be corrected rather than resented.
     */
    private suspend fun incrementFor(exercise: Exercise, history: TrainingHistory): Increment =
        when (exercise.equipment) {
            BARBELL -> plates.barbell()

            // Two, not one: a single logged position says where the pin was once and nothing
            // about where the next notch is. Until there are two the app knows no more about
            // this stack than about a dumbbell rack, and a step is the honest stand-in.
            in STACKED -> history.observedLoads()
                .takeIf { it.size >= STACK_IS_KNOWN_FROM }
                ?.let(Increment::Stack)
                ?: Increment.DEFAULT

            else -> Increment.DEFAULT
        }

    private companion object {
        /** What the bank calls the thing built from the plate inventory. */
        const val BARBELL = "barbell"

        /** What the bank calls the things whose numbers are pin positions rather than weights. */
        val STACKED = setOf("machine", "cable")

        /** How many distinct positions it takes before the log describes a stack at all. */
        const val STACK_IS_KNOWN_FROM = 2
    }
}
