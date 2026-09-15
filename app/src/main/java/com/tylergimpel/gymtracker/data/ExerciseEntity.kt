package com.tylergimpel.gymtracker.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tylergimpel.gymtracker.domain.Exercise
import com.tylergimpel.gymtracker.domain.ExerciseSource
import com.tylergimpel.gymtracker.domain.LoggingType
import com.tylergimpel.gymtracker.domain.WeightUnit

/**
 * A row of the exercise bank as it ships in the prepopulated asset database.
 *
 * Every column here is written at build time by `GenerateExerciseBankTask`, which reads the column
 * list out of Room's exported schema — so adding a field to this entity fails the build until the
 * generator is told what to put in it, rather than silently shipping nulls.
 */
@Entity(tableName = "exercise")
data class ExerciseEntity(
    /**
     * The upstream string id for a shipped row. User-authored exercises get a UUID in a separate id
     * space, so a bank update can match on id without ever touching a row the user created.
     */
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "primary_muscles")
    val primaryMuscles: List<String>,

    @ColumnInfo(name = "secondary_muscles")
    val secondaryMuscles: List<String>,

    @ColumnInfo(name = "equipment")
    val equipment: String?,

    @ColumnInfo(name = "mechanic")
    val mechanic: String?,

    @ColumnInfo(name = "force")
    val force: String?,

    @ColumnInfo(name = "category")
    val category: String,

    @ColumnInfo(name = "level")
    val level: String,

    @ColumnInfo(name = "logging_type")
    val loggingType: String,

    @ColumnInfo(name = "unilateral")
    val unilateral: Boolean,

    /**
     * `upstream`, `authored` or `user`.
     *
     * The first two ship inside the APK and are a bank update's to correct; the third is the
     * lifter's own and is never an update's to touch.
     */
    @ColumnInfo(name = "source")
    val source: String,

    /**
     * Set when a bank update dropped this exercise while a lifter had already trained it.
     *
     * The alternative is deleting a row that logged sets point at, which a training log does not
     * get to do. Tombstoned rows stop being offered and stay readable for ever.
     */
    @ColumnInfo(name = "tombstoned", defaultValue = "0")
    val tombstoned: Boolean = false,
)

/**
 * The bank's own fields together with whatever the lifter decided about them.
 *
 * [preference] is passed in rather than read from this row: a rename, a note and a unit are choices
 * the lifter made, held in `exercise_preference` so a bank update cannot overwrite them (ADR 0009).
 */
fun ExerciseEntity.toDomain(preference: ExercisePreferenceEntity?) = Exercise(
    id = id,
    bankName = name,
    primaryMuscles = primaryMuscles,
    secondaryMuscles = secondaryMuscles,
    equipment = equipment,
    mechanic = mechanic,
    force = force,
    category = category,
    level = level,
    loggingType = LoggingType.fromStored(loggingType),
    unilateral = unilateral,
    displayUnit = WeightUnit.fromStored(preference?.displayUnit) ?: WeightUnit.DEFAULT,
    userName = preference?.name,
    note = preference?.note,
    source = ExerciseSource.fromStored(source),
    isTombstoned = tombstoned,
)

/** The row a lifter's own exercise is stored as, with a UUID in its own id space. */
fun Exercise.toEntity() = ExerciseEntity(
    id = id,
    name = bankName,
    primaryMuscles = primaryMuscles,
    secondaryMuscles = secondaryMuscles,
    equipment = equipment,
    mechanic = mechanic,
    force = force,
    category = category,
    level = level,
    loggingType = loggingType.stored,
    unilateral = unilateral,
    source = source.stored,
    tombstoned = isTombstoned,
)
