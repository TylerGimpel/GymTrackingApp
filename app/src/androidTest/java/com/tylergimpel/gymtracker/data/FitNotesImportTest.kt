package com.tylergimpel.gymtracker.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tylergimpel.gymtracker.domain.ImportSource
import com.tylergimpel.gymtracker.domain.Kilograms
import com.tylergimpel.gymtracker.domain.LoggingType
import com.tylergimpel.gymtracker.domain.Performance
import com.tylergimpel.gymtracker.domain.Recommendation
import com.tylergimpel.gymtracker.domain.SetDraft
import com.tylergimpel.gymtracker.domain.SetType
import com.tylergimpel.gymtracker.domain.WeightUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import com.tylergimpel.gymtracker.domain.Metres
import com.tylergimpel.gymtracker.domain.NameDecision
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Bringing a FitNotes backup in (#96), against a synthetic file built here to reproduce the real
 * one's traps rather than an idealised version of it.
 *
 * **The fixture is synthetic and committed.** The author's own file is personal training data and
 * stays outside the repo (`docs/fixtures.md`); a test that depended on it would run for nobody
 * else. What this builds is what that file was found to do: `unit` zero on every row, a weight of
 * 22.5 in a column declared INTEGER, a bodyweight set at 0.0, and names that do not match.
 */
@RunWith(AndroidJUnit4::class)
class FitNotesImportTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val zone: ZoneId = ZoneId.of("Europe/London")
    /**
     * A week after the fixture's last session. The engine rightly recalibrates a lift left more
     * than eight weeks (the layoff rule), so imported history only drives a prescription while it
     * is recent — which is what a lifter switching apps mid-training has.
     */
    private val clock = Clock.fixed(Instant.parse("2026-03-12T17:00:00Z"), zone)
    private lateinit var database: GymDatabase
    private lateinit var fixture: File

    @Before
    fun setUp() {
        context.deleteDatabase(TEST_DATABASE)
        database = Room.databaseBuilder(context, GymDatabase::class.java, TEST_DATABASE)
            .createFromAsset(GymDatabase.ASSET_PATH)
            .addMigrations(*MIGRATIONS)
            .build()
        fixture = buildFixture(File(context.cacheDir, "fixture.fitnotes"))
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(TEST_DATABASE)
        fixture.delete()
    }

    @Test
    fun aHalfKiloPlateInAnIntegerColumnComesAcrossAsHalfAKilo() {
        val sets = FitNotesParser.parse(fixture).sets

        // SQLite let 22.5 sit in a column declared INTEGER; a strictly typed reader breaks here.
        assertTrue(sets.any { (it.performance as? Performance.WeightAndReps)?.weight == Kilograms(22.5) })
    }

    @Test
    fun theUnitColumnIsNeverTakenForTheWeightUnit() {
        // Every row's `unit` is 0 and the squat was lifted at 100: plain kilograms, unscaled. Read
        // as a unit, 0 would have converted or scaled every load in the file.
        val squats = FitNotesParser.parse(fixture).sets.filter { it.exerciseName == "Barbell Squat" }

        assertEquals(
            listOf(Kilograms(100.0), Kilograms(105.0)),
            squats.map { (it.performance as Performance.WeightAndReps).weight },
        )
    }

    @Test
    fun aBodyweightSetComesInAsBodyweightAndNeverAsAPhantomKilo() {
        val crunches = FitNotesParser.parse(fixture).sets.single { it.order == 4L }

        // 0.0 is FitNotes' "no load", not a load of nothing: a bodyweight set, twelve reps, with
        // nothing added. The older 1.0 sentinel would have been a one-kilogram lift (#100).
        assertEquals(Performance.BodyweightReps(addedWeight = null, reps = 12), crunches.performance)
    }

    @Test
    fun aHoldComesInAsTimeWithNoReps() {
        val plank = FitNotesParser.parse(fixture).sets.single { it.exerciseName == "Plank" }

        assertEquals(Performance.Timed(Duration.ofSeconds(40)), plank.performance)
    }

    @Test
    fun aRunComesInAsDistanceAndTimeInCanonicalUnits() {
        val run = FitNotesParser.parse(fixture).sets.single { it.exerciseName == "Running, Treadmill" }

        // FitNotes keeps distance, like weight, in its metric base unit: metres.
        assertEquals(Performance.DistanceAndTime(Metres(5000.0), Duration.ofSeconds(1200)), run.performance)
    }

    @Test
    fun aLiftReadInPoundsIsNotConvertedTwice() {
        val curl = FitNotesParser.parse(fixture).sets.single { it.exerciseName == "Dumbbell Bicep Curl" }

        // The exercise is read in pounds, and FitNotes stored the 25 lb as kilograms already.
        // Converting it again would be the silent corruption of every pound lift in the file.
        assertEquals(Performance.WeightAndReps(Kilograms(11.34), 10), curl.performance)
    }

    @Test
    fun everyKindOfSetInTheFileIsBroughtAcross() {
        val read = FitNotesParser.parse(fixture)

        assertTrue(read.notYetSupported.isEmpty())
        assertEquals(11, read.sets.size)
    }

    @Test
    fun theLiftingHistoryArrivesOnItsOwnDatesAsImportedWorkingSets() = runBlocking {
        val result = importFixture()

        val imported = sessions().observeRecent(limit = 100).first().filter { it.session.isImported }
        assertEquals(listOf(LocalDate.parse("2026-03-02"), LocalDate.parse("2026-03-05")), imported.map { it.session.date }.sorted())
        assertTrue(imported.all { it.session.importBatchId == result.batchId })
        assertTrue(imported.flatMap { it.exercises }.flatMap { it.sets }.all { it.type == SetType.WORKING })
        // Everything but the three sets under names with no exact match, until they are decided.
        assertEquals(8, result.sets)
    }

    @Test
    fun aNameWithNoExactMatchIsLeftOutAndCountedNeverGuessed() = runBlocking {
        val result = importFixture()

        // "Hammer Strength Row" is near several bank rows and exactly none of them.
        assertEquals(mapOf("Hammer Strength Row" to 2, "Pec Deck Machine" to 1), result.unmatched)
        val imported = sessions().observeRecent(limit = 100).first().filter { it.session.isImported }
        assertFalse(imported.flatMap { it.exercises }.any { it.exercise.name.contains("Row") })
    }

    @Test
    fun theBatchRecordsFitNotesVolumeAsApproximateBecauseOfTheSourceNotTheImport() = runBlocking {
        val fitNotes = importFixture()
        val declaring = (
            imports().import(
                FitNotesParser.parse(fixture).copy(
                    source = ImportSource(id = "declares-types", declaresSetTypes = true, declaresSupersets = false),
                    fingerprint = "another",
                ),
                bank(),
            ) as ImportOutcome.Imported
            ).result

        val batches = database.importBatchDao().all().associateBy { it.id }
        assertFalse(batches.getValue(fitNotes.batchId).volumeReliable)
        assertTrue(batches.getValue(declaring.batchId).volumeReliable)
    }

    @Test
    fun supersetsComeAcrossAsSupersetsInOrder() = runBlocking {
        importFixture()
        val byDate = sessions().observeRecent(limit = 100).first()
            .filter { it.session.isImported }.associateBy { it.session.date.toString() }

        // The pair on the first day, and the triple on the second, each as one superset whose
        // members sit together — which is what the app's supersets are made of.
        val pair = byDate.getValue("2026-03-02")
        assertEquals(1, pair.supersets.size)
        assertEquals(setOf("Barbell_Squat", BENCH), pair.exercises.filter { it.supersetId != null }.map { it.exercise.id }.toSet())

        val triple = byDate.getValue("2026-03-05")
        val grouped = triple.exercises.sortedBy { it.orderIndex }.filter { it.supersetId != null }
        assertEquals(setOf(BENCH, "Dumbbell_Bicep_Curl", "Crunches"), grouped.map { it.exercise.id }.toSet())
        val positions = grouped.map { it.orderIndex }
        assertEquals("the triple's members are not together", positions.max() - positions.min(), positions.size - 1)
    }

    @Test
    fun aSetsNoteComesAcrossAndAnOrphanedOneIsSkipped() = runBlocking {
        val result = importFixture()

        val bench = sessions().observeRecent(limit = 100).first()
            .single { it.session.date.toString() == "2026-03-02" && it.session.isImported }
            .exercises.single { it.exercise.id == BENCH }
        assertEquals("Pause at the chest", bench.sets.single().efforts.single().note)
        // The comment on a deleted set is not a sign of a corrupt file: the import went through.
        assertTrue(result.sets > 0)
    }

    @Test
    fun whatFitNotesKeptAboutAnExerciseComesAcrossWhereTheAppHasNoChoiceYet() = runBlocking {
        val exercises = ExerciseRepository(
            database,
            database.exerciseDao(),
            database.sessionDao(),
            ProfileRepository(database.profileDao(), WeightUnit.KILOGRAMS),
        )
        exercises.note(BENCH, "My note")

        importFixture()

        val curl = bank().single { it.id == "Dumbbell_Bicep_Curl" }
        assertEquals("Supinate at the top", curl.note)
        assertEquals(WeightUnit.POUNDS, curl.displayUnit)
        assertEquals(Kilograms(1.13), curl.chosenIncrement)
        assertEquals(Duration.ofSeconds(90), curl.chosenRest)
        // A choice already made here is the lifter's, and an import does not overwrite it.
        assertEquals("My note", bank().single { it.id == BENCH }.note)
    }

    @Test
    fun anUnmatchedNameBecomesAnExerciseOfTheLiftersOwnByDefault() = runBlocking {
        val result = (
            imports().import(
                FitNotesParser.parse(fixture),
                bank(),
                decisions = mapOf("Hammer Strength Row" to NameDecision.CreateOwn, "Pec Deck Machine" to NameDecision.CreateOwn),
            ) as ImportOutcome.Imported
            ).result

        // For a gym full of machines the bank has never heard of, this is the right outcome (#99).
        assertTrue(result.unmatched.isEmpty())
        assertEquals(setOf("Hammer Strength Row", "Pec Deck Machine"), result.created.toSet())
        val pecDeck = bank().single { it.name == "Pec Deck Machine" }
        assertEquals(listOf("chest"), pecDeck.primaryMuscles)
        // FitNotes filed the row under Back, which names no single muscle, so none is guessed —
        // and the summary says which exercises came across that way.
        assertTrue(bank().single { it.name == "Hammer Strength Row" }.primaryMuscles.isEmpty())
        assertEquals(listOf("Hammer Strength Row"), result.createdWithoutMuscles)
        // Once per name: both of the row's sets came in under the one exercise.
        val rows = importedSessions().flatMap { it.exercises }.filter { it.exercise.name == "Hammer Strength Row" }
        assertEquals(2, rows.sumOf { it.sets.size })
    }

    @Test
    fun anUnmatchedNameCanBeTheSameAsAnExerciseTheLifterChooses() = runBlocking {
        imports().import(
            FitNotesParser.parse(fixture),
            bank(),
            decisions = mapOf("Hammer Strength Row" to NameDecision.SameAs(BENT_OVER_ROW)),
        )

        // The lifter's decision, and never a guess: both sets under the row they chose.
        val chosen = importedSessions().flatMap { it.exercises }.filter { it.exercise.id == BENT_OVER_ROW }
        assertEquals(2, chosen.sumOf { it.sets.size })
        assertTrue(bank().none { it.name == "Hammer Strength Row" })
    }

    @Test
    fun importingTheSameFileTwiceIsRecognisedAndAddsNothing() = runBlocking {
        importFixture()
        val before = importedSessions().size

        val again = imports().import(FitNotesParser.parse(fixture), bank())

        // Caught before anything is written: doubling a training history by accident is exactly
        // what the batch's fingerprint is for (#102).
        assertTrue("a repeat import was not recognised: $again", again is ImportOutcome.AlreadyImported)
        assertEquals(before, importedSessions().size)
    }

    @Test
    fun undoingAnImportRemovesExactlyWhatItAddedAndNothingElse() = runBlocking {
        sessions().logSet(BENCH, SetDraft("60", "8"))
        sessions().finishSession(sessions().observeActiveSession().first()!!.id)
        val batch = importFixture().batchId
        sessions().logSet(SQUAT_ID, SetDraft("100", "5"))
        sessions().finishSession(sessions().observeActiveSession().first()!!.id)
        val loggedBefore = sessions().observeRecent(limit = 100).first().filter { !it.session.isImported }

        imports().undo(batch)

        // Every imported session, set, superset and drop gone; both sessions the lifter logged,
        // one before the import and one after, exactly as they were.
        assertTrue(importedSessions().isEmpty())
        assertEquals(loggedBefore, sessions().observeRecent(limit = 100).first())
        assertTrue(imports().observeListed().first().isEmpty())
        // And the same file can be brought in again, since nothing of it is left.
        assertTrue(imports().import(FitNotesParser.parse(fixture), bank()) is ImportOutcome.Imported)
    }

    @Test
    fun undoingAnImportTakesTheExercisesItCreatedUnlessTheLifterHasSinceUsedThem() = runBlocking {
        val batch = (
            imports().import(
                FitNotesParser.parse(fixture),
                bank(),
                decisions = mapOf("Hammer Strength Row" to NameDecision.CreateOwn, "Pec Deck Machine" to NameDecision.CreateOwn),
            ) as ImportOutcome.Imported
            ).result.batchId
        val pecDeck = bank().single { it.name == "Pec Deck Machine" }
        sessions().logSet(pecDeck.id, SetDraft("45", "12"))
        sessions().finishSession(sessions().observeActiveSession().first()!!.id)

        imports().undo(batch)

        // Every row the import added goes (#102) — but a machine the lifter has since trained on
        // is theirs now, and their session still has it.
        assertTrue(bank().none { it.name == "Hammer Strength Row" })
        assertTrue(bank().any { it.id == pecDeck.id })
        // And a later undo of something else never reaches it: it no longer belongs to an import.
        assertTrue(database.exerciseDao().createdBy(batch).isEmpty())
    }

    @Test
    fun aLoadedSetOnABodyweightExerciseComesInAsAddedLoad() = runBlocking {
        // A weighted crunch, as FitNotes records it: a weight like any other lift.
        SQLiteDatabase.openDatabase(fixture.path, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL(
                "INSERT INTO training_log (_id, exercise_id, date, metric_weight, reps) VALUES (12, 6, '2026-03-05', 10, 15)",
            )
        }
        assertEquals(LoggingType.BODYWEIGHT_REPS, bank().single { it.id == "Crunches" }.loggingType)

        importFixture()

        val crunches = importedSessions().flatMap { it.exercises }.single { it.exercise.id == "Crunches" }.sets
        // Ten kilograms on top of the lifter, not a ten-kilogram lift (#100).
        assertEquals(
            setOf(
                Performance.BodyweightReps(addedWeight = null, reps = 12),
                Performance.BodyweightReps(addedWeight = Kilograms(10.0), reps = 15),
            ),
            crunches.map { it.efforts.single().performance }.toSet(),
        )
    }

    @Test
    fun aNewerBackupOfTheSameHistoryBringsInOnlyWhatIsNew() = runBlocking {
        val newer = FitNotesParser.parse(fixture)
        val older = newer.copy(fingerprint = "an older backup", sets = newer.sets.filter { it.date == MARCH_2 })
        val first = (imports().import(older, bank()) as ImportOutcome.Imported).result

        val second = (imports().import(newer, bank()) as ImportOutcome.Imported).result

        // The second of March came in with the older backup; only the fifth is new (#116).
        assertEquals(1, second.alreadyHere)
        assertEquals(1, second.sessions)
        assertEquals(listOf(MARCH_2, MARCH_5), importedSessions().map { it.session.date }.sorted())

        // Each is its own import, undone on its own.
        imports().undo(second.batchId)
        assertEquals(listOf(MARCH_2), importedSessions().map { it.session.date })
        val again = (imports().import(newer, bank()) as ImportOutcome.Imported).result
        imports().undo(first.batchId)
        assertEquals(listOf(MARCH_5), importedSessions().map { it.session.date })
        assertEquals(1, again.alreadyHere)
    }

    @Test
    fun aDateTheLifterLoggedThemselvesIsStillImported() = runBlocking {
        sessions().logSet(BENCH, SetDraft("60", "8"))
        sessions().finishSession(sessions().observeActiveSession().first()!!.id)
        val today = LocalDate.now(clock)
        val parsed = FitNotesParser.parse(fixture)
        val records = parsed.copy(sets = parsed.sets + parsed.sets.first().copy(date = today))

        val result = (imports().import(records, bank()) as ImportOutcome.Imported).result

        // Only an earlier import of the same source means "already here", never the lifter's own
        // logging: two sessions on one day is a thing that happens.
        assertEquals(0, result.alreadyHere)
        assertTrue(importedSessions().any { it.session.date == today })
    }

    @Test
    fun aCorrectedImportedSessionIsStillTheImportsAndGoesWithIt() = runBlocking {
        val batch = importFixture().batchId
        val bench = importedSessions().flatMap { it.exercises }.first { it.exercise.id == BENCH }

        // A set FitNotes never had, added to a session it did (#120).
        sessions().addSetTo(bench.id, SetDraft("70", "5"))

        // Still the import's: correcting a session does not make it the lifter's own logging.
        val corrected = importedSessions().flatMap { it.exercises }.single { it.id == bench.id }
        assertEquals(bench.sets.size + 1, corrected.sets.size)
        // So undoing the import takes the added set with it, and leaves nothing behind.
        imports().undo(batch)
        assertTrue(sessions().observeRecent(limit = 100).first().isEmpty())
    }

    @Test
    fun importsAreListedWithWhatEachBroughtIn() = runBlocking {
        importFixture()

        val listed = imports().observeListed().first().single()

        assertEquals("fitnotes", listed.source)
        assertEquals(importedSessions().size, listed.sessions)
    }

    @Test
    fun importingTouchesNothingAlreadyLogged() = runBlocking {
        sessions().logSet(BENCH, SetDraft("60", "8"))
        sessions().finishSession(sessions().observeActiveSession().first()!!.id)
        val before = sessions().observeRecent(limit = 100).first().single()

        importFixture()

        val mine = sessions().observeRecent(limit = 100).first().single { !it.session.isImported }
        // What was logged: the session and every set in it. What an import may add to the
        // exercise itself — a note FitNotes kept, where there was none — is not the log (#101).
        assertEquals(before.session, mine.session)
        assertEquals(
            before.exercises.map { it.id to it.sets },
            mine.exercises.map { it.id to it.sets },
        )
    }

    @Test
    fun aLiftWithImportedHistoryIsPrescribedFromItRatherThanRamped() = runBlocking {
        importFixture()
        val profile = ProfileRepository(database.profileDao(), WeightUnit.KILOGRAMS)

        val next = RecommendationRepository(
            database,
            database.exerciseDao(),
            database.sessionDao(),
            clock,
            plates = PlateRepository(database.plateDao(), profile),
            profile = profile,
        ).nextSet(bank().single { it.id == BENCH })

        // The payoff for importing: the first session knows what the lifter already knows.
        assertTrue("expected a prescription from history, got $next", next is Recommendation.Prescribing)
    }

    private suspend fun importFixture(): ImportResult =
        (imports().import(FitNotesParser.parse(fixture), bank()) as ImportOutcome.Imported).result

    private fun imports() =
        ImportRepository(database, clock, zone, ProfileRepository(database.profileDao(), WeightUnit.KILOGRAMS))

    private suspend fun importedSessions() =
        sessions().observeRecent(limit = 100).first().filter { it.session.isImported }

    private suspend fun bank() = ExerciseRepository(
        database,
        database.exerciseDao(),
        database.sessionDao(),
        ProfileRepository(database.profileDao(), WeightUnit.KILOGRAMS),
    ).observeBank().first()

    private fun sessions() = SessionRepository(
        database,
        database.sessionDao(),
        clock,
        zone,
        ProfileRepository(database.profileDao(), WeightUnit.KILOGRAMS),
    )

    /**
     * A `.fitnotes`-shaped file with no personal data, reproducing the real file's traps.
     *
     * The tables and columns are FitNotes' own names, read off a real backup's schema.
     */
    private fun buildFixture(file: File): File {
        file.delete()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(
                "CREATE TABLE exercise (_id INTEGER PRIMARY KEY, name TEXT, category_id INTEGER, " +
                    "exercise_type_id INTEGER, notes TEXT, weight_increment INTEGER, default_graph_id INTEGER, " +
                    "default_rest_time INTEGER, weight_unit_id INTEGER, is_favourite INTEGER)",
            )
            db.execSQL(
                "CREATE TABLE training_log (_id INTEGER PRIMARY KEY, exercise_id INTEGER, date DATE, " +
                    "metric_weight INTEGER, reps INTEGER, unit INTEGER, routine_section_exercise_set_id INTEGER, " +
                    "timer_auto_start INTEGER, is_personal_record INTEGER, is_personal_record_first INTEGER, " +
                    "is_complete INTEGER, is_pending_update INTEGER, distance INTEGER, duration_seconds INTEGER)",
            )
            db.execSQL(
                "CREATE TABLE WorkoutGroup (_id INTEGER PRIMARY KEY, name TEXT, date TEXT, colour INTEGER, " +
                    "routine_section_id INTEGER, auto_jump_enabled INTEGER, rest_timer_auto_start_enabled INTEGER)",
            )
            db.execSQL(
                "CREATE TABLE WorkoutGroupExercise (_id INTEGER PRIMARY KEY, exercise_id INTEGER, date TEXT, " +
                    "routine_section_id INTEGER, workout_group_id INTEGER)",
            )
            db.execSQL(
                "CREATE TABLE Comment (_id INTEGER PRIMARY KEY, date DATE, owner_type_id INTEGER, owner_id INTEGER, comment TEXT)",
            )
            // A pair on the first day, and a triple on the second.
            db.execSQL("INSERT INTO WorkoutGroup (_id, name, date) VALUES (1, 'A', '2026-03-02'), (2, 'B', '2026-03-05')")
            db.execSQL(
                "INSERT INTO WorkoutGroupExercise (_id, exercise_id, date, workout_group_id) VALUES " +
                    "(1, 1, '2026-03-02', 1), (2, 2, '2026-03-02', 1), " +
                    "(3, 2, '2026-03-05', 2), (4, 7, '2026-03-05', 2), (5, 6, '2026-03-05', 2)",
            )
            // A note on a set, and an orphan pointing at a set that was deleted: expected, and skipped.
            db.execSQL(
                "INSERT INTO Comment (_id, date, owner_type_id, owner_id, comment) VALUES " +
                    "(1, '2026-03-02 18:00:00', 1, 3, 'Pause at the chest'), " +
                    "(2, '2026-03-02 18:05:00', 1, 999, 'A set that no longer exists')",
            )
            listOf(
                arrayOf<Any?>(1, "Barbell Squat", 0, 1),
                arrayOf<Any?>(2, "Barbell Bench Press - Medium Grip", 0, 1),
                arrayOf<Any?>(3, "Hammer Strength Row", 0, 1),
                arrayOf<Any?>(4, "Plank", 3, 1),
                arrayOf<Any?>(5, "Running, Treadmill", 1, 1),
                arrayOf<Any?>(6, "Crunches", 0, 1),
                // Read in pounds by this lifter; FitNotes still stores the weight in kilograms.
                arrayOf<Any?>(7, "Dumbbell Bicep Curl", 0, 2),
            ).forEach {
                db.execSQL("INSERT INTO exercise (_id, name, exercise_type_id, weight_unit_id) VALUES (?, ?, ?, ?)", it)
            }
            // FitNotes' categories: Chest names one muscle, Back names several.
            db.execSQL("CREATE TABLE Category (_id INTEGER PRIMARY KEY, name TEXT, colour INTEGER, sort_order INTEGER)")
            db.execSQL("INSERT INTO Category (_id, name) VALUES (1, 'Chest'), (2, 'Back')")
            db.execSQL("INSERT INTO exercise (_id, name, exercise_type_id, weight_unit_id, category_id) VALUES (8, 'Pec Deck Machine', 0, 1, 1)")
            db.execSQL("UPDATE exercise SET category_id = 2 WHERE _id = 3")
            // Everything FitNotes keeps about an exercise, on one of them, and a note on another.
            db.execSQL(
                "UPDATE exercise SET notes = 'Supinate at the top', weight_increment = 1.13, default_rest_time = 90 WHERE _id = 7",
            )
            db.execSQL("UPDATE exercise SET notes = 'Their note' WHERE _id = 2")
            // `unit` is 0 on every row, as in the real file; the weights are plain kilograms.
            // Columns: _id, exercise, date, metric_weight, reps, duration_seconds, distance.
            listOf(
                arrayOf<Any?>(1, 1, "2026-03-02", 100, 5, null, null),
                arrayOf<Any?>(2, 1, "2026-03-02", 105, 5, null, null),
                arrayOf<Any?>(3, 2, "2026-03-02", 22.5, 10, null, null),
                // The bodyweight sentinel is 0.0, and the reps are real.
                arrayOf<Any?>(4, 6, "2026-03-05", 0.0, 12, null, null),
                arrayOf<Any?>(5, 2, "2026-03-05", 60, 8, null, null),
                arrayOf<Any?>(6, 3, "2026-03-05", 50, 10, null, null),
                // A hold: time, and no reps at all.
                arrayOf<Any?>(7, 4, "2026-03-05", 0.0, null, 40, null),
                // A run: time and distance.
                arrayOf<Any?>(8, 5, "2026-03-05", 0.0, null, 1200, 5000),
                // 25 lb, as FitNotes stores it: already kilograms.
                arrayOf<Any?>(9, 7, "2026-03-05", 11.34, 10, null, null),
                // More under names the bank does not have: a machine, and a second row set.
                arrayOf<Any?>(10, 8, "2026-03-05", 45, 12, null, null),
                arrayOf<Any?>(11, 3, "2026-03-05", 55, 8, null, null),
            ).forEach {
                db.execSQL(
                    "INSERT INTO training_log (_id, exercise_id, date, metric_weight, reps, unit, duration_seconds, distance) " +
                        "VALUES (?, ?, ?, ?, ?, 0, ?, ?)",
                    it,
                )
            }
        }
        return file
    }

    private companion object {
        const val TEST_DATABASE = "fitnotes-import-test.db"
        const val BENCH = "Barbell_Bench_Press_-_Medium_Grip"
        const val SQUAT_ID = "Barbell_Squat"
        const val BENT_OVER_ROW = "Bent_Over_Barbell_Row"
        val MARCH_2: LocalDate = LocalDate.parse("2026-03-02")
        val MARCH_5: LocalDate = LocalDate.parse("2026-03-05")
    }
}
