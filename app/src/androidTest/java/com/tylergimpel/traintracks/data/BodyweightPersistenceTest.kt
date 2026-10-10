package com.tylergimpel.traintracks.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tylergimpel.traintracks.domain.BodyweightEntry
import com.tylergimpel.traintracks.domain.Kilograms
import com.tylergimpel.traintracks.domain.SetDraft
import com.tylergimpel.traintracks.domain.WeightUnit
import com.tylergimpel.traintracks.domain.volumeByMuscle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
            profile = ProfileRepository(database.profileDao(), WeightUnit.KILOGRAMS),
        )
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun recordingAWeightStoresTheDayAndTheKilograms() = runBlocking {
        repository.record(at("2026-09-14T07:10"), monday, Kilograms(82.4))

        val recorded = repository.observeHistory().first().mostRecentFirst.single()
        assertEquals(monday, recorded.date)
        assertEquals(82.4, recorded.weight.value, 0.0)
    }

    @Test
    fun aReadingTakenInPoundsIsStoredAsKilograms() = runBlocking {
        repository.record(at("2026-09-14T07:10"), monday, WeightUnit.POUNDS.toKilograms(180.0))

        assertEquals(81.65, storedWeights().single(), 0.01)
        assertEquals(180.0, WeightUnit.POUNDS.fromKilograms(repository.latest()!!.weight), 0.0)
    }

    @Test
    fun weighingAgainOnADayIsASecondReadingRatherThanACorrection() = runBlocking {
        repository.record(at("2026-09-14T07:10"), monday, Kilograms(82.4))

        repository.record(at("2026-09-14T21:30"), monday, Kilograms(82.9))

        // Morning and evening, both kept and the evening read first: the swing between them is a
        // measurement rather than a mistake.
        val history = repository.observeHistory().first().mostRecentFirst
        assertEquals(listOf(82.9, 82.4), history.map { it.weight.value })
        assertEquals(listOf(monday, monday), history.map(BodyweightEntry::date))
        assertEquals(82.9, repository.latest()!!.weight.value, 0.0)
    }

    @Test
    fun historyIsListedMostRecentFirst() = runBlocking {
        // Written out of order on purpose: a lifter catching up on a missed weigh-in writes the
        // older row last, and the list they read is still newest first.
        repository.record(at("2026-09-14T07:10"), monday, Kilograms(82.4))
        repository.record(at("2026-08-31T07:10"), monday.minusDays(14), Kilograms(84.0))
        repository.record(at("2026-09-07T07:10"), monday.minusDays(7), Kilograms(83.1))

        assertEquals(
            listOf(monday, monday.minusDays(7), monday.minusDays(14)),
            repository.observeHistory().first().mostRecentFirst.map(BodyweightEntry::date),
        )
    }

    @Test
    fun theLatestWeightIsTheMostRecentDayRatherThanTheRowWrittenLast() = runBlocking {
        repository.record(at("2026-09-14T07:10"), monday, Kilograms(82.4))
        repository.record(at("2026-09-07T07:10"), monday.minusDays(7), Kilograms(83.1))

        assertEquals(82.4, repository.latest()!!.weight.value, 0.0)
    }

    @Test
    fun aLifterWhoHasNeverWeighedThemselvesHasNoLatestWeight() = runBlocking {
        assertNull(repository.latest())
    }

    @Test
    fun anEntryCanBeCorrected() = runBlocking {
        val id = repository.record(at("2026-09-14T07:10"), monday, Kilograms(82.4))

        repository.correct(id, monday.minusDays(1), Kilograms(83.0))

        val corrected = repository.observeHistory().first().mostRecentFirst.single()
        assertEquals(monday.minusDays(1), corrected.date)
        assertEquals(83.0, corrected.weight.value, 0.0)
    }

    @Test
    fun correctingAWeighInLeavesWhenItWasTakenAlone() = runBlocking {
        val id = repository.record(at("2026-09-14T07:10"), monday, Kilograms(82.4))

        repository.correct(id, monday, Kilograms(82.9))

        // The same correction a session's date gets: what was written down changes, and the moment
        // the lifter actually stood on the scale is not a thing an edit gets to claim.
        val corrected = repository.latest()!!
        assertEquals(82.9, corrected.weight.value, 0.0)
        assertEquals(at("2026-09-14T07:10"), corrected.recordedAt)
    }

    @Test
    fun anEntryCanBeMovedOntoADayThatAlreadyHasOne() = runBlocking {
        // A day holds as many readings as the lifter took, so moving one onto an already-weighed
        // day is an ordinary correction and destroys nothing.
        repository.record(at("2026-09-13T07:10"), monday.minusDays(1), Kilograms(83.0))
        val id = repository.record(at("2026-09-14T07:10"), monday, Kilograms(82.4))

        repository.correct(id, monday.minusDays(1), Kilograms(82.4))

        val history = repository.observeHistory().first().mostRecentFirst
        assertEquals(listOf(monday.minusDays(1), monday.minusDays(1)), history.map(BodyweightEntry::date))
        assertEquals(setOf(82.4, 83.0), history.map { it.weight.value }.toSet())
    }

    @Test
    fun anEntryCanBeDeleted() = runBlocking {
        val id = repository.record(at("2026-09-14T07:10"), monday, Kilograms(82.4))

        repository.delete(id)

        assertEquals(emptyList<BodyweightEntry>(), repository.observeHistory().first().mostRecentFirst)
        assertEquals(0, countOf("bodyweight_entry"))
    }

    @Test
    fun recordingAWeightOpensNoSessionAndWritesNoSet() = runBlocking {
        repository.record(at("2026-09-14T07:10"), monday, Kilograms(82.4))

        assertNull(sessions.observeActiveSession().first())
        assertEquals(0, countOf("session"))
        assertEquals(0, countOf("session_exercise"))
        assertEquals(0, countOf("set_entry"))
    }

    @Test
    fun aWeighInOnATrainingDayIsNeitherASetNorAnyVolume() = runBlocking {
        sessions.logSet(BENCH, SetDraft("60", "5"))

        repository.record(at("2026-09-14T07:10"), monday, Kilograms(82.4))

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
        repository.record(at("2026-09-14T07:10"), monday, Kilograms(82.4))

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
