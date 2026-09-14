package com.tylergimpel.gymtracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineDao {

    @Query("SELECT * FROM routine ORDER BY id LIMIT 1")
    fun observeRoutine(): Flow<RoutineEntity?>

    @Query("SELECT * FROM routine ORDER BY id LIMIT 1")
    suspend fun routine(): RoutineEntity?

    @Query("SELECT * FROM routine_day WHERE routine_id = :routineId ORDER BY order_index")
    fun observeDays(routineId: Long): Flow<List<RoutineDayEntity>>

    @Query("SELECT * FROM routine_day WHERE routine_id = :routineId ORDER BY order_index")
    suspend fun days(routineId: Long): List<RoutineDayEntity>

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
