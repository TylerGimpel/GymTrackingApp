package com.tylergimpel.gymtracker.data

import android.database.Cursor
import android.util.Base64
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tylergimpel.gymtracker.domain.ExerciseSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock

/** One table's rows as the backup holds them: column name to value, for every column it had. */
typealias BackupRows = List<Map<String, Any?>>

/** A backup read back in, before anything is done with it. */
data class Backup(
    /** The database version that wrote it, which is what restore reads its columns against. */
    val databaseVersion: Int,
    val tables: Map<String, BackupRows>,
) {
    /** How many training sessions it holds, which is what a lifter recognises a backup by. */
    val sessions: Int get() = tables["session"].orEmpty().size
}

/**
 * What a restore would replace, in the lifter's terms (#104).
 *
 * Only what is the lifter's: their training, their routine, their weigh-ins, and anything they
 * have said — their own exercises, choices about bank ones, plates, profile answers. What the
 * engine worked out for itself is left out, because it is rebuilt from the log (ADR 0010) and
 * merely opening an exercise writes some.
 */
data class WouldReplace(
    val sessions: Int,
    val routine: Boolean,
    val weighIns: Int,
    /** Whether the lifter has made any choice of their own: exercises, units, plates, profile. */
    val customised: Boolean,
) {
    val isNothing: Boolean get() = sessions == 0 && !routine && weighIns == 0 && !customised
}

/** Why a file could not be restored, in terms the lifter can act on. */
class NotABackup(message: String) : Exception(message)

/**
 * The complete backup, and restoring it (#97).
 *
 * **The only unqualified promise in the app.** Everything else is a recommendation, an estimate or
 * an advisory; if this silently drops something, the lifter finds out restoring onto a new phone
 * after losing the old one. So it is built not to be able to forget anything:
 *
 * - **Every table is enumerated from the database itself** rather than listed by hand, and every
 *   column comes with it. A table or column added next year is in the backup without anybody
 *   remembering to add it — which is the failure a hand-kept list invites.
 * - **One exception, stated:** `exercise` also holds the shipped bank, which a new install
 *   brings with it, so only the lifter's own exercises are backed up from it. Everything that
 *   hangs off a bank row — renames, units, notes, **Dislikes** — lives in tables that are backed
 *   up whole.
 *
 * **Restore replaces rather than merges**, inside one transaction with foreign keys checked at the
 * end: a file that cannot be applied changes nothing. Columns are matched by name, so a backup
 * from an older version restores into a newer one, with added columns taking their defaults.
 */
