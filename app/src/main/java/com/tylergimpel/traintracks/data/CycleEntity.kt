package com.tylergimpel.traintracks.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * One full pass through every routine day.
 *
 * Cycle state belongs to the routine and dies with it — revising a routine ends the cycle anyway,
 * so cascading is correct here in a way it never is for a session.
 */
@Entity(
    tableName = "cycle",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routine_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("routine_id")],
)
data class CycleEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "routine_id")
    val routineId: Long,

    /** Which pass through the routine this is, from 1. */
    @ColumnInfo(name = "ordinal")
    val ordinal: Int,

    /**
     * When the cycle opened. Recorded for history only — the cursor never reads it, because a
     * cycle has no duration and a layoff must cost nothing.
     */
    @ColumnInfo(name = "started_at")
    val startedAt: Instant,
)

/**
 * A routine day trained within a cycle.
 *
 * Linked to the session that realised it, so deleting that session makes the day undone again
 * rather than leaving the cycle claiming training that no longer exists. This is cycle state
 * pointing at history, which is safe; history never points back at the routine.
 */
@Entity(
    tableName = "cycle_completion",
    foreignKeys = [
        ForeignKey(
            entity = CycleEntity::class,
            parentColumns = ["id"],
            childColumns = ["cycle_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = RoutineDayEntity::class,
            parentColumns = ["id"],
            childColumns = ["routine_day_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("cycle_id"), Index("routine_day_id"), Index("session_id")],
)
data class CycleCompletionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "cycle_id")
    val cycleId: Long,

    @ColumnInfo(name = "routine_day_id")
    val routineDayId: Long,

    @ColumnInfo(name = "session_id")
    val sessionId: Long,
)

/**
 * A routine day marked as dealt with without a session.
 *
 * Its own table rather than an outcome column on a completion, because a skip is distinct from a
 * completion in history: it is how history avoids ever claiming training that did not happen.
 */
@Entity(
    tableName = "skip",
    foreignKeys = [
        ForeignKey(
            entity = CycleEntity::class,
            parentColumns = ["id"],
            childColumns = ["cycle_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = RoutineDayEntity::class,
            parentColumns = ["id"],
            childColumns = ["routine_day_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("cycle_id"), Index("routine_day_id")],
)
data class SkipEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "cycle_id")
    val cycleId: Long,

    @ColumnInfo(name = "routine_day_id")
    val routineDayId: Long,

    @ColumnInfo(name = "recorded_at")
    val recordedAt: Instant,
)
