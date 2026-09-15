package com.tylergimpel.gymtracker.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.tylergimpel.gymtracker.domain.WeightUnit

/**
 * What a lifter has decided about one exercise, as opposed to what the bank says about it.
 *
 * A table of its own rather than columns on `exercise`, which ships inside the APK and is replaced
 * wholesale by a bank update — see `docs/adr/0009-user-choices-live-apart-from-the-shipped-bank.md`.
 * A row exists only where something has actually been chosen, so absence is the answer "nothing
 * yet" rather than a value that has to be maintained for 896 exercises.
 */
@Entity(
    tableName = "exercise_preference",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exercise_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ExercisePreferenceEntity(
    @PrimaryKey
    @ColumnInfo(name = "exercise_id")
    val exerciseId: String,

    /** `kg` or `lb` — how this exercise is read. Never what its weights are stored in. */
    @ColumnInfo(name = "display_unit")
    val displayUnit: String,
) {
    companion object {
        fun of(exerciseId: String, displayUnit: WeightUnit) =
            ExercisePreferenceEntity(exerciseId = exerciseId, displayUnit = displayUnit.stored)
    }
}
