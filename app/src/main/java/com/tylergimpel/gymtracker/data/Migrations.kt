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

internal val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
