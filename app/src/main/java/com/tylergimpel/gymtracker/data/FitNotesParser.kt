package com.tylergimpel.gymtracker.data

import android.database.sqlite.SQLiteDatabase
import com.tylergimpel.gymtracker.domain.FITNOTES
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
 */
object FitNotesParser {

    fun parse(file: File): NeutralImport {
        val sets = mutableListOf<ImportedSet>()
        val notYetSupported = mutableMapOf<String, Int>()
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery(
                "SELECT t._id, t.date, t.metric_weight, t.reps, e.name, e.exercise_type_id, " +
                    "t.duration_seconds, t.distance " +
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
                    sets += ImportedSet(
                        date = LocalDate.parse(rows.getString(1)),
                        exerciseName = rows.getString(4),
                        order = rows.getLong(0),
                        performance = performance,
                    )
                }
            }
        }
        return NeutralImport(source = FITNOTES, fingerprint = fingerprintOf(file), sets = sets, notYetSupported = notYetSupported)
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

    /** What FitNotes stores for "no load", which the current format writes as zero, not one. */
    private const val BODYWEIGHT_SENTINEL = 0.0

    /** A row read but not brought across, because it fits none of the shapes FitNotes writes. */
    const val UNREADABLE_SETS = "unreadable"

    private const val BUFFER = 64 * 1024
}
