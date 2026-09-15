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
        // `SET_NULL` is what makes ungrouping structural: deleting the group releases its members
        // and can reach nothing else. Nothing has to remember to tidy up after it.
        ForeignKey(
            entity = SupersetGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["superset_group_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("session_id"), Index("exercise_id"), Index("superset_group_id")],
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

    /**
     * The superset this exercise was performed as part of, or null where it was performed alone.
     *
     * Null is the ordinary case, and is also what a member becomes the moment its group is deleted.
     */
    @ColumnInfo(name = "superset_group_id")
    val supersetGroupId: Long? = null,
)
