package com.tylergimpel.gymtracker.data

import androidx.room.Embedded
import androidx.room.Relation
import com.tylergimpel.gymtracker.domain.SessionExercise

/** One exercise in a session together with every set logged against it. */
data class SessionExerciseWithSets(
    @Embedded
    val sessionExercise: SessionExerciseEntity,

    @Relation(parentColumn = "exercise_id", entityColumn = "id")
    val exercise: ExerciseEntity,

    @Relation(parentColumn = "id", entityColumn = "session_exercise_id")
    val sets: List<SetEntryEntity>,
)

/** A whole session: what was done, grouped by the exercise it was done against. */
data class SessionWithContent(
    @Embedded
    val session: SessionEntity,

    @Relation(
        entity = SessionExerciseEntity::class,
        parentColumn = "id",
        entityColumn = "session_id",
    )
    val exercises: List<SessionExerciseWithSets>,
)

fun SessionExerciseWithSets.toDomain() = SessionExercise(
    id = sessionExercise.id,
    exercise = exercise.toDomain(),
    orderIndex = sessionExercise.orderIndex,
    sets = sets.map(SetEntryEntity::toDomain).sortedBy { it.setIndex },
)
