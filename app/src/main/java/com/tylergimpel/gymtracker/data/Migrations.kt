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

/**
 * Version 8 lets a session group exercises performed alternately.
 *
 * `session_exercise` is rebuilt rather than altered, because the new column carries a foreign key
 * and SQLite cannot add one to an existing table. This is the shape Room's own generated migrations
 * take: build the table as version 8 declares it, copy every row across, swap the names. Every set
 * stays where it was — `set_entry` points at `session_exercise.id`, and the ids are carried over
 * unchanged.
 *
 * Existing rows take a null group, which is the truth about them: nothing was grouped before there
 * was a way to group it.
 */
internal val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `superset_group` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `session_id` INTEGER NOT NULL, `order_index` INTEGER NOT NULL, `auto_jump` INTEGER NOT NULL, `rest_seconds` INTEGER, FOREIGN KEY(`session_id`) REFERENCES `session`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_superset_group_session_id` ON `superset_group` (`session_id`)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `_new_session_exercise` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `session_id` INTEGER NOT NULL, `exercise_id` TEXT NOT NULL, `order_index` INTEGER NOT NULL, `superset_group_id` INTEGER, FOREIGN KEY(`session_id`) REFERENCES `session`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`exercise_id`) REFERENCES `exercise`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`superset_group_id`) REFERENCES `superset_group`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
        )
        db.execSQL(
            "INSERT INTO `_new_session_exercise` (`id`, `session_id`, `exercise_id`, `order_index`) " +
                "SELECT `id`, `session_id`, `exercise_id`, `order_index` FROM `session_exercise`",
        )
        db.execSQL("DROP TABLE `session_exercise`")
        db.execSQL("ALTER TABLE `_new_session_exercise` RENAME TO `session_exercise`")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_session_exercise_session_id` ON `session_exercise` (`session_id`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_session_exercise_exercise_id` ON `session_exercise` (`exercise_id`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_session_exercise_superset_group_id` ON `session_exercise` (`superset_group_id`)",
        )
        // As Room's own generated rebuilds do: a copy that broke a reference should say so here,
        // rather than surface later as a session missing its sets.
        db.execSQL("PRAGMA foreign_key_check(`session_exercise`)")
    }
}

/**
 * Version 9 lets a lifter own part of the exercise bank.
 *
 * `exercise.tombstoned` marks a shipped row an update dropped that something still points at.
 * `exercise_preference` gains a name and a note — what the lifter calls an exercise and what they
 * wrote about it — and is rebuilt rather than altered so its unit can become nullable: this row
 * holds only what has actually been chosen, and a lifter who renamed an exercise and said nothing
 * about its unit has not chosen kilograms.
 *
 * The rename lives beside the unit rather than on the bank row, for the reason ADR 0009 gives: the
 * bank row is a bank update's to correct, and what the lifter decided is not. It also keeps the
 * name the bank shipped, which is what the search aliases resolve against.
 *
 * Every existing row takes `tombstoned = 0` and a null name and note, which is the truth about an
 * installed copy: nothing has been dropped, renamed or annotated yet.
 */
internal val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `exercise` ADD COLUMN `tombstoned` INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `_new_exercise_preference` (`exercise_id` TEXT NOT NULL, `display_unit` TEXT, `name` TEXT, `note` TEXT, PRIMARY KEY(`exercise_id`), FOREIGN KEY(`exercise_id`) REFERENCES `exercise`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "INSERT INTO `_new_exercise_preference` (`exercise_id`, `display_unit`) " +
                "SELECT `exercise_id`, `display_unit` FROM `exercise_preference`",
        )
        db.execSQL("DROP TABLE `exercise_preference`")
        db.execSQL("ALTER TABLE `_new_exercise_preference` RENAME TO `exercise_preference`")
        db.execSQL("PRAGMA foreign_key_check(`exercise_preference`)")
    }
}

