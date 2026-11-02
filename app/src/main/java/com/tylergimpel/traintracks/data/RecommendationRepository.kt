package com.tylergimpel.traintracks.data

import com.tylergimpel.traintracks.domain.from
import com.tylergimpel.traintracks.domain.keepsOverriding
import androidx.room.withTransaction
import com.tylergimpel.traintracks.domain.CalibrationRamp
import kotlinx.coroutines.flow.first
import com.tylergimpel.traintracks.domain.BodySize
import com.tylergimpel.traintracks.domain.Exercise
import com.tylergimpel.traintracks.domain.ExerciseProgress
import com.tylergimpel.traintracks.domain.Increment
import com.tylergimpel.traintracks.domain.LayoffDecay
import com.tylergimpel.traintracks.domain.Kilograms
import com.tylergimpel.traintracks.domain.PlateLine
import com.tylergimpel.traintracks.domain.PlannedSets
import com.tylergimpel.traintracks.domain.stepFor
import com.tylergimpel.traintracks.domain.Recommendation
import com.tylergimpel.traintracks.domain.RepPreference
import com.tylergimpel.traintracks.domain.RepRange
import com.tylergimpel.traintracks.domain.repRangeOf
import com.tylergimpel.traintracks.domain.RoutineGoal
import com.tylergimpel.traintracks.domain.seedRepRange
import com.tylergimpel.traintracks.domain.SetType
import com.tylergimpel.traintracks.domain.warmUpsFor
import com.tylergimpel.traintracks.domain.TrainingHistory
import com.tylergimpel.traintracks.domain.recommendNextSet
import com.tylergimpel.traintracks.domain.restFor
import com.tylergimpel.traintracks.domain.resting
import java.time.Clock
import java.time.Duration
import java.time.LocalDate

/**
 * What to do next, and how much of this exercise the session was asked for.
 *
 * Paired because they come out of one reading of the log: the recommendation folds the history and
 * the ramp falls out of the weight it arrives at.
 */
