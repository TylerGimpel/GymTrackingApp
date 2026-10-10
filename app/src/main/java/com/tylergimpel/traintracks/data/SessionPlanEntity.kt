package com.tylergimpel.traintracks.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * One exercise a session was supposed to contain, copied from its routine day when it began.
 *
 * **Deliberately no foreign key to `routine_day`.** The same seam the session's copied routine and
 * day names sit on: history references the exercise with a real constraint, and the routine only
 * ever by copy. Revising or deleting a routine can therefore not reach a plan, which is what makes
 * changing a routine safe.
 *
 * The exercise is a real constraint, because a plan naming a row that no longer exists is not a
 * record of anything.
 */
@Entity(
    tableName = "session_plan",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exercise_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("session_id"), Index("exercise_id")],
)
data class SessionPlanEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "session_id")
    val sessionId: Long,

    @ColumnInfo(name = "exercise_id")
    val exerciseId: String,

    /** Where this exercise sat in the day it was copied from, from 1. */
    @ColumnInfo(name = "order_index")
    val orderIndex: Int,

    /**
     * How many working sets the session was asked for, or null where it was asked for none.
     *
     * Copied with everything else, so a session trained in March still reads what it was asked
     * for however often the routine has changed since — which is the whole reason a plan is a
     * snapshot rather than a pointer.
     *
     * Null for every session logged before the app prescribed set counts at all. That is the
     * truth about them and not a gap to be filled: a number written over history would be a
     * prescription nobody made.
     */
    @ColumnInfo(name = "sets")
    val sets: Int? = null,
)

/** One planned exercise, with the bank row and the lifter's choices about it. */
data class SessionPlanWithExercise(
    @Embedded
    val entry: SessionPlanEntity,

    @Relation(entity = ExerciseEntity::class, parentColumn = "exercise_id", entityColumn = "id")
    val exercise: ExerciseWithPreference,
)
