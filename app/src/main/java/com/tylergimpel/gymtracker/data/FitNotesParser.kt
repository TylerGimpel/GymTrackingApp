package com.tylergimpel.gymtracker.data

import android.database.sqlite.SQLiteDatabase
import com.tylergimpel.gymtracker.domain.FITNOTES
import com.tylergimpel.gymtracker.domain.ImportedSet
import com.tylergimpel.gymtracker.domain.Kilograms
import com.tylergimpel.gymtracker.domain.NeutralImport
import com.tylergimpel.gymtracker.domain.Performance
import java.io.File
import java.security.MessageDigest
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
 * - **The bodyweight sentinel is `0.0`**, not the `1.0` older exports used.
 *
 * The tracer reads weight × reps (`exercise_type_id` 0). Timed and cardio rows are counted and
 * left for #100 rather than guessed at.
 */
object FitNotesParser {

    fun parse(file: File): NeutralImport {
        val sets = mutableListOf<ImportedSet>()
        val notYet = mutableMapOf<String, Int>()
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery(
                "SELECT t._id, t.date, t.metric_weight, t.reps, e.name, e.exercise_type_id " +
                    "FROM training_log t JOIN exercise e ON e._id = t.exercise_id ORDER BY t.date, t._id",
                null,
            ).use { rows ->
                while (rows.moveToNext()) {
                    val type = rows.getInt(5)
                    if (type != WEIGHT_AND_REPS) {
                        notYet.merge(if (type == TIMED) TIMED_SETS else CARDIO_SETS, 1, Int::plus)
                        continue
                    }
                    sets += ImportedSet(
                        date = LocalDate.parse(rows.getString(1)),
                        exerciseName = rows.getString(4),
                        order = rows.getLong(0),
                        // getDouble, whatever the column says: 22.5 lives in an INTEGER column.
                        performance = Performance.WeightAndReps(Kilograms(rows.getDouble(2)), rows.getInt(3)),
                    )
                }
            }
        }
        return NeutralImport(source = FITNOTES, fingerprint = fingerprintOf(file), sets = sets, notYetSupported = notYet)
    }

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

    /** FitNotes' `exercise_type_id` values, verified against a real file. */
    private const val WEIGHT_AND_REPS = 0
    private const val TIMED = 3

    /** What the tracer counts rather than brings across, until #100. */
    const val TIMED_SETS = "timed"
    const val CARDIO_SETS = "cardio"

    private const val BUFFER = 64 * 1024
}
