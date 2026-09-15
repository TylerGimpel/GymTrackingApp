package com.tylergimpel.gymtracker.data

import androidx.room.Embedded
import androidx.room.Relation
import com.tylergimpel.gymtracker.domain.Effort
import com.tylergimpel.gymtracker.domain.Kilograms
import com.tylergimpel.gymtracker.domain.PerformedSet
import com.tylergimpel.gymtracker.domain.SessionExercise
import com.tylergimpel.gymtracker.domain.SetType
import com.tylergimpel.gymtracker.domain.Side
import com.tylergimpel.gymtracker.domain.WeightUnit

/** One stored row of a set, together with any drops continuing it. */
data class SetEntryWithDrops(
    @Embedded
    val setEntry: SetEntryEntity,

    @Relation(parentColumn = "id", entityColumn = "set_entry_id")
    val drops: List<DropSegmentEntity>,
)

/** One exercise in a session together with every row logged against it. */
data class SessionExerciseWithSets(
    @Embedded
    val sessionExercise: SessionExerciseEntity,

    @Relation(entity = ExerciseEntity::class, parentColumn = "exercise_id", entityColumn = "id")
    val exercise: ExerciseWithPreference,

    @Relation(
        entity = SetEntryEntity::class,
        parentColumn = "id",
        entityColumn = "session_exercise_id",
    )
    val sets: List<SetEntryWithDrops>,
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
    sets = sets.toPerformedSets(),
)

/**
 * Gathers stored rows into sets.
 *
 * This is where "one set, however many rows record it" stops being a rule every caller has to
 * remember and becomes the shape of the data: rows sharing a set index arrive as one
 * [PerformedSet], so nothing downstream is able to count them twice.
 */
fun List<SetEntryWithDrops>.toPerformedSets(): List<PerformedSet> =
    groupBy { it.setEntry.setIndex }
        .entries
        .sortedBy { it.key }
        .map { (setIndex, rows) ->
            PerformedSet(
                setIndex = setIndex,
                // Every row of one set carries the same type: the set is the thing that has a type,
                // and each of its rows records it.
                type = SetType.fromStored(rows.first().setEntry.type),
                // As with the type: the unit belongs to the set, and each of its rows records it.
                displayUnit = WeightUnit.fromStored(rows.first().setEntry.displayUnit),
                efforts = rows.sortedBy { it.setEntry.id }.map(SetEntryWithDrops::toEffort),
            )
        }

private fun SetEntryWithDrops.toEffort() = Effort(
    id = setEntry.id,
    side = Side.fromStored(setEntry.side),
    weight = Kilograms(setEntry.weightKg),
    reps = setEntry.reps,
    note = setEntry.note,
    drops = drops.sortedBy(DropSegmentEntity::orderIndex).map(DropSegmentEntity::toDomain),
)
