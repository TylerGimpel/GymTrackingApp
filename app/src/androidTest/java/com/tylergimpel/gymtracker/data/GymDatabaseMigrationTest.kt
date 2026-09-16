package com.tylergimpel.gymtracker.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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

    @Test
    fun migratesToPlansLeavingEverySessionAlreadyLoggedWithoutOne() {
        helper.createDatabase(SIXTH_DATABASE, 6).use { version6 ->
            version6.execSQL(
                "INSERT INTO exercise (id, name, primary_muscles, secondary_muscles, equipment, " +
                    "mechanic, force, category, level, logging_type, unilateral, source) VALUES " +
                    "('Test_Exercise', 'Test Exercise', 'chest', '', 'barbell', 'compound', " +
                    "'push', 'strength', 'beginner', 'weight_reps', 0, 'upstream')",
            )
            version6.execSQL(
                "INSERT INTO session (started_at, finished_at, date, note, routine_name, " +
                    "routine_day_name) VALUES (1757869200000, NULL, '2026-09-14', NULL, NULL, NULL)",
            )
            version6.execSQL(
                "INSERT INTO routine (name, goal, cadence, equipment, rep_preference) " +
                    "VALUES ('Push / Pull / Legs', 'HYPERTROPHY', 4, 'barbell', 'MODERATE')",
            )
            version6.execSQL(
                "INSERT INTO routine_day (routine_id, order_index, name) VALUES (1, 1, 'Push')",
            )
        }

        val version7 = helper.runMigrationsAndValidate(SIXTH_DATABASE, 7, true, MIGRATION_6_7)

        version7.use { db ->
            // Both tables arrive empty, which is the truth about an installed copy: no day held
            // anything yet, and a plan cannot be reconstructed for a session already logged.
            db.query("SELECT COUNT(*) FROM routine_day_exercise").use {
                it.moveToFirst()
                assertEquals(0, it.getInt(0))
            }
            db.query("SELECT COUNT(*) FROM session_plan").use {
                it.moveToFirst()
                assertEquals(0, it.getInt(0))
            }
            // And both can now be written to.
            db.execSQL(
                "INSERT INTO routine_day_exercise (routine_day_id, exercise_id, order_index) " +
                    "VALUES (1, 'Test_Exercise', 1)",
            )
            db.execSQL(
                "INSERT INTO session_plan (session_id, exercise_id, order_index) " +
                    "VALUES (1, 'Test_Exercise', 1)",
            )
            db.query("SELECT exercise_id FROM session_plan").use {
                it.moveToFirst()
                assertEquals("Test_Exercise", it.getString(0))
            }
        }
    }

    @Test
    fun migratesToSupersetsCarryingEveryLoggedSetAcrossTheRebuiltTable() {
        helper.createDatabase(SEVENTH_DATABASE, 7).use { version7 ->
            version7.execSQL(
                "INSERT INTO exercise (id, name, primary_muscles, secondary_muscles, equipment, " +
                    "mechanic, force, category, level, logging_type, unilateral, source) VALUES " +
                    "('Test_Exercise', 'Test Exercise', 'chest', '', 'barbell', 'compound', " +
                    "'push', 'strength', 'beginner', 'weight_reps', 0, 'upstream')",
            )
            version7.execSQL(
                "INSERT INTO session (started_at, finished_at, date, note, routine_name, " +
                    "routine_day_name) VALUES (1757869200000, NULL, '2026-09-14', NULL, NULL, NULL)",
            )
            version7.execSQL(
                "INSERT INTO session_exercise (session_id, exercise_id, order_index) " +
                    "VALUES (1, 'Test_Exercise', 1)",
            )
            version7.execSQL(
                "INSERT INTO set_entry (session_exercise_id, set_index, type, weight_kg, reps, " +
                    "note, side, display_unit) VALUES (1, 1, 'working', 60.0, 5, NULL, NULL, NULL)",
            )
        }

        val version8 = helper.runMigrationsAndValidate(SEVENTH_DATABASE, 8, true, MIGRATION_7_8)

        version8.use { db ->
            // `session_exercise` is rebuilt rather than altered, so the thing to prove is that the
            // rows came across with their ids intact — a set points at one, and a set that lost its
            // exercise is a set lost.
            db.query("SELECT id, exercise_id, superset_group_id FROM session_exercise").use {
                it.moveToFirst()
                assertEquals(1, it.getInt(0))
                assertEquals("Test_Exercise", it.getString(1))
                // Nothing was grouped before there was a way to group it.
                assertTrue(it.isNull(2))
            }
            db.query("SELECT session_exercise_id, weight_kg FROM set_entry").use {
                it.moveToFirst()
                assertEquals(1, it.getInt(0))
                assertEquals(60.0, it.getDouble(1), 0.0)
            }
            // And a group can now be formed against the rebuilt table. What happens when one is
            // deleted is not asked here: foreign keys are not enforced on a migration helper's
            // database, so `SET_NULL` is proved against a real Room instance instead.
            db.execSQL(
                "INSERT INTO superset_group (session_id, order_index, auto_jump, rest_seconds) " +
                    "VALUES (1, 1, 1, NULL)",
            )
            db.execSQL("UPDATE session_exercise SET superset_group_id = 1 WHERE id = 1")
            db.query("SELECT superset_group_id FROM session_exercise").use {
                it.moveToFirst()
                assertEquals(1, it.getInt(0))
            }
        }
    }

    @Test
    fun migratesToLifterOwnedFieldsWithNothingDroppedRenamedOrAnnotatedYet() {
        helper.createDatabase(EIGHTH_DATABASE, 8).use { version8 ->
            version8.execSQL(
                "INSERT INTO exercise (id, name, primary_muscles, secondary_muscles, equipment, " +
                    "mechanic, force, category, level, logging_type, unilateral, source) VALUES " +
                    "('Test_Exercise', 'Test Exercise', 'chest', '', 'barbell', 'compound', " +
                    "'push', 'strength', 'beginner', 'weight_reps', 0, 'upstream')",
            )
            version8.execSQL(
                "INSERT INTO exercise_preference (exercise_id, display_unit) " +
                    "VALUES ('Test_Exercise', 'lb')",
            )
        }

        val version9 = helper.runMigrationsAndValidate(EIGHTH_DATABASE, 9, true, MIGRATION_8_9)

        version9.use { db ->
            // The unit the lifter had already chosen is still there, and the three new answers are
            // the truth about an installed copy: nothing dropped, renamed or annotated yet.
            db.query("SELECT display_unit, name, note FROM exercise_preference").use {
                it.moveToFirst()
                assertEquals("lb", it.getString(0))
                assertTrue(it.isNull(1))
                assertTrue(it.isNull(2))
            }
            db.query("SELECT tombstoned FROM exercise").use {
                it.moveToFirst()
                assertEquals(0, it.getInt(0))
            }
            // And a lifter's own exercise can now live beside the shipped ones.
            db.execSQL(
                "INSERT INTO exercise (id, name, primary_muscles, secondary_muscles, equipment, " +
                    "mechanic, force, category, level, logging_type, unilateral, source, " +
                    "tombstoned) VALUES ('8f14e45f-ceea-467a-9c2b-27e8a1d4f9b0', " +
                    "'Pendulum Squat', 'quadriceps', '', 'machine', 'compound', 'push', " +
                    "'strength', 'beginner', 'weight_reps', 0, 'user', 0)",
            )
            db.query("SELECT COUNT(*) FROM exercise WHERE source = 'user'").use {
                it.moveToFirst()
                assertEquals(1, it.getInt(0))
            }
        }
    }

    @Test
    fun migratesToBodyweightEntriesWithNoWeighInsAndNothingJoiningThemToTraining() {
        helper.createDatabase(NINTH_DATABASE, 9).use { version9 ->
            version9.execSQL(
                "INSERT INTO exercise (id, name, primary_muscles, secondary_muscles, equipment, " +
                    "mechanic, force, category, level, logging_type, unilateral, source, " +
                    "tombstoned) VALUES ('Test_Exercise', 'Test Exercise', 'chest', '', 'barbell', " +
                    "'compound', 'push', 'strength', 'beginner', 'weight_reps', 0, 'upstream', 0)",
            )
            version9.execSQL(
                "INSERT INTO session (started_at, finished_at, date, note, routine_name, " +
                    "routine_day_name) VALUES (1757869200000, NULL, '2026-09-14', NULL, NULL, NULL)",
            )
        }

        val version10 = helper.runMigrationsAndValidate(NINTH_DATABASE, 10, true, MIGRATION_9_10)

        version10.use { db ->
            // Empty, which is the truth about an installed copy: nobody has weighed themselves in
            // an app that had nowhere to put it, and a weight cannot be reconstructed after the
            // fact any more than a plan can.
            db.query("SELECT COUNT(*) FROM bodyweight_entry").use {
                it.moveToFirst()
                assertEquals(0, it.getInt(0))
            }
            // A weigh-in is recorded against a day and nothing else. There is no session column
            // here to point at the session above, which is what keeps it out of training.
            db.execSQL("INSERT INTO bodyweight_entry (date, weight_kg) VALUES ('2026-09-14', 82.4)")
            db.query("SELECT date, weight_kg FROM bodyweight_entry").use {
                it.moveToFirst()
                assertEquals("2026-09-14", it.getString(0))
                assertEquals(82.4, it.getDouble(1), 0.0)
            }
            // And a day holds one weight: stepping on the scale twice on a Monday corrects Monday.
            assertThrows(SQLiteConstraintException::class.java) {
                db.execSQL("INSERT INTO bodyweight_entry (date, weight_kg) VALUES ('2026-09-14', 82.9)")
            }
        }
    }

    @Test
    fun migratesToTimestampedWeighInsCarryingEveryReadingOntoTheStartOfItsOwnDay() {
        helper.createDatabase(TENTH_DATABASE, 10).use { version10 ->
            version10.execSQL(
                "INSERT INTO bodyweight_entry (date, weight_kg) VALUES ('2026-09-14', 82.4)",
            )
        }

        val version11 = helper.runMigrationsAndValidate(TENTH_DATABASE, 11, true, MIGRATION_10_11)

        version11.use { db ->
            // The reading survives the rebuild with its id, its day and its weight, and takes the
            // start of its own day as when it was recorded — the only honest answer for a row
            // written when nothing recorded a time, and one that leaves it ordered where it was.
            db.query("SELECT id, recorded_at, date, weight_kg FROM bodyweight_entry").use {
                it.moveToFirst()
                assertEquals(1, it.getInt(0))
                // 2026-09-14T00:00:00Z.
                assertEquals(1789344000000L, it.getLong(1))
                assertEquals("2026-09-14", it.getString(2))
                assertEquals(82.4, it.getDouble(3), 0.0)
            }
            // And a day can now hold the morning and the evening both, which version 10 refused.
            db.execSQL(
                "INSERT INTO bodyweight_entry (recorded_at, date, weight_kg) " +
                    "VALUES (1789394400000, '2026-09-14', 83.6)",
            )
            db.query("SELECT COUNT(*) FROM bodyweight_entry WHERE date = '2026-09-14'").use {
                it.moveToFirst()
                assertEquals(2, it.getInt(0))
            }
        }
    }

    @Test
    fun migratesToAProfileWithNothingChosenInIt() {
        helper.createDatabase(ELEVENTH_DATABASE, 11).use { version11 ->
            version11.execSQL(
                "INSERT INTO bodyweight_entry (recorded_at, date, weight_kg) " +
                    "VALUES (1789344000000, '2026-09-14', 82.4)",
            )
        }

        val version12 = helper.runMigrationsAndValidate(ELEVENTH_DATABASE, 12, true, MIGRATION_11_12)

        version12.use { db ->
            // Empty, which is the truth about an installed copy: nobody has said what unit they
            // read in, so the device's locale answers until they tap a chip.
            db.query("SELECT COUNT(*) FROM profile").use {
                it.moveToFirst()
                assertEquals(0, it.getInt(0))
            }
            // What they weigh is untouched by where the unit they read it in now lives.
            db.query("SELECT weight_kg FROM bodyweight_entry").use {
                it.moveToFirst()
                assertEquals(82.4, it.getDouble(0), 0.0)
            }
            db.execSQL("INSERT INTO profile (id, display_unit) VALUES (1, 'lb')")
            db.query("SELECT display_unit FROM profile WHERE id = 1").use {
                it.moveToFirst()
                assertEquals("lb", it.getString(0))
            }
        }
    }

    @Test
    fun migratesToSetsThatNeedNotBeAWeightAndReps() {
        helper.createDatabase(TWELFTH_DATABASE, 12).use { version12 ->
            version12.execSQL(
                "INSERT INTO exercise (id, name, primary_muscles, secondary_muscles, equipment, " +
                    "mechanic, force, category, level, logging_type, unilateral, source, " +
                    "tombstoned) VALUES ('Test_Exercise', 'Test Exercise', 'chest', '', 'barbell', " +
                    "'compound', 'push', 'strength', 'beginner', 'weight_reps', 0, 'upstream', 0)",
            )
            version12.execSQL(
                "INSERT INTO session (started_at, finished_at, date, note, routine_name, " +
                    "routine_day_name) VALUES (1757869200000, NULL, '2026-09-14', NULL, NULL, NULL)",
            )
            version12.execSQL(
                "INSERT INTO session_exercise (session_id, exercise_id, order_index) " +
                    "VALUES (1, 'Test_Exercise', 1)",
            )
            version12.execSQL(
                "INSERT INTO set_entry (session_exercise_id, set_index, type, weight_kg, reps, " +
                    "note, side, display_unit) VALUES (1, 1, 'working', 60.0, 5, 'felt heavy', " +
                    "NULL, 'lb')",
            )
            // A drop hangs off that row by its id, which is what the rebuild must not move.
            version12.execSQL(
                "INSERT INTO drop_segment (set_entry_id, order_index, weight_kg, reps) " +
                    "VALUES (1, 1, 50.0, 4)",
            )
        }

        val version13 = helper.runMigrationsAndValidate(TWELFTH_DATABASE, 13, true, MIGRATION_12_13)

        version13.use { db ->
            // `set_entry` is rebuilt so its weight and reps can be null, and everything logged has
            // to come across unchanged — the id above all, because a drop points at it and a set is
            // identified by its position rather than by that id (ADR 0003).
            db.query(
                "SELECT id, session_exercise_id, set_index, type, weight_kg, reps, " +
                    "duration_seconds, note, side, display_unit FROM set_entry",
            ).use {
                it.moveToFirst()
                assertEquals(1, it.getInt(0))
                assertEquals(1, it.getInt(1))
                assertEquals(1, it.getInt(2))
                assertEquals("working", it.getString(3))
                assertEquals(60.0, it.getDouble(4), 0.0)
                assertEquals(5, it.getInt(5))
                // Nothing logged before this could have recorded a time.
                assertTrue(it.isNull(6))
                assertEquals("felt heavy", it.getString(7))
                assertTrue(it.isNull(8))
                assertEquals("lb", it.getString(9))
            }
            db.query("SELECT set_entry_id FROM drop_segment").use {
                it.moveToFirst()
                assertEquals(1, it.getInt(0))
            }
            // And a set that is a length of time and nothing else can now be written.
            db.execSQL(
                "INSERT INTO set_entry (session_exercise_id, set_index, type, duration_seconds) " +
                    "VALUES (1, 2, 'working', 90)",
            )
            db.query("SELECT duration_seconds, weight_kg, reps FROM set_entry WHERE set_index = 2").use {
                it.moveToFirst()
                assertEquals(90, it.getInt(0))
                assertTrue(it.isNull(1))
                assertTrue(it.isNull(2))
            }
        }
    }

    @Test
    fun migratesToSetsThatRecordHowFarTheyWentWithNothingAlreadyLoggedClaimingADistance() {
        helper.createDatabase(THIRTEENTH_DATABASE, 13).use { version13 ->
            version13.execSQL(
                "INSERT INTO exercise (id, name, primary_muscles, secondary_muscles, equipment, " +
                    "mechanic, force, category, level, logging_type, unilateral, source, " +
                    "tombstoned) VALUES ('Test_Exercise', 'Test Exercise', 'chest', '', 'barbell', " +
                    "'compound', 'push', 'strength', 'beginner', 'weight_reps', 0, 'upstream', 0)",
            )
            version13.execSQL(
                "INSERT INTO session (started_at, finished_at, date, note, routine_name, " +
                    "routine_day_name) VALUES (1757869200000, NULL, '2026-09-14', NULL, NULL, NULL)",
            )
            version13.execSQL(
                "INSERT INTO session_exercise (session_id, exercise_id, order_index) " +
                    "VALUES (1, 'Test_Exercise', 1)",
            )
            version13.execSQL(
                "INSERT INTO set_entry (session_exercise_id, set_index, type, weight_kg, reps) " +
                    "VALUES (1, 1, 'working', 60.0, 5)",
            )
        }

        val version14 = helper.runMigrationsAndValidate(THIRTEENTH_DATABASE, 14, true, MIGRATION_13_14)

        version14.use { db ->
            // The lift is untouched and covered no ground, which is the truth about every set
            // logged when there was nowhere to record a distance.
            db.query("SELECT weight_kg, reps, distance_m FROM set_entry").use {
                it.moveToFirst()
                assertEquals(60.0, it.getDouble(0), 0.0)
                assertEquals(5, it.getInt(1))
                assertTrue(it.isNull(2))
            }
            // And a run can now be written: how far, how long, and no weight at all.
            db.execSQL(
                "INSERT INTO set_entry (session_exercise_id, set_index, type, duration_seconds, " +
                    "distance_m) VALUES (1, 2, 'working', 1680, 5000.0)",
            )
            db.query(
                "SELECT distance_m, duration_seconds, weight_kg FROM set_entry WHERE set_index = 2",
            ).use {
                it.moveToFirst()
                assertEquals(5000.0, it.getDouble(0), 0.0)
                assertEquals(1680, it.getInt(1))
                assertTrue(it.isNull(2))
            }
        }
    }

    @Test
    fun migratesToBeltsAndAssistanceMovingWhatWasAlreadyTypedIntoTheWeightField() {
        helper.createDatabase(FOURTEENTH_DATABASE, 14).use { version14 ->
            // Both types showed a plain weight field until version 15, so this is where a belt and
            // a machine's setting were put: the only place a lifter had for them.
            listOf(
                "'Pullups', 'Pullups', 'lats', '', 'body only', 'compound', 'pull', 'strength', " +
                    "'beginner', 'bodyweight_reps'",
                "'Assisted_Pull_Up_Machine', 'Assisted Pull-Up Machine', 'lats', '', 'machine', " +
                    "'compound', 'pull', 'strength', 'beginner', 'assisted'",
                "'Barbell_Bench', 'Barbell Bench', 'chest', '', 'barbell', 'compound', 'push', " +
                    "'strength', 'beginner', 'weight_reps'",
            ).forEach { columns ->
                version14.execSQL(
                    "INSERT INTO exercise (id, name, primary_muscles, secondary_muscles, " +
                        "equipment, mechanic, force, category, level, logging_type, unilateral, " +
                        "source, tombstoned) VALUES ($columns, 0, 'upstream', 0)",
                )
            }
            version14.execSQL(
                "INSERT INTO session (started_at, finished_at, date, note, routine_name, " +
                    "routine_day_name) VALUES (1757869200000, NULL, '2026-09-14', NULL, NULL, NULL)",
            )
            listOf("Pullups", "Assisted_Pull_Up_Machine", "Barbell_Bench").forEachIndexed { index, id ->
                version14.execSQL(
                    "INSERT INTO session_exercise (session_id, exercise_id, order_index) " +
                        "VALUES (1, '$id', ${index + 1})",
                )
            }
            // A belted pull-up, an unweighted one, 20 kg of help, and an ordinary bench press.
            version14.execSQL(
                "INSERT INTO set_entry (session_exercise_id, set_index, type, weight_kg, reps) " +
                    "VALUES (1, 1, 'working', 20.0, 6), (1, 2, 'working', 0.0, 8), " +
                    "(2, 1, 'working', 20.0, 8), (3, 1, 'working', 60.0, 5)",
            )
        }

        val version15 = helper.runMigrationsAndValidate(FOURTEENTH_DATABASE, 15, true, MIGRATION_14_15)

        version15.use { db ->
            // The belt moves across as added weight, and reads as load added.
            db.query("SELECT weight_kg, added_weight_kg, reps FROM set_entry WHERE session_exercise_id = 1 ORDER BY set_index").use {
                it.moveToFirst()
                assertTrue(it.isNull(0))
                assertEquals(20.0, it.getDouble(1), 0.0)
                assertEquals(6, it.getInt(2))
                // A zero was "no belt", and becomes no belt rather than a belt of nothing.
                it.moveToNext()
                assertTrue(it.isNull(0))
                assertTrue(it.isNull(1))
            }
            // The machine's help moves across negative, which is what stops 30 kg of it outranking
            // 20 and reading as progress — ADR 0004's exact failure.
            db.query("SELECT weight_kg, added_weight_kg FROM set_entry WHERE session_exercise_id = 2").use {
                it.moveToFirst()
                assertTrue(it.isNull(0))
                assertEquals(-20.0, it.getDouble(1), 0.0)
            }
            // And a barbell set is untouched: it always did mean what it said.
            db.query("SELECT weight_kg, added_weight_kg FROM set_entry WHERE session_exercise_id = 3").use {
                it.moveToFirst()
                assertEquals(60.0, it.getDouble(0), 0.0)
                assertTrue(it.isNull(1))
            }
        }
    }

    @Test
    fun migratesToPrescriptionsAndMeasurementsWithNeitherClaimedByAnythingAlreadyLogged() {
        helper.createDatabase(FIFTEENTH_DATABASE, 15).use { version15 ->
            version15.execSQL(
                "INSERT INTO exercise (id, name, primary_muscles, secondary_muscles, equipment, " +
                    "mechanic, force, category, level, logging_type, unilateral, source, " +
                    "tombstoned) VALUES ('Test_Exercise', 'Test Exercise', 'chest', '', 'barbell', " +
                    "'compound', 'push', 'strength', 'beginner', 'weight_reps', 0, 'upstream', 0)",
            )
            version15.execSQL(
                "INSERT INTO session (started_at, finished_at, date, note, routine_name, " +
                    "routine_day_name) VALUES (1757869200000, NULL, '2026-09-14', NULL, NULL, NULL)",
            )
            version15.execSQL(
                "INSERT INTO session_exercise (session_id, exercise_id, order_index) " +
                    "VALUES (1, 'Test_Exercise', 1)",
            )
            version15.execSQL(
                "INSERT INTO set_entry (session_exercise_id, set_index, type, weight_kg, reps) " +
                    "VALUES (1, 1, 'working', 60.0, 5)",
            )
        }

        val version16 = helper.runMigrationsAndValidate(FIFTEENTH_DATABASE, 16, true, MIGRATION_15_16)

        version16.use { db ->
            // Nothing already logged was prescribed anything: there was no engine to prescribe it.
            db.query(
                "SELECT weight_kg, recommended_weight_kg, recommended_reps, " +
                    "recommended_rest_seconds, actual_rest_seconds, rpe, heart_rate FROM set_entry",
            ).use {
                it.moveToFirst()
                assertEquals(60.0, it.getDouble(0), 0.0)
                (1..6).forEach { column -> assertTrue(it.isNull(column)) }
            }
            // And a set can now carry what it was asked for beside what was done.
            db.execSQL(
                "UPDATE set_entry SET recommended_weight_kg = 62.5, recommended_reps = 8, " +
                    "recommended_rest_seconds = 120, actual_rest_seconds = 145, rpe = 8.5, " +
                    "heart_rate = 142 WHERE id = 1",
            )
            db.query("SELECT recommended_weight_kg, recommended_reps, rpe FROM set_entry").use {
                it.moveToFirst()
                assertEquals(62.5, it.getDouble(0), 0.0)
                assertEquals(8, it.getInt(1))
                assertEquals(8.5, it.getDouble(2), 0.0)
            }
        }
    }

    @Test
    fun migratesToProgressionModelsWithNothingPrescribedForWhatIsOnlyRecorded() {
        helper.createDatabase(SIXTEENTH_DATABASE, 16).use { version16 ->
            listOf(
                "'Barbell_Bench', 'Barbell Bench', 'chest', '', 'barbell', 'weight_reps'",
                "'Plank', 'Plank', 'abdominals', '', 'body only', 'time'",
                "'Treadmill', 'Treadmill', 'quadriceps', '', 'machine', 'distance_time'",
            ).forEach { columns ->
                version16.execSQL(
                    "INSERT INTO exercise (id, name, primary_muscles, secondary_muscles, " +
                        "equipment, logging_type, mechanic, force, category, level, unilateral, " +
                        "source, tombstoned) VALUES ($columns, 'compound', 'push', 'strength', " +
                        "'beginner', 0, 'upstream', 0)",
                )
            }
            version16.execSQL(
                "INSERT INTO exercise_preference (exercise_id, display_unit) " +
                    "VALUES ('Barbell_Bench', 'lb')",
            )
        }

        val version17 = helper.runMigrationsAndValidate(SIXTEENTH_DATABASE, 17, true, MIGRATION_16_17)

        version17.use { db ->
            // Anything with a load takes what ADR 0004 settles; anything measured in time or
            // distance is recorded and never prescribed.
            db.query("SELECT id, progression_model FROM exercise ORDER BY id").use {
                it.moveToFirst()
                assertEquals("Barbell_Bench", it.getString(0))
                assertEquals("double_progression", it.getString(1))
                it.moveToNext()
                assertEquals("Plank", it.getString(0))
                assertEquals("none", it.getString(1))
                it.moveToNext()
                assertEquals("Treadmill", it.getString(0))
                assertEquals("none", it.getString(1))
            }
            // The unit the lifter had already chosen survives, and they have chosen nothing about
            // coaching yet.
            db.query("SELECT display_unit, progression_model FROM exercise_preference").use {
                it.moveToFirst()
                assertEquals("lb", it.getString(0))
                assertTrue(it.isNull(1))
            }
        }
    }

    private companion object {
        const val TEST_DATABASE = "gym-migration-test.db"
        const val FOURTH_DATABASE = "gym-migration-test-4.db"
        const val SECOND_DATABASE = "gym-migration-test-2.db"
        const val THIRD_DATABASE = "gym-migration-test-3.db"
        const val FIFTH_DATABASE = "gym-migration-test-5.db"
        const val SIXTH_DATABASE = "gym-migration-test-6.db"
        const val SEVENTH_DATABASE = "gym-migration-test-7.db"
        const val EIGHTH_DATABASE = "gym-migration-test-8.db"
        const val NINTH_DATABASE = "gym-migration-test-9.db"
        const val TENTH_DATABASE = "gym-migration-test-10.db"
        const val ELEVENTH_DATABASE = "gym-migration-test-11.db"
        const val TWELFTH_DATABASE = "gym-migration-test-12.db"
        const val THIRTEENTH_DATABASE = "gym-migration-test-13.db"
        const val FOURTEENTH_DATABASE = "gym-migration-test-14.db"
        const val FIFTEENTH_DATABASE = "gym-migration-test-15.db"
        const val SIXTEENTH_DATABASE = "gym-migration-test-16.db"
    }
}
