package com.tylergimpel.gymtracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {

    /**
     * The bank as the app offers it, in the order a reader scanning it would expect.
     *
     * Tombstoned rows are left out: an exercise a bank update dropped stops being suggested. It is
     * still there, and every set logged against it still reads back, because history resolves an
     * exercise by its own id rather than by this list.
     */
    @Transaction
    @Query(
        """
        SELECT exercise.* FROM exercise
        LEFT JOIN exercise_preference ON exercise_preference.exercise_id = exercise.id
        WHERE exercise.tombstoned = 0
        ORDER BY COALESCE(exercise_preference.name, exercise.name) COLLATE NOCASE
        """,
    )
    fun observeAll(): Flow<List<ExerciseWithPreference>>

    /** Everything, tombstones included — what a bank update has to reason about. */
    @Transaction
    @Query("SELECT * FROM exercise")
    suspend fun all(): List<ExerciseWithPreference>

    /**
     * Records the unit an exercise is read in, leaving every other choice about it alone.
     *
     * One column per write. A whole-row upsert would quietly clear the lifter's name for the
     * exercise and the note they wrote on it, which is the opposite of what this table is for.
     */
    @Query(
        """
        INSERT INTO exercise_preference (exercise_id, display_unit)
        VALUES (:exerciseId, :displayUnit)
        ON CONFLICT(exercise_id) DO UPDATE SET display_unit = excluded.display_unit
        """,
    )
    suspend fun setDisplayUnit(exerciseId: String, displayUnit: String?)

    /** Records what the lifter calls an exercise, or clears it back to the bank's name. */
    @Query(
        """
        INSERT INTO exercise_preference (exercise_id, name) VALUES (:exerciseId, :name)
        ON CONFLICT(exercise_id) DO UPDATE SET name = excluded.name
        """,
    )
    suspend fun setName(exerciseId: String, name: String?)

    /** Records the standing note on an exercise, or clears it. */
    @Query(
        """
        INSERT INTO exercise_preference (exercise_id, note) VALUES (:exerciseId, :note)
        ON CONFLICT(exercise_id) DO UPDATE SET note = excluded.note
        """,
    )
    suspend fun setNote(exerciseId: String, note: String?)

    /** Records that the lifter never wants this exercise suggested again, or takes it back. */
    @Query(
        """
        INSERT INTO exercise_preference (exercise_id, disliked) VALUES (:exerciseId, :disliked)
        ON CONFLICT(exercise_id) DO UPDATE SET disliked = excluded.disliked
        """,
    )
    suspend fun setDisliked(exerciseId: String, disliked: Boolean)

    /** Records the step this exercise moves by, or clears it back to what the equipment says. */
    @Query(
        """
        INSERT INTO exercise_preference (exercise_id, increment_kg) VALUES (:exerciseId, :incrementKg)
        ON CONFLICT(exercise_id) DO UPDATE SET increment_kg = excluded.increment_kg
        """,
    )
    suspend fun setIncrement(exerciseId: String, incrementKg: Double?)

    /** Records how long the lifter rests on an exercise, or clears it back to the derived answer. */
    @Query(
        """
        INSERT INTO exercise_preference (exercise_id, rest_seconds) VALUES (:exerciseId, :seconds)
        ON CONFLICT(exercise_id) DO UPDATE SET rest_seconds = excluded.rest_seconds
        """,
    )
    suspend fun setRest(exerciseId: String, seconds: Int?)

    /** Records what the lifter decided an exercise advances by, or clears it back to the bank's. */
    @Query(
        """
        INSERT INTO exercise_preference (exercise_id, progression_model)
        VALUES (:exerciseId, :progressionModel)
        ON CONFLICT(exercise_id) DO UPDATE SET progression_model = excluded.progression_model
        """,
    )
    suspend fun setProgressionModel(exerciseId: String, progressionModel: String?)

    /**
     * Drops a preference row that no longer records a choice.
     *
     * Absence is this table's way of saying "nothing chosen" (ADR 0009). A row left behind by a
     * cleared name would say the lifter had decided something, and the next thing to read it would
     * have to know that three nulls mean no.
     */
    @Query(
        """
        DELETE FROM exercise_preference
        WHERE exercise_id = :exerciseId
          AND display_unit IS NULL AND name IS NULL AND note IS NULL
          AND progression_model IS NULL AND rest_seconds IS NULL AND disliked = 0
          AND increment_kg IS NULL
        """,
    )
    suspend fun forgetEmptyPreference(exerciseId: String)

    /** One exercise and whatever the lifter has decided about it. */
    @Transaction
    @Query("SELECT * FROM exercise WHERE id = :exerciseId")
    suspend fun byId(exerciseId: String): ExerciseWithPreference?

    /** What the app has worked out about an exercise, or null where it has nothing yet. */
    @Query("SELECT * FROM exercise_progress WHERE exercise_id = :exerciseId")
    suspend fun progress(exerciseId: String): ExerciseProgressEntity?

    /**
     * Records what the engine now makes of an exercise.
     *
     * A whole-row write, unlike the preferences above, and safely so: every column here is the
     * app's own and is written together from the same reading of the log. There is nothing of the
     * lifter's in this table to clear by accident.
     */
    @Upsert
    suspend fun upsertProgress(progress: ExerciseProgressEntity)

    /**
     * Forgets what the app had worked out about an exercise.
     *
     * For the case where the history it was derived from has gone — the last logged set corrected
     * away or deleted. Absence is this table's way of saying "nothing to go on", and a stale
     * estimate of training that no longer exists is worse than no estimate at all.
     */
    @Query("DELETE FROM exercise_progress WHERE exercise_id = :exerciseId")
    suspend fun forgetProgress(exerciseId: String)

    /** Adds a row: the lifter's own exercise, or one a bank update brought. */
    @Insert
    suspend fun insert(exercise: ExerciseEntity)

    /** Corrects a shipped row. Touches nothing in `exercise_preference`, which is the point. */
    @Update
    suspend fun update(exercise: ExerciseEntity)

    /** Stops an exercise being offered without touching a single set logged against it. */
    @Query("UPDATE exercise SET tombstoned = 1 WHERE id = :exerciseId")
    suspend fun tombstone(exerciseId: String)

    /**
     * Removes a shipped row an update dropped that nothing points at.
     *
     * Takes any preference on it with it, by cascade. That is right rather than unfortunate: a name
     * the lifter gave to an exercise that no longer exists is a name for nothing. It is also why
     * [referencedExerciseIds] has to be complete — a row something does point at must be
     * tombstoned instead, or this refuses and takes the whole update down with it.
     */
    @Query("DELETE FROM exercise WHERE id = :exerciseId")
    suspend fun deleteById(exerciseId: String)

    /**
     * Every exercise anything at all points at — what tombstoning turns on.
     *
     * All three, because all three hold the exercise with a real constraint: a logged set, a
     * session's plan, and a routine day. Missing any of them would have an update try to delete a
     * row the database refuses to delete, and the refusal takes the whole update down rather than
     * that one row.
     *
     * An exercise planned and never performed has no `session_exercise` row at all, which is
     * exactly the case the plan exists to make visible.
     *
     * Asked once for the whole bank rather than per row: an update reasons about 896 exercises, and
     * asking the database about each in turn is 896 questions with one answer.
     */
    @Query(
        """
        SELECT exercise_id FROM session_exercise
        UNION SELECT exercise_id FROM session_plan
        UNION SELECT exercise_id FROM routine_day_exercise
        """,
    )
    suspend fun referencedExerciseIds(): List<String>

    @Query("SELECT COUNT(*) FROM exercise")
    suspend fun count(): Int

    /**
     * The equipment values the bank actually uses, which is what the gym checklist offers.
     *
     * Read from the bank rather than restated in the app, so the closed vocabulary the build step
     * enforces stays the only place it is written down.
     */
    @Query("SELECT DISTINCT equipment FROM exercise WHERE equipment IS NOT NULL ORDER BY equipment")
    fun observeEquipment(): Flow<List<String>>
}
