package com.tylergimpel.gymtracker.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tylergimpel.gymtracker.domain.Exercise
import com.tylergimpel.gymtracker.domain.ExerciseSource
import com.tylergimpel.gymtracker.domain.LoggingType
import com.tylergimpel.gymtracker.domain.NewExerciseDraft
import com.tylergimpel.gymtracker.domain.ProgressionModel
import com.tylergimpel.gymtracker.domain.PlannedExercise
import com.tylergimpel.gymtracker.domain.SessionPlan
import com.tylergimpel.gymtracker.domain.SetDraft
import com.tylergimpel.gymtracker.domain.WeightUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.ZoneId

/**
 * What survives an app update that improves the exercise bank.
 *
 * The rules are the domain's and are tested there; this is the part that has to hold against a real
 * database — that a lifter's own exercise is untouched, that a rename and a note outlive a
 * correction to the row they were made on, and that an exercise somebody trained is never deleted
 * out from under its history.
 */
@RunWith(AndroidJUnit4::class)
class ExerciseOwnershipTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val zone: ZoneId = ZoneId.of("Europe/London")
    private val clock = Clock.fixed(java.time.Instant.parse("2026-09-14T18:00:00Z"), zone)

    private lateinit var database: GymDatabase
    private lateinit var exercises: ExerciseRepository
    private lateinit var sessions: SessionRepository
    private lateinit var routines: RoutineRepository

    /** Pinned rather than the device's: what a test reads in is part of what it asserts. */
    private lateinit var profile: ProfileRepository

    @Before
    fun setUp() {
        context.deleteDatabase(TEST_DATABASE)
        database = Room.databaseBuilder(context, GymDatabase::class.java, TEST_DATABASE)
            .createFromAsset(GymDatabase.ASSET_PATH)
            .addMigrations(*MIGRATIONS)
            .build()
        profile = ProfileRepository(database.profileDao(), WeightUnit.KILOGRAMS)
        exercises = ExerciseRepository(
            database,
            database.exerciseDao(),
            database.sessionDao(),
            profile,
        )
        sessions = SessionRepository(database, database.sessionDao(), clock, zone, profile)
        routines = RoutineRepository(database, database.routineDao(), database.sessionDao(), clock)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun anExerciseTheLifterAddsIsFirstClassAndOwnsItsOwnId() = runBlocking {
        val id = exercises.createExercise(
            NewExerciseDraft(
                name = "Pendulum Squat",
                primaryMuscles = listOf("quadriceps"),
                secondaryMuscles = listOf("glutes"),
                equipment = "machine",
            ),
        )

        val mine = bank().single { it.id == id }
        // A UUID, in an id space of its own: a bank update matches on the upstream string ids it
        // ships, so it can never find this row.
        assertEquals(36, id.length)
        assertEquals(ExerciseSource.USER, mine.source)
        assertTrue(mine.isUserAuthored)
        // And it is in the bank proper, so search, filtering and routines all see it.
        assertEquals("Pendulum Squat", mine.name)
        assertEquals(listOf("quadriceps"), mine.primaryMuscles)
    }

    @Test
    fun aRenameAndANoteOutliveACorrectionToTheRowTheyWereMadeOn() = runBlocking {
        exercises.rename(BENCH, "Flat Press")
        exercises.note(BENCH, "seat notch 3, use the long bar")

        // The bank corrects the name it ships for that very exercise.
        exercises.applyBankUpdate(listOf(shipped(BENCH, "Barbell Bench Press")))

        val corrected = bank().single { it.id == BENCH }
        assertEquals("Barbell Bench Press", corrected.bankName)
        assertEquals("Flat Press", corrected.userName)
        assertEquals("Flat Press", corrected.name)
        assertEquals("seat notch 3, use the long bar", corrected.note)
    }

    @Test
    fun aRenameLeavesTheUnitAloneAndTheUnitLeavesTheRenameAlone() = runBlocking {
        // One row holds all three choices, so each write has to touch only its own column.
        exercises.setDisplayUnit(BENCH, WeightUnit.POUNDS)
        exercises.rename(BENCH, "Flat Press")
        exercises.note(BENCH, "long bar")

        val chosen = bank().single { it.id == BENCH }
        assertEquals(WeightUnit.POUNDS, chosen.displayUnit)
        assertEquals("Flat Press", chosen.userName)
        assertEquals("long bar", chosen.note)
    }

    @Test
    fun anExerciseTheLifterAddedIsUntouchedByAnUpdateThatDropsEverythingElse() = runBlocking {
        val id = exercises.createExercise(
            NewExerciseDraft(name = "Pendulum Squat", primaryMuscles = listOf("quadriceps")),
        )

        // An update shipping a bank of exactly one exercise.
        exercises.applyBankUpdate(listOf(shipped(BENCH, "Barbell Bench Press")))

        assertNotNull(bank().singleOrNull { it.id == id })
        assertEquals("Pendulum Squat", bank().single { it.id == id }.name)
    }

    @Test
    fun anExerciseSomebodyTrainedIsTombstonedRatherThanDeleted() = runBlocking {
        sessions.logSet(BENCH, SetDraft("60", "5"))

        // The bank drops it. Deleting the row would orphan the set logged against it.
        exercises.applyBankUpdate(listOf(shipped(SQUAT, "Barbell Squat")))

        // Gone from what the app offers...
        assertNull(bank().singleOrNull { it.id == BENCH })
        // ...and still there, with the training intact and readable.
        assertEquals(1, countOf("set_entry"))
        val session = sessions.observeActiveSession().first()!!
        val logged = sessions.observeSession(session.id).first()!!
        assertEquals(BENCH, logged.exercises.single().exercise.id)
        assertTrue(logged.exercises.single().exercise.isTombstoned)
    }

    @Test
    fun anExerciseSittingInARoutineIsTombstonedRatherThanTakingTheUpdateDownWithIt() = runBlocking {
        val routineId = routines.createRoutine(name = "Push")
        routines.addDay(routineId, "Legs")
        val day = routines.observeProgress().first()!!.routine.rotation.single()
        routines.addExerciseToDay(day.id, SQUAT)

        // The bank drops it. Nobody has trained it, so there is no logged set — but the routine
        // holds it with a real constraint, and deleting the row would be refused.
        exercises.applyBankUpdate(listOf(shipped(BENCH, "Barbell Bench Press")))

        // Tombstoned rather than deleted, the routine still holds it...
        assertNull(bank().singleOrNull { it.id == SQUAT })
        assertEquals(1, countOf("routine_day_exercise"))
        // ...and the rest of the update went in, rather than the whole thing rolling back.
        assertEquals("Barbell Bench Press", bank().single { it.id == BENCH }.name)
    }

    @Test
    fun anExercisePlannedAndNeverPerformedIsTombstonedToo() = runBlocking {
        // Exactly the case the session plan exists to make visible: asked for, not done, so there
        // is no `session_exercise` row pointing at it at all.
        sessions.startSession(SessionPlan(listOf(PlannedExercise(bankExercise(SQUAT)))))
        sessions.logSet(BENCH, SetDraft("60", "5"))

        exercises.applyBankUpdate(listOf(shipped(BENCH, "Barbell Bench Press")))

        assertNull(bank().singleOrNull { it.id == SQUAT })
        assertEquals(1, countOf("session_plan"))
        assertEquals("Barbell Bench Press", bank().single { it.id == BENCH }.name)
    }

    @Test
    fun anExerciseNobodyTrainedThatTheBankDropsSimplyGoes() = runBlocking {
        assertNotNull(bank().singleOrNull { it.id == SQUAT })

        exercises.applyBankUpdate(listOf(shipped(BENCH, "Barbell Bench Press")))

        assertNull(bank().singleOrNull { it.id == SQUAT })
        assertEquals(0, countOf("exercise WHERE id = '$SQUAT'"))
    }

    @Test
    fun aRowTheBankGainsArrives() = runBlocking {
        val added = shipped("Pendulum_Squat_Upstream", "Pendulum Squat")

        exercises.applyBankUpdate(listOf(shipped(BENCH, "Barbell Bench Press"), added))

        assertEquals("Pendulum Squat", bank().single { it.id == added.id }.name)
    }

    private suspend fun bank(): List<Exercise> = exercises.observeBank().first()

    private suspend fun bankExercise(id: String): Exercise = bank().single { it.id == id }

    private fun countOf(where: String): Int =
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $where").use {
            it.moveToFirst()
            it.getInt(0)
        }

    private fun shipped(id: String, name: String) = Exercise(
        id = id,
        bankName = name,
        primaryMuscles = listOf("chest"),
        secondaryMuscles = emptyList(),
        equipment = "barbell",
        mechanic = "compound",
        force = "push",
        category = "strength",
        level = "beginner",
        loggingType = LoggingType.WEIGHT_REPS,
        unilateral = false,
        source = ExerciseSource.UPSTREAM,
    )

    @Test
    fun everyShippedExerciseCarriesAProgressionModelDerivedFromWhatItMeasures() = runBlocking {
        val bank = bank().associateBy { it.id }

        // Anything with a load advances by what ADR 0004 settles.
        assertEquals(ProgressionModel.DOUBLE_PROGRESSION, bank.getValue(BENCH).progressionModel)
        // A stretch is recorded and never prescribed: no load for a recommendation to move.
        assertEquals(ProgressionModel.NONE, bank.getValue(HAMSTRING_STRETCH).progressionModel)
        assertEquals(ProgressionModel.NONE, bank.getValue(TREADMILL).progressionModel)
    }

    @Test
    fun aLifterCanSayTheyDoNotWantALiftCoached() = runBlocking {
        exercises.setProgressionModel(BENCH, ProgressionModel.NONE)

        val bench = bankExercise(BENCH)
        assertEquals(ProgressionModel.NONE, bench.progressionModel)
        // The bank still says what it derived; the lifter's answer is what anything reads.
        assertEquals(ProgressionModel.DOUBLE_PROGRESSION, bench.bankProgressionModel)
    }

    @Test
    fun clearingTheChoiceGoesBackToWhatTheBankDerived() = runBlocking {
        exercises.setProgressionModel(BENCH, ProgressionModel.NONE)

        exercises.setProgressionModel(BENCH, null)

        assertEquals(ProgressionModel.DOUBLE_PROGRESSION, bankExercise(BENCH).progressionModel)
        // And the row that recorded nothing else is gone: absence is how this table says "not
        // chosen" (ADR 0009).
        assertEquals(0, countOf("exercise_preference"))
    }

    @Test
    fun aBankUpdateLeavesWhatTheLifterDecidedAboutCoaching() = runBlocking {
        exercises.setProgressionModel(BENCH, ProgressionModel.NONE)
        val incoming = bank().map { it.copy(chosenProgressionModel = null) }

        exercises.applyBankUpdate(incoming)

        // The same protection the rename, the note and the display unit have.
        assertEquals(ProgressionModel.NONE, bankExercise(BENCH).progressionModel)
    }

    private companion object {
        const val TEST_DATABASE = "exercise-ownership-test.db"
        const val BENCH = "Barbell_Bench_Press_-_Medium_Grip"
        const val SQUAT = "Barbell_Squat"
        const val HAMSTRING_STRETCH = "90_90_Hamstring"
        const val TREADMILL = "Jogging_Treadmill"
    }
}
