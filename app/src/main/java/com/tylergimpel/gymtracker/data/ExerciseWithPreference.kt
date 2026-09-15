package com.tylergimpel.gymtracker.data

import androidx.room.Embedded
import androidx.room.Relation
import com.tylergimpel.gymtracker.domain.Exercise

/**
 * A bank row together with whatever the lifter has decided about it.
 *
 * Stored apart (ADR 0009) and joined here, so nothing downstream has to remember to go and look a
 * choice up. An exercise nobody has decided anything about has no preference row, which is the
 * ordinary case rather than missing data.
 */
data class ExerciseWithPreference(
    @Embedded
    val exercise: ExerciseEntity,

    @Relation(parentColumn = "id", entityColumn = "exercise_id")
    val preference: ExercisePreferenceEntity?,
)

fun ExerciseWithPreference.toDomain(): Exercise = exercise.toDomain(preference)
