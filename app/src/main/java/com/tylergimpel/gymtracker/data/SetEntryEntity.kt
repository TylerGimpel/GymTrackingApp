package com.tylergimpel.gymtracker.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tylergimpel.gymtracker.domain.Performance
import com.tylergimpel.gymtracker.domain.Prescription
import com.tylergimpel.gymtracker.domain.SetMeasurements
import com.tylergimpel.gymtracker.domain.SetType

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

    /**
     * What the set was for. Stored explicitly and never inferred: position and load cannot tell a
     * back-off set from a warm-up, and guessing corrupts every volume figure downstream.
     */
    @ColumnInfo(name = "type", defaultValue = SetEntryEntity.DEFAULT_TYPE)
    val type: String,

    /**
     * Canonical kilograms, always. Display units never reach storage.
     *
     * Null for a set its exercise does not weigh — a plank is a length of time and nothing else.
     * Null rather than zero, because zero is a weight: an unloaded bar and a bodyweight dip both
     * genuinely weigh nothing added, and a progression engine reading a stand-in zero as real would
     * be reading a set nobody performed.
     */
    @ColumnInfo(name = "weight_kg")
    val weightKg: Double? = null,

    /** Null for a set its exercise does not count, for the same reason as [weightKg]. */
    @ColumnInfo(name = "reps")
    val reps: Int? = null,

    /** Seconds held or run, for a set measured in time. Null for one measured in weight and reps. */
    @ColumnInfo(name = "duration_seconds")
    val durationSeconds: Int? = null,

    /**
     * A belt, or a machine's help, in canonical kilograms — and **signed**.
     *
     * Positive is weight hung off a lifter, negative is weight taken off them. One column because
     * they are one measurement read two ways, and signed because that is what makes the row say
     * which it was without asking the exercise: an exercise retagged by a bank update must not be
     * able to turn somebody's assisted sets into weighted ones. Null for a set that had neither.
     */
    @ColumnInfo(name = "added_weight_kg")
    val addedWeightKg: Double? = null,

    /**
     * Canonical metres, always — the same rule the weight follows.
     *
     * A treadmill labelled in miles and a track measured in kilometres are the same running, so
     * what a lifter reads it in never reaches storage. Null for a set that covered no ground.
     */
    @ColumnInfo(name = "distance_m")
    val distanceM: Double? = null,

    @ColumnInfo(name = "note")
    val note: String?,

    /**
     * Which side this row records, or null when the set was logged as a single entry.
     *
     * Two rows sharing one `set_index` and discriminated by side are still one set. Nothing counts
     * rows, so adding the second one cannot inflate a volume figure.
     */
    @ColumnInfo(name = "side")
    val side: String?,

    /**
     * The unit this set was read in, or null where it reads as its exercise does.
     *
     * Null rather than a copy of the exercise's unit at the time, so that moving an exercise
     * between units moves its whole history's display with it (ADR 0009). Display only either way:
     * [weightKg] is what was lifted and nothing here touches it.
     */
    @ColumnInfo(name = "display_unit")
    val displayUnit: String?,

    /**
     * What the app told the lifter to do for this set, where anything told them.
     *
     * Recorded beside what they did, which is the only way overriding a recommendation and falling
     * short of one can ever be told apart — they are identical in a log that records only the
     * actual. Nothing fills these until the recommendation engine arrives; they are here so it does
     * not have to migrate the log on its way in (ADR 0007's precedent).
     *
     * On the row like the type and the unit are, and belonging to the set: every row of a per-side
     * set carries the one prescription that set was given.
     */
    @ColumnInfo(name = "recommended_weight_kg")
    val recommendedWeightKg: Double? = null,

    @ColumnInfo(name = "recommended_reps")
    val recommendedReps: Int? = null,

    @ColumnInfo(name = "recommended_rest_seconds")
    val recommendedRestSeconds: Int? = null,

    /** How long the lifter actually rested after this set. */
    @ColumnInfo(name = "actual_rest_seconds")
    val actualRestSeconds: Int? = null,

    /** How hard it felt, 1 to 10. The lifter's own reading rather than anything derived. */
    @ColumnInfo(name = "rpe")
    val rpe: Double? = null,

    /** Beats per minute, typed in: this app integrates with no wearable and never will. */
    @ColumnInfo(name = "heart_rate")
    val heartRate: Int? = null,

) {
    companion object {
        /**
         * The value existing rows take when the type column arrives.
         *
         * Declared on the entity as well as in the migration so the two agree: a migrated table
         * whose default differs from Room's expectation fails the identity check on next open.
         * Every set logged before types existed was intended as work, so `working` is what they
         * were — this is not a guess standing in for a missing answer.
         */
        const val DEFAULT_TYPE = "working"
    }
}

/**
 * The same row, carrying what the lifter measured about the set.
 *
 * Theirs rather than the app's: an effort rating is a judgement nobody else can make for them, and
 * a heart rate is typed in because this project integrates with no wearable.
 */
fun SetEntryEntity.measured(measurements: SetMeasurements): SetEntryEntity = copy(
    rpe = measurements.rpe,
    heartRate = measurements.heartRate,
    actualRestSeconds = measurements.restTaken?.seconds?.toInt(),
)

/**
 * The same row, carrying what the app asked of this set.
 *
 * What was asked rather than what was accepted: a lifter who was offered 60 and loaded 65 has
 * overridden a recommendation, and that only reads as a decision rather than as a miss because the
 * offer is written down beside what happened.
 */
fun SetEntryEntity.prescribed(prescription: Prescription): SetEntryEntity = copy(
    recommendedWeightKg = prescription.load?.value,
    recommendedReps = prescription.reps,
    recommendedRestSeconds = prescription.rest?.seconds?.toInt(),
)

/**
 * The same row, recording [performance] and nothing else.
 *
 * Every shape's columns are cleared and only its own filled, so a set corrected from a weight and
 * reps to a duration cannot keep a weight it no longer has. That is one rule in one place rather
 * than a nulling-out every caller has to remember — and the reason every measurement column
 * defaults to null: a row is built empty and then told what it records, never filled field by field.
 */
fun SetEntryEntity.recording(performance: Performance): SetEntryEntity = when (performance) {
    is Performance.WeightAndReps -> copy(
        weightKg = performance.weight.value,
        reps = performance.reps,
        addedWeightKg = null,
        durationSeconds = null,
        distanceM = null,
    )

    is Performance.BodyweightReps -> copy(
        weightKg = null,
        reps = performance.reps,
        addedWeightKg = performance.addedWeight?.value,
        durationSeconds = null,
        distanceM = null,
    )

    is Performance.Assisted -> copy(
        weightKg = null,
        reps = performance.reps,
        // Negative: help is weight taken off the lifter.
        addedWeightKg = -performance.assistance.value,
        durationSeconds = null,
        distanceM = null,
    )

    is Performance.Timed -> copy(
        weightKg = null,
        reps = null,
        addedWeightKg = null,
        durationSeconds = performance.duration.seconds.toInt(),
        distanceM = null,
    )

    is Performance.DistanceAndTime -> copy(
        weightKg = null,
        reps = null,
        addedWeightKg = null,
        durationSeconds = performance.duration.seconds.toInt(),
        distanceM = performance.distance.value,
    )
}
