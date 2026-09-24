package com.tylergimpel.gymtracker.data

import android.database.Cursor
import android.util.Base64
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tylergimpel.gymtracker.domain.ExerciseSource
import com.tylergimpel.gymtracker.domain.Experience
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

/** Why a file could not be restored, each of which the lifter is told in words of their own. */
enum class RefusedBecause { NOT_A_BACKUP, NEWER_APP, DOES_NOT_FIT }

/** A file that could not be restored, and why; nothing was changed by it. */
class NotABackup(val because: RefusedBecause, detail: String? = null) :
    Exception(detail ?: because.name)

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
 *   brings with it, so from it only the lifter's own exercises and every **Tombstone** are backed
 *   up. A tombstone is a bank row an update dropped, kept for the history pointing at it; a new
 *   install's bank may not have it at all, and without it that history would point at nothing.
 *   Everything that hangs off a bank row — renames, units, notes, **Dislikes** — lives in tables
 *   that are backed up whole.
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

    /**
     * Writes everything to [output] as JSON, streamed, so years of training stay quick.
     *
     * Answers how many sessions it wrote, counted from the rows that went into the file rather
     * than asked again afterwards, so what the lifter is told is what the file holds.
     */
    suspend fun write(output: OutputStream): Int = withContext(Dispatchers.IO) {
        var sessions = 0
        JsonWriter(output.bufferedWriter()).use { json ->
            json.beginObject()
            json.name("format").value(FORMAT)
            json.name("formatVersion").value(FORMAT_VERSION)
            json.name("databaseVersion").value(GymDatabase.VERSION.toLong())
            json.name("createdAt").value(clock.instant().toString())
            json.name("tables").beginObject()
            backedUpTables().forEach { table ->
                json.name(table).beginArray()
                db.query("SELECT * FROM `$table`" + backedUpWhere(table)).use { rows ->
                    if (table == SESSION_TABLE) sessions = rows.count
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
        sessions
    }

    /** Reads a backup from [input], or says why the file is not one. */
    suspend fun read(input: InputStream): Backup = withContext(Dispatchers.IO) {
        try {
            JsonReader(input.bufferedReader()).use { json -> json.readBackup() }
        } catch (notOne: NotABackup) {
            throw notOne
        } catch (unreadable: Exception) {
            throw NotABackup(RefusedBecause.NOT_A_BACKUP, unreadable.message)
        }
    }

    /**
     * Replaces everything the backup covers with what [backup] holds.
     *
     * All or nothing: foreign keys are checked when the transaction ends, so the order rows go in
     * does not matter and a backup that does not hold together leaves the app as it was.
     */
    suspend fun restore(backup: Backup): Unit = database.withTransaction {
        val covered = backedUpTables().toSet()
        // Only into tables the backup covers. A name from the file is never trusted on its own:
        // Room's and SQLite's own tables are not the lifter's, and a file naming one — or naming
        // a table this version does not have — is refused rather than half-applied.
        backup.tables.keys.firstOrNull { it !in covered }?.let {
            throw NotABackup(RefusedBecause.DOES_NOT_FIT, "unknown table $it")
        }
        db.execSQL("PRAGMA defer_foreign_keys = ON")
        covered.forEach { table -> db.execSQL("DELETE FROM `$table`" + replacedWhere(table)) }
        backup.tables.forEach { (table, rows) ->
            val columns = columnsOf(table)
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
                "SELECT (SELECT COUNT(*) FROM exercise WHERE source = '$USER_SOURCE') + " +
                    "(SELECT COUNT(*) FROM exercise_preference) + " +
                    "(SELECT COUNT(*) FROM plate_inventory) + " +
                    "(SELECT COUNT(*) FROM profile WHERE sex IS NOT NULL OR birth_year IS NOT NULL " +
                    "OR height_cm IS NOT NULL OR bar_kg IS NOT NULL OR display_unit IS NOT NULL " +
                    "OR experience != '${Experience.TRAINED.name}')",
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

    /** Which rows of [table] go into a backup: all of them, less the bank's own live rows. */
    private fun backedUpWhere(table: String): String =
        if (table == EXERCISE_TABLE) " WHERE source = '$USER_SOURCE' OR tombstoned = 1" else ""

    /**
     * Which rows of [table] a restore clears first: the lifter's own exercises, but never a bank
     * row, tombstoned or not, since the new install's bank is what it is.
     */
    private fun replacedWhere(table: String): String =
        if (table == EXERCISE_TABLE) " WHERE source = '$USER_SOURCE'" else ""

    private fun columnsOf(table: String): Set<String> =
        db.query("PRAGMA table_info(`$table`)").use { rows ->
            val name = rows.getColumnIndexOrThrow("name")
            buildSet { while (rows.moveToNext()) add(rows.getString(name)) }
        }

    private fun insert(table: String, row: Map<String, Any?>) {
        if (row.isEmpty()) return
        val columns = row.keys.joinToString(",") { "`$it`" }
        val holes = row.keys.joinToString(",") { "?" }
        // A tombstone the new bank still has is the bank's to keep: it is the same exercise.
        val verb = if (table == EXERCISE_TABLE) "INSERT OR IGNORE" else "INSERT"
        db.execSQL("$verb INTO `$table` ($columns) VALUES ($holes)", row.values.toTypedArray())
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
        if (format != FORMAT) throw NotABackup(RefusedBecause.NOT_A_BACKUP)
        val written = version ?: throw NotABackup(RefusedBecause.NOT_A_BACKUP, "no database version")
        if (written > GymDatabase.VERSION) throw NotABackup(RefusedBecause.NEWER_APP)
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
        else -> throw NotABackup(RefusedBecause.NOT_A_BACKUP, "unexpected ${peek()}")
    }

    companion object {
        /** What a backup file says it is, so a random JSON file is refused rather than restored. */
        const val FORMAT = "gym-tracker-backup"

        /** The shape of the file around the tables; the tables themselves follow the database. */
        const val FORMAT_VERSION = 1L

        private const val EXERCISE_TABLE = "exercise"
        private const val SESSION_TABLE = "session"
        private val USER_SOURCE = ExerciseSource.USER.stored
        private const val BLOB = "base64"
    }
}
