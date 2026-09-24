package com.tylergimpel.gymtracker.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tylergimpel.gymtracker.domain.CardioPlacement
import com.tylergimpel.gymtracker.domain.Experience
import com.tylergimpel.gymtracker.domain.FITNOTES
import com.tylergimpel.gymtracker.domain.ImportedSet
import com.tylergimpel.gymtracker.domain.NeutralImport
import com.tylergimpel.gymtracker.domain.Performance
import com.tylergimpel.gymtracker.domain.Kilograms
import com.tylergimpel.gymtracker.domain.NewExerciseDraft
import com.tylergimpel.gymtracker.domain.RepPreference
import com.tylergimpel.gymtracker.domain.RoutineGoal
import com.tylergimpel.gymtracker.domain.SPLIT_SHAPES
import com.tylergimpel.gymtracker.domain.SessionPlan
import com.tylergimpel.gymtracker.domain.SetDraft
import com.tylergimpel.gymtracker.domain.Sex
import com.tylergimpel.gymtracker.domain.Theme
import com.tylergimpel.gymtracker.domain.WeightUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The backup is the only unqualified promise in the app, so this is the most exhaustive test in it.
 *
 * **Every table, every column, out and back in** (#97). The tables are not listed here: the test
 * fills every one the database has through the app's own repositories, fails if any is left empty
 * — so a table added later fails here until somebody gives it data — and then compares every row of
 * every table before the backup and after the restore.
 */
@RunWith(AndroidJUnit4::class)
class BackupRestoreTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val zone: ZoneId = ZoneId.of("Europe/London")
    private val clock = Clock.fixed(Instant.parse("2026-09-14T17:00:00Z"), zone)

    private lateinit var database: GymDatabase

    @Before
    fun setUp() {
        database = fresh()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun everythingThatMakesTheAppTheLiftersComesBackExactly() = runBlocking {
        fillEveryTable()
        val backups = BackupRepository(database, clock)

        // The test's own list of tables, read from the database rather than asked of the backup: a
        // backup that forgot a table must not be able to forget it here too and still pass.
        // Nothing may be backed up empty: an empty table proves nothing about whether it survives.
        val empty = everyTable().filter { rowsOf(it).isEmpty() }
        assertTrue("these tables have no data in this test, so nothing proves they survive: $empty", empty.isEmpty())
        val before = everyTable().associateWith(::rowsOf)

        val file = ByteArrayOutputStream().also { backups.write(it) }.toByteArray()

        // A new phone: nothing but the shipped bank.
        database.close()
        context.deleteDatabase(TEST_DATABASE)
        database = fresh()
        val restoring = BackupRepository(database, clock)
        assertTrue(restoring.wouldReplace().isNothing)

        restoring.restore(restoring.read(ByteArrayInputStream(file)))

        val after = everyTable().associateWith(::rowsOf)
        assertEquals(before.keys, after.keys)
        before.forEach { (table, rows) -> assertEquals("table $table", rows, after.getValue(table)) }
    }

    @Test
    fun aNewPhoneHasNothingARestoreWouldReplace() = runBlocking {
        val backups = BackupRepository(database, clock)
        val profile = ProfileRepository(database.profileDao(), WeightUnit.KILOGRAMS)
        val exercises = ExerciseRepository(database, database.exerciseDao(), database.sessionDao(), profile)

        // Opening an exercise asks the engine for a prescription, and the engine writes down what it
        // worked out. That is the app's arithmetic, not the lifter's data, and must not make a new
        // phone look like one with something to lose (#104).
        RecommendationRepository(
            database,
            database.exerciseDao(),
            database.sessionDao(),
            clock,
            plates = PlateRepository(database.plateDao(), profile),
            profile = profile,
        ).nextSet(exercises.observeBank().first().single { it.id == BENCH })

        assertTrue(backups.wouldReplace().isNothing)
    }

    @Test
    fun whatARestoreWouldReplaceIsCountedInTheLiftersTerms() = runBlocking {
        val backups = BackupRepository(database, clock)
        sessions().logSet(BENCH, SetDraft("60", "8"))
        BodyweightRepository(database, database.bodyweightDao())
            .record(clock.instant(), LocalDate.parse("2026-09-14"), Kilograms(72.5))
        ProfileRepository(database.profileDao(), WeightUnit.KILOGRAMS).setSex(Sex.MALE)

        val there = backups.wouldReplace()

        assertEquals(1, there.sessions)
        assertEquals(1, there.weighIns)
        assertFalse(there.routine)
        assertTrue("a profile answer is the lifter's, and a restore would replace it", there.customised)
        assertFalse(there.isNothing)
    }

    @Test
    fun historyOnAnExerciseTheBankHasSinceDroppedStillRestores() = runBlocking {
        // A bank update dropped an exercise the lifter had trained, so this phone keeps it as a
        // Tombstone for the history pointing at it. A new install's bank no longer has the row at
        // all — and without it, that history would point at nothing and the restore would fail.
        sessions().logSet(SQUAT, SetDraft("100", "5"))
        database.exerciseDao().tombstone(SQUAT)
        val file = ByteArrayOutputStream().also { BackupRepository(database, clock).write(it) }.toByteArray()

        database.close()
        context.deleteDatabase(TEST_DATABASE)
        database = fresh()
        // A newer bank, without the row.
        database.openHelper.writableDatabase.execSQL("DELETE FROM exercise WHERE id = ?", arrayOf(SQUAT))

        val restoring = BackupRepository(database, clock)
        restoring.restore(restoring.read(ByteArrayInputStream(file)))

        assertEquals(1, rowsOf("set_entry").size)
        assertEquals(SQUAT, rowsOf("session_exercise").single()["exercise_id"])
    }

    @Test
    fun aBackupNamingATableItDoesNotCoverIsRefused() = runBlocking {
        val backups = BackupRepository(database, clock)
        val forged = """{"format":"gym-tracker-backup","formatVersion":1,"databaseVersion":${GymDatabase.VERSION},""" +
            """"tables":{"room_master_table":[{"id":42,"identity_hash":"x"}]}}"""

        // Only the tables a backup covers can be restored into; Room's and SQLite's own are not
        // the lifter's, and a file naming one is not a backup this app wrote.
        try {
            backups.restore(backups.read(ByteArrayInputStream(forged.toByteArray())))
            fail("a backup naming a table it does not cover was restored")
        } catch (expected: NotABackup) {
            // Refused inside the transaction, so nothing was changed.
        }
    }

    @Test
    fun aRestoreReplacesRatherThanMerges() = runBlocking {
        val backups = BackupRepository(database, clock)
        sessions().logSet(BENCH, SetDraft("60", "8"))
        val file = ByteArrayOutputStream().also { backups.write(it) }.toByteArray()

        sessions().logSet(SQUAT, SetDraft("100", "5"))
        backups.restore(backups.read(ByteArrayInputStream(file)))

        // Exactly the backed-up state: the squat logged after it is gone, and nothing is doubled.
        assertEquals(1, rowsOf("set_entry").size)
        assertEquals(1, rowsOf("session_exercise").size)
    }

    @Test
    fun aFileThatIsNotABackupIsRefusedAndChangesNothing() = runBlocking {
        val backups = BackupRepository(database, clock)
        sessions().logSet(BENCH, SetDraft("60", "8"))

        try {
            backups.read(ByteArrayInputStream("""{"tables":{}}""".toByteArray()))
            fail("a JSON file that is not a backup was accepted")
        } catch (expected: NotABackup) {
            // Refused before anything is touched.
        }
        assertEquals(1, rowsOf("set_entry").size)
    }

    @Test
    fun aBackupFromANewerAppIsRefused() = runBlocking {
        val backups = BackupRepository(database, clock)
        val newer = """{"format":"gym-tracker-backup","formatVersion":1,"databaseVersion":${GymDatabase.VERSION + 1},"tables":{}}"""

        try {
            backups.read(ByteArrayInputStream(newer.toByteArray()))
            fail("a backup from a newer database version was accepted")
        } catch (expected: NotABackup) {
            // Its columns cannot be known, so restoring it would guess.
        }
    }

    @Test
    fun twoYearsOfTrainingIsWrittenInSeconds() = runBlocking {
        // About the author's own history: 2,234 sets. Written straight into the tables so the test
        // measures the backup rather than the logging.
        sessions().logSet(BENCH, SetDraft("60", "8"))
        val performed = rowsOf("session_exercise").single()["id"]
        val db = database.openHelper.writableDatabase
        db.beginTransaction()
        repeat(SETS) { index ->
            db.execSQL(
                "INSERT INTO set_entry (session_exercise_id, set_index, type, weight_kg, reps) VALUES (?, ?, 'working', 60.0, 8)",
                arrayOf<Any?>(performed, index + 2),
            )
        }
        db.setTransactionSuccessful()
        db.endTransaction()

        val started = System.nanoTime()
        ByteArrayOutputStream().also { BackupRepository(database, clock).write(it) }
        val seconds = (System.nanoTime() - started) / 1e9

        assertTrue("writing $SETS sets took $seconds s", seconds < MOST_SECONDS)
    }

    /** Puts at least one row in every table, the way the app itself would. */
    private suspend fun fillEveryTable() {
        val profile = ProfileRepository(database.profileDao(), WeightUnit.KILOGRAMS)
        val exercises = ExerciseRepository(database, database.exerciseDao(), database.sessionDao(), profile)
        val routines = RoutineRepository(database, database.routineDao(), database.sessionDao(), clock)
        val sessions = sessions()

        // Profile, plates and the bar.
        profile.setSex(Sex.FEMALE)
        profile.setBirthYear(1990)
        profile.setHeightCm(170.0)
        profile.setExperience(Experience.NOVICE)
        profile.setTheme(Theme.DARK)
        profile.dismissCoachMark()
        profile.setBar(Kilograms(20.0))
        PlateRepository(database.plateDao(), profile).setPairs(Kilograms(20.0), 3)

        // Bodyweight history.
        BodyweightRepository(database, database.bodyweightDao())
            .record(clock.instant(), LocalDate.parse("2026-09-14"), Kilograms(72.5))

        // The lifter's own exercise, and what they have said about bank ones.
        exercises.createExercise(
            NewExerciseDraft(name = "Hip Thrust Machine", primaryMuscles = listOf("glutes"), equipment = "machine"),
        )
        exercises.rename(BENCH, "Flat bench")
        exercises.note(BENCH, "Elbows in")
        exercises.setDisliked(INCLINE, disliked = true)

        // A routine, its days and pins.
        val shape = SPLIT_SHAPES.single { it.id == "push-pull-legs-3" }
        val bank = exercises.observeBank().first()
        routines.createFromShape(
            name = shape.description,
            shape = shape,
            goal = RoutineGoal.HYPERTROPHY,
            cadence = 3,
            equipment = setOf("barbell", "dumbbell", "cable", "machine", "body only"),
            repPreference = RepPreference.MODERATE,
            cardio = CardioPlacement.NONE,
            bank = bank,
        )
        val routine = routines.observeProgress().first()!!.routine
        val push = routine.rotation.first()
        routines.setPinned(push.id, push.exercises.first().exercise.id, pinned = true)

        // A session from a routine day: its plan, two exercises in a superset, and a drop.
        val sessionId = sessions.startSession(SessionPlan(push.exercises))
        sessions.logSet(BENCH, SetDraft("60", "8"))
        sessions.logSet(SQUAT, SetDraft("100", "5"))
        val logged = sessions.observeSession(sessionId).first()!!
        sessions.groupWithNext(logged.exercises.first().id)
        sessions.addDrop(logged.exercises.first().sets.first().efforts.first().id, Kilograms(50.0), 6)
        sessions.finishSession(sessionId, routine, push)

        // The cycle: a day completed and a day skipped.
        routines.recordCompletion(routine.id, push, sessionId)
        routines.recordSkip(routine.id, routine.rotation[1])

        // An import, which every session it brought in points at.
        ImportRepository(database, clock, zone).import(
            NeutralImport(
                source = FITNOTES,
                fingerprint = "backup-test",
                sets = listOf(
                    ImportedSet(
                        date = LocalDate.parse("2026-03-02"),
                        exerciseName = "Barbell Squat",
                        order = 1,
                        performance = Performance.WeightAndReps(Kilograms(100.0), 5),
                    ),
                ),
            ),
            exercises.observeBank().first(),
        )

        // A regeneration's step back, taken last: logging a session spends it (ADR 0006), so it
        // only exists to be backed up after the training above.
        routines.regenerate(shape, bank)

        // What the engine worked out, which carries the rep range the lifter's goal chose.
        RecommendationRepository(
            database,
            database.exerciseDao(),
            database.sessionDao(),
            clock,
            plates = PlateRepository(database.plateDao(), profile),
            profile = profile,
        ).nextSet(exercises.observeBank().first().single { it.id == BENCH })
    }

    private fun sessions() = SessionRepository(
        database,
        database.sessionDao(),
        clock,
        zone,
        ProfileRepository(database.profileDao(), WeightUnit.KILOGRAMS),
    )

    /** Every table in the database, less SQLite's and Room's own bookkeeping. */
    private fun everyTable(): List<String> =
        database.openHelper.readableDatabase.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' " +
                "AND name NOT IN ('android_metadata', 'room_master_table') ORDER BY name",
        ).use { rows -> buildList { while (rows.moveToNext()) add(rows.getString(0)) } }

    /** Every row of [table] the backup covers, as comparable values. */
    private fun rowsOf(table: String): List<Map<String, Any?>> {
        val where = if (table == "exercise") " WHERE source = 'user' OR tombstoned = 1" else ""
        return database.openHelper.readableDatabase.query("SELECT * FROM `$table`$where").use { rows ->
            buildList {
                while (rows.moveToNext()) {
                    add(
                        (0 until rows.columnCount).associate { column ->
                            rows.getColumnName(column) to when (rows.getType(column)) {
                                android.database.Cursor.FIELD_TYPE_NULL -> null
                                android.database.Cursor.FIELD_TYPE_INTEGER -> rows.getLong(column)
                                android.database.Cursor.FIELD_TYPE_FLOAT -> rows.getDouble(column)
                                android.database.Cursor.FIELD_TYPE_BLOB -> rows.getBlob(column).toList()
                                else -> rows.getString(column)
                            }
                        },
                    )
                }
            }
        }.sortedBy { it.toString() }
    }

    private fun fresh(): GymDatabase =
        Room.databaseBuilder(context, GymDatabase::class.java, TEST_DATABASE)
            .createFromAsset(GymDatabase.ASSET_PATH)
            .addMigrations(*MIGRATIONS)
            .build()

    private companion object {
        const val TEST_DATABASE = "backup-restore-test.db"
        const val BENCH = "Barbell_Bench_Press_-_Medium_Grip"
        const val SQUAT = "Barbell_Squat"
        const val INCLINE = "Barbell_Incline_Bench_Press_-_Medium_Grip"
        const val SETS = 2_500

        /** "Seconds, not minutes", with room for a slow emulator. */
        const val MOST_SECONDS = 10.0
    }
}
