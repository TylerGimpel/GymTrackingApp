package com.tylergimpel.gymtracker.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tylergimpel.gymtracker.domain.Performance
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
 * The same row, recording [performance] and nothing else.
 *
 * Every shape's columns are cleared and only its own filled, so a set corrected from a weight and
 * reps to a duration cannot keep a weight it no longer has. That is one rule in one place rather
 * than a nulling-out every caller has to remember — and the reason the three measurement columns
 * default to null: a row is built empty and then told what it records, never filled field by field.
 */
fun SetEntryEntity.recording(performance: Performance): SetEntryEntity = when (performance) {
    is Performance.WeightAndReps -> copy(
        weightKg = performance.weight.value,
        reps = performance.reps,
        durationSeconds = null,
        distanceM = null,
    )

    is Performance.Timed -> copy(
        weightKg = null,
        reps = null,
        durationSeconds = performance.duration.seconds.toInt(),
        distanceM = null,
    )

    is Performance.DistanceAndTime -> copy(
        weightKg = null,
        reps = null,
        durationSeconds = performance.duration.seconds.toInt(),
        distanceM = performance.distance.value,
    )
}
