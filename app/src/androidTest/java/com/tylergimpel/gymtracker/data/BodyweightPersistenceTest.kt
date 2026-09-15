package com.tylergimpel.gymtracker.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tylergimpel.gymtracker.domain.BodyweightEntry
import com.tylergimpel.gymtracker.domain.Kilograms
import com.tylergimpel.gymtracker.domain.SetDraft
import com.tylergimpel.gymtracker.domain.WeightUnit
import com.tylergimpel.gymtracker.domain.volumeByMuscle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * What a real SQLite instance proves about a weigh-in: that it lands in a table of its own, that a
 * day holds one weight, and that none of it is visible to anything that counts training.
 *
 * The last of those is why bodyweight was worth a ticket of its own. Recording a weight must not
 * open a session, add an exercise or write a set — and asserting that against real tables is the
 * only way to show it, because the mistake it guards against is a schema mistake rather than an
 * arithmetic one.
 */
@RunWith(AndroidJUnit4::class)
class BodyweightPersistenceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val zone: ZoneId = ZoneId.of("Europe/London")
    private val monday: LocalDate = LocalDate.of(2026, 9, 14)

    private lateinit var database: GymDatabase
    private lateinit var repository: BodyweightRepository
    private lateinit var sessions: SessionRepository

    @Before
    fun setUp() {
        context.deleteDatabase(TEST_DATABASE)
        database = Room.databaseBuilder(context, GymDatabase::class.java, TEST_DATABASE)
            .createFromAsset(GymDatabase.ASSET_PATH)
            .addMigrations(*MIGRATIONS)
            .build()
        repository = BodyweightRepository(database, database.bodyweightDao())
        sessions = SessionRepository(
            database = database,
            sessions = database.sessionDao(),
            clock = Clock.fixed(at("2026-09-14T18:00"), zone),
            zone = zone,
        )
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun recordingAWeightStoresTheDayAndTheKilograms() = runBlocking {
        repository.record(monday, Kilograms(82.4))

        val recorded = repository.observeHistory().first().mostRecentFirst.single()
        assertEquals(monday, recorded.date)
        assertEquals(82.4, recorded.weight.value, 0.0)
    }

    @Test
    fun aReadingTakenInPoundsIsStoredAsKilograms() = runBlocking {
        repository.record(monday, WeightUnit.POUNDS.toKilograms(180.0))

        assertEquals(81.65, storedWeights().single(), 0.01)
        assertEquals(180.0, WeightUnit.POUNDS.fromKilograms(repository.latest()!!.weight), 0.0)
    }

    @Test
    fun weighingAgainOnADayCorrectsThatDayRatherThanAddingASecondReading() = runBlocking {
        repository.record(monday, Kilograms(82.4))

        repository.record(monday, Kilograms(82.9))

        val history = repository.observeHistory().first().mostRecentFirst
        assertEquals(1, history.size)
        assertEquals(82.9, history.single().weight.value, 0.0)
    }

    @Test
    fun historyIsListedMostRecentFirst() = runBlocking {
        // Written out of order on purpose: a lifter catching up on a missed weigh-in writes the
        // older row last, and the list they read is still newest first.
        repository.record(monday, Kilograms(82.4))
        repository.record(monday.minusDays(14), Kilograms(84.0))
        repository.record(monday.minusDays(7), Kilograms(83.1))

        assertEquals(
            listOf(monday, monday.minusDays(7), monday.minusDays(14)),
            repository.observeHistory().first().mostRecentFirst.map(BodyweightEntry::date),
        )
    }

    @Test
    fun theLatestWeightIsTheMostRecentDayRatherThanTheRowWrittenLast() = runBlocking {
        repository.record(monday, Kilograms(82.4))
        repository.record(monday.minusDays(7), Kilograms(83.1))

        assertEquals(82.4, repository.latest()!!.weight.value, 0.0)
    }

    @Test
    fun aLifterWhoHasNeverWeighedThemselvesHasNoLatestWeight() = runBlocking {
        assertNull(repository.latest())
    }

    @Test
    fun anEntryCanBeCorrected() = runBlocking {
        val id = repository.record(monday, Kilograms(82.4))

        assertTrue(repository.correct(id, monday.minusDays(1), Kilograms(83.0)))

        val corrected = repository.observeHistory().first().mostRecentFirst.single()
        assertEquals(monday.minusDays(1), corrected.date)
        assertEquals(83.0, corrected.weight.value, 0.0)
    }

    @Test
    fun correctingOnlyTheWeightLeavesTheEntryOnItsOwnDay() = runBlocking {
        // The day is unchanged, so the entry is not being moved anywhere and the day it already
        // occupies is not a day that is taken.
        val id = repository.record(monday, Kilograms(82.4))

        assertTrue(repository.correct(id, monday, Kilograms(82.9)))

        assertEquals(82.9, repository.latest()!!.weight.value, 0.0)
    }

    @Test
    fun anEntryIsNotMovedOntoADayThatAlreadyHasOne() = runBlocking {
        repository.record(monday.minusDays(1), Kilograms(83.0))
        val id = repository.record(monday, Kilograms(82.4))

        assertFalse(repository.correct(id, monday.minusDays(1), Kilograms(82.4)))

        // Both readings survive, untouched. A mistyped date is a common slip and the record it
        // lands on is the one thing that cannot be recovered by typing again.
        val history = repository.observeHistory().first().mostRecentFirst
        assertEquals(listOf(monday, monday.minusDays(1)), history.map(BodyweightEntry::date))
        assertEquals(listOf(82.4, 83.0), history.map { it.weight.value })
    }

    @Test
    fun anEntryCanBeDeleted() = runBlocking {
        val id = repository.record(monday, Kilograms(82.4))

        repository.delete(id)

        assertEquals(emptyList<BodyweightEntry>(), repository.observeHistory().first().mostRecentFirst)
        assertEquals(0, countOf("bodyweight_entry"))
    }

    @Test
    fun recordingAWeightOpensNoSessionAndWritesNoSet() = runBlocking {
        repository.record(monday, Kilograms(82.4))

        assertNull(sessions.observeActiveSession().first())
        assertEquals(0, countOf("session"))
        assertEquals(0, countOf("session_exercise"))
        assertEquals(0, countOf("set_entry"))
    }

    @Test
    fun aWeighInOnATrainingDayIsNeitherASetNorAnyVolume() = runBlocking {
        sessions.logSet(BENCH, SetDraft("60", "5"))

        repository.record(monday, Kilograms(82.4))

        // The session stays exactly what it was: one set, and the volume that one set earns. The
        // heaviest number on the calendar that day is the one that counts for nothing.
        val active = sessions.observeActiveSession().first()!!
        val logged = sessions.observeSession(active.id).first()!!
        assertEquals(1, logged.exercises.single().sets.size)
        assertEquals(
            mapOf("chest" to 1.0, "shoulders" to 0.5, "triceps" to 0.5),
            volumeByMuscle(logged.exercises),
        )
        assertEquals(1, countOf("bodyweight_entry"))
    }

    @Test
    fun deletingASessionLeavesEveryWeighInWhereItWas() = runBlocking {
        sessions.logSet(BENCH, SetDraft("60", "5"))
        val session = sessions.observeActiveSession().first()!!
        repository.record(monday, Kilograms(82.4))

        database.openHelper.writableDatabase.execSQL("DELETE FROM session WHERE id = ${session.id}")

        // Nothing joins the two, so a session taken out of the log cannot take a weigh-in with it.
        assertEquals(82.4, repository.latest()!!.weight.value, 0.0)
    }

    private fun storedWeights(): List<Double> =
        database.openHelper.readableDatabase
            .query("SELECT weight_kg FROM bodyweight_entry ORDER BY id")
            .use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getDouble(0)) } }

    private fun countOf(table: String): Int =
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use {
            it.moveToFirst()
            it.getInt(0)
        }

    private fun at(localDateTime: String): Instant =
        LocalDateTime.parse(localDateTime).atZone(zone).toInstant()

    private companion object {
        const val TEST_DATABASE = "bodyweight-persistence-test.db"
        const val BENCH = "Barbell_Bench_Press_-_Medium_Grip"
    }
}
