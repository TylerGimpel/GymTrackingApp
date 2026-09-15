package com.tylergimpel.gymtracker.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tylergimpel.gymtracker.domain.Superset

/**
 * A superset as a row. [Superset] is where what one *is* is written down.
 *
 * A row of its own rather than a flag on the exercises, because a group has properties — an order,
 * an auto-jump, a rest — that belong to the group and not to any member. It also makes ungrouping
 * structural: members point *at* the group, so deleting it releases them and cannot reach a single
 * logged set.
 *
 * Belongs to the session, not to the routine. A superset is something a lifter forms and breaks in
 * the gym, unlike the plan, which history only ever snapshots.
 */
@Entity(
    tableName = "superset_group",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("session_id")],
)
data class SupersetGroupEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "session_id")
    val sessionId: Long,

    /** Where this group sits among the session's groups, from 1. */
    @ColumnInfo(name = "order_index")
    val orderIndex: Int,

    /** Whether the logging screen moves to the next member on its own after a set is logged. */
    @ColumnInfo(name = "auto_jump")
    val autoJump: Boolean,

    /** Rest after the whole group, in seconds, or null to rest for whatever the app would anyway. */
    @ColumnInfo(name = "rest_seconds")
    val restSeconds: Int?,
)

fun SupersetGroupEntity.toDomain() = Superset(
    id = id,
    orderIndex = orderIndex,
    autoJump = autoJump,
    restSeconds = restSeconds,
)
