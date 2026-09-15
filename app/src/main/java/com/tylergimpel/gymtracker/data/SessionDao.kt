package com.tylergimpel.gymtracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    /** The session still being logged, if there is one. Unfinished is what makes it current. */
    @Query("SELECT * FROM session WHERE finished_at IS NULL ORDER BY started_at DESC LIMIT 1")
    fun observeActiveSession(): Flow<SessionEntity?>

    @Query("SELECT * FROM session WHERE finished_at IS NULL ORDER BY started_at DESC LIMIT 1")
    suspend fun activeSession(): SessionEntity?

    @Transaction
    @Query("SELECT * FROM session WHERE id = :sessionId")
    fun observeContent(sessionId: Long): Flow<SessionWithContent?>

    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Update
    suspend fun update(session: SessionEntity)

    @Query("SELECT * FROM session WHERE id = :sessionId")
    suspend fun session(sessionId: Long): SessionEntity?

    /**
     * The most recent entry for this exercise in this session, if any.
     *
     * An exercise may legitimately appear twice in one session, so new sets join the latest entry
     * rather than the first.
     */
    @Query(
        """
        SELECT * FROM session_exercise
        WHERE session_id = :sessionId AND exercise_id = :exerciseId
        ORDER BY order_index DESC LIMIT 1
        """,
    )
    suspend fun latestSessionExerciseFor(sessionId: Long, exerciseId: String): SessionExerciseEntity?

    @Query("SELECT COALESCE(MAX(order_index), 0) FROM session_exercise WHERE session_id = :sessionId")
    suspend fun highestExerciseOrder(sessionId: Long): Int

    @Insert
    suspend fun insert(sessionExercise: SessionExerciseEntity): Long

    @Transaction
    @Query("SELECT * FROM set_entry WHERE session_exercise_id = :sessionExerciseId ORDER BY set_index")
    suspend fun setsFor(sessionExerciseId: Long): List<SetEntryWithDrops>

    @Query("SELECT * FROM set_entry WHERE id = :setId")
    suspend fun setEntry(setId: Long): SetEntryEntity?

    @Insert
    suspend fun insert(setEntry: SetEntryEntity): Long

    @Update
    suspend fun update(setEntry: SetEntryEntity)

    /**
     * Retypes every row of one set.
     *
     * A type belongs to the set, not to the row: leaving one side of a per-side set as working and
     * the other as a warm-up would make whether it counts depend on which row is read first.
     */
    @Query(
        """
        UPDATE set_entry SET type = :type
        WHERE session_exercise_id = :sessionExerciseId AND set_index = :setIndex
        """,
    )
    suspend fun retypeSet(sessionExerciseId: Long, setIndex: Int, type: String)

    /**
     * Changes which unit one set reads in, every row of it together.
     *
     * The same reasoning as the type: a per-side set whose two rows disagreed about the unit would
     * show one arm in kilograms and the other in pounds.
     */
    @Query(
        """
        UPDATE set_entry SET display_unit = :displayUnit
        WHERE session_exercise_id = :sessionExerciseId AND set_index = :setIndex
        """,
    )
    suspend fun redisplaySet(sessionExerciseId: Long, setIndex: Int, displayUnit: String?)

    /**
     * Drops the unit override from every set of [exerciseId] that had overridden to [displayUnit].
     *
     * Run when an exercise adopts a unit some of its sets had already departed to: those sets are
     * no longer departures from anything, and one that kept its override would be frozen in that
     * unit the next time the exercise moved (ADR 0009).
     */
    @Query(
        """
        UPDATE set_entry SET display_unit = NULL
        WHERE display_unit = :displayUnit AND session_exercise_id IN (
            SELECT id FROM session_exercise WHERE exercise_id = :exerciseId
        )
        """,
    )
    suspend fun clearDisplayUnitOverrides(exerciseId: String, displayUnit: String)

    /**
     * Removes every row of one set. No renumbering: the index it held stays empty, because shifting
     * the sets around it can corrupt one the lifter is editing at that moment.
     */
    @Query("DELETE FROM set_entry WHERE session_exercise_id = :sessionExerciseId AND set_index = :setIndex")
    suspend fun deleteSetAt(sessionExerciseId: Long, setIndex: Int)

    @Query("SELECT COALESCE(MAX(order_index), 0) FROM drop_segment WHERE set_entry_id = :setEntryId")
    suspend fun highestDropOrder(setEntryId: Long): Int

    @Insert
    suspend fun insert(drop: DropSegmentEntity): Long

    @Query("DELETE FROM drop_segment WHERE id = :dropId")
    suspend fun deleteDrop(dropId: Long)
}
