package com.tylergimpel.gymtracker.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One exercise as it appears within one session, and the thing a set belongs to.
 *
 * The reference to the exercise is a real constraint, and deliberately `RESTRICT`: an exercise a
 * lifter has logged against must never be deleted out from under its history. A later ticket
 * tombstones bank exercises for exactly this reason; the database refuses it either way.
 */
@Entity(
    tableName = "session_exercise",
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
data class SessionExerciseEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "session_id")
    val sessionId: Long,

    @ColumnInfo(name = "exercise_id")
    val exerciseId: String,

    /**
     * Where this exercise sits among the others in the session.
     *
     * Nothing to do with a set's index: this orders exercises, that identifies sets.
     */
    @ColumnInfo(name = "order_index")
    val orderIndex: Int,
)
