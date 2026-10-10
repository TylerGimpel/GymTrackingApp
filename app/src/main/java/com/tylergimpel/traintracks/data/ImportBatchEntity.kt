package com.tylergimpel.traintracks.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * One import, which every session it brought in points at (ADR 0007).
 *
 * The one row that buys three things at once: a repeat import is recognised by its [fingerprint]
 * rather than doubling a history (#102), undoing removes exactly what this batch added and nothing
 * logged since, and whatever draws history can say where imported history ends.
 */
@Entity(tableName = "import_batch")
data class ImportBatchEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    /** Which source it came from, as the source names itself: `fitnotes`. */
    @ColumnInfo(name = "source")
    val source: String,

    /** What identifies the file, so importing the same one again can be caught. */
    @ColumnInfo(name = "fingerprint")
    val fingerprint: String,

    @ColumnInfo(name = "imported_at")
    val importedAt: Instant,

    /**
     * Whether volume counted from this batch can be trusted, as its source declared.
     *
     * Recorded per batch rather than assumed of importing: false for FitNotes, which never said
     * what a set was for, and true for any source that does.
     */
    @ColumnInfo(name = "volume_reliable")
    val volumeReliable: Boolean,
)
