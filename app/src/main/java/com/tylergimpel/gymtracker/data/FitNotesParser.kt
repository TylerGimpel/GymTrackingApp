package com.tylergimpel.gymtracker.data

import android.database.sqlite.SQLiteDatabase
import com.tylergimpel.gymtracker.domain.FITNOTES
import com.tylergimpel.gymtracker.domain.ImportedExercise
import com.tylergimpel.gymtracker.domain.WeightUnit
import com.tylergimpel.gymtracker.domain.ImportedSet
import com.tylergimpel.gymtracker.domain.Kilograms
import com.tylergimpel.gymtracker.domain.Metres
import com.tylergimpel.gymtracker.domain.NeutralImport
import com.tylergimpel.gymtracker.domain.Performance
import java.io.File
import java.security.MessageDigest
import java.time.Duration
import java.time.LocalDate

/**
 * Reads a FitNotes `.fitnotes` backup into neutral records: the one format-specific half of
 * importing (ADR 0007). Everything after this knows nothing about FitNotes.
 *
 * **The backup, never the CSV.** On the author's own file the CSV drops 98 superset groups and
 * 114 personal-record flags that the database keeps, with nothing to say so.
 *
 * Built to what the real file does rather than what documentation said it would
 * (`docs/research/fitnotes-export-actual.md`):
 *
 * - **`metric_weight` is declared `INTEGER` and stores `REAL`.** SQLite lets `22.5` sit in an
 *   integer column, so it is read as a double whatever the declaration says. Plain kilograms,
 *   unscaled.
 * - **`training_log.unit` is not the weight unit** — it is `0` on every row — and is never read.
 *   The unit a lifter saw lives on the exercise.
 * - **The bodyweight sentinel is `0.0`**, not the `1.0` older exports used: a weight × reps row
 *   at 0.0 is a bodyweight set with nothing added, never a lift of one kilogram.
 * - **`Reps` and `Time` are mutually exclusive**, so a hold and a lift tell themselves apart
 *   without the bank's help. `exercise_type_id` maps cleanly: `0` weight × reps, `1` cardio,
 *   `3` timed — and this app's **Logging types** are a superset of those three, so nothing is lost.
 * - **Weights are kilograms whatever the exercise was read in.** An exercise FitNotes showed in
 *   pounds still stores `metric_weight`; converting it again would corrupt every pound lift.
 * - **Cardio is read defensively.** The author never logged any, so the distance column's unit
 *   is unverified; it is read as FitNotes' metric base, metres, the way `metric_weight` is in
 *   kilograms, and a row with no distance is a timed effort rather than a guess.
 * - **Supersets** are `WorkoutGroup`s: which exercises were grouped, on which day. Every member
 *   shares the group's date in the real file, so a set belongs to the group its exercise was in
 *   on the day it was done.
 * - **A set's note is a `Comment`** of owner type 1 pointing at the set. Some point at sets that
 *   were since deleted — 13 in the author's file — which is expected, and they are skipped.
 * - **Per-exercise settings** are read defensively: the author's file never set one, so the unit
 *   ids and the rest-time unit are unverified. Unit 1 is read as kilograms and 2 as pounds, with 0
 *   as "no choice"; rest as seconds, as FitNotes' own rest timer counts; increment as kilograms.
 */
object FitNotesParser {

