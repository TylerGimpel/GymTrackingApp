package com.tylergimpel.gymtracker.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tylergimpel.gymtracker.domain.Exercise
import com.tylergimpel.gymtracker.domain.LoggingType

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

    /** `upstream` or `authored` — which source shipped the row, not whether a user created it. */
    @ColumnInfo(name = "source")
    val source: String,
)

fun ExerciseEntity.toDomain() = Exercise(
    id = id,
    name = name,
    primaryMuscles = primaryMuscles,
    secondaryMuscles = secondaryMuscles,
    equipment = equipment,
    mechanic = mechanic,
    force = force,
    category = category,
    level = level,
    loggingType = LoggingType.fromStored(loggingType),
    unilateral = unilateral,
)
