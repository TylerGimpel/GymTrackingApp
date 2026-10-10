package com.tylergimpel.traintracks.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tylergimpel.traintracks.domain.Drop
import com.tylergimpel.traintracks.domain.Kilograms

/**
 * One drop continuing a set, stored as a child row.
 *
 * Deliberately not "every set has segments": over 99% of sets have no drops, and the first effort
 * stays inline on the set because those are the values progression reads. The fatigued later drops
 * must never feed an estimated one-rep max (ADR 0003).
 */
@Entity(
    tableName = "drop_segment",
    foreignKeys = [
        ForeignKey(
            entity = SetEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["set_entry_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("set_entry_id")],
)
data class DropSegmentEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "set_entry_id")
    val setEntryId: Long,

    /** Where this drop falls in the sequence, from 1. */
    @ColumnInfo(name = "order_index")
    val orderIndex: Int,

    @ColumnInfo(name = "weight_kg")
    val weightKg: Double,

    @ColumnInfo(name = "reps")
    val reps: Int,
)

fun DropSegmentEntity.toDomain() = Drop(
    id = id,
    orderIndex = orderIndex,
    weight = Kilograms(weightKg),
    reps = reps,
)
