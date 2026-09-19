package com.tylergimpel.gymtracker.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Relation
import com.tylergimpel.gymtracker.domain.Effort
import com.tylergimpel.gymtracker.domain.Kilograms
import com.tylergimpel.gymtracker.domain.Metres
import com.tylergimpel.gymtracker.domain.Performance
import com.tylergimpel.gymtracker.domain.PerformedSet
import com.tylergimpel.gymtracker.domain.Prescription
import com.tylergimpel.gymtracker.domain.SessionExercise
import com.tylergimpel.gymtracker.domain.SessionPlan
import com.tylergimpel.gymtracker.domain.SetMeasurements
import com.tylergimpel.gymtracker.domain.SetType
import com.tylergimpel.gymtracker.domain.Side
import com.tylergimpel.gymtracker.domain.TrainedSession
import com.tylergimpel.gymtracker.domain.TrainedSet
import com.tylergimpel.gymtracker.domain.TrainingHistory
import com.tylergimpel.gymtracker.domain.WeightUnit
import java.time.Duration
import java.time.LocalDate

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

    @Relation(
        entity = SessionPlanEntity::class,
        parentColumn = "id",
        entityColumn = "session_id",
    )
    val plan: List<SessionPlanWithExercise>,

    @Relation(parentColumn = "id", entityColumn = "session_id")
    val supersets: List<SupersetGroupEntity>,
)

/**
 * What the session was for, in the order its routine day held it.
 *
 * Empty for a session trained with no routine, which reads back as a plan of nothing rather than as
 * anything missing.
 */
fun List<SessionPlanWithExercise>.toSessionPlan(lifterUnit: WeightUnit = WeightUnit.DEFAULT) =
    SessionPlan(
        exercises = sortedBy { it.entry.orderIndex }.map { it.exercise.toDomain(lifterUnit) },
    )

fun SessionExerciseWithSets.toDomain(lifterUnit: WeightUnit = WeightUnit.DEFAULT) = SessionExercise(
    id = sessionExercise.id,
    exercise = exercise.toDomain(lifterUnit),
    orderIndex = sessionExercise.orderIndex,
    sets = sets.toPerformedSets(),
    supersetId = sessionExercise.supersetGroupId,
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
                prescription = rows.first().setEntry.toPrescription(),
                measurements = rows.first().setEntry.toMeasurements(),
                efforts = rows.sortedBy { it.setEntry.id }.map(SetEntryWithDrops::toEffort),
            )
        }

private fun SetEntryWithDrops.toEffort() = Effort(
    id = setEntry.id,
    side = Side.fromStored(setEntry.side),
    performance = setEntry.toPerformance(),
    note = setEntry.note,
    drops = drops.sortedBy(DropSegmentEntity::orderIndex).map(DropSegmentEntity::toDomain),
)

/** What the lifter measured about this set, where they measured anything. */
private fun SetEntryEntity.toMeasurements() = SetMeasurements(
    rpe = rpe,
    heartRate = heartRate,
    restTaken = actualRestSeconds?.let { Duration.ofSeconds(it.toLong()) },
)

/** What the app asked of this set, where anything asked. */
private fun SetEntryEntity.toPrescription() = Prescription(
    load = recommendedWeightKg?.let(::Kilograms),
    reps = recommendedReps,
    rest = recommendedRestSeconds?.let { Duration.ofSeconds(it.toLong()) },
)

/**
 * What the row records, read from the columns it filled rather than from the exercise it hangs off.
 *
 * The row is self-describing on purpose: a set is a record of what happened, and re-reading an old
 * one through whatever its exercise records *today* would let a correction to the bank rewrite
 * history. A row that filled neither shape is corrupt rather than empty, and says so here instead
 * of arriving somewhere further away as a weight of zero.
 */
internal fun SetEntryEntity.toPerformance(): Performance = when {
    durationSeconds != null && distanceM != null ->
        Performance.DistanceAndTime(Metres(distanceM), Duration.ofSeconds(durationSeconds.toLong()))

    durationSeconds != null -> Performance.Timed(Duration.ofSeconds(durationSeconds.toLong()))
    weightKg != null && reps != null -> Performance.WeightAndReps(Kilograms(weightKg), reps)

    // The sign says which way the help went, so the row answers for itself rather than asking the
    // exercise what it records today.
    reps != null && addedWeightKg != null && addedWeightKg < 0.0 ->
        Performance.Assisted(Kilograms(-addedWeightKg), reps)

    reps != null -> Performance.BodyweightReps(addedWeightKg?.let(::Kilograms), reps)
    else -> error("Set entry $id records no shape at all: no duration, and no weight and reps")
}

/**
 * One logged row of one exercise, carrying the session it was logged in.
 *
 * A projection rather than a relation: the engine reads across every session an exercise has ever
 * appeared in, which is the opposite direction from [SessionWithContent] and would otherwise mean
 * loading whole sessions to reach a handful of their rows.
 */
data class TrainedSetRow(
    @Embedded
    val setEntry: SetEntryEntity,

    @ColumnInfo(name = "session_id")
    val sessionId: Long,

    @ColumnInfo(name = "session_date")
    val date: LocalDate,
)

/**
 * Rows gathered into the history the recommendation engine reads.
 *
 * Two collapses happen here, both of them ADR 0003's rule that a set is one set however many rows
 * record it: rows sharing a set index are one set, read as the effort progression measures — the
 * first, never a later side — and sets sharing a session are one session, which contributes one
 * point to the estimate however many sets it took.
 *
 * Drops need no filtering at all: a drop is a row of `drop_segment` and was never in this list,
 * which is what keeps a fatigued continuation out of an estimated one-rep maximum.
 */
fun List<TrainedSetRow>.toTrainingHistory(): TrainingHistory = TrainingHistory(
    groupBy(TrainedSetRow::sessionId).map { (_, rows) ->
        TrainedSession(
            date = rows.first().date,
            sets = rows.groupBy { it.setEntry.sessionExerciseId to it.setEntry.setIndex }
                .map { (_, ofOneSet) -> ofOneSet.minBy { it.setEntry.id } }
                .map {
                    TrainedSet(
                        type = SetType.fromStored(it.setEntry.type),
                        performance = it.setEntry.toPerformance(),
                        // What the app asked of that set, which is what the ladder reads it
                        // against: beaten, missed, or a weight the lifter chose instead.
                        prescription = it.setEntry.toPrescription(),
                    )
                },
        )
    },
)
