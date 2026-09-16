package com.tylergimpel.gymtracker.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import com.tylergimpel.gymtracker.domain.WeightUnit

/**
 * Proves the shipped asset database is one Room will actually open.
 *
 * A prepopulated file whose schema does not match what Room was compiled for fails at runtime with
 * no compile-time warning — it is the specific crash the build-time generation exists to prevent, so
 * it gets a test that fails loudly if the asset ever drifts from the schema.
 */
@RunWith(AndroidJUnit4::class)
class ExerciseBankAssetTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var fromAsset: GymDatabase
    private lateinit var freshlyCreated: GymDatabase

    @Before
    fun setUp() {
        // A real file, because createFromAsset only has anything to do when Room is opening one.
        context.deleteDatabase(TEST_DATABASE)
        fromAsset = Room.databaseBuilder(context, GymDatabase::class.java, TEST_DATABASE)
            .createFromAsset(GymDatabase.ASSET_PATH)
            .build()
        // The same schema, created by Room itself. Its identity hash is the expectation the asset
        // has to meet, which makes the comparison below a direct assertion rather than an inference
        // from the absence of a crash.
        freshlyCreated = Room.inMemoryDatabaseBuilder(context, GymDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        fromAsset.close()
        freshlyCreated.close()
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun assetSchemaIdentityMatchesWhatRoomExpects() {
        assertEquals(freshlyCreated.identityHash(), fromAsset.identityHash())
    }

    @Test
    fun assetDatabaseOpensAndCarriesTheWholeBank() = runBlocking {
        // 876 upstream rows, less the 3 superseded by a row that records the same movement with an
        // optional added weight, plus the 20 authored ones.
        assertEquals(893, fromAsset.exerciseDao().count())
    }

    @Test
    fun assetSchemaVersionIsTheOneRoomWasCompiledFor() {
        assertEquals(
            GymDatabase.VERSION,
            fromAsset.openHelper.readableDatabase.version,
        )
    }

    @Test
    fun everyExerciseCarriesAPrimaryMuscleAndAReadableLoggingType() = runBlocking {
        val bank = ExerciseRepository(
            fromAsset,
            fromAsset.exerciseDao(),
            fromAsset.sessionDao(),
            ProfileRepository(fromAsset.profileDao(), WeightUnit.KILOGRAMS),
        ).observeBank().first()

        assertEquals(893, bank.size)
        assertTrue(bank.all { it.primaryMuscle != null })
        // LoggingType.fromStored throws on anything outside the vocabulary, so reaching here at all
        // means every shipped row parsed.
        assertTrue(bank.all { it.loggingType.stored.isNotEmpty() })
    }

    @Test
    fun cardioRowsShipWithTheirRetaggedEquipment() = runBlocking {
        val bank = ExerciseRepository(
            fromAsset,
            fromAsset.exerciseDao(),
            fromAsset.sessionDao(),
            ProfileRepository(fromAsset.profileDao(), WeightUnit.KILOGRAMS),
        ).observeBank().first().associateBy { it.id }

        // Upstream tags every cardio machine as the generic `machine`, which cannot drive the gym
        // equipment checklist. These are the re-tagged values from data/cardio-retag.json.
        assertEquals("treadmill", bank.getValue("Running_Treadmill").equipment)
        assertEquals("rower", bank.getValue("Rowing_Stationary").equipment)
        assertEquals("stair climber", bank.getValue("Stairmaster").equipment)
    }

    @Test
    fun authoredExercisesShipAlongsideUpstreamOnes() = runBlocking {
        val bank = ExerciseRepository(
            fromAsset,
            fromAsset.exerciseDao(),
            fromAsset.sessionDao(),
            ProfileRepository(fromAsset.profileDao(), WeightUnit.KILOGRAMS),
        ).observeBank().first().associateBy { it.id }

        assertNotNull(bank["Pendlay_Row"])
        assertEquals(listOf("shoulders"), bank.getValue("Landmine_Press").primaryMuscles)
        assertTrue(bank.getValue("Single_Leg_Leg_Press").unilateral)
        // Nothing in "Bulgarian Split Squat" says one side at a time, so this row is unilateral only
        // because the authored data says so — which is the flag surviving the build, not a guess.
        assertTrue(bank.getValue("Bulgarian_Split_Squat").unilateral)
    }

    @Test
    fun theAssetCarriesNoInstructionsOrImages() {
        val columns = fromAsset.openHelper.readableDatabase
            .query("SELECT * FROM exercise LIMIT 0")
            .use { it.columnNames.toList() }

        // ADR 0002: upstream prose and images are excluded on provenance grounds. If a column for
        // either ever appears, the omission has been undone.
        assertFalse(columns.any { it.contains("instruction", ignoreCase = true) })
        assertFalse(columns.any { it.contains("image", ignoreCase = true) })
    }

    private fun GymDatabase.identityHash(): String =
        openHelper.readableDatabase
            .query("SELECT identity_hash FROM room_master_table LIMIT 1")
            .use {
                assertTrue("room_master_table is empty", it.moveToFirst())
                it.getString(0)
            }

    private companion object {
        const val TEST_DATABASE = "exercise-bank-asset-test.db"
    }
}
