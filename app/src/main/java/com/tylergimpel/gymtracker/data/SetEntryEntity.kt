package com.tylergimpel.gymtracker.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tylergimpel.gymtracker.domain.Kilograms
import com.tylergimpel.gymtracker.domain.LoggedSet

/**
 * One set, stored as a row.
 *
 * The row's [id] is not the set's identity — `(session_exercise_id, set_index)` is (ADR 0003). The
 * distinction is not academic: a set will later be recorded by more than one row, and anything that
 * counts rows instead of positions reports a drop set as three sets and silently blows through the
 * per-session volume cap.
 */
@Entity(
    tableName = "set_entry",
    foreignKeys = [
        ForeignKey(
            entity = SessionExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_exercise_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("session_exercise_id")],
)
data class SetEntryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "session_exercise_id")
    val sessionExerciseId: Long,

    /**
     * This set's position within its exercise. Gaps are expected and meaningful: deleting a set
     * leaves one rather than renumbering, because renumbering can corrupt a set being edited.
     */
    @ColumnInfo(name = "set_index")
    val setIndex: Int,

    /** Canonical kilograms, always. Display units never reach storage. */
    @ColumnInfo(name = "weight_kg")
    val weightKg: Double,

    @ColumnInfo(name = "reps")
    val reps: Int,

    @ColumnInfo(name = "note")
    val note: String?,
)

fun SetEntryEntity.toDomain() = LoggedSet(
    id = id,
    setIndex = setIndex,
    weight = Kilograms(weightKg),
    reps = reps,
    note = note,
)
