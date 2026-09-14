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
                "INSERT INTO routine_day (routine_id, order_index, name, is_cardio) " +
                    "VALUES (1, 1, 'Push', 0)",
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

    private companion object {
        const val TEST_DATABASE = "gym-migration-test.db"
        const val SECOND_DATABASE = "gym-migration-test-2.db"
    }
}