    fun parse(file: File): NeutralImport {
        val sets = mutableListOf<ImportedSet>()
        val notYetSupported = mutableMapOf<String, Int>()
        var exercises: Map<String, ImportedExercise> = emptyMap()
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val groups = groupsIn(db)
            val notes = setNotesIn(db)
            exercises = exercisesIn(db)
            db.rawQuery(
                "SELECT t._id, t.date, t.metric_weight, t.reps, e.name, e.exercise_type_id, " +
                    "t.duration_seconds, t.distance, t.exercise_id " +
                    "FROM training_log t JOIN exercise e ON e._id = t.exercise_id ORDER BY t.date, t._id",
                null,
            ).use { rows ->
                while (rows.moveToNext()) {
                    val performance = performanceOf(
                        type = rows.getInt(5),
                        // getDouble, whatever the column says: 22.5 lives in an INTEGER column.
                        kilograms = rows.getDouble(2),
                        reps = rows.intOrNull(3),
                        seconds = rows.intOrNull(6),
                        metres = if (rows.isNull(7)) null else rows.getDouble(7),
                    )
                    if (performance == null) {
                        // A row that is none of the shapes FitNotes writes is counted and said,
                        // never turned into something it was not.
                        notYetSupported.merge(UNREADABLE_SETS, 1, Int::plus)
                        continue
                    }
                    val date = rows.getString(1)
                    sets += ImportedSet(
                        date = LocalDate.parse(date),
                        exerciseName = rows.getString(4),
                        order = rows.getLong(0),
                        performance = performance,
                        supersetGroup = groups[date to rows.getLong(8)],
                        note = notes[rows.getLong(0)],
                    )
                }
            }
        }
        return NeutralImport(
            source = FITNOTES,
            fingerprint = fingerprintOf(file),
            sets = sets,
            exercises = exercises,
            notYetSupported = notYetSupported,
        )
    }

    /**
     * What one row records, by the exercise's type and which columns it filled; null for a row
     * that fits none of them.
     */
    private fun performanceOf(type: Int, kilograms: Double, reps: Int?, seconds: Int?, metres: Double?): Performance? =
        when {
            type == CARDIO && seconds != null && metres != null && metres > 0.0 ->
                Performance.DistanceAndTime(Metres(metres), Duration.ofSeconds(seconds.toLong()))
            (type == TIMED || type == CARDIO) && seconds != null -> Performance.Timed(Duration.ofSeconds(seconds.toLong()))
            type == WEIGHT_AND_REPS && reps != null && kilograms == BODYWEIGHT_SENTINEL ->
                Performance.BodyweightReps(addedWeight = null, reps = reps)
            type == WEIGHT_AND_REPS && reps != null -> Performance.WeightAndReps(Kilograms(kilograms), reps)
            else -> null
        }

    private fun android.database.Cursor.intOrNull(column: Int): Int? = if (isNull(column)) null else getInt(column)

    /** Which superset each exercise was in on each day: (date, exercise id) to group id. */
    private fun groupsIn(db: SQLiteDatabase): Map<Pair<String, Long>, Long> = buildMap {
        if (!db.hasTable("WorkoutGroupExercise")) return@buildMap
        db.rawQuery("SELECT date, exercise_id, workout_group_id FROM WorkoutGroupExercise", null).use { rows ->
            while (rows.moveToNext()) put(rows.getString(0) to rows.getLong(1), rows.getLong(2))
        }
    }

    /**
     * Each set's note, by the set it belongs to. A comment on a set that no longer exists simply
     * never meets one, which is how an orphan is skipped.
     */
    private fun setNotesIn(db: SQLiteDatabase): Map<Long, String> = buildMap {
        if (!db.hasTable("Comment")) return@buildMap
        db.rawQuery("SELECT owner_id, comment FROM Comment WHERE owner_type_id = $SET_COMMENT", null).use { rows ->
            while (rows.moveToNext()) rows.getString(1)?.takeIf(String::isNotBlank)?.let { put(rows.getLong(0), it) }
        }
    }

    /** What FitNotes kept about each exercise the lifter set anything on, by its name there. */
    private fun exercisesIn(db: SQLiteDatabase): Map<String, ImportedExercise> = buildMap {
        db.rawQuery("SELECT name, notes, weight_unit_id, weight_increment, default_rest_time FROM exercise", null).use { rows ->
            while (rows.moveToNext()) {
                val kept = ImportedExercise(
                    note = rows.getString(1)?.takeIf(String::isNotBlank),
                    displayUnit = when (rows.intOrNull(2)) {
                        KILOGRAMS_UNIT -> WeightUnit.KILOGRAMS
                        POUNDS_UNIT -> WeightUnit.POUNDS
                        else -> null
                    },
                    increment = if (rows.isNull(3)) null else rows.getDouble(3).takeIf { it > 0.0 }?.let(::Kilograms),
                    rest = rows.intOrNull(4)?.takeIf { it > 0 }?.let { Duration.ofSeconds(it.toLong()) },
                )
                if (kept != ImportedExercise()) put(rows.getString(0), kept)
            }
        }
    }

    private fun SQLiteDatabase.hasTable(name: String): Boolean =
        rawQuery("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(name)).use { it.moveToFirst() }

    /** What identifies this exact file, so importing it twice can be recognised (#102). */
    private fun fingerprintOf(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** FitNotes' `exercise_type_id` for weight × reps, verified against a real file. */
    private const val WEIGHT_AND_REPS = 0

    /** FitNotes' `exercise_type_id` for cardio, verified against a real file's exercise list. */
    private const val CARDIO = 1

    /** FitNotes' `exercise_type_id` for a hold, verified against a real file. */
    private const val TIMED = 3

    /** The `Comment.owner_type_id` of a note on a set; every comment in the author's file. */
    private const val SET_COMMENT = 1

    /** `exercise.weight_unit_id` values read as an explicit choice; unverified, see above. */
    private const val KILOGRAMS_UNIT = 1
    private const val POUNDS_UNIT = 2

    /** What FitNotes stores for "no load", which the current format writes as zero, not one. */
    private const val BODYWEIGHT_SENTINEL = 0.0

    /** A row read but not brought across, because it fits none of the shapes FitNotes writes. */
    const val UNREADABLE_SETS = "unreadable"

    private const val BUFFER = 64 * 1024
}
