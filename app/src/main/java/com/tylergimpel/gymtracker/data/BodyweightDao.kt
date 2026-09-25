package com.tylergimpel.gymtracker.data

import java.time.LocalDate
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BodyweightDao {

    /**
     * Every weigh-in, unordered.
     *
     * Ordering and "the latest" are the domain's, so that "most recent" has one meaning in one
     * place: it is the latest reading on the most recent day, which is not the row written last.
     * A lifter weighing themselves daily writes a few hundred rows a year, so there is nothing to
     * page and nothing to push into SQL.
     */
    @Query("SELECT * FROM bodyweight_entry")
    fun observeAll(): Flow<List<BodyweightEntryEntity>>

    /** The days between [from] and [to] with a weigh-in on them, for the calendar (#118). */
    @Query("SELECT DISTINCT date FROM bodyweight_entry WHERE date BETWEEN :from AND :to")
    fun observeDaysBetween(from: LocalDate, to: LocalDate): Flow<List<LocalDate>>

    /** The day of the first weigh-in there is, or null with none. */
    @Query("SELECT MIN(date) FROM bodyweight_entry")
    suspend fun firstDay(): LocalDate?

    @Query("SELECT * FROM bodyweight_entry")
    suspend fun all(): List<BodyweightEntryEntity>

    /** One weigh-in, to correct what it holds without disturbing when it was taken. */
    @Query("SELECT * FROM bodyweight_entry WHERE id = :id")
    suspend fun entry(id: Long): BodyweightEntryEntity?

    @Insert
    suspend fun insert(entry: BodyweightEntryEntity): Long

    @Update
    suspend fun update(entry: BodyweightEntryEntity)

    @Query("DELETE FROM bodyweight_entry WHERE id = :id")
    suspend fun delete(id: Long)
}
