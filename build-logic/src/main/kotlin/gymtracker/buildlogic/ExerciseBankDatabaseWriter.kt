package gymtracker.buildlogic

import org.sqlite.SQLiteDataSource
import java.io.File
import java.sql.Connection

/**
 * Writes the prepopulated exercise bank that ships in the APK.
 *
 * Everything structural comes from the Room exported schema: the DDL, the column list, the database
 * version and the identity hash. Nothing about the table is written down here, so an entity change
 * cannot leave the asset behind without the build noticing.
 */
object ExerciseBankDatabaseWriter {

    const val TABLE = "exercise"

    /**
     * How a list of muscles is stored in a single column.
     *
     * The vocabulary is closed and contains no vertical bar, so this needs no escaping and the app
     * and the build step cannot disagree about how to read it back. [assertDelimiterSafe] keeps that
     * true if the vocabulary ever grows.
     */
    const val LIST_DELIMITER = "|"

    fun write(target: File, schema: RoomSchema, exercises: List<BankExercise>) {
        val columns = schema.columnsByTable[TABLE]
            ?: error(
                "The Room schema declares no `$TABLE` table (found: " +
                    "${schema.columnsByTable.keys.sorted()}). The generator and the entity have diverged."
            )
        exercises.forEach(::assertDelimiterSafe)

        target.parentFile?.mkdirs()
        target.delete()

        // Addressing the driver directly rather than through DriverManager: Gradle loads this plugin
        // in an isolated classloader, where JDBC's ServiceLoader lookup finds no driver at all.
        val dataSource = SQLiteDataSource().apply { url = "jdbc:sqlite:${target.absolutePath}" }

        dataSource.connection.use { connection ->
            connection.autoCommit = false
            connection.createStatement().use { statement ->
                schema.createStatements.forEach(statement::executeUpdate)
                schema.setupQueries.forEach(statement::executeUpdate)
                // Room reads the schema version from `PRAGMA user_version`. Leaving it at 0 makes
                // Room treat a fully-populated file as a brand new database and try to create it.
                statement.executeUpdate("PRAGMA user_version = ${schema.version}")
            }
            connection.insertExercises(columns, exercises)
            connection.commit()

            connection.autoCommit = true
            connection.createStatement().use { it.executeUpdate("VACUUM") }
        }
    }

    private fun Connection.insertExercises(columns: List<String>, exercises: List<BankExercise>) {
        val sql = columns.joinToString(
            prefix = "INSERT INTO $TABLE (",
            separator = ", ",
            postfix = ") VALUES (${columns.joinToString(", ") { "?" }})",
        ) { "`$it`" }

        prepareStatement(sql).use { statement ->
            exercises.forEach { exercise ->
                columns.forEachIndexed { index, column ->
                    statement.setObject(index + 1, exercise.valueFor(column))
                }
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }

    /**
     * Maps a schema column to its value. An unrecognised column fails the build rather than being
     * inserted as null, so adding a field to the entity forces a decision about what ships in it.
     */
    private fun BankExercise.valueFor(column: String): Any? = when (column) {
        "id" -> id
        "name" -> name
        "primary_muscles" -> primaryMuscles.joinToString(LIST_DELIMITER)
        "secondary_muscles" -> secondaryMuscles.joinToString(LIST_DELIMITER)
        "equipment" -> equipment
        "mechanic" -> mechanic
        "force" -> force
        "category" -> category
        "level" -> level
        "logging_type" -> loggingType
        "progression_model" -> progressionModel
        "unilateral" -> if (unilateral) 1 else 0
        "source" -> source
        // Nothing ships tombstoned: a tombstone is what happens to a row on a lifter's phone when
        // an update drops an exercise they have already trained.
        "tombstoned" -> 0
        else -> error(
            "The Room schema declares `$TABLE`.`$column`, which the bank generator does not know " +
                "how to fill. Add it to ExerciseBankDatabaseWriter.valueFor."
        )
    }

    private fun assertDelimiterSafe(exercise: BankExercise) {
        (exercise.primaryMuscles + exercise.secondaryMuscles).forEach { muscle ->
            check(LIST_DELIMITER !in muscle) {
                "Muscle `$muscle` on `${exercise.id}` contains the list delimiter `$LIST_DELIMITER`"
            }
        }
    }
}
