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

    /**
     * The session finished most recently, if there is one.
     *
     * Ordered by when it was finished rather than by the date it carries, because the point of it
     * is to be the session a lifter has just closed and is about to realise they misdated — and
     * refiling is exactly the operation that would move it out of its own way.
     */
    @Transaction
    @Query("SELECT * FROM session WHERE finished_at IS NOT NULL ORDER BY finished_at DESC LIMIT 1")
    fun observeLastFinished(): Flow<SessionWithContent?>

    /**
     * The most recent finished sessions, newest first.
     *
     * What the revealed-preference counts are derived from: an exercise avoided once is nothing,
     * and the same exercise unperformed across most of the sessions that asked for it is a signal.
     */
    @Transaction
    @Query("SELECT * FROM session WHERE finished_at IS NOT NULL ORDER BY finished_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SessionWithContent>>

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

    @Update
    suspend fun update(sessionExercise: SessionExerciseEntity)

    @Query("SELECT * FROM session_exercise WHERE id = :id")
    suspend fun sessionExercise(id: Long): SessionExerciseEntity?

    /** The exercise performed after [after] in this session, which is the one a group reaches for. */
    @Query(
        """
        SELECT * FROM session_exercise
        WHERE session_id = :sessionId AND order_index > :after
        ORDER BY order_index LIMIT 1
        """,
    )
    suspend fun exerciseAfter(sessionId: Long, after: Int): SessionExerciseEntity?

    @Insert
    suspend fun insert(superset: SupersetGroupEntity): Long

    @Query("SELECT COALESCE(MAX(order_index), 0) FROM superset_group WHERE session_id = :sessionId")
    suspend fun highestSupersetOrder(sessionId: Long): Int

    /**
     * Breaks a group up.
     *
     * Its members are released by the foreign key rather than by anything here, and nothing it
     * touches is a logged set: grouping was only ever a statement about rest.
     */
    @Query("DELETE FROM superset_group WHERE id = :supersetId")
    suspend fun deleteSuperset(supersetId: Long)

    /** Moves every member of one group into another, which is how two groups become one. */
    @Query("UPDATE session_exercise SET superset_group_id = :to WHERE superset_group_id = :from")
    suspend fun moveSupersetMembers(from: Long, to: Long)

    /** Whether a group is on by default when formed, and what it is set to afterwards. */
    @Query("UPDATE superset_group SET auto_jump = :autoJump WHERE id = :supersetId")
    suspend fun setAutoJump(supersetId: Long, autoJump: Boolean)

    @Insert
    suspend fun insert(planned: SessionPlanEntity): Long

    /** Whether this session has already been planned. A session is planned once, when it begins. */
    @Query("SELECT COUNT(*) FROM session_plan WHERE session_id = :sessionId")
    suspend fun plannedCount(sessionId: Long): Int

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
