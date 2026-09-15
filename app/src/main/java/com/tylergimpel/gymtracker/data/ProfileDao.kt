package com.tylergimpel.gymtracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {

    /** Null until the lifter has chosen something, which is the ordinary state on first launch. */
    @Query("SELECT * FROM profile WHERE id = ${ProfileEntity.SINGLETON_ID}")
    fun observe(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE id = ${ProfileEntity.SINGLETON_ID}")
    suspend fun profile(): ProfileEntity?

    /**
     * Writes the one row, whether or not it was there.
     *
     * Replacing is safe here in a way it would not be on a table with children: the id is fixed and
     * known, nothing references this row, and the alternative — read, branch, insert or update — is
     * three round trips to say one thing.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: ProfileEntity)
}
