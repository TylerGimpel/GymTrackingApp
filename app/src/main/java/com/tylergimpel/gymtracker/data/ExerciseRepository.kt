package com.tylergimpel.gymtracker.data

import androidx.room.withTransaction
import com.tylergimpel.gymtracker.domain.Exercise
import com.tylergimpel.gymtracker.domain.WeightUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
            exercises.save(ExercisePreferenceEntity.of(exerciseId, displayUnit))
            sessions.clearDisplayUnitOverrides(exerciseId, displayUnit.stored)
        }
}
