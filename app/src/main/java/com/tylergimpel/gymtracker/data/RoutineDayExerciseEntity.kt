package com.tylergimpel.gymtracker.data

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
