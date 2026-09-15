package com.tylergimpel.gymtracker.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version 1 shipped the exercise bank alone. Version 2 adds the log: a session, the exercises in
 * it, and the sets logged against those.
 *
 * The statements below are copied verbatim from Room's exported schema for version 2. They have to
 * be: an installed copy migrated with DDL that differs from what Room expects — even by a default
 * or an index — fails the identity check on the next open, and that is a crash rather than a
 * compile error. `GymDatabaseMigrationTest` re-checks them against the exported schema.
 */
internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `session` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `started_at` INTEGER NOT NULL, `finished_at` INTEGER, `date` TEXT NOT NULL, `note` TEXT)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `session_exercise` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `session_id` INTEGER NOT NULL, `exercise_id` TEXT NOT NULL, `order_index` INTEGER NOT NULL, FOREIGN KEY(`session_id`) REFERENCES `session`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`exercise_id`) REFERENCES `exercise`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_session_exercise_session_id` ON `session_exercise` (`session_id`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_session_exercise_exercise_id` ON `session_exercise` (`exercise_id`)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `set_entry` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `session_exercise_id` INTEGER NOT NULL, `set_index` INTEGER NOT NULL, `weight_kg` REAL NOT NULL, `reps` INTEGER NOT NULL, `note` TEXT, FOREIGN KEY(`session_exercise_id`) REFERENCES `session_exercise`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_set_entry_session_exercise_id` ON `set_entry` (`session_exercise_id`)",
        )
    }
}

/**
 * Version 3 adds the routine, its ordered days, and the cycle state the cursor is derived from.
 *
 * The two columns added to `session` are the snapshot that makes revising a routine safe: names
 * only, deliberately nullable, and deliberately not a foreign key.
 */
internal val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `routine` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `goal` TEXT NOT NULL, `cadence` INTEGER NOT NULL, `equipment` TEXT NOT NULL, `rep_preference` TEXT NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `routine_day` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `routine_id` INTEGER NOT NULL, `order_index` INTEGER NOT NULL, `name` TEXT NOT NULL, FOREIGN KEY(`routine_id`) REFERENCES `routine`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_routine_day_routine_id` ON `routine_day` (`routine_id`)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `cycle` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `routine_id` INTEGER NOT NULL, `ordinal` INTEGER NOT NULL, `started_at` INTEGER NOT NULL, FOREIGN KEY(`routine_id`) REFERENCES `routine`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_cycle_routine_id` ON `cycle` (`routine_id`)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `cycle_completion` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `cycle_id` INTEGER NOT NULL, `routine_day_id` INTEGER NOT NULL, `session_id` INTEGER NOT NULL, FOREIGN KEY(`cycle_id`) REFERENCES `cycle`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`routine_day_id`) REFERENCES `routine_day`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`session_id`) REFERENCES `session`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_cycle_completion_cycle_id` ON `cycle_completion` (`cycle_id`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_cycle_completion_routine_day_id` ON `cycle_completion` (`routine_day_id`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_cycle_completion_session_id` ON `cycle_completion` (`session_id`)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `skip` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `cycle_id` INTEGER NOT NULL, `routine_day_id` INTEGER NOT NULL, `recorded_at` INTEGER NOT NULL, FOREIGN KEY(`cycle_id`) REFERENCES `cycle`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`routine_day_id`) REFERENCES `routine_day`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_skip_cycle_id` ON `skip` (`cycle_id`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_skip_routine_day_id` ON `skip` (`routine_day_id`)",
        )
        db.execSQL(
            "ALTER TABLE `session` ADD COLUMN `routine_name` TEXT",
        )
        db.execSQL(
            "ALTER TABLE `session` ADD COLUMN `routine_day_name` TEXT",
        )
    }
}

/**
 * Version 4 gives every set an explicit type.
 *
 * The first migration here that has to put a value in existing rows rather than only add tables.
 * The default is declared on the entity too, because Room compares defaults when it validates a
 * migrated schema: an `ALTER` whose default differs from the entity's fails the identity check on
 * the next open, which is a crash rather than a compile error.
 */
internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `set_entry` ADD COLUMN `type` TEXT NOT NULL " +
                "DEFAULT '" + SetEntryEntity.DEFAULT_TYPE + "'",
        )
    }
}

/**
 * Version 5 lets one set be recorded by more than one row.
 *
 * `side` is nullable, so every set already logged stays exactly what it was: a single entry, with
 * no side, unchanged. The drops table is new and empty — nothing before this could have had any.
 */
internal val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `drop_segment` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `set_entry_id` INTEGER NOT NULL, `order_index` INTEGER NOT NULL, `weight_kg` REAL NOT NULL, `reps` INTEGER NOT NULL, FOREIGN KEY(`set_entry_id`) REFERENCES `set_entry`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_drop_segment_set_entry_id` ON `drop_segment` (`set_entry_id`)",
        )
        db.execSQL(
            "ALTER TABLE `set_entry` ADD COLUMN `side` TEXT",
        )
    }
}

/**
 * Version 6 gives an exercise a unit to be read in, and a set the right to depart from it.
 *
 * Nothing about what is stored changes: `weight_kg` was canonical kilograms before this migration
 * and still is after it. The new table is empty and `display_unit` is null on every existing row,
 * which together say "every exercise reads in kilograms and no set has overridden that" — exactly
 * what was true of a database that had no notion of units at all.
 *
 * The preferences are a table of their own rather than columns on `exercise`, for the reasons in
 * `docs/adr/0009-user-choices-live-apart-from-the-shipped-bank.md`.
 */
internal val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `exercise_preference` (`exercise_id` TEXT NOT NULL, `display_unit` TEXT NOT NULL, PRIMARY KEY(`exercise_id`), FOREIGN KEY(`exercise_id`) REFERENCES `exercise`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "ALTER TABLE `set_entry` ADD COLUMN `display_unit` TEXT",
        )
    }
}

/**
 * Version 7 gives a routine day its exercises, and a session a record of what it was for.
 *
 * Both tables arrive empty, which reads correctly on an installed copy: every routine day holds
 * nothing until a lifter fills it in, and every session already logged was for whatever it turned
 * out to contain. A plan cannot be reconstructed after the fact, and inventing one would put words
 * in the lifter's mouth about sessions they have already finished.
 *
 * `session_plan` has no foreign key to `routine_day` on purpose: the plan is a copy, so revising a
 * routine cannot reach it.
 */
internal val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `routine_day_exercise` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `routine_day_id` INTEGER NOT NULL, `exercise_id` TEXT NOT NULL, `order_index` INTEGER NOT NULL, FOREIGN KEY(`routine_day_id`) REFERENCES `routine_day`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`exercise_id`) REFERENCES `exercise`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_routine_day_exercise_routine_day_id_exercise_id` ON `routine_day_exercise` (`routine_day_id`, `exercise_id`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_routine_day_exercise_exercise_id` ON `routine_day_exercise` (`exercise_id`)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `session_plan` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `session_id` INTEGER NOT NULL, `exercise_id` TEXT NOT NULL, `order_index` INTEGER NOT NULL, FOREIGN KEY(`session_id`) REFERENCES `session`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`exercise_id`) REFERENCES `exercise`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_session_plan_session_id` ON `session_plan` (`session_id`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_session_plan_exercise_id` ON `session_plan` (`exercise_id`)",
        )
    }
}

internal val MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
    MIGRATION_3_4,
    MIGRATION_4_5,
    MIGRATION_5_6,
    MIGRATION_6_7,
)
