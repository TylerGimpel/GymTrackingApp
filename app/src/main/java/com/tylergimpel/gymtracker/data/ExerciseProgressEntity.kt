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
     * The load the exercise is currently being worked at.
     *
     * **Nothing reads this.** The weight a lifter is working at is the one the app last asked for,
     * the log records every prescription, and the engine recovers it from there and moves it with
     * the ladder rather than trusting a column that could have drifted (ADR 0010). It is written
     * because spec #25 asks the engine to hold the working weight as stored state, and it is the
     * one number here that a reader would otherwise have to fold a whole history to get.
     *
     * If the analytics arrive (#28) and still do not want it, delete it rather than keep inventing
     * readers for it.
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
