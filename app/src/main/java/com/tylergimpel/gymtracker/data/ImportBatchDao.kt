package com.tylergimpel.gymtracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.ColumnInfo
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.Instant

/** The record of each import, which every session it brought in points at. */
@Dao
interface ImportBatchDao {
    @Insert
    suspend fun insert(batch: ImportBatchEntity): Long

    @Query("SELECT * FROM import_batch ORDER BY imported_at DESC")
    suspend fun all(): List<ImportBatchEntity>

    /** The import that brought in this exact file, if one did and has not been undone. */
    @Query("SELECT * FROM import_batch WHERE fingerprint = :fingerprint LIMIT 1")
    suspend fun withFingerprint(fingerprint: String): ImportBatchEntity?

    /** Every import, newest first, with how many sessions each brought in. */
    @Query(
        "SELECT b.id, b.source, b.imported_at, COUNT(s.id) AS sessions FROM import_batch b " +
            "LEFT JOIN session s ON s.import_batch_id = b.id GROUP BY b.id ORDER BY b.imported_at DESC",
    )
    fun observeListed(): Flow<List<ListedImport>>

    /** Every date a still-standing import from [source] filed a session on (#116). */
    @Query(
        "SELECT DISTINCT s.date FROM session s JOIN import_batch b ON b.id = s.import_batch_id " +
            "WHERE b.source = :source",
    )
    suspend fun datesImportedFrom(source: String): List<LocalDate>

    /** Everything the import brought in: its sessions, and all that hangs off them by cascade. */
    @Query("DELETE FROM session WHERE import_batch_id = :batchId")
    suspend fun deleteSessionsOf(batchId: Long)

    @Query("DELETE FROM import_batch WHERE id = :batchId")
    suspend fun delete(batchId: Long)
}

/** One import as the lifter sees it listed: where from, when, and how much it brought. */
data class ListedImport(
    val id: Long,
    val source: String,
    @ColumnInfo(name = "imported_at")
    val importedAt: Instant,
    val sessions: Int,
)
