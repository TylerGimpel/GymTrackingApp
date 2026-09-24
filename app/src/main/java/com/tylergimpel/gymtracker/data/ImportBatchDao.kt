package com.tylergimpel.gymtracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/** The record of each import, which every session it brought in points at. */
@Dao
interface ImportBatchDao {
    @Insert
    suspend fun insert(batch: ImportBatchEntity): Long

    @Query("SELECT * FROM import_batch ORDER BY imported_at DESC")
    suspend fun all(): List<ImportBatchEntity>
}