/**
 * Version 10 gives the lifter somewhere to record what they weigh.
 *
 * A table of its own with no foreign key and no session column, which is the point: a weigh-in
 * shares the calendar with training and nothing else, so there is nothing here for a set or a
 * volume query to reach.
 *
 * It arrives empty, which is the truth about an installed copy — nobody has weighed themselves in
 * an app that had nowhere to put it. `date` is unique, because a day holds one weight.
 */
internal val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `bodyweight_entry` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` TEXT NOT NULL, `weight_kg` REAL NOT NULL)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_bodyweight_entry_date` ON `bodyweight_entry` (`date`)",
        )
    }
}

/**
 * Version 11 gives a weigh-in the moment it was taken, and lets a day hold more than one.
 *
 * Version 10 made a day unique, on the reasoning that two readings for one day is a question with
 * two answers. It is not: bodyweight swings a kilogram or two between morning and evening, so a
 * lifter who steps on the scale twice has measured that swing, and the way to tell the readings
 * apart is to record when each was taken rather than to keep only the last.
 *
 * The table is rebuilt rather than altered, because the unique index is the thing being dropped and
 * `recorded_at` is not null. Every reading already stored keeps its id, its day and its weight, and
 * takes the start of its own day as the moment it was recorded — the only honest answer for a row
 * written when nothing recorded a time, and one that leaves it ordered exactly where it was.
 */
internal val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `_new_bodyweight_entry` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `recorded_at` INTEGER NOT NULL, `date` TEXT NOT NULL, `weight_kg` REAL NOT NULL)",
        )
        db.execSQL(
            "INSERT INTO `_new_bodyweight_entry` (`id`, `recorded_at`, `date`, `weight_kg`) " +
                "SELECT `id`, CAST(strftime('%s', `date`) AS INTEGER) * 1000, `date`, `weight_kg` " +
                "FROM `bodyweight_entry`",
        )
        db.execSQL("DROP TABLE `bodyweight_entry`")
        db.execSQL("ALTER TABLE `_new_bodyweight_entry` RENAME TO `bodyweight_entry`")
    }
}

/**
 * Version 12 gives the lifter somewhere to keep what they read weights in.
 *
 * One row, arriving empty, which is the truth about an installed copy: nobody has said what unit
 * they think in, so the device's locale answers until they tap a chip and settle it. Kept apart from
 * `exercise_preference` because it is not about any exercise — that table says what a machine is
 * labelled in, and this says what a person reads.
 */
internal val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `profile` (`id` INTEGER NOT NULL, `display_unit` TEXT, PRIMARY KEY(`id`))",
        )
    }
}

/**
 * Version 13 lets a set record something other than a weight and a rep count.
 *
 * `weight_kg` and `reps` were `NOT NULL`, and a timed set has neither — a plank is a length of time
 * and nothing else. SQLite cannot drop `NOT NULL` in place, so `set_entry` is rebuilt the way Room's
 * own generated migrations do it: build the table as version 13 declares it, copy every row across,
 * swap the names.
 *
 * **Every row keeps its id.** `drop_segment` points at `set_entry.id`, and a set is identified by
 * its position within its exercise rather than by that id (ADR 0003) — so an id that moved would
 * strand a drop against a set that no longer exists, silently, in history nobody is looking at.
 *
 * Nothing already logged changes meaning: every existing set had a weight and reps and still has
 * them, and `duration_seconds` is null on all of them, which is the truth about sets logged when
 * there was no way to record time.
 */
