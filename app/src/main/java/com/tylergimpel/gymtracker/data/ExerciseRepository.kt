package com.tylergimpel.gymtracker.data

import com.tylergimpel.gymtracker.domain.Exercise
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Reads the exercise bank, handing the rest of the app domain types rather than Room entities. */
class ExerciseRepository(private val exercises: ExerciseDao) {

    fun observeBank(): Flow<List<Exercise>> =
        exercises.observeAll().map { rows -> rows.map(ExerciseEntity::toDomain) }
}
