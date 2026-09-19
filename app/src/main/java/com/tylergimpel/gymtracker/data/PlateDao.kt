package com.tylergimpel.gymtracker.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** The plates in the room, which the engine and the plate line both read. */
@Dao
interface PlateDao {

    @Query("SELECT * FROM plate_inventory ORDER BY weight_kg DESC")
    fun observeAll(): Flow<List<PlateInventoryEntity>>

    @Query("SELECT * FROM plate_inventory ORDER BY weight_kg DESC")
    suspend fun all(): List<PlateInventoryEntity>

    /** Records how many pairs of one weight there are, whether or not there were any before. */
    @Upsert
    suspend fun put(pair: PlateInventoryEntity)

    /** Says there are none of these. Distinct from a count of zero only in that nothing lists it. */
    @Query("DELETE FROM plate_inventory WHERE weight_kg = :weightKg")
    suspend fun remove(weightKg: Double)
}
