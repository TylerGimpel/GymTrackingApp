package com.tylergimpel.gymtracker.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tylergimpel.gymtracker.domain.Exercise
import com.tylergimpel.gymtracker.domain.Experience
import com.tylergimpel.gymtracker.domain.Kilograms
import com.tylergimpel.gymtracker.domain.Sex
import com.tylergimpel.gymtracker.domain.Theme
import com.tylergimpel.gymtracker.domain.WeightUnit
import com.tylergimpel.gymtracker.domain.WorkoutTip
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun aProfileNobodyHasFilledInIsStillAProfile() = runBlocking {
        val nothing = profile(seededWith = WeightUnit.KILOGRAMS).observeProfile().first()

        // Every field optional and none of them gating anything (#87): a lifter who declines all
        // of it is answered by defaults, never by a value somebody invented for them.
        assertNull(nothing.sex)
        assertNull(nothing.birthYear)
        assertNull(nothing.heightCm)
        assertEquals(Experience.TRAINED, nothing.experience)
    }

    @Test
    fun eachThingALifterSaysAboutThemselvesIsKeptWithoutLosingTheOthers() = runBlocking {
        val profile = profile(seededWith = WeightUnit.KILOGRAMS)
        profile.setBar(Kilograms(20.0))
        profile.setExperience(Experience.NOVICE)

        profile.setSex(Sex.FEMALE)
        profile.setBirthYear(1994)
        profile.setHeightCm(168.0)

        // The row is replaced whole on every write, so a field written without reading the rest
        // first takes the others away with it — the lifter's bar included.
        val kept = profile.observeProfile().first()
        assertEquals(Sex.FEMALE, kept.sex)
        assertEquals(1994, kept.birthYear)
        assertEquals(168.0, kept.heightCm!!, 0.0)
        assertEquals(Experience.NOVICE, kept.experience)
        assertEquals(Kilograms(20.0), profile.bar())
    }

    @Test
    fun theAppFollowsThePhoneUntilTheLifterSaysOtherwise() = runBlocking {
        val profile = profile(seededWith = WeightUnit.KILOGRAMS)
        assertEquals(Theme.SYSTEM, profile.observeTheme().first())

        profile.setTheme(Theme.DARK)
        assertEquals(Theme.DARK, profile.observeTheme().first())

        // Back to following the phone is no choice at all, so a phone that switches at dusk is
        // followed again rather than pinned to whatever it was when they tapped.
        profile.setTheme(Theme.SYSTEM)
        assertEquals(Theme.SYSTEM, profile.observeTheme().first())
        assertNull(database.profileDao().profile()?.theme)
    }

    @Test
    fun eachTipIsRememberedOnItsOwnUntilTheLifterAsksForThemBack() = runBlocking {
        assertEquals(emptySet<WorkoutTip>(), profile(seededWith = WeightUnit.KILOGRAMS).observeTipsDone().first())

        profile(seededWith = WeightUnit.KILOGRAMS).tipDone(WorkoutTip.SWITCH_UNIT)

        // A repository built fresh over the same database is the app opened again: a tip gone
        // stays gone after a restart, and takes none of the others with it (#156).
        assertEquals(setOf(WorkoutTip.SWITCH_UNIT), profile(seededWith = WeightUnit.KILOGRAMS).observeTipsDone().first())

        profile(seededWith = WeightUnit.KILOGRAMS).tipDone(WorkoutTip.TAP_TO_TYPE)
        profile(seededWith = WeightUnit.KILOGRAMS).setTheme(Theme.DARK)
        profile(seededWith = WeightUnit.KILOGRAMS).showTipsAgain()

        // Show tips again brings all of them back, and nothing else about the lifter with it.
        assertEquals(emptySet<WorkoutTip>(), profile(seededWith = WeightUnit.KILOGRAMS).observeTipsDone().first())
        assertEquals(Theme.DARK, profile(seededWith = WeightUnit.KILOGRAMS).observeTheme().first())
    }

    @Test
    fun anythingSaidCanBeTakenBack() = runBlocking {
        val profile = profile(seededWith = WeightUnit.KILOGRAMS)
        profile.setSex(Sex.MALE)
        profile.setBirthYear(1980)
        profile.setHeightCm(180.0)

        profile.setSex(null)
        profile.setBirthYear(null)
        profile.setHeightCm(null)

        // Optional means optional after the fact too: withdrawing an answer leaves no trace of it.
        val withdrawn = profile.observeProfile().first()
        assertNull(withdrawn.sex)
        assertNull(withdrawn.birthYear)
        assertNull(withdrawn.heightCm)
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
