package com.tylergimpel.gymtracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineDao {

    @Query("SELECT * FROM routine ORDER BY id LIMIT 1")
    fun observeRoutine(): Flow<RoutineEntity?>

    @Query("SELECT * FROM routine ORDER BY id LIMIT 1")
    suspend fun routine(): RoutineEntity?

    @Transaction
    @Query("SELECT * FROM routine_day WHERE routine_id = :routineId ORDER BY order_index")
    fun observeDays(routineId: Long): Flow<List<RoutineDayWithExercises>>

    @Query("SELECT * FROM routine_day WHERE routine_id = :routineId ORDER BY order_index")
    suspend fun days(routineId: Long): List<RoutineDayEntity>

    /**
     * The days with what they hold, for building a domain routine.
     *
     * Kept apart from [days], which answers the structural questions — reordering, renumbering,
     * renaming — and has no use for a day's contents.
     */
    @Transaction
    @Query("SELECT * FROM routine_day WHERE routine_id = :routineId ORDER BY order_index")
    suspend fun daysWithExercises(routineId: Long): List<RoutineDayWithExercises>

    @Insert
    suspend fun insert(routine: RoutineEntity): Long

    @Update
    suspend fun update(routine: RoutineEntity)

    @Delete
    suspend fun delete(routine: RoutineEntity)

    @Insert
    suspend fun insert(day: RoutineDayEntity): Long

    @Update
    suspend fun update(day: RoutineDayEntity)

    @Query("DELETE FROM routine_day WHERE id = :dayId")
    suspend fun deleteDay(dayId: Long)

    /**
     * Adds an exercise to a day, or does nothing if the day already holds it.
     *
     * The unique index is what actually enforces "once per day"; ignoring the conflict is how that
     * rule is obeyed rather than re-checked.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(planned: RoutineDayExerciseEntity): Long

    @Query("SELECT COALESCE(MAX(order_index), 0) FROM routine_day_exercise WHERE routine_day_id = :dayId")
    suspend fun highestDayExerciseOrder(dayId: Long): Int

    @Query(
        """
        DELETE FROM routine_day_exercise
        WHERE routine_day_id = :dayId AND exercise_id = :exerciseId
        """,
    )
    suspend fun deleteDayExercise(dayId: Long, exerciseId: String)

    @Query("SELECT * FROM routine_day_exercise WHERE routine_day_id = :dayId ORDER BY order_index")
    suspend fun dayExercises(dayId: Long): List<RoutineDayExerciseEntity>

    /** Marks one of a day's exercises as one to keep, or stops keeping it. */
    @Query(
        """
        UPDATE routine_day_exercise SET pinned = :pinned
        WHERE routine_day_id = :dayId AND exercise_id = :exerciseId
        """,
    )
    suspend fun setPinned(dayId: Long, exerciseId: String, pinned: Boolean)

    /** Everything a day holds that the lifter asked to keep. */
    @Query("SELECT * FROM routine_day_exercise WHERE routine_day_id = :dayId AND pinned = 1")
    suspend fun pinnedIn(dayId: Long): List<RoutineDayExerciseEntity>

    /** Clears what a day holds, so a regeneration can put something else there. */
    @Query("DELETE FROM routine_day_exercise WHERE routine_day_id = :dayId")
    suspend fun clearDay(dayId: Long)

    @Insert
    suspend fun insertUndo(row: RoutineDayExerciseUndoEntity): Long

    @Query("SELECT * FROM routine_day_exercise_undo ORDER BY routine_day_id, order_index")
    suspend fun undoRows(): List<RoutineDayExerciseUndoEntity>

    /**
     * Forgets the step back.
     *
     * Called when one is taken, when a new one replaces it, and when a session is logged — after
     * which the routine has been trained and the thing undo would restore is no longer what the
     * lifter was doing.
     */
    @Query("DELETE FROM routine_day_exercise_undo")
    suspend fun clearUndo()

    @Query("SELECT COALESCE(MAX(order_index), 0) FROM routine_day WHERE routine_id = :routineId")
    suspend fun highestDayOrder(routineId: Long): Int

    // --- cycle state -------------------------------------------------------------------------

    /** The cycle currently in progress, which is simply the latest one opened. */
    @Query("SELECT * FROM cycle WHERE routine_id = :routineId ORDER BY ordinal DESC LIMIT 1")
    fun observeLatestCycle(routineId: Long): Flow<CycleEntity?>

    @Query("SELECT * FROM cycle WHERE routine_id = :routineId ORDER BY ordinal DESC LIMIT 1")
    suspend fun latestCycle(routineId: Long): CycleEntity?

    @Insert
    suspend fun insert(cycle: CycleEntity): Long

    @Query("SELECT * FROM cycle_completion WHERE cycle_id = :cycleId")
    fun observeCompletions(cycleId: Long): Flow<List<CycleCompletionEntity>>

    @Query("SELECT * FROM cycle_completion WHERE cycle_id = :cycleId")
    suspend fun completions(cycleId: Long): List<CycleCompletionEntity>

    @Insert
    suspend fun insert(completion: CycleCompletionEntity): Long

    @Query("SELECT * FROM skip WHERE cycle_id = :cycleId")
    fun observeSkips(cycleId: Long): Flow<List<SkipEntity>>

    @Query("SELECT * FROM skip WHERE cycle_id = :cycleId")
    suspend fun skips(cycleId: Long): List<SkipEntity>

    @Insert
    suspend fun insert(skip: SkipEntity): Long
}
