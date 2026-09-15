package com.tylergimpel.gymtracker.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {

    /** The whole bank, in the order a reader scanning it would expect. */
    @Transaction
    @Query("SELECT * FROM exercise ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ExerciseWithPreference>>

    /**
     * Records what a lifter has decided about an exercise, replacing whatever they decided before.
     *
     * An upsert because a preference is a single standing answer rather than a history: a lifter
     * moving an exercise back to kilograms is correcting the answer, not adding to it.
     */
    @Upsert
    suspend fun save(preference: ExercisePreferenceEntity)

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
