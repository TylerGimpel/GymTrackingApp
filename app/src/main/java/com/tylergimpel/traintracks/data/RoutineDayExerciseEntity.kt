package com.tylergimpel.traintracks.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * One exercise a routine day holds, in the order it is to be trained.
 *
 * What a lifter intends, as opposed to what they did: a session copies this list rather than
 * pointing at it, and the copy is the session's **Plan**. Editing it is not a revision — the
 * rotation still holds the same days in the same order, so the cycle is untouched (ADR 0006).
 *
 * At most one row per exercise per day. A day that listed the same movement twice would be asking
 * for it to be counted twice by everything downstream, and nothing in the app means that.
 */
@Entity(
    tableName = "routine_day_exercise",
    foreignKeys = [
        ForeignKey(
            entity = RoutineDayEntity::class,
            parentColumns = ["id"],
            childColumns = ["routine_day_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exercise_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["routine_day_id", "exercise_id"], unique = true),
        Index("exercise_id"),
    ],
)
data class RoutineDayExerciseEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "routine_day_id")
    val routineDayId: Long,

    @ColumnInfo(name = "exercise_id")
    val exerciseId: String,

    /** Where this exercise sits within the day, from 1. */
    @ColumnInfo(name = "order_index")
    val orderIndex: Int,

    /**
     * How many working sets this day asks for, where something has prescribed a number.
     *
     * Null is the ordinary state today and means the engine's answer for the routine's goal
     * stands. What will write a number here is the generator dividing a real volume budget across
     * a day (#74); until then nothing has made a claim specific to this exercise, and storing the
     * derived answer would strand it the day the derivation improved — the same move a display
     * unit and a progression model already make.
     */
    @ColumnInfo(name = "sets")
    val sets: Int? = null,

    /**
     * Whether the lifter marked this one to keep.
     *
     * Regeneration treats a pin as a fixed constraint and builds the day around it. **It is about
     * the shape of the routine and never about the safety of anything**: per-exercise history is
     * continuous whatever a routine does, so nothing is at stake in leaving something unpinned.
     */
    @ColumnInfo(name = "pinned", defaultValue = "0")
    val pinned: Boolean = false,
)

/**
 * What a routine day held before the last regeneration, so one step of it can be undone.
 *
 * **A shadow of `routine_day_exercise` rather than a serialized blob**, because that is what it
 * is: the same rows, one regeneration ago. Keeping the shape means the restore is a copy back
 * rather than a parser, and it means nothing here can drift out of step with the table it
 * shadows without the compiler noticing.
 *
 * It points at the live `routine_day`, which is safe because **regeneration never touches the
 * days themselves** — it replaces what unpinned days hold, and editing what a day holds is not a
 * **Revision** (ADR 0006). So the ids on the other end are the same ids they were.
 *
 * One step, and it lives until the next session is logged. The case it exists for is "I
 * regenerated and I hate it", which surfaces in seconds; a longer chain would be carried forever
 * for something that does not happen.
 */
@Entity(
    tableName = "routine_day_exercise_undo",
    foreignKeys = [
        ForeignKey(
            entity = RoutineDayEntity::class,
            parentColumns = ["id"],
            childColumns = ["routine_day_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exercise_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("routine_day_id"), Index("exercise_id")],
)
data class RoutineDayExerciseUndoEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "routine_day_id")
    val routineDayId: Long,

    @ColumnInfo(name = "exercise_id")
    val exerciseId: String,

    @ColumnInfo(name = "order_index")
    val orderIndex: Int,

    @ColumnInfo(name = "sets")
    val sets: Int? = null,

    @ColumnInfo(name = "pinned")
    val pinned: Boolean = false,
)

/** One of a day's exercises, with the bank row and the lifter's choices about it. */
data class RoutineDayExerciseWithExercise(
    @Embedded
    val entry: RoutineDayExerciseEntity,

    @Relation(entity = ExerciseEntity::class, parentColumn = "exercise_id", entityColumn = "id")
    val exercise: ExerciseWithPreference,
)

/** A routine day together with what it holds. */
data class RoutineDayWithExercises(
    @Embedded
    val day: RoutineDayEntity,

    @Relation(
        entity = RoutineDayExerciseEntity::class,
        parentColumn = "id",
        entityColumn = "routine_day_id",
    )
    val exercises: List<RoutineDayExerciseWithExercise>,
)
