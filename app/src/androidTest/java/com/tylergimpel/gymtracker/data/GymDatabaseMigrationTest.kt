package com.tylergimpel.gymtracker.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * An installed copy of version 1 has to reach version 2 with the schema Room expects.
 *
 * `runMigrationsAndValidate` compares the migrated database against the exported schema, so DDL
 * that drifts from the entities — a missing index, a different foreign key action — fails here
 * rather than as an identity-hash crash on a lifter's phone after an update.
 */
@RunWith(AndroidJUnit4::class)
class GymDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        GymDatabase::class.java,
    )

    @Test
    fun migratesFromTheShippedBankToTheLogSchema() {
        helper.createDatabase(TEST_DATABASE, 1).use { version1 ->
            // Version 1 was the exercise bank on its own; nothing here logged anything.
            version1.execSQL(
                "INSERT INTO exercise (id, name, primary_muscles, secondary_muscles, equipment, " +
                    "mechanic, force, category, level, logging_type, unilateral, source) VALUES " +
                    "('Test_Exercise', 'Test Exercise', 'chest', '', 'barbell', 'compound', " +
                    "'push', 'strength', 'beginner', 'weight_reps', 0, 'upstream')",
            )
        }

        val version2 = helper.runMigrationsAndValidate(TEST_DATABASE, 2, true, MIGRATION_1_2)

        version2.use { db ->
            // The bank survives the migration untouched.
            db.query("SELECT name FROM exercise WHERE id = 'Test_Exercise'").use {
                it.moveToFirst()
                assertEquals("Test Exercise", it.getString(0))
            }
            // And the log tables now exist to be written to.
            db.execSQL(
                "INSERT INTO session (started_at, finished_at, date, note) " +
                    "VALUES (1757869200000, NULL, '2026-09-14', NULL)",
            )
            db.execSQL(
                "INSERT INTO session_exercise (session_id, exercise_id, order_index) " +
                    "VALUES (1, 'Test_Exercise', 1)",
            )
            db.execSQL(
                "INSERT INTO set_entry (session_exercise_id, set_index, weight_kg, reps, note) " +
                    "VALUES (1, 1, 60.0, 5, NULL)",
            )
            db.query("SELECT set_index, weight_kg FROM set_entry").use {
                it.moveToFirst()
                assertEquals(1, it.getInt(0))
                assertEquals(60.0, it.getDouble(1), 0.0)
            }
        }
    }

    @Test
    fun migratesFromTheLogSchemaToTheRoutineAndCycleSchema() {
        helper.createDatabase(SECOND_DATABASE, 2).use { version2 ->
            version2.execSQL(
                "INSERT INTO session (started_at, finished_at, date, note) " +
                    "VALUES (1757869200000, NULL, '2026-09-14', NULL)",
            )
        }

        val version3 = helper.runMigrationsAndValidate(SECOND_DATABASE, 3, true, MIGRATION_2_3)

        version3.use { db ->
            // The session survives, and gains the snapshot columns as null rather than losing its
            // place in history.
            db.query("SELECT routine_name, routine_day_name FROM session").use {
                it.moveToFirst()
                assertTrue(it.isNull(0))
                assertTrue(it.isNull(1))
            }
            db.execSQL(
                "INSERT INTO routine (name, goal, cadence, equipment, rep_preference) " +
                    "VALUES ('Push / Pull / Legs', 'HYPERTROPHY', 4, 'barbell', 'MODERATE')",
            )
            db.execSQL(
                "INSERT INTO routine_day (routine_id, order_index, name) VALUES (1, 1, 'Push')",
            )
            db.execSQL("INSERT INTO cycle (routine_id, ordinal, started_at) VALUES (1, 1, 1757869200000)")
            db.execSQL(
                "INSERT INTO cycle_completion (cycle_id, routine_day_id, session_id) VALUES (1, 1, 1)",
            )
            db.query("SELECT COUNT(*) FROM cycle_completion").use {
                it.moveToFirst()
                assertEquals(1, it.getInt(0))
            }
        }
    }

    @Test
    fun migratesToSetTypesAndCallsWhatWasAlreadyLoggedWork() {
        helper.createDatabase(THIRD_DATABASE, 3).use { version3 ->
            version3.execSQL(
                "INSERT INTO exercise (id, name, primary_muscles, secondary_muscles, equipment, " +
                    "mechanic, force, category, level, logging_type, unilateral, source) VALUES " +
                    "('Test_Exercise', 'Test Exercise', 'chest', '', 'barbell', 'compound', " +
                    "'push', 'strength', 'beginner', 'weight_reps', 0, 'upstream')",
            )
            version3.execSQL(
                "INSERT INTO session (started_at, finished_at, date, note, routine_name, " +
                    "routine_day_name) VALUES (1757869200000, NULL, '2026-09-14', NULL, NULL, NULL)",
            )
            version3.execSQL(
                "INSERT INTO session_exercise (session_id, exercise_id, order_index) " +
                    "VALUES (1, 'Test_Exercise', 1)",
            )
            version3.execSQL(
                "INSERT INTO set_entry (session_exercise_id, set_index, weight_kg, reps, note) " +
                    "VALUES (1, 1, 60.0, 5, NULL)",
            )
        }

        val version4 = helper.runMigrationsAndValidate(THIRD_DATABASE, 4, true, MIGRATION_3_4)

        version4.use { db ->
            // Sets logged before types existed were all intended as work, and say so now.
            db.query("SELECT type, weight_kg FROM set_entry").use {
                it.moveToFirst()
                assertEquals("working", it.getString(0))
                assertEquals(60.0, it.getDouble(1), 0.0)
            }
        }
    }

    @Test
    fun migratesToPerSideRowsAndDropsLeavingExistingSetsAsSingleEntries() {
        helper.createDatabase(FOURTH_DATABASE, 4).use { version4 ->
            version4.execSQL(
                "INSERT INTO exercise (id, name, primary_muscles, secondary_muscles, equipment, " +
                    "mechanic, force, category, level, logging_type, unilateral, source) VALUES " +
                    "('Test_Exercise', 'Test Exercise', 'chest', '', 'barbell', 'compound', " +
                    "'push', 'strength', 'beginner', 'weight_reps', 0, 'upstream')",
            )
            version4.execSQL(
                "INSERT INTO session (started_at, finished_at, date, note, routine_name, " +
                    "routine_day_name) VALUES (1757869200000, NULL, '2026-09-14', NULL, NULL, NULL)",
            )
            version4.execSQL(
                "INSERT INTO session_exercise (session_id, exercise_id, order_index) " +
                    "VALUES (1, 'Test_Exercise', 1)",
            )
            version4.execSQL(
                "INSERT INTO set_entry (session_exercise_id, set_index, type, weight_kg, reps, note) " +
                    "VALUES (1, 1, 'working', 60.0, 5, NULL)",
            )
        }

        val version5 = helper.runMigrationsAndValidate(FOURTH_DATABASE, 5, true, MIGRATION_4_5)

        version5.use { db ->
            // Everything already logged was a single entry, and stays one: no side, nothing to
            // reinterpret.
            db.query("SELECT side, weight_kg FROM set_entry").use {
                it.moveToFirst()
                assertTrue(it.isNull(0))
                assertEquals(60.0, it.getDouble(1), 0.0)
            }
            // And drops are now recordable against it.
            db.execSQL(
                "INSERT INTO drop_segment (set_entry_id, order_index, weight_kg, reps) " +
                    "VALUES (1, 1, 50.0, 4)",
            )
            db.query("SELECT COUNT(*) FROM drop_segment").use {
                it.moveToFirst()
                assertEquals(1, it.getInt(0))
            }
        }
    }

    @Test
    fun migratesToDisplayUnitsLeavingEveryStoredWeightInKilograms() {
        helper.createDatabase(FIFTH_DATABASE, 5).use { version5 ->
            version5.execSQL(
                "INSERT INTO exercise (id, name, primary_muscles, secondary_muscles, equipment, " +
                    "mechanic, force, category, level, logging_type, unilateral, source) VALUES " +
                    "('Test_Exercise', 'Test Exercise', 'chest', '', 'barbell', 'compound', " +
                    "'push', 'strength', 'beginner', 'weight_reps', 0, 'upstream')",
            )
            version5.execSQL(
                "INSERT INTO session (started_at, finished_at, date, note, routine_name, " +
                    "routine_day_name) VALUES (1757869200000, NULL, '2026-09-14', NULL, NULL, NULL)",
            )
            version5.execSQL(
                "INSERT INTO session_exercise (session_id, exercise_id, order_index) " +
                    "VALUES (1, 'Test_Exercise', 1)",
            )
            version5.execSQL(
                "INSERT INTO set_entry (session_exercise_id, set_index, type, weight_kg, reps, " +
                    "note, side) VALUES (1, 1, 'working', 60.0, 5, NULL, NULL)",
            )
        }

        val version6 = helper.runMigrationsAndValidate(FIFTH_DATABASE, 6, true, MIGRATION_5_6)

        version6.use { db ->
            // The weight was canonical kilograms before units existed and is the same number after:
            // this migration adds a way to read a weight, not a way to change one.
            db.query("SELECT weight_kg, display_unit FROM set_entry").use {
                it.moveToFirst()
                assertEquals(60.0, it.getDouble(0), 0.0)
                // No override, so the set reads however its exercise does.
                assertTrue(it.isNull(1))
            }
            // And an exercise can now be told which unit it is read in.
            db.execSQL(
                "INSERT INTO exercise_preference (exercise_id, display_unit) " +
                    "VALUES ('Test_Exercise', 'lb')",
            )
            db.query("SELECT display_unit FROM exercise_preference").use {
                it.moveToFirst()
                assertEquals("lb", it.getString(0))
            }
        }
    }

    private companion object {
        const val TEST_DATABASE = "gym-migration-test.db"
        const val FOURTH_DATABASE = "gym-migration-test-4.db"
        const val SECOND_DATABASE = "gym-migration-test-2.db"
        const val THIRD_DATABASE = "gym-migration-test-3.db"
        const val FIFTH_DATABASE = "gym-migration-test-5.db"
    }
}
