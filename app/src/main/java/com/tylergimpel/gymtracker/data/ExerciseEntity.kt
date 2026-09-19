package com.tylergimpel.gymtracker.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tylergimpel.gymtracker.domain.Exercise
import com.tylergimpel.gymtracker.domain.ExerciseSource
import com.tylergimpel.gymtracker.domain.LoggingType
import com.tylergimpel.gymtracker.domain.ProgressionModel
import com.tylergimpel.gymtracker.domain.WeightUnit
import java.time.Duration
import com.tylergimpel.gymtracker.domain.Kilograms

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

    /**
     * What this exercise advances by, derived at build time from what it measures.
     *
     * On the bank row rather than in `exercise_preference`, because it is the bank's guess: what the
     * lifter chose instead lives apart, where a **Bank update** cannot reach it (ADR 0009).
     */
    @ColumnInfo(name = "progression_model", defaultValue = ExerciseEntity.DEFAULT_PROGRESSION_MODEL)
    val progressionModel: String = DEFAULT_PROGRESSION_MODEL,

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
) {
    companion object {
        /**
         * What a row migrated from a version with no progression model takes.
         *
         * Declared here as well as in the migration because Room compares defaults when it validates
         * a migrated schema: an `ALTER` whose default differs from the entity's fails the identity
         * check on the next open, which is a crash rather than a compile error.
         */
        const val DEFAULT_PROGRESSION_MODEL = "double_progression"
    }
}

/**
 * The bank's own fields together with whatever the lifter decided about them.
 *
 * [preference] is passed in rather than read from this row: a rename, a note, a unit and a
 * progression model are choices the lifter made, held in `exercise_preference` so a bank update
 * cannot overwrite them (ADR 0009).
 *
 * [lifterUnit] is what this exercise reads in when the lifter has said nothing about it — their own
 * unit rather than kilograms, because someone who reads in pounds reads a machine they have never
 * touched in pounds too. A preference row still wins: that is the machine's own label.
 */
fun ExerciseEntity.toDomain(
    preference: ExercisePreferenceEntity?,
    lifterUnit: WeightUnit = WeightUnit.DEFAULT,
) = Exercise(
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
    bankProgressionModel = ProgressionModel.fromStored(progressionModel)
        ?: ProgressionModel.of(LoggingType.fromStored(loggingType)),
    chosenProgressionModel = ProgressionModel.fromStored(preference?.progressionModel),
    unilateral = unilateral,
    displayUnit = WeightUnit.fromStored(preference?.displayUnit) ?: lifterUnit,
    chosenRest = preference?.restSeconds?.let { Duration.ofSeconds(it.toLong()) },
    isDisliked = preference?.disliked == true,
    chosenIncrement = preference?.incrementKg?.let(::Kilograms),
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
    progressionModel = bankProgressionModel.stored,
    unilateral = unilateral,
    source = source.stored,
    tombstoned = isTombstoned,
)