data class PrescribedNext(
    val recommendation: Recommendation,
    val planned: PlannedSets?,
    /** Whether to offer to find this lift's estimate again, because it keeps being overridden (#128). */
    val offerReset: Boolean = false,
)

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
    /**
     * What day it is, which is half of how long a lifter has been away from an exercise.
     *
     * Required rather than defaulted to the device's: a caller that forgot it would age every
     * estimate by however long ago its history was written, which a test only notices weeks later.
     */
    private val clock: Clock,
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
    private val plates: PlateRepository = database.plateRepository(),
    /** What the lifter reads in, which decides the step where no equipment has been described. */
    private val profile: ProfileRepository = database.profileRepository(),
    /**
     * The routine, read for the goal and rep preference a band is seeded from.
     *
     * Only ever read here, and only for an exercise that has not been banded yet: a routine is not
     * allowed to reach back and re-band an exercise a lifter is mid-progression on.
     */
    private val routines: RoutineDao = database.routineDao(),
    /**
     * The weigh-ins, read for the latest one: what a barbell compound's first ramp may start
     * closer to. Never required — no weigh-in is the ordinary case, and costs a few ramp sets.
     */
    private val bodyweights: BodyweightRepository =
        BodyweightRepository(database, database.bodyweightDao()),
) {

    /**
     * What to tell the lifter to do for the next set of [exercise], or null where there is nothing
     * honest to say.
     *
     * Two modes, and which one this is turns on whether the exercise's own logged sets support an
     * estimate. Null where neither has anything: the lifter — or the bank — decided this lift is
     * recorded rather than coached, or it is a hold or a run, or today's ramp has run its course.
     */
    suspend fun nextSet(exercise: Exercise): Recommendation? =
        nextSetWithPlan(exercise)?.recommendation

    /**
     * The same answer, with how many sets of [exercise] this session was asked for.
     *
     * Together rather than as a second call because the two share a fold of the log and the ramp
     * computed from it. Nothing but the header wants the counts, and the header should not cost a
     * second reading of a lifter's history to draw.
     */
    suspend fun nextSetWithPlan(exercise: Exercise): PrescribedNext? {
        if (!exercise.progressionModel.isPrescribed) return null

        return database.withTransaction {
            val open = sessions.activeSession()?.id
            // A reset **Anchor** is found again from the next session (#128): the day it was reset
            // on carries on as it was, so accepting mid-session does not start a calibration ramp
            // halfway down the exercise, and the overrides that prompted it are not folded into the
            // new estimate. The log is untouched; only what the engine folds starts later.
            val today = LocalDate.now(clock)
            val resetPending = exercise.estimateResetOn?.let { !it.isBefore(today) } == true
            val everything = historyOf(exercise.id, open)
            val history = if (resetPending) everything else everything.from(exercise.estimateResetOn?.plusDays(1))
            // Two bands, and they are not the same question. The seed is what the routine's
            // goal put this exercise in and is what gets stored; the lifter's own answer, where
            // they gave one, is what the set is actually prescribed in. Storing the effective one
            // would mean clearing a choice fell back to that same choice.
            val seeded = seededRepRangeFor(exercise)
            val repRange = repRangeOf(chosen = exercise.chosenRepRange, seeded = seeded)
            val rounding = incrementFor(exercise)
            val progress = history.progressIn(repRange, rounding, decay, today)
            remember(exercise.id, progress, seeded)
            val next = recommendNextSet(
                history = history,
                progress = progress,
                calibration = CalibrationRamp.of(
                    exercise = exercise,
                    rounding = rounding,
                    bar = plates.barbell(exercise.displayUnit).bar.takeIf { exercise.isBarbell },
                    body = bodySize(),
                ),
                setsToday = open?.let { sessions.setsLoggedInSession(exercise.id, it) } ?: 0,
                rounding = rounding,
            ) ?: return@withTransaction null

            PrescribedNext(
                recommendation = warmUpFirst(next, exercise, open, rounding)
                    .let { it.resting(restToPrescribe(exercise, it.setType, repRange, open)) },
                planned = plannedSetsOf(next, exercise, open, rounding),
                // Offered on the sessions since the lifter last said not now, so saying it means
                // waiting for the whole pattern again rather than being asked next time — and not
                // at all once they have said yes and are waiting for the next session.
                offerReset = !resetPending &&
                    history.from(exercise.resetOfferDismissedOn?.plusDays(1)).keepsOverriding(),
            )
        }
    }

    /**
     * The lifter's size as far as they have given it: their latest weigh-in, and their sex if said.
     *
     * Null without a weigh-in, which leaves every ramp where it always started (#90).
     */
    private suspend fun bodySize(): BodySize? = bodyweights.latest()?.let { latest ->
        BodySize(bodyweight = latest.weight, sex = profile.observeProfile().first().sex)
    }

    /**
     * How many sets this session asked for of [exercise], or null where nothing asked.
     *
     * The working half is the plan's, copied from the routine day when the session began. The
     * whole is that plus the ramp — which is settled only once the work starts: until then the
     * app still owes whatever rungs are left, and afterwards the ramp is however many the lifter
     * actually did. A total that kept counting rungs nobody climbed would leave somebody reading
     * `6 of 7` with nothing left to do.
     *
     * **Nothing is claimed while the app is calibrating.** The session *is* the ramp then — the
     * plan's working sets are not what the lifter is doing, and counting their calibration rungs
     * against a total meant for work reads `set 5 of 4`. A mode that announces it is guessing has
     * no business also stating how much is left, which is the same rule that keeps a denominator
     * off a session trained with no routine at all.
     */
    private suspend fun plannedSetsOf(
        next: Recommendation,
        exercise: Exercise,
        open: Long?,
        rounding: Increment,
    ): PlannedSets? {
        if (next is Recommendation.Calibrating) return null
        val session = open ?: return null
        val working = sessions.plannedSets(session, exercise.id) ?: return null
        val rampDone = sessions.setsOfTypeInSession(exercise.id, session, SetType.WARMUP.stored)
        val workStarted =
            sessions.setsOfTypeInSession(exercise.id, session, SetType.WORKING.stored) > 0

        val ramp = when {
            workStarted -> rampDone
            else -> maxOf(rampDone, rampSizeFor(next, exercise, session, rounding))
        }
        return PlannedSets(total = ramp + working, working = working)
    }

    /** How many rungs the ramp up to this prescription has, or none where it asks for no load. */
    private suspend fun rampSizeFor(
        next: Recommendation,
        exercise: Exercise,
        session: Long,
        rounding: Increment,
    ): Int {
        val load = when (next) {
            is Recommendation.Prescribing, is Recommendation.CuttingVolume -> next.prescription.load
            else -> null
        } ?: return 0

        return warmUpsFor(
            workingLoad = load,
            exercise = exercise,
            alreadyWorkedTheMuscle = alreadyWarmed(exercise, session),
            rounding = rounding,
        ).size
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
     * The band the routine put this exercise in, which is not necessarily the one it is worked in.
     *
     * **Read back rather than recomputed**, once it exists. Unlike the anchor it is derived from
     * nothing and is a decision (ADR 0010), so a goal edited since must not quietly re-band a
     * lifter halfway up a progression — offering that as a regeneration is #77's job.
     *
     * So only an exercise that has never been banded reads the routine, which is what makes the
     * seeding happen once. With no routine yet there is no goal to read, and the default answers.
     */
    private suspend fun seededRepRangeFor(exercise: Exercise): RepRange {
        exercises.progress(exercise.id)?.let { return it.seededRepRange() }

        val routine = routines.routine() ?: return RepRange.DEFAULT
        return seedRepRange(
            goal = RoutineGoal.valueOf(routine.goal),
            preference = RepPreference.valueOf(routine.repPreference),
            // The gym, because a preference for heavy triples is not deliverable in a room with
            // nothing to load. The conflict that explains the override is reported on the routine.
            equipment = routine.equipment.toSet(),
        ).range
    }

    /**
     * Keeps what the app now makes of this exercise, and the band it was asked in either way.
     *
     * Forgetting the estimate matters: an exercise whose only logged set has been deleted has
     * nothing to prescribe from, and an estimate left behind would go on prescribing from training
     * that is no longer there.
     *
     * **The band is not forgotten with it.** It was seeded from the routine's goal rather than
     * derived from the log, so it is a decision and outlives the arithmetic (ADR 0010) — which is
     * also why a row is written for an exercise the app has no estimate for at all. That write is
     * what pins the band, so the first recommendation an exercise ever gets settles what it is
     * worked in, and a goal edited afterwards changes nothing until somebody asks it to.
     */
    private suspend fun remember(
        exerciseId: String,
        progress: ExerciseProgress?,
        seeded: RepRange,
    ) {
        exercises.upsertProgress(
            ExerciseProgressEntity(
                exerciseId = exerciseId,
                anchorKg = progress?.anchor?.estimated1rm?.value,
                repRangeBottom = seeded.bottom,
                repRangeTop = seeded.top,
                workingLoadKg = progress?.workingLoad?.value,
            ),
        )
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
    private suspend fun incrementFor(exercise: Exercise): Increment {
        // What the lifter said, over anything the app can work out. They are standing in front of
        // the rack: a gym that goes up in fours is a fact, not a tier to be inferred.
        exercise.chosenIncrement?.let { return Increment(it) }

        // The unit the weight will be *read* in, which belongs to the exercise rather than to
        // the lifter: a lat pulldown labelled in pounds is labelled in pounds whoever walks up
        // to it, and rounding it in kilograms is what puts 132.28 on the screen (#64).
        val readIn = exercise.displayUnit

        return when {
            exercise.isBarbell -> plates.barbell(readIn)

            // Read from every session including the one open right now, which is the one
            // place today's sets are not held back. A pin position is not a claim about what the
            // lifter has proved — that is what ADR 0010 keeps out of today — it is a fact about
            // the machine the moment somebody sets it, and waiting until the session is over to
            // believe in it would leave a lifter who has just outgrown a setting being offered
            // the old one for the rest of the evening (#65).
            //
            // Two, not one: a single logged position says where the pin was once and nothing
            // about where the next notch is. Until there are two the app knows no more about
            // this stack than about a dumbbell rack, and a step is the honest stand-in.
            exercise.isStacked -> historyOf(exercise.id, open = null).observedLoads()
                .takeIf { it.size >= STACK_IS_KNOWN_FROM }
                ?.let(Increment::Stack)
                ?: stepFor(readIn)

            else -> stepFor(readIn)
        }
    }

    /**
     * How to load the bar for [load], or null where this exercise is not one.
     *
     * The same inventory the rounding above read, which is the point of it being one inventory:
     * the line under a prescription always adds up to the number above it, because the two are
     * one answer asked twice. A view onto work already done rather than a second opinion.
     */
    suspend fun plateLine(exercise: Exercise, load: Kilograms): PlateLine? {
        // Only where the plates are what rounded it. A lifter who has set a step of their own
        // has overruled the inventory, and the weight that comes back is not one these plates
        // were asked to build — showing a line under it would be explaining somebody else's
        // arithmetic, and getting it wrong.
        if (!exercise.isBarbell || exercise.chosenIncrement != null) return null

        val barbell = plates.barbell(exercise.displayUnit)
        // Null rather than an empty list where the weight is not buildable, so that "a bar with
        // nothing on it" stays a real answer and never stands in for "the app cannot say".
        return barbell.perSide(load)?.let { PlateLine(barbell.bar, it) }
    }

    private companion object {
        /** How many distinct positions it takes before the log describes a stack at all. */
        const val STACK_IS_KNOWN_FROM = 2
    }
}