class BackupRepository(
    private val database: GymDatabase,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private val db: SupportSQLiteDatabase get() = database.openHelper.writableDatabase

    /** Writes everything to [output] as JSON, streamed, so years of training stay quick. */
    suspend fun write(output: OutputStream): Unit = withContext(Dispatchers.IO) {
        JsonWriter(output.bufferedWriter()).use { json ->
            json.beginObject()
            json.name("format").value(FORMAT)
            json.name("formatVersion").value(FORMAT_VERSION)
            json.name("databaseVersion").value(GymDatabase.VERSION.toLong())
            json.name("createdAt").value(clock.instant().toString())
            json.name("tables").beginObject()
            backedUpTables().forEach { table ->
                json.name(table).beginArray()
                db.query("SELECT * FROM `$table`" + whereFor(table)).use { rows ->
                    while (rows.moveToNext()) {
                        json.beginObject()
                        for (column in 0 until rows.columnCount) {
                            json.name(rows.getColumnName(column))
                            json.writeCell(rows, column)
                        }
                        json.endObject()
                    }
                }
                json.endArray()
            }
            json.endObject()
            json.endObject()
        }
    }

    /** Reads a backup from [input], or says why the file is not one. */
    suspend fun read(input: InputStream): Backup = withContext(Dispatchers.IO) {
        try {
            JsonReader(input.bufferedReader()).use { json -> json.readBackup() }
        } catch (notOne: NotABackup) {
            throw notOne
        } catch (unreadable: Exception) {
            throw NotABackup("not a backup this app wrote: ${unreadable.message}")
        }
    }

    /**
     * Replaces everything the backup covers with what [backup] holds.
     *
     * All or nothing: foreign keys are checked when the transaction ends, so the order rows go in
     * does not matter and a backup that does not hold together leaves the app as it was.
     */
    suspend fun restore(backup: Backup): Unit = database.withTransaction {
        db.execSQL("PRAGMA defer_foreign_keys = ON")
        backedUpTables().forEach { table -> db.execSQL("DELETE FROM `$table`" + whereFor(table)) }
        backup.tables.forEach { (table, rows) ->
            val columns = columnsOf(table)
            // A table this version does not have is one it cannot restore into; said rather than
            // skipped, because skipping is exactly the silent loss a backup must never have.
            if (columns.isEmpty()) throw NotABackup("made by a newer version of the app ($table)")
            rows.forEach { row -> insert(table, row.filterKeys { it in columns }) }
        }
    }

    /**
     * What restoring now would replace, so the lifter can be told before it happens (#104).
     *
     * A profile row only counts where it holds an answer: dismissing the first-session tip or
     * choosing a theme writes one too, and neither is training a lifter could lose.
     */
    suspend fun wouldReplace(): WouldReplace = withContext(Dispatchers.IO) {
        fun count(sql: String): Int = db.query(sql).use { if (it.moveToFirst()) it.getInt(0) else 0 }
        WouldReplace(
            sessions = count("SELECT COUNT(*) FROM session"),
            routine = count("SELECT COUNT(*) FROM routine") > 0,
            weighIns = count("SELECT COUNT(*) FROM bodyweight_entry"),
            customised = count(
                "SELECT (SELECT COUNT(*) FROM exercise WHERE source = '${ExerciseSource.USER.stored}') + " +
                    "(SELECT COUNT(*) FROM exercise_preference) + " +
                    "(SELECT COUNT(*) FROM plate_inventory) + " +
                    "(SELECT COUNT(*) FROM profile WHERE sex IS NOT NULL OR birth_year IS NOT NULL " +
                    "OR height_cm IS NOT NULL OR bar_kg IS NOT NULL OR display_unit IS NOT NULL " +
                    "OR experience != 'TRAINED')",
            ) > 0,
        )
    }

    /** Every table the lifter's data lives in: all of them, less SQLite's and Room's own. */
    internal fun backedUpTables(): List<String> =
        db.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' " +
                "AND name NOT LIKE 'sqlite_%' AND name NOT IN ('android_metadata', 'room_master_table') " +
                "ORDER BY name",
        ).use { rows -> buildList { while (rows.moveToNext()) add(rows.getString(0)) } }

    private fun whereFor(table: String): String =
        if (table == EXERCISE_TABLE) " WHERE source = '${ExerciseSource.USER.stored}'" else ""

    private fun columnsOf(table: String): Set<String> =
        db.query("PRAGMA table_info(`$table`)").use { rows ->
            val name = rows.getColumnIndexOrThrow("name")
            buildSet { while (rows.moveToNext()) add(rows.getString(name)) }
        }

    private fun insert(table: String, row: Map<String, Any?>) {
        if (row.isEmpty()) return
        val columns = row.keys.joinToString(",") { "`$it`" }
        val holes = row.keys.joinToString(",") { "?" }
        db.execSQL("INSERT INTO `$table` ($columns) VALUES ($holes)", row.values.toTypedArray())
    }

    private fun JsonWriter.writeCell(rows: Cursor, column: Int) {
        when (rows.getType(column)) {
            Cursor.FIELD_TYPE_NULL -> nullValue()
            Cursor.FIELD_TYPE_INTEGER -> value(rows.getLong(column))
            Cursor.FIELD_TYPE_FLOAT -> value(rows.getDouble(column))
            Cursor.FIELD_TYPE_BLOB -> {
                // Tagged so that reading it back cannot mistake it for a string.
                beginObject()
                name(BLOB).value(Base64.encodeToString(rows.getBlob(column), Base64.NO_WRAP))
                endObject()
            }
            else -> value(rows.getString(column))
        }
    }

    private fun JsonReader.readBackup(): Backup {
        var format: String? = null
        var version: Int? = null
        var tables: Map<String, BackupRows> = emptyMap()
        beginObject()
        while (hasNext()) {
            when (nextName()) {
                "format" -> format = nextString()
                "databaseVersion" -> version = nextInt()
                "tables" -> tables = readTables()
                else -> skipValue()
            }
        }
        endObject()
        if (format != FORMAT) throw NotABackup("not a backup this app wrote")
        val written = version ?: throw NotABackup("no database version")
        if (written > GymDatabase.VERSION) throw NotABackup("made by a newer version of the app")
        return Backup(written, tables)
    }

    private fun JsonReader.readTables(): Map<String, BackupRows> = buildMap {
        beginObject()
        while (hasNext()) {
            val table = nextName()
            val rows = mutableListOf<Map<String, Any?>>()
            beginArray()
            while (hasNext()) rows += readRow()
            endArray()
            put(table, rows)
        }
        endObject()
    }

    private fun JsonReader.readRow(): Map<String, Any?> = buildMap {
        beginObject()
        while (hasNext()) put(nextName(), readCell())
        endObject()
    }

    private fun JsonReader.readCell(): Any? = when (peek()) {
        JsonToken.NULL -> nextNull().let { null }
        JsonToken.STRING -> nextString()
        JsonToken.BOOLEAN -> if (nextBoolean()) 1L else 0L
        JsonToken.NUMBER -> {
            // Integers stay integers: an id or a count read back as 3.0 would not match its row.
            val text = nextString()
            text.toLongOrNull() ?: text.toDouble()
        }
        JsonToken.BEGIN_OBJECT -> {
            beginObject()
            nextName()
            val bytes = Base64.decode(nextString(), Base64.NO_WRAP)
            endObject()
            bytes
        }
        else -> throw NotABackup("unexpected ${peek()}")
    }

    companion object {
        /** What a backup file says it is, so a random JSON file is refused rather than restored. */
        const val FORMAT = "gym-tracker-backup"

        /** The shape of the file around the tables; the tables themselves follow the database. */
        const val FORMAT_VERSION = 1L

        private const val EXERCISE_TABLE = "exercise"
        private const val BLOB = "base64"
    }
}
