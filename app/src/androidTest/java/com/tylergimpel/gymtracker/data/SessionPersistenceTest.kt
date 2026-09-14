package com.tylergimpel.gymtracker.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tylergimpel.gymtracker.domain.LoggedSet
import com.tylergimpel.gymtracker.domain.SetDraft
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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * What a real SQLite instance proves and plain objects cannot: that a gap survives a round trip,
 * that deleting a session takes its sets with it, and that an exercise with logged history cannot
 * be deleted out from under it.
 */
@RunWith(AndroidJUnit4::class)
class SessionPersistenceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val zone: ZoneId = ZoneId.of("Europe/London")
    private val clock = MovableClock(at("2026-09-14T18:00"), zone)

    private lateinit var database: GymDatabase
    private lateinit var repository: SessionRepository

    @Before
    fun setUp() {
        context.deleteDatabase(TEST_DATABASE)
        // A real file opened from the shipped asset: the exercise rows have to exist for the
        // foreign key on a logged set to mean anything.
        database = Room.databaseBuilder(context, GymDatabase::class.java, TEST_DATABASE)
            .createFromAsset(GymDatabase.ASSET_PATH)
            .addMigrations(*MIGRATIONS)
            .build()
        repository = SessionRepository(database, database.sessionDao(), clock, zone)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun loggingASetWithNoSessionOpensOne() = runBlocking {
        assertNull(repository.observeActiveSession().first())

        repository.logSet(BENCH, SetDraft("60", "5"))

        val session = repository.observeActiveSession().first()
        assertNotNull(session)
        assertEquals(LocalDate.of(2026, 9, 14), session!!.date)
        assertTrue(session.isActive)
    }

    @Test
    fun aSessionIsCompletedWithoutEverNamingARoutine() = runBlocking {
        repository.logSet(BENCH, SetDraft("60", "5"))
        val session = repository.observeActiveSession().first()!!

        repository.finishSession(session.id)

        assertNull(repository.observeActiveSession().first())
        assertEquals(1, database.sessionDao().observeAll().first().size)
    }

    @Test
    fun setsAreListedBackGroupedByExercise() = runBlocking {
        repository.logSet(BENCH, SetDraft("60", "5"))
        repository.logSet(SQUAT, SetDraft("100", "5"))
        repository.logSet(BENCH, SetDraft("62.5", "5"))

        val logged = currentSession()

        assertEquals(listOf("Barbell Bench Press - Medium Grip", "Barbell Squat"), logged.exercises.map { it.exerciseName })
        assertEquals(listOf(1, 2), logged.exercises[0].sets.map(LoggedSet::setIndex))
        assertEquals(listOf(1), logged.exercises[1].sets.map(LoggedSet::setIndex))
    }

    @Test
    fun weightIsStoredAsTheKilogramsItWasEnteredIn() = runBlocking {
        repository.logSet(BENCH, SetDraft("62.5", "5"))

        assertEquals(62.5, currentSession().exercises.single().sets.single().weight.value, 0.0)
    }

    @Test
    fun deletingASetLeavesAGapAndEveryOtherSetIntact() = runBlocking {
        repeat(3) { repository.logSet(BENCH, SetDraft("${60 + it}", "5")) }
        val middle = currentSession().exercises.single().sets.single { it.setIndex == 2 }

        repository.deleteSet(middle.id)

        val remaining = currentSession().exercises.single().sets
        assertEquals(listOf(1, 3), remaining.map(LoggedSet::setIndex))
        assertEquals(listOf(60.0, 62.0), remaining.map { it.weight.value })
    }

    @Test
    fun theGapSurvivesAndTheNextSetLandsAboveIt() = runBlocking {
        repeat(3) { repository.logSet(BENCH, SetDraft("60", "5")) }
        val middle = currentSession().exercises.single().sets.single { it.setIndex == 2 }
        repository.deleteSet(middle.id)

        repository.logSet(BENCH, SetDraft("65", "5"))

        assertEquals(listOf(1, 3, 4), currentSession().exercises.single().sets.map(LoggedSet::setIndex))
    }

    @Test
    fun editingASetLeavesEveryOtherSetAlone() = runBlocking {
        repeat(3) { repository.logSet(BENCH, SetDraft("${60 + it}", "5")) }
        val middle = currentSession().exercises.single().sets.single { it.setIndex == 2 }

        repository.editSet(middle.id, SetDraft("70", "3", note = "felt heavy"))

        val sets = currentSession().exercises.single().sets
        assertEquals(listOf(60.0, 70.0, 62.0), sets.map { it.weight.value })
        assertEquals(listOf(5, 3, 5), sets.map(LoggedSet::reps))
        assertEquals("felt heavy", sets[1].note)
        // The edited set keeps its position: editing is not a delete and re-add.
        assertEquals(listOf(1, 2, 3), sets.map(LoggedSet::setIndex))
    }

    @Test
    fun deletingASessionTakesItsSetsWithIt() = runBlocking {
        repository.logSet(BENCH, SetDraft("60", "5"))
        val session = repository.observeActiveSession().first()!!

        database.openHelper.writableDatabase.execSQL("DELETE FROM session WHERE id = ${session.id}")

        assertEquals(0, countOf("set_entry"))
        assertEquals(0, countOf("session_exercise"))
    }

    @Test
    fun anExerciseWithLoggedHistoryCannotBeDeleted() = runBlocking {
        repository.logSet(BENCH, SetDraft("60", "5"))

        val refused = runCatching {
            database.openHelper.writableDatabase.execSQL("DELETE FROM exercise WHERE id = '$BENCH'")
        }.exceptionOrNull()

        assertTrue("expected a constraint failure, got $refused", refused is SQLiteConstraintException)
        assertEquals(1, currentSession().exercises.single().sets.size)
    }

    @Test
    fun aSessionRunningPastMidnightKeepsTheDateItStartedOn() = runBlocking {
        clock.moveTo(at("2026-09-14T23:40"))
        repository.logSet(BENCH, SetDraft("60", "5"))

        clock.moveTo(at("2026-09-15T00:20"))
        repository.logSet(BENCH, SetDraft("60", "5"))
        val session = repository.observeActiveSession().first()!!
        repository.finishSession(session.id)

        val finished = database.sessionDao().observeAll().first().single()
        assertEquals(LocalDate.of(2026, 9, 14), finished.date)
        // Both sets belong to that one session rather than to a second one opened after midnight.
        assertEquals(1, database.sessionDao().observeAll().first().size)
    }

    @Test
    fun aSessionDateCanBeCorrectedWithoutMovingWhenItWasTrained() = runBlocking {
        repository.logSet(BENCH, SetDraft("60", "5"))
        val session = repository.observeActiveSession().first()!!

        repository.dateSession(session.id, LocalDate.of(2026, 9, 12))

        val corrected = database.sessionDao().session(session.id)!!
        assertEquals(LocalDate.of(2026, 9, 12), corrected.date)
        assertEquals(session.startedAt, corrected.startedAt)
    }

    @Test
    fun notesAttachToBothASetAndASession() = runBlocking {
        repository.logSet(BENCH, SetDraft("60", "5", note = "shoulder felt off"))
        val session = repository.observeActiveSession().first()!!

        repository.noteSession(session.id, "short on time")

        assertEquals("shoulder felt off", currentSession().exercises.single().sets.single().note)
        assertEquals("short on time", database.sessionDao().session(session.id)!!.note)
    }

    private suspend fun currentSession(): LoggedSession {
        val active = repository.observeActiveSession().first()!!
        return repository.observeSession(active.id).first()!!
    }

    private fun countOf(table: String): Int =
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use {
            it.moveToFirst()
            it.getInt(0)
        }

    private fun at(localDateTime: String): Instant =
        LocalDateTime.parse(localDateTime).atZone(zone).toInstant()

    /** A clock the test moves by hand, so midnight can be crossed without waiting for it. */
    private class MovableClock(private var now: Instant, private val zone: ZoneId) : Clock() {
        fun moveTo(instant: Instant) { now = instant }
        override fun getZone(): ZoneId = zone
        override fun withZone(zone: ZoneId): Clock = MovableClock(now, zone)
        override fun instant(): Instant = now
    }

    private companion object {
        const val TEST_DATABASE = "session-persistence-test.db"
        const val BENCH = "Barbell_Bench_Press_-_Medium_Grip"
        const val SQUAT = "Barbell_Squat"
    }
}