internal val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `_new_set_entry` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `session_exercise_id` INTEGER NOT NULL, `set_index` INTEGER NOT NULL, `type` TEXT NOT NULL DEFAULT 'working', `weight_kg` REAL, `reps` INTEGER, `duration_seconds` INTEGER, `note` TEXT, `side` TEXT, `display_unit` TEXT, FOREIGN KEY(`session_exercise_id`) REFERENCES `session_exercise`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "INSERT INTO `_new_set_entry` (`id`, `session_exercise_id`, `set_index`, `type`, " +
                "`weight_kg`, `reps`, `note`, `side`, `display_unit`) " +
                "SELECT `id`, `session_exercise_id`, `set_index`, `type`, `weight_kg`, `reps`, " +
                "`note`, `side`, `display_unit` FROM `set_entry`",
        )
        db.execSQL("DROP TABLE `set_entry`")
        db.execSQL("ALTER TABLE `_new_set_entry` RENAME TO `set_entry`")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_set_entry_session_exercise_id` ON `set_entry` (`session_exercise_id`)",
        )
        db.failIfReferencesBroke("set_entry")
    }
}

/**
 * Version 14 lets a set record how far it went.
 *
 * A plain column rather than a rebuild: `distance_m` is nullable, so every set already logged
 * answers "no distance", which is the truth about all of them — nothing before this could record
 * one. What a lifter reads a distance in is not stored, here or anywhere: it follows the unit their
 * weights are read in, so there is nothing to migrate and nothing that can fall out of step.
 */
internal val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `set_entry` ADD COLUMN `distance_m` REAL")
    }
}

/**
 * Version 15 lets a set record a belt, or a machine's help.
 *
 * One nullable column for both, and **signed**: positive is weight hung off the lifter, negative is
 * weight taken off them. One measurement read two ways, and the sign is what makes the row say
 * which without asking the exercise — an exercise retagged by a later bank update must not be able
 * to turn somebody's assisted sets into weighted ones.
 *
 * It also settles a direction. ADR 0004 records that an assisted exercise inverts every comparison,
 * because less help is more progress, and that getting it wrong means confidently coaching somebody
 * to get weaker. Storing the sign means nothing downstream has to invert anything: more is more, on
 * every exercise, by arithmetic rather than by remembering.
 *
 * **Sets already logged against these exercises are moved, not left.** Until this version both types
 * showed a plain weight field, because it was the only place a lifter had to put a belt or a
 * machine's setting — so those numbers are sitting in `weight_kg`, where they now read as load
 * lifted. On an assisted exercise that is the ADR's exact failure: 30 kg of help would outrank 20,
 * and weaning off the machine would read as getting weaker. The logging type is the only record of
 * what those numbers meant, so it is read here, once, to put them where they belong. A zero on a
 * bodyweight set was "no belt" and becomes no belt.
 */
internal val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `set_entry` ADD COLUMN `added_weight_kg` REAL")

        db.execSQL(
            "UPDATE `set_entry` SET `added_weight_kg` = -`weight_kg`, `weight_kg` = NULL " +
                "WHERE `weight_kg` IS NOT NULL AND `weight_kg` > 0 AND `session_exercise_id` IN (" +
                "SELECT `se`.`id` FROM `session_exercise` `se` " +
                "JOIN `exercise` `e` ON `e`.`id` = `se`.`exercise_id` " +
                "WHERE `e`.`logging_type` = 'assisted')",
        )
        db.execSQL(
            "UPDATE `set_entry` SET " +
                "`added_weight_kg` = CASE WHEN `weight_kg` > 0 THEN `weight_kg` ELSE NULL END, " +
                "`weight_kg` = NULL " +
                "WHERE `weight_kg` IS NOT NULL AND `session_exercise_id` IN (" +
                "SELECT `se`.`id` FROM `session_exercise` `se` " +
                "JOIN `exercise` `e` ON `e`.`id` = `se`.`exercise_id` " +
                "WHERE `e`.`logging_type` IN ('bodyweight_reps', 'assisted'))",
        )
    }
}

internal val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `set_entry` ADD COLUMN `recommended_weight_kg` REAL")
        db.execSQL("ALTER TABLE `set_entry` ADD COLUMN `recommended_reps` INTEGER")
        db.execSQL("ALTER TABLE `set_entry` ADD COLUMN `recommended_rest_seconds` INTEGER")
        db.execSQL("ALTER TABLE `set_entry` ADD COLUMN `actual_rest_seconds` INTEGER")
        db.execSQL("ALTER TABLE `set_entry` ADD COLUMN `rpe` REAL")
        db.execSQL("ALTER TABLE `set_entry` ADD COLUMN `heart_rate` INTEGER")
    }
}

/**
 * Version 17 gives an exercise a progression model, and the lifter somewhere to overrule it.
 *
 * The last of the five fields spec #24 adds to an exercise. Two columns, because they answer
 * different questions: what the bank derived from what the exercise measures, and what the lifter
 * decided instead. The second lives in `exercise_preference` for the reason ADR 0009 gives — an
 * update writes bank rows and never touches that table, so an improvement to the derivation cannot
 * overrule somebody who said they do not want a lift coached.
 *
 * Every existing row takes the model its logging type derives, which is what the shipped bank would
 * have given it: nothing prescribed for a stretch, a hold or a treadmill, and double progression for
 * anything with a load (ADR 0004). The default on the `ALTER` matches the entity, because Room
 * compares defaults when it validates a migrated schema.
 */
internal val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `exercise` ADD COLUMN `progression_model` TEXT NOT NULL " +
                "DEFAULT '" + ExerciseEntity.DEFAULT_PROGRESSION_MODEL + "'",
        )
        db.execSQL(
            "UPDATE `exercise` SET `progression_model` = 'none' " +
                "WHERE `logging_type` IN ('time', 'distance_time')",
        )
        db.execSQL("ALTER TABLE `exercise_preference` ADD COLUMN `progression_model` TEXT")
    }
}

/**
 * Version 18 gives the engine somewhere to keep what it works out about an exercise.
 *
 * A table of its own beside `exercise_preference`, for the reason ADR 0009 gives and one more: the
 * preference table holds what the lifter chose and this holds what the app computed, and the two
 * must not share a row. Both are out of `exercise`, which a bank update replaces wholesale.
 *
 * Nothing is backfilled. An anchor is a fold over a history rather than a value a column can be
 * derived into, and the first recommendation asked for on an exercise computes it from the log —
 * so an installed copy with years of training in it arrives at the same numbers as a fresh one,
 * without this migration having to reproduce the engine in SQL.
 */
internal val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `exercise_progress` (`exercise_id` TEXT NOT NULL, " +
                "`anchor_kg` REAL NOT NULL, `rep_range_bottom` INTEGER NOT NULL, " +
                "`rep_range_top` INTEGER NOT NULL, `working_load_kg` REAL NOT NULL, " +
                "PRIMARY KEY(`exercise_id`), FOREIGN KEY(`exercise_id`) REFERENCES `exercise`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
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
    MIGRATION_7_8,
    MIGRATION_8_9,
    MIGRATION_9_10,
    MIGRATION_10_11,
    MIGRATION_11_12,
    MIGRATION_12_13,
    MIGRATION_13_14,
    MIGRATION_14_15,
    MIGRATION_15_16,
    MIGRATION_16_17,
    MIGRATION_17_18,
)

/**
 * Fails the migration if [table] now holds a row pointing at something that is not there.
 *
 * `PRAGMA foreign_key_check` *returns* the offending rows rather than raising, so running it with
 * `execSQL` — which discards what a statement returns — checks nothing at all. The rebuilds above
 * predate this and are left as they are: they have shipped, and turning a silent inconsistency on
 * an installed copy into a refusal to open the app is not a fix anyone asked for. New rebuilds use
 * this, where the alternative is a drop hanging off a set that no longer exists, discovered months
 * later in history nobody is looking at.
 */
private fun SupportSQLiteDatabase.failIfReferencesBroke(table: String) {
    query("PRAGMA foreign_key_check(`$table`)").use { broken ->
        check(broken.count == 0) {
            "Migrating `$table` left ${broken.count} row(s) referencing something that is not there"
        }
    }
}
