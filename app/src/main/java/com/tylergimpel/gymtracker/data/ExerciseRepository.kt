package com.tylergimpel.gymtracker.data

import androidx.room.withTransaction
import com.tylergimpel.gymtracker.domain.BankChange
import com.tylergimpel.gymtracker.domain.Exercise
import com.tylergimpel.gymtracker.domain.ExerciseSource
import com.tylergimpel.gymtracker.domain.NewExerciseDraft
import com.tylergimpel.gymtracker.domain.WeightUnit
import com.tylergimpel.gymtracker.domain.planBankUpdate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/** Reads the exercise bank, handing the rest of the app domain types rather than Room entities. */
class ExerciseRepository(
    private val database: GymDatabase,
    private val exercises: ExerciseDao,
    private val sessions: SessionDao,
) {

    fun observeBank(): Flow<List<Exercise>> =
        exercises.observeAll().map { rows -> rows.map(ExerciseWithPreference::toDomain) }

    /**
     * Remembers that [exerciseId] is read in [displayUnit] from now on.
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
            exercises.setDisplayUnit(exerciseId, displayUnit.stored)
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
    suspend fun createExercise(draft: NewExerciseDraft): String {
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
        return own.id
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
