package com.tylergimpel.gymtracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface BodyweightDao {

    /**
     * Every weigh-in, unordered.
     *
     * Ordering and "the latest" are the domain's, so that "most recent" has one meaning in one
     * place: it is the most recent *day*, which is not the row written last. One row per day makes
     * this a few hundred rows for a lifter who has weighed themselves daily for a year, so there is
     * nothing to page and nothing to push into SQL.
     */
    @Query("SELECT * FROM bodyweight_entry")
    fun observeAll(): Flow<List<BodyweightEntryEntity>>

    @Query("SELECT * FROM bodyweight_entry")
    suspend fun all(): List<BodyweightEntryEntity>

    /** What a day already holds, which is what makes weighing again on it a correction. */
    @Query("SELECT * FROM bodyweight_entry WHERE date = :date")
    suspend fun entryOn(date: LocalDate): BodyweightEntryEntity?

    @Insert
    suspend fun insert(entry: BodyweightEntryEntity): Long

    @Update
    suspend fun update(entry: BodyweightEntryEntity)

    @Query("DELETE FROM bodyweight_entry WHERE id = :id")
    suspend fun delete(id: Long)
}
