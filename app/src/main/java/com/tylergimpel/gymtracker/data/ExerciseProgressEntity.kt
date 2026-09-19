package com.tylergimpel.gymtracker.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.tylergimpel.gymtracker.domain.Anchor
import com.tylergimpel.gymtracker.domain.ExerciseProgress
import com.tylergimpel.gymtracker.domain.Kilograms
import com.tylergimpel.gymtracker.domain.RepRange

/**
 * What the app has worked out about one exercise, stored.
 *
 * A table of its own for the reason `exercise_preference` is one: `exercise` ships inside the APK
 * and a **Bank update** replaces those rows wholesale, so anything of the lifter's — or, here,
 * anything about the lifter — kept in them would be thrown away by the release that improved them
 * (`docs/adr/0009-user-choices-live-apart-from-the-shipped-bank.md`).
 *
 * Its own table rather than more columns on `exercise_preference`, because the two answer different
 * questions. That one holds what a lifter *chose*, where absence means "they have not said"; this
 * holds what the app *computed*, where absence means "it has nothing to go on yet". Sharing a row
 * would make clearing a choice and forgetting an estimate the same operation, and would have the
 * engine writing to the table whose whole point is that only the lifter writes to it.
 *
 * A row exists only once there is something to say, so the shipped asset carries the table empty.
 */
@Entity(
    tableName = "exercise_progress",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exercise_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ExerciseProgressEntity(
    @PrimaryKey
    @ColumnInfo(name = "exercise_id")
    val exerciseId: String,

    /**
     * The smoothed estimated one-rep maximum, in canonical kilograms and **signed** as a load is:
     * negative on an assisted machine, where help is weight taken off the lifter.
     */
    @ColumnInfo(name = "anchor_kg")
    val anchorKg: Double,

    /** The band this exercise is worked in. Both ends, because they do different jobs. */
    @ColumnInfo(name = "rep_range_bottom")
    val repRangeBottom: Int,

    @ColumnInfo(name = "rep_range_top")
    val repRangeTop: Int,

    /**
     * The load the exercise is currently being worked at — the anchor read back down to the bottom
     * of the range and rounded to something loadable.
     *
     * **Written and not yet read back.** Today every recommendation recomputes it from the log, so
     * this column is a record rather than a source; what will read it is the step that advances a
     * weight after the range has been beaten twice (#50), which has to start from the weight the
     * lifter has been working at rather than from a fresh answer to the formula. Carried ahead of
     * its reader on ADR 0007's precedent, which is how the prescription columns arrived too — the
     * alternative is a schema migration inside the ticket that needs it.
     */
    @ColumnInfo(name = "working_load_kg")
    val workingLoadKg: Double,
)

fun ExerciseProgressEntity.toDomain() = ExerciseProgress(
    anchor = Anchor(Kilograms(anchorKg)),
    repRange = RepRange(repRangeBottom, repRangeTop),
    workingLoad = Kilograms(workingLoadKg),
)

fun ExerciseProgress.toEntity(exerciseId: String) = ExerciseProgressEntity(
    exerciseId = exerciseId,
    anchorKg = anchor.estimated1rm.value,
    repRangeBottom = repRange.bottom,
    repRangeTop = repRange.top,
    workingLoadKg = workingLoad.value,
)
