package com.tylergimpel.gymtracker.data

import java.time.LocalDate
import androidx.room.withTransaction
import com.tylergimpel.gymtracker.domain.BankChange
import com.tylergimpel.gymtracker.domain.Exercise
import com.tylergimpel.gymtracker.domain.ExerciseSource
import com.tylergimpel.gymtracker.domain.NewExerciseDraft
import com.tylergimpel.gymtracker.domain.ProgressionModel
import com.tylergimpel.gymtracker.domain.RepRange
import com.tylergimpel.gymtracker.domain.WeightUnit
import com.tylergimpel.gymtracker.domain.planBankUpdate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.util.UUID
import com.tylergimpel.gymtracker.domain.Kilograms

/** Reads the exercise bank, handing the rest of the app domain types rather than Room entities. */
class ExerciseRepository(
    private val database: GymDatabase,
    private val exercises: ExerciseDao,
    private val sessions: SessionDao,
    /**
     * Where the unit comes from for an exercise the lifter has said nothing about.
     *
     * Injected rather than read here, so that "what does this lifter read in" has one answer in one
     * place and a bank row cannot disagree with a logged set about it.
     */
    private val profile: ProfileRepository,
) {

    fun observeBank(): Flow<List<Exercise>> =
        combine(exercises.observeAll(), profile.observeDisplayUnit()) { rows, lifterUnit ->
            rows.map { it.toDomain(lifterUnit) }
        }

    /**
     * Remembers that [exerciseId] is read in [displayUnit] from now on.
     *
     * A unit matching the lifter's own is stored as no choice at all, the same move a set makes
     * against its exercise: this row is for a machine that departs from how its owner reads, and an
     * exercise that agrees with them has not departed from anything. One that stored the agreement
     * would be frozen in this unit the day the lifter switched, which is the trail of stranded
     * kilogram sets ADR 0009 exists to prevent, one level up.
     *
     * Sets that had already departed to this very unit stop departing, in the same transaction: a
     * set logged in pounds before the exercise read in pounds is no longer an override of
     * anything, and one that kept it would be stranded there the next time the exercise moved
     * (ADR 0009).
     *
     * Nothing logged moves. The weights are canonical kilograms and this only changes the number
     * they are shown as, which is what keeps one gym's two lat pulldowns on one graph.
     */
    suspend fun setDisplayUnit(exerciseId: String, displayUnit: WeightUnit) =
        database.withTransaction {
            val chosen = displayUnit.takeIf { it != profile.displayUnit() }
            exercises.setDisplayUnit(exerciseId, chosen?.stored)
            exercises.forgetEmptyPreference(exerciseId)
            sessions.clearDisplayUnitOverrides(exerciseId, displayUnit.stored)
        }

    /**
     * Records what the lifter calls [exerciseId], or clears it back to the bank's name.
     *
     * Kept apart from the bank row, so a later update correcting the shipped name cannot reach it —
     * and so the shipped name stays available for the search aliases to resolve against.
     */
    suspend fun rename(exerciseId: String, name: String?) = database.withTransaction {
        exercises.setName(exerciseId, name?.takeIf(String::isNotBlank))
        exercises.forgetEmptyPreference(exerciseId)
    }

    /**
     * Records what the lifter decided [exerciseId] advances by, or clears it back to the bank's.
     *
     * Kept apart from the bank row, so an update improving what the bank derives cannot overrule a
     * lifter who has said they do not want this lift coached (ADR 0009).
     */
    suspend fun setProgressionModel(exerciseId: String, model: ProgressionModel?) =
        database.withTransaction {
            exercises.setProgressionModel(exerciseId, model?.stored)
            exercises.forgetEmptyPreference(exerciseId)
        }

    /**
     * Records how long the lifter rests on [exerciseId] from now on, or clears it back to what the
     * app derives from each set.
     *
     * Kept apart from the bank row like every other choice: the app's table is a starting point,
     * and a lifter who knows the machine is in a corner nobody walks past is right about their own
     * gym (ADR 0009).
     */
    suspend fun setRest(exerciseId: String, rest: Duration?) = database.withTransaction {
        exercises.setRest(exerciseId, rest?.seconds?.toInt())
        exercises.forgetEmptyPreference(exerciseId)
    }

    /**
     * Resets [exerciseId]'s **Anchor** [on] a day, so the engine finds it again from the next
     * session (#128).
     *
     * Nothing logged is touched: records, charts and history still read every set. Only the fold
     * the engine prescribes from starts again, which with nothing logged since is **Calibrating**.
     */
    suspend fun resetEstimate(exerciseId: String, on: LocalDate) = exercises.setEstimateResetOn(exerciseId, on)

    /** The lifter said not now to resetting [exerciseId]'s estimate: the offer waits for the pattern again. */
    suspend fun keepEstimate(exerciseId: String, on: LocalDate) = exercises.setResetOfferDismissedOn(exerciseId, on)

    /**
     * Records that the lifter never wants [exerciseId] suggested again, or takes it back.
     *
     * Global and reversible, and it touches nothing that was logged: the sets already against
     * this exercise are a record of training that happened, and excluding it from what the app
     * offers is not a claim that it did not.
     */
    suspend fun setDisliked(exerciseId: String, disliked: Boolean) = database.withTransaction {
        exercises.setDisliked(exerciseId, disliked)
        exercises.forgetEmptyPreference(exerciseId)
    }

    /**
     * Records the step [exerciseId] moves by, or clears it back to what the equipment says.
     *
     * The lifter's word over the engine's: they are standing in front of the rack and the app is
     * not. Cleared to null rather than to a number, so that "I was wrong about this" puts the
     * equipment back in charge instead of freezing whatever was guessed.
     */
    suspend fun setIncrement(exerciseId: String, increment: Kilograms?) = database.withTransaction {
        exercises.setIncrement(exerciseId, increment?.value)
        exercises.forgetEmptyPreference(exerciseId)
    }

    /**
     * Records the band the lifter works [exerciseId] in, or clears it back to the seeded one.
     *
     * Clearing hands the question back to the routine's goal rather than to a constant, which is
     * why it is null here rather than a band written out: an exercise the lifter has stopped
     * having an opinion about is one the goal answers for again.
     */
    suspend fun setRepRange(exerciseId: String, repRange: RepRange?) = database.withTransaction {
        exercises.setRepRange(exerciseId, repRange?.bottom, repRange?.top)
        exercises.forgetEmptyPreference(exerciseId)
    }

    /** Records the standing note on [exerciseId] — "seat notch 3, use the long bar". */
    suspend fun note(exerciseId: String, note: String?) = database.withTransaction {
        exercises.setNote(exerciseId, note?.takeIf(String::isNotBlank))
        exercises.forgetEmptyPreference(exerciseId)
    }

    /**
     * Adds an exercise the lifter's gym has and the bank has never heard of.
     *
     * Its id is a UUID, in an id space of its own: a bank update matches on the upstream string ids
     * it ships, so it can never find this row to correct or drop it.
     */
    suspend fun createExercise(draft: NewExerciseDraft): String = createOwn(draft).id

    /** The same, answering with the exercise itself, for a caller that goes on to use it. */
    suspend fun createOwn(draft: NewExerciseDraft): Exercise {
        val own = Exercise(
            id = UUID.randomUUID().toString(),
            bankName = draft.name.trim(),
            primaryMuscles = draft.primaryMuscles,
            secondaryMuscles = draft.secondaryMuscles,
            equipment = draft.equipment,
            mechanic = draft.mechanic,
            force = draft.force,
            category = draft.category,
            level = draft.level,
            loggingType = draft.loggingType,
            unilateral = draft.unilateral,
            source = ExerciseSource.USER,
        )
        exercises.insert(own.toEntity())
        return own
    }

    /**
     * Applies an update to the shipped bank, obeying [planBankUpdate].
     *
     * The rules live in the domain and are tested there; this is the part that writes. Everything
     * runs in one transaction, because a half-applied update is a bank in a state no rule describes.
     */
    suspend fun applyBankUpdate(incoming: List<Exercise>) = database.withTransaction {
        val current = exercises.all().map(ExerciseWithPreference::toDomain)
        val referenced = exercises.referencedExerciseIds().toSet()
        val plan = planBankUpdate(current, incoming) { it in referenced }

        plan.forEach { change ->
            when (change) {
                is BankChange.Add -> exercises.insert(change.exercise.toEntity())
                is BankChange.Replace -> exercises.update(change.exercise.toEntity())
                is BankChange.Tombstone -> exercises.tombstone(change.id)
                is BankChange.Remove -> exercises.deleteById(change.id)
            }
        }
    }
}
