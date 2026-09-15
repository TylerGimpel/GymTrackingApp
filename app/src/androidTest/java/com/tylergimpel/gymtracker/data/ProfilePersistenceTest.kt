package com.tylergimpel.gymtracker.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tylergimpel.gymtracker.domain.Exercise
import com.tylergimpel.gymtracker.domain.WeightUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What the lifter reads weights in, and what that reaches.
 *
 * The point of storing it at all is that it is one answer: an exercise nobody has said anything
 * about reads the way its owner reads, on every screen, and a machine that departs from that still
 * carries its own label. Both halves are asserted here, because they are what keeps the bank and
 * the log from disagreeing about what a number on screen means.
 */
@RunWith(AndroidJUnit4::class)
class ProfilePersistenceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var database: GymDatabase
    private lateinit var exercises: ExerciseRepository

    @Before
    fun setUp() {
        context.deleteDatabase(TEST_DATABASE)
        database = Room.databaseBuilder(context, GymDatabase::class.java, TEST_DATABASE)
            .createFromAsset(GymDatabase.ASSET_PATH)
            .addMigrations(*MIGRATIONS)
            .build()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun aLifterWhoHasChosenNothingReadsInWhatTheirDeviceSuggests() = runBlocking {
        assertEquals(WeightUnit.POUNDS, profile(seededWith = WeightUnit.POUNDS).displayUnit())
    }

    @Test
    fun aChosenUnitOutlivesTheDeviceSuggestingAnother() = runBlocking {
        profile(seededWith = WeightUnit.POUNDS).setDisplayUnit(WeightUnit.KILOGRAMS)

        // A phone that changes locale — travelling, or a language setting — does not re-read a
        // lifter's whole history in a unit they did not ask for.
        assertEquals(WeightUnit.KILOGRAMS, profile(seededWith = WeightUnit.POUNDS).displayUnit())
    }

    @Test
    fun theChosenUnitIsWhatAnExerciseReadsInUntilItSaysOtherwise() = runBlocking {
        val profile = profile(seededWith = WeightUnit.KILOGRAMS)
        exercises = bankReading(profile)

        profile.setDisplayUnit(WeightUnit.POUNDS)

        assertEquals(WeightUnit.POUNDS, bench().displayUnit)
    }

    @Test
    fun anExerciseThatDepartsFromTheLifterKeepsItsOwnUnit() = runBlocking {
        val profile = profile(seededWith = WeightUnit.KILOGRAMS)
        exercises = bankReading(profile)

        // The lb machine among a gym of kg ones: its label is a fact about the machine, so it stays
        // put when its owner switches.
        exercises.setDisplayUnit(BENCH, WeightUnit.POUNDS)
        profile.setDisplayUnit(WeightUnit.POUNDS)
        profile.setDisplayUnit(WeightUnit.KILOGRAMS)

        assertEquals(WeightUnit.POUNDS, bench().displayUnit)
    }

    @Test
    fun anExerciseSetToTheUnitTheLifterAlreadyReadsInStoresNoChoiceAtAll() = runBlocking {
        val profile = profile(seededWith = WeightUnit.KILOGRAMS)
        exercises = bankReading(profile)

        exercises.setDisplayUnit(BENCH, WeightUnit.KILOGRAMS)

        // Nothing was departed from, so nothing is recorded as a departure — and the exercise
        // follows the lifter when they switch rather than being stranded in kilograms.
        assertEquals(0, countOf("exercise_preference"))
        profile.setDisplayUnit(WeightUnit.POUNDS)
        assertEquals(WeightUnit.POUNDS, bench().displayUnit)
    }

    private fun profile(seededWith: WeightUnit) =
        ProfileRepository(database.profileDao(), seededWith)

    private fun bankReading(profile: ProfileRepository) =
        ExerciseRepository(database, database.exerciseDao(), database.sessionDao(), profile)

    private suspend fun bench(): Exercise =
        exercises.observeBank().first().single { it.id == BENCH }

    private fun countOf(table: String): Int =
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use {
            it.moveToFirst()
            it.getInt(0)
        }

    private companion object {
        const val TEST_DATABASE = "profile-persistence-test.db"
        const val BENCH = "Barbell_Bench_Press_-_Medium_Grip"
    }
}
