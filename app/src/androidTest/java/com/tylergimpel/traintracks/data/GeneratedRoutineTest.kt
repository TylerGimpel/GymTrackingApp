package com.tylergimpel.traintracks.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tylergimpel.traintracks.domain.CardioPlacement
import com.tylergimpel.traintracks.domain.Exercise
import com.tylergimpel.traintracks.domain.Experience
import com.tylergimpel.traintracks.domain.RepPreference
import com.tylergimpel.traintracks.domain.RoutineGoal
import com.tylergimpel.traintracks.domain.SPLIT_SHAPES
import com.tylergimpel.traintracks.domain.WeightUnit
import com.tylergimpel.traintracks.domain.fillDays
import com.tylergimpel.traintracks.domain.isNeverGenerated
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock

/**
 * Routines built from the bank that ships, for the three gyms #150 names: a full gym, a home gym
 * with dumbbells and nothing else, and a floor and a bar to hang from.
 *
 * Asserted by the lifts a program would have, because "reads like a real program" is the whole
 * point and nothing short of the real bank can show it.
 */
@RunWith(AndroidJUnit4::class)
class GeneratedRoutineTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var database: GymDatabase
    private lateinit var routines: RoutineRepository
    private lateinit var bank: List<Exercise>

    @Before
    fun setUp() = runBlocking {
        context.deleteDatabase(TEST_DATABASE)
        database = Room.databaseBuilder(context, GymDatabase::class.java, TEST_DATABASE)
            .createFromAsset(GymDatabase.ASSET_PATH)
            .addMigrations(*MIGRATIONS)
            .build()
        routines = RoutineRepository(database, database.routineDao(), database.sessionDao(), Clock.systemUTC())
        bank = ExerciseRepository(
            database,
            database.exerciseDao(),
            database.sessionDao(),
            ProfileRepository(database.profileDao(), WeightUnit.KILOGRAMS),
        ).observeBank().first()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun aFullGymGetsTheLiftsProgramsUse() {
        val days = names(pushPullLegs, FULL_GYM)

        assertEquals(listOf("Bench Press", "Overhead Press"), days.getValue("Push").take(2))
        assertTrue(days.getValue("Push").toString(), "Triceps Pushdown" in days.getValue("Push"))
        assertEquals(listOf("Lat Pulldown", "Barbell Row"), days.getValue("Pull").take(2))
        assertTrue(days.getValue("Pull").toString(), "Dumbbell Curl" in days.getValue("Pull"))
        assertEquals(listOf("Back Squat", "Romanian Deadlift"), days.getValue("Legs").take(2))
        assertTrue(days.getValue("Legs").toString(), "Standing Calf Raise" in days.getValue("Legs"))
    }

    @Test
    fun aDumbbellHomeGymGetsTheDumbbellVersions() {
        val days = names(pushPullLegs, setOf("dumbbell", "body only"))

        assertEquals("Dumbbell Bench Press", days.getValue("Push").first())
        assertTrue(days.getValue("Push").toString(), "Dumbbell Shoulder Press" in days.getValue("Push"))
        assertTrue(days.getValue("Pull").toString(), "Pull-Up" in days.getValue("Pull"))
        assertTrue(days.getValue("Pull").toString(), "Dumbbell Row" in days.getValue("Pull"))
        assertTrue(days.getValue("Legs").toString(), "Dumbbell Romanian Deadlift" in days.getValue("Legs"))
    }

    @Test
    fun aBodyweightGymGetsPushUpsPullUpsAndSquats() {
        val days = names(pushPullLegs, setOf("body only"))

        assertEquals("Push-Up", days.getValue("Push").first())
        assertEquals("Pull-Up", days.getValue("Pull").first())
        assertEquals("Bodyweight Squat", days.getValue("Legs").first())
    }

    @Test
    fun noGymGetsAnOddity() {
        listOf(FULL_GYM, setOf("dumbbell", "body only"), setOf("body only")).forEach { gym ->
            SPLIT_SHAPES.forEach { shape ->
                val chosen = fillDays(shape, bank, gym, RoutineGoal.HYPERTROPHY, cadence = 3).flatMap { it.exercises }
                val odd = chosen.map { it.exercise }.filter { it.isNeverGenerated || it.level == "expert" }
                assertTrue("${shape.id} at $gym: ${odd.map { it.name }}", odd.isEmpty())
            }
        }
    }

    @Test
    fun aRoutineBuiltNowIsNeverOfferedARebuild() = runBlocking {
        routines.createFromShape("PPL", pushPullLegs, RoutineGoal.HYPERTROPHY, 3, FULL_GYM, RepPreference.MODERATE, CardioPlacement.NONE, bank)

        assertFalse(current().offersRecipes)
    }

    @Test
    fun aRoutineFromBeforeRecipesIsOfferedOnceAndKeepingItEndsTheOffer() = runBlocking {
        builtTheOldWay()
        val before = current().days.map { day -> day.exercises.map { it.exercise.id } }
        assertTrue(current().offersRecipes)

        routines.keepWithoutRecipes()

        assertFalse(current().offersRecipes)
        assertEquals(before, current().days.map { day -> day.exercises.map { it.exercise.id } })
    }

    @Test
    fun takingTheOfferBuildsTheBestRankedLiftsWithKeptOnesStayingAndUndoPutsItBack() = runBlocking {
        builtTheOldWay()
        val push = current().rotation.first()
        val kept = push.exercises.last().exercise.id
        routines.setPinned(push.id, kept, pinned = true)
        val before = current().days.map { day -> day.exercises.map { it.exercise.id } }

        routines.regenerate(pushPullLegs, bank, fresh = true)

        val after = current()
        assertFalse(after.offersRecipes)
        assertEquals("Bench Press", after.rotation.first().exercises.first().exercise.name)
        assertTrue(kept in after.rotation.first().exercises.map { it.exercise.id })
        routines.undoRegeneration()
        assertEquals(before, current().days.map { day -> day.exercises.map { it.exercise.id } })
    }

    @Test
    fun aHandBuiltRoutineIsNeverOffered() = runBlocking {
        val id = routines.createRoutine("Mine")
        routines.addDay(id, "Day 1")
        routines.addExerciseToDay(current().rotation.first().id, "Barbell_Squat")

        assertFalse(current().offersRecipes)
    }

    @Test
    fun buildingOneLiftAgainPutsTheNextLiftForItsMuscleInItsPlace() = runBlocking {
        routines.createFromShape("PPL", pushPullLegs, RoutineGoal.HYPERTROPHY, 3, FULL_GYM, RepPreference.MODERATE, CardioPlacement.NONE, bank)
        val push = current().rotation.first()
        val first = push.exercises.first()

        val next = routines.buildAgainOne(push.id, first.exercise.id, bank, Experience.TRAINED)

        val now = current().rotation.first().exercises.first()
        assertEquals(next?.id, now.exercise.id)
        assertNotEquals(first.exercise.id, now.exercise.id)
        assertEquals("chest", now.exercise.primaryMuscles.first())
        assertEquals(first.sets, now.sets)
    }

    /** What each day of [shape] is filled with at [gym], by the names a lifter reads. */
    private fun names(shape: com.tylergimpel.traintracks.domain.SplitShape, gym: Set<String>): Map<String, List<String>> =
        fillDays(shape, bank, gym, RoutineGoal.HYPERTROPHY, cadence = 3)
            .associate { day -> day.shape.name to day.exercises.map { it.exercise.name } }

    /**
     * A generated routine as it stood before #150: built from a shape, never offered recipes, and
     * holding the kind of oddity the old alphabetical generator put first on a chest day.
     */
    private suspend fun builtTheOldWay() {
        routines.createFromShape("PPL", pushPullLegs, RoutineGoal.HYPERTROPHY, 3, FULL_GYM, RepPreference.MODERATE, CardioPlacement.NONE, bank)
        val dao = database.routineDao()
        dao.update(dao.routine()!!.copy(builtWithRecipes = false, seed = 4))
        val push = current().rotation.first()
        routines.replaceExerciseInDay(push.id, push.exercises.first().exercise.id, "Around_The_Worlds")
    }

    private suspend fun current() = routines.observeProgress().first()!!.routine

    private companion object {
        const val TEST_DATABASE = "generated-routine-test.db"

        /** A full gym is stored as no narrowing: the whole bank (#144). */
        val FULL_GYM = emptySet<String>()

        val pushPullLegs = SPLIT_SHAPES.single { it.id == "push-pull-legs-3" }
    }
}
